#ifndef FEAR_SHADER_PIPELINE_H
#define FEAR_SHADER_PIPELINE_H
// G-Buffer and modern pipeline layout translation states
struct ShaderPipeline {
    bool enableMRT = true;
    int activeDrawBuffers = 8;
};
#endif
