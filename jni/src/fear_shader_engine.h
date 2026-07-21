#ifndef FEAR_SHADER_ENGINE_H
#define FEAR_SHADER_ENGINE_H
#include "fear_shader_vulkan.h"
#include "fear_shader_pipeline.h"
#include "fear_shader_parser.h"
#include "fear_shader_transpiler.h"
#include "fear_shader_optimizer.h"
#include "fear_shader_cache.h"
#include "fear_shader_debug.h"
#include "fear_shader_fallback.h"
#include "fear_shader_gbuffer.h"
#include "fear_shader_shadow.h"
#include "fear_shader_composite.h"
#include "fear_shader_final.h"
#include "fear_shader_lighting.h"
#include "fear_shader_water.h"
#include "fear_shader_sky.h"
#include "fear_shader_terrain.h"
#include "fear_shader_entity.h"
#include "fear_shader_particle.h"
#include "fear_shader_postprocess.h"
#include "fear_shader_bloom.h"
#include "fear_shader_ssao.h"
#include "fear_shader_motionblur.h"
#include "fear_shader_anti_aliasing.h"
#include "fear_shader_compatibility.h"
#include "fear_shader_math.h"

// FEAR ULTRA-CORE Shader Transpiler Engine root manager
struct ShaderEngine {
    VulkanBridgeState vkState;
    ShaderPipeline pipeline;
};
#endif
