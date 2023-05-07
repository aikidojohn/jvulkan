package com.johnhite.game.vulkan

import kotlinx.serialization.Serializable

@Serializable
class VulkanContextConfiguration(
    val applicationName: String,
    val validate: Boolean
) {

}