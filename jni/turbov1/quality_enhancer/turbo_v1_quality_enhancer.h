#ifndef TURBO_V1_QUALITY_ENHANCER_H
#define TURBO_V1_QUALITY_ENHANCER_H

#include <GLES3/gl32.h>
#include <cstdint>
#include <mutex>
#include <vector>
#include <string>

namespace turbo_v1 {

// ============================================================================
// TurboV1 Quality Enhancer - Beautiful Graphics System
// Enhances colors, saturation, sharpness, and HDR for stunning visuals
// ============================================================================

class QualityEnhancer {
public:
    static QualityEnhancer& get_instance();
    
    // Initialize the quality enhancer
    void initialize();
    
    // Enable/disable the enhancer
    void set_enabled(bool enabled);
    bool is_enabled() const;
    
    // Color enhancement settings
    void set_saturation(float value);      // 0.0 - 2.0 (default: 1.3)
    void set_contrast(float value);        // 0.5 - 2.0 (default: 1.2)
    void set_brightness(float value);      // 0.0 - 2.0 (default: 1.0)
    void set_vibrance(float value);        // 0.0 - 2.0 (default: 1.5)
    void set_warmth(float value);          // 0.0 - 1.0 (default: 0.05)
    
    // HDR settings
    void set_hdr_enabled(bool enabled);
    void set_hdr_intensity(float value);   // 0.0 - 3.0 (default: 1.5)
    void set_hdr_brightness(float value);  // 0.0 - 2.0 (default: 1.2)
    
    // Sharpening settings
    void set_sharpening_enabled(bool enabled);
    void set_sharpening_strength(float value); // 0.0 - 3.0 (default: 0.8)
    void set_sharpening_radius(int value);    // 1 - 5 (default: 2)
    
    // Bloom effect
    void set_bloom_enabled(bool enabled);
    void set_bloom_intensity(float value);   // 0.0 - 2.0 (default: 0.3)
    void set_bloom_threshold(float value);   // 0.0 - 1.0 (default: 0.8)
    
    // Film grain
    void set_film_grain_enabled(bool enabled);
    void set_film_grain_strength(float value); // 0.0 - 0.1 (default: 0.02)
    
    // Anti-aliasing
    void set_antialiasing_enabled(bool enabled);
    void set_antialiasing_samples(int samples); // 2, 4, 8, 16
    
    // Ambient occlusion
    void set_ambient_occlusion_enabled(bool enabled);
    void set_ambient_occlusion_strength(float value); // 0.0 - 2.0
    
    // Apply enhancement to current frame
    void apply_enhancement(GLuint texture_id, int width, int height);
    
    // Apply enhancement to screen
    void apply_to_screen();
    
    // Reset to defaults
    void reset_to_defaults();
    
    // Get current settings as string
    std::string get_settings_summary() const;
    
private:
    QualityEnhancer();
    ~QualityEnhancer();
    
    // Prevent copying
    QualityEnhancer(const QualityEnhancer&) = delete;
    QualityEnhancer& operator=(const QualityEnhancer&) = delete;
    
    // Settings structure
    struct Settings {
        bool enabled;
        
        // Color
        float saturation;
        float contrast;
        float brightness;
        float vibrance;
        float warmth;
        
        // HDR
        bool hdr_enabled;
        float hdr_intensity;
        float hdr_brightness;
        
        // Sharpening
        bool sharpening_enabled;
        float sharpening_strength;
        int sharpening_radius;
        
        // Bloom
        bool bloom_enabled;
        float bloom_intensity;
        float bloom_threshold;
        
        // Film grain
        bool film_grain_enabled;
        float film_grain_strength;
        
        // Anti-aliasing
        bool antialiasing_enabled;
        int antialiasing_samples;
        
        // Ambient occlusion
        bool ambient_occlusion_enabled;
        float ambient_occlusion_strength;
    } m_settings;
    
    // OpenGL resources
    struct GLResources {
        GLuint framebuffer;
        GLuint render_texture;
        GLuint depth_buffer;
        GLuint color_boost_shader;
        GLuint hdr_shader;
        GLuint sharpen_shader;
        GLuint bloom_shader;
        GLuint final_composite_shader;
    } m_gl;
    
    // Screen dimensions
    int m_screen_width;
    int m_screen_height;
    
    // Shader sources
    std::string load_shader(const std::string& name);
    GLuint compile_shader(GLenum type, const std::string& source);
    GLuint create_program(GLuint vs, GLuint fs);
    
    // Initialize OpenGL resources
    void init_gl_resources();
    void cleanup_gl_resources();
    
    // Create shaders
    void create_color_boost_shader();
    void create_hdr_shader();
    void create_sharpen_shader();
    void create_bloom_shader();
    void create_final_composite_shader();
    
    std::mutex m_mutex;
};

// Global quality enhancer instance
QualityEnhancer& get_quality_enhancer();

// C interface for JNI
extern "C" {
    void turbo_v1_quality_init();
    void turbo_v1_quality_set_enabled(bool enabled);
    void turbo_v1_quality_set_saturation(float value);
    void turbo_v1_quality_set_contrast(float value);
    void turbo_v1_quality_set_brightness(float value);
    void turbo_v1_quality_set_vibrance(float value);
    void turbo_v1_quality_set_hdr_enabled(bool enabled);
    void turbo_v1_quality_set_hdr_intensity(float value);
    void turbo_v1_quality_set_sharpening_enabled(bool enabled);
    void turbo_v1_quality_set_sharpening_strength(float value);
    void turbo_v1_quality_apply();
    void turbo_v1_quality_reset();
}

} // namespace turbo_v1

#endif // TURBO_V1_QUALITY_ENHANCER_H
