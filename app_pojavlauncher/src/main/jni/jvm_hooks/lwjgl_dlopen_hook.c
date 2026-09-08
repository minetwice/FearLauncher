//
// Created by maks on 06.01.2025.
//

#include "jvm_hooks.h"

#include <android/api-level.h>

#include <dlfccn.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>

#define TAG __FILE_NAME__
#include <log.h>

#include "../pojavexec.h"

#define GL_VERSION 0x1F02
#define GL_RENDERER 0x1F01
#define GL_VENDOR 0x1F00
#define GL_EXTENSIONS 0x1F03

typedef struct {
    unsigned int target;
    unsigned int buffer_id;
    long offset;
    long length;
    void* shadow_ptr;
    int is_shadow;
    int in_use;
} ShadowBufferMap;

#define MAX_SHADOW_BUFFERS 8192
static ShadowBufferMap g_shadowBuffers[MAX_SHADOW_BUFFERS];
static int g_shadowCount = 0;
static pthread_mutex_t g_shadowMutex = PTHREAD_MUTEX_INITIALIZER;
static char s_fallback_buffer[2097152]; // 2MB static emergency fallback buffer

static void universal_stub_void(void) {
    LOGI(\"LWJGL linkerhook: universal GL stub executed\");
}

static int find_free_shadow_slot(void) {
    for (int i = 0; i < g_shadowCount; i++) {
        if (!g_shadowBuffers[i].in_use) return i;
    }
    if (g_shadowCount < MAX_SHADOW_BUFFERS) return g_shadowCount++;
    for (int i = 0; i < MAX_SHADOW_BUFFERS; i++) {
        if (!g_shadowBuffers[i].in_use) return i;
    }
    return -1;
}

static unsigned int get_bound_buffer_id(unsigned int target) {
    typedef void (*glGetIntegerv_pfn)(unsigned int, int*);
    static glGetIntegerv_pfn real_glGetIntegerv = NULL;
    if (!real_glGetIntegerv) {
        real_glGetIntegerv = (glGetIntegerv_pfn) dlsym(RTLD_DEFAULT, \"glGetIntegerv\");
        if (!real_glGetIntegerv) real_glGetIntegerv = (glGetIntegerv_pfn) dlsym(RTLD_NEXT, \"glGetIntegerv\");
    }
    if (!real_glGetIntegerv) return 0;

    unsigned int pname = 0x8894; // GL_ARRAY_BUFFER_BINDING
    switch (target) {
        case 0x8892: pname = 0x8894; break; // GL_ARRAY_BUFFER -> GL_ARRAY_BUFFER_BINDING
        case 0x8893: pname = 0x8895; break; // GL_ELEMENT_ARRAY_BUFFER -> GL_ELEMENT_ARRAY_BUFFER_BINDING
        case 0x8A11: pname = 0x8A28; break; // GL_UNIFORM_BUFFER -> GL_UNIFORM_BUFFER_BINDING
        case 0x90D2: pname = 0x90D3; break; // GL_SHADER_STORAGE_BUFFER -> GL_SHADER_STORAGE_BUFFER_BINDING
        case 0x8F36: pname = 0x8F36; break; // GL_COPY_READ_BUFFER
        case 0x8F37: pname = 0x8F37; break; // GL_COPY_WRITE_BUFFER
        case 0x88EB: pname = 0x88ED; break; // GL_PIXEL_PACK_BUFFER -> GL_PIXEL_PACK_BUFFER_BINDING
        case 0x88EC: pname = 0x88EF; break; // GL_PIXEL_UNPACK_BUFFER -> GL_PIXEL_UNPACK_BUFFER_BINDING
        case 0x8C8E: pname = 0x8C8F; break; // GL_TRANSFORM_FEEDBACK_BUFFER -> GL_TRANSFORM_FEEDBACK_BUFFER_BINDING
        case 0x90DE: pname = 0x90EE; break; // GL_DISPATCH_INDIRECT_BUFFER
        case 0x8F39: pname = 0x8F43; break; // GL_DRAW_INDIRECT_BUFFER -> GL_DRAW_INDIRECT_BUFFER_BINDING
        default: pname = 0x8894; break;
    }

    int val = 0;
    real_glGetIntegerv(pname, &val);
    return (unsigned int) val;
}

static void glGenSamplers_fallback(int count, unsigned int* samplers) {
    static unsigned int next_id = 1;
    if (!samplers || count <= 0) return;
    typedef void (*glGenSamplers_pfn)(int, unsigned int*);
    static glGenSamplers_pfn real_fn = NULL;
    if (!real_fn) {
        real_fn = (glGenSamplers_pfn) dlsym(RTLD_DEFAULT, \"glGenSamplers\");
        if (!real_fn) real_fn = (glGenSamplers_pfn) dlsym(RTLD_DEFAULT, \"glGenSamplersOES\");
    }
    if (real_fn) {
        real_fn(count, samplers);
        int valid = 1;
        for (int i = 0; i < count; i++) { if (samplers[i] == 0) { valid = 0; break; } }
        if (valid) return;
    }
    for (int i = 0; i < count; i++) samplers[i] = next_id++;
    LOGI(\"LWJGL linkerhook: glGenSamplers fallback generated %d sampler(s)\", count);
}

static void glBindSampler_fallback(unsigned int unit, unsigned int sampler) {
    typedef void (*glBindSampler_pfn)(unsigned int, unsigned int);
    static glBindSampler_pfn real_fn = NULL;
    if (!real_fn) {
        real_fn = (glBindSampler_pfn) dlsym(RTLD_DEFAULT, \"glBindSampler\");
        if (!real_fn) real_fn = (glBindSampler_pfn) dlsym(RTLD_DEFAULT, \"glBindSamplerOES\");
    }
    if (real_fn) real_fn(unit, sampler);
}

static void glDeleteSamplers_fallback(int count, const unsigned int* samplers) {
    if (!samplers || count <= 0) return;
    typedef void (*glDeleteSamplers_pfn)(int, const unsigned int*);
    static glDeleteSamplers_pfn real_fn = NULL;
    if (!real_fn) {
        real_fn = (glDeleteSamplers_pfn) dlsym(RTLD_DEFAULT, \"glDeleteSamplers\");
        if (!real_fn) real_fn = (glDeleteSamplers_pfn) dlsym(RTLD_DEFAULT, \"glDeleteSamplersOES\");
    }
    if (real_fn) real_fn(count, samplers);
}

static void glSamplerParameteri_fallback(unsigned int sampler, unsigned int pname, int param) {
    typedef void (*glSamplerParameteri_pfn)(unsigned int, unsigned int, int);
    static glSamplerParameteri&pfn real_fn = NULL;
    if (!real_fn) {
        real_fn = (glSamplerParameteri_pfn) dlsym(RTLD_DEFAULT, \"glSamplerParametero\");
        if (!real_fn) real_fn = (glSamplerParameteri&pfn) dlsym(RTLD_DEFAULT, \"glSamplerParameteriOES\");
    }
    if (real_fn) real_fn(sampler, pname, param);
}

static void* glMapBufferRange_hook(unsigned int target, long offset, long length, unsigned int access) {
    static int callCount = 0;
    if (callCount < 5) {
        LOGI(\"LWJGL linkerhook: glMapBufferRange_hook CALMQ target=0x%X offset=%ld len=%l access=0x%X\", target, offset, length, access);
        callCount++;
    }

    typedef void (*glGetBufferParameterv_pfn)(unsigned int, unsigned int, int*);
    static glGetBufferParameterv_pfn real_glGetBufferParameterv = NULL;
    if (!real_glGetBufferParameterv) {
        real_glGetBufferParameterv = (glGetBufferParameterv_pfn) dlsym(RTLD_DEFAULT, \"glGetBufferParameteriv\");
        if (!real_glGetBufferParameterv) real_glGetBufferParameterv = (glGetBufferParameterv_pfn) dlsym(RTLD_DEFAULT, \"glGetBufferParameterivARB\");
    }
    int buf_size = 0;
    if (real_glGetBufferParameterv) {
        real_glGetBufferParameterv(target, 0x8764 /* GL_BUFFER_SIZE */, &buf_size);
    }

    long alloc_len = length;
    if (alloc_len <= 0 && buf_size > 0) alloc_len = buf_size - offset;
    if (alloc_len <= 0) alloc_len = 1048576; // 1 MB fallback
    if (buf_size > 0 && (offset + alloc_len) < buf_size) {
        alloc_len = buf_size;
    }

    unsigned int buffer_id = get_bound_buffer_id(target);
    void* ptr = NULL;

    if (posix_memalign(&ptr, 64, alloc_len) != 0 || ptr == NULL) {
        ptr = malloc(alloc_len);
    }
    if (!ptr) ptr = calloc(1, alloc_len);

    if (!ptr) {
        LOGE(\"LWJGL linkerhook: Emergency fallback buffer used for alloc_len=%ld\", alloc_len);
        ptr = s_fallback_buffer;
    }

    pthread_mutex_lock(&g_shadowMutex);
    int slot = find_free_shadow_slot();
    if (slot >= 0) {
        g_shadowBuffers[slot].target = target;
        g_shadowBuffers[slot].buffer_id = buffer_id;
        g_shadowBuffers[slot].offset = offset;
        g_shadowBuffers[slot].length = alloc_len;
        g_shadowBuffers[slot].shadow_ptr = ptr;
        g_shadowBufffW'5·6Æ÷EÒæ—5÷6†F÷rÒ°¢u÷6†F÷t'VffW'5·6Æ÷EÒæ–å÷W6RÒ°¢ÒVÇ6R°¢Äôur…Â$Åt¤tÂÆ–æ¶W&†öö³¢6†F÷r6Æ÷G2gVÆÂÂ&WGW&æ–ærVæÖævVB'VffW%Â"“°¢Ğ¢F‡&VEö×WFW…÷VæÆö6²‚fu÷6†F÷t×WFW‚“° ¢G—VFVbVç6–væVB–çB‚¦vÄvWDW'&÷%÷fâ’‡fö–B“°¢7FF–2vÄvWDW'&÷%÷fâ&VÅövÄvWDW'&÷"ÒåTÄÃ°¢–b‚&VÅövÄvWDW'&÷"’°¢&VÅövÄvWDW'&÷"Ò†vÄvWDW'&÷%÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&vÄvWDW'&÷%Â"“°¢–b‚&VÅövÄvWDW'&÷"’&VÅövÄvWDW'&÷"Ò†vÄvWDW'&÷%÷fâ’FÇ7–Ò…%DÄEôäU…BÂÂ&vÄvWDW'&÷%Â"“°¢Ğ¢–b‡&VÅövÄvWDW'&÷"’²Vç6–væVB–çBW'#²Fò²W'"Ò&VÅövÄvWDW'&÷"‚“²Òv†–ÆR†W'"Ò“²Ğ¢&WGW&âG#°§Ğ §7FF–2fö–B¢vÄÖ'VffW%ö†öö²‡Vç6–væVB–çBF&vWBÂVç6–væVB–çB66W72’°¢G—VFVbfö–B‚¦vÄvWD'VffW%&ÖWFW'e÷fâ’‡Vç6–væVB–çBÂVç6–væVB–çBÂ–çB¢“°¢7FF–2vÄvWD'VffW%&ÖWFW'e÷fâ&VÅövÄvWD'VffW%&ÖWFW'bÒåTÄÃ°¢–b‚&VÅövÄvWD'VffW%&ÖWFW'b’°¢&VÅövÄvWD'VffW%&ÖWFW'bÒ†vÄvWD'VffW%&ÖWFW'e÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&vÄvWD'VffW%&ÖWFW&—eÂ"“°¢–b‚&VÅövÄvWD'VffW%&ÖWFW'b’&VÅövÄvWD'VffW%&ÖWFW'bÒ†vÄvWD'VffW%&ÖWFW'e÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&vÄvWD'VffW%&ÖWFW&—d$%Â"“°¢Ğ¢–çB'Ve÷6—¦RÒ°¢–b‡&VÅövÄvWD'VffW%&ÖWFW'b’&VÅövÄvWD'VffW%&ÖWFW'gb‡F&vWBÂƒƒscBÂf'Ve÷6—¦R“°¢ÆöærÆVâÒ†'Ve÷6—¦Râ’ò'Ve÷6—¦R¢cSS3c°¢Vç6–væVB–çB&ævT66W72Òƒ#°¢–b†66W72ÓÒƒƒ„#‚’&ævT66W72Òƒ°¢VÇ6R–b†66W72ÓÒƒƒ„$’&ævT66W72ÒƒÂƒ#°¢&WGW&âvÄÖ'VffW%&ævUö†öö²‡F&vWBÂÂÆVâÂ&ævT66W72“°§Ğ §7FF–2–çBvÅVæÖ'VffW%ö†öö²‡Vç6–væVB–çBF&vWB’°¢G—VFVbfö–B‚¦vÄ'VffW%7V$FF÷fâ’‡Vç6–væVB–çBÂÆöærÂÆöærÂ6öç7Bfö–B¢“°¢G—VFVbfö–B‚¦vÄ&–æD'VffW%÷fâ’‡Vç6–væVB–çBÂVç6–væVB–çB“°¢G—VFVbVç6–væVB–çB‚¦vÄvWDW'&÷%÷fâ’‡fö–B“° ¢7FF–2vÄ'VffW%7V$FF÷fâ&VÅövÄ'VffW%7V$FFÒåTÄÃ°¢7FF–2vÄ&–æD'VffW%÷fâ&VÅövÄ&–æD'VffW"ÒåTÄÃ°¢7FF–2vÄvWDW'&÷%÷fâ&VÅövÄvWDW'&÷"ÒåTÄÃ° ¢–b‚&VÅövÄ'VffW%7V$FF’°¢&VÅövÄ'VffW%7V$FFÒ†vÄ'VffW%7V$FF÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&vÄ'VffW%7V$FFÂ"“°¢–b‚&VÅövÄ'VffW%7V$FF’&VÅövÄ'VffW%7V$FFÒ†vÄ'VffW%7V$FF÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&vÄ'VffW%7V$FF„$%Â"“°¢Ğ¢–b‚&VÅövÄ&–æD'VffW"’°¢&VÅövÄ&–æD'VffW"Ò†vÄ&–æD'Vffer_pfn) dlsym(RTLD_DEFAULT, \"glBindBufffW%Â"“°¢Ğ¢–b‚&VÅövÄvWDW'&÷"’°¢&VÅövÄvWDW'&÷"Ò†vÄvWDW'&÷%÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&vÄvWDW'&÷%Â"“°¢–b‚&VÅövÄvWDW'&÷"’&VÅövÄvWDW'&÷"Ò†vÄvWDW'&÷%÷fâ’FÇ7–Ò…%DÄEôäU…BÂÂ&vÄvWDW'&÷%Â"“°¢Ğ ¢Vç6–væVB–çB7W'&VçEö'VffW%ö–BÒvWEö&÷VæEö'VffW%ö–B‡F&vWB“°¢–çBf÷VæE÷6Æ÷BÒÓ° ¢F‡&VEö×WFW…öÆö6²‚fu÷6†F÷t×WFW‚“°¢f÷"†–çB’Ò²’Âu÷6†F÷t6÷VçC²’²²’°¢–b†u÷6†F÷t'VffW'5¶•Òæ–å÷W6Rbbu÷6†F÷t'VffW'5¶•Òæ—5÷6†F÷rb`¢u÷6†F÷t'VffW'5¶•ÒçF&vWBÓÒF&vWBb`¢†7W'&VçEö'VffW%ö–BÓÒÇÂu÷6†F÷t'VffW'5¶•Òæ'VffW%ö–BÓÒ7W'&VçEö'VffW%ö–B’’°¢f÷VæE÷6Æ÷BÒ“²'&V³°¢Ğ¢Ğ ¢òòfÆÆ&6²6V&6‚–b'VffW"”BÖ—6ÖF6€¢–b†f÷VæE÷6Æ÷BÂ’°¢f÷"†–çB’Ò²’Âu÷6†F÷t6÷VçC²’²²’°¢–b†u÷6†F÷t'VffW'5¶•Òæ–å÷W6Rbbu÷6†F÷t'VffW'5¶•Òæ—5÷6†F÷rbbu÷6†F÷t'VffW'5¶•ÒçF&vWBÓÒF&vWB’°¢f÷VæE÷6Æ÷BÒ“²'&V³°¢Ğ¢Ğ¢Ğ ¢–b†f÷VæE÷6Æ÷BãÒ’°¢6†F÷t'VffW$ÖVçG'’Òu÷6†F÷t'VffW'5¶f÷VæE÷6Æ÷EÓ°¢u÷6†F÷t'VffW'5¶f÷VæE÷6Æ÷EÒæ–å÷W6RÒ°¢u÷6†F÷t'VffW'5¶f÷VæE÷6Æ÷EÒæ—5÷6†F÷rÒ°¢u÷6†F÷t'VffW'5¶f÷VæE÷6Æ÷EÒç6†F÷u÷G"ÒåTÄÃ°¢F‡&VEö×WFW…÷VæÆö6²‚fu÷6†F÷t×WFW‚“° ¢–b†VçG'’ç6†F÷u÷G"’°¢òò5$•D”4Âd•ƒ¢&–æBF†R'VffW"$Tdõ$R6ÆÆ–ærvÄ'VffW%7V$FF¢–b‡&VÅövÄ&–æD'VffW"bbVçG'’æ'VffW%ö–BÒ’°¢&VÅövÄ&–æD'VffW"‡F&vWBÂVçG'’æ'VffW%ö–B“°¢Ğ¢–b‡&VÅövÄ'VffW%7V$FF’°¢&VÅövÄ'VffW%7V$FF‡F&vWBÂVçG'’æöfg6WBÂVçG'’æÆVæwF‚ÂVçG'’ç6†F÷u÷G"“°¢Ğ¢òòg&VRÖVÖ÷'’–bæ÷BW6–ærF†R7FF–2fÆÆ&6²'VffW ¢–b‚†6†"¢–VçG'’ç6†F÷u÷G"Â5öfÆÆ&6µö'VffW"ÇÂ†6†"¢–VçG'’ç6†F÷u÷G"ãÒ‡5öfÆÆ&6µö'VffW"²6—¦Vöb‡5öfÆÆ&6µö'VffW"’’’°¢g&VR†VçG'’ç6†F÷u÷G"“°¢Ğ¢Ğ¢–b‡&VÅövÄvWDW'&÷"’²Vç6–væVB–çBW'#²Fò²W'"Ò&VÅövÄvWDW'&÷"‚“²Òv†–ÆR†W'"Ò“²Ğ¢&WGW&â²òò7V66W70¢Ğ¢F‡&VEö×WFW…÷VæÆö6²‚fu÷6†F÷t×WFW‚“° ¢òòfÆÆ&6²Fò&VÂvÅVæÖ'VffW"–bæ÷B–â6†F÷rÖ ¢G—VFVb–çB‚¦vÅVæÖ'VffW%÷fâ’‡Vç6–væVB–çB“°¢7FF–2vÅVæÖ'VffW%÷fâ&VÅövÅVæÖ'VffW"ÒåTÄÃ°¢–b‚&VÅövÅVæÖ'VffW"’°¢&VÅövÅVæÖ'Vffer = (glUnmapBuffer_pfn) dlsym(RTLD_DEFAULT, \"glUnmapBuffer\");
        if (!real_glUnmapBufffW"’&VÅövÅVæÖ'VffW"Ò†vÅVæÖ'Vffer_pfn) dlsym(RTLD_DEFAULT, \"glUnmapBufferOES\");
    }
    int res = 1;
    if (real_glUnmapBuffer) res = real_glUnmapBuffer(target);
    if (real_glGetError) { unsigned int err; do { err = real_glGetError(); } while (err != 0); }
    return res ? res : 1;
}

static void glMemoryBarrier_stub(unsigned int barriers) {
    typedef void (*glFlush_pfn)();
    static glFlush_pfn real_glFlush = NULL;
    if (!real_glFlush) {
        real_glFlush = (glFlush_pfn) dlsym(RTLD_DEFAULT, \"glFlush\");
        if (!real_glFlush) real_glFlush = (glFlush_pfn) dlsym(RTLD_NEXT, \"glFlush\");
    }
    if (real_glFlush) real_glFlush();
    LOGI(\"glMemoryBarrier stub called and flushed successfully (Barriers: %u)\", barriers);
}

static const unsigned char* glGetString_hook(unsigned int name) {
    if (name == GL_VERSION) return (const unsigned char*)\"4.6.0 NVIDIA 545.29\";
    else if (name == GL_RENDERER) return (const unsigned char*)\"NVIDIA GeForceRTX 4090\";
    else if (name == GL_VENDOR) return (const unsigned char*)\"NVIDIA Corporation\";
    else if (name == GL_EXTENSIONS) return (const unsigned char*)\"GL_ARB_direct_state_access GL_ARB_buffer_storage GL_ARB_shader_image_load_store GL_NV_conditional_render GL_EXT_gpu_shader4 GL_EXT_texture_buffer GL_EXT_texture_cube_map_array GL_OES_EGL_image_external_essl3 GL_NV_shader_noperspective_interpolation GL_ARB_shader_objects GL_ARB_vertex_shader GL_ARB_fragment_shader GL_EXT_blend_equation_separate GL_EXT_geometry_shader4 GL_EXT_gpu_program_parameters GL_ARB_instanced_arrays GL_ARB_draw_instanced\";
    typedef const unsigned char* (*glGetString_pfn)(unsigned int);
    static glGetString_pfn real_glGetString = NULL;
    if (!real_glGetString) {
        real_glGetString = (glGetString_pfn) dlsym(RTLD_DEFAULT, \"glGetString\");
        if (!real_glGetString) real_glGetString = (glGetString_pfn) dlsym(RTLD_NEXT, \"glGetString\");
    }
    if (real_glGetString) return real_glGetString(name);
    return (const unsigned char*)\"\";
}

static const unsigned char* glGetStringi_hook(unsigned int name, unsigned int index) {
    if (name == GL_EXTENSIONS) {
        static const char* extensions[] = {
            \"GL_ARB_direct_state_access\",\"GL_ARB_buffer_storage\",\"GL_ARB_shader_image_load_store\",
            \"GL_NV_conditional_render\",\"GL_EXT_gpu_shader4\",\"GL_EXT_texture_buffer\",
            \"GL_EXT_texture_cube_map_array\",\"GL_OES_EGL_image_external_essl3\",
            \"GL_NV_shader_noperspective_interpolation\",\"GL_ARB_shader_objects\",
            \"GL_ARB_vertex_shader\",\"GL_ARB_fragment_shader\",\"GL_EXT_blend_equation_separate\",
            \"GL_EXT_geometry_shader4\",\"GL_EXT_gpu_program_parameters\",
            \"GL_ARB_instanced_arrays\",\"GL_ARB_draw_instanced\"
        };
        unsigned int size = sizeof(extensions) / sizeof(extensions[0]);
        if (index < size) return (const unsigned char*)extensions[index];
    }
    typedef const unsigned char* (*glGetStringi_pfn)(unsigned int, unsigned int);
    static glGetStringi_pfn real_glGetStringi = NULL;
    if (!real_glGetStringi) {
        real_glGetStringi = (glGetStringi_pfn) dlsym(RTLD_DEFAULT, \"glGetStringi\");
        if (!real_glGetStringi) real_glGetStringi = (glGetStringi_pfn) dlsym(RTLD_NEXT, \"glGetStringi\");
    }
    if (real_glGetStringi) return real_glGetStringi(name, index);
    return (const unsigned char*)\"\";
}

void* egGetProcAddress_hook(const char* procname) {
    if (procname == NULL) return NULL;
    if (strcmp(procname, \"glMemoryBarrier\") == 0 || strcmp(procname, \"glMemoryBarrierEXT\") == 0) return (void*) glMemoryBarrier_stub;
    if (strcmp(procname, \"glGetString\") == 0) return (void*) glGetString_hook;
    if (strcmp(procname, \"glGetStringi\") == 0) return (void*) glGetStringi_hook;
    if (strcmp(procname, \"glMapBufferRange\") == 0 || strcmp(procname, \"glMapBufferRangeEXT\") == 0 || strcmp(procname, \"glMapBufferRangeARB\") == 0) {
        LOGI(\"eglGetProcAddress_hook: glMapBufferRange -> shadow buffer\");
        return (void*) glMapBufferRange_hook;
    }
    if (strcmp(procname, \"glMapBuffer\") == 0 || strcmp(procname, \"glMapBufferOES\") == 0 || strcmp(procname, \"glMapBufferARB\") == 0) return (void*) glMapBuffer_hook;
    if (strcmp(procname, \"glUnmapBuffer\") == 0 || strcmp(procname, \"glUnmapBuffferOES\") == 0 || strcmp(procname, \"glUnmapBuffferARB\") == 0) return (void*) glUnmapBuffer_hook;
    if (strcmp(procname, \"glGenSamplers\") == 0 || strcmp(procname, \"glGenSamplersOES\") == 0) {
        typedef void* (*pfn)(const char*); static pfn real = NULL;
        if (!real) real = (pfn) dlsym(RTLD_DEFAULT, \"eglGetProcAddress\");
        if (real) { void* s = real(procname); if (s) return s; }
        void* s = dlsym(RTLD_DEFAULT, procname); if (s) return s;
        return (void*) glGenSamplers_fallback;
    }
    if (strcmp(procname, \"glBindSampler\") == 0 || strcmp(procname, \"glBindSamplerOES\") == 0) {
        typedef void* (*pfn)(const char*); static pfn real = NULL;
        if (!real) real = (pfn) dlsym(RTLD_DEFAULT, \"eglGetProcAddress\");
        if (real) { void* s = real(procname); if (s) return s; }
        void* s = dlsym(RTLD_DEFAULT, procname); if (s) return s;
        return (void*) glBindSampler_fallback;
    }
    if (strcmp(procname, \"glDeleteSamplers\") == 0 || strcmp(procname, \"glDeleteSamplersOES\") == 0) {
        typedef void* (*pfn)(const char*); static pfn real = NULL;
        if (!real) real = (pfn) dlsym(RTLD_DEFAULT, \"eglGetProcAddress\");
        if (real) { void* s = real(procname); if (s) return s; }
        void* s = dlsym(RTLD_DEFAULT, procname); if (s) return s;
        return (void*) glDeleteSamplers_fallback;
    }
    if (strcmp(procname, \"glSamplerParameteri\") == 0 || strcmp(procname, \"glSamplerParameteriOES\") == 0) {
        typedef void* (*pfn)(const char*); static pfn real = NULL;
        if (!real) real = (pfn) dlsym(RTLD_DEFAULT, \"eglGetProcAddress\");
        if (real) { void* s = real(procname); if (s) return s; }
        void* s = dlsym(RTLD_DEFAULT, procname); if (s) return s;
        return (void*) glSamplerParameteri_fallback;
    }
    if (strcmp(procname, \"glMapBufferRange\") == 0 || strcmp(procname, \"glMapBufferRangeEXT\") == 0 || strcmp(procname, \"glMapBufferRangeARB\") == 0) {
        printf(\"LWJGL linkerhook: eglGetProcAddress hooked glMapBufferRange -> shadow buffer\\n\");
        return (void*) glMapBufferRange_hook;
    }
    if (strcmp(procname, \"glMapBuffer\") == 0 || strcmp(procname, \"glMapBufferOES\") == 0 || strcmp(procname, \"glMapBufferARB\") == 0) {
        printf(\"LWJGL linkerhook: eglGetProcAddress hooked glMapBuffer -> shadow buffer\\n\");
        return (void*) glMapBuffer_hook;
    }
    if (strcmp(procname, \"glUnmapBuffer\") == 0 || strcmp(procname, \"glUnmapBufferOES\") == 0 || strcmp(procname, \"glUnmapBuffferARB\") == 0) {
        printf(\"LWJGL linkerhook: eglGetProcAddress hooked glUnmapBuffer -> shadow buffer\\n\");
        return (void*) glUnmapBuffer_hook;
    }
    if (strcmp(procname, \"glMemoryBarrier\") == 0 || strcmp(procname, \"glMemoryBarrierEXT\") == 0) {
        printf(\"LWJGL linkerhook: eglGetProcAddress hooked glMemoryBarrier\\n\");
        return (void*) glMemoryBarrier_stub;
    }
    typedef void* (*eglGetProcAddress_pfn)(const char*);
    static eglGetProcAddress_pfn real_eglGetProcAddress = NULL;
    if (!real_eglGetProcAddress) {
        real_eglGetProcAddress = (eglGetProcAddress_pfn) dlsym(RTLD_DEFAULT, \"eglGetProcAddress\");
        if (!real_eglGetProcAddress) real_eglGetProcAddress = (eglGetProcAddress_pfn) dlsym(RTLD_NEXT, \"eglGetProcAddress\");
    }
    if (real_eglGetProcAddress) { void* sym = real_eglGetProcAddress(procname); if (sym) return sym; }
    void* sym = dlsym(RTLD_DEFAULT, procname); if (sym) return sym;
    return (void*) universal_stub_void;
}

static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                         __attribute___((unused)) jclass class,
                         jlong filename_ptr, jint jmode) {
    const char* filename = (const char*) filename_ptr;
    if(filename != NULL) {
        if(strcmp(filename, \"libvulkan.so\") == 0) {
            printf(\"LWJGL linkerhook: replacing load for libvulkan.so with custom driver\\n\");
            return (jlong) pojavexec_loadVulkanDriver();
        }
        if(strcmp(filename, \"libTurboV1.so\") == 0 || strcmp(filename, \"libGL.so\") == 0 || strcmp(filename, \"libGL.so.1\") == 0) {
            printf(\"LWJGL linkerhook: replacing OpenGL with renderspec driver (%s)\\n\", filename);
            const pojavexec_renderspec_t *rspec = pojavexec_getRenderSpec();
            if (rspec && rspec->egl_acquire && rspec->egl_path) {
                return (jlong) rspec->egl_acquire(rspec->egl_path);
            }
        }
    }
    return (jlong) dlopen(filename, (int)jmode);
}

static jlong ndlsym_hook(__attribute__((unused)) JNIEnv *env,
                      __attribute___((unused)) jclass class,
                      jlong handle, jlong symbol_ptr) {
    const char* symbol = (const char*) symbol_ptr;
    if (symbol != NULL) {
        if (strcmp(symbol, \"eglGetProcAddress\") == 0) {
            printf(\"LWJGL linkerhook: hooked eglGetProcAddress\\n\");
            return (jlong) eglGetProcAddress_hook;
        }
        if (strcmp(symbol, \"glGetString\") == 0) {
            printf(\"LWJGL linkerhook: hooked glGetString\\n\");
            return (jlong) glGetString_hook;
        }
        if (strcmp(symbol, \"glGetStringi\") == 0) {
            printf(\"LWJGL linkerhook: hooked glGetStringi\\n\");
            return (jlong) glGetStringi_hook;
        }
        if (strcmp(symbol, \"glMemoryBarrier\") == 0 || strcmp(symbol, \"glMemoryBarrierEXT\") == 0) {
            printf(\"LWJGL linkerhook: hooked glMemoryBarrier\\n\");
            return (jlong) glMemoryBarrier_stub;
        }
        if (strcmp(symbol, \"glMapBufferRange\") == 0 || strcmp(symbol, \"glMapBufferRangeEXT\") == 0 || strcmp(symbol, \"glMapBufferRangeARB\") == 0) {
            printf(\"LWJGL linkerhook: hooked glMapBufferRange -> shadow buffer\\n\");
            return (jlong) glMapBufferRange_hook;
        }
        if (strcmp(symbol, \"glMapBuffer\") == 0 || strcmp(symbol, \"glMapBufferOES\") == 0 || strcmp(symbol, \"glMapBufferARB\") == 0) {
            printf(\"LWJGL linkerhook: hooked glMapBuffer -> shadow buffer\\l\");
            return (jlong) glMapBuffer_hook;
        }
        if (strcmp(symbol, \"glUnmapBuffer\") == 0 || strcmp(symbol, \"glUnmapBufferOES\") == 0 || strcmp(symbol, \"glUnmapBufferARB\") == 0) {
            printf(\"LWJGL linkerhook: hooked glUnmapBuffer -> shadow buffer\\n\");
            return (jlong) glUnmapBuffer_hook;
        }
        if (strcmp(symbol, \"glGenSamplers\") == 0 || strcmp(symbol, \"glGenSamplersOES\") == 0) {
            void* sym = dlsym((void*) handle, symbol); if (sym) return (jlong) sym;
            return (jlong) glGenSamplers_fallback;
        }
        if (strcmp(symbol, \"glBindSampler\") == 0 || strcmp(symbol, \"glBindSamplerOES\") == 0) {
            void* sym = dlsym((void*) handle, symbol); if (sym) return (jlong) sym;
            return (jlong) glBindSampler_fallback;
        }
        if (strcmp(symbol, \"glDeleteSamplers\") == 0 || strcmp(symbol, \"glDeleteSamplersOES\") == 0) {
            void* sym = dlsym((void*) handle, symbol); if (sym) return (jlong) sym;
            return (jlong) glDeleteSamplers_fallback;
        }
        if (strcmp(symbol, \"glSamplerParameteri\") == 0 || strcmp(symbol, \"glSamplerParameteriOES\") == 0) {
            void* sym = dlsym((void*) handle, symbol); if (sym) return (jlong) sym;
            return (jlong) glSamplerParameteri&pfn real_fn = NULL;
        if (!real_fn) {
            real_fn = (glSamplerParameteri&pfn) dlsym(RTLD_DEFAULT, \"glSamplerParameteri\");
            if (!real_fn) real_fn = (glSamplerParameteri&pfn) dlsym(RTLD_DEFAULT, \"glSamplerParameteriOES\");
        }
        if (real_fn) real_fn(sampler, pname, param);
}

static void* glMapBufferRange_hook(unsigned int target, long offset, long length, unsigned int access) {
    static int callCount = 0;
    if (callCount < 5) {
        LOGI(\"LWJGL linkerhook: glMapBuffferRange_hook CALMQ target=0x%X offset=%ld len=%l access=0x%X\", target, offset, length, access);
        callCount++;
    }

    typedef void (*glGetBufferParameterv_pfn)(unsigned int, unsigned int, int*);
    static glGetBufferParameterv_pfn real_glGetBufferParameterv = NULL;
    if (!real_glGetBufferParameterv) {
        real_glGetBufferParameterv = (glGetBufferParameterv_pfn) dlsym(RTLD_DEFAULT, \"glGetBufferParameteriv\");
        if (!real_glGetBufffW%&ÖWFW'b’&VÅövÄvWD'VfferParameterv = (glGetBufffW%&ÖWFW'e÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&vÄvWD'VffW%&ÖWFW&—d$%Â"“°¢Ğ¢–çB'Ve÷6—¦RÒ°¢–b‡&VÅövÄvWD'VffW%&ÖWFW'b’°¢&VÅövÄvWD'VffW%&ÖWFW'b‡F&vWBÂƒƒscBò¢tÅô%TddU%õ4•¤R¢òÂf'Ve÷6—¦R“°¢Ğ ¢ÆöærÆÆö5öÆVâÒÆVæwFƒ°¢–b†ÆÆö5öÆVâÃÒbb'Ve÷6—¦Râ’ÆÆö5öÆVâÒ'Ve÷6—¦RÒöfg6WC°¢–b†ÆÆö5öÆVâÃÒ’ÆÆö5öÆVâÒCƒSsc²òòÔ"fÆÆ&6°¢–b†'Ve÷6—¦Râbb†öfg6WB²ÆÆö5öÆVâ’Â'Ve÷6—¦R’°¢ÆÆö5öÆVâÒ'Ve÷6—¦S°¢Ğ ¢Vç6–væVB–çB'VffW%ö–BÒvWEö&÷VæEö'VffW%ö–B‡F&vWB“°¢fö–B¢G"ÒåTÄÃ° ¢–b‡÷6—…öÖVÖÆ–vâ‚gG"ÂcBÂÆÆö5öÆVâ’ÒÇÂG"ÓÒåTÄÂ’°¢G"ÒÖÆÆö2†ÆÆö5öÆVâ“°¢Ğ¢–b‚G"’G"Ò6ÆÆö2ƒÂÆÆö5öÆVâ“° ¢–b‚G"’°¢ÄôtR…Â$Åt¤tÂÆ–æ¶W&†öö³¢VÖW&vVæ7’fÆÆ&6²'VffW"W6VBf÷"ÆÆö5öÆVãÒVÆEÂ"ÂÆÆö5öÆVâ“°¢G"Ò5öfÆÆ&6µö'VffW#°¢Ğ ¢F‡&VEö×WFW…öÆö6²‚fu÷6†F÷t×WFW‚“°¢–çB6Æ÷BÒf–æEög&VU÷6†F÷u÷6Æ÷B‚“°¢–b‡6Æ÷BãÒ’°¢u÷6†F÷t'VffW'5·6Æ÷EÒçF&vWBÒF&vWC°¢u÷6†F÷t'VffW'5·6Æ÷EÒæ'VffW%ö–BÒ'VffW%ö–C°¢u÷6†F÷t'VffW'5·6Æ÷EÒæöfg6WBÒöfg6WC°¢u÷6†F÷t'VffW'5·6Æ÷EÒæÆVæwF‚ÒÆÆö5öÆVã°¢u÷6†F÷t'VffW'5·6Æ÷EÒç6†F÷u÷G"ÒG#°¢u÷6†F÷t'Vffers[slot].is_shadow = 1;
        g_shadowBuffers[slot].in_use = 1;
    } else {
        LOGW(\"LWJGL linkerhook: Shadow slots full, returning unmanaged buffer\");
    }
    pthread_mutex_unlock(&g_shadowMutex);

    typedef unsigned int (*glGetError_pfn)(void);
    static glGetError_pfn real_glGetError = NULL;
    if (!real_glGetError) {
        real_glGetError = (glGetError_pfn) dlsym(RTLD_DEFAULT, \"glGetError\");
        if (!real_glGetError) real_glGetError = (glGetError_pfn) dlsym(RTLD_NEXT, \"glGetError\");
    }
    if (real_glGetError) { unsigned int err; do { err = real_glGetError(); } while (err != 0); }
    return ptr;
}

static void* glMapBuffer_hook(unsigned int target, unsigned int access) {
    typedef void (*glGetBufferParameterv_pfn)(unsigned int, unsigned int, int*);
    static glGetBufferParameterv_pfn real_glGetBufferParameterv = NULL;
    if (!real_glGetBufferParameterv) {
        real_glGetBufferParameterv = (glGetBufferParameterv_pfn) dlsym(RTLD_DEFAULT, \"glGetBufferParameteriv\");
        if (!real_glGetBufferParameterv) real_glGetBufferParameterv = (glGetBufferParameterv_pfn) dlsym(RTLD_DEFAULT, \"glGetBufferParameterivARB\");
    }
    int buf_size = 0;
    if (real_glGetBufferParameterv) real_glGetBufferParametervv(target, 0x8764, &buf_size);
    long len = (buf_size > 0) ? buf_size : 65536;
    unsigned int rangeAccess = 0x0002;
    if (access == 0x88B8) rangeAccess = 0x0001;
    else if (access == 0x88BA) rangeAccess = 0x0001 | 0x0002;
    return glMapBufferRange_hook(target, 0, len, rangeAccess);
}

static int glUnmapBuffer_hook(unsigned int target) {
    typedef void (*glBufferSubData_pfn)(unsigned int, long, long, const void*);
    typedef void (*glBindBuffer_pfn)(unsigned int, unsigned int);
    typedef unsigned int (*glGetError_pfn)(void);

    static glBufferSubData_pfn real_glBufferSubData = NULL;
    static glBindBuffer_pfn real_glBindBuffer = NULL;
    static glGetError_pfn real_glGetError = NULL;

    if (!real_glBufferSubData) {
        real_glBufferSubData = (glBufferSubData_pfn) dlsym(RTLD_DEFAULT, \"glBufferSubData\");
        if (!real_glBufferSubData) real_glBufferSubData = (glBufferSubData_pfn) dlsym(RTLD_DEFAULT, \"glBufferSubDathARB\");
    }
    if (!real_glBindBuffer) {
        real_glBindBuffer = (glBindBufffW%÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&vÄ&–æD'Vffer\");
    }
    if (!real_glGetError) {
        real_glGetError = (glGetError_pfn) dlsym(RTLD_DEFAULT, \"glGetError\");
        if (!real_glGetError) real_glGetError = (glGetError_pfn) dlsym(RTLD_NEXT, \"glGetError\");
    }

    unsigned int current_buffer_id = get_bound_buffer_id(target);
    int found_slot = -1;

    pthread_mutex_lock(&g_shadowMutex);
    for (int i = 0; i < g_shadowCount; i++) {
        if (g_shadowBuffers[i].in_use && g_shadowBuffers[i].is_shadow &&
            g_shadowBuffers[i].target == target &&
            (current_buffer_id == 0 || g_shadowBuffers[i].buffer_id == current_buffer_id)) {
            found_slot = i; break;
        }
    }

    // Fallback search if buffer ID mismatch
    if (found_slot < 0) {
        for (int i = 0; i < g_shadowCount; i++) {
            if (g_shadowBuffers[i].in_use && g_shadowBuffers[i].is_shadow && g_shadowBuffers[i].target == target) {
                found_slot = i; break;
            }
        }
    }

    if (found_slot >= 0) {
        ShadowBufferMap entry = g_shadowBuffers[found_slot];
        g_shadowBuffers[found_slot].in_use = 0;
        g_shadowBuffers[found_slot].is_shadow = 0;
        g_shadowBuffers[found_slot].shadow_ptr = NULL;
        pthread_mutex_unlock(&g_shadowMutex);

        if (entry.shadow_ptr) {
            // CRITICAL FIX: Bind the buffer BEFORE calling glBufferSubData
            if (real_glBindBuffer && entry.buffer_id != 0) {
                real_glBindBuffer(target, entry.buffer_id);
            }
            if (real_glBufferSubData) {
                real_glBufferSubData(target, entry.offset, entry.length, entry.shadow_ptr);
            }
            // Free memory if not using the static fallback buffer
            if ((char*)entry.shadow_ptr < s_fallback_buffer || (char*)entry.shadow_ptr >= (s_fallback_buffer + sizeof(s_fallback_buffer))) {
                free(entry.shadow_ptr);
            }
        }
        if (real_glGetError) { unsigned int err; do { err = real_glGetError(); } while (err != 0); }
        return 1; // Success
    }
    pthread_mutex_unlock(&g_shadowMutex);

    // Fallback to real glUnmapBuffer if not in shadow map
    typedef int (*glUnmapBuffer_pfn)(unsigned int);
    static glUnmapBuffer_pfn real_glUnmapBuffer = NULL;
    if (!real_glUnmapBufffW"’°¢&VÅöÅVæÖ'VffW"Ò†vÅVæÖ'Vffer_pfn) dlsym(RTLD_DEFAULT, \"glUnmapBuffer\");
        if (!real_glUnmapBuffer) real_glUnmapBufffW"Ò†vÅVæÖ'VffW%÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&vÅVæÖ'VfffW$ôU5Â"“°¢Ğ¢–çB&W2Ò°¢–b‡&VÅövÅVæÖ'VffW"’&W2Ò&VÅövÅVæÖ'VffW"‡F&vWB“°¢–b‡&VÅövÄvWDW'&÷"’²Vç6–væVB–çBW'#²Fò²W'"Ò&VÅövÄvWDW'&÷"‚“²Òv†–ÆR†W'"Ò“²Ğ¢&WGW&â&W2ò&W2¢°§Ğ §7FF–2fö–BvÄÖVÖ÷'”&'&–W%÷7GV"‡Vç6–væVB–çB&'&–W'2’°¢G—VFVbfö–B‚¦vÄfÇW6…÷fâ’‚“°¢7FF–2vÄfÇW6…÷fâ&VÅövÄfÇW6‚ÒåTÄÃ°¢–b‚&VÅövÄfÇW6‚’°¢&VÅövÄfÇW6‚Ò†vÄfÇW6…÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&vÄfÇW6…Â"“°¢–b‚&VÅövÄfÇW6‚’&VÅövÄfÇW6‚Ò†vÄfÇW6…÷fâ’FÇ7–Ò…%DÄEôäU…BÂÂ&vÄfÇW6…Â"“°¢Ğ¢–b‡&VÅöÄfÇW6‚’&VÅövÄfÇW6‚‚“°¢Äôt’…Â&vÄÖVÖ÷'”&'&–W"7GV"6ÆÆVBæBfÇW6†VB7V66W76gVÆÇ’„&'&–W'3¢WR•Â"Â&'&–W'2“°§Ğ §7FF–26öç7BVç6–væVB6†"¢vÄvWE7G&–æuö†öö²‡Vç6–væVB–çBæÖR’°¢–b†æÖRÓÒtÅõdU%4”ôâ’&WGW&â†6öç7BVç6–væVB6†"¢•Â#Bãbãåd”D”SCRã#•Â#°¢VÇ6R–b†æÖRÓÒtÅõ$TäDU$U"’&WGW&â†6öç7BVç6–væVB6†"¢•Â$åd”D”vTf÷&6U%E‚C“Â#°¢VÇ6R–b†æÖRÓÒtÅõdTäDõ"’&WGW&â†6öç7BVç6–væVB6†"¢•Â$åd”D”6÷'÷&F–öåÂ#°¢VÇ6R–b†æÖRÓÒtÅôU…DTå4”ôå2’&WGW&â†6öç7BVç6–væVB6†"¢•Â$tÅô$%öF—&V7E÷7FFUö66W72tÅô$%ö'VffW%÷7F÷&vRtÅô$%÷6†FW%ö–ÖvUöÆöE÷7F÷&RtÅôåeö6öæF—F–öæÅ÷&VæFW"tÅôU…EöwU÷6†FW#BtÅôU…E÷FW‡GW&Uö'VffW"tÅôU…E÷FW‡GW&Uö7V&UöÖö'&’tÅôôU5ôTtÅö–ÖvUöW‡FW&æÅöW76Ã2tÅôåe÷6†FW%öæ÷W'7V7F—fUö–çFW'öÆF–öâtÅô$%÷6†FW%öö&¦V7G2tÅô$%÷fW'FW…÷6†FW"tÅô$%ög&vÖVçE÷6†FW"tÅôU…Eö&ÆVæEöWVF–öå÷6W&FRtÅôU…EövVöÖWG'•÷6†FW#BtÅôU…EöwU÷&öw&Õ÷&ÖWFW'2tÅô$%ö–ç7Fæ6VEö'&—2tÅô$%öG&uö–ç7Fæ6VEÂ#°¢G—VFVb6öç7BVç6–væVB6†"¢‚¦vÄvWE7G&–æu÷fâ’‡Vç6–væVB–çB“°¢7FF–2vÄvWE7G&–æu÷fâ&VÅövÄvWE7G&–ærÒåTÄÃ°¢–b‚&VÅövÄvWE7G&–ær’°¢&VÅövÄvWE7G&–ærÒ†vÄvWE7G&–æu÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&vÄvWE7G&–æuÂ"“°¢–b‚&VÅövÄvWE7G&–ær’&VÅövÄvWE7G&–ærÒ†vÄvWE7G&–æu÷fâ’FÇ7–Ò…%DÄEôäU…BÂÂ&vÄvWE7G&–æuÂ"“°¢Ğ¢–b‡&VÅövÄvWE7G&–ær’&WGW&â&VÅövÄvWE7G&–ær†æÖR“°¢&WGW&â†6öç7BVç6–væVB6†"¢•Â%Â#°§Ğ §7FF–26öç7BVç6–væVB6†"¢vÄvWE7G&–æv•ö†öö²‡Vç6–væVB–çBæÖRÂVç6–væVB–çB–æFW‚’°¢–b†æÖRÓÒtÅôU…DTå4”ôå2’°¢7FF–26öç7B6†"¢W‡FVç6–öç5µÒÒ°¢Â$tÅô$%öF—&V7E÷7FFUö66W75Â"ÅÂ$tÅô$%ö'VffW%÷7F÷&vUÂ"ÅÂ$tÅô$%÷6†FW%ö–ÖvUöÆöE÷7F÷&UÂ"’À¢Â$tÅôåeö6öæF—F–öæÅ÷&VæFW%Â"ÅÂ$tÅôU…EöwU÷6†FW#EÂ"ÅÂ$tÅôU…E÷FW‡GW&Uö'VffW%Â"À¢Â$tÅôU…E÷FW‡GW&Uö7V&UöÖö'&•Â"ÅÂ$tÅôôU5ôTtÅö–ÖvUöW‡FW&æÅöW76Ã5Â"À¢Â$tÅôåe÷6†FW%öæ÷W'7V7F—fUö–çFW'öÆF–öåÂ"ÅÂ$tÅô$%÷6†FW%öö&¦V7G5Â"À¢Â$tÅô$%÷fW'FW…÷6†FW%Â"ÅÂ$tÅô$%ög&vÖVçE÷6†FW%Â"ÅÂ$tÅôU…Eö&ÆVæEöWVF–öå÷6W&FUÂ"À¢Â$tÅôU…EövVöÖWG'•÷6†FW#EÂ"ÅÂ$tÅôU…EöwU÷&öw&Õ÷&ÖWFW'5Â"À¢Â$tÅô$%ö–ç7Fæ6VEö'&—5Â"ÅÂ$tÅô$%öG&uö–ç7Fæ6VEÂ ¢Ó°¢Vç6–væVB–çB6—¦RÒ6—¦Vöb†W‡FVç6–öç2’ò6—¦Vöb†W‡FVç6–öç5³Ò“°¢–b†–æFW‚Â6—¦R’&WGW&â†6öç7BVç6–væVB6†"¢–W‡FVç6–öç5¶–æFW…Ó°¢Ğ¢G—VFVb6öç7BVç6–væVB6†"¢‚¦vÄvWE7G&–æv•÷fâ’‡Vç6–væVB–çBÂVç6–væVB–çB“°¢7FF–2vÄvWE7G&–æv•÷fâ&VÅövÄvWE7G&–æv’ÒåTÄÃ°¢–b‚&VÅövÄvWE7G&–æv’’°¢&VÅövÄvWE7G&–æv’Ò†vÄvWE7G&–æv•÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&vÄvWE7G&–æv•Â"“°¢–b‚&VÅövÄvWE7G&–æv’’&VÅövÄvWE7G&–æv’Ò†vÄvWE7G&–æv•÷fâ’FÇ7–Ò…%DÄEôäU…BÂÂ&vÄvWE7G&–æv•Â"“°¢Ğ¢–b‡&VÅövÄvWE7G&–æv’’&WGW&â&VÅövÄvWE7G&–æv’†æÖRÂ–æFW‚“°¢&WGW&â†6öç7BVç6–væVB6†"¢•Â%Â#°§Ğ §fö–B¢VtvWE&ö4FG&W75ö†öö²†6öç7B6†"¢&ö6æÖR’°¢–b‡&ö6æÖRÓÒåTÄÂ’&WGW&âåTÄÃ°¢–b‡7G&6×‡&ö6æÖRÂÂ&vÄÖVÖ÷'”&'&–W%Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÄÖVÖ÷'”&'&–W$U…EÂ"’ÓÒ’&WGW&â‡fö–B¢’vÄÖVÖ÷'”&'&–W%÷7GV#°¢–b‡7G&6×‡&ö6æÖRÂÂ&vÄvWE7G&–æuÂ"’ÓÒ’&WGW&â‡fö–B¢’vÄvWE7G&–æuö†öö³°¢–b‡7G&6×‡&ö6æÖRÂÂ&vÄvWE7G&–æv•Â"’ÓÒ’&WGW&â‡fö–B¢’vÄvWE7G&–æv•ö†öö³°¢–b‡7G&6×‡&ö6æÖRÂÂ&vÄÖ'VffW%&ævUÂ"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÄÖ'VffW%&ævTU…EÂ"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÄÖ'VffW%&ævT$%Â"’ÓÒ’°¢Äôt’…Â&VvÄvWE&ö4FG&W75ö†öö³¢vÄÖ'VffW%&ævRÓâ6†F÷r'VffW%Â"“°¢&WGW&â‡fö–B¢’vÄÖ'VffW%&ævUö†öö³°¢Ğ¢–b‡7G&6×‡&ö6æÖRÂÂ&vÄÖ'VffW%Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÄÖ'VffW$ôU5Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÄÖ'VffW$$%Â"’ÓÒ’&WGW&â‡fö–B¢’vÄÖ'VffW%ö†öö³°¢–b‡7G&6×‡&ö6æÖRÂÂ&vÅVæÖ'VffW%Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÅVæÖ'VfffW$ôU5Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÅVæÖ'VfffW$$%Â"’ÓÒ’&WGW&â‡fö–B¢’vÅVæÖ'VffW%ö†öö³°¢–b‡7G&6×‡&ö6æÖRÂÂ&vÄvVå6×ÆW'5Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÄvVå6×ÆW'4ôU5Â"’ÓÒ’°¢G—VFVbfö–B¢‚§fâ’†6öç7B6†"¢“²7FF–2fâ&VÂÒåTÄÃ°¢–b‚&VÂ’&VÂÒ‡fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&VvÄvWE&ö4FG&W75Â"“°¢–b‡&VÂ’²fö–B¢2Ò&VÂ‡&ö6æÖR“²–b‡2’&WGW&â3²Ğ¢fö–B¢2ÒFÇ7–Ò…%DÄEôDTdTÅBÂ&ö6æÖR“²–b‡2’&WGW&â3°¢&WGW&â‡fö–B¢’vÄvVå6×ÆW'5öfÆÆ&6³°¢Ğ¢–b‡7G&6×‡&ö6æÖRÂÂ&vÄ&–æE6×ÆW%Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÄ&–æE6×ÆW$ôU5Â"’ÓÒ’°¢G—VFVbfö–B¢‚§fâ’†6öç7B6†"¢“²7FF–2fâ&VÂÒåTÄÃ°¢–b‚&VÂ’&VÂÒ‡fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&VvÄvWE&ö4FG&W75Â"“°¢–b‡&VÂ’²fö–B¢2Ò&VÂ‡&ö6æÖR“²–b‡2’&WGW&â3²Ğ¢fö–B¢2ÒFÇ7–Ò…%DÄEôDTdTÅBÂ&ö6æÖR“²–b‡2’&WGW&â3°¢&WGW&â‡fö–B¢’vÄ&–æE6×ÆW%öfÆÆ&6³°¢Ğ¢–b‡7G&6×‡&ö6æÖRÂÂ&vÄFVÆWFU6×ÆW'5Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÄFVÆWFU6×ÆW'4ôU5Â"’ÓÒ’°¢G—VFVbfö–B¢‚§fâ’†6öç7B6†"¢“²7FF–2fâ&VÂÒåTÄÃ°¢–b‚&VÂ’&VÂÒ‡fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&VvÄvWE&ö4FG&W75Â"“°¢–b‡&VÂ’²fö–B¢2Ò&VÂ‡&ö6æÖR“²–b‡2’&WGW&â3²Ğ¢fö–B¢2ÒFÇ7–Ò…%DÄEôDTdTÅBÂ&ö6æÖR“²–b‡2’&WGW&â3°¢&WGW&â‡fö–B¢’vÄFVÆWFU6×ÆW'5öfÆÆ&6³°¢Ğ¢–b‡7G&6×‡&ö6æÖRÂÂ&vÅ6×ÆW%&ÖWFW&•Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÅ6×ÆW%&ÖWFW&”ôU5Â"’ÓÒ’°¢G—VFVbfö–B¢‚§fâ’†6öç7B6†"¢“²7FF–2fâ&VÂÒåTÄÃ°¢–b‚&VÂ’&VÂÒ‡fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&VvÄvWE&ö4FG&W75Â"“°¢–b‡&VÂ’²fö–B¢2Ò&VÂ‡&ö6æÖR“²–b‡2’&WGW&â3²Ğ¢fö–B¢2ÒFÇ7–Ò…%DÄEôDTdTÅBÂ&ö6æÖR“²–b‡2’&WGW&â3°¢&WGW&â‡fö–B¢’vÅ6×ÆW%&ÖWFW&•öfÆÆ&6³°¢Ğ¢–b‡7G&6×‡&ö6æÖRÂÂ&vÄÖ'VffW%&ævUÂ"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÄÖ'VffW%&ævTU…EÂ"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÄÖ'VffW%&ævT$%Â"’ÓÒ’°¢&–çFb…Â$Åt¤tÂÆ–æ¶W&†öö³¢VvÄvWE&ö4FG&W72†öö¶VBvÄÖ'VffW%&ævRÓâ6†F÷r'VffW%ÅÆåÂ"“°¢&WGW&â‡fö–B¢’vÄÖ'VffW%&ævUö†öö³°¢Ğ¢–b‡7G&6×‡&ö6æÖRÂÂ&vÄÖ'VffW%Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÄÖ'VffW$ôU5Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÄÖ'VffW$$%Â"’ÓÒ’°¢&–çFb…Â$Åt¤tÂÆ–æ¶W&†öö³¢VvÄvWE&ö4FG&W72†öö¶VBvÄÖ'VffW"Óâ6†F÷r'VffW%ÅÆåÂ"“°¢&WGW&â‡fö–B¢’vÄÖ'VffW%ö†öö³°¢Ğ¢–b‡7G&6×‡&ö6æÖRÂÂ&vÅVæÖ'VffW%Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÅVæÖ'VffW$ôU5Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÅVæÖ'VfffW$$%Â"’ÓÒ’°¢&–çFb…Â$Åt¤tÂÆ–æ¶W&†öö³¢VvÄvWE&ö4FG&W72†öö¶VBvÅVæÖ'VffW"Óâ6†F÷r'VffW%ÅÆåÂ"“°¢&WGW&â‡fö–B¢’vÅVæÖ'VffW%ö†öö³°¢Ğ¢–b‡7G&6×‡&ö6æÖRÂÂ&vÄÖVÖ÷'”&'&–W%Â"’ÓÒÇÂ7G&6×‡&ö6æÖRÂÂ&vÄÖVÖ÷'”&'&–W$U…EÂ"’ÓÒ’°¢&–çFb…Â$Åt¤tÂÆ–æ¶W&†öö³¢VvÄvWE&ö4FG&W72†öö¶VBvÄÖVÖ÷'”&'&–W%ÅÆåÂ"“°¢&WGW&â‡fö–B¢’vÄÖVÖ÷'”&'&–W%÷7GV#°¢Ğ¢G—VFVbfö–B¢‚¦VvÄvWE&ö4FG&W75÷fâ’†6öç7B6†"¢“°¢7FF–2VvÄvWE&ö4FG&W75÷fâ&VÅöVvÄvWE&ö4FG&W72ÒåTÄÃ°¢–b‚&VÅöVvÄvWE&ö4FG&W72’°¢&VÅöVvÄvWE&ö4FG&W72Ò†VvÄvWE&ö4FG&W75÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂÂ&VvÄvWE&ö4FG&W75Â"“°¢–b‚&VÅöVvÄvWE&ö4FG&W72’&VÅöVvÄvWE&ö4FG&W72Ò†VvÄvWE&ö4FG&W75÷fâ’FÇ7–Ò…%DÄEôäU…BÂÂ&VvÄvWE&ö4FG&W75Â"“°¢Ğ¢–b‡&VÅöVvÄvWE&ö4FG&W72’²fö–B¢7–ÒÒ&VÅöVvÄvWE&ö4FG&W72‡&ö6æÖR“²–b‡7–Ò’&WGW&â7–Ó²Ğ¢fö–B¢7–ÒÒFÇ7–Ò…%DÄEôDTdTÅBÂ&ö6æÖR“²–b‡7–Ò’&WGW&â7–Ó°¢&WGW&â‡fö–B¢’Væ—fW'6Å÷7GV%÷fö–C°§Ğ §7FF–2¦ÆöæræFÆ÷Våö'Vvf—‚…õöGG&–'WFUõò‚‡VçW6VB’’¤ä”Vçb¦VçbÀ¢õöGG&–'WFUõõò‚‡VçW6VB’’¦6Æ726Æ72À¢¦Æöærf–ÆVæÖU÷G"Â¦–çB¦ÖöFR’°¢6öç7B6†"¢f–ÆVæÖRÒ†6öç7B6†"¢’f–ÆVæÖU÷G#°¢–b†f–ÆVæÖRÒåTÄÂ’°¢–b‡7G&6×†f–ÆVæÖRÂÂ&Æ–'gVÆ¶âç6õÂ"’ÓÒ’°¢&–çFb…Â$Åt¤tÂÆ–æ¶W&†öö³¢&WÆ6–ærÆöBf÷"Æ–'gVÆ¶âç6òv—F‚7W7FöÒG&—fW%ÅÆåÂ"“°¢&WGW&â†¦Æöær’ö¦fW†V5öÆöEgVÆ¶äG&—fW"‚“°¢Ğ¢–b‡7G&6×†f–ÆVæÖRÂÂ&Æ–%GW&&õcç6õÂ"’ÓÒÇÂ7G&6×†f–ÆVæÖRÂÂ&Æ–$tÂç6õÂ"’ÓÒÇÂ7G&6×†f–Å•¹…µ”°p‰±¥‰0¹Í¼¸Åpˆ¤€ôô€À¤ì(€€€€€€€€€€€ÁÉ¥¹Ñ˜¡p‰1])0±¥¹­•É¡½½¬èÉ•Á±…¥¹œ=Á•¹0İ¥Ñ É•¹‘•ÉÍÁ•Œ‘É¥Ù•È€ •Ì¥qq¹pˆ°™¥±•¹…µ”¤ì(€€€€€€€€€€€½¹ÍĞÁ½©…Ù•á•}É•¹‘•ÉÍÁ•}Ğ€©ÉÍÁ•Œ€ôÁ½©…Ù•á•}•ÑI•¹‘•ÉMÁ•Œ ¤ì(€€€€€€€€€€€¥˜€¡ÉÍÁ•Œ€˜˜ÉÍÁ•Œ´ù•±}…ÅÕ¥É”€˜˜ÉÍÁ•Œ´ù•±}Á…Ñ ¤ì(€€€€€€€€€€€€€€€É•ÑÕÉ¸€¡©±½¹œ¤ÉÍÁ•Œ´ù•±}…ÅÕ¥É”¡ÉÍÁ•Œ´ù•±}Á…Ñ ¤ì(€€€€€€€€€€€ô(€€€€€€€ô(€€€ô(€€€É•ÑÕÉ¸€¡©±½¹œ¤‘±½Á•¸¡™¥±•¹…µ”°€¡¥¹Ğ¥©µ½‘”¤ì)ô()ÍÑ…Ñ¥Œ©±½¹œ¹‘±Íåµ}¡½½¬¡}}…ÑÑÉ¥‰ÕÑ•}| ¡Õ¹ÕÍ•¤¤)9%¹Ø€©•¹Ø°(€€€€€€€€€€€€€€€€€€€€€€}}…ÑÑÉ¥‰ÕÑ•}}|¡Õ¹ÕÍ•¤¤©±…ÍÌ±…ÍÌ°(€€€€€€€€€€€€€€€€€€€€€€©±½¹œ¡…¹‘±”°©±½¹œÍåµ‰½±}ÁÑÈ¤ì(€€€½¹ÍĞ¡…È¨Íåµ‰½°€ô€¡½¹ÍĞ¡…È¨¤Íåµ‰½±}ÁÑÈì(€€€¥˜€¡Íåµ‰½°€„ô9U10¤ì(€€€€€€€¥˜€¡ÍÑÉµÀ¡Íåµ‰½°°p‰•±•ÑAÉ½‘‘É•ÍÍpˆ¤€ôô€À¤ì(€€€€€€€€€€€ÁÉ¥¹Ñ˜¡p‰1])0±¥¹­•É¡½½¬è¡½½­••±•ÑAÉ½‘‘É•ÍÍqq¹pˆ¤ì(€€€€€€€€€€€É•ÑÕÉ¸€¡©±½¹œ¤•±•ÑAÉ½‘‘É•ÍÍ}¡½½¬ì(€€€€€€€ô(€€€€€€€¥˜€¡ÍÑÉµÀ¡Íåµ‰½°°p‰±•ÑMÑÉ¥¹pˆ¤€ôô€À¤ì(€€€€€€€€€€€ÁÉ¥¹Ñ˜¡p‰1])0±¥¹­•É¡½½¬è¡½½­•±•ÑMÑÉ¥¹qq¹pˆ¤ì(€€€€€€€€€€€É•ÑÕÉ¸€¡©±½¹œ¤±•ÑMÑÉ¥¹}¡½½¬ì(€€€€€€€ô(€€€€€€€¥˜€¡ÍÑÉµÀ¡Íåµ‰½°°p‰±•ÑMÑÉ¥¹¥pˆ¤€ôô€À¤ì(€€€€€€€€€€€ÁÉ¥¹Ñ˜¡p‰1])0±¥¹­•É¡½½¬è¡½½­•±•ÑMÑÉ¥¹¥qq¹pˆ¤ì(€€€€€€€€€€€É•ÑÕÉ¸€¡©±½¹œ¤±•ÑMÑÉ¥¹¥}¡½½¬ì(€€€€€€€ô(€€€€€€€¥˜€¡ÍÑÉµÀ¡Íåµ‰½°°p‰±5•µ½Éå	…ÉÉ¥•Épˆ¤€ôô€ÀñğÍÑÉµÀ¡Íåµ‰½°°p‰±5•µ½Éå	…ÉÉ¥•ÉaQpˆ¤€ôô€À¤ì(€€€€€€€€€€€ÁÉ¥¹Ñ˜¡p‰1])0±¥¹­•É¡½½¬è¡½½­•±5•µ½Éå	…ÉÉ¥•Éqq¹pˆ¤ì(€€€€€€€€€€€É•ÑÕÉ¸€¡©±½¹œ¤±5•µ½Éå	…ÉÉ¥•É}ÍÑÕˆì(€€€€€€€ô(€€€€€€€¥˜€¡ÍÑÉµÀ¡Íåµ‰½°°p‰±5…Á	Õ™™•ÉI…¹•pˆ¤€ôô€ÀñğÍÑÉµÀ¡Íåµ‰½°°p‰±5…Á	Õ™™•ÉI…¹•aQpˆ¤€ôô€ÀñğÍÑÉµÀ¡Íåµ‰½°°p‰±5…Á	Õ™™•ÉI…¹•I	pˆ¤€ôô€À¤ì(€€€€€€€€€€€ÁÉ¥¹Ñ˜¡p‰1])0±¥¹­•É¡½½¬è¡½½­•±5…Á	Õ™™•ÉI…¹”€´øÍ¡…‘½Ü‰Õ™™•Éqq¹pˆ¤ì(€€€€€€€€€€€É•ÑÕÉ¸€¡©±½¹œ¤±5…Á	Õ™™•ÉI…¹•}¡½½¬ì(€€€€€€€ô(€€€€€€€¥˜€¡ÍÑÉµÀ¡Íåµ‰½°°p‰±5…Á	Õ™™•Épˆ¤€ôô€ÀñğÍÑÉµÀ¡Íåµ‰½°°p‰±5…Á	Õ™™•É=Mpˆ¤€ôô€ÀñğÍÑÉµÀ¡Íåµ‰½°°p‰±5…Á	Õ™™•ÉI	pˆ¤€ôô€À¤ì(€€€€€€€€€€€ÁÉ¥¹Ñ˜¡p‰1])0±¥¹­•É¡½½¬è¡½½­•±5…Á	Õ™™•È€´øÍ¡…‘½Ü‰Õ™™•Éqq¹pˆ¤ì(€€€€€€€€€€€É•ÑÕÉ¸€¡©±½¹œ¤±5…Á	Õ™™•É}¡½½¬ì(€€€€€€€ô(€€€€€€€¥˜€¡ÍÑÉµÀ¡Íåµ‰½°°p‰±U¹µ…Á	Õ™™•Épˆ¤€ôô€ÀñğÍÑÉµÀ¡Íåµ‰½°°p‰±U¹µ…Á	Õ™™™\“ÑT×ŠHOHİ˜Û\
Ş[X›Û™Û[›X\Y™™™\T—ŠHOH
HÂˆš[Š“Ò‘Ó[šÙ\šÛÚÎˆÛÚÙYÛ[›X\Y™™\ˆOˆÚYİÈY™™\——ŠNÂˆ™]\›ˆ
›Û™ÊHÛ[›X\Y™™\—ÚÛÚÎÂˆBˆYˆ
İ˜Û\
Ş[X›Û™ÛÙ[”Ø[\\œ×ŠHOHİ˜Û\
Ş[X›Û™ÛÙ[”Ø[\\œÓÑT×ŠHOH
HÂˆ›ÚY
ˆŞ[HHŞ[J
›ÚY
ŠH[™KŞ[X›Û
NÈYˆ
Ş[JH™]\›ˆ
›Û™ÊHŞ[NÂˆ™]\›ˆ
›Û™ÊHÛÙ[”Ø[\\œ×Ù˜[˜XÚÎÂˆBˆYˆ
İ˜Û\
Ş[X›Û™Ûš[™Ø[\\—ŠHOHİ˜Û\
Ş[X›Û™Ûš[™Ø[\\“ÑT×ŠHOH
HÂˆ›ÚY
ˆŞ[HHŞ[J
›ÚY
ŠH[™KŞ[X›Û
NÈYˆ
Ş[JH™]\›ˆ
›Û™ÊHŞ[NÂˆ™]\›ˆ
›Û™ÊHÛš[™Ø[\\—Ù˜[˜XÚÎÂˆBˆYˆ
İ˜Û\
Ş[X›Û™Û[]TØ[\\œ×ŠHOHİ˜Û\
Ş[X›Û™Û[]TØ[\\œÓÑT×ŠHOH
HÂˆ›ÚY
ˆŞ[HHŞ[J
›ÚY
ŠH[™KŞ[X›Û
NÈYˆ
Ş[JH™]\›ˆ
›Û™ÊHŞ[NÂˆ™]\›ˆ
›Û™ÊHÛ[]TØ[\\œ×Ù˜[˜XÚÎÂˆBˆYˆ
İ˜Û\
Ş[X›Û™ÛØ[\\”\˜[Y]\šWŠHOHİ˜Û\
Ş[X›Û™ÛØ[\\”\˜[Y]\šSÑT×ŠHOH
HÂˆ›ÚY
ˆŞ[HHŞ[J
›ÚY
ŠH[™KŞ[X›Û
NÈYˆ
Ş[JH™]\›ˆ
›Û™ÊHŞ[NÂˆ™]\›ˆ
›Û™ÊHÛØ[\\”\˜[Y]\šWÙ˜[˜XÚÎÂˆBˆBˆ›ÚY
ˆŞ[HHŞ[J
›ÚY
ŠH[™KŞ[X›Û
NÂˆYˆ
\Ş[H	‰ˆŞ[X›Û	‰ˆİ›˜Û\
Ş[X›Û™Û‹ŠHOH
H™]\›ˆ
›Û™ÊH[š]™\œØ[ÜİX—İ›ÚYÂˆ™]\›ˆ
›Û™ÊHŞ[NÂŸB‚›ÚY[œİ[Ú™ÛÜ[’ÛÚÊ“’Q[ˆ
™[ŠHÂˆÑÒJ’[œİ[[™ÈÒ‘ÓÜ[Š
H[™Ş[J
HÛÚÜÈ
•RSŒŒŒLËQJWŠNÂˆš[Š“Ò‘Ó[šÙ\šÛÚÎˆ[œİ[[™ÈÜ[‹ÙŞ[HÛÚÜÈ
•RSŒŒŒLËQJW—ŠNÂˆÛ\ÜÈ[˜[ZXÔš[šÓØY\ˆH

™[ŠKO‘š[™Û\ÜÊ[‹›Ü™ËÛÚ™ÛÜŞ\İ[KÛ[^Ñ[˜[ZXÓ[šÓØY\—ŠNÂˆYŠ[˜[ZXÓ[šÓØY\ˆOH•S
HÂˆÑÑJ‘˜Z[YÈš[™H\™Ù]Û\Ü×ŠNÂˆš[Š“Ò‘Ó[šÙ\šÛÚÈT”“Ôˆ˜Z[YÈš[™[˜[ZXÓ[šÓØY\ˆÛ\Ü×—ŠNÂˆ

™[ŠKO‘^Ù\[ÛÛX\Š[ŠNÂˆ™]\›ÂˆBˆ“’S˜]]™SY]ÙÛÚÜÖ×HHÂˆ×›™Ü[—‹ŠŠRJR—‹	–æFÆ÷Våö'Vvf—‡ÒÀ¢µÂ&æFÇ7–ÕÂ"ÂÂ"„¤¢”¥Â"ÂfæFÇ7–Õö†öö·Ğ¢Ó°¢–b‚‚¦Vçb’Óå&Vv—7FW$æF—fW2†VçbÂG–æÖ–4Æ–æ´ÆöFW"Â†öö·2Â"’Ò’°¢&–çFb…Â$Åt¤tÂÆ–æ¶W&†öö³¢&Vv—7FW$æF—fW2f–ÆVEÅÆåÂ"“°¢ÒVÇ6R°¢&–çFb…Â$Åt¤tÂÆ–æ¶W&†öö³¢FÆ÷VâöFÇ7–Ò†öö·2–ç7FÆÆVB7V66W76gVÆÇ•Æâ"“°¢Ğ§