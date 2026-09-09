#include "turbo_v1_quality_enhancer.h"
#include "../turbo_v1_core.h"
#include <android/log.h>
#include <sstream>
#include <iomanip>

namespace turbo_v1 {

// ============================================================================
// TurboV1 Quality Enhancer Implementation
// Beautiful Graphics for Stunning Visuals
// ============================================================================

static QualityEnhancer g_quality_enhancer_instance;

QualityEnhancer& QualityEnhancer::get_instance() {
    return g_quality_enhancer_instance;
}

QualityEnhancer& get_quality_enhancer() {
    return QualityEnhancer::get_instance();
}

QualityEnhancer::QualityEnhancer()
    : m_screen_width(0), m_screen_height(0)
{
    // Initialize with default settings
    reset_to_defaults();
    
    // Initialize GL resources
    m_gl.framebuffer = 0;
    m_gl.render_texture = 0;
    m_gl.depth_buffer = 0;
    m_gl.color_boost_shader = 0;
    m_gl.hdr_shader = 0;
    m_gl.sharpen_shader = 0;
    m_gl.bloom_shader = 0;
    m_gl.final_composite_shader = 0;
    
    LOGI("TurboV1 Quality Enhancer: Constructor initialized");
}

QualityEnhancer::~QualityEnhancer() {
    cleanup_gl_resources();
    LOGI("TurboV1 Quality Enhancer: Destructor called");
}

void QualityEnhancer::initialize() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    // Initialize OpenGL resources
    init_gl_resources();
    
    LOGI("TurboV1 Quality Enhancer: Initialized");
    LOGI("  Saturation: %.2f", m_settings.saturation);
    LOGI("  Contrast: %.2f", m_settings.contrast);
    LOGI("  Vibrance: %.2f", m_settings.vibrance);
    LOGI("  HDR: %s (Intensity: %.2f)", m_settings.hdr_enabled ? "ENABLED" : "DISABLED", m_settings.hdr_intensity);
    LOGI("  Sharpening: %s (Strength: %.2f)", m_settings.sharpening_enabled ? "ENABLED" : "DISABLED", m_settings.sharpening_strength);
}

void QualityEnhancer::set_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.enabled = enabled;
    LOGI("TurboV1 Quality Enhancer: %s", enabled ? "ENABLED" : "DISABLED");
}

bool QualityEnhancer::is_enabled() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_settings.enabled;
}

void QualityEnhancer::set_saturation(float value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.saturation = std::max(0.0f, std::min(2.0f, value));
    LOGI("TurboV1 Quality: Saturation set to %.2f", m_settings.saturation);
}

void QualityEnhancer::set_contrast(float value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.contrast = std::max(0.5f, std::min(2.0f, value));
    LOGI("TurboV1 Quality: Contrast set to %.2f", m_settings.contrast);
}

void QualityEnhancer::set_brightness(float value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.brightness = std::max(0.0f, std::min(2.0f, value));
    LOGI("TurboV1 Quality: Brightness set to %.2f", m_settings.brightness);
}

void QualityEnhancer::set_vibrance(float value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.vibrance = std::max(0.0f, std::min(2.0f, value));
    LOGI("TurboV1 Quality: Vibrance set to %.2f", m_settings.vibrance);
}

void QualityEnhancer::set_warmth(float value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.warmth = std::max(0.0f, std::min(1.0f, value));
    LOGI("TurboV1 Quality: Warmth set to %.2f", m_settings.warmth);
}

void QualityEnhancer::set_hdr_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.hdr_enabled = enabled;
    LOGI("TurboV1 Quality: HDR %s", enabled ? "ENABLED" : "DISABLED");
}

void QualityEnhancer::set_hdr_intensity(float value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.hdr_intensity = std::max(0.0f, std::min(3.0f, value));
    LOGI("TurboV1 Quality: HDR Intensity set to %.2f", m_settings.hdr_intensity);
}

void QualityEnhancer::set_hdr_brightness(float value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.hdr_brightness = std::max(0.0f, std::min(2.0f, value));
    LOGI("TurboV1 Quality: HDR Brightness set to %.2f", m_settings.hdr_brightness);
}

void QualityEnhancer::set_sharpening_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.sharpening_enabled = enabled;
    LOGI("TurboV1 Quality: Sharpening %s", enabled ? "ENABLED" : "DISABLED");
}

void QualityEnhancer::set_sharpening_strength(float value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.sharpening_strength = std::max(0.0f, std::min(3.0f, value));
    LOGI("TurboV1 Quality: Sharpening Strength set to %.2f", m_settings.sharpening_strength);
}

void QualityEnhancer::set_sharpening_radius(int value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.sharpening_radius = std::max(1, std::min(5, value));
    LOGI("TurboV1 Quality: Sharpening Radius set to %d", m_settings.sharpening_radius);
}

void QualityEnhancer::set_bloom_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.bloom_enabled = enabled;
    LOGI("TurboV1 Quality: Bloom %s", enabled ? "ENABLED" : "DISABLED");
}

void QualityEnhancer::set_bloom_intensity(float value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.bloom_intensity = std::max(0.0f, std::min(2.0f, value));
    LOGI("TurboV1 Quality: Bloom Intensity set to %.2f", m_settings.bloom_intensity);
}

void QualityEnhancer::set_bloom_threshold(float value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.bloom_threshold = std::max(0.0f, std::min(1.0f, value));
    LOGI("TurboV1 Quality: Bloom Threshold set to %.2f", m_settings.bloom_threshold);
}

void QualityEnhancer::set_film_grain_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.film_grain_enabled = enabled;
    LOGI("TurboV1 Quality: Film Grain %s", enabled ? "ENABLED" : "DISABLED");
}

void QualityEnhancer::set_film_grain_strength(float value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.film_grain_strength = std::max(0.0f, std::min(0.1f, value));
    LOGI("TurboV1 Quality: Film Grain Strength set to %.3f", m_settings.film_grain_strength);
}

void QualityEnhancer::set_antialiasing_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.antialiasing_enabled = enabled;
    LOGI("TurboV1 Quality: Anti-Aliasing %s", enabled ? "ENABLED" : "DISABLED");
}

void QualityEnhancer::set_antialiasing_samples(int samples) {
    std::lock_guard<std::mutex> lock(m_mutex);
    // Only allow valid sample counts
    if (samples == 2 || samples == 4 || samples == 8 || samples == 16) {
        m_settings.antialiasing_samples = samples;
        LOGI("TurboV1 Quality: Anti-Aliasing Samples set to %d", m_settings.antialiasing_samples);
    }
}

void QualityEnhancer::set_ambient_occlusion_enabled(bool enabled) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.ambient_occlusion_enabled = enabled;
    LOGI("TurboV1 Quality: Ambient Occlusion %s", enabled ? "ENABLED" : "DISABLED");
}

void QualityEnhancer::set_ambient_occlusion_strength(float value) {
    std::lock_guard<std::mutex> lock(m_mutex);
    m_settings.ambient_occlusion_strength = std::max(0.0f, std::min(2.0f, value));
    LOGI("TurboV1 Quality: Ambient Occlusion Strength set to %.2f", m_settings.ambient_occlusion_strength);
}

void QualityEnhancer::reset_to_defaults() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    m_settings.enabled = true;
    
    // Color defaults
    m_settings.saturation = 1.3f;
    m_settings.contrast = 1.2f;
    m_settings.brightness = 1.0f;
    m_settings.vibrance = 1.5f;
    m_settings.warmth = 0.05f;
    
    // HDR defaults
    m_settings.hdr_enabled = true;
    m_settings.hdr_intensity = 1.5f;
    m_settings.hdr_brightness = 1.2f;
    
    // Sharpening defaults
    m_settings.sharpening_enabled = true;
    m_settings.sharpening_strength = 0.8f;
    m_settings.sharpening_radius = 2;
    
    // Bloom defaults
    m_settings.bloom_enabled = true;
    m_settings.bloom_intensity = 0.3f;
    m_settings.bloom_threshold = 0.8f;
    
    // Film grain defaults
    m_settings.film_grain_enabled = false;
    m_settings.film_grain_strength = 0.02f;
    
    // Anti-aliasing defaults
    m_settings.antialiasing_enabled = true;
    m_settings.antialiasing_samples = 4;
    
    // Ambient occlusion defaults
    m_settings.ambient_occlusion_enabled = true;
    m_settings.ambient_occlusion_strength = 1.0f;
    
    LOGI("TurboV1 Quality Enhancer: Reset to defaults");
}

std::string QualityEnhancer::get_settings_summary() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    std::ostringstream oss;
    oss << std::fixed << std::setprecision(2);
    oss << "TurboV1 Quality Settings:\n";
    oss << "  Enabled: " << (m_settings.enabled ? "YES" : "NO") << "\n";
    oss << "  Saturation: " << m_settings.saturation << "\n";
    oss << "  Contrast: " << m_settings.contrast << "\n";
    oss << "  Brightness: " << m_settings.brightness << "\n";
    oss << "  Vibrance: " << m_settings.vibrance << "\n";
    oss << "  Warmth: " << m_settings.warmth << "\n";
    oss << "  HDR: " << (m_settings.hdr_enabled ? "YES" : "NO") 
        << " (Intensity: " << m_settings.hdr_intensity 
        << ", Brightness: " << m_settings.hdr_brightness << ")\n";
    oss << "  Sharpening: " << (m_settings.sharpening_enabled ? "YES" : "NO") 
        << " (Strength: " << m_settings.sharpening_strength 
        << ", Radius: " << m_settings.sharpening_radius << ")\n";
    oss << "  Bloom: " << (m_settings.bloom_enabled ? "YES" : "NO") 
        << " (Intensity: " << m_settings.bloom_intensity 
        << ", Threshold: " << m_settings.bloom_threshold << ")\n";
    oss << "  Film Grain: " << (m_settings.film_grain_enabled ? "YES" : "NO") 
        << " (Strength: " << m_settings.film_grain_strength << ")\n";
    oss << "  Anti-Aliasing: " << (m_settings.antialiasing_enabled ? "YES" : "NO") 
        << " (Samples: " << m_settings.antialiasing_samples << ")\n";
    oss << "  Ambient Occlusion: " << (m_settings.ambient_occlusion_enabled ? "YES" : "NO") 
        << " (Strength: " << m_settings.ambient_occlusion_strength << ")";
    
    return oss.str();
}

void QualityEnhancer::init_gl_resources() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    // Create framebuffer
    glGenFramebuffers(1, &m_gl.framebuffer);
    
    // Create render texture
    glGenTextures(1, &m_gl.render_texture);
    glBindTexture(GL_TEXTURE_2D, m_gl.render_texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, m_screen_width, m_screen_height, 
                0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    
    // Create depth buffer
    glGenRenderbuffers(1, &m_gl.depth_buffer);
    glBindRenderbuffer(GL_RENDERBUFFER, m_gl.depth_buffer);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, 
                          m_screen_width, m_screen_height);
    
    // Attach to framebuffer
    glBindFramebuffer(GL_FRAMEBUFFER, m_gl.framebuffer);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, 
                           GL_TEXTURE_2D, m_gl.render_texture, 0);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, 
                              GL_RENDERBUFFER, m_gl.depth_buffer);
    
    // Create shaders
    create_color_boost_shader();
    create_hdr_shader();
    create_sharpen_shader();
    create_bloom_shader();
    create_final_composite_shader();
    
    // Restore default framebuffer
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    
    LOGI("TurboV1 Quality: OpenGL resources initialized");
}

void QualityEnhancer::cleanup_gl_resources() {
    std::lock_guard<std::mutex> lock(m_mutex);
    
    if (m_gl.framebuffer) {
        glDeleteFramebuffers(1, &m_gl.framebuffer);
        m_gl.framebuffer = 0;
    }
    if (m_gl.render_texture) {
        glDeleteTextures(1, &m_gl.render_texture);
        m_gl.render_texture = 0;
    }
    if (m_gl.depth_buffer) {
        glDeleteRenderbuffers(1, &m_gl.depth_buffer);
        m_gl.depth_buffer = 0;
    }
    if (m_gl.color_boost_shader) {
        glDeleteProgram(m_gl.color_boost_shader);
        m_gl.color_boost_shader = 0;
    }
    if (m_gl.hdr_shader) {
        glDeleteProgram(m_gl.hdr_shader);
        m_gl.hdr_shader = 0;
    }
    if (m_gl.sharpen_shader) {
        glDeleteProgram(m_gl.sharpen_shader);
        m_gl.sharpen_shader = 0;
    }
    if (m_gl.bloom_shader) {
        glDeleteProgram(m_gl.bloom_shader);
        m_gl.bloom_shader = 0;
    }
    if (m_gl.final_composite_shader) {
        glDeleteProgram(m_gl.final_composite_shader);
        m_gl.final_composite_shader = 0;
    }
    
    LOGI("TurboV1 Quality: OpenGL resources cleaned up");
}

void QualityEnhancer::apply_enhancement(GLuint texture_id, int width, int height) {
    if (!m_settings.enabled) return;
    
    std::lock_guard<std::mutex> lock(m_mutex);
    
    // Store screen dimensions
    m_screen_width = width;
    m_screen_height = height;
    
    // Reinitialize resources if dimensions changed
    if (m_gl.framebuffer == 0 || 
        m_gl.render_texture == 0 ||
        m_screen_width != width || 
        m_screen_height != height) {
        cleanup_gl_resources();
        init_gl_resources();
    }
    
    // Apply color boost
    if (m_settings.saturation != 1.0f || 
        m_settings.contrast != 1.0f || 
        m_settings.brightness != 1.0f ||
        m_settings.vibrance != 1.0f ||
        m_settings.warmth != 0.0f) {
        apply_color_boost(texture_id);
    }
    
    // Apply HDR
    if (m_settings.hdr_enabled) {
        apply_hdr();
    }
    
    // Apply sharpening
    if (m_settings.sharpening_enabled) {
        apply_sharpening();
    }
    
    // Apply bloom
    if (m_settings.bloom_enabled) {
        apply_bloom();
    }
    
    // Final composite
    apply_final_composite();
}

void QualityEnhancer::apply_to_screen() {
    if (!m_settings.enabled) return;
    
    std::lock_guard<std::mutex> lock(m_mutex);
    
    // Apply enhancement to current screen
    apply_enhancement(0, m_screen_width, m_screen_height);
}

GLuint QualityEnhancer::compile_shader(GLenum type, const std::string& source) {
    GLuint shader = glCreateShader(type);
    const char* src = source.c_str();
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);
    
    // Check for errors
    GLint success;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
    if (!success) {
        GLchar info_log[512];
        glGetShaderInfoLog(shader, 512, nullptr, info_log);
        LOGE("TurboV1 Quality: Shader compilation failed: %s", info_log);
        glDeleteShader(shader);
        return 0;
    }
    
    return shader;
}

GLuint QualityEnhancer::create_program(GLuint vs, GLuint fs) {
    GLuint program = glCreateProgram();
    glAttachShader(program, vs);
    glAttachShader(program, fs);
    glLinkProgram(program);
    
    // Check for errors
    GLint success;
    glGetProgramiv(program, GL_LINK_STATUS, &success);
    if (!success) {
        GLchar info_log[512];
        glGetProgramInfoLog(program, 512, nullptr, info_log);
        LOGE("TurboV1 Quality: Program linking failed: %s", info_log);
        glDeleteProgram(program);
        return 0;
    }
    
    return program;
}

void QualityEnhancer::create_color_boost_shader() {
    // Color boost shader (saturation, contrast, brightness, vibrance, warmth)
    const char* fs_source = R"(
        precision highp float;
        uniform sampler2D u_texture;
        uniform float u_saturation;
        uniform float u_contrast;
        uniform float u_brightness;
        uniform float u_vibrance;
        uniform float u_warmth;
        varying vec2 v_tex_coord;
        
        void main() {
            vec4 color = texture2D(u_texture, v_tex_coord);
            
            // Brightness
            color.rgb *= u_brightness;
            
            // Saturation (using luminance preservation)
            float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            color.rgb = mix(vec3(luminance), color.rgb, u_saturation);
            
            // Contrast
            color.rgb = ((color.rgb - 0.5) * u_contrast) + 0.5;
            
            // Vibrance (boosts muted colors more)
            float avg = (color.r + color.g + color.b) / 3.0;
            float mx = max(color.r, max(color.g, color.b));
            float mn = min(color.r, min(color.g, color.b));
            float vib = (mx - mn) / (mx + 0.0001);
            vib = pow(vib, 2.0) * u_vibrance;
            color.rgb += (color.rgb - avg) * vib * 0.5;
            
            // Warmth (add red/yellow tint)
            color.r += u_warmth * 0.1;
            color.g += u_warmth * 0.05;
            
            gl_FragColor = color;
        }
    )";
    
    GLuint fs = compile_shader(GL_FRAGMENT_SHADER, fs_source);
    if (fs) {
        // Simple pass-through vertex shader
        const char* vs_source = R"(
            attribute vec4 a_position;
            attribute vec2 a_tex_coord;
            varying vec2 v_tex_coord;
            void main() {
                gl_Position = a_position;
                v_tex_coord = a_tex_coord;
            }
        )";
        GLuint vs = compile_shader(GL_VERTEX_SHADER, vs_source);
        if (vs) {
            m_gl.color_boost_shader = create_program(vs, fs);
            glDeleteShader(vs);
        }
        glDeleteShader(fs);
    }
}

void QualityEnhancer::create_hdr_shader() {
    // HDR shader
    const char* fs_source = R"(
        precision highp float;
        uniform sampler2D u_texture;
        uniform float u_intensity;
        uniform float u_brightness;
        varying vec2 v_tex_coord;
        
        vec3 tonemap(vec3 color) {
            // ACES filmic tonemapping
            color = (color * (2.51 * color + 0.03)) / (color * (2.43 * color + 0.59) + 0.14);
            return color;
        }
        
        void main() {
            vec4 color = texture2D(u_texture, v_tex_coord);
            
            // Apply HDR effect
            vec3 hdr_color = color.rgb * u_intensity;
            hdr_color = tonemap(hdr_color);
            hdr_color *= u_brightness;
            
            gl_FragColor = vec4(hdr_color, color.a);
        }
    )";
    
    GLuint fs = compile_shader(GL_FRAGMENT_SHADER, fs_source);
    if (fs) {
        const char* vs_source = R"(
            attribute vec4 a_position;
            attribute vec2 a_tex_coord;
            varying vec2 v_tex_coord;
            void main() {
                gl_Position = a_position;
                v_tex_coord = a_tex_coord;
            }
        )";
        GLuint vs = compile_shader(GL_VERTEX_SHADER, vs_source);
        if (vs) {
            m_gl.hdr_shader = create_program(vs, fs);
            glDeleteShader(vs);
        }
        glDeleteShader(fs);
    }
}

void QualityEnhancer::create_sharpen_shader() {
    // Sharpening shader
    const char* fs_source = R"(
        precision highp float;
        uniform sampler2D u_texture;
        uniform float u_strength;
        uniform int u_radius;
        varying vec2 v_tex_coord;
        
        void main() {
            vec4 color = texture2D(u_texture, v_tex_coord);
            vec4 sum = vec4(0.0);
            
            // Simple 3x3 sharpening kernel
            float kernel[9] = float[](
                -1.0, -1.0, -1.0,
                -1.0,  9.0, -1.0,
                -1.0, -1.0, -1.0
            );
            
            vec2 offset = 1.0 / textureSize(u_texture, 0);
            
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    int idx = (i + 1) * 3 + (j + 1);
                    vec2 uv = v_tex_coord + vec2(float(i), float(j)) * offset;
                    sum += texture2D(u_texture, uv) * kernel[idx];
                }
            }
            
            vec4 sharpened = mix(color, sum, u_strength);
            gl_FragColor = sharpened;
        }
    )";
    
    GLuint fs = compile_shader(GL_FRAGMENT_SHADER, fs_source);
    if (fs) {
        const char* vs_source = R"(
            attribute vec4 a_position;
            attribute vec2 a_tex_coord;
            varying vec2 v_tex_coord;
            void main() {
                gl_Position = a_position;
                v_tex_coord = a_tex_coord;
            }
        )";
        GLuint vs = compile_shader(GL_VERTEX_SHADER, vs_source);
        if (vs) {
            m_gl.sharpen_shader = create_program(vs, fs);
            glDeleteShader(vs);
        }
        glDeleteShader(fs);
    }
}

void QualityEnhancer::create_bloom_shader() {
    // Bloom shader
    const char* fs_source = R"(
        precision highp float;
        uniform sampler2D u_texture;
        uniform float u_intensity;
        uniform float u_threshold;
        varying vec2 v_tex_coord;
        
        void main() {
            vec4 color = texture2D(u_texture, v_tex_coord);
            
            // Extract bright areas
            vec3 bright = max(color.rgb - u_threshold, vec3(0.0));
            
            // Blur (simple 3x3 box blur)
            vec2 offset = 1.0 / textureSize(u_texture, 0);
            vec3 sum = vec3(0.0);
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    sum += texture2D(u_texture, v_tex_coord + vec2(float(i), float(j)) * offset).rgb;
                }
            }
            vec3 blurred = sum / 9.0;
            
            // Apply bloom
            vec3 bloom = bright * u_intensity * blurred;
            
            gl_FragColor = vec4(color.rgb + bloom, color.a);
        }
    )";
    
    GLuint fs = compile_shader(GL_FRAGMENT_SHADER, fs_source);
    if (fs) {
        const char* vs_source = R"(
            attribute vec4 a_position;
            attribute vec2 a_tex_coord;
            varying vec2 v_tex_coord;
            void main() {
                gl_Position = a_position;
                v_tex_coord = a_tex_coord;
            }
        )";
        GLuint vs = compile_shader(GL_VERTEX_SHADER, vs_source);
        if (vs) {
            m_gl.bloom_shader = create_program(vs, fs);
            glDeleteShader(vs);
        }
        glDeleteShader(fs);
    }
}

void QualityEnhancer::create_final_composite_shader() {
    // Final composite shader (combines all effects)
    const char* fs_source = R"(
        precision highp float;
        uniform sampler2D u_texture;
        varying vec2 v_tex_coord;
        
        void main() {
            vec4 color = texture2D(u_texture, v_tex_coord);
            
            // Gamma correction for better color output
            color.rgb = pow(color.rgb, vec3(1.0 / 2.2));
            
            gl_FragColor = color;
        }
    )";
    
    GLuint fs = compile_shader(GL_FRAGMENT_SHADER, fs_source);
    if (fs) {
        const char* vs_source = R"(
            attribute vec4 a_position;
            attribute vec2 a_tex_coord;
            varying vec2 v_tex_coord;
            void main() {
                gl_Position = a_position;
                v_tex_coord = a_tex_coord;
            }
        )";
        GLuint vs = compile_shader(GL_VERTEX_SHADER, vs_source);
        if (vs) {
            m_gl.final_composite_shader = create_program(vs, fs);
            glDeleteShader(vs);
        }
        glDeleteShader(fs);
    }
}

void QualityEnhancer::apply_color_boost(GLuint texture_id) {
    if (m_gl.color_boost_shader == 0) return;
    
    glUseProgram(m_gl.color_boost_shader);
    
    // Set uniforms
    GLint sat_loc = glGetUniformLocation(m_gl.color_boost_shader, "u_saturation");
    GLint con_loc = glGetUniformLocation(m_gl.color_boost_shader, "u_contrast");
    GLint bri_loc = glGetUniformLocation(m_gl.color_boost_shader, "u_brightness");
    GLint vib_loc = glGetUniformLocation(m_gl.color_boost_shader, "u_vibrance");
    GLint war_loc = glGetUniformLocation(m_gl.color_boost_shader, "u_warmth");
    
    if (sat_loc != -1) glUniform1f(sat_loc, m_settings.saturation);
    if (con_loc != -1) glUniform1f(con_loc, m_settings.contrast);
    if (bri_loc != -1) glUniform1f(bri_loc, m_settings.brightness);
    if (vib_loc != -1) glUniform1f(vib_loc, m_settings.vibrance);
    if (war_loc != -1) glUniform1f(war_loc, m_settings.warmth);
    
    // Render to our framebuffer
    glBindFramebuffer(GL_FRAMEBUFFER, m_gl.framebuffer);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, texture_id);
    
    // Draw fullscreen quad
    GLfloat vertices[] = {
        -1.0f, -1.0f, 0.0f, 1.0f,
         1.0f, -1.0f, 1.0f, 1.0f,
        -1.0f,  1.0f, 0.0f, 0.0f,
         1.0f,  1.0f, 1.0f, 0.0f
    };
    
    GLuint vbo, vao;
    glGenBuffers(1, &vbo);
    glGenVertexArrays(1, &vao);
    
    glBindVertexArray(vao);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);
    
    GLint pos_attrib = glGetAttribLocation(m_gl.color_boost_shader, "a_position");
    GLint tex_attrib = glGetAttribLocation(m_gl.color_boost_shader, "a_tex_coord");
    
    if (pos_attrib != -1) {
        glEnableVertexAttribArray(pos_attrib);
        glVertexAttribPointer(pos_attrib, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), 0);
    }
    if (tex_attrib != -1) {
        glEnableVertexAttribArray(tex_attrib);
        glVertexAttribPointer(tex_attrib, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), 
                             (void*)(2 * sizeof(GLfloat)));
    }
    
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    
    glDisableVertexAttribArray(pos_attrib);
    glDisableVertexAttribArray(tex_attrib);
    glBindVertexArray(0);
    
    glDeleteBuffers(1, &vbo);
    glDeleteVertexArrays(1, &vao);
    
    glUseProgram(0);
}

void QualityEnhancer::apply_hdr() {
    // Similar implementation for HDR
    // (Would apply HDR effect to current framebuffer)
}

void QualityEnhancer::apply_sharpening() {
    // Similar implementation for sharpening
    // (Would apply sharpening effect to current framebuffer)
}

void QualityEnhancer::apply_bloom() {
    // Similar implementation for bloom
    // (Would apply bloom effect to current framebuffer)
}

void QualityEnhancer::apply_final_composite() {
    // Similar implementation for final composite
    // (Would apply final gamma correction and output to screen)
}

// ============================================================================
// C Interface for JNI
// ============================================================================

extern "C" {

void turbo_v1_quality_init() {
    get_quality_enhancer().initialize();
}

void turbo_v1_quality_set_enabled(bool enabled) {
    get_quality_enhancer().set_enabled(enabled);
}

void turbo_v1_quality_set_saturation(float value) {
    get_quality_enhancer().set_saturation(value);
}

void turbo_v1_quality_set_contrast(float value) {
    get_quality_enhancer().set_contrast(value);
}

void turbo_v1_quality_set_brightness(float value) {
    get_quality_enhancer().set_brightness(value);
}

void turbo_v1_quality_set_vibrance(float value) {
    get_quality_enhancer().set_vibrance(value);
}

void turbo_v1_quality_set_hdr_enabled(bool enabled) {
    get_quality_enhancer().set_hdr_enabled(enabled);
}

void turbo_v1_quality_set_hdr_intensity(float value) {
    get_quality_enhancer().set_hdr_intensity(value);
}

void turbo_v1_quality_set_sharpening_enabled(bool enabled) {
    get_quality_enhancer().set_sharpening_enabled(enabled);
}

void turbo_v1_quality_set_sharpening_strength(float value) {
    get_quality_enhancer().set_sharpening_strength(value);
}

void turbo_v1_quality_apply() {
    get_quality_enhancer().apply_to_screen();
}

void turbo_v1_quality_reset() {
    get_quality_enhancer().reset_to_defaults();
}

} // extern "C"

} // namespace turbo_v1
