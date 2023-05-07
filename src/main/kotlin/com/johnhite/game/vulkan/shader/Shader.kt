package com.johnhite.game.vulkan.shader

import com.johnhite.game.vulkan.LogicalDevice
import com.johnhite.game.vulkan.Resources
import com.johnhite.game.vulkan.checkVk
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import java.io.File
import java.nio.ByteBuffer

class Shader(val id: Long, val stage: Int, private val device: LogicalDevice) : AutoCloseable {

    companion object {
        fun load(device: LogicalDevice, shaderSrc: File, stage: Int? = null) : Shader {
            val code = compile(shaderSrc)
            val finalStage = stage ?: shadercKindToVulkanKind(senseShaderKind(shaderSrc))
            return Shader(create(device, code), finalStage, device)
        }

        fun load(device: LogicalDevice, resourcePath: String, stage: Int? = null) : Shader {
            val code = compile(resourcePath)
            val id = create(device, code)
            val finalStage = stage ?: shadercKindToVulkanKind(senseShaderKind(Resources.getFile(resourcePath)))
            return Shader(id, finalStage, device)
        }
        fun create(device: LogicalDevice, byteCode: ByteBuffer) : Long {
            MemoryStack.stackPush().use { stack ->
                val createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(byteCode)

                val lp = stack.mallocLong(1)
                checkVk(vkCreateShaderModule(device.device, createInfo, null, lp))
                MemoryUtil.memFree(byteCode)
                return lp[0]
            }
        }

        fun compile(resourcePath: String) : ByteBuffer {
            return compile(Resources.getFile(resourcePath))
        }

        fun compile(shaderSrc: File) : ByteBuffer {
            var compiler: Long? = null
            var result: Long? = null
            try {
                compiler = Shaderc.shaderc_compiler_initialize()

                val kind = senseShaderKind(shaderSrc)
                val source = Resources.read(shaderSrc)

                result = Shaderc.shaderc_compile_into_spv(compiler, source, kind, shaderSrc.name, "main", 0)
                val status = Shaderc.shaderc_result_get_compilation_status(result)
                if (status != Shaderc.shaderc_compilation_status_success) {
                    val msg = Shaderc.shaderc_result_get_error_message(result)
                    when (status) {
                        Shaderc.shaderc_compilation_status_compilation_error -> throw RuntimeException("Failed to compile shader: Compilation Error: $msg")
                        Shaderc.shaderc_compilation_status_configuration_error -> throw RuntimeException("Failed to compile shader: Configuration Error: $msg")
                        Shaderc.shaderc_compilation_status_internal_error -> throw RuntimeException("Failed to compile shader: Internal Error: $msg")
                        Shaderc.shaderc_compilation_status_invalid_assembly -> throw RuntimeException("Failed to compile shader: Invalid Assembly: $msg")
                        Shaderc.shaderc_compilation_status_invalid_stage -> throw RuntimeException("Failed to compile shader: Invalid Stage: $msg")
                        Shaderc.shaderc_compilation_status_null_result_object -> throw RuntimeException("Failed to compile shader: Null Result Object: $msg")
                        Shaderc.shaderc_compilation_status_transformation_error -> throw RuntimeException("Failed to compile shader: Transformation Error: $msg")
                        Shaderc.shaderc_compilation_status_validation_error -> throw RuntimeException("Failed to compile shader: Validation Error: $msg")
                    }
                }
                val spv = Shaderc.shaderc_result_get_bytes(result) ?: throw RuntimeException("Failed to read shader compilation result")
                val ret = MemoryUtil.memAlloc(spv.capacity())
                MemoryUtil.memCopy(spv, ret)
                return ret
            }
            finally {
                if (result != null) {
                    Shaderc.shaderc_result_release(result)
                }
                if (compiler != null) {
                    Shaderc.shaderc_compiler_release(compiler)
                }
            }
        }

        private fun senseShaderKind(shaderSrc: File) : Int {
            return when(shaderSrc.extension) {
                "vert" -> Shaderc.shaderc_glsl_vertex_shader
                "frag" -> Shaderc.shaderc_glsl_fragment_shader
                "tesc" -> Shaderc.shaderc_glsl_tess_control_shader
                "tese" -> Shaderc.shaderc_glsl_tess_evaluation_shader
                "geom" -> Shaderc.shaderc_glsl_geometry_shader
                "comp" -> Shaderc.shaderc_glsl_compute_shader
                else -> Shaderc.shaderc_glsl_infer_from_source
            }
        }

        private fun shadercKindToVulkanKind(kind: Int) : Int {
            return when(kind) {
                Shaderc.shaderc_glsl_vertex_shader -> VK_SHADER_STAGE_VERTEX_BIT
                Shaderc.shaderc_glsl_fragment_shader -> VK_SHADER_STAGE_FRAGMENT_BIT
                Shaderc.shaderc_glsl_tess_control_shader -> VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT
                Shaderc.shaderc_glsl_tess_evaluation_shader -> VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT
                Shaderc.shaderc_glsl_geometry_shader -> VK_SHADER_STAGE_GEOMETRY_BIT
                Shaderc.shaderc_glsl_compute_shader -> VK_SHADER_STAGE_COMPUTE_BIT
                else -> VK_SHADER_STAGE_ALL
            }
        }
    }

    override fun close() {
        vkDestroyShaderModule(device.device, id, null)
    }
}

fun main(args: Array<String>) {
    val vert = Shader.compile("shaders/default.vert")
    val frag = Shader.compile("shaders/default.frag")
    println(vert.capacity())
    println(frag.capacity())
}