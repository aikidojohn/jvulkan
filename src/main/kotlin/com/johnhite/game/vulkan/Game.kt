package com.johnhite.game.vulkan

import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
import org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME
import org.lwjgl.vulkan.KHRSurface.*
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK10.*
import java.lang.StringBuilder
import java.nio.ByteBuffer
import java.nio.IntBuffer

fun checkVk(errcode: Int) {
    if (errcode != 0) {
        throw IllegalStateException(String.format("Vulkan error [0x%X]", errcode));
    }
}

class Game {

    private val validate: Boolean = true

    private var window: Long = -1L
    private var surface: Long = -1L
    private lateinit var instance: VkInstance
    private var debugMessenger: DebugMessenger? = null
    private lateinit var devices: List<PhysicalDevice>
    private lateinit var gpu: PhysicalDevice
    private lateinit var device: LogicalDevice
    private lateinit var swapchain: SwapChain

    private val extensionNames = MemoryUtil.memAllocPointer(64)
    private val debugUtilExtensionName = MemoryUtil.memASCII(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME)
    private val portabilityExtensionName = MemoryUtil.memASCII(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME)

    /*private fun checkVk(errcode: Int) {
        if (errcode != 0) {
            throw IllegalStateException(String.format("Vulkan error [0x%X]", errcode));
        }
    }*/

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
        if (validate) {
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

    private fun getRequiredValidationLayers(stack: MemoryStack) : PointerBuffer {
        var requiredLayers: PointerBuffer? = null
        val ip = stack.mallocInt(1)
        checkVk(vkEnumerateInstanceLayerProperties(ip, null))
        if (ip[0] > 0) {
            val availableLayers = VkLayerProperties.malloc(ip[0], stack)
            checkVk(vkEnumerateInstanceLayerProperties(ip, availableLayers))

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

    private fun getPhysicalDevices() : List<PhysicalDevice> {
        val deviceList = ArrayList<PhysicalDevice>()
        MemoryStack.stackPush().use { stack ->
            val ip = stack.mallocInt(1)
            checkVk(vkEnumeratePhysicalDevices(instance, ip, null))
            if (ip[0] > 0) {
                val devices = stack.mallocPointer(ip[0])
                checkVk(vkEnumeratePhysicalDevices(instance, ip, devices))
                for (i in 0 until ip[0]) {
                    deviceList.add(PhysicalDevice(VkPhysicalDevice(devices[i], instance)))
                }
            } else {
                throw IllegalStateException("vkEnumeratePhysicalDevices reported zero accessible devices.")
            }
        }
        return deviceList
    }

    private fun selectPhysicalDevice(devices: List<PhysicalDevice>) : PhysicalDevice {
        var highestRank = -1
        var bestDevice = devices[0]
        val requiredExtensions = setOf<String>(VK_KHR_SWAPCHAIN_EXTENSION_NAME)

        for (device in devices) {
            var rank = 0
            device.checkSurfaceSupport(surface)
            if (device.queueSurfaceSupportIndices.isNotEmpty()) {
                rank += 1000
            }
            //check for required extensions
            if (device.supportsExtensions(requiredExtensions)) {
                rank += 1000
            }

            //Check device type
            if (device.properties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
                rank += 1000
            }

            device.filterQueueFamilies { q ->
                if ((q.queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0) {
                    rank += 100
                }
                if ((q.queueFlags() and VK_QUEUE_COMPUTE_BIT) != 0) {
                    rank += 10
                }
                if ((q.queueFlags() and VK_QUEUE_TRANSFER_BIT) != 0) {
                    rank += 10
                }
                false
            }
            if (rank > highestRank) {
                highestRank = rank
                bestDevice = device
            }
        }
        return bestDevice
    }


    private fun initVkInstance() : VkInstance {
        MemoryStack.stackPush().use { stack ->

            var requiredLayers: PointerBuffer? = getRequiredValidationLayers(stack)
            val requiredExtensions = getRequiredExtensions(stack)

            val version = VK.getInstanceVersionSupported()
            println("Supported Vulkan Version: ${VK_API_VERSION_MAJOR(version)}.${VK_API_VERSION_MINOR(version)}.${VK_API_VERSION_PATCH(version)}.${VK_API_VERSION_VARIANT(version)}")
            val appName = stack.UTF8("tri")
            val app = VkApplicationInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .pApplicationName(appName)
                .applicationVersion(0)
                .pEngineName(appName)
                .engineVersion(0)
                .apiVersion(VK.getInstanceVersionSupported())

            val flags = if (OS.isOSX) VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR else 0
            val instanceCreateInfo = VkInstanceCreateInfo.calloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .flags(flags)
                .pApplicationInfo(app)
                .ppEnabledLayerNames(requiredLayers)
                .ppEnabledExtensionNames(requiredExtensions)
            if (validate) {
                instanceCreateInfo.pNext(DebugMessenger.getDebugUtilsCreateInfo().address())
            }
            val pp = stack.mallocPointer(1)
            var err = vkCreateInstance(instanceCreateInfo, null, pp)
            check(err != VK10.VK_ERROR_INCOMPATIBLE_DRIVER) { "Cannot find a compatible Vulkan installable client driver (ICD)." }
            check(err != VK10.VK_ERROR_EXTENSION_NOT_PRESENT) { "Cannot find a specified extension library. Make sure your layers path is set appropriately." }
            check(err == 0) { "vkCreateInstance failed. Do you have a compatible Vulkan installable client driver (ICD) installed?" }
            this.instance = VkInstance(pp[0], instanceCreateInfo)

            MemoryUtil.memFree(debugUtilExtensionName)
            return this.instance
        }
    }

    private fun initDebugMessenger() {
        debugMessenger = DebugMessenger(instance)
    }

    private fun initWindow(width: Int, height: Int, title: String) {
        GLFWErrorCallback.createPrint().set();
        if (!glfwInit()) {
            throw IllegalStateException("Unable to initialize GLFW")
        }

        if (!glfwVulkanSupported()) {
            throw IllegalStateException("Cannot find a compatible Vulkan installable client driver")
        }

        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)
        window = glfwCreateWindow(width, height, title, 0, 0)
    }

    private fun initSurface() {
        MemoryStack.stackPush().use {stack ->
            val lp = stack.mallocLong(1)
            GLFWVulkan.glfwCreateWindowSurface(instance, window, null, lp)
            surface = lp[0]
        }
    }

    fun run() {
        try {
            initWindow(1024, 768, "Vulkan")
            initVkInstance()
            initSurface()
            initDebugMessenger()
            devices = getPhysicalDevices()
            for (device in devices) {
                println(device)
            }
            gpu = selectPhysicalDevice(devices)
            device = LogicalDevice(gpu)
            swapchain = SwapChain(device, surface, window)

            while (!glfwWindowShouldClose(window)) {
                glfwPollEvents()

            }
        }
        finally {
            destroy()
        }
    }

    private fun destroy() {
        if (this::swapchain.isInitialized) {
            swapchain.close()
        }
        if (this::device.isInitialized) {
            device.close()
        }
        if (this::devices.isInitialized) {
            for (d in devices) {
                d.close()
            }
        }

        vkDestroySurfaceKHR(instance, surface, null)
        if (validate) {
            debugMessenger?.destroy()
        }
        if (this::instance.isInitialized) {
            vkDestroyInstance(instance, null)
        }
        glfwDestroyWindow(window)
        glfwTerminate()
    }
}

class SwapChain(private val device: LogicalDevice, surface: Long, window: Long) : AutoCloseable {
    val capabilities = VkSurfaceCapabilitiesKHR.malloc()
    val formats: VkSurfaceFormatKHR.Buffer
    val presentModes: IntBuffer
    val format: VkSurfaceFormatKHR
    val presentMode: Int
    val extent = VkExtent2D.malloc()
    val swapchain: Long
    val images = ArrayList<Long>()

    init {
        MemoryStack.stackPush().use { stack ->
            val physical = device.device.physicalDevice
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physical, surface, capabilities)

            val ip = stack.mallocInt(1)
            vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, ip, null)

            if (ip[0] > 0) {
                formats = VkSurfaceFormatKHR.malloc(ip[0])
                vkGetPhysicalDeviceSurfaceFormatsKHR(physical, surface, ip, formats)
            } else {
                throw RuntimeException("Device does not support any surface formats!")
            }

            vkGetPhysicalDeviceSurfacePresentModesKHR(physical, surface, ip, null as IntBuffer?)
            if (ip[0] > 0) {
                presentModes = MemoryUtil.memAllocInt(ip[0])
                vkGetPhysicalDeviceSurfacePresentModesKHR(physical, surface, ip, presentModes)
            } else {
                throw RuntimeException("Device does not support any present modes!")
            }

            //Select format
            format = formats.stream().filter { f -> f.format() == VK_FORMAT_B8G8R8A8_SRGB && f.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR }
                .findFirst()
                .orElse(formats[0])

            //Select present mode
            var pm = VK_PRESENT_MODE_FIFO_KHR
            for (i in 0 until presentModes.capacity()) {
                if (presentModes[i] == VK_PRESENT_MODE_MAILBOX_KHR) {
                     pm = VK_PRESENT_MODE_MAILBOX_KHR
                    break;
                }
            }
            presentMode = pm

            //Select swap extent
            if (capabilities.currentExtent().width() >= Int.MAX_VALUE || capabilities.currentExtent().width() < 0) {
                //High Density Display
                val width = stack.mallocInt(1)
                val height = stack.mallocInt(1)
                glfwGetFramebufferSize(window, width, height)
                extent.set(
                    Utils.clampInt(width[0], capabilities.minImageExtent().width(), capabilities.maxImageExtent().width()),
                    Utils.clampInt(height[0], capabilities.minImageExtent().height(), capabilities.maxImageExtent().height())
                )
            } else {
                extent.set(capabilities.currentExtent().width(), capabilities.currentExtent().height())
            }

            val imageCount = if (capabilities.maxImageCount() == 0 || capabilities.minImageCount() + 1 <= capabilities.maxImageCount()) capabilities.minImageCount() + 1 else capabilities.minImageCount()
            //Create Swapchain
            val swapChainCreateInfo = VkSwapchainCreateInfoKHR.calloc(stack)
            swapChainCreateInfo
                .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surface)
                .minImageCount(imageCount)
                .imageFormat(format.format())
                .imageColorSpace(format.colorSpace())
                .imageExtent(extent)
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .preTransform(capabilities.currentTransform())
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(presentMode)
                .clipped(true)
                .oldSwapchain(VK_NULL_HANDLE)

            if (device.queueFamilyIndices.size > 1) {
                val queueFamilies = stack.mallocInt(device.queueFamilyIndices.size)
                for (family in device.queueFamilyIndices) {
                    queueFamilies.put(family)
                }
                queueFamilies.rewind()

                swapChainCreateInfo
                    .imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                    .queueFamilyIndexCount(device.queueFamilyIndices.size)
                    .pQueueFamilyIndices(queueFamilies)
            }
            else {
                swapChainCreateInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
            }

            //Create swap chain
            val lp = stack.mallocLong(1)
            checkVk(vkCreateSwapchainKHR(device.device, swapChainCreateInfo, null, lp))
            swapchain = lp[0]

            //Get swapchain images
            vkGetSwapchainImagesKHR(device.device, swapchain, ip, null)
            val imagePointers = stack.mallocLong(ip[0])
            vkGetSwapchainImagesKHR(device.device, swapchain, ip, imagePointers)
            for (i in 0 until imagePointers.capacity()) {
                images.add(imagePointers[i])
            }
        }
    }


    override fun close() {
        vkDestroySwapchainKHR(device.device, swapchain, null)
        capabilities.free()
        formats.free()
        MemoryUtil.memFree(presentModes)
        extent.free()
    }
}

class LogicalDevice(physical: PhysicalDevice) : AutoCloseable {
    val graphicsQueue: VkQueue
    val presentQueue: VkQueue
    val device: VkDevice
    val presentEqualsGraphics: Boolean
    val queueFamilyIndices: Set<Int>

    init {
        //TODO queues should be a constructor parameter
        val graphicsQueues = physical.filterQueueFamilies { q ->
            (q.queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0
        }

        val presentQueueIndex = physical.queueSurfaceSupportIndices[0]
        val graphicsQueueIndex = graphicsQueues[0]
        val queues = setOf(presentQueueIndex, graphicsQueueIndex)
        presentEqualsGraphics = queues.size == 1
        queueFamilyIndices = queues

        MemoryStack.stackPush().use { stack ->
            val queueCreateInfo = VkDeviceQueueCreateInfo.calloc(queues.size, stack)
            val priority = stack.mallocFloat(1)
            priority.put(1.0f)
            priority.rewind()

            for ((i, qIndex) in queues.withIndex()) {
                queueCreateInfo[i].sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                queueCreateInfo[i].queueFamilyIndex(qIndex)
                queueCreateInfo[i].pQueuePriorities(priority)
                queueCreateInfo[i].pNext(MemoryUtil.NULL)
                queueCreateInfo[i].flags(0)
            }

            //TODO extension list should be a constructor parameter
            val enabledExtensions = stack.mallocPointer(1)
            enabledExtensions.put(stack.ASCII(VK_KHR_SWAPCHAIN_EXTENSION_NAME))
            enabledExtensions.rewind()

            val deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
            deviceCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
            deviceCreateInfo.pQueueCreateInfos(queueCreateInfo)
            deviceCreateInfo.pEnabledFeatures(physical.features)
            deviceCreateInfo.ppEnabledExtensionNames(enabledExtensions)
            deviceCreateInfo.flags(0)
            deviceCreateInfo.pNext(MemoryUtil.NULL)


            val pp = stack.mallocPointer(1)
            checkVk(vkCreateDevice(physical.device, deviceCreateInfo,null,pp))
            device = VkDevice(pp[0], physical.device, deviceCreateInfo)

            vkGetDeviceQueue(device, presentQueueIndex, 0, pp)
            presentQueue = VkQueue(pp[0], device)

            vkGetDeviceQueue(device, graphicsQueueIndex, 0, pp)
            graphicsQueue = VkQueue(pp[0], device)
        }
    }

    override fun close() {
        vkDestroyDevice(device, null)
    }
}

class PhysicalDevice(val device: VkPhysicalDevice) : AutoCloseable {
    val properties: VkPhysicalDeviceProperties = VkPhysicalDeviceProperties.malloc()
    val features: VkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.malloc()
    val extensions: VkExtensionProperties.Buffer
    val queueFamilies: VkQueueFamilyProperties.Buffer
    val queueSurfaceSupportIndices = ArrayList<Int>()

    init {
        MemoryStack.stackPush().use { stack ->
            val ip = stack.mallocInt(1)
            vkGetPhysicalDeviceProperties(device, properties)
            vkGetPhysicalDeviceFeatures(device, features)

            vkGetPhysicalDeviceQueueFamilyProperties(device, ip, null)
            queueFamilies = VkQueueFamilyProperties.malloc(ip[0])
            vkGetPhysicalDeviceQueueFamilyProperties(device, ip, queueFamilies)
            check(ip[0] != 0)

            vkEnumerateDeviceExtensionProperties(device, null as String?, ip, null)
            extensions = VkExtensionProperties.malloc(ip[0])
            vkEnumerateDeviceExtensionProperties(device, null as String?, ip, extensions)
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
                if (supportsPresent[i] == VK_TRUE) {
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
        val deviceType = if (properties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_CPU)
            "CPU"
        else if (properties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU)
            "Discrete GPU"
        else if (properties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU)
            "Integrated GPU"
        else if (properties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU)
            "Virtual GPU"
        else
            "Unknown"
        sb.append("\tDevice Type: $deviceType\n")
        for (i in 0 until queueFamilies.capacity()) {
            sb.append("\tQueue Family:\n")
            sb.append("\t\tGraphics: ${(queueFamilies[i].queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0}\n")
            sb.append("\t\tCompute: ${(queueFamilies[i].queueFlags() and VK_QUEUE_COMPUTE_BIT) != 0}\n")
            sb.append("\t\tTransfer: ${(queueFamilies[i].queueFlags() and VK_QUEUE_TRANSFER_BIT) != 0}\n")
            sb.append("\t\tSparse Binding: ${(queueFamilies[i].queueFlags() and VK_QUEUE_SPARSE_BINDING_BIT) != 0}\n")
        }
        return sb.toString()
    }

    override fun close() {
        properties.free()
        features.free()
        extensions.free()
        queueFamilies.free()
    }
}

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
                VK_FALSE
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
                VK_SUCCESS -> lp[0]
                VK_ERROR_OUT_OF_HOST_MEMORY -> throw IllegalStateException("CreateDebugReportCallback: out of host memory")
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


fun main(args: Array<String>) {
    val game = Game()
    game.run()
}