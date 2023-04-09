package com.johnhite.game.vulkan

object Utils {

    fun clampInt(value: Int, min: Int, max: Int) : Int {
        if (value < min) return min
        if (value > max) return max
        return value
    }
}