#include "fear_hooks.h"
#include "fear_shader.h"
#include <android/log.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <string>
#include <mutex>
#include <map>
#include <vector>
#include <algorithm>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>

#define TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_EXTENSIONS 0x1F03

// Global thread-safe structures for tracking shader sources and program attachments
static std::mutex g_shader_mutex;
static std::map<unsigned int, std::string> g_shader_hashes; // shader ID -> SHA-256 of original source
static std::map<unsigned int, std::vector<std::string>> g_program_shaders; // program ID -> attached shader hashes

// Helper function to resolve cache directory dynamically from TMPDIR env var
static std::string get_shader_cache_dir() {
    const char* tmp = getenv("TMPDIR");
    if (!tmp) {
        return "/data/data/git.artdeell.mojo/files/fear_shader_cache";
    }
    std::string tmp_str(tmp);
    size_t last_slash = tmp_str.find_last_of('/');
    if (last_slash != std::string::npos) {
        std::string base = tmp_str.substr(0, last_slash);
        return base + "/files/fear_shader_cache";
    }
    return tmp_str + "/../files/fear_shader_cache";
}

extern "C" {

const unsigned char* fear_glGetString(unsigned int name) {
    if (name == GL_VERSION) {
        return (const unsigned char*)"4.6.0 NVIDIA 545.29";
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

const unsigned char* fear_glGetStringi(unsigned int name, unsigned int index) {
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

// Export the dynamic symbols exactly so LWJGL binds directly to them
void glMemoryBarrier(unsigned int barriers) {
    typedef void (*glFlush_pfn)();
    static glFlush_pfn real_glFlush = nullptr;
    if (!real_glFlush) {
        real_glFlush = (glFlush_pfn)dlsym(RTLD_NEXT, "glFlush");
    }
    if (real_glFlush) {
        real_glFlush();
    }
    LOGI("glMemoryBarrier intercepted and flushed safely to prevent world rendering crash (Barriers: %u)", barriers);
}

void glMemoryBarrierEXT(unsigned int barriers) {
    glMemoryBarrier(barriers);
}

// Hook and stub glMemoryBarrier to prevent JVM crashes on server lobbies
void fear_glMemoryBarrier(unsigned int barriers) {
    glMemoryBarrier(barriers);
}

// Intercept and bypass glMemoryBarrierEXT
void fear_glMemoryBarrierEXT(unsigned int barriers) {
    glMemoryBarrier(barriers);
}

// Hook glShaderSource to dynamically rewrite shaders on-the-fly
void glShaderSource(unsigned int shader, int count, const char* const* string, const int* length) {
    typedef void (*glShaderSource_pfn)(unsigned int, int, const char* const*, const int*);
    static glShaderSource_pfn real_glShaderSource = nullptr;
    if (!real_glShaderSource) {
        real_glShaderSource = (glShaderSource_pfn)dlsym(RTLD_NEXT, "glShaderSource");
    }

    if (!real_glShaderSource) {
        LOGE("Failed to find real glShaderSource symbol!");
        return;
    }

    if (count <= 0 || !string || !string[0]) {
        real_glShaderSource(shader, count, string, length);
        return;
    }

    // Allocate memory and combine multiple source blocks if needed
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

    // Phase 4: Generate SHA-256 hash of the original desktop GLSL string
    std::string original_hash = calculate_sha256(full_source);
    {
        std::lock_guard<std::mutex> lock(g_shader_mutex);
        g_shader_hashes[shader] = original_hash;
    }

    // Phase 2: Detect shader type to pass isFragment parameter accurately
    GLint shader_type = 0;
    typedef void (*glGetShaderiv_pfn)(unsigned int, unsigned int, int*);
    static glGetShaderiv_pfn real_glGetShaderiv = nullptr;
    if (!real_glGetShaderiv) {
        real_glGetShaderiv = (glGetShaderiv_pfn)dlsym(RTLD_NEXT, "glGetShaderiv");
    }
    if (real_glGetShaderiv) {
        real_glGetShaderiv(shader, 0x8B37 /* GL_SHADER_TYPE */, &shader_type);
    }
    bool isFragment = (shader_type == 0x8B30 /* GL_FRAGMENT_SHADER */);

    // Translate GLSL BEFORE it reaches the native mobile GPU driver
    std::string translated = FearTranslateGLSL(full_source.c_str(), isFragment);

    const char* translated_cstr = translated.c_str();
    real_glShaderSource(shader, 1, &translated_cstr, nullptr);
    LOGI("glShaderSource intercepted and transpiled dynamically on-the-fly (Shader: %u, Hash: %s)", shader, original_hash.c_str());
}

// Export fear_glShaderSource alias
void fear_glShaderSource(unsigned int shader, int count, const char* const* string, const int* length) {
    glShaderSource(shader, count, string, length);
}

// Hook glCompileShader
void glCompileShader(unsigned int shader) {
    typedef void (*glCompileShader_pfn)(unsigned int);
    static glCompileShader_pfn real_glCompileShader = nullptr;
    if (!real_glCompileShader) {
        real_glCompileShader = (glCompileShader_pfn)dlsym(RTLD_NEXT, "glCompileShader");
    }
    if (real_glCompileShader) {
        real_glCompileShader(shader);
        LOGI("glCompileShader executed (Shader: %u)", shader);
    }
}

// Hook glAttachShader to track which shader hashes are attached to which program
void glAttachShader(unsigned int program, unsigned int shader) {
    typedef void (*glAttachShader_pfn)(unsigned int, unsigned int);
    static glAttachShader_pfn real_glAttachShader = nullptr;
    if (!real_glAttachShader) {
        real_glAttachShader = (glAttachShader_pfn)dlsym(RTLD_NEXT, "glAttachShader");
    }
    if (real_glAttachShader) {
        real_glAttachShader(program, shader);
    }

    std::string sh_hash = "";
    {
        std::lock_guard<std::mutex> lock(g_shader_mutex);
        if (g_shader_hashes.find(shader) != g_shader_hashes.end()) {
            sh_hash = g_shader_hashes[shader];
        }
    }
    if (!sh_hash.empty()) {
        std::lock_guard<std::mutex> lock(g_shader_mutex);
        auto& list = g_program_shaders[program];
        if (std::find(list.begin(), list.end(), sh_hash) == list.end()) {
            list.push_back(sh_hash);
        }
    }
}

// Hook glDetachShader to remove tracked shader hashes
void glDetachShader(unsigned int program, unsigned int shader) {
    typedef void (*glDetachShader_pfn)(unsigned int, unsigned int);
    static glDetachShader_pfn real_glDetachShader = nullptr;
    if (!real_glDetachShader) {
        real_glDetachShader = (glDetachShader_pfn)dlsym(RTLD_NEXT, "glDetachShader");
    }
    if (real_glDetachShader) {
        real_glDetachShader(program, shader);
    }

    std::string sh_hash = "";
    {
        std::lock_guard<std::mutex> lock(g_shader_mutex);
        if (g_shader_hashes.find(shader) != g_shader_hashes.end()) {
            sh_hash = g_shader_hashes[shader];
        }
    }
    if (!sh_hash.empty()) {
        std::lock_guard<std::mutex> lock(g_shader_mutex);
        auto& list = g_program_shaders[program];
        auto it = std::find(list.begin(), list.end(), sh_hash);
        if (it != list.end()) {
            list.erase(it);
        }
    }
}

// Hook glDeleteShader to prevent leaks
void glDeleteShader(unsigned int shader) {
    typedef void (*glDeleteShader_pfn)(unsigned int);
    static glDeleteShader_pfn real_glDeleteShader = nullptr;
    if (!real_glDeleteShader) {
        real_glDeleteShader = (glDeleteShader_pfn)dlsym(RTLD_NEXT, "glDeleteShader");
    }
    if (real_glDeleteShader) {
        real_glDeleteShader(shader);
    }
    std::lock_guard<std::mutex> lock(g_shader_mutex);
    g_shader_hashes.erase(shader);
}

// Hook glDeleteProgram to prevent leaks
void glDeleteProgram(unsigned int program) {
    typedef void (*glDeleteProgram_pfn)(unsigned int);
    static glDeleteProgram_pfn real_glDeleteProgram = nullptr;
    if (!real_glDeleteProgram) {
        real_glDeleteProgram = (glDeleteProgram_pfn)dlsym(RTLD_NEXT, "glDeleteProgram");
    }
    if (real_glDeleteProgram) {
        real_glDeleteProgram(program);
    }
    std::lock_guard<std::mutex> lock(g_shader_mutex);
    g_program_shaders.erase(program);
}

// Hook glLinkProgram to implement program binary caching
void glLinkProgram(unsigned int program) {
    typedef void (*glLinkProgram_pfn)(unsigned int);
    static glLinkProgram_pfn real_glLinkProgram = nullptr;
    if (!real_glLinkProgram) {
        real_glLinkProgram = (glLinkProgram_pfn)dlsym(RTLD_NEXT, "glLinkProgram");
    }

    std::vector<std::string> hashes;
    {
        std::lock_guard<std::mutex> lock(g_shader_mutex);
        if (g_program_shaders.find(program) != g_program_shaders.end()) {
            hashes = g_program_shaders[program];
        }
    }

    // Generate unique program hash from all attached shader hashes
    std::sort(hashes.begin(), hashes.end());
    std::string concat = "";
    for (const auto& h : hashes) {
        concat += h;
    }

    std::string program_hash = "";
    if (!concat.empty()) {
        program_hash = calculate_sha256(concat);
    }

    bool loaded_from_cache = false;
    std::string cache_dir = get_shader_cache_dir();
    std::string cache_path = cache_dir + "/" + program_hash + ".bin";

    if (!program_hash.empty()) {
        FILE* f = fopen(cache_path.c_str(), "rb");
        if (f) {
            // Find length
            fseek(f, 0, SEEK_END);
            long file_size = ftell(f);
            fseek(f, 0, SEEK_SET);

            if (file_size > 4) {
                unsigned int binary_format = 0;
                fread(&binary_format, sizeof(unsigned int), 1, f);
                long data_size = file_size - 4;
                void* binary_data = malloc(data_size);
                if (binary_data) {
                    fread(binary_data, 1, data_size, f);
                    fclose(f);
                    f = nullptr;

                    typedef void (*glProgramBinary_pfn)(unsigned int, unsigned int, const void*, int);
                    static glProgramBinary_pfn real_glProgramBinary = nullptr;
                    if (!real_glProgramBinary) {
                        real_glProgramBinary = (glProgramBinary_pfn)dlsym(RTLD_NEXT, "glProgramBinary");
                    }

                    if (real_glProgramBinary) {
                        real_glProgramBinary(program, binary_format, binary_data, data_size);

                        // Verify link status
                        int link_status = 0;
                        typedef void (*glGetProgramiv_pfn)(unsigned int, unsigned int, int*);
                        static glGetProgramiv_pfn real_glGetProgramiv = nullptr;
                        if (!real_glGetProgramiv) {
                            real_glGetProgramiv = (glGetProgramiv_pfn)dlsym(RTLD_NEXT, "glGetProgramiv");
                        }
                        if (real_glGetProgramiv) {
                            real_glGetProgramiv(program, 0x8B82 /* GL_LINK_STATUS */, &link_status);
                        }
                        if (link_status) {
                            loaded_from_cache = true;
                            LOGI("Shader Program binary LOADED INSTANTLY from cache (Program: %u, Hash: %s)", program, program_hash.c_str());
                        } else {
                            LOGW("Shader Program binary cached but failed to load, relinking... (Program: %u)", program);
                        }
                    }
                    free(binary_data);
                }
            }
            if (f) fclose(f);
        }
    }

    if (!loaded_from_cache) {
        if (real_glLinkProgram) {
            real_glLinkProgram(program);
        }

        // Check if link succeeded, if so save binary
        int link_status = 0;
        typedef void (*glGetProgramiv_pfn)(unsigned int, unsigned int, int*);
        static glGetProgramiv_pfn real_glGetProgramiv = nullptr;
        if (!real_glGetProgramiv) {
            real_glGetProgramiv = (glGetProgramiv_pfn)dlsym(RTLD_NEXT, "glGetProgramiv");
        }
        if (real_glGetProgramiv) {
            real_glGetProgramiv(program, 0x8B82 /* GL_LINK_STATUS */, &link_status);
        }

        if (link_status && !program_hash.empty()) {
            int binary_len = 0;
            if (real_glGetProgramiv) {
                real_glGetProgramiv(program, 0x8741 /* GL_PROGRAM_BINARY_LENGTH */, &binary_len);
            }
            if (binary_len > 0) {
                void* binary_data = malloc(binary_len);
                unsigned int binary_format = 0;
                int written_len = 0;

                typedef void (*glGetProgramBinary_pfn)(unsigned int, int, int*, unsigned int*, void*);
                static glGetProgramBinary_pfn real_glGetProgramBinary = nullptr;
                if (!real_glGetProgramBinary) {
                    real_glGetProgramBinary = (glGetProgramBinary_pfn)dlsym(RTLD_NEXT, "glGetProgramBinary");
                }

                if (real_glGetProgramBinary && binary_data) {
                    real_glGetProgramBinary(program, binary_len, &written_len, &binary_format, binary_data);

                    // Save to local cache directory
                    mkdir(cache_dir.c_str(), 0777);
                    FILE* out = fopen(cache_path.c_str(), "wb");
                    if (out) {
                        fwrite(&binary_format, sizeof(unsigned int), 1, out);
                        fwrite(binary_data, 1, written_len, out);
                        fclose(out);
                        LOGI("Shader Program binary SAVED to cache (Program: %u, Hash: %s)", program, program_hash.c_str());
                    }
                }
                if (binary_data) free(binary_data);
            }
        }
    }
}

// Override eglGetProcAddress to proxy glMemoryBarrier and glShaderSource safely to prevent LWJGL 3 crashes
void* eglGetProcAddress(const char* procname) {
    if (procname == nullptr) return nullptr;

    if (strcmp(procname, "glMemoryBarrier") == 0 || strcmp(procname, "glMemoryBarrierEXT") == 0) {
        LOGI("eglGetProcAddress: Intercepted and returned custom glMemoryBarrier proxy!");
        return (void*)glMemoryBarrier;
    }
    if (strcmp(procname, "glShaderSource") == 0 || strcmp(procname, "glShaderSourceARB") == 0) {
        LOGI("eglGetProcAddress: Intercepted and returned custom glShaderSource proxy!");
        return (void*)glShaderSource;
    }
    if (strcmp(procname, "glCompileShader") == 0 || strcmp(procname, "glCompileShaderARB") == 0) {
        LOGI("eglGetProcAddress: Intercepted and returned custom glCompileShader proxy!");
        return (void*)glCompileShader;
    }
    if (strcmp(procname, "glAttachShader") == 0) {
        return (void*)glAttachShader;
    }
    if (strcmp(procname, "glDetachShader") == 0) {
        return (void*)glDetachShader;
    }
    if (strcmp(procname, "glLinkProgram") == 0) {
        return (void*)glLinkProgram;
    }
    if (strcmp(procname, "glDeleteShader") == 0) {
        return (void*)glDeleteShader;
    }
    if (strcmp(procname, "glDeleteProgram") == 0) {
        return (void*)glDeleteProgram;
    }
    if (strcmp(procname, "glGetString") == 0) {
        return (void*)fear_glGetString;
    }
    if (strcmp(procname, "glGetStringi") == 0) {
        return (void*)fear_glGetStringi;
    }

    // Call real eglGetProcAddress
    typedef void* (*eglGetProcAddress_pfn)(const char*);
    static eglGetProcAddress_pfn real_eglGetProcAddress = nullptr;
    if (!real_eglGetProcAddress) {
        real_eglGetProcAddress = (eglGetProcAddress_pfn)dlsym(RTLD_NEXT, "eglGetProcAddress");
    }
    if (real_eglGetProcAddress) {
        return real_eglGetProcAddress(procname);
    }

    // Fallback to dlsym
    return dlsym(RTLD_NEXT, procname);
}

} // extern "C"

void initialize_fear_hooks() {
    LOGI("Fear Hooking Engine successfully activated.");
}
