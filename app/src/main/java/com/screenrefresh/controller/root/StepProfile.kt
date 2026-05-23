package com.screenrefresh.controller.root

data class StepProfile(
    val id: String,
    val name: String,
    val rates: List<Int>
)

object StepProfiles {
    val profiles = listOf(
        StepProfile("standard", "标准 120→132→144", listOf(120, 132, 144)),
        StepProfile("high", "高帧 132→144→156→165", listOf(132, 144, 156, 165)),
        StepProfile("full", "全段 120→132→144→155→165", listOf(120, 132, 144, 155, 165))
    )

    fun getById(id: String): StepProfile {
        return profiles.find { it.id == id } ?: profiles.first()
    }

    fun getAvailableRates(profile: StepProfile, supportedRates: List<Int>): List<Int> {
        return profile.rates.filter { supportedRates.contains(it) }
    }
}
