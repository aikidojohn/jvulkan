package com.johnhite.game.vulkan

import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*

class DebugMessenger(private val instance: VkInstance) : AutoCloseable {

    companion object {
        private val dbgFunc: VkDebugUtilsMessengerCallbackEXT =
            VkDebugUtilsMessengerCallbackEXT.create { messageSeverity: Int, messageTypes: Int, pCallbackData: Long, pUserData: Long ->
                val severity: String =
                    if (messageSeverity and EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT != 0) {
                        "VERBOSE"
                    } else if (messageSeverity and EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT != 0) {
                        "INFO"
                    } else if (messageSeverity and EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT != 0) {
                        "WARNING"
                    } else if (messageSeverity and EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT != 0) {
                        "ERROR"
                    } else {
                        "UNKNOWN"
                    }
                val type: String =
                    if (messageTypes and EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT != 0) {
                        "GENERAL"
                    } else if (messageTypes and EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT != 0) {
                        "VALIDATION"
                    } else if (messageTypes and EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT != 0) {
                        "PERFORMANCE"
                    } else {
                        "UNKNOWN"
                    }
                val data = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData)
                System.err.format(
                    "%s %s: [%s]\n\t%s\n",
                    type, severity, data.pMessageIdNameString(), data.pMessageString()
                )
                VK10.VK_FALSE
            }

        fun getDebugUtilsCreateInfo() : VkDebugUtilsMessengerCreateInfoEXT {
            MemoryStack.stackPush().use { stack ->
                return VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                    .`sType$Default`()
                    .pNext(MemoryUtil.NULL)
                    .flags(0)
                    .messageSeverity( /*VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT |
                        VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT |*/
                        EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT or
                                EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
                    )
                    .messageType(
                        EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or
                                EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or
                                EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
                    )
                    .pfnUserCallback(dbgFunc)
                    .pUserData(MemoryUtil.NULL)
            }
        }
    }

    val msgCallback: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val lp = stack.mallocLong(1)
            val err = EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(instance, getDebugUtilsCreateInfo(), null, lp)
            msgCallback = when (err) {
                VK10.VK_SUCCESS -> lp[0]
                VK10.VK_ERROR_OUT_OF_HOST_MEMORY -> throw IllegalStateException("CreateDebugReportCallback: out of host memory")
                else -> throw IllegalStateException("CreateDebugReportCallback: unknown failure")
            }
        }
    }

    fun destroy() {
        EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(instance, msgCallback, null)
        dbgFunc.free()
    }

    override fun close() {
        EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(instance, msgCallback, null)
        dbgFunc.free()
    }
}