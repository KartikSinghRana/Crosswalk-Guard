package com.crosswalkguard

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.*
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.crosswalkguard.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var map: GoogleMap
    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var distractionDetector: DistractionDetector
    private lateinit var alertManager: AlertManager
    private lateinit var analytics: AnalyticsManager
    private lateinit var crosswalkRepo: CrosswalkRepository

    private var currentLocation: Location? = null
    private val crosswalkMarkers = mutableListOf<Marker>()
    private var radiusCircle: Circle? = null
    private var isProtectionOn = true
    private var isDistracted   = false
    private var currentLevel   = AlertLevel.NONE
    private var hasCenteredMap = false

    private val radiusOptions = listOf(100, 200, 300, 500, 750, 1000, 1500, 2000, 3000, 5000)
    private var selectedRadiusIndex = 3

    private var iconReal     : BitmapDescriptor? = null
    private var iconSynthetic: BitmapDescriptor? = null

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 2000L
    ).setMinUpdateIntervalMillis(1000L).build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onLocationUpdated)
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startLocationUpdates()
            enableMyLocationLayer()
        }
    }

    // ═════════════════════════════ Lifecycle ══════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        analytics           = AnalyticsManager(this)
        crosswalkRepo       = CrosswalkRepository()
        alertManager        = AlertManager(this, analytics)
        distractionDetector = DistractionDetector(this) { distracted ->
            isDistracted = distracted
            updateDistractionUI(distracted)
            evaluateAlert()
        }

        iconReal      = buildCrosswalkIcon(synthetic = false)
        iconSynthetic = buildCrosswalkIcon(synthetic = true)

        setupMap()
        setupUI()
        applyWindowInsets()
        requestPermissions()
        updateStats()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 99)
    }

    override fun onResume()  { super.onResume();  if (isProtectionOn) distractionDetector.start() }
    override fun onPause()   { super.onPause();   distractionDetector.stop() }
    override fun onDestroy() {
        super.onDestroy()
        if (::fusedLocation.isInitialized)
            fusedLocation.removeLocationUpdates(locationCallback)
        alertManager.dismiss()
    }

    // ═════════════════════════════ Map ════════════════════════════════════════

    private fun setupMap() {
        (supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment)
            .getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        enableMyLocationLayer()
        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))
        } catch (_: Resources.NotFoundException) {}
        map.uiSettings.apply {
            isMyLocationButtonEnabled = false
            isCompassEnabled          = false
            isMapToolbarEnabled       = false
        }
    }

    private fun enableMyLocationLayer() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            @Suppress("MissingPermission")
            map.isMyLocationEnabled = true
        }
    }

    // ═════════════════════════════ UI ═════════════════════════════════════════

    private fun setupUI() {
        binding.fabMyLocation.setOnClickListener {
            currentLocation?.let {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    LatLng(it.latitude, it.longitude), 17f))
            }
        }

        binding.chipToggle.setOnClickListener {
            isProtectionOn = !isProtectionOn
            binding.chipToggle.text = if (isProtectionOn) "🛡  ON" else "🛡  OFF"
            if (isProtectionOn) {
                distractionDetector.start()
                binding.chipStatus.visibility = View.VISIBLE
            } else {
                distractionDetector.stop()
                alertManager.dismiss()
                currentLevel = AlertLevel.NONE
                binding.chipStatus.visibility = View.GONE
            }
        }

        updateRadiusButtons()

        binding.btnRadiusMinus.setOnClickListener {
            if (selectedRadiusIndex > 0) {
                selectedRadiusIndex--
                updateRadiusButtons()
                refreshCrosswalks()
            }
        }

        binding.btnRadiusPlus.setOnClickListener {
            if (selectedRadiusIndex < radiusOptions.lastIndex) {
                selectedRadiusIndex++
                updateRadiusButtons()
                refreshCrosswalks()
            }
        }

        binding.btnRefreshCrosswalks.setOnClickListener {
            it.animate().rotationBy(360f).setDuration(500).start()
            binding.tvCrosswalkDistance.text = "Refreshing…"
            currentLocation?.let { loc ->
                lifecycleScope.launch {
                    crosswalkRepo.bustCache()
                    val crosswalks = crosswalkRepo.fetchCrosswalks(
                        loc.latitude, loc.longitude,
                        radiusOptions[selectedRadiusIndex]
                    )
                    drawCrosswalksOnMap(crosswalks, loc)
                    drawRadiusCircle(loc)
                }
            }
        }
    }

    private fun updateRadiusButtons() {
        val r = radiusOptions[selectedRadiusIndex]
        binding.tvRadius.text            = if (r >= 1000) "${r / 1000}km" else "${r}m"
        binding.btnRadiusMinus.isEnabled = selectedRadiusIndex > 0
        binding.btnRadiusPlus.isEnabled  = selectedRadiusIndex < radiusOptions.lastIndex
    }

    private fun refreshCrosswalks() {
        currentLocation?.let { loc ->
            lifecycleScope.launch {
                crosswalkRepo.bustCache()
                val crosswalks = crosswalkRepo.fetchCrosswalks(
                    loc.latitude, loc.longitude,
                    radiusOptions[selectedRadiusIndex]
                )
                drawCrosswalksOnMap(crosswalks, loc)
                drawRadiusCircle(loc)
            }
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (v.layoutParams as ViewGroup.MarginLayoutParams).topMargin = s.top + 16; insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomCard) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (v.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = s.bottom + 16; insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabMyLocation) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (v.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                s.bottom + resources.getDimensionPixelSize(R.dimen.fab_bottom_offset); insets
        }
    }

    // ═════════════════════════════ Location ═══════════════════════════════════

    private fun requestPermissions() {
        permLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocation = LocationServices.getFusedLocationProviderClient(this)
        fusedLocation.requestLocationUpdates(
            locationRequest, locationCallback, Looper.getMainLooper()
        )
    }

    private fun onLocationUpdated(location: Location) {
        currentLocation = location
        val latlng = LatLng(location.latitude, location.longitude)
        if (!hasCenteredMap) {
            hasCenteredMap = true
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latlng, 17f))
        }

        lifecycleScope.launch {
            val radius     = radiusOptions[selectedRadiusIndex]
            val crosswalks = crosswalkRepo.fetchCrosswalks(
                location.latitude, location.longitude, radius
            )
            drawCrosswalksOnMap(crosswalks, location)
            drawRadiusCircle(location)

            if (!isProtectionOn || !isDistracted) {
                if (currentLevel != AlertLevel.NONE) {
                    currentLevel = AlertLevel.NONE
                    alertManager.dismiss()
                    updateProximityUI(null, null)
                }
                return@launch
            }

            val nearest = crosswalks.minByOrNull { cw ->
                FloatArray(1).also { r ->
                    Location.distanceBetween(
                        location.latitude, location.longitude, cw.lat, cw.lon, r)
                }[0]
            }
            val dist = nearest?.let { cw ->
                FloatArray(1).also { r ->
                    Location.distanceBetween(
                        location.latitude, location.longitude, cw.lat, cw.lon, r)
                }[0]
            }

            updateProximityUI(nearest, dist)
            val newLevel = when {
                dist == null || dist > 30f -> AlertLevel.NONE
                dist > 12f                 -> AlertLevel.APPROACHING
                dist > 7f                  -> AlertLevel.CLOSE
                else                       -> AlertLevel.ENTERING
            }
            if (newLevel != currentLevel) {
                currentLevel = newLevel
                alertManager.showAlert(newLevel, nearest)
            }
        }
    }

    private fun evaluateAlert() { currentLocation?.let(::onLocationUpdated) }

    // ═════════════════════════════ Map Drawing ════════════════════════════════

    private fun drawCrosswalksOnMap(crosswalks: List<Crosswalk>, userLocation: Location) {
        crosswalkMarkers.forEach { it.remove() }
        crosswalkMarkers.clear()

        crosswalks.forEach { cw ->
            val isSynthetic = cw.id < 0
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(cw.lat, cw.lon))
                    .title(if (isSynthetic) "Intersection (estimated)" else "Crosswalk")
                    .icon(if (isSynthetic) iconSynthetic else iconReal)
                    .anchor(0.5f, 0.5f)
                    .zIndex(1f)
            )
            marker?.let { crosswalkMarkers.add(it) }
        }

        binding.tvCrosswalkDistance.text = when {
            crosswalks.isEmpty()         -> "No crosswalks found"
            crosswalks.any { it.id < 0 } -> "${crosswalks.size} intersections (estimated)"
            else                         -> "${crosswalks.size} crosswalks mapped"
        }
    }

    private fun drawRadiusCircle(location: Location) {
        radiusCircle?.remove()
        radiusCircle = map.addCircle(
            CircleOptions()
                .center(LatLng(location.latitude, location.longitude))
                .radius(radiusOptions[selectedRadiusIndex].toDouble())
                .strokeColor(0x441A73E8)
                .fillColor(0x111A73E8)
                .strokeWidth(2f)
        )
    }

    // ═════════════════════════════ Custom Icon ════════════════════════════════

    private fun buildCrosswalkIcon(synthetic: Boolean): BitmapDescriptor {
        val dp          = resources.displayMetrics.density
        val size        = (32 * dp).toInt()
        val radius      = (7 * dp)
        val pad         = (4 * dp)
        val stripeCount = 4

        val bmp    = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgColor = if (synthetic) Color.parseColor("#F57C00") else Color.parseColor("#1A73E8")
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, bgPaint)

        val clipPath = Path().apply {
            addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        val stripeAreaTop    = pad + size * 0.38f
        val stripeAreaBottom = size - pad
        val totalStripeArea  = stripeAreaBottom - stripeAreaTop
        val stripeH          = totalStripeArea / (stripeCount * 2f - 1f)
        val stripePaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        for (i in 0 until stripeCount) {
            val top = stripeAreaTop + i * (stripeH * 2f)
            canvas.drawRect(pad, top, size - pad, top + stripeH, stripePaint)
        }

        val personPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = Color.WHITE
            strokeWidth = 2.5f * dp
            strokeCap   = Paint.Cap.ROUND
            style       = Paint.Style.FILL_AND_STROKE
        }
        val cx     = size / 2f
        val headR  = 2.8f * dp
        val headCy = pad + headR + dp
        canvas.drawCircle(cx, headCy, headR, personPaint)

        val bodyTop = headCy + headR
        val bodyBot = bodyTop + 6f * dp
        personPaint.style = Paint.Style.STROKE
        canvas.drawLine(cx, bodyTop, cx, bodyBot, personPaint)
        canvas.drawLine(cx - 3.5f*dp, bodyTop + 1.5f*dp, cx + 3.5f*dp, bodyTop + 1.5f*dp, personPaint)
        canvas.drawLine(cx, bodyBot, cx - 2.5f*dp, bodyBot + 4f*dp, personPaint)
        canvas.drawLine(cx, bodyBot, cx + 2.5f*dp, bodyBot + 4f*dp, personPaint)

        return BitmapDescriptorFactory.fromBitmap(bmp)
    }

    // ═════════════════════════════ UI Updates ════════════════════════════════

    private fun updateDistractionUI(distracted: Boolean) {
        binding.chipStatus.text = if (distracted) "📵  Phone detected" else "✅  Eyes on road"
        binding.chipStatus.setChipBackgroundColorResource(
            if (distracted) R.color.alert_red_light else R.color.safe_green_light
        )
    }

    private fun updateProximityUI(nearest: Crosswalk?, dist: Float?) {
        if (nearest == null || dist == null) {
            binding.proximityBar.progress = 0
            return
        }
        binding.tvCrosswalkDistance.text = "Nearest crosswalk: ${dist.toInt()}m"
        binding.proximityBar.progress    =
            ((1 - (dist / 30f).coerceIn(0f, 1f)) * 100).toInt()
        binding.proximityBar.progressTintList =
            android.content.res.ColorStateList.valueOf(
                when {
                    dist < 7f  -> ContextCompat.getColor(this, R.color.alert_red)
                    dist < 14f -> ContextCompat.getColor(this, R.color.alert_orange)
                    else       -> ContextCompat.getColor(this, R.color.safe_green)
                }
            )
    }

    private fun updateStats() {
        val s = analytics.getStats()
        binding.tvStatDistracted.text = s.distractionsPrevented.toString()
        binding.tvStatCrosswalks.text  = s.crosswalksApproached.toString()
    }
}