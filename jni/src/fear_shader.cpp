#include "fear_shader.h"
#include "fear_shader_engine.h"
#include <string>
#include <string_view>
#include <android/log.h>
#include <iomanip>
#include <sstream>

#define TAG "FEAR_RENDERER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

std::string calculate_sha256(const std::string& str) {
    unsigned int h0 = 0x6a09e667, h1 = 0xbb67ae85, h2 = 0x3c6ef372, h3 = 0xa54ff53a;
    unsigned int h4 = 0x510e527f, h5 = 0x9b05688c, h6 = 0x1f83d9ab, h7 = 0x5be0cd19;

    static const unsigned int k[64] = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    };

    std::string msg = str;
    uint64_t bit_len = msg.size() * 8;
    msg += (char)0x80;
    while ((msg.size() + 8) % 64 != 0) {
        msg += (char)0x00;
    }
    for (int i = 7; i >= 0; --i) {
        msg += (char)((bit_len >> (i * 8)) & 0xff);
    }

    for (size_t chunk = 0; chunk < msg.size(); chunk += 64) {
        unsigned int w[64];
        for (int i = 0; i < 16; ++i) {
            w[i] = ((unsigned char)msg[chunk + i * 4] << 24) |
                   ((unsigned char)msg[chunk + i * 4 + 1] << 16) |
                   ((unsigned char)msg[chunk + i * 4 + 2] << 8) |
                   ((unsigned char)msg[chunk + i * 4 + 3]);
        }
        for (int i = 16; i < 64; ++i) {
            unsigned int s0 = ((w[i-15] >> 7) | (w[i-15] << 25)) ^
                              ((w[i-15] >> 18) | (w[i-15] << 14)) ^
                              (w[i-15] >> 3);
            unsigned int s1 = ((w[i-2] >> 17) | (w[i-2] << 15)) ^
                              ((w[i-2] >> 19) | (w[i-2] << 13)) ^
                              (w[i-2] >> 10);
            w[i] = w[i-16] + s0 + w[i-7] + s1;
        }

        unsigned int a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, h = h7;
        for (int i = 0; i < 64; ++i) {
            unsigned int s1 = ((e >> 6) | (e << 26)) ^
                              ((e >> 11) | (e << 21)) ^
                              ((e >> 25) | (e << 7));
            unsigned int ch = (e & f) ^ (~e & g);
            unsigned int temp1 = h + s1 + ch + k[i] + w[i];
            unsigned int s0 = ((a >> 2) | (a << 30)) ^
                              ((a >> 13) | (a << 19)) ^
                              ((a >> 22) | (a << 10));
            unsigned int maj = (a & b) ^ (a & c) ^ (b & c);
            unsigned int temp2 = s0 + maj;

            h = g;
            g = f;
            f = e;
            e = d + temp1;
            d = c;
            c = b;
            b = a;
            a = temp1 + temp2;
        }

        h0 += a; h1 += b; h2 += c; h3 += d;
        h4 += e; h5 += f; h6 += g; h7 += h;
    }

    std::stringstream ss;
    ss << std::hex << std::setfill('0');
    ss << std::setw(8) << h0 << std::setw(8) << h1 << std::setw(8) << h2 << std::setw(8) << h3
       << std::setw(8) << h4 << std::setw(8) << h5 << std::setw(8) << h6 << std::setw(8) << h7;
    return ss.str();
}

std::string FearTranslateGLSL(const char* source, bool isFragment) {
    if (!source) return "";

    std::string glsl_code(source);

    // Detect the #version directive (e.g., 120, 150, 330). If it's not ES, replace it with #version 320 es (or 300 es)
    size_t version_pos = glsl_code.find("#version");
    if (version_pos != std::string::npos) {
        size_t end_line = glsl_code.find("\n", version_pos);
        if (end_line != std::string::npos) {
            std::string version_line = glsl_code.substr(version_pos, end_line - version_pos);
            if (version_line.find("es") == std::string::npos) {
                glsl_code.replace(version_pos, end_line - version_pos, "#version 320 es");
            }
        }
    } else {
        glsl_code = "#version 320 es\n" + glsl_code;
    }

    // If it is a Fragment Shader (isFragment == true), inject precision highp float; immediately after the version directive.
    if (isFragment) {
        size_t post_version_pos = glsl_code.find("#version");
        if (post_version_pos != std::string::npos) {
            size_t end_line = glsl_code.find("\n", post_version_pos);
            if (end_line != std::string::npos) {
                glsl_code.insert(end_line + 1, "precision highp float;\nprecision highp int;\nprecision highp sampler2D;\nprecision highp sampler2DShadow;\n");
            }
        } else {
            glsl_code = "precision highp float;\nprecision highp int;\nprecision highp sampler2D;\nprecision highp sampler2DShadow;\n" + glsl_code;
        }
    }

    // Replace all instances of texture2D with texture.
    size_t pos = 0;
    while ((pos = glsl_code.find("texture2D", pos)) != std::string::npos) {
        glsl_code.replace(pos, 9, "texture");
        pos += 7;
    }

    // Replace all instances of textureCube with texture.
    pos = 0;
    while ((pos = glsl_code.find("textureCube", pos)) != std::string::npos) {
        glsl_code.replace(pos, 11, "texture");
        pos += 7;
    }

    // Replace gl_FragColor with a custom output variable out vec4 FragColor; (and update the final output assignment).
    if (isFragment && glsl_code.find("gl_FragColor") != std::string::npos) {
        if (glsl_code.find("out vec4 FragColor;") == std::string::npos) {
            size_t insert_pos = 0;
            size_t post_version_pos = glsl_code.find("#version");
            if (post_version_pos != std::string::npos) {
                size_t end_line = glsl_code.find("\n", post_version_pos);
                if (end_line != std::string::npos) {
                    insert_pos = end_line + 1;
                }
            }
            glsl_code.insert(insert_pos, "out vec4 FragColor;\n");
        }
        pos = 0;
        while ((pos = glsl_code.find("gl_FragColor", pos)) != std::string::npos) {
            glsl_code.replace(pos, 12, "FragColor");
            pos += 9;
        }
    }

    // Remove or comment out desktop-only extensions like #extension GL_ARB_geometry_shader : enable
    size_t ext_pos = 0;
    while ((ext_pos = glsl_code.find("#extension", ext_pos)) != std::string::npos) {
        size_t end_line = glsl_code.find("\n", ext_pos);
        if (end_line != std::string::npos) {
            std::string ext_line = glsl_code.substr(ext_pos, end_line - ext_pos);
            if (ext_line.find("GL_ARB") != std::string::npos ||
                ext_line.find("geometry_shader") != std::string::npos ||
                ext_line.find("gpu_shader4") != std::string::npos) {
                glsl_code.insert(ext_pos, "// ");
                ext_pos += 3;
            }
        }
        ext_pos = glsl_code.find("#extension", ext_pos + 1);
    }

    // Standard high-core shader engine structures
    ShaderEngine engine;
    engine.vkState.enableVulkanBridge = true;
    engine.pipeline.enableMRT = true;

    // Map noperspective to flat fallback safely
    pos = 0;
    while ((pos = glsl_code.find("noperspective", pos)) != std::string::npos) {
        glsl_code.replace(pos, 13, "flat");
        pos += 4;
    }

    // Translate desktop layout qualifiers
    pos = 0;
    while ((pos = glsl_code.find("layout(binding = ", pos)) != std::string::npos) {
        size_t end_bracket = glsl_code.find(")", pos);
        if (end_bracket != std::string::npos) {
            glsl_code.replace(pos, end_bracket + 1 - pos, "/* layout binding mapped */");
        } else {
            pos += 17;
        }
    }

    return glsl_code;
}

const char* translate_glsl_shader_on_the_fly(const char* source) {
    if (!source) return nullptr;
    std::string translated = FearTranslateGLSL(source, false);
    char* result = (char*)malloc(translated.size() + 1);
    strcpy(result, translated.c_str());
    return result;
}
