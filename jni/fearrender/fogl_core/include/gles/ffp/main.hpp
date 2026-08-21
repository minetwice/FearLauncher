#pragma once

#include "wrappers/base.hpp"

#include <memory>

namespace FFP {
    class FFPWrapper : BaseWrapper {
        public:
            void init();
    };

    const inline std::shared_ptr<FFPWrapper> wrapper = std::make_shared<FFPWrapper>();

    void registerStubFunctions();
    void registerImmediateFunctions();
    void registerColorFunctions();
    void registerTexCoordFunctions();
    void registerVertexFunctions();
    void registerMatrixFunctions();
    void registerListsFunctions();
    void registerAlphaTestFunctions();
    void registerTextureFunctions();
    void registerArrayFunctions();
    void registerShadingFunction();
    void registerFogFunctions();
    void registerNormalFunctions();
    void registerLightFunctions();
}