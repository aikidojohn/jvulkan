package com.johnhite.game.vulkan

import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.Callbacks
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import java.nio.LongBuffer
import java.util.*


/*
 * Copyright (c) 2015-2016 The Khronos Group Inc.
 * Copyright (c) 2015-2016 Valve Corporation
 * Copyright (c) 2015-2016 LunarG, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Chia-I Wu <olvaffe@gmail.com>
 * Author: Cody Northrop <cody@lunarg.com>
 * Author: Courtney Goeltzenleuchter <courtney@LunarG.com>
 * Author: Ian Elliott <ian@LunarG.com>
 * Author: Jon Ashburn <jon@lunarg.com>
 * Author: Piers Daniell <pdaniell@nvidia.com>
 * Author: Gwan-gyeong Mun <elongbug@gmail.com>
 * Porter: Camilla Berglund <elmindreda@glfw.org>
 */

/*
 * Copyright (c) 2015-2016 The Khronos Group Inc.
 * Copyright (c) 2015-2016 Valve Corporation
 * Copyright (c) 2015-2016 LunarG, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Chia-I Wu <olvaffe@gmail.com>
 * Author: Cody Northrop <cody@lunarg.com>
 * Author: Courtney Goeltzenleuchter <courtney@LunarG.com>
 * Author: Ian Elliott <ian@LunarG.com>
 * Author: Jon Ashburn <jon@lunarg.com>
 * Author: Piers Daniell <pdaniell@nvidia.com>
 * Author: Gwan-gyeong Mun <elongbug@gmail.com>
 * Porter: Camilla Berglund <elmindreda@glfw.org>
 */
/** Simple Vulkan demo. Ported from the GLFW [vulkan](https://github.com/glfw/glfw/blob/master/tests/vulkan.c) test.  */
class HelloVulkan private constructor() {
    // buffers for handle output-params
    private val ip = MemoryUtil.memAllocInt(1)
    private val lp = MemoryUtil.memAllocLong(1)
    private val pp = MemoryUtil.memAllocPointer(1)
    private val extension_names = MemoryUtil.memAllocPointer(64)
    private var inst: VkInstance? = null
    private var gpu: VkPhysicalDevice? = null
    private var msg_callback: Long = 0
    private val gpu_props = VkPhysicalDeviceProperties.malloc()
    private val gpu_features = VkPhysicalDeviceFeatures.malloc()
    private var queue_props: VkQueueFamilyProperties.Buffer? = null
    private var width = 300
    private var height = 300
    private var depthStencil = 1.0f
    private var depthIncrement = -0.01f
    private var window: Long = 0
    private var surface: Long = 0
    private var graphics_queue_node_index = 0
    private var device: VkDevice? = null
    private var queue: VkQueue? = null
    private var format = 0
    private var color_space = 0
    private val memory_properties = VkPhysicalDeviceMemoryProperties.malloc()
    private var cmd_pool: Long = 0
    private var draw_cmd: VkCommandBuffer? = null
    private var swapchain: Long = 0
    private var swapchainImageCount = 0
    private var buffers: Array<SwapchainBuffers?>? = arrayOfNulls(1)
    private var current_buffer = 0
    private var setup_cmd: VkCommandBuffer? = null
    private val depth = Depth()
    private val textures = arrayOfNulls<TextureObject>(DEMO_TEXTURE_COUNT)
    private val vertices = Vertices()
    private var desc_layout: Long = 0
    private var pipeline_layout: Long = 0
    private var render_pass: Long = 0
    private var pipeline: Long = 0
    private var desc_pool: Long = 0
    private var desc_set: Long = 0
    private var framebuffers: LongBuffer? = null
    private val dbgFunc =
        VkDebugUtilsMessengerCallbackEXT.create { messageSeverity: Int, messageTypes: Int, pCallbackData: Long, pUserData: Long ->
            val severity: String = if (messageSeverity and EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT != 0) {
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
            val type: String = if (messageTypes and EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT != 0) {
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

    init {
        for (i in textures.indices) {
            textures[i] = TextureObject()
        }
    }

    private fun demo_init_vk() {
        MemoryStack.stackPush().use { stack ->
            var requiredLayers: PointerBuffer? = null
            if (VALIDATE) {
                check(VK10.vkEnumerateInstanceLayerProperties(ip, null))
                if (ip[0] > 0) {
                    val availableLayers = VkLayerProperties.malloc(ip[0], stack)
                    check(VK10.vkEnumerateInstanceLayerProperties(ip, availableLayers))

                    // VulkanSDK 1.1.106+
                    requiredLayers = demo_check_layers(
                        stack, availableLayers,
                        "VK_LAYER_KHRONOS_validation" /*,
                        "VK_LAYER_LUNARG_assistant_layer"*/
                    )
                    if (requiredLayers == null) { // use alternative (deprecated) set of validation layers
                        requiredLayers = demo_check_layers(
                            stack, availableLayers,
                            "VK_LAYER_LUNARG_standard_validation" /*,
                            "VK_LAYER_LUNARG_assistant_layer"*/
                        )
                    }
                    if (requiredLayers == null) { // use alternative (deprecated) set of validation layers
                        requiredLayers = demo_check_layers(
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
            }
            val required_extensions = GLFWVulkan.glfwGetRequiredInstanceExtensions()
                ?: throw IllegalStateException("glfwGetRequiredInstanceExtensions failed to find the platform surface extensions.")
            for (i in 0 until required_extensions.capacity()) {
                extension_names.put(required_extensions[i])
            }
            check(VK10.vkEnumerateInstanceExtensionProperties(null as String?, ip, null))
            if (ip[0] != 0) {
                val instance_extensions = VkExtensionProperties.malloc(ip[0], stack)
                check(
                    VK10.vkEnumerateInstanceExtensionProperties(
                        null as String?,
                        ip,
                        instance_extensions
                    )
                )
                for (i in 0 until ip[0]) {
                    instance_extensions.position(i)
                    if (EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME == instance_extensions.extensionNameString()) {
                        if (VALIDATE) {
                            extension_names.put(EXT_debug_utils)
                        }
                    }
                }
            }
            val APP_SHORT_NAME = stack.UTF8("tri")
            val app = VkApplicationInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .pApplicationName(APP_SHORT_NAME)
                .applicationVersion(0)
                .pEngineName(APP_SHORT_NAME)
                .engineVersion(0)
                .apiVersion(VK.getInstanceVersionSupported())
            extension_names.flip()
            val inst_info = VkInstanceCreateInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .flags(0)
                .pApplicationInfo(app)
                .ppEnabledLayerNames(requiredLayers)
                .ppEnabledExtensionNames(extension_names)
            extension_names.clear()
            val dbgCreateInfo: VkDebugUtilsMessengerCreateInfoEXT
            //if (VALIDATE) {
                dbgCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.malloc(stack)
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
                inst_info.pNext(dbgCreateInfo.address())
            //}
            var err = VK10.vkCreateInstance(inst_info, null, pp)
            check(err != VK10.VK_ERROR_INCOMPATIBLE_DRIVER) { "Cannot find a compatible Vulkan installable client driver (ICD)." }
            check(err != VK10.VK_ERROR_EXTENSION_NOT_PRESENT) { "Cannot find a specified extension library. Make sure your layers path is set appropriately." }
            check(err == 0) { "vkCreateInstance failed. Do you have a compatible Vulkan installable client driver (ICD) installed?" }
            inst = VkInstance(pp[0], inst_info)

            /* Make initial call to query gpu_count, then second call for gpu info */
            check(VK10.vkEnumeratePhysicalDevices(inst, ip, null))
            gpu = if (ip[0] > 0) {
                val physical_devices = stack.mallocPointer(ip[0])
                check(VK10.vkEnumeratePhysicalDevices(inst, ip, physical_devices))

                /* For tri demo we just grab the first physical device */
                VkPhysicalDevice(physical_devices[0], inst)
            } else {
                throw IllegalStateException("vkEnumeratePhysicalDevices reported zero accessible devices.")
            }

            /* Look for device extensions */
            var swapchainExtFound = false
            check(
                VK10.vkEnumerateDeviceExtensionProperties(
                    gpu,
                    null as String?,
                    ip,
                    null
                )
            )
            if (ip[0] > 0) {
                val device_extensions = VkExtensionProperties.malloc(ip[0], stack)
                check(
                    VK10.vkEnumerateDeviceExtensionProperties(
                        gpu,
                        null as String?,
                        ip,
                        device_extensions
                    )
                )
                for (i in 0 until ip[0]) {
                    device_extensions.position(i)
                    if (KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME == device_extensions.extensionNameString()) {
                        swapchainExtFound = true
                        extension_names.put(KHR_swapchain)
                    }
                }
            }
            check(swapchainExtFound) { "vkEnumerateDeviceExtensionProperties failed to find the " + KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME + " extension." }
            if (VALIDATE) {
                err = EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(inst, dbgCreateInfo, null, lp)
                msg_callback = when (err) {
                    VK10.VK_SUCCESS -> lp[0]
                    VK10.VK_ERROR_OUT_OF_HOST_MEMORY -> throw IllegalStateException("CreateDebugReportCallback: out of host memory")
                    else -> throw IllegalStateException("CreateDebugReportCallback: unknown failure")
                }
            }
            VK10.vkGetPhysicalDeviceProperties(gpu, gpu_props)

            // Query with NULL data to get count
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(gpu, ip, null)
            queue_props = VkQueueFamilyProperties.malloc(ip[0])
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(gpu, ip, queue_props)
            check(ip[0] != 0)
            VK10.vkGetPhysicalDeviceFeatures(gpu, gpu_features)
        }
    }

    private fun demo_init() {
        demo_init_connection()
        demo_init_vk()
    }

    private fun demo_create_window() {
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API)
        window =
            GLFW.glfwCreateWindow(width, height, "The Vulkan Triangle Demo Program", MemoryUtil.NULL, MemoryUtil.NULL)
        check(window != MemoryUtil.NULL) { "Cannot create a window in which to draw!" }
        GLFW.glfwSetWindowRefreshCallback(window) { window: Long -> demo_draw() }
        GLFW.glfwSetFramebufferSizeCallback(window) { window: Long, width: Int, height: Int ->
            this.width = width
            this.height = height
            if (width != 0 && height != 0) {
                demo_resize()
            }
        }
        GLFW.glfwSetKeyCallback(
            window
        ) { window: Long, key: Int, scancode: Int, action: Int, mods: Int ->
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE) {
                GLFW.glfwSetWindowShouldClose(window, true)
            }
        }
    }

    private fun demo_init_device() {
        MemoryStack.stackPush().use { stack ->
            val queue = VkDeviceQueueCreateInfo.malloc(1, stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .flags(0)
                .queueFamilyIndex(graphics_queue_node_index)
                .pQueuePriorities(stack.floats(0.0f))
            val features = VkPhysicalDeviceFeatures.calloc(stack)
            if (gpu_features.shaderClipDistance()) {
                features.shaderClipDistance(true)
            }
            extension_names.flip()
            val device = VkDeviceCreateInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .flags(0)
                .pQueueCreateInfos(queue)
                .ppEnabledLayerNames(null)
                .ppEnabledExtensionNames(extension_names)
                .pEnabledFeatures(features)
            check(VK10.vkCreateDevice(gpu, device, null, pp))
            this.device = VkDevice(pp[0], gpu, device)
        }
    }

    private fun demo_init_vk_swapchain() {
        // Create a WSI surface for the window:
        GLFWVulkan.glfwCreateWindowSurface(inst, window, null, lp)
        surface = lp[0]
        MemoryStack.stackPush().use { stack ->
            // Iterate over each queue to learn whether it supports presenting:
            val supportsPresent = stack.mallocInt(queue_props!!.capacity())
            var graphicsQueueNodeIndex: Int
            var presentQueueNodeIndex: Int
            for (i in 0 until supportsPresent.capacity()) {
                supportsPresent.position(i)
                KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(gpu, i, surface, supportsPresent)
            }

            // Search for a graphics and a present queue in the array of queue
            // families, try to find one that supports both
            graphicsQueueNodeIndex = Int.MAX_VALUE
            presentQueueNodeIndex = Int.MAX_VALUE
            for (i in 0 until supportsPresent.capacity()) {
                if (queue_props!![i].queueFlags() and VK10.VK_QUEUE_GRAPHICS_BIT != 0) {
                    if (graphicsQueueNodeIndex == Int.MAX_VALUE) {
                        graphicsQueueNodeIndex = i
                    }
                    if (supportsPresent[i] == VK10.VK_TRUE) {
                        graphicsQueueNodeIndex = i
                        presentQueueNodeIndex = i
                        break
                    }
                }
            }
            if (presentQueueNodeIndex == Int.MAX_VALUE) {
                // If didn't find a queue that supports both graphics and present, then
                // find a separate present queue.
                for (i in 0 until supportsPresent.capacity()) {
                    if (supportsPresent[i] == VK10.VK_TRUE) {
                        presentQueueNodeIndex = i
                        break
                    }
                }
            }

            // Generate error if could not find both a graphics and a present queue
            check(!(graphicsQueueNodeIndex == Int.MAX_VALUE || presentQueueNodeIndex == Int.MAX_VALUE)) { "Could not find a graphics and a present queue" }

            // TODO: Add support for separate queues, including presentation,
            //       synchronization, and appropriate tracking for QueueSubmit.
            // NOTE: While it is possible for an application to use a separate graphics
            //       and a present queues, this demo program assumes it is only using
            //       one:
            check(graphicsQueueNodeIndex == presentQueueNodeIndex) { "Could not find a common graphics and a present queue" }
            graphics_queue_node_index = graphicsQueueNodeIndex
            demo_init_device()
            VK10.vkGetDeviceQueue(device, graphics_queue_node_index, 0, pp)
            queue = VkQueue(pp[0], device)

            // Get the list of VkFormat's that are supported:
            check(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, surface, ip, null))
            val surfFormats = VkSurfaceFormatKHR.malloc(ip[0], stack)
            check(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(gpu, surface, ip, surfFormats))

            // If the format list includes just one entry of VK_FORMAT_UNDEFINED,
            // the surface has no preferred format.  Otherwise, at least one
            // supported format will be returned.
            format = if (ip[0] == 1 && surfFormats[0].format() == VK10.VK_FORMAT_UNDEFINED) {
                VK10.VK_FORMAT_B8G8R8A8_UNORM
            } else {
                assert(ip[0] >= 1)
                surfFormats[0].format()
            }
            color_space = surfFormats[0].colorSpace()

            // Get Memory information and properties
            VK10.vkGetPhysicalDeviceMemoryProperties(gpu, memory_properties)
        }
    }

    private class SwapchainBuffers {
        var image: Long = 0
        var cmd: VkCommandBuffer? = null
        var view: Long = 0
    }

    private fun demo_set_image_layout(
        image: Long,
        aspectMask: Int,
        old_image_layout: Int,
        new_image_layout: Int,
        srcAccessMask: Int
    ) {
        MemoryStack.stackPush().use { stack ->
            if (setup_cmd == null) {
                val cmd = VkCommandBufferAllocateInfo.malloc(stack)
                    .`sType$Default`()
                    .pNext(MemoryUtil.NULL)
                    .commandPool(cmd_pool)
                    .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1)
                check(VK10.vkAllocateCommandBuffers(device, cmd, pp))
                setup_cmd = VkCommandBuffer(pp[0], device)
                val cmd_buf_info = VkCommandBufferBeginInfo.malloc(stack)
                    .`sType$Default`()
                    .pNext(MemoryUtil.NULL)
                    .flags(0)
                    .pInheritanceInfo(null)
                check(VK10.vkBeginCommandBuffer(setup_cmd, cmd_buf_info))
            }
            val image_memory_barrier = VkImageMemoryBarrier.malloc(1, stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .srcAccessMask(srcAccessMask)
                .dstAccessMask(0)
                .oldLayout(old_image_layout)
                .newLayout(new_image_layout)
                .srcQueueFamilyIndex(0)
                .dstQueueFamilyIndex(0)
                .image(image)
                .subresourceRange { it: VkImageSubresourceRange ->
                    it
                        .aspectMask(aspectMask)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }
            var src_stages = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
            var dest_stages = VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT
            if (srcAccessMask == VK10.VK_ACCESS_HOST_WRITE_BIT) {
                src_stages = VK10.VK_PIPELINE_STAGE_HOST_BIT
            }
            when (new_image_layout) {
                VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL -> image_memory_barrier.dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> {
                    image_memory_barrier.dstAccessMask(VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                    dest_stages = VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT
                }

                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> {
                    /* Make sure any Copy or CPU writes to image are flushed */image_memory_barrier.dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT or VK10.VK_ACCESS_INPUT_ATTACHMENT_READ_BIT)
                    dest_stages = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
                }

                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> {
                    image_memory_barrier.srcAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT)
                    /* Make sure anything that was copying from this image has completed */image_memory_barrier.dstAccessMask(
                        VK10.VK_ACCESS_TRANSFER_READ_BIT
                    )
                    dest_stages = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT
                }

                KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR -> image_memory_barrier.dstAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT)
            }
            VK10.vkCmdPipelineBarrier(setup_cmd, src_stages, dest_stages, 0, null, null, image_memory_barrier)
        }
    }

    private fun demo_prepare_buffers() {
        val oldSwapchain = swapchain
        MemoryStack.stackPush().use { stack ->
            // Check the surface capabilities and formats
            val surfCapabilities = VkSurfaceCapabilitiesKHR.malloc(stack)
            check(
                KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                    gpu,
                    surface,
                    surfCapabilities
                )
            )
            check(KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(gpu, surface, ip, null))
            val presentModes = stack.mallocInt(ip[0])
            check(
                KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(
                    gpu,
                    surface,
                    ip,
                    presentModes
                )
            )
            val swapchainExtent = VkExtent2D.malloc(stack)
            // width and height are either both 0xFFFFFFFF, or both not 0xFFFFFFFF.
            if (surfCapabilities.currentExtent().width() == -0x1) {
                // If the surface size is undefined, the size is set to the size
                // of the images requested, which must fit within the minimum and
                // maximum values.
                swapchainExtent.width(width)
                swapchainExtent.height(height)
                if (swapchainExtent.width() < surfCapabilities.minImageExtent().width()) {
                    swapchainExtent.width(surfCapabilities.minImageExtent().width())
                } else if (swapchainExtent.width() > surfCapabilities.maxImageExtent().width()) {
                    swapchainExtent.width(surfCapabilities.maxImageExtent().width())
                }
                if (swapchainExtent.height() < surfCapabilities.minImageExtent().height()) {
                    swapchainExtent.height(surfCapabilities.minImageExtent().height())
                } else if (swapchainExtent.height() > surfCapabilities.maxImageExtent().height()) {
                    swapchainExtent.height(surfCapabilities.maxImageExtent().height())
                }
            } else {
                // If the surface size is defined, the swap chain size must match
                swapchainExtent.set(surfCapabilities.currentExtent())
                width = surfCapabilities.currentExtent().width()
                height = surfCapabilities.currentExtent().height()
            }
            val swapchainPresentMode = KHRSurface.VK_PRESENT_MODE_FIFO_KHR

            // Determine the number of VkImage's to use in the swap chain.
            // Application desires to only acquire 1 image at a time (which is
            // "surfCapabilities.minImageCount").
            var desiredNumOfSwapchainImages = surfCapabilities.minImageCount()
            // If maxImageCount is 0, we can ask for as many images as we want;
            // otherwise we're limited to maxImageCount
            if (surfCapabilities.maxImageCount() > 0 && desiredNumOfSwapchainImages > surfCapabilities.maxImageCount()
            ) {
                // Application must settle for fewer images than desired:
                desiredNumOfSwapchainImages = surfCapabilities.maxImageCount()
            }
            val preTransform: Int
            preTransform =
                if (surfCapabilities.supportedTransforms() and KHRSurface.VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR != 0) {
                    KHRSurface.VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR
                } else {
                    surfCapabilities.currentTransform()
                }
            val swapchain = VkSwapchainCreateInfoKHR.calloc(stack)
                .`sType$Default`()
                .surface(surface)
                .minImageCount(desiredNumOfSwapchainImages)
                .imageFormat(format)
                .imageColorSpace(color_space)
                .imageExtent(swapchainExtent)
                .imageArrayLayers(1)
                .imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                .preTransform(preTransform)
                .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(swapchainPresentMode)
                .clipped(true)
                .oldSwapchain(oldSwapchain)
            check(KHRSwapchain.vkCreateSwapchainKHR(device, swapchain, null, lp))
            this.swapchain = lp[0]

            // If we just re-created an existing swapchain, we should destroy the old
            // swapchain at this point.
            // Note: destroying the swapchain also cleans up all its associated
            // presentable images once the platform is done with them.
            if (oldSwapchain != VK10.VK_NULL_HANDLE) {
                KHRSwapchain.vkDestroySwapchainKHR(device, oldSwapchain, null)
            }
            check(KHRSwapchain.vkGetSwapchainImagesKHR(device, this.swapchain, ip, null))
            swapchainImageCount = ip[0]
            val swapchainImages = stack.mallocLong(swapchainImageCount)
            check(
                KHRSwapchain.vkGetSwapchainImagesKHR(
                    device,
                    this.swapchain,
                    ip,
                    swapchainImages
                )
            )
            buffers = arrayOfNulls(swapchainImageCount)
            for (i in 0 until swapchainImageCount) {
                buffers!![i] = SwapchainBuffers()
                buffers!![i]!!.image = swapchainImages[i]
                val color_attachment_view = VkImageViewCreateInfo.malloc(stack)
                    .`sType$Default`()
                    .pNext(MemoryUtil.NULL)
                    .flags(0)
                    .image(buffers!![i]!!.image)
                    .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                    .format(format)
                    .components { it: VkComponentMapping ->
                        it
                            .r(VK10.VK_COMPONENT_SWIZZLE_R)
                            .g(VK10.VK_COMPONENT_SWIZZLE_G)
                            .b(VK10.VK_COMPONENT_SWIZZLE_B)
                            .a(VK10.VK_COMPONENT_SWIZZLE_A)
                    }
                    .subresourceRange { it: VkImageSubresourceRange ->
                        it
                            .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1)
                    }
                check(VK10.vkCreateImageView(device, color_attachment_view, null, lp))
                buffers!![i]!!.view = lp[0]
            }
            current_buffer = 0
        }
    }

    private class Depth {
        var format = 0
        var image: Long = 0
        var mem: Long = 0
        var view: Long = 0
    }

    private fun memory_type_from_properties(
        typeBits: Int,
        requirements_mask: Int,
        mem_alloc: VkMemoryAllocateInfo
    ): Boolean {
        // Search memtypes to find first index with those properties
        var typeBits = typeBits
        for (i in 0 until VK10.VK_MAX_MEMORY_TYPES) {
            if (typeBits and 1 == 1) {
                // Type is available, does it match user properties?
                if (memory_properties.memoryTypes()[i].propertyFlags() and requirements_mask == requirements_mask) {
                    mem_alloc.memoryTypeIndex(i)
                    return true
                }
            }
            typeBits = typeBits shr 1
        }
        // No memory types matched, return failure
        return false
    }

    private fun demo_prepare_depth() {
        depth.format = VK10.VK_FORMAT_D16_UNORM
        MemoryStack.stackPush().use { stack ->
            val image = VkImageCreateInfo.calloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .imageType(VK10.VK_IMAGE_TYPE_2D)
                .format(depth.format)
                .extent { it: VkExtent3D ->
                    it
                        .width(width)
                        .height(height)
                        .depth(1)
                }
                .mipLevels(1)
                .arrayLayers(1)
                .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                .usage(VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)

            /* create image */check(VK10.vkCreateImage(device, image, null, lp))
            depth.image = lp[0]

            /* get memory requirements for this object */
            val mem_reqs = VkMemoryRequirements.malloc(stack)
            VK10.vkGetImageMemoryRequirements(device, depth.image, mem_reqs)

            /* select memory size and type */
            val mem_alloc = VkMemoryAllocateInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .allocationSize(mem_reqs.size())
                .memoryTypeIndex(0)
            val pass = memory_type_from_properties(
                mem_reqs.memoryTypeBits(),
                0,  /* No requirements */
                mem_alloc
            )
            assert(pass)

            /* allocate memory */check(VK10.vkAllocateMemory(device, mem_alloc, null, lp))
            depth.mem = lp[0]

            /* bind memory */check(VK10.vkBindImageMemory(device, depth.image, depth.mem, 0))
            demo_set_image_layout(
                depth.image, VK10.VK_IMAGE_ASPECT_DEPTH_BIT,
                VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                0
            )

            /* create image view */
            val view = VkImageViewCreateInfo.calloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .flags(0)
                .image(depth.image)
                .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                .format(depth.format)
                .subresourceRange { it: VkImageSubresourceRange ->
                    it
                        .aspectMask(VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }
            check(VK10.vkCreateImageView(device, view, null, lp))
            depth.view = lp[0]
        }
    }

    private class TextureObject {
        var sampler: Long = 0
        var image: Long = 0
        var imageLayout = 0
        var mem: Long = 0
        var view: Long = 0
        var tex_width = 0
        var tex_height = 0
    }

    private fun demo_prepare_texture_image(
        tex_colors: IntArray,
        tex_obj: TextureObject?, tiling: Int,
        usage: Int, required_props: Int
    ) {
        val tex_format = VK10.VK_FORMAT_B8G8R8A8_UNORM
        val tex_width = 2
        val tex_height = 2
        var pass: Boolean
        tex_obj!!.tex_width = tex_width
        tex_obj.tex_height = tex_height
        MemoryStack.stackPush().use { stack ->
            val image_create_info = VkImageCreateInfo.calloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .imageType(VK10.VK_IMAGE_TYPE_2D)
                .format(tex_format)
                .extent { it: VkExtent3D ->
                    it
                        .width(tex_width)
                        .height(tex_height)
                        .depth(1)
                }
                .mipLevels(1)
                .arrayLayers(1)
                .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .tiling(tiling)
                .usage(usage)
                .flags(0)
                .initialLayout(VK10.VK_IMAGE_LAYOUT_PREINITIALIZED)
            check(VK10.vkCreateImage(device, image_create_info, null, lp))
            tex_obj.image = lp[0]
            val mem_reqs = VkMemoryRequirements.malloc(stack)
            VK10.vkGetImageMemoryRequirements(device, tex_obj.image, mem_reqs)
            val mem_alloc = VkMemoryAllocateInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .allocationSize(mem_reqs.size())
                .memoryTypeIndex(0)
            pass = memory_type_from_properties(mem_reqs.memoryTypeBits(), required_props, mem_alloc)
            assert(pass)

            /* allocate memory */check(VK10.vkAllocateMemory(device, mem_alloc, null, lp))
            tex_obj.mem = lp[0]

            /* bind memory */check(
            VK10.vkBindImageMemory(
                device,
                tex_obj.image,
                tex_obj.mem,
                0
            )
        )
            if (required_props and VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT != 0) {
                val subres = VkImageSubresource.malloc(stack)
                    .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .arrayLayer(0)
                val layout = VkSubresourceLayout.malloc(stack)
                VK10.vkGetImageSubresourceLayout(device, tex_obj.image, subres, layout)
                check(
                    VK10.vkMapMemory(
                        device,
                        tex_obj.mem,
                        0,
                        mem_alloc.allocationSize(),
                        0,
                        pp
                    )
                )
                for (y in 0 until tex_height) {
                    val row = MemoryUtil.memIntBuffer(pp[0] + layout.rowPitch() * y, tex_width)
                    for (x in 0 until tex_width) {
                        row.put(x, tex_colors[x and 1 xor (y and 1)])
                    }
                }
                VK10.vkUnmapMemory(device, tex_obj.mem)
            }
            tex_obj.imageLayout = VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
            demo_set_image_layout(
                tex_obj.image,
                VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                VK10.VK_IMAGE_LAYOUT_PREINITIALIZED,
                tex_obj.imageLayout,
                VK10.VK_ACCESS_HOST_WRITE_BIT
            )
        }
    }

    private fun demo_destroy_texture_image(tex_obj: TextureObject) {
        /* clean up staging resources */
        VK10.vkDestroyImage(device, tex_obj.image, null)
        VK10.vkFreeMemory(device, tex_obj.mem, null)
    }

    private fun demo_flush_init_cmd() {
        if (setup_cmd == null) {
            return
        }
        check(VK10.vkEndCommandBuffer(setup_cmd))
        MemoryStack.stackPush().use { stack ->
            val submit_info = VkSubmitInfo.calloc(stack)
                .`sType$Default`()
                .pCommandBuffers(pp.put(0, setup_cmd))
            check(VK10.vkQueueSubmit(queue, submit_info, VK10.VK_NULL_HANDLE))
        }
        check(VK10.vkQueueWaitIdle(queue))
        VK10.vkFreeCommandBuffers(device, cmd_pool, pp)
        setup_cmd = null
    }

    private fun demo_prepare_textures() {
        val tex_format = VK10.VK_FORMAT_B8G8R8A8_UNORM
        val tex_colors = arrayOf(intArrayOf(-0x10000, -0xff0100))
        MemoryStack.stackPush().use { stack ->
            val props = VkFormatProperties.malloc(stack)
            VK10.vkGetPhysicalDeviceFormatProperties(gpu, tex_format, props)
            for (i in 0 until DEMO_TEXTURE_COUNT) {
                if (props.linearTilingFeatures() and VK10.VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT != 0 && !USE_STAGING_BUFFER) {
                    /* Device can texture using linear textures */
                    demo_prepare_texture_image(
                        tex_colors[i], textures[i], VK10.VK_IMAGE_TILING_LINEAR,
                        VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                        VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                    )
                } else if (props.optimalTilingFeatures() and VK10.VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT != 0) {
                    /* Must use staging buffer to copy linear texture to optimized */
                    val staging_texture = TextureObject()
                    demo_prepare_texture_image(
                        tex_colors[i], staging_texture, VK10.VK_IMAGE_TILING_LINEAR,
                        VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT,
                        VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                    )
                    demo_prepare_texture_image(
                        tex_colors[i], textures[i],
                        VK10.VK_IMAGE_TILING_OPTIMAL,
                        VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK10.VK_IMAGE_USAGE_SAMPLED_BIT,
                        VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                    )
                    demo_set_image_layout(
                        staging_texture.image,
                        VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                        staging_texture.imageLayout,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        0
                    )
                    demo_set_image_layout(
                        textures[i]!!.image,
                        VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                        textures[i]!!.imageLayout,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        0
                    )
                    val copy_region = VkImageCopy.malloc(1, stack)
                        .srcSubresource { it: VkImageSubresourceLayers ->
                            it
                                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                                .mipLevel(0)
                                .baseArrayLayer(0)
                                .layerCount(1)
                        }
                        .srcOffset { it: VkOffset3D ->
                            it
                                .x(0)
                                .y(0)
                                .z(0)
                        }
                        .dstSubresource { it: VkImageSubresourceLayers ->
                            it
                                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                                .mipLevel(0)
                                .baseArrayLayer(0)
                                .layerCount(1)
                        }
                        .dstOffset { it: VkOffset3D ->
                            it
                                .x(0)
                                .y(0)
                                .z(0)
                        }
                        .extent { it: VkExtent3D ->
                            it
                                .width(staging_texture.tex_width)
                                .height(staging_texture.tex_height)
                                .depth(1)
                        }
                    VK10.vkCmdCopyImage(
                        setup_cmd, staging_texture.image,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, textures[i]!!.image,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copy_region
                    )
                    demo_set_image_layout(
                        textures[i]!!.image,
                        VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                        VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        textures[i]!!.imageLayout,
                        0
                    )
                    demo_flush_init_cmd()
                    demo_destroy_texture_image(staging_texture)
                } else {
                    /* Can't support VK_FORMAT_B8G8R8A8_UNORM !? */
                    throw IllegalStateException("No support for B8G8R8A8_UNORM as texture image format")
                }
                val sampler = VkSamplerCreateInfo.calloc(stack)
                    .`sType$Default`()
                    .pNext(MemoryUtil.NULL)
                    .magFilter(VK10.VK_FILTER_NEAREST)
                    .minFilter(VK10.VK_FILTER_NEAREST)
                    .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .mipLodBias(0.0f)
                    .anisotropyEnable(false)
                    .maxAnisotropy(1f)
                    .compareOp(VK10.VK_COMPARE_OP_NEVER)
                    .minLod(0.0f)
                    .maxLod(0.0f)
                    .borderColor(VK10.VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE)
                    .unnormalizedCoordinates(false)

                /* create sampler */check(VK10.vkCreateSampler(device, sampler, null, lp))
                textures[i]!!.sampler = lp[0]
                val view = VkImageViewCreateInfo.malloc(stack)
                    .`sType$Default`()
                    .pNext(MemoryUtil.NULL)
                    .image(VK10.VK_NULL_HANDLE)
                    .viewType(VK10.VK_IMAGE_VIEW_TYPE_2D)
                    .format(tex_format)
                    .flags(0)
                    .components { it: VkComponentMapping ->
                        it
                            .r(VK10.VK_COMPONENT_SWIZZLE_R)
                            .g(VK10.VK_COMPONENT_SWIZZLE_G)
                            .b(VK10.VK_COMPONENT_SWIZZLE_B)
                            .a(VK10.VK_COMPONENT_SWIZZLE_A)
                    }
                    .subresourceRange { it: VkImageSubresourceRange ->
                        it
                            .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1)
                    }

                /* create image view */view.image(textures[i]!!.image)
                check(VK10.vkCreateImageView(device, view, null, lp))
                textures[i]!!.view = lp[0]
            }
        }
    }

    private class Vertices {
        var buf: Long = 0
        var mem: Long = 0
        var vi = VkPipelineVertexInputStateCreateInfo.calloc()
        var vi_bindings = VkVertexInputBindingDescription.calloc(1)
        var vi_attrs = VkVertexInputAttributeDescription.calloc(2)
    }

    private fun demo_prepare_vertices() {
        val vb = arrayOf(
            floatArrayOf(-1.0f, -1.0f, 0.25f, 0.0f, 0.0f),
            floatArrayOf(1.0f, -1.0f, 0.25f, 1.0f, 0.0f),
            floatArrayOf(0.0f, 1.0f, 1.0f, 0.5f, 1.0f)
        )
        MemoryStack.stackPush().use { stack ->
            val buf_info = VkBufferCreateInfo.calloc(stack)
                .`sType$Default`()
                .size( /*sizeof(vb)*/(vb.size * vb[0].size * 4).toLong())
                .usage(VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
                .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
            check(VK10.vkCreateBuffer(device, buf_info, null, lp))
            vertices.buf = lp[0]
            val mem_reqs = VkMemoryRequirements.malloc(stack)
            VK10.vkGetBufferMemoryRequirements(device, vertices.buf, mem_reqs)
            val mem_alloc = VkMemoryAllocateInfo.calloc(stack)
                .`sType$Default`()
                .allocationSize(mem_reqs.size())
            val pass = memory_type_from_properties(
                mem_reqs.memoryTypeBits(),
                VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                mem_alloc
            )
            assert(pass)
            check(VK10.vkAllocateMemory(device, mem_alloc, null, lp))
            vertices.mem = lp[0]
            check(VK10.vkMapMemory(device, vertices.mem, 0, mem_alloc.allocationSize(), 0, pp))
            val data = pp.getFloatBuffer(0, mem_alloc.allocationSize().toInt() shr 2)
            data
                .put(vb[0])
                .put(vb[1])
                .put(vb[2])
                .flip()
        }
        VK10.vkUnmapMemory(device, vertices.mem)
        check(VK10.vkBindBufferMemory(device, vertices.buf, vertices.mem, 0))
        vertices.vi
            .`sType$Default`()
            .pNext(MemoryUtil.NULL)
            .pVertexBindingDescriptions(vertices.vi_bindings)
            .pVertexAttributeDescriptions(vertices.vi_attrs)
        vertices.vi_bindings[0]
            .binding(VERTEX_BUFFER_BIND_ID)
            .stride(vb[0].size * 4)
            .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX)
        vertices.vi_attrs[0]
            .binding(VERTEX_BUFFER_BIND_ID)
            .location(0)
            .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
            .offset(0)
        vertices.vi_attrs[1]
            .binding(VERTEX_BUFFER_BIND_ID)
            .location(1)
            .format(VK10.VK_FORMAT_R32G32_SFLOAT)
            .offset(4 * 3)
    }

    private fun demo_prepare_descriptor_layout() {
        MemoryStack.stackPush().use { stack ->
            val descriptor_layout = VkDescriptorSetLayoutCreateInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .flags(0)
                .pBindings(
                    VkDescriptorSetLayoutBinding.calloc(1, stack)
                        .binding(0)
                        .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(DEMO_TEXTURE_COUNT)
                        .stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                )
            val layouts = stack.mallocLong(1)
            check(VK10.vkCreateDescriptorSetLayout(device, descriptor_layout, null, layouts))
            desc_layout = layouts[0]
            val pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .pSetLayouts(layouts)
            check(VK10.vkCreatePipelineLayout(device, pPipelineLayoutCreateInfo, null, lp))
            pipeline_layout = lp[0]
        }
    }

    private fun demo_prepare_render_pass() {
        MemoryStack.stackPush().use { stack ->
            val attachments = VkAttachmentDescription.malloc(2, stack)
            attachments[0]
                .flags(0)
                .format(format)
                .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .finalLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            attachments[1]
                .flags(0)
                .format(depth.format)
                .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                .finalLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
            val subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(
                    VkAttachmentReference.malloc(1, stack)
                        .attachment(0)
                        .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                )
                .pDepthStencilAttachment(
                    VkAttachmentReference.malloc(stack)
                        .attachment(1)
                        .layout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                )
            val rp_info = VkRenderPassCreateInfo.calloc(stack)
                .`sType$Default`()
                .pAttachments(attachments)
                .pSubpasses(subpass)
            check(VK10.vkCreateRenderPass(device, rp_info, null, lp))
            render_pass = lp[0]
        }
    }

    private fun demo_prepare_shader_module(code: ByteArray): Long {
        MemoryStack.stackPush().use { stack ->
            val pCode = MemoryUtil.memAlloc(code.size).put(code)
            pCode.flip()
            val moduleCreateInfo = VkShaderModuleCreateInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .flags(0)
                .pCode(pCode)
            check(VK10.vkCreateShaderModule(device, moduleCreateInfo, null, lp))
            MemoryUtil.memFree(pCode)
            return lp[0]
        }
    }

    private fun demo_prepare_pipeline() {
        var vert_shader_module: Long
        var frag_shader_module: Long
        var pipelineCache: Long
        MemoryStack.stackPush().use { stack ->
            val pipeline = VkGraphicsPipelineCreateInfo.calloc(1, stack)

            // Two stages: vs and fs
            val main = stack.UTF8("main")
            val shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack)
            shaderStages[0]
                .`sType$Default`()
                .stage(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                .module(demo_prepare_shader_module(vertShaderCode).also {
                    vert_shader_module = it
                })
                .pName(main)
            shaderStages[1]
                .`sType$Default`()
                .stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(demo_prepare_shader_module(fragShaderCode).also {
                    frag_shader_module = it
                })
                .pName(main)
            val ds = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                .`sType$Default`()
                .depthTestEnable(true)
                .depthWriteEnable(true)
                .depthCompareOp(VK10.VK_COMPARE_OP_LESS_OR_EQUAL)
                .depthBoundsTestEnable(false)
                .stencilTestEnable(false)
                .back { it: VkStencilOpState ->
                    it
                        .failOp(VK10.VK_STENCIL_OP_KEEP)
                        .passOp(VK10.VK_STENCIL_OP_KEEP)
                        .compareOp(VK10.VK_COMPARE_OP_ALWAYS)
                }
            ds.front(ds.back())
            pipeline
                .`sType$Default`()
                .pStages(shaderStages)
                .pVertexInputState(vertices.vi)
                .pInputAssemblyState(
                    VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                        .`sType$Default`()
                        .topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                )
                .pViewportState(
                    VkPipelineViewportStateCreateInfo.calloc(stack)
                        .`sType$Default`()
                        .viewportCount(1)
                        .scissorCount(1)
                )
                .pRasterizationState(
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                        .`sType$Default`()
                        .polygonMode(VK10.VK_POLYGON_MODE_FILL)
                        .cullMode(VK10.VK_CULL_MODE_BACK_BIT)
                        .frontFace(VK10.VK_FRONT_FACE_CLOCKWISE)
                        .depthClampEnable(false)
                        .rasterizerDiscardEnable(false)
                        .depthBiasEnable(false)
                        .lineWidth(1.0f)
                )
                .pMultisampleState(
                    VkPipelineMultisampleStateCreateInfo.calloc(stack)
                        .`sType$Default`()
                        .pSampleMask(null)
                        .rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT)
                )
                .pDepthStencilState(ds)
                .pColorBlendState(
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                        .`sType$Default`()
                        .pAttachments(
                            VkPipelineColorBlendAttachmentState.calloc(1, stack)
                                .colorWriteMask(0xf)
                                .blendEnable(false)
                        )
                )
                .pDynamicState(
                    VkPipelineDynamicStateCreateInfo.calloc(stack)
                        .`sType$Default`()
                        .pDynamicStates(
                            stack.ints(
                                VK10.VK_DYNAMIC_STATE_VIEWPORT,
                                VK10.VK_DYNAMIC_STATE_SCISSOR
                            )
                        )
                )
                .layout(pipeline_layout)
                .renderPass(render_pass)
            val pipelineCacheCI = VkPipelineCacheCreateInfo.calloc(stack)
                .`sType$Default`()
            check(VK10.vkCreatePipelineCache(device, pipelineCacheCI, null, lp))
            pipelineCache = lp[0]
            check(VK10.vkCreateGraphicsPipelines(device, pipelineCache, pipeline, null, lp))
            this.pipeline = lp[0]
            VK10.vkDestroyPipelineCache(device, pipelineCache, null)
            VK10.vkDestroyShaderModule(device, frag_shader_module, null)
            VK10.vkDestroyShaderModule(device, vert_shader_module, null)
        }
    }

    private fun demo_prepare_descriptor_pool() {
        MemoryStack.stackPush().use { stack ->
            val descriptor_pool = VkDescriptorPoolCreateInfo.calloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .maxSets(1)
                .pPoolSizes(
                    VkDescriptorPoolSize.malloc(1, stack)
                        .type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(DEMO_TEXTURE_COUNT)
                )
            check(VK10.vkCreateDescriptorPool(device, descriptor_pool, null, lp))
            desc_pool = lp[0]
        }
    }

    private fun demo_prepare_descriptor_set() {
        MemoryStack.stackPush().use { stack ->
            val layouts = stack.longs(desc_layout)
            val alloc_info = VkDescriptorSetAllocateInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .descriptorPool(desc_pool)
                .pSetLayouts(layouts)
            check(VK10.vkAllocateDescriptorSets(device, alloc_info, lp))
            desc_set = lp[0]
            val tex_descs =
                VkDescriptorImageInfo.calloc(DEMO_TEXTURE_COUNT, stack)
            for (i in 0 until DEMO_TEXTURE_COUNT) {
                tex_descs[i]
                    .sampler(textures[i]!!.sampler)
                    .imageView(textures[i]!!.view)
                    .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            }
            val write = VkWriteDescriptorSet.calloc(1, stack)
                .`sType$Default`()
                .dstSet(desc_set)
                .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(tex_descs.remaining())
                .pImageInfo(tex_descs)
            VK10.vkUpdateDescriptorSets(device, write, null)
        }
    }

    private fun demo_prepare_framebuffers() {
        MemoryStack.stackPush().use { stack ->
            val attachments = stack.longs(0, depth.view)
            val fb_info = VkFramebufferCreateInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .flags(0)
                .renderPass(render_pass)
                .pAttachments(attachments)
                .width(width)
                .height(height)
                .layers(1)
            val framebuffers = MemoryUtil.memAllocLong(swapchainImageCount)
            for (i in 0 until swapchainImageCount) {
                attachments.put(0, buffers!![i]!!.view)
                check(VK10.vkCreateFramebuffer(device, fb_info, null, lp))
                framebuffers.put(i, lp[0])
            }
            this.framebuffers = framebuffers
        }
    }

    private fun demo_prepare() {
        MemoryStack.stackPush().use { stack ->
            val cmd_pool_info = VkCommandPoolCreateInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(graphics_queue_node_index)
            check(VK10.vkCreateCommandPool(device, cmd_pool_info, null, lp))
            cmd_pool = lp[0]
            val cmd = VkCommandBufferAllocateInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .commandPool(cmd_pool)
                .level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1)
            check(VK10.vkAllocateCommandBuffers(device, cmd, pp))
        }
        draw_cmd = VkCommandBuffer(pp[0], device)
        demo_prepare_buffers()
        demo_prepare_depth()
        demo_prepare_textures()
        demo_prepare_vertices()
        demo_prepare_descriptor_layout()
        demo_prepare_render_pass()
        demo_prepare_pipeline()
        demo_prepare_descriptor_pool()
        demo_prepare_descriptor_set()
        demo_prepare_framebuffers()
    }

    private fun demo_draw_build_cmd() {
        MemoryStack.stackPush().use { stack ->
            val cmd_buf_info = VkCommandBufferBeginInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .flags(0)
                .pInheritanceInfo(null)
            check(VK10.vkBeginCommandBuffer(draw_cmd, cmd_buf_info))
            val clear_values = VkClearValue.malloc(2, stack)
            clear_values[0].color()
                .float32(0, 0.2f)
                .float32(1, 0.2f)
                .float32(2, 0.2f)
                .float32(3, 0.2f)
            clear_values[1].depthStencil()
                .depth(depthStencil)
                .stencil(0)
            val rp_begin = VkRenderPassBeginInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .renderPass(render_pass)
                .framebuffer(framebuffers!![current_buffer])
                .renderArea { ra: VkRect2D ->
                    ra
                        .offset { it: VkOffset2D ->
                            it
                                .x(0)
                                .y(0)
                        }
                        .extent { it: VkExtent2D ->
                            it
                                .width(width)
                                .height(height)
                        }
                }
                .pClearValues(clear_values)

            // We can use LAYOUT_UNDEFINED as a wildcard here because we don't care what
            // happens to the previous contents of the image
            val image_memory_barrier = VkImageMemoryBarrier.malloc(1, stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .srcAccessMask(0)
                .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .oldLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                .newLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .image(buffers!![current_buffer]!!.image)
                .subresourceRange { it: VkImageSubresourceRange ->
                    it
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }
            VK10.vkCmdPipelineBarrier(
                draw_cmd,
                VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                0,
                null,
                null,
                image_memory_barrier
            )
            VK10.vkCmdBeginRenderPass(draw_cmd, rp_begin, VK10.VK_SUBPASS_CONTENTS_INLINE)
            VK10.vkCmdBindPipeline(draw_cmd, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline)
            lp.put(0, desc_set)
            VK10.vkCmdBindDescriptorSets(draw_cmd, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline_layout, 0, lp, null)
            val viewport = VkViewport.calloc(1, stack)
                .height(height.toFloat())
                .width(width.toFloat())
                .minDepth(0.0f)
                .maxDepth(1.0f)
            VK10.vkCmdSetViewport(draw_cmd, 0, viewport)
            val scissor = VkRect2D.calloc(1, stack)
                .extent { it: VkExtent2D ->
                    it
                        .width(width)
                        .height(height)
                }
                .offset { it: VkOffset2D ->
                    it
                        .x(0)
                        .y(0)
                }
            VK10.vkCmdSetScissor(draw_cmd, 0, scissor)
            lp.put(0, 0)
            val pBuffers = stack.longs(vertices.buf)
            VK10.vkCmdBindVertexBuffers(draw_cmd, VERTEX_BUFFER_BIND_ID, pBuffers, lp)
            VK10.vkCmdDraw(draw_cmd, 3, 1, 0, 0)
            VK10.vkCmdEndRenderPass(draw_cmd)
            val prePresentBarrier = VkImageMemoryBarrier.malloc(1, stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .srcAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT)
                .oldLayout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .newLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .image(buffers!![current_buffer]!!.image)
                .subresourceRange { it: VkImageSubresourceRange ->
                    it
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }
            VK10.vkCmdPipelineBarrier(
                draw_cmd,
                VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                0,
                null,
                null,
                prePresentBarrier
            )
            check(VK10.vkEndCommandBuffer(draw_cmd))
        }
    }

    private fun demo_draw() {
        MemoryStack.stackPush().use { stack ->
            val semaphoreCreateInfo = VkSemaphoreCreateInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .flags(0)
            check(VK10.vkCreateSemaphore(device, semaphoreCreateInfo, null, lp))
            val imageAcquiredSemaphore = lp[0]
            check(VK10.vkCreateSemaphore(device, semaphoreCreateInfo, null, lp))
            val drawCompleteSemaphore = lp[0]

            // Get the index of the next available swapchain image:
            var err = KHRSwapchain.vkAcquireNextImageKHR(
                device, swapchain, 0L.inv(),
                imageAcquiredSemaphore,
                MemoryUtil.NULL,  // TODO: Show use of fence
                ip
            )
            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                // demo->swapchain is out of date (e.g. the window was resized) and
                // must be recreated:
                demo_resize()
                demo_draw()
                VK10.vkDestroySemaphore(device, drawCompleteSemaphore, null)
                VK10.vkDestroySemaphore(device, imageAcquiredSemaphore, null)
                return
            } else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                // demo->swapchain is not as optimal as it could be, but the platform's
                // presentation engine will still present the image correctly.
            } else {
                check(err)
            }
            current_buffer = ip[0]
            demo_flush_init_cmd()

            // Wait for the present complete semaphore to be signaled to ensure
            // that the image won't be rendered to until the presentation
            // engine has fully released ownership to the application, and it is
            // okay to render to the image.
            demo_draw_build_cmd()
            val lp2 = stack.mallocLong(1)
            val submit_info = VkSubmitInfo.malloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .waitSemaphoreCount(1)
                .pWaitSemaphores(lp.put(0, imageAcquiredSemaphore))
                .pWaitDstStageMask(ip.put(0, VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT))
                .pCommandBuffers(pp.put(0, draw_cmd))
                .pSignalSemaphores(lp2.put(0, drawCompleteSemaphore))
            check(VK10.vkQueueSubmit(queue, submit_info, VK10.VK_NULL_HANDLE))
            val present = VkPresentInfoKHR.calloc(stack)
                .`sType$Default`()
                .pNext(MemoryUtil.NULL)
                .pWaitSemaphores(lp2)
                .swapchainCount(1)
                .pSwapchains(lp.put(0, swapchain))
                .pImageIndices(ip.put(0, current_buffer))
            err = KHRSwapchain.vkQueuePresentKHR(queue, present)
            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                // demo->swapchain is out of date (e.g. the window was resized) and
                // must be recreated:
                demo_resize()
            } else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                // demo->swapchain is not as optimal as it could be, but the platform's
                // presentation engine will still present the image correctly.
            } else {
                check(err)
            }
            check(VK10.vkQueueWaitIdle(queue))
            VK10.vkDestroySemaphore(device, drawCompleteSemaphore, null)
            VK10.vkDestroySemaphore(device, imageAcquiredSemaphore, null)
        }
    }

    private fun demo_resize() {
        // In order to properly resize the window, we must re-create the swapchain
        // AND redo the command buffers, etc.
        //
        // First, perform part of the demo_cleanup() function:
        for (i in 0 until swapchainImageCount) {
            VK10.vkDestroyFramebuffer(device, framebuffers!![i], null)
        }
        MemoryUtil.memFree(framebuffers)
        VK10.vkDestroyDescriptorPool(device, desc_pool, null)
        if (setup_cmd != null) {
            VK10.vkFreeCommandBuffers(device, cmd_pool, setup_cmd)
            setup_cmd = null
        }
        VK10.vkFreeCommandBuffers(device, cmd_pool, draw_cmd)
        VK10.vkDestroyCommandPool(device, cmd_pool, null)
        VK10.vkDestroyPipeline(device, pipeline, null)
        VK10.vkDestroyRenderPass(device, render_pass, null)
        VK10.vkDestroyPipelineLayout(device, pipeline_layout, null)
        VK10.vkDestroyDescriptorSetLayout(device, desc_layout, null)
        VK10.vkDestroyBuffer(device, vertices.buf, null)
        VK10.vkFreeMemory(device, vertices.mem, null)
        for (i in 0 until DEMO_TEXTURE_COUNT) {
            VK10.vkDestroyImageView(device, textures[i]!!.view, null)
            VK10.vkDestroyImage(device, textures[i]!!.image, null)
            VK10.vkFreeMemory(device, textures[i]!!.mem, null)
            VK10.vkDestroySampler(device, textures[i]!!.sampler, null)
        }
        for (i in 0 until swapchainImageCount) {
            VK10.vkDestroyImageView(device, buffers!![i]!!.view, null)
        }
        VK10.vkDestroyImageView(device, depth.view, null)
        VK10.vkDestroyImage(device, depth.image, null)
        VK10.vkFreeMemory(device, depth.mem, null)
        buffers = null

        // Second, re-perform the demo_prepare() function, which will re-create the
        // swapchain:
        demo_prepare()
    }

    private fun demo_run() {
        var c = 0
        var t = System.nanoTime()
        while (!GLFW.glfwWindowShouldClose(window)) {
            GLFW.glfwPollEvents()
            demo_draw()
            if (depthStencil > 0.99f) {
                depthIncrement = -0.001f
            }
            if (depthStencil < 0.8f) {
                depthIncrement = 0.001f
            }
            depthStencil += depthIncrement
            c++
            if (System.nanoTime() - t > 1000 * 1000 * 1000) {
                println(c)
                t = System.nanoTime()
                c = 0
            }

            // Wait for work to finish before updating MVP.
            VK10.vkDeviceWaitIdle(device)
        }
    }

    private fun demo_cleanup() {
        for (i in 0 until swapchainImageCount) {
            VK10.vkDestroyFramebuffer(device, framebuffers!![i], null)
        }
        MemoryUtil.memFree(framebuffers)
        VK10.vkDestroyDescriptorPool(device, desc_pool, null)
        if (setup_cmd != null) {
            VK10.vkFreeCommandBuffers(device, cmd_pool, setup_cmd)
            setup_cmd = null
        }
        VK10.vkFreeCommandBuffers(device, cmd_pool, draw_cmd)
        VK10.vkDestroyCommandPool(device, cmd_pool, null)
        VK10.vkDestroyPipeline(device, pipeline, null)
        VK10.vkDestroyRenderPass(device, render_pass, null)
        VK10.vkDestroyPipelineLayout(device, pipeline_layout, null)
        VK10.vkDestroyDescriptorSetLayout(device, desc_layout, null)
        VK10.vkDestroyBuffer(device, vertices.buf, null)
        VK10.vkFreeMemory(device, vertices.mem, null)
        vertices.vi.free()
        vertices.vi_bindings.free()
        vertices.vi_attrs.free()
        for (i in 0 until DEMO_TEXTURE_COUNT) {
            VK10.vkDestroyImageView(device, textures[i]!!.view, null)
            VK10.vkDestroyImage(device, textures[i]!!.image, null)
            VK10.vkFreeMemory(device, textures[i]!!.mem, null)
            VK10.vkDestroySampler(device, textures[i]!!.sampler, null)
        }
        for (i in 0 until swapchainImageCount) {
            VK10.vkDestroyImageView(device, buffers!![i]!!.view, null)
        }
        VK10.vkDestroyImageView(device, depth.view, null)
        VK10.vkDestroyImage(device, depth.image, null)
        VK10.vkFreeMemory(device, depth.mem, null)
        KHRSwapchain.vkDestroySwapchainKHR(device, swapchain, null)
        buffers = null
        VK10.vkDestroyDevice(device, null)
        KHRSurface.vkDestroySurfaceKHR(inst, surface, null)
        if (msg_callback != MemoryUtil.NULL) {
            EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(inst, msg_callback, null)
        }
        VK10.vkDestroyInstance(inst, null)
        dbgFunc.free()
        gpu_features.free()
        gpu_props.free()
        queue_props!!.free()
        memory_properties.free()
        Callbacks.glfwFreeCallbacks(window)
        GLFW.glfwDestroyWindow(window)
        GLFW.glfwTerminate()
        Objects.requireNonNull(GLFW.glfwSetErrorCallback(null))?.free()
        MemoryUtil.memFree(extension_names)
        MemoryUtil.memFree(pp)
        MemoryUtil.memFree(lp)
        MemoryUtil.memFree(ip)
        MemoryUtil.memFree(EXT_debug_utils)
        MemoryUtil.memFree(KHR_swapchain)
    }

    private fun run() {
        demo_init()
        demo_create_window()
        demo_init_vk_swapchain()
        demo_prepare()
        demo_run()
        demo_cleanup()
    }

    companion object {
        private const val VALIDATE = true
        private const val USE_STAGING_BUFFER = false
        private const val DEMO_TEXTURE_COUNT = 1
        private const val VERTEX_BUFFER_BIND_ID = 0
        private val KHR_swapchain = MemoryUtil.memASCII(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME)
        private val EXT_debug_utils = MemoryUtil.memASCII(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME)
        private val fragShaderCode = byteArrayOf(
            0x03, 0x02, 0x23, 0x07, 0x00, 0x00, 0x01, 0x00, 0x08, 0x00, 0x0d, 0x00,
            0x14, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x11, 0x00, 0x02, 0x00,
            0x01, 0x00, 0x00, 0x00, 0x0b, 0x00, 0x06, 0x00, 0x01, 0x00, 0x00, 0x00,
            0x47, 0x4c, 0x53, 0x4c, 0x2e, 0x73, 0x74, 0x64, 0x2e, 0x34, 0x35, 0x30,
            0x00, 0x00, 0x00, 0x00, 0x0e, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x00, 0x0f, 0x00, 0x07, 0x00, 0x04, 0x00, 0x00, 0x00,
            0x04, 0x00, 0x00, 0x00, 0x6d, 0x61, 0x69, 0x6e, 0x00, 0x00, 0x00, 0x00,
            0x09, 0x00, 0x00, 0x00, 0x11, 0x00, 0x00, 0x00, 0x10, 0x00, 0x03, 0x00,
            0x04, 0x00, 0x00, 0x00, 0x07, 0x00, 0x00, 0x00, 0x03, 0x00, 0x03, 0x00,
            0x02, 0x00, 0x00, 0x00, 0xcc.toByte(), 0x01, 0x00, 0x00, 0x04, 0x00, 0x0a, 0x00,
            0x47, 0x4c, 0x5f, 0x47, 0x4f, 0x4f, 0x47, 0x4c, 0x45, 0x5f, 0x63, 0x70,
            0x70, 0x5f, 0x73, 0x74, 0x79, 0x6c, 0x65, 0x5f, 0x6c, 0x69, 0x6e, 0x65,
            0x5f, 0x64, 0x69, 0x72, 0x65, 0x63, 0x74, 0x69, 0x76, 0x65, 0x00, 0x00,
            0x04, 0x00, 0x08, 0x00, 0x47, 0x4c, 0x5f, 0x47, 0x4f, 0x4f, 0x47, 0x4c,
            0x45, 0x5f, 0x69, 0x6e, 0x63, 0x6c, 0x75, 0x64, 0x65, 0x5f, 0x64, 0x69,
            0x72, 0x65, 0x63, 0x74, 0x69, 0x76, 0x65, 0x00, 0x05, 0x00, 0x04, 0x00,
            0x04, 0x00, 0x00, 0x00, 0x6d, 0x61, 0x69, 0x6e, 0x00, 0x00, 0x00, 0x00,
            0x05, 0x00, 0x05, 0x00, 0x09, 0x00, 0x00, 0x00, 0x75, 0x46, 0x72, 0x61,
            0x67, 0x43, 0x6f, 0x6c, 0x6f, 0x72, 0x00, 0x00, 0x05, 0x00, 0x03, 0x00,
            0x0d, 0x00, 0x00, 0x00, 0x74, 0x65, 0x78, 0x00, 0x05, 0x00, 0x05, 0x00,
            0x11, 0x00, 0x00, 0x00, 0x74, 0x65, 0x78, 0x63, 0x6f, 0x6f, 0x72, 0x64,
            0x00, 0x00, 0x00, 0x00, 0x47, 0x00, 0x04, 0x00, 0x09, 0x00, 0x00, 0x00,
            0x1e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x47, 0x00, 0x04, 0x00,
            0x0d, 0x00, 0x00, 0x00, 0x22, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x47, 0x00, 0x04, 0x00, 0x0d, 0x00, 0x00, 0x00, 0x21, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x47, 0x00, 0x04, 0x00, 0x11, 0x00, 0x00, 0x00,
            0x1e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x13, 0x00, 0x02, 0x00,
            0x02, 0x00, 0x00, 0x00, 0x21, 0x00, 0x03, 0x00, 0x03, 0x00, 0x00, 0x00,
            0x02, 0x00, 0x00, 0x00, 0x16, 0x00, 0x03, 0x00, 0x06, 0x00, 0x00, 0x00,
            0x20, 0x00, 0x00, 0x00, 0x17, 0x00, 0x04, 0x00, 0x07, 0x00, 0x00, 0x00,
            0x06, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x20, 0x00, 0x04, 0x00,
            0x08, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x07, 0x00, 0x00, 0x00,
            0x3b, 0x00, 0x04, 0x00, 0x08, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00,
            0x03, 0x00, 0x00, 0x00, 0x19, 0x00, 0x09, 0x00, 0x0a, 0x00, 0x00, 0x00,
            0x06, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x1b, 0x00, 0x03, 0x00, 0x0b, 0x00, 0x00, 0x00,
            0x0a, 0x00, 0x00, 0x00, 0x20, 0x00, 0x04, 0x00, 0x0c, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x0b, 0x00, 0x00, 0x00, 0x3b, 0x00, 0x04, 0x00,
            0x0c, 0x00, 0x00, 0x00, 0x0d, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x17, 0x00, 0x04, 0x00, 0x0f, 0x00, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00,
            0x02, 0x00, 0x00, 0x00, 0x20, 0x00, 0x04, 0x00, 0x10, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x00, 0x0f, 0x00, 0x00, 0x00, 0x3b, 0x00, 0x04, 0x00,
            0x10, 0x00, 0x00, 0x00, 0x11, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
            0x36, 0x00, 0x05, 0x00, 0x02, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0xf8.toByte(), 0x00, 0x02, 0x00,
            0x05, 0x00, 0x00, 0x00, 0x3d, 0x00, 0x04, 0x00, 0x0b, 0x00, 0x00, 0x00,
            0x0e, 0x00, 0x00, 0x00, 0x0d, 0x00, 0x00, 0x00, 0x3d, 0x00, 0x04, 0x00,
            0x0f, 0x00, 0x00, 0x00, 0x12, 0x00, 0x00, 0x00, 0x11, 0x00, 0x00, 0x00,
            0x57, 0x00, 0x05, 0x00, 0x07, 0x00, 0x00, 0x00, 0x13, 0x00, 0x00, 0x00,
            0x0e, 0x00, 0x00, 0x00, 0x12, 0x00, 0x00, 0x00, 0x3e, 0x00, 0x03, 0x00,
            0x09, 0x00, 0x00, 0x00, 0x13, 0x00, 0x00, 0x00, 0xfd.toByte(), 0x00, 0x01, 0x00,
            0x38, 0x00, 0x01, 0x00
        )
        private val vertShaderCode = byteArrayOf(
            0x03,
            0x02,
            0x23,
            0x07,
            0x00,
            0x00,
            0x01,
            0x00,
            0x08,
            0x00,
            0x0d,
            0x00,
            0x1b,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x11,
            0x00,
            0x02,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x0b,
            0x00,
            0x06,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x47,
            0x4c,
            0x53,
            0x4c,
            0x2e,
            0x73,
            0x74,
            0x64,
            0x2e,
            0x34,
            0x35,
            0x30,
            0x00,
            0x00,
            0x00,
            0x00,
            0x0e,
            0x00,
            0x03,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x0f,
            0x00,
            0x09,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x04,
            0x00,
            0x00,
            0x00,
            0x6d,
            0x61,
            0x69,
            0x6e,
            0x00,
            0x00,
            0x00,
            0x00,
            0x09,
            0x00,
            0x00,
            0x00,
            0x0b,
            0x00,
            0x00,
            0x00,
            0x13,
            0x00,
            0x00,
            0x00,
            0x17,
            0x00,
            0x00,
            0x00,
            0x03,
            0x00,
            0x03,
            0x00,
            0x02,
            0x00,
            0x00,
            0x00,
            0xcc.toByte(),
            0x01,
            0x00,
            0x00,
            0x04,
            0x00,
            0x0a,
            0x00,
            0x47,
            0x4c,
            0x5f,
            0x47,
            0x4f,
            0x4f,
            0x47,
            0x4c,
            0x45,
            0x5f,
            0x63,
            0x70,
            0x70,
            0x5f,
            0x73,
            0x74,
            0x79,
            0x6c,
            0x65,
            0x5f,
            0x6c,
            0x69,
            0x6e,
            0x65,
            0x5f,
            0x64,
            0x69,
            0x72,
            0x65,
            0x63,
            0x74,
            0x69,
            0x76,
            0x65,
            0x00,
            0x00,
            0x04,
            0x00,
            0x08,
            0x00,
            0x47,
            0x4c,
            0x5f,
            0x47,
            0x4f,
            0x4f,
            0x47,
            0x4c,
            0x45,
            0x5f,
            0x69,
            0x6e,
            0x63,
            0x6c,
            0x75,
            0x64,
            0x65,
            0x5f,
            0x64,
            0x69,
            0x72,
            0x65,
            0x63,
            0x74,
            0x69,
            0x76,
            0x65,
            0x00,
            0x05,
            0x00,
            0x04,
            0x00,
            0x04,
            0x00,
            0x00,
            0x00,
            0x6d,
            0x61,
            0x69,
            0x6e,
            0x00,
            0x00,
            0x00,
            0x00,
            0x05,
            0x00,
            0x05,
            0x00,
            0x09,
            0x00,
            0x00,
            0x00,
            0x74,
            0x65,
            0x78,
            0x63,
            0x6f,
            0x6f,
            0x72,
            0x64,
            0x00,
            0x00,
            0x00,
            0x00,
            0x05,
            0x00,
            0x04,
            0x00,
            0x0b,
            0x00,
            0x00,
            0x00,
            0x61,
            0x74,
            0x74,
            0x72,
            0x00,
            0x00,
            0x00,
            0x00,
            0x05,
            0x00,
            0x06,
            0x00,
            0x11,
            0x00,
            0x00,
            0x00,
            0x67,
            0x6c,
            0x5f,
            0x50,
            0x65,
            0x72,
            0x56,
            0x65,
            0x72,
            0x74,
            0x65,
            0x78,
            0x00,
            0x00,
            0x00,
            0x00,
            0x06,
            0x00,
            0x06,
            0x00,
            0x11,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x67,
            0x6c,
            0x5f,
            0x50,
            0x6f,
            0x73,
            0x69,
            0x74,
            0x69,
            0x6f,
            0x6e,
            0x00,
            0x06,
            0x00,
            0x07,
            0x00,
            0x11,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x67,
            0x6c,
            0x5f,
            0x50,
            0x6f,
            0x69,
            0x6e,
            0x74,
            0x53,
            0x69,
            0x7a,
            0x65,
            0x00,
            0x00,
            0x00,
            0x00,
            0x06,
            0x00,
            0x07,
            0x00,
            0x11,
            0x00,
            0x00,
            0x00,
            0x02,
            0x00,
            0x00,
            0x00,
            0x67,
            0x6c,
            0x5f,
            0x43,
            0x6c,
            0x69,
            0x70,
            0x44,
            0x69,
            0x73,
            0x74,
            0x61,
            0x6e,
            0x63,
            0x65,
            0x00,
            0x06,
            0x00,
            0x07,
            0x00,
            0x11,
            0x00,
            0x00,
            0x00,
            0x03,
            0x00,
            0x00,
            0x00,
            0x67,
            0x6c,
            0x5f,
            0x43,
            0x75,
            0x6c,
            0x6c,
            0x44,
            0x69,
            0x73,
            0x74,
            0x61,
            0x6e,
            0x63,
            0x65,
            0x00,
            0x05,
            0x00,
            0x03,
            0x00,
            0x13,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x05,
            0x00,
            0x03,
            0x00,
            0x17,
            0x00,
            0x00,
            0x00,
            0x70,
            0x6f,
            0x73,
            0x00,
            0x47,
            0x00,
            0x04,
            0x00,
            0x09,
            0x00,
            0x00,
            0x00,
            0x1e,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x47,
            0x00,
            0x04,
            0x00,
            0x0b,
            0x00,
            0x00,
            0x00,
            0x1e,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x48,
            0x00,
            0x05,
            0x00,
            0x11,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x0b,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x48,
            0x00,
            0x05,
            0x00,
            0x11,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x0b,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x48,
            0x00,
            0x05,
            0x00,
            0x11,
            0x00,
            0x00,
            0x00,
            0x02,
            0x00,
            0x00,
            0x00,
            0x0b,
            0x00,
            0x00,
            0x00,
            0x03,
            0x00,
            0x00,
            0x00,
            0x48,
            0x00,
            0x05,
            0x00,
            0x11,
            0x00,
            0x00,
            0x00,
            0x03,
            0x00,
            0x00,
            0x00,
            0x0b,
            0x00,
            0x00,
            0x00,
            0x04,
            0x00,
            0x00,
            0x00,
            0x47,
            0x00,
            0x03,
            0x00,
            0x11,
            0x00,
            0x00,
            0x00,
            0x02,
            0x00,
            0x00,
            0x00,
            0x47,
            0x00,
            0x04,
            0x00,
            0x17,
            0x00,
            0x00,
            0x00,
            0x1e,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x13,
            0x00,
            0x02,
            0x00,
            0x02,
            0x00,
            0x00,
            0x00,
            0x21,
            0x00,
            0x03,
            0x00,
            0x03,
            0x00,
            0x00,
            0x00,
            0x02,
            0x00,
            0x00,
            0x00,
            0x16,
            0x00,
            0x03,
            0x00,
            0x06,
            0x00,
            0x00,
            0x00,
            0x20,
            0x00,
            0x00,
            0x00,
            0x17,
            0x00,
            0x04,
            0x00,
            0x07,
            0x00,
            0x00,
            0x00,
            0x06,
            0x00,
            0x00,
            0x00,
            0x02,
            0x00,
            0x00,
            0x00,
            0x20,
            0x00,
            0x04,
            0x00,
            0x08,
            0x00,
            0x00,
            0x00,
            0x03,
            0x00,
            0x00,
            0x00,
            0x07,
            0x00,
            0x00,
            0x00,
            0x3b,
            0x00,
            0x04,
            0x00,
            0x08,
            0x00,
            0x00,
            0x00,
            0x09,
            0x00,
            0x00,
            0x00,
            0x03,
            0x00,
            0x00,
            0x00,
            0x20,
            0x00,
            0x04,
            0x00,
            0x0a,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x07,
            0x00,
            0x00,
            0x00,
            0x3b,
            0x00,
            0x04,
            0x00,
            0x0a,
            0x00,
            0x00,
            0x00,
            0x0b,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x17,
            0x00,
            0x04,
            0x00,
            0x0d,
            0x00,
            0x00,
            0x00,
            0x06,
            0x00,
            0x00,
            0x00,
            0x04,
            0x00,
            0x00,
            0x00,
            0x15,
            0x00,
            0x04,
            0x00,
            0x0e,
            0x00,
            0x00,
            0x00,
            0x20,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x2b,
            0x00,
            0x04,
            0x00,
            0x0e,
            0x00,
            0x00,
            0x00,
            0x0f,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x1c,
            0x00,
            0x04,
            0x00,
            0x10,
            0x00,
            0x00,
            0x00,
            0x06,
            0x00,
            0x00,
            0x00,
            0x0f,
            0x00,
            0x00,
            0x00,
            0x1e,
            0x00,
            0x06,
            0x00,
            0x11,
            0x00,
            0x00,
            0x00,
            0x0d,
            0x00,
            0x00,
            0x00,
            0x06,
            0x00,
            0x00,
            0x00,
            0x10,
            0x00,
            0x00,
            0x00,
            0x10,
            0x00,
            0x00,
            0x00,
            0x20,
            0x00,
            0x04,
            0x00,
            0x12,
            0x00,
            0x00,
            0x00,
            0x03,
            0x00,
            0x00,
            0x00,
            0x11,
            0x00,
            0x00,
            0x00,
            0x3b,
            0x00,
            0x04,
            0x00,
            0x12,
            0x00,
            0x00,
            0x00,
            0x13,
            0x00,
            0x00,
            0x00,
            0x03,
            0x00,
            0x00,
            0x00,
            0x15,
            0x00,
            0x04,
            0x00,
            0x14,
            0x00,
            0x00,
            0x00,
            0x20,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x2b,
            0x00,
            0x04,
            0x00,
            0x14,
            0x00,
            0x00,
            0x00,
            0x15,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x20,
            0x00,
            0x04,
            0x00,
            0x16,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x0d,
            0x00,
            0x00,
            0x00,
            0x3b,
            0x00,
            0x04,
            0x00,
            0x16,
            0x00,
            0x00,
            0x00,
            0x17,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x20,
            0x00,
            0x04,
            0x00,
            0x19,
            0x00,
            0x00,
            0x00,
            0x03,
            0x00,
            0x00,
            0x00,
            0x0d,
            0x00,
            0x00,
            0x00,
            0x36,
            0x00,
            0x05,
            0x00,
            0x02,
            0x00,
            0x00,
            0x00,
            0x04,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x03,
            0x00,
            0x00,
            0x00,
            0xf8.toByte(),
            0x00,
            0x02,
            0x00,
            0x05,
            0x00,
            0x00,
            0x00,
            0x3d,
            0x00,
            0x04,
            0x00,
            0x07,
            0x00,
            0x00,
            0x00,
            0x0c,
            0x00,
            0x00,
            0x00,
            0x0b,
            0x00,
            0x00,
            0x00,
            0x3e,
            0x00,
            0x03,
            0x00,
            0x09,
            0x00,
            0x00,
            0x00,
            0x0c,
            0x00,
            0x00,
            0x00,
            0x3d,
            0x00,
            0x04,
            0x00,
            0x0d,
            0x00,
            0x00,
            0x00,
            0x18,
            0x00,
            0x00,
            0x00,
            0x17,
            0x00,
            0x00,
            0x00,
            0x41,
            0x00,
            0x05,
            0x00,
            0x19,
            0x00,
            0x00,
            0x00,
            0x1a,
            0x00,
            0x00,
            0x00,
            0x13,
            0x00,
            0x00,
            0x00,
            0x15,
            0x00,
            0x00,
            0x00,
            0x3e,
            0x00,
            0x03,
            0x00,
            0x1a,
            0x00,
            0x00,
            0x00,
            0x18,
            0x00,
            0x00,
            0x00,
            0xfd.toByte(),
            0x00,
            0x01,
            0x00,
            0x38,
            0x00,
            0x01,
            0x00
        )

        private fun check(errcode: Int) {
            check(errcode == 0) { String.format("Vulkan error [0x%X]", errcode) }
        }

        private fun demo_init_connection() {
            GLFWErrorCallback.createPrint().set()
            check(GLFW.glfwInit()) { "Unable to initialize GLFW" }
            check(GLFWVulkan.glfwVulkanSupported()) { "Cannot find a compatible Vulkan installable client driver (ICD)" }
        }

        /**
         * Return true if all layer names specified in `check_names` can be found in given `layer` properties.
         */
        private fun demo_check_layers(
            stack: MemoryStack,
            available: VkLayerProperties.Buffer,
            vararg layers: String
        ): PointerBuffer? {
            val required = stack.mallocPointer(layers.size)
            for (i in layers.indices) {
                var found = false
                for (j in 0 until available.capacity()) {
                    available.position(j)
                    if (layers[i] == available.layerNameString()) {
                        found = true
                        break
                    }
                }
                if (!found) {
                    System.err.format("Cannot find layer: %s\n", layers[i])
                    return null
                }
                required.put(i, stack.ASCII(layers[i]))
            }
            return required
        }

        @JvmStatic
        fun main(args: Array<String>) {
            HelloVulkan().run()
        }
    }
}
