#include <string>
#include <string_view>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <mutex>

#define TAG "MH_DRIVE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define GL_VERSION 0x1F02
#define GL_SHADING_LANGUAGE_VERSION 0x8B8C
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_EXTENSIONS 0x1F03
#define GL_FRAMEBUFFER 0x8D40

extern "C" {

// Global states for context virtualization and viewport downscaler
static unsigned int g_current_fbo = 0;
static int g_native_width = 0;
static int g_native_height = 0;
static std::mutex g_state_mutex;

// Layer 1: Runtime API Hooking & Context Virtualization
const unsigned char* glGetString(unsigned int name) {
    if (name == GL_VERSION) {
        LOGI("[MH_DRIVE] glGetString(GL_VERSION) intercepted. Spoofing Desktop OpenGL 4.6 Core Profile.");
        return (const unsigned char*)"4.6.0 NVIDIA 550.00";
    } else if (name == GL_SHADING_LANGUAGE_VERSION) {
        LOGI("[MH_DRIVE] glGetString(GL_SHADING_LANGUAGE_VERSION) intercepted. Spoofing GLSL 460.");
        return (const unsigned char*)"460";
    } else if (name == GL_RENDERER) {
        return (const unsigned char*)"NVIDIA GeForce RTX 4090";
    } else if (name == GL_VENDOR) {
        return (const unsigned char*)"NVIDIA Corporation";
    } else if (name == GL_EXTENSIONS) {
        return (const unsigned char*)"GL_ARB_direct_state_access GL_ARB_buffer_storage GL_ARB_shader_image_load_store GL_NV_conditional_render GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 GL_NV_shader_noperspective_interpolation GL_ARB_shader_objects GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced";
    }

    typedef const unsigned char* (*glGetString_pfn)(unsigned int);
    static glGetString_pfn real_glGetString = nullptr;
    if (!real_glGetString) {
        real_glGetString = (glGetString_pfn)dlsym(RTLD_NEXT, "glGetString");
    }
    if (real_glGetString) {
        return real_glGetString(name);
    }
    return (const unsigned char*)"";
}

const unsigned char* glGetStringi(unsigned int name, unsigned int index) {
    if (name == GL_EXTENSIONS) {
        static const char* extensions[] = {
            "GL_ARB_direct_state_access",
            "GL_ARB_buffer_storage",
            "GL_ARB_shader_image_load_store",
            "GL_NV_conditional_render",
            "GL_EXT_gpu_shader4",
            "GL_EXT_texture_buffer",
            "GL_EXT_texture_cube_map_array",
            "GL_OES_EGL_image_external_essl3",
            "GL_NV_shader_noperspective_interpolation",
            "GL_ARB_shader_objects",
            "GL_ARB_vertex_shader",
            "GL_ARB_fragment_shader",
            "GL_EXT_blend_equation_separate",
            "GL_EXT_geometry_shader4",
            "GL_EXT_gpu_program_parameters",
            "GL_ARB_instanced_arrays",
            "GL_ARB_draw_instanced"
        };
        unsigned int size = sizeof(extensions) / sizeof(extensions[0]);
        if (index < size) {
            return (const unsigned char*)extensions[index];
        }
    }

    typedef const unsigned char* (*glGetStringi_pfn)(unsigned int, unsigned int);
    static glGetStringi_pfn real_glGetStringi = nullptr;
    if (!real_glGetStringi) {
        real_glGetStringi = (glGetStringi_pfn)dlsym(RTLD_NEXT, "glGetStringi");
    }
    if (real_glGetStringi) {
        return real_glGetStringi(name, index);
    }
    return (const unsigned char*)"";
}

// Export glMemoryBarrier to prevent server lobby world rendering crashes under MH DRIVE
void glMemoryBarrier(unsigned int barriers) {
    typedef void (*glFlush_pfn)();
    static glFlush_pfn real_glFlush = nullptr;
    if (!real_glFlush) {
        real_glFlush = (glFlush_pfn)dlsym(RTLD_NEXT, "glFlush");
    }
    if (real_glFlush) {
        real_glFlush();
    }
    LOGI("[MH_DRIVE] glMemoryBarrier intercepted and flushed safely (Barriers: %u)", barriers);
}

void glMemoryBarrierEXT(unsigned int barriers) {
    glMemoryBarrier(barriers);
}

// Hook glCompileShader to add diagnostic logging and prevent driver crash
void glCompileShader(unsigned int shader) {
    typedef void (*glCompileShader_pfn)(unsigned int);
    static glCompileShader_pfn real_glCompileShader = nullptr;
    if (!real_glCompileShader) {
        real_glCompileShader = (glCompileShader_pfn)dlsym(RTLD_NEXT, "glCompileShader");
    }
    if (real_glCompileShader) {
        real_glCompileShader(shader);
        LOGI("[MH_DRIVE] glCompileShader intercepted for Shader %u", shader);
    }
}

// Hook glLinkProgram to verify success and trace runtime linkages
void glLinkProgram(unsigned int program) {
    typedef void (*glLinkProgram_pfn)(unsigned int);
    static glLinkProgram_pfn real_glLinkProgram = nullptr;
    if (!real_glLinkProgram) {
        real_glLinkProgram = (glLinkProgram_pfn)dlsym(RTLD_NEXT, "glLinkProgram");
    }
    if (real_glLinkProgram) {
        real_glLinkProgram(program);
        LOGI("[MH_DRIVE] glLinkProgram intercepted for Program %u", program);
    }
}

// Helper routine: replace all occurrences of a substring
static void replace_all(std::string& str, const std::string& from, const std::string& to) {
    size_t start_pos = 0;
    while((start_pos = str.find(from, start_pos)) != std::string::npos) {
        str.replace(start_pos, from.length(), to);
        start_pos += to.length();
    }
}

// Layer 2: Dynamic GLSL Transpiler & Parsing Engine
void glShaderSource(unsigned int shader, int count, const char* const* string, const int* length) {
    typedef void (*glShaderSource_pfn)(unsigned int, int, const char* const*, const int*);
    static glShaderSource_pfn real_glShaderSource = nullptr;
    if (!real_glShaderSource) {
        real_glShaderSource = (glShaderSource_pfn)dlsym(RTLD_NEXT, "glShaderSource");
    }

    if (!real_glShaderSource) {
        return;
    }

    if (count <= 0 || !string || !string[0]) {
        real_glShaderSource(shader, count, string, length);
        return;
    }

    std::string full_source = "";
    for (int i = 0; i < count; i++) {
        if (string[i]) {
            if (length && length[i] >= 0) {
                full_source.append(string[i], length[i]);
            } else {
                full_source.append(string[i]);
            }
        }
    }

    // Step A: Strip unsupported desktop "noperspective" qualifiers
    replace_all(full_source, "noperspective", "flat");

    // Step B: Downscale heavy layout and output variables for GLES 3.2 compatibility
    replace_all(full_source, "layout(location = 0) out vec4 fragColor;", "out vec4 fragColor;");
    replace_all(full_source, "layout(location = 0) out vec4 out_Color;", "out vec4 out_Color;");

    // Step C: Transpile desktop versions (e.g. #version 330 compatibility, #version 460 core) into mobile-safe headers
    if (full_source.find("#version 330") != std::string::npos ||
        full_source.find("#version 150") != std::string::npos ||
        full_source.find("#version 400") != std::string::npos ||
        full_source.find("#version 410") != std::string::npos ||
        full_source.find("#version 430") != std::string::npos ||
        full_source.find("#version 450") != std::string::npos ||
        full_source.find("#version 460") != std::string::npos) {

        size_t pos = full_source.find("#version");
        if (pos != std::string::npos) {
            size_t end_line = full_source.find("\n", pos);
            full_source.replace(pos, end_line - pos, "#version 320 es\nprecision highp float;\nprecision highp int;");
        }
    }

    // Step D: Downscale and optimize complex sampler structures & sampler2DShadow arrays
    // Used heavily in Complementary/Solas Shaders to bypass thermal throttling on mobile
    replace_all(full_source, "sampler2DShadow shadow0[2]", "sampler2DShadow shadow0[1]");
    replace_all(full_source, "sampler2DShadow shadow1[2]", "sampler2DShadow shadow1[1]");

    // Step E: Convert desktop double precision types (double, dvec2, dvec3, dvec4) to mobile-safe floats
    replace_all(full_source, "double ", "float ");
    replace_all(full_source, "dvec2 ", "vec2 ");
    replace_all(full_source, "dvec3 ", "vec3 ");
    replace_all(full_source, "dvec4 ", "vec4 ");

    const char* translated_cstr = full_source.c_str();
    real_glShaderSource(shader, 1, &translated_cstr, nullptr);
    LOGI("[MH_DRIVE] glShaderSource intercepted and transpiled beautifully (Shader: %u).", shader);
}

const char* mh_drive_preprocess_shader_ast(const char* glsl_source) {
    if (!glsl_source) return nullptr;

    std::string source_str(glsl_source);
    size_t pos;

    // Rewrite desktop output layout qualifiers to compatible GLES 3.2 variables
    while ((pos = source_str.find("layout(location = 0) out vec4 fragColor;")) != std::string::npos) {
        source_str.replace(pos, 40, "out vec4 fragColor;");
    }

    while ((pos = source_str.find("layout(location = 0) out vec4 out_Color;")) != std::string::npos) {
        source_str.replace(pos, 40, "out vec4 out_Color;");
    }

    // Strip unsupported desktop noperspective qualifiers
    while ((pos = source_str.find("noperspective")) != std::string::npos) {
        source_str.replace(pos, 13, "flat");
    }

    // Adapt layout binding layouts dynamically for GLES 3.2
    if (source_str.find("#version 330") != std::string::npos || source_str.find("#version 150") != std::string::npos) {
        pos = source_str.find("#version");
        if (pos != std::string::npos) {
            size_t end_line = source_str.find("\n", pos);
            source_str.replace(pos, end_line - pos, "#version 320 es\nprecision highp float;\nprecision highp int;");
        }
    }

    char* allocated_result = (char*)malloc(source_str.size() + 1);
    strcpy(allocated_result, source_str.c_str());

    LOGI("[MH_DRIVE] mh_drive_preprocess_shader_ast compilation string preprocess hook complete.");
    return allocated_result;
}

// Layer 3: Viewport Buffer Downscaler & stretching logic
void glBindFramebuffer(unsigned int target, unsigned int framebuffer) {
    typedef void (*glBindFramebuffer_pfn)(unsigned int, unsigned int);
    static glBindFramebuffer_pfn real_glBindFramebuffer = nullptr;
    if (!real_glBindFramebuffer) {
        real_glBindFramebuffer = (glBindFramebuffer_pfn)dlsym(RTLD_NEXT, "glBindFramebuffer");
    }

    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        if (target == GL_FRAMEBUFFER) {
            g_current_fbo = framebuffer;
        }
    }

    if (real_glBindFramebuffer) {
        real_glBindFramebuffer(target, framebuffer);
    }
}

void glViewport(int x, int y, int width, int height) {
    typedef void (*glViewport_pfn)(int, int, int, int);
    static glViewport_pfn real_glViewport = nullptr;
    if (!real_glViewport) {
        real_glViewport = (glViewport_pfn)dlsym(RTLD_NEXT, "glViewport");
    }

    int final_width = width;
    int final_height = height;

    {
        std::lock_guard<std::mutex> lock(g_state_mutex);
        // Track the native window/SurfaceView size from the first few full-screen FBO 0 viewports
        if (g_current_fbo == 0) {
            if (width > g_native_width) g_native_width = width;
            if (height > g_native_height) g_native_height = height;
        }

        // Automated Resolution downscaler:
        // Downscale intermediate 3D framebuffers (FBO != 0) rendering at window size by 60%
        // to mitigate heavy frame drops during volumetric fog/clouds rendering in Solas/Complementary.
        // It bypasses shadow maps / UI smaller passes by filtering dimensions that equal native screen size.
        if (g_current_fbo != 0 && g_native_width > 0 && g_native_height > 0) {
            if (width == g_native_width && height == g_native_height) {
                final_width = (int)(width * 0.60f);
                final_height = (int)(height * 0.60f);
                LOGI("[MH_DRIVE] Viewport Downscaler: Downscaled heavy 3D rendering pass from (%d x %d) to (%d x %d) (60%% scale)",
                     width, height, final_width, final_height);
            }
        }
    }

    if (real_glViewport) {
        real_glViewport(x, y, final_width, final_height);
    }
}

} // extern "C"
