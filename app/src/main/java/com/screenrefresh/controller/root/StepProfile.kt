package com.screenrefresh.controller.root

data class StepProfile(
    val id: String,
    val name: String,
    val rates: List<Int>
)

object StepProfiles {
    val profiles = listOf(
        StepProfile("custom", "自定义", emptyList()),
        StepProfile("120_144", "120→144", listOf(120, 144)),
        StepProfile("120_165", "120→165", listOf(120, 165)),
        StepProfile("90_120_144", "90→120→144", listOf(90, 120, 144)),
        StepProfile("60_90_120", "60→90→120", listOf(60, 90, 120)),
    )

    private const val PREFS_KEY = "custom_rates"

    fun getById(id: String): StepProfile {
        return profiles.find { it.id == id } ?: profiles.first()
    }

    fun getEffectiveRates(profile: StepProfile, customRates: List<Int>): List<Int> {
        if (profile.id == "custom") {
            return customRates.filter { it >= 30 }
        }
        return profile.rates
    }
}
