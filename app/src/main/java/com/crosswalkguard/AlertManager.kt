package com.crosswalkguard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.crosswalkguard.databinding.DialogAlertBinding
import com.crosswalkguard.databinding.DialogEnteringBinding

class AlertManager(
    private val activity: AppCompatActivity,
    private val analytics: AnalyticsManager
) {
    private var activeDialog: Dialog? = null
    private var currentLevel = AlertLevel.NONE
    private var tone: ToneGenerator? = null

    @Suppress("DEPRECATION")
    @SuppressLint("ServiceCast")
    private val vibrator: Vibrator =
        activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    // ── Public API ────────────────────────────────────────────────────────────

    fun showAlert(level: AlertLevel, crosswalk: Crosswalk?) {
        if (level == currentLevel) return
        currentLevel = level
        dismiss()
        if (activity.isFinishing || activity.isDestroyed) return

        when (level) {
            AlertLevel.NONE        -> Unit
            AlertLevel.APPROACHING -> showApproaching()
            AlertLevel.CLOSE       -> showClose()
            AlertLevel.ENTERING    -> showEntering()
        }
    }

    fun dismiss() {
        activeDialog?.dismiss()
        activeDialog = null
        tone?.release()
        tone = null
    }

    // ── Alert Levels ──────────────────────────────────────────────────────────

    /** Level 1 — subtle: toast + short double-buzz */
    private fun showApproaching() {
        analytics.recordDistraction()
        analytics.recordCrosswalkApproached()
        vibrate(longArrayOf(0, 120, 80, 120))
        Toast.makeText(activity, "⚠️  Crosswalk ahead — look up!", Toast.LENGTH_SHORT).show()
    }

    /** Level 2 — moderate: modal card + vibration + warning tone */
    private fun showClose() {
        vibrate(longArrayOf(0, 250, 100, 250, 100, 250))
        playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1200)

        val dialog = Dialog(activity, R.style.CrosswalkDialog)
        val binding = DialogAlertBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)
        dialog.setCancelable(true)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        binding.btnDismiss.setOnClickListener { dismiss() }
        dialog.show()
        activeDialog = dialog
    }

    /** Level 3 — critical: full-screen takeover + emergency tone */
    private fun showEntering() {
        vibrate(longArrayOf(0, 500, 100, 500, 100, 500, 100, 800))
        playTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 2500)

        val dialog = Dialog(activity, R.style.CrosswalkDialogFullscreen)
        val binding = DialogEnteringBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)
        dialog.setCancelable(false)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        binding.btnAcknowledge.setOnClickListener { dismiss() }
        dialog.show()
        activeDialog = dialog
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun vibrate(pattern: LongArray) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun playTone(type: Int, durationMs: Int) {
        try {
            tone?.release()
            tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tone?.startTone(type, durationMs)
        } catch (_: Exception) { /* audio unavailable — silent */ }
    }
}
