package com.johnhite.game.vulkan

import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.vkDestroyInstance

class VulkanContext(private val config: VulkanContextConfiguration) : AutoCloseable {

    val instance: VkInstance
    val physicalDevices: List<PhysicalDevice>
    private var debugMessenger: DebugMessenger? = null
    private val extensionNames = MemoryUtil.memAllocPointer(64)
    private val debugUtilExtensionName = MemoryUtil.memASCII(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME)
    private val portabilityExtensionName = MemoryUtil.memASCII(KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME)


    init {
        MemoryStack.stackPush().use { stack ->

            var requiredLayers: PointerBuffer? = getRequiredValidationLayers(stack)
            val requiredExtensions = getRequiredExtensions(stack)

            val version = VK.getInstanceVersionSupported()
            println("Supported Vulkan Version: ${VK10.VK_API_VERSION_MAJOR(version)}.${
                VK10.VK_API_VERSION_MINOR(
                    version
                )
            }.${VK10.VK_API_VERSION_PATCH(version)}.${VK10.VK_API_VERSION_VARIANT(version)}")

            val appName = stack.UTF8(config.applicationName)
            val app = VkApplicationInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .pApplicationName(appName)
                .applicationVersion(0)
                .pEngineName(appName)
                .engineVersion(0)
                .apiVersion(VK.getInstanceVersionSupported())

            val flags = if (OS.isOSX) KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR else 0
            val instanceCreateInfo = VkInstanceCreateInfo.calloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .flags(flags)
                .pApplicationInfo(app)
                .ppEnabledLayerNames(requiredLayers)
                .ppEnabledExtensionNames(requiredExtensions)
            if (config.validate) {
                instanceCreateInfo.pNext(DebugMessenger.getDebugUtilsCreateInfo().address())
            }
            val pp = stack.mallocPointer(1)
            var err = VK10.vkCreateInstance(instanceCreateInfo, null, pp)
            check(err != VK10.VK_ERROR_INCOMPATIBLE_DRIVER) { "Cannot find a compatible Vulkan installable client driver (ICD)." }
            check(err != VK10.VK_ERROR_EXTENSION_NOT_PRESENT) { "Cannot find a specified extension library. Make sure your layers path is set appropriately." }
            check(err == 0) { "vkCreateInstance failed. Do you have a compatible Vulkan installable client driver (ICD) installed?" }
            this.instance = VkInstance(pp[0], instanceCreateInfo)
            if (config.validate) {
                this.debugMessenger = DebugMessenger(instance)
            }

            val devices = listPhysicalDevices()
            this.physicalDevices = devices.toList()


            MemoryUtil.memFree(debugUtilExtensionName)
        }
    }

    private fun listPhysicalDevices() : List<PhysicalDevice> {
        val deviceList = ArrayList<PhysicalDevice>()
        MemoryStack.stackPush().use { stack ->
            val ip = stack.mallocInt(1)
            checkVk(VK10.vkEnumeratePhysicalDevices(instance, ip, null))
            if (ip[0] > 0) {
                val devices = stack.mallocPointer(ip[0])
                checkVk(VK10.vkEnumeratePhysicalDevices(instance, ip, devices))
                for (i in 0 until ip[0]) {
                    deviceList.add(PhysicalDevice(VkPhysicalDevice(devices[i], instance)))
                }
            } else {
                throw IllegalStateException("vkEnumeratePhysicalDevices reported zero accessible devices.")
            }
        }
        return deviceList
    }

    private fun checkLayers(stack: MemoryStack, available: VkLayerProperties.Buffer, vararg layers: String) : PointerBuffer? {
        val required = stack.mallocPointer(layers.size)
        for (i in layers.indices) {
            var found = false
            for (j in 0 until available.capacity()) {
                available.position(j)
                if (layers[i] == available.layerNameString()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.err.format("Cannot find layer: %s\n", layers[i]);
                return null;
            }

            required.put(i, stack.ASCII(layers[i]));
        }
        return required
    }

    private fun getRequiredValidationLayers(stack: MemoryStack) : PointerBuffer {
        var requiredLayers: PointerBuffer? = null
        val ip = stack.mallocInt(1)
        checkVk(VK10.vkEnumerateInstanceLayerProperties(ip, null))
        if (ip[0] > 0) {
            val availableLayers = VkLayerProperties.malloc(ip[0], stack)
            checkVk(VK10.vkEnumerateInstanceLayerProperties(ip, availableLayers))

            // VulkanSDK 1.1.106+
            requiredLayers = checkLayers(
                stack, availableLayers,
                "VK_LAYER_KHRONOS_validation" /*,
                        "VK_LAYER_LUNARG_assistant_layer"*/
            )
            if (requiredLayers == null) { // use alternative (deprecated) set of validation layers
                requiredLayers = checkLayers(
                    stack, availableLayers,
                    "VK_LAYER_LUNARG_standard_validation" /*,
                            "VK_LAYER_LUNARG_assistant_layer"*/
                )
            }
            if (requiredLayers == null) { // use alternative (deprecated) set of validation layers
                requiredLayers = checkLayers(
                    stack, availableLayers,
                    "VK_LAYER_GOOGLE_threading",
                    "VK_LAYER_LUNARG_parameter_validation",
                    "VK_LAYER_LUNARG_object_tracker",
                    "VK_LAYER_LUNARG_core_validation",
                    "VK_LAYER_GOOGLE_unique_objects" /*,
                            "VK_LAYER_LUNARG_assistant_layer"*/
                )
            }
        }
        checkNotNull(requiredLayers) { "vkEnumerateInstanceLayerProperties failed to find required validation layer." }
        return requiredLayers
    }

    private fun getRequiredExtensions(stack: MemoryStack) : PointerBuffer {
        val requiredExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions()
            ?: throw IllegalStateException("glfwGetRequiredInstanceExtensions failed to find the platform surface extensions.")

        for (i in 0 until requiredExtensions.capacity()) {
            extensionNames.put(requiredExtensions[i])
        }
        //MoltenVK requires the VK_HKR_PORTABILITY_subset extension
        if (OS.isOSX) {
            extensionNames.put(portabilityExtensionName)
        }
        //Add debug extensions if validation layers enabled
        if (config.validate) {
            val ip = stack.mallocInt(1)
            checkVk(VK10.vkEnumerateInstanceExtensionProperties(null as String?, ip, null))
            if (ip[0] != 0) {
                val instanceExtensions = VkExtensionProperties.malloc(ip[0], stack)
                checkVk(VK10.vkEnumerateInstanceExtensionProperties(null as String?, ip, instanceExtensions))

                for (i in 0 until ip[0]) {
                    instanceExtensions.position(i)
                    if (EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME == instanceExtensions.extensionNameString()) {
                        extensionNames.put(debugUtilExtensionName)
                    }
                }
            }
        }
        extensionNames.flip()
        return extensionNames
    }

    override fun close() {
        for (d in physicalDevices) {
            d.close()
        }

        if (config.validate) {
            debugMessenger?.close()
        }
        vkDestroyInstance(instance, null)
    }
}