package com.crosswalkguard

enum class AlertLevel {
    NONE,        // No crosswalk nearby or not distracted
    APPROACHING, // ~30m — gentle toast + buzz
    CLOSE,       // ~15m — popup card + vibration + tone
    ENTERING     // ~8m  — full-screen critical alert
}
