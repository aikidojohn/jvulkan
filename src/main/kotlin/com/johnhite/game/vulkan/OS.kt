package com.johnhite.game.vulkan

object OS {
    val name = System.getProperty("os.name")
    val isWindows = name.lowercase().contains("windows")
    val isOSX = name.lowercase().contains("mac")
    val isLinux = !(isWindows || isOSX)
}