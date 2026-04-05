package com.crosswalkguard

import android.location.Location
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

data class Crosswalk(val id: Long, val lat: Double, val lon: Double)

private data class OsmResponse(val elements: List<OsmElement> = emptyList())
private data class OsmElement(
    val id: Long = 0, val lat: Double = 0.0, val lon: Double = 0.0,
    val tags: Map<String, String> = emptyMap()
)

class CrosswalkRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    fun bustCache() {
        cacheTime = 0L
        cacheRadius = 0
    }

    private val overpassEndpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    )

    private var cachedList  : List<Crosswalk> = emptyList()
    private var cacheLat    = 0.0
    private var cacheLon    = 0.0
    private var cacheTime   = 0L
    private var cacheRadius = 0

    suspend fun fetchCrosswalks(lat: Double, lon: Double, radiusMeters: Int = 500): List<Crosswalk> =
        withContext(Dispatchers.IO) {
            val moved = FloatArray(1).also {
                Location.distanceBetween(cacheLat, cacheLon, lat, lon, it)
            }[0]
            val age           = System.currentTimeMillis() - cacheTime
            val radiusChanged = radiusMeters != cacheRadius

            if (cachedList.isNotEmpty() && age < 90_000L && moved < 40f && !radiusChanged) {
                Log.d("CWRepo", "Cache hit — ${cachedList.size} items")
                return@withContext cachedList
            }

            val query = """
                [out:json][timeout:15];
                (
                  node["highway"="crossing"](around:$radiusMeters,$lat,$lon);
                  node["crossing"="marked"](around:$radiusMeters,$lat,$lon);
                  node["crossing"="zebra"](around:$radiusMeters,$lat,$lon);
                  node["crossing"="traffic_signals"](around:$radiusMeters,$lat,$lon);
                  node["crossing"="uncontrolled"](around:$radiusMeters,$lat,$lon);
                  node["highway"="traffic_signals"](around:$radiusMeters,$lat,$lon);
                );
                out body;
            """.trimIndent()

            Log.d("CWRepo", "Racing ${overpassEndpoints.size} mirrors, radius=${radiusMeters}m")

            // ── Race: fire all mirrors simultaneously, use first valid response ──
            val result: List<Crosswalk>? = try {
                raceEndpoints(query)
            } catch (e: Exception) {
                Log.e("CWRepo", "Race failed: ${e.message}")
                null
            }

            val final = when {
                result != null && result.isNotEmpty() -> result
                result != null                         -> generateSyntheticMarkers(lat, lon, radiusMeters)
                else                                   -> cachedList.ifEmpty {
                    generateSyntheticMarkers(lat, lon, radiusMeters)
                }
            }

            Log.d("CWRepo", "Resolved ${final.size} crosswalks (synthetic=${final.any { it.id < 0 }})")
            cachedList  = final
            cacheLat    = lat
            cacheLon    = lon
            cacheTime   = System.currentTimeMillis()
            cacheRadius = radiusMeters
            final
        }

    /**
     * Fires all endpoints concurrently. Returns the parsed result from whichever
     * responds first with a valid 2xx body. Cancels the rest immediately.
     */
    private suspend fun raceEndpoints(query: String): List<Crosswalk>? =
        withContext(Dispatchers.IO) {
            val body: RequestBody = FormBody.Builder().add("data", query).build()

            // Use select{} to return as soon as one deferred succeeds
            val deferreds = overpassEndpoints.map { endpoint ->
                async {
                    try {
                        val req = okhttp3.Request.Builder()
                            .url(endpoint)
                            .post(FormBody.Builder().add("data", query).build())
                            .header("User-Agent", "CrosswalkGuard/1.0 Android")
                            .build()
                        val resp     = client.newCall(req).execute()
                        val respBody = resp.body?.string()
                        if (!resp.isSuccessful || respBody.isNullOrBlank()) {
                            Log.w("CWRepo", "$endpoint → ${resp.code}")
                            return@async null
                        }
                        val parsed = gson.fromJson(respBody, OsmResponse::class.java)
                            .elements
                            .filter { it.lat != 0.0 && it.lon != 0.0 }
                            .map { Crosswalk(it.id, it.lat, it.lon) }
                            .distinctBy { it.id }
                        Log.d("CWRepo", "$endpoint → ${parsed.size} results")
                        parsed
                    } catch (e: Exception) {
                        Log.e("CWRepo", "$endpoint exception: ${e.message}")
                        null
                    }
                }
            }

            // Wait for first non-null result, cancel the rest
            var winner: List<Crosswalk>? = null
            try {
                winner = deferreds
                    .awaitFirstNonNull()
            } finally {
                deferreds.forEach { it.cancel() }
            }
            winner
        }

    private fun generateSyntheticMarkers(lat: Double, lon: Double, radius: Int): List<Crosswalk> {
        val mPerLat = 111_320.0
        val mPerLon = 111_320.0 * Math.cos(Math.toRadians(lat))
        val spacing = (radius / 4.0).coerceAtLeast(60.0)
        return listOf(
            Pair( spacing,  0.0), Pair(-spacing,  0.0),
            Pair( 0.0,  spacing), Pair( 0.0, -spacing),
            Pair( spacing,  spacing), Pair(-spacing,  spacing),
            Pair( spacing, -spacing), Pair(-spacing, -spacing),
            Pair( spacing * 1.8,  0.0), Pair(-spacing * 1.8,  0.0),
            Pair( 0.0,  spacing * 1.8), Pair( 0.0, -spacing * 1.8)
        ).mapIndexed { i, (dy, dx) ->
            Crosswalk(-1000L - i, lat + dy / mPerLat, lon + dx / mPerLon)
        }
    }
}

// Extension: await the first Deferred<T?> in a list that returns non-null
private suspend fun <T> List<Deferred<T?>>.awaitFirstNonNull(): T? = coroutineScope {
    val channel = kotlinx.coroutines.channels.Channel<T?>(capacity = this@awaitFirstNonNull.size)
    val jobs = this@awaitFirstNonNull.map { deferred ->
        launch {
            try { channel.send(deferred.await()) }
            catch (_: Exception) { channel.send(null) }
        }
    }
    var result: T? = null
    var received  = 0
    while (received < this@awaitFirstNonNull.size) {
        val value = channel.receive()
        received++
        if (value != null) { result = value; break }
    }
    jobs.forEach { it.cancel() }
    channel.close()
    result
}