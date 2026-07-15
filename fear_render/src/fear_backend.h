#ifndef FEAR_BACKEND_H
#define FEAR_BACKEND_H

#include <string>

enum class RenderBackendType {
    VULKAN_HARDWARE,
    GLES_32_EMULATION
};

struct GPUCapabilities {
    bool has_fill_mode_non_solid;
    bool has_shader_clip_distance;
    bool has_geometry_shader;
    bool has_imageless_framebuffer;
};

class FearBackendManager {
public:
    static FearBackendManager& getInstance() {
        static FearBackendManager instance;
        return instance;
    }

    void detectAndInitializeBackend();
    RenderBackendType getActiveBackend() const { return m_active_backend; }
    GPUCapabilities getCapabilities() const { return m_capabilities; }

private:
    FearBackendManager() : m_active_backend(RenderBackendType::GLES_32_EMULATION), m_capabilities{false, false, false, false} {}

    RenderBackendType m_active_backend;
    GPUCapabilities m_capabilities;
};

#endif // FEAR_BACKEND_H
