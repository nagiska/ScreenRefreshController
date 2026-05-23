package com.screenrefresh.controller.root

object Stepper {
    fun getChain(targetDisplayHz: Int): List<Int> = when (targetDisplayHz) {
        120  -> listOf(120)
        132  -> listOf(120, 132)
        144  -> listOf(120, 132, 144)
        156  -> listOf(120, 132, 144, 156)
        165  -> listOf(120, 132, 144, 156, 165)
        else -> listOf(targetDisplayHz)
    }
}
