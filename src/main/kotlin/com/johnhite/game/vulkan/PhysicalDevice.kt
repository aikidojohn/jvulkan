package com.johnhite.game.vulkan

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties
import java.lang.StringBuilder

class PhysicalDevice(val device: VkPhysicalDevice) : AutoCloseable {
    val properties: VkPhysicalDeviceProperties = VkPhysicalDeviceProperties.malloc()
    val features: VkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.malloc()
    val memoryProperties = VkPhysicalDeviceMemoryProperties.malloc()
    val extensions: VkExtensionProperties.Buffer
    val queueFamilies: VkQueueFamilyProperties.Buffer
    val queueSurfaceSupportIndices = ArrayList<Int>()

    init {
        MemoryStack.stackPush().use { stack ->
            val ip = stack.mallocInt(1)
            VK10.vkGetPhysicalDeviceProperties(device, properties)
            VK10.vkGetPhysicalDeviceFeatures(device, features)

            VK10.vkGetPhysicalDeviceQueueFamilyProperties(device, ip, null)
            queueFamilies = VkQueueFamilyProperties.malloc(ip[0])
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(device, ip, queueFamilies)
            check(ip[0] != 0)

            VK10.vkEnumerateDeviceExtensionProperties(device, null as String?, ip, null)
            extensions = VkExtensionProperties.malloc(ip[0])
            VK10.vkEnumerateDeviceExtensionProperties(device, null as String?, ip, extensions)

            vkGetPhysicalDeviceMemoryProperties(device, memoryProperties)
        }
    }

    fun supportsExtensions(requestedExtensions : Set<String>) : Boolean {
        val intersection = this.extensions.stream().map { e -> e.extensionNameString() }.filter { e -> requestedExtensions.contains(e) }.toList()
        if (intersection.size == requestedExtensions.size) {
            return true
        } else {
            val missing = requestedExtensions.subtract(intersection.toSet())
            for (m in missing) {
                println("Missing extension: $m")
            }
        }
        return false
    }

    fun checkSurfaceSupport(surface: Long) {
        MemoryStack.stackPush().use { stack ->
            val supportsPresent = stack.mallocInt(queueFamilies.capacity())
            for (i in 0 until supportsPresent.capacity()) {
                supportsPresent.position(i)
                KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, supportsPresent)
                if (supportsPresent[i] == VK10.VK_TRUE) {
                    queueSurfaceSupportIndices.add(i)
                }
            }
        }
    }

    fun filterQueueFamilies(filter: (q: VkQueueFamilyProperties) -> Boolean) : List<Int> {
        val indices = ArrayList<Int>()
        for (i in 0 until queueFamilies.capacity()) {
            if (filter.invoke(queueFamilies[i])) {
                indices.add(i)
            }
        }
        return indices;
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(properties.deviceNameString())
        sb.append("\n")
        val deviceType = if (properties.deviceType() == VK10.VK_PHYSICAL_DEVICE_TYPE_CPU)
            "CPU"
        else if (properties.deviceType() == VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU)
            "Discrete GPU"
        else if (properties.deviceType() == VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU)
            "Integrated GPU"
        else if (properties.deviceType() == VK10.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU)
            "Virtual GPU"
        else
            "Unknown"
        sb.append("\tDevice Type: $deviceType\n")
        for (i in 0 until queueFamilies.capacity()) {
            sb.append("\tQueue Family:\n")
            sb.append("\t\tGraphics: ${(queueFamilies[i].queueFlags() and VK10.VK_QUEUE_GRAPHICS_BIT) != 0}\n")
            sb.append("\t\tCompute: ${(queueFamilies[i].queueFlags() and VK10.VK_QUEUE_COMPUTE_BIT) != 0}\n")
            sb.append("\t\tTransfer: ${(queueFamilies[i].queueFlags() and VK10.VK_QUEUE_TRANSFER_BIT) != 0}\n")
            sb.append("\t\tSparse Binding: ${(queueFamilies[i].queueFlags() and VK10.VK_QUEUE_SPARSE_BINDING_BIT) != 0}\n")
        }
        return sb.toString()
    }

    override fun close() {
        memoryProperties.free()
        properties.free()
        features.free()
        extensions.free()
        queueFamilies.free()
    }
}