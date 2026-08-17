#pragma once

#include "wrappers/base.hpp"
#include <memory>

namespace GLES30 {
    class GLES30Wrapper : BaseWrapper {
        public:
            void init();
    };

    const inline std::shared_ptr<GLES30Wrapper> wrapper = std::make_shared<GLES30Wrapper>();

    void registerTranslatedFunctions();
    void registerTextureOverrides();
    void registerMultiDrawEmulation();
    void registerVertexAttribFunctions();
}