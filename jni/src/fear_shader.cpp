#include "fear_render.h"

static std::string fear_transpile_glsl(const std::string &source) {
    std::string result = source;

    // Replace noperspective with smooth
    size_t pos = 0;
    while ((pos = result.find("noperspective", pos)) != std::string::npos) {
        result.replace(pos, 13, "smooth");
        pos += 6;
    }

    // Replace texture2D with texture
    pos = 0;
    while ((pos = result.find("texture2D", pos)) != std::string::npos) {
        result.replace(pos, 9, "texture");
        pos += 7;
    }

    return result;
}

extern "C" {

void fear_compile_shader_safe(GLuint shader, const char* source_code, const char* name) {
    if (!source_code) return;

    std::string transpiled = fear_transpile_glsl(source_code);
    const char* src_ptr = transpiled.c_str();

    typedef void (*pfn_glShaderSource)(GLuint, GLsizei, const char* const*, const GLint*);
    typedef void (*pfn_glCompileShader)(GLuint);
    typedef void (*pfn_glGetShaderiv)(GLuint, GLenum, GLint*);

    static pfn_glShaderSource real_glShaderSource = (pfn_glShaderSource) dlsym(RTLD_DEFAULT, "glShaderSource");
    static pfn_glCompileShader real_glCompileShader = (pfn_glCompileShader) dlsym(RTLD_DEFAULT, "glCompileShader");
    static pfn_glGetShaderiv real_glGetShaderiv = (pfn_glGetShaderiv) dlsym(RTLD_DEFAULT, "glGetShaderiv");

    if (real_glShaderSource && real_glCompileShader && real_glGetShaderiv) {
        real_glShaderSource(shader, 1, &src_ptr, NULL);
        real_glCompileShader(shader);

        GLint compiled = GL_FALSE;
        real_glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);

        if (!compiled) {
            LOGW("[FearRender] shader fallback %s - retrying with minimal safe shader", name ? name : "unnamed");
            const char* minimal_safe = "#version 300 es\nprecision highp float;\nvoid main() {}\n";
            real_glShaderSource(shader, 1, &minimal_safe, NULL);
            real_glCompileShader(shader);
        }
    }
}

} // extern "C"
