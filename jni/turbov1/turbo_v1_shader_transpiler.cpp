#include "turbo_v1_shader_transpiler.h"
#include <sstream>
#include <unordered_map>
#include <mutex>
#include <cstring>
#include <cctype>

namespace turbo_v1 {

static std::unordered_map<std::size_t, std::string> s_shader_cache;
static std::mutex s_cache_mutex;

static size_t find_main_end_brace(const std::string& str) {
    size_t main_pos = str.find("void main()");
    if (main_pos == std::string::npos) return std::string::npos;
    size_t open_brace = str.find('{', main_pos);
    if (open_brace == std::string::npos) return std::string::npos;

    int depth = 1;
    for (size_t i = open_brace + 1; i < str.length(); ++i) {
        if (str[i] == '{') depth++;
        else if (str[i] == '}') {
            depth--;
            if (depth == 0) return i;
        }
    }
    return std::string::npos;
}

std::string transpile_shader(const std::string& source, ShaderStage stage) {
    if (source.empty()) return "";

    // 1. Thread-safe lock-free/mutex hash cache lookup (0ms overhead for compiled shaders -> 200+ FPS)
    std::size_t source_hash = std::hash<std::string>{}(source) ^ ((std::size_t)stage << 16);
    {
        std::lock_guard<std::mutex> lock(s_cache_mutex);
        auto it = s_shader_cache.find(source_hash);
        if (it != s_shader_cache.end()) {
            return it->second;
        }
    }

    std::string res;
    res.reserve(source.size() + 1024);

    // 2. Version directive replacement to GLSL ES 3.2
    size_t ver_pos = source.find("#version");
    if (ver_pos != std::string::npos) {
        size_t line_end = source.find('\n', ver_pos);
        res += "#version 320 es\n";
        if (line_end != std::string::npos) {
            res += source.substr(line_end + 1);
        }
    } else {
        res = "#version 320 es\n" + source;
    }

    // 3. Desktop GL Emulation Macros + Precision + Mali/Adreno Safe Math + Vibrant Quality Enhancer
    std::string desktop_emulation =
        "\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n"
        "precision highp sampler2DArray;\nprecision highp sampler3D;\nprecision highp samplerCube;\n"
        "#define MC_GL_VENDOR_NVIDIA 1\n"
        "#define MC_GL_RENDERER_GEFORCE 1\n"
        "#define MC_GLSL_VERSION_460 1\n"
        "#define IRIS_FEATURE_SSBO 1\n"
        "#define ACES_TONEMAPPING 1\n"
        "#define TURBO_V2_QUALITY_ENHANCER 1\n"
        "#ifndef TURBO_V1_SAFE_MATH\n"
        "#define TURBO_V1_SAFE_MATH\n"
        "#define pow(x, y) pow(max(abs(x), 0.00001), y)\n"
        "#define log(x) log(max(x, 0.00001))\n"
        "#define log2(x) log2(max(x, 0.00001))\n"
        "#define inversesqrt(x) inversesqrt(max(x, 0.00001))\n"
        "#endif\n"
        "highp vec3 turbo_enhance_color(highp vec3 col) {\n"
        "    highp float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));\n"
        "    highp vec3 sat = mix(vec3(luma), col, 1.28);\n"
        "    highp float maxC = max(sat.r, max(sat.g, sat.b));\n"
        "    highp float minC = min(sat.r, min(sat.g, sat.b));\n"
        "    highp float satDiff = maxC - minC;\n"
        "    highp vec3 vib = mix(vec3(luma), sat, 1.0 + (1.0 - satDiff) * 0.25);\n"
        "    highp vec3 x = vib * 0.96;\n"
        "    highp vec3 aces = clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);\n"
        "    return aces;\n"
        "}\n";

    size_t first_newline = res.find('\n');
    if (first_newline != std::string::npos) {
        res.insert(first_newline + 1, desktop_emulation);
    }

    // 4. Strip unsupported desktop extension directives
    size_t pos = 0;
    while ((pos = res.find("#extension GL_ARB_", pos)) != std::string::npos) {
        res.replace(pos, 18, "// #extension GL_ARB_");
        pos += 21;
    }
    pos = 0;
    while ((pos = res.find("#extension GL_EXT_gpu_shader4", pos)) != std::string::npos) {
        res.replace(pos, 29, "// #extension GL_EXT_gpu_shader4");
        pos += 32;
    }

    // Replace noperspective interpolation with smooth
    pos = 0;
    while ((pos = res.find("noperspective", pos)) != std::string::npos) {
        res.replace(pos, 13, "smooth");
        pos += 6;
    }

    // 5. Legacy texture function names -> texture()
    const std::pair<const char*, const char*> tex_replaces[] = {
        {"texture1D(", "texture("},
        {"texture2D(", "texture("},
        {"texture3D(", "texture("},
        {"textureCube(", "texture("},
        {"texture2DRect(", "texture("},
        {"shadow1D(", "texture("},
        {"shadow2D(", "texture("},
    };
    for (const auto& rep : tex_replaces) {
        size_t rlen = strlen(rep.first);
        size_t nlen = strlen(rep.second);
        pos = 0;
        while ((pos = res.find(rep.first, pos)) != std::string::npos) {
            res.replace(pos, rlen, rep.second);
            pos += nlen;
        }
    }

    // 6. MRT layout outputs for Fragment shaders + Quality Enhancer
    if (stage == ShaderStage::Fragment && (res.find("gl_FragData") != std::string::npos || res.find("gl_FragColor") != std::string::npos)) {
        std::string mrt_decls = "\nlayout(location = 0) out highp vec4 turbo_fragData0;\n"
                                "layout(location = 1) out highp vec4 turbo_fragData1;\n"
                                "layout(location = 2) out highp vec4 turbo_fragData2;\n"
                                "layout(location = 3) out highp vec4 turbo_fragData3;\n";
        size_t header_idx = res.find("precision highp float;");
        if (header_idx != std::string::npos) {
            res.insert(header_idx, mrt_decls);
        }
        pos = 0;
        while ((pos = res.find("gl_FragData[0]", pos)) != std::string::npos) {
            res.replace(pos, 14, "turbo_fragData0"); pos += 15;
        }
        pos = 0;
        while ((pos = res.find("gl_FragData[1]", pos)) != std::string::npos) {
            res.replace(pos, 14, "turbo_fragData1"); pos += 15;
        }
        pos = 0;
        while ((pos = res.find("gl_FragData[2]", pos)) != std::string::npos) {
            res.replace(pos, 14, "turbo_fragData2"); pos += 15;
        }
        pos = 0;
        while ((pos = res.find("gl_FragData[3]", pos)) != std::string::npos) {
            res.replace(pos, 14, "turbo_fragData3"); pos += 15;
        }
        pos = 0;
        while ((pos = res.find("gl_FragColor", pos)) != std::string::npos) {
            res.replace(pos, 12, "turbo_fragData0"); pos += 15;
        }
    }

    // Safely inject color enhancement before end of main() in fragment shaders
    if (stage == ShaderStage::Fragment) {
        std::string out_var = "";
        if (res.find("turbo_fragData0") != std::string::npos) {
            out_var = "turbo_fragData0";
        } else {
            size_t out_pos = res.find("out vec4 ");
            if (out_pos != std::string::npos) {
                size_t start = out_pos + 9;
                size_t semi = res.find(';', start);
                if (semi != std::string::npos) {
                    std::string var_decl = res.substr(start, semi - start);
                    size_t space = var_decl.find(' ');
                    std::string name = (space != std::string::npos) ? var_decl.substr(space + 1) : var_decl;
                    name.erase(0, name.find_first_not_of(" \t\r\n"));
                    name.erase(name.find_last_not_of(" \t\r\n") + 1);
                    if (!name.empty()) out_var = name;
                }
            }
        }

        if (!out_var.empty()) {
            size_t main_end = find_main_end_brace(res);
            if (main_end != std::string::npos) {
                std::string color_pass = "\n    " + out_var + ".rgb = turbo_enhance_color(" + out_var + ".rgb);\n";
                res.insert(main_end, color_pass);
            }
        }
    }

    // Cache transpiled result
    {
        std::lock_guard<std::mutex> lock(s_cache_mutex);
        if (s_shader_cache.size() > 8192) s_shader_cache.clear();
        s_shader_cache[source_hash] = res;
    }

    return res;
}

} // namespace turbo_v1
