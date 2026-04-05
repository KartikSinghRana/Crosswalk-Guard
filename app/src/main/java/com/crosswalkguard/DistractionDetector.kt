package com.crosswalkguard

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

class DistractionDetector(
    context: Context,
    private val onChanged: (distracted: Boolean) -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Prefer TYPE_GRAVITY; fall back to raw accelerometer
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gravity   = FloatArray(3)
    private val alpha     = 0.8f  // low-pass smoothing
    private var lastState = false

    fun start() {
        sensor?.let {
            sensorManager.registerListener(
                this, it, SensorManager.SENSOR_DELAY_UI,
                Handler(Looper.getMainLooper())
            )
        }
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        // Smooth the gravity vector
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

        val mag = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
        if (mag < 7f) return // skip near-zero readings

        // normY > 0 → phone is upright (being held/viewed)
        // normZ < 0.85 → screen isn't flat on a table
        val normY = gravity[1] / mag
        val normZ = gravity[2] / mag
        val isLooking = normY > 0.28f && normZ < 0.85f

        if (isLooking != lastState) {
            lastState = isLooking
            onChanged(isLooking)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
