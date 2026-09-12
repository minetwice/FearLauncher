//
// Created by maks on 06.01.2025.
//

#include "jvm_hooks.h"

#include <android/api-level.h>

#include <dlfcn.h>
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
    LOGI("LWJGL linkerhook: universal GL stub executed");
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
        real_glGetIntegerv = (glGetIntegerv_pfn) dlsym(RTLD_DEFAULT, "glGetIntegerv");
        if (!real_glGetIntegerv) real_glGetIntegerv = (glGetIntegerv_pfn) dlsym(RTLD_NEXT, "glGetIntegerv");
    }
    if (!real_glGetError) return 0;

    unsigned int pname = 0x8894; // GL_ARRAY_BUFFER_BINDING
    switch (target) {
        case 0x8892: pname = 0x8894; break; // GL_ARRAY_BUFFER -> GL_ARRAY_BUFFER_BINDING
        case 0x8893: pname = 0x8895; break; // GL_ELEMENT_ARRAY_BUFFER -> GL_ELEMENT_ARRAY_BUFFER_BINDING
        case 0x8A11: pname = 0x8A28; break; // GL_UNIFORM_BUFFER -> GL_UNIFORM_BUFFER_BINDING
        case 0x90D2: pname = 0x90D3; break; // GL_SHADER_STORAGE_BUFFER -> GL_SHADER_STORAGE_BUFFER_BINDING
        case 0x8F36: pname = 0x8F36; break; // GL_COPY_READ_BUFFER
        case 0x8F37: pname = 0x8F37; break; // GL_COPY_WÔ’UWĞ•Q‘‘T‚ˆØ\ÙHPˆ˜[YHHQÈœ™XZÎÈËÈÓÔVSÔPÒ×Ğ•Q‘‘TˆOˆÓÔVSÔPÒ×Ğ•Q‘‘T—Ğ’S‘S‘ÂˆØ\ÙHPÎˆ˜[YHHQÈœ™XZÎÈËÈÓÔVSÕS”PÒ×Ğ•Q‘‘TˆOˆÓÔVSÕS”PÒ×Ğ•Q‘‘T—Ğ’S‘S‘ÂˆØ\ÙHÎNˆ˜[YHHÎÈœ™XZÎÈËÈÓÕS”Ñ“Ô“WÑ‘QQPÒ×Ğ•Q‘‘TˆOˆÓÕS”Ñ“Ô“WÑ‘QQPÒ×Ğ•Q‘‘T—Ğ’S‘S‘ÂˆØ\ÙHLQNˆ˜[YHHLQNÈœ™XZÎÈËÈÓÑTÔUÒÒS‘T‘PÕĞ•Q‘‘TˆOˆÓÑTÔUPÒÒS‘T‘PÕĞ•Q‘‘T—Ğ’S‘S‘ÂˆØ\ÙHŒÎNˆ˜[YHHÎÈœ™XZÎÈËÈÓÑU×ÒS‘T‘PÕĞ•Q‘‘TˆOˆÓÑU×ÒS‘T‘PÕĞ•Q‘‘T—Ğ’S‘S‘ÂˆY˜][ˆ˜[YHHMÈœ™XZÎÂˆB‚ˆ[˜[HÂˆ™X[ÙÛÙ][YÙ\—Ü›ˆ™X[ÙÛÙ][YÙ\ˆH•SÂˆYˆ
\™X[ÙÛÙ][YÙ\ŠHÂˆ™X[ÙÛÙ][YÙ\ˆH
ÛÙ][YÙ\—Ü›ŠHŞ[J•ÑQUS™ÛÙ][YÙ\ˆŠNÂˆYˆ
\™X[ÙÛÙ][YÙ\ŠH™X[ÙÛÙ][YÙ\ˆH
ÛÙ][YÙ\—Ü›ŠHŞ[J•Ó‘V™ÛÙ][YÙ\ˆŠNÂˆBˆYˆ
\™X[ÙÛÙ]\œ›ÜŠH™]\›ˆÂ‚ˆ[œÚYÛ™Y[˜[YHHMÈËÈÓĞT”VWĞ•Q‘‘T—Ğ’S‘S‘ÂˆİÚ]Ú
\™Ù]
HÂˆØ\ÙHLˆ˜[YHHMÈœ™XZÎÈËÈÓĞT”VWĞ•Q‘‘TˆOˆÓĞT”VWĞ•Q‘‘T—Ğ’S‘S‘ÂˆØ\ÙHLÎˆ˜[YHHMNÈœ™XZÎÈËÈÓÑSSQS•ĞT”VWĞ•Q‘‘TˆOˆÓÑSSQS•ĞT”VWĞ•Q‘‘T—Ğ’S‘S‘ÂˆØ\ÙHLLNˆ˜[YHHLÈœ™XZÎÈËÈÓÕS’Q“Ô“WĞ•Q‘‘TˆOˆÓÕS’Q“Ô“WĞ•Q‘‘T—Ğ’S‘S‘ÂˆØ\ÙHLˆ˜[YHHLÎÈœ™XZÎÈËÈÓÔÒQT—ÔÕÔQÑWĞ•Q‘‘TˆOˆÓÔÒQT—ÔÕÔQÑWĞ•Q‘‘T—Ğ’S‘S‘ÂˆØ\ÙHŒÍˆ˜[YHHŒÍÈœ™XZÎÈËÈÓĞÓÔWÔ‘PQĞ•Q‘‘T‚ˆØ\ÙHŒÍÎˆ˜[YHHŒÍÎÈœ™XZÎÈËÈÓĞÓÔWÕõ$•DUô%TddU ¢66Rƒƒ„T#¢æÖRÒƒƒ„TC²'&V³²òòtÅõ•„TÅõ4µô%TddU"ÓâtÅõ•„TÅõ4µô%TddU%ô$”äD”äp¢66Rƒƒ„T3¢æÖRÒƒƒ„Tc²'&V³²òòtÅõ•„TÅõTå4µô%TddU"ÓâtÅõ•„TÅõTå4µô%TddU%ô$”äD”äp¢66Rƒ„3„S¢æÖRÒƒ„3„c²'&V³²òòtÅõE$å4dõ$ÕôdTTD$4µô%TddU"ÓâtÅõE$å4dõ$ÕôdTTD$4µô%TddU%ô$”äD”äp¢66Rƒ“TS¢æÖRÒƒ“TS²'&V³²òòtÅôD•5D4…ô”äD•$T5Eô%TddU"ÓâtÅôD•5CÔ4…ô”äD•$T5Eô%TddU%ô$”äD”äp¢66Rƒ„c3“¢æÖRÒƒ„cC3²'&V³²òòtÅôE$uô”äD•$T5Eô%TddU"ÓâtÅôE$uô”äD•$T5Eô%TddU%ô$”äD”äp¢FVfVÇC¢æÖRÒƒƒƒ“C²'&V³°¢Ğ ¢–çBfÂÒ°¢&VÅövÄvWD–çFVvW'e÷fâ&VÅövÄvWD–çFVvW'bÒåTÄÃ°¢–b‚&VÅövÄvWD–çFVvW'b’°¢&VÅövÄvWD–çFVvW'bÒ†vÄvWD–çFVvW'e÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂ&vÄvWD–çFVvW'b"“°¢–b‚&VÅövÄvWD–çFVvW'b’&VÅövÄvWD–çFVvW'bÒ†vÄvWD–çFVvW'e÷fâ’FÇ7–Ò…%DÄEôäU…BÂ&vÄvWD–çFVvW'b"“°¢Ğ¢–b‚&VÅövÄvWDW'&÷"’&WGW&â° ¢fö–B¢G"ÒåTÄÃ° ¢–b‡÷6—…öÖVÖÆ–vâ‚gG"ÂcBÂÆÆö5öÆVâ’ÒÇÂG"ÓÒåTÄÂ’°¢G"ÒÖÆÆö2†ÆÆö5öÆVâ“°¢Ğ¢–b‚G"’G"Ò6ÆÆö2ƒÂÆÆö5öÆVâ“° ¢–b‚G"’°¢ÄôtR‚$Åt¤tÂÆ–æ¶W&†öö³¢VÖW&vVæ7’fÆÆ&6²'VffW"W6VBf÷"ÆÆö5öÆVãÒVÆB"ÂÆÆö5öÆVâ“°¢G"Ò5öfÆÆ&6µö'VffW#°¢Ğ ¢F‡&VEö×WFW…öÆö6²‚fu÷6†F÷t×WFW‚“°¢–çB6Æ÷BÒf–æEög&VU÷6†F÷u÷6Æ÷B‚“°¢–b‡6Æ÷BãÒ’°¢u÷6†F÷t'VffW'5·6Æ÷EÒçF&vWBÒF&vWC°¢u÷6†F÷t'VffW'5·6Æ÷EÒæ'VffW%ö–BÒ'VffW%ö–C°¢u÷6†F÷t'VffW'5·6Æ÷EÒæöfg6WBÒöfg6WC°¢u÷6†F÷t'VffW'5·6Æ÷EÒæÆVæwF‚ÒÆÆö5öÆVã°¢u÷6†F÷t'VffW'5·6Æ÷EÒç6†F÷u÷G"ÒG#°¢u÷6†F÷t'VffW'5·6Æ÷EÒæ—5÷6†F÷rÒ°¢u÷6†F÷t'VffW'5·6Æ÷EÒæ–å÷W6RÒ°¢ÒVÇ6R°¢Äôur‚$Åt¤tÂÆ–æ¶W&†öö³¢6†F÷r6Æ÷G2gVÆÂÂ&WGW&æ–ærVæÖævVB'VffW""“°¢Ğ¢F‡&VEö×WFW…÷VæÆö6²‚fu÷6†F÷t×WFW‚“° ¢G—VFVbVç6–væVB–çB‚¦vÄvWDW'&÷%÷fâ’‡fö–B“°¢7FF–2vÄvWDW'&÷%÷fâ&VÅövÄvWDW'&÷"ÒåTÄÃ°¢–b‚&VÅövÄvWDW'&÷"’°¢&VÅövÄvWDW'&÷"Ò†vÄvWDW'&÷%÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂ&vÄvWDW'&÷""“°¢–b‚&VÅövÄvWDW'&÷"’&VÅövÄvWDW'&÷"Ò†vÄvWDW'&÷%÷fâ’FÇ7–Ò…%DÄEôäU…BÂ&vÄvWDW'&÷""“°¢Ğ¢–b‡&VÅövÄvWDW'&÷"’²Vç6–væVB–çBW'#²Fò²W'"Ò&VÅövÄvWDW'&÷"‚“²Òv†–ÆR†W'"Ò“²Ğ¢&WGW&âG#°§Ğ §7FF–2fö–B¢vÄÖ'VffW%ö†öö²‡Vç6–væVB–çBF&vWBÂVç6–væVB–çB66W72’°¢G—VFVbfö–B‚¦vÄvWD'VffW%&ÖWFW&—e÷fâ’‡Vç6–væVB–çBÂVç6–væVB–çBÂ–çB¢“°¢7FF–2vÄvWD'VffW%&ÖWFW&—e÷fâ&VÅövÄvWD'VffW%&ÖWFW&—bÒåTÄÃ°¢–b‚&VÅövÄvWD'VffW%&ÖWFW&—b’°¢&VÅövÄvWD'VffW%&ÖWFW&—bÒ†vÄvWD'VffW%&ÖWFW&—e÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂ&vÄvWD'VffW%&ÖWFW&—b"“°¢–b‚&VÅövÄvWD'VffW%&ÖWFW&—b’&VÅövÄvWD'VffW%&ÖWFW&—bÒ†vÄvWD'VffW%&ÖWFW&—e÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂ&vÄvWD'VffW%&ÖWFW&—d$""“°¢Ğ¢–çB'Ve÷6—¦RÒ°¢–b‡&VÅövÄvWD'VffW%&ÖWFW&—b’&VÅövÄvWD'VffW%&ÖWFW&—b‡F&vWBÂƒƒscBÂf'Ve÷6—¦R“°¢ÆöærÆVâÒ†'Ve÷6—¦Râ’ò'Ve÷6—¦R¢cSS3c°¢Vç6–væVB–çB&ævT66W72Òƒ#°¢–b†66W72ÓÒƒƒ„#‚’&ævT66W72Òƒ°¢VÇ6R–b†66W72ÓÒƒƒ„$’&ævT66W72ÒƒÂƒ#°¢&WGW&âvÄÖ'VffW%&ævUö†öö²‡F&vWBÂÂÆVâÂ&ævT66W72“°§Ğ §7FF–2–çBvÅVæÖ'VffW%ö†öö²‡Vç6–væVB–çBF&vWB’°¢G—VFVbfö–B‚¦vÄ'VffW%7V$FF÷fâ’‡Vç6–væVB–çBÂÆöærÂÆöærÂ6öç7Bfö–B¢“°¢G—VFVbfö–B‚¦vÄ&–æD'VffW%÷fâ’‡Vç6–væVB–çBÂVç6–væVB–çB“°¢G—VFVbVç6–væVB–çB‚¦vÄvWDW'&÷%÷fâ’‡fö–B“° ¢7FF–2vÄ'VffW%7V$FF÷fâ&VÅövÄ'VffW%7V$FFÒåTÄÃ°¢7FF–2vÄ&–æD'VffW%÷fâ&VÅövÄ&–æD'VffW"ÒåTÄÃ°¢7FF–2vÄvWDW'&÷%÷fâ&VÅövÄvWDW'&÷"ÒåTÄÃ° ¢–b‚&VÅövÄ'VffW%7V$FF’°¢&VÅövÄ'VffW%7V$FFÒ†vÄ'VffW%7V$FF÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂ&vÄ'VffW%7V$FF"“°¢–b‚&VÅövÄ'VffW%7V$FF’&VÅövÄ'VffW%7V$FFÒ†vÄ'VffW%7V$FF÷fâ’FÇ7–Ò…%DÄEôDTdTÅBÂ&vÄ'VfferSubDatARB");
    }
    if (!real_glBindBuffer) {
        real_glBindBuffer = (glBindBuffer_pfn) dlsym(RTLD_DEFAULT, "glBindBuffer");
    }
    if (!real_glGetError) {
        real_glGetError = (glGetError_pfn) dlsym(RTLD_DEFAULT, "glGetError");
        if (!real_glGetError) real_glGetError = (glGetError_pfn) dlsym(RTLD_NEXT, "glGetError");
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
    if (!real_glUnmapBuffer) {
        real_glUnmapBuffer = (glUnmapBuffer_pfn) dlsym(RTLD_DEFAULT, "glUnmapBuffer");
        if (!real_glUnmapBuffer) real_glUnmapBuffer = (glUnmapBuffer_pfn) dlsym(RTLD_DEFAULT, "glUnmapBufferOES");
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
        real_glFlush = (glFlush_pfn) dlsym(RTLD_DEFAULT, "glFlush");
        if (!real_glFlush) real_glFlush = (glFlush_pfn) dlsym(RTLD_NEXT, "glFlush");
    }
    if (real_glFlush) real_glFlush();
    LOGI("glMemoryBarrier stub called and flushed successfully (Barriers: %u)", barriers);
}

static unsigned int eglGetError_stub(void) {
    return 0x3000; // EGL_SUCCESS
}

void* hooked_glfwCreateWindow(int width, int height, const char* title, void* monitor, void* share) {
    printf("TurboV1 Interceptor: Executing hooked_glfwCreateWindow with EGL OpenGL ES context for Zink\n");
    fflush(stdout);
    typedef void (*glfwWindowHint_pfn)(int, int);
    typedef void* (*glfwCreateWindow_pfn)(int, int, const char*, void*, void*);

    static glfwWindowHint_pfn real_win_hint = NULL;
    static glfwCreateWindow_pfn real_create_win = NULL;

    if (!real_win_hint) {
        real_win_hint = (glfwWindowHint_pfn) dlsym(RTLD_DEFAULT, "glfwWindowHint");
    }
    if (!real_create_win) {
        real_create_win = (glfwCreateWindow_pfn) dlsym(RTLD_DEFAULT, "glfwCreateWindow");
    }

    if (real_win_hint) {
        real_win_hint(0x00022001 /* GLFW_CLIENT_API */, 0x00030001 /* GLFW_OPENGL_ES_API */);
        real_win_hint(0x0002200B /* GLFW_CONTEXT_CREATION_API */, 0x00036002 /* GLFW_EGL_CONTEXT_API */);
    }

    if (real_create_win) {
        return real_create_win(width, height, title, monitor, share);
    }
    return NULL;
}
