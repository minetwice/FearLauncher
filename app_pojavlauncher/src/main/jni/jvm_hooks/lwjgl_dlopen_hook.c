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

    unsigned int pname = 0x8894; // GL_ARRAY_BUFFER_BINDINg
    switch (target) {
        case 0x8892: pname = 0x8894; break; // GL_ARRAY_BUFFER -> GL_ARRAY_BUFFER_BINDING
        case 0x8893: pname = 0x8895; break; // GL_ELEMENT_ARRAY_BUFFER -> GL_ELEMENT_ARRAY_BUFFER_BINDING
        case 0x8A11: pname = 0x8A28; break; // GL_UNIFORM_BUFFER -> GL_UNIFORM_BUFFER_BINDINg
        case 0x90D2: pname = 0x90D3; break; // GL_SHADER_STORAGE_BUFFER -> GL_SHADER_STORAGE_BUFFER_BINDINg
        case 0x8F36: pname = 0x8F36; break; // GL_COPY_READ_BUFFER
        case 0x8F37: pname = 0x8F37; break; // GL_COPY_WÔ’UWÐ•Q‘‘T‚ˆØ\ÙHPŽˆ˜[YHHQÈœ™XZÎÈËÈÓÔVSÔPÒ×Ð•Q‘‘TˆOˆÓÔVSÔPÒ×Ð•Q‘‘T—Ð’S‘S‘ÂˆØ\ÙHPÎˆ˜[YHHQŽÈœ™XZÎÈËÈÓÔVSÕS”PÒ×Ð•Q‘‘TˆOˆÓÔVSÕS”PÒ×Ð•Q‘‘T—Ð’S‘S‘ÂˆØ\ÙHÎNˆ˜[YHHÎŽÈœ™XZÎÈËÈÓÕS”Ñ“Ô“WÑ‘QQPÒ×Ð•Q‘‘TˆOˆÓÕS”Ñ“Ô“WÑ‘QQPÒ×Ð•Q‘‘T—Ð’S‘S™ÂˆØ\ÙHLQNˆ˜[YHHLQNÈœ™XZÎÈËÈÓÑTÔUÒÒS‘T‘PÕÐ•Q‘‘TˆOˆÓÑTÔUÒÒS‘T‘PÕÐ•Q‘‘T—Ð’S‘S™ÂˆØ\ÙHŒÎNˆ˜[YHHÎÈœ™XZÎÈËÈÓÑU×ÒS‘T‘PÕÐ•Q‘‘TˆOˆÓÑU×ÒS‘T‘PÕÐ•Q‘‘T—Ð’S‘S‘ÂˆY˜][ˆ˜[YHHMÈœ™XZÎÂˆB‚ˆ[˜[HÂˆ™X[ÙÛÙ][YÙ\Š˜[YK	˜[
NÂˆ™]\›ˆ
[œÚYÛ™Y[
H˜[ÂŸB‚œÝ]XÈ›ÚYÛÙ[”Ø[\\œ×Ù˜[˜XÚÊ[ÛÝ[[œÚYÛ™Y[
ˆØ[\\œÊHÂˆÝ]XÈ[œÚYÛ™Y[™^ÚYHNÂˆYˆ
\Ø[\\œÈÛÝ[H
H™]\›ŽÂˆ\YYˆ›ÚY

™ÛÙ[”Ø[\\œ×Ü›ŠJ[[œÚYÛ™Y[
ŠNÂˆÝ]XÈÛÙ[”Ø[\\œ×Ü›ˆ™X[Ù›ˆH•SÂˆYˆ
\™X[Ù›ŠHÂˆ™X[Ù›ˆH
ÛÙ[”Ø[\\œ×Ü›ŠHÞ[J•ÑQUS™ÛÙ[”Ø[\\œÈŠNÂˆYˆ
\™X[Ù›ŠH™X[Ù›ˆH
ÛÙ[”Ø[\\œ×Ü›ŠHÞ[J•ÑQUS™ÛÙ[”Ø[\\œÓÑTÈŠNÂˆBˆYˆ
™X[Ù›ŠHÂˆ™X[Ù›ŠÛÝ[Ø[\\œÊNÂˆ[˜[YHNÂˆ›Üˆ
[HHÈHÛÝ[ÈJÊÊHÈYˆ
Ø[\\œÖÚWHOH
HÈ˜[YHÈœ™XZÎÈHBˆYˆ
˜[Y
H™]\›ŽÂˆBˆ›Üˆ
[HHÈHÛÝ[ÈJÊÊHØ[\\œÖÚWHH™^ÚY
ÊÎÂˆÑÒJ“Ò‘Ó[šÙ\šÛÚÎˆÛÙ[”Ø[\\œÈ˜[˜XÚÈÙ[™\˜]Y	YØ[\\ŠÊH‹ÛÝ[
NÂŸB‚œÝ]XÈ›ÚYÛš[™Ø[\\—Ù˜[˜XÚÊ[œÚYÛ™Y[[š][œÚYÛ™Y[Ø[\\ŠHÂˆ\YYˆ›ÚY

™Ûš[™Ø[\\—Ü›ŠJ[œÚYÛ™Y[[œÚYÛ™Y[
NÂˆÝ]XÈÛš[™Ø[\\—Ü›ˆ™X[Ù›ˆH•SÂˆYˆ
\™X[Ù›ŠHÂˆ™X[Ù›ˆH
Ûš[™Ø[\\—Ü›ŠHÞ[J•ÑQUS™Ûš[™Ø[\\ˆŠNÂˆYˆ
\™X[Ù›ŠH™X[Ù›ˆH
Ûš[™Ø[\\—Ü›ŠHÞ[J•ÑQUS™Ûš[™Ø[\\“ÑTÈŠNÂˆBˆYˆ
™X[Ù›ŠH™X[Ù›Š[š]Ø[\\ŠNÂŸB‚œÝ]XÈ›ÚYÛ[]TØ[\\œ×Ù˜[˜XÚÊ[ÛÝ[ÛÛœÝ[œÚYÛ™Y[
ˆØ[\\œÊHÂˆYˆ
\Ø[\\œÈÛÝ[H
H™]\›ŽÂˆ\YYˆ›ÚY

™Û[]TØ[\\œ×Ü›ŠJ[ÛÛœÝ[œÚYÛ™Y[
ŠNÂˆÝ]XÈÛ[]TØ[\\œ×Ü›ˆ™X[Ù›ˆH•SÂˆYˆ
\™X[Ù›ŠHÂˆ™X[Ù›ˆH
Û[]TØ[\\œ×Ü›ŠHÞ[J•ÑQUS™Û[]TØ[\\œÈŠNÂˆYˆ
\™X[Ù›ŠH™X[Ù›ˆH
Û[]TØ[\\œ×Ü›ŠHÞ[J•ÑQUS™Û[]TØ[\\œÓÑTÈŠNÂˆBˆYˆ
™X[Ù›ŠH™X[Ù›ŠÛÝ[Ø[\\œÊNÂŸB‚œÝ]XÈ›ÚYÛØ[\\”\˜[Y]\šWÙ˜[˜XÚÊ[œÚYÛ™Y[Ø[\\‹[œÚYÛ™Y[˜[YK[\˜[JHÂˆ\YYˆ›ÚY

™ÛØ[\\”\˜[Y]\šWÜ›ŠJ[œÚYÛ™Y[[œÚYÛ™Y[[
NÂˆÝ]XÈÛØ[\\”\˜[Y]\šWÜ›ˆ™X[Ù›ˆH•SÂˆYˆ
\™X[Ù›ŠHÂˆ™X[Ù›ˆH
ÛØ[\\”\˜[Y]\šWÜ›ŠHÞ[J•ÑQUS™ÛØ[\\”\˜[Y]\šHŠNÂˆYˆ
\™X[Ù›ŠH™X[Ù›ˆH
ÛØ[\\”\˜[Y]\šWÜ›ŠHÞ[J•ÑQUS™ÛØ[\\”\˜[Y]\šSÑTÈŠNÂˆBˆYˆ
™X[Ù›ŠH™X[Ù›ŠØ[\\‹˜[YK\˜[JNÂŸB‚œÝ]XÈ›ÚY
ˆÛX\Y™™\”˜[™ÙWÚÛÚÊ[œÚYÛ™Y[\™Ù]Û™ÈÙ™œÙ]Û™È[™Ý[œÚYÛ™Y[XØÙ\ÜÊHÂˆÝ]XÈ[Ø[ÛÝ[HÂˆYˆ
Ø[ÛÝ[JHÂˆÑÒJ“Ò‘Ó[šÙ\šÛÚÎˆÛX\Y™™\”˜[™ÙWÚÛÚÈÐSQ\™Ù]L	VÙ™œÙ]I[[IYXØÙ\ÜÏL	V‹\™Ù]Ù™œÙ][™ÝXØÙ\ÜÊNÂˆØ[ÛÝ[
ÊÎÂˆB‚ˆ\YYˆ›ÚY

™ÛÙ]Y™™\”\˜[Y]\š]—Ü›ŠJ[œÚYÛ™Y[[œÚYÛ™Y[[
ŠNÂˆÝ]XÈÛÙ]Y™™\”\˜[Y]\š]—Ü›ˆ™X[ÙÛÙ]Y™™\”\˜[Y]\š]ˆH•SÂˆYˆ
\™X[ÙÛÙ]Y™™\”\˜[Y]\š]ŠHÂˆ™X[ÙÛÙ]Y™™\”\˜[Y]\š]ˆH
ÛÙ]Y™™\”\˜[Y]\š]—Ü›ŠHÞ[J•ÑQUS™ÛÙ]Y™™\”\˜[Y]\š]ˆŠNÂˆYˆ
\™X[ÙÛÙ]Y™™\”\˜[Y]\š]ŠH™X[ÙÛÙ]Y™™\”\˜[Y]\š]ˆH
ÛÙ]Y™™\”\˜[Y]\š]—Ü›ŠHÞ[J•ÑQUS™ÛÙ]Y™™\”\˜[Y]\š]TˆŠNÂˆBˆ[Y—ÜÚ^™HHÂˆYˆ
™X[ÙÛÙ]Y™™\”\˜[Y]\š]ŠHÂˆ™X[ÙÛÙ]Y™™\”\˜[Y]\š]Š\™Ù]ÍÊˆÓÐ•Q‘‘T—ÔÒV‘H
‹Ë	˜Y—ÜÚ^™JNÂˆB‚ˆÛ™È[Ø×Û[ˆH[™ÝÂˆYˆ
[Ø×Û[ˆH	‰ˆY—ÜÚ^™Hˆ
H[Ø×Û[ˆHY—ÜÚ^™HHÙ™œÙ]ÂˆYˆ
[Ø×Û[ˆH
H[Ø×Û[ˆHLMÍŽÈËÈHPˆ˜[˜XÚÂˆYˆ
Y—ÜÚ^™Hˆ	‰ˆ
Ù™œÙ]
È[Ø×Û[ŠHY—ÜÚ^™JHÂˆ[Ø×Û[ˆHY—ÜÚ^™NÂˆB‚ˆ[œÚYÛ™Y[Y™™\—ÚYHÙ]Ø›Ý[™ØY™™\—ÚY
\™Ù]
NÂˆ›ÚY
ˆˆH•SÂ‚ˆYˆ
ÜÚ^ÛY[X[YÛŠ	œ‹[Ø×Û[ŠHOHˆOH•S
HÂˆˆHX[ØÊ[Ø×Û[ŠNÂˆBˆYˆ
\ŠHˆHØ[ØÊK[Ø×Û[ŠNÂ‚ˆYˆ
\ŠHÂˆÑÑJ“Ò‘Ó[šÙ\šÛÚÎˆ[Y\™Ù[˜ÞH˜[˜XÚÈY™™\ˆ\ÙY›Üˆ[Ø×Û[I[‹[Ø×Û[ŠNÂˆˆH×Ù˜[˜XÚ×ØY™™\ŽÂˆB‚ˆ™XYÛ]]^ÛØÚÊ	™×ÜÚYÝÓ]]^
NÂˆ[ÛÝHš[™Ùœ™YWÜÚYÝ×ÜÛÝ

NÂˆYˆ
ÛÝH
HÂˆ×ÜÚYÝÐY™™\œÖÜÛÝK\™Ù]H\™Ù]Âˆ×ÜÚYÝÐY™™\œÖÜÛÝK˜Y™™\—ÚYHY™™\—ÚYÂˆ×ÜÚYÝÐY™™\œÖÜÛÝK›Ù™œÙ]HÙ™œÙ]Âˆ×ÜÚYÝÐY™™\œÖÜÛÝK›[™ÝH[Ø×Û[ŽÂˆ×ÜÚYÝÐY™™\œÖÜÛÝKœÚYÝ×ÜˆHŽÂˆ×ÜÚYÝÐY™™\œÖÜÛÝKš\×ÜÚYÝÈHNÂˆ×ÜÚYÝÐY™™\œÖÜÛÝKš[—Ý\ÙHHNÂˆH[ÙHÂˆÑÕÊ“Ò‘Ó[šÙ\šÛÚÎˆÚYÝÈÛÝÈ[™]\›š[™È[›X[˜YÙYY™™\ˆŠNÂˆBˆ™XYÛ]]^Ý[›ØÚÊ	™×ÜÚYÝÓ]]^
NÂ‚ˆ\YYˆ[œÚYÛ™Y[

™ÛÙ]\œ›Ü—Ü›ŠJ›ÚY
NÂˆÝ]XÈÛÙ]\œ›Ü—Ü›ˆ™X[ÙÛÙ]\œ›ÜˆH•SÂˆYˆ
\™X[ÙÛÙ]\œ›ÜŠHÂˆ™X[ÙÛÙ]\œ›ÜˆH
ÛÙ]\œ›Ü—Ü›ŠHÞ[J•ÑQUS™ÛÙ]\œ›ÜˆŠNÂˆYˆ
\™X[ÙÛÙ]\œ›ÜŠH™X[ÙÛÙ]\œ›ÜˆH
ÛÙ]\œ›Ü—Ü›ŠHÞ[J•Ó‘V™ÛÙ]\œ›ÜˆŠNÂˆBˆYˆ
™X[ÙÛÙ]\œ›ÜŠHÈ[œÚYÛ™Y[\œŽÈÈÈ\œˆH™X[ÙÛÙ]\œ›ÜŠ
NÈHÚ[H
\œˆOH
NÈBˆ™]\›ˆŽÂŸB‚œÝ]XÈ›ÚY
ˆÛX\Y™™\—ÚÛÚÊ[œÚYÛ™Y[\™Ù][œÚYÛ™Y[XØÙ\ÜÊHÂˆ\YYˆ›ÚY

™ÛÙ]Y™™\”\˜[Y]\š]—Ü›ŠJ[œÚYÛ™Y[[œÚYÛ™Y[[
ŠNÂˆÝ]XÈÛÙ]Y™™\”\˜[Y]\š]—Ü›ˆ™X[ÙÛÙ]Y™™\”\˜[Y]\š]ˆH•SÂˆYˆ
\™X[ÙÛÙ]Y™™\”\˜[Y]\š]ŠHÂˆ™X[ÙÛÙ]Y™™\”\˜[Y]\š]ˆH
ÛÙ]Y™™\”\˜[Y]\š]—Ü›ŠHÞ[J•ÑQUS™ÛÙ]Y™™\”\˜[Y]\š]ˆŠNÂˆYˆ
\™X[ÙÛÙ]Y™™\”\˜[Y]\š]ŠH™X[ÙÛÙ]Y™™\”\˜[Y]\š]ˆH
ÛÙ]Y™™\”\˜[Y]\š]—Ü›ŠHÞ[J•ÑQUS™ÛÙ]Y™™\”\˜[Y]\š]TˆŠNÂˆBˆ[Y—ÜÚ^™HHÂˆYˆ
™X[ÙÛÙ]Y™™\”\˜[Y]\š]ŠH™X[ÙÛÙ]Y™™\”\˜[Y]\š]Š\™Ù]Í	˜Y—ÜÚ^™JNÂˆÛ™È[ˆH
Y—ÜÚ^™Hˆ
HÈY—ÜÚ^™HˆMLÍŽÂˆ[œÚYÛ™Y[˜[™ÙPXØÙ\ÜÈHŽÂˆYˆ
XØÙ\ÜÈOHŽ
H˜[™ÙPXØÙ\ÜÈHNÂˆ[ÙHYˆ
XØÙ\ÜÈOHJH˜[™ÙPXØÙ\ÜÈHHŽÂˆ™]\›ˆÛX\Y™™\”˜[™ÙWÚÛÚÊ\™Ù][‹˜[™ÙPXØÙ\ÜÊNÂŸB‚œÝ]XÈ[Û[›X\Y™™\—ÚÛÚÊ[œÚYÛ™Y[\™Ù]
HÂˆ\YYˆ›ÚY

™ÛY™™\”ÝX‘]WÜ›ŠJ[œÚYÛ™Y[Û™ËÛ™ËÛÛœÝ›ÚY
ŠNÂˆ\YYˆ›ÚY

™Ûš[™Y™™\—Ü›ŠJ[œÚYÛ™Y[[œÚYÛ™Y[
NÂˆ\YYˆ[œÚYÛ™Y[

™ÛÙ]\œ›Ü—Ü›ŠJ›ÚY
NÂ‚ˆÝ]XÈÛY™™\”ÝX‘]WÜ›ˆ™X[ÙÛY™™\”ÝX‘]HH•SÂˆÝ]XÈÛš[™Y™™\—Ü›ˆ™X[ÙÛš[™Y™™\ˆH•SÂˆÝ]XÈÛÙ]\œ›Ü—Ü›ˆ™X[ÙÛÙ]\œ›ÜˆH•SÂ‚ˆYˆ
\™X[ÙÛY™™\”ÝX‘]JHÂˆ™X[ÙÛY™™\”ÝX‘]HH
ÛY™™\”ÝX‘]WÜ›ŠHÞ[J•ÑQUS™ÛY™™\”ÝX‘]HŠNÂˆYˆ
\™X[ÙÛY™™\”ÝX‘]JH™X[ÙÛY™™\”ÝX‘]HH
ÛY™™\”ÝX‘]WÜ›ŠHÞ[J•ÑQUS™ÛY™™\”ÝX‘]PTˆŠNÂˆBˆYˆ
\™X[ÙÛš[™Y™™\ŠHÂˆ™X[ÙÛš[™Y™™\ˆH
Ûš[™Y™™\—Ü›ŠHÞ[J•ÑQUS™Ûš[™Y™™\ˆŠNÂˆBˆYˆ
\™X[ÙÛÙ]\œ›ÜŠHÂˆ™X[ÙÛÙ]\œ›ÜˆH
ÛÙ]\œ›Ü—Ü›ŠHÞ[J•ÑQUS™ÛÙ]\œ›ÜˆŠNÂˆYˆ
\™X[ÙÛÙ]\œ›ÜŠH™X[ÙÛÙ]\œ›ÜˆH
ÛÙ]\œ›Ü—Ü›ŠHÞ[J•Ó‘V™ÛÙ]\œ›ÜˆŠNÂˆB‚ˆ[œÚYÛ™Y[Ý\œ™[ØY™™\—ÚYHÙ]Ø›Ý[™ØY™™\—ÚY
\™Ù]
NÂˆ[›Ý[™ÜÛÝHLNÂ‚ˆ™XYÛ]]^ÛØÚÊ	™×ÜÚYÝÓ]]^
NÂˆ›Üˆ
[HHÈH×ÜÚYÝÐÛÝ[ÈJÊÊHÂˆYˆ
×ÜÚYÝÐY™™\œÖÚWKš[—Ý\ÙH	‰ˆ×ÜÚYÝÐY™™\œÖÚWKš\×ÜÚYÝÈ	‰‚ˆ×ÜÚYÝÐY™™\œÖÚWK\™Ù]OH\™Ù]	‰‚ˆ
Ý\œ™[ØY™™\—ÚYOH×ÜÚYÝÐY™™\œÖÚWK˜Y™™\—ÚYOHÝ\œ™[ØY™™\—ÚY
JHÂˆ›Ý[™ÜÛÝHNÈœ™XZÎÂˆBˆB‚ˆËÈ˜[˜XÚÈÙX\˜ÚYˆY™™\ˆQZ\ÛX]ÚˆYˆ
›Ý[™ÜÛÝ
HÂˆ›Üˆ
[HHÈH×ÜÚYÝÐÛÝ[ÈJÊÊHÂˆYˆ
×ÜÚYÝÐY™™\œÖÚWKš[—Ý\ÙH	‰ˆ×ÜÚYÝÐY™™\œÖÚWKš\×ÜÚYÝÈ	‰ˆ×ÜÚYÝÐY™™\œÖÚWK\™Ù]OH\™Ù]
HÂˆ›Ý[™ÜÛÝHNÈœ™XZÎÂˆBˆBˆB‚ˆYˆ
›Ý[™ÜÛÝH
HÂˆÚYÝÐY™™\“X\[žHH×ÜÚYÝÐY™™\œÖÙ›Ý[™ÜÛÝNÂˆ×ÜÚYÝÐY™™\œÖÙ›Ý[™ÜÛÝKš[—Ý\ÙHHÂˆ×ÜÚYÝÐY™™\œÖÙ›Ý[™ÜÛÝKš\×ÜÚYÝÈHÂˆ×ÜÚYÝÐY™™\œÖÙ›Ý[™ÜÛÝKœÚYÝ×ÜˆH•SÂˆ™XYÛ]]^Ý[›ØÚÊ	™×ÜÚYÝÓ]]^
NÂ‚ˆYˆ
[žKœÚYÝ×ÜŠHÂˆËÈÔ’UPÐS’Vˆš[™HY™™\ˆ‘Q“Ô‘HØ[[™ÈÛY™™\”ÝX‘]BˆYˆ
™X[ÙÛš[™Y™™\ˆ	‰ˆ[žK˜Y™™\—ÚYOH
HÂˆ™X[ÙÛš[™Y™™\Š\™Ù][žK˜Y™™\—ÚY
NÂˆBˆYˆ
™X[ÙÛY™™\”ÝX‘]JHÂˆ™X[ÙÛY™™\”ÝX‘]J\™Ù][žK›Ù™œÙ][žK›[™Ý[žKœÚYÝ×ÜŠNÂˆBˆËÈœ™YHY[[ÜžHYˆ›Ý\Ú[™ÈHÝ]XÈ˜[˜XÚÈY™™\‚ˆYˆ

Ú\ŠŠY[žKœÚYÝ×Üˆ×Ù˜[˜XÚ×ØY™™\ˆ
Ú\ŠŠY[žKœÚYÝ×ÜˆH
×Ù˜[˜XÚ×ØY™™\ˆ
ÈÚ^™[ÙŠ×Ù˜[˜XÚ×ØY™™\ŠJJHÂˆœ™YJ[žKœÚYÝ×ÜŠNÂˆBˆBˆYˆ
™X[ÙÛÙ]\œ›ÜŠHÈ[œÚYÛ™Y[\œŽÈÈÈ\œˆH™X[ÙÛÙ]\œ›ÜŠ
NÈHÚ[H
\œˆOH
NÈBˆ™]\›ˆNÈËÈÝXØÙ\ÜÂˆBˆ™XYÛ]]^Ý[›ØÚÊ	™×ÜÚYÝÓ]]^
NÂ‚ˆËÈ˜[˜XÚÈÈ™X[Û[›X\Y™™\ˆYˆ›Ý[ˆÚYÝÈX\ˆ\YYˆ[

™Û[›X\Y™™\—Ü›ŠJ[œÚYÛ™Y[
NÂˆÝ]XÈÛ[›X\Y™™\—Ü›ˆ™X[ÙÛ[›X\Y™™\ˆH•SÂˆYˆ
\™X[ÙÛ[›X\Y™™\ŠHÂˆ™X[ÙÛ[›X\Y™™\ˆH
Û[›X\Y™™\—Ü›ŠHÞ[J•ÑQUS™Û[›X\Y™™\ˆŠNÂˆYˆ
\™X[ÙÛ[›X\Y™™\ŠH™X[ÙÛ[›X\Y™™\ˆH
Û[›X\Y™™\—Ü›ŠHÞ[J•ÑQUS™Û[›X\Y™™\“ÑTÈŠNÂˆBˆ[™\ÈHNÂˆYˆ
™X[ÙÛ[›X\Y™™\ŠH™\ÈH™X[ÙÛ[›X\Y™™\Š\™Ù]
NÂˆYˆ
™X[ÙÛÙ]\œ›ÜŠHÈ[œÚYÛ™Y[\œŽÈÈÈ\œˆH™X[ÙÛÙ]\œ›ÜŠ
NÈHÚ[H
\œˆOH
NÈBˆ™]\›ˆ™\ÈÈ™\ÈˆNÂŸB‚œÝ]XÈ›ÚYÛY[[ÜžP˜\œšY\—ÜÝXŠ[œÚYÛ™Y[˜\œšY\œÊHÂˆ\YYˆ›ÚY

™Û›\ÚÜ›ŠJ
NÂˆÝ]XÈÛ›\ÚÜ›ˆ™X[ÙÛ›\ÚH•SÂˆYˆ
\™X[ÙÛ›\Ú
HÂˆ™X[ÙÛ›\ÚH
Û›\ÚÜ›ŠHÞ[J•ÑQUS™Û›\ÚŠNÂˆYˆ
\™X[ÙÛ›\Ú
H™X[ÙÛ›\ÚH
Û›\ÚÜ›ŠHÞ[J•Ó‘V™Û›\ÚŠNÂˆBˆYˆ
™X[ÙÛ›\Ú
H™X[ÙÛ›\Ú

NÂˆÑÒJ™ÛY[[ÜžP˜\œšY\ˆÝXˆØ[Y[™›\ÚYÝXØÙ\ÜÙ[H
˜\œšY\œÎˆ	]JH‹˜\œšY\œÊNÂŸB‚œÝ]XÈ[œÚYÛ™Y[YÛÙ]\œ›Ü—ÜÝXŠ›ÚY
HÂˆ™]\›ˆÌÈËÈQÓÔÕPÐÑTÔÂŸB‚›ÚY
ˆÛÚÙYÙÛÐÜ™X]UÚ[™ÝÊ[ÚY[ZYÚÛÛœÝÚ\Šˆ]K›ÚY
ˆ[Ûš]Ü‹›ÚY
ˆÚ\™JHÂˆš[Š•\˜›ÕŒH[\˜Ù\ÜŽˆ^XÝ][™ÈÛÚÙYÙÛÐÜ™X]UÚ[™ÝÈÚ]QÓÜ[‘ÓTÈÛÛ^›Üˆš[š×ˆŠNÂˆ™›\Ú
ÝÝ]
NÂˆ\YYˆ›ÚY

™ÛÕÚ[™ÝÒ[Ü›ŠJ[[
NÂˆ\YYˆ›ÚY
ˆ

™ÛÐÜ™X]UÚ[™Ý×Ü›ŠJ[[ÛÛœÝÚ\Š‹›ÚY
‹›ÚY
ŠNÂ‚ˆÝ]XÈÛÕÚ[™ÝÒ[Ü›ˆ™X[ÝÚ[—Ú[H•SÂˆÝ]XÈÛÐÜ™X]UÚ[™Ý×Ü›ˆ™X[ØÜ™X]WÝÚ[ˆH•SÂ‚ˆYˆ
\™X[ÝÚ[—Ú[
HÂˆ™X[ÝÚ[—Ú[H
ÛÕÚ[™ÝÒ[Ü›ŠHÞ[J•ÑQUS™ÛÕÚ[™ÝÒ[ŠNÂˆBˆYˆ
\™X[ØÜ™X]WÝÚ[ŠHÂˆ™X[ØÜ™X]WÝÚ[ˆH
ÛÐÜ™X]UÚ[™Ý×Ü›ŠHÞ[J•ÑQUS™ÛÐÜ™X]UÚ[™ÝÈŠNÂˆB‚ˆYˆ
™X[ÝÚ[—Ú[
HÂˆ™X[ÝÚ[—Ú[
ŒŒHÊˆÓ•×ÐÓQS•ÐTH
‹ËÌHÊˆÓ•×ÓÔS‘ÓÑT×ÐTH
‹ÊNÂˆ™X[ÝÚ[—Ú[
ŒŒˆÊˆÓ•×ÐÓÓ•VÐÔ‘PUSÓ—ÐTH
‹ËÍŒˆÊˆÓ•×ÑQÓÐÓÓ•VÐTH
‹ÊNÂˆB‚ˆYˆ
™X[ØÜ™X]WÝÚ[ŠHÂˆ™]\›ˆ™X[ØÜ™X]WÝÚ[ŠÚYZYÚ]K[Ûš]Ü‹Ú\™JNÂˆBˆ™]\›ˆ•SÂŸB‚œÝ]XÈ[YÛÝØ\[\˜[ÚÛÚÊ›ÚY
ˆ\Ü^K×Ø]šX]W×Ê
[\ÙY
JH[[\˜[
HÂˆ\YYˆ[

™YÛÝØ\[\˜[Ü›ŠJ›ÚY
‹[
NÂˆÝ]XÈYÛÝØ\[\˜[Ü›ˆ™X[Ù›ˆH•SÂˆYˆ
\™X[Ù›ŠHÂˆ™X[Ù›ˆH
YÛÝØ\[\˜[Ü›ŠHÞ[J•ÑQUS™YÛÝØ\[\˜[ŠNÂˆYˆ
\™X[Ù›ŠH™X[Ù›ˆH
[ÝØ\[\˜[Ü›ŠHÞ[J•Ó‘V™YÛÝØ\[\˜[ŠNÂˆBˆYˆ
™X[Ù›ŠH™]\›ˆ™X[Ù›Š\Ü^K
NÈËÈ[Ø^\È›Ü˜ÙHÝØ\[\˜[
[›ØÚÜÈ”È\ÝŒ–Žˆ\Ü^HØÚÈJBˆ™]\›ˆNÂŸB‚œÝ]XÈÛÛœÝ[œÚYÛ™YÚ\ŠˆÛÙ]Ýš[™×ÚÛÚÊ[œÚYÛ™Y[˜[YJHÂˆYˆ
˜[YHOHÓÕ‘T”ÒSÓŠH™]\›ˆ
ÛÛœÝ[œÚYÛ™YÚ\ŠŠH‹Œ\˜›ÕŒH[™Ú[™HŒKŒ
[Ø[ˆÛÜ™JHŽÂˆ[ÙHYˆ
˜[YHOHÓÔ‘S‘T‘TŠH™]\›ˆ
ÛÛœÝ[œÚYÛ™YÚ\ŠŠH“X[KQÍÌLÑÍŒMHšXH\˜›ÕŒH˜[œÛ][ÛˆŽÂˆ[ÙHYˆ
˜[YHOHÓÕ‘S‘ÔŠH™]\›ˆ
ÛÛœÝ[œÚYÛ™YÚ\ŠŠH•\˜›ÕŒH[™Ú[™HŒKŒ
[Ø[ˆÛÜ™JHŽÂˆ[ÙHYˆ
˜[YHOHÓÑVS”ÒSÓ”ÊH™]\›ˆ
ÛÛœÝ[œÚYÛ™YÚ\ŠŠH‹Œ\˜›ÕŒH[™Ú[™HŒKŒ
[Ø[ˆÛÜ™JHH[HÛÛ\]X›HŽÂˆ\YYˆÛÛœÝ[œÚYÛ™YÚ\Šˆ

™ÛÙ]Ýš[™×Ü›ŠJ[œÚYÛ™Y[
NÂˆÝ]XÈÛÙ]Ýš[™×Ü›ˆ™X[ÙÛÙ]Ýš[™ÈH•SÂˆYˆ
\™X[ÙÛÙ]Ýš[™ÊHÂˆ™X[ÙÛÙ]Ýš[™ÈH
ÛÙ]Ýš[™×Ü›ŠHÞ[J•ÑQUS™ÛÙ]Ýš[™ÈŠNÂˆYˆ
\™X[ÙÛÙ]Ýš[™ÊH™X[ÙÛÙ]Ýš[™ÈH
ÛÙ]Ýš[™×Ü›ŠHÞ[J•Ó‘V™ÛÙ]Ýš[™ÈŠNÂˆBˆYˆ
™X[ÙÛÙ]Ýš[™ÊH™]\›ˆ™X[ÙÛÙ]Ýš[™Ê˜[YJNÂˆ™]\›ˆ
ÛÛœÝ[œÚYÛ™YÚ\ŠŠHˆŽÂŸB‚œÝ]XÈÛÛœÝ[œÚYÛ™YÚ\ŠˆÛÙ]Ýš[™ÚWÚÛÚÊ[œÚYÛ™Y[˜[YK[œÚYÛ™Y[[™^
HÂˆYˆ
˜[YHOHÓÑVS”ÒSÓ”ÊHÂˆÝ]XÈÛÛœÝÚ\Šˆ^[œÚ[ÛœÖ×HHÂˆ‘ÓÐT—Ù\™XÝÜÝ]WØXØÙ\ÜÈ‹‘ÓÐT—ØY™™\—ÜÝÜ˜YÙH‹‘ÓÐT—ÜÚY\—Ú[XYÙWÛØYÜÝÜ™H‹ˆ‘ÓÓ•—ØÛÛ™][Û˜[Ü™[™\ˆ‹‘ÓÑVÙÜWÜÚY\‹‘ÓÑVÝ^\™WØY™™\ˆ‹ˆ‘ÓÑVÝ^\™WØÝX™WÛX\Ø\œ˜^H‹‘ÓÓÑT×ÑQÓÚ[XYÙWÙ^\›˜[Ù\ÜÛÈ‹ˆ‘ÓÓ•—ÜÚY\—Û›Ü\œÜXÝ]™WÚ[\œÛ][Ûˆ‹‘ÓÐT—ÜÚY\—ÛØš™XÝÈ‹ˆ‘ÓÐT—Ý™\^ÜÚY\ˆ‹‘ÓÐT—Ùœ˜YÛY[ÜÚY\ˆ‹‘ÓÑVØ›[™Ù\]X][Û—ÜÙ\\˜]H‹ˆ‘ÓÑVÙÙ[ÛY]žWÜÚY\‹‘ÓÑVÙÜWÜ›ÙÜ˜[WÜ\˜[Y]\œÈ‹ˆ‘ÓÐT—Ú[œÝ[˜ÙYØ\œ˜^\È‹‘ÓÐT—Ù˜]×Ú[œÝ[˜ÙY‚ˆNÂˆ[œÚYÛ™Y[Ú^™HHÚ^™[ÙŠ^[œÚ[ÛœÊHÈÚ^™[ÙŠ^[œÚ[ÛœÖÌJNÂˆYˆ
[™^Ú^™JH™]\›ˆ
ÛÛœÝ[œÚYÛ™YÚ\ŠŠY^[œÚ[ÛœÖÚ[™^NÂˆBˆ\YYˆÛÛœÝ[œÚYÛ™YÚ\Šˆ

™ÛÙ]Ýš[™ÚWÜ›ŠJ[œÚYÛ™Y[[œÚYÛ™Y[
NÂˆÝ]XÈÛÙ]Ýš[™ÚWÜ›ˆ™X[ÙÛÙ]Ýš[™ÚHH•SÂˆYˆ
\™X[ÙÛÙ]Ýš[™ÚJHÂˆ™X[ÙÛÙ]Ýš[™ÚHH
ÛÙ]Ýš[™ÚWÜ›ŠHÞ[J•ÑQUS™ÛÙ]Ýš[™ÚHŠNÂˆYˆ
\™X[ÙÛÙ]Ýš[™ÚJH™X[ÙÛÙ]Ýš[™ÚHH
ÛÙ]Ýš[™ÚWÜ›ŠHÞ[J•Ó‘V™ÛÙ]Ýš[™ÚHŠNÂˆBˆYˆ
™X[ÙÛÙ]Ýš[™ÚJH™]\›ˆ™X[ÙÛÙ]Ýš[™ÚJ˜[YK[™^
NÂˆ™]\›ˆ
ÛÛœÝ[œÚYÛ™YÚ\ŠŠHˆŽÂŸB‚›ÚY
ˆYÛÙ]›ØÐY™\Ü×ÚÛÚÊÛÛœÝÚ\Šˆ›ØÛ˜[YJHÂˆYˆ
›ØÛ˜[YHOH•S
H™]\›ˆ•SÂˆYˆ
Ý˜Û\
›ØÛ˜[YK™YÛÝØ\[\˜[ŠHOH
H™]\›ˆ
›ÚY
ŠHYÛÝØ\[\˜[ÚÛÚÎÂˆYˆ
Ý˜Û\
›ØÛ˜[YK™ÛY[[ÜžP˜\œšY\ˆŠHOHÝ˜Û\
›ØÛ˜[YK™ÛY[[ÜžP˜\œšY\‘VŠHOH
H™]\›ˆ
›ÚY
ŠHÛY[[ÜžP˜\œšY\—ÜÝXŽÂˆYˆ
Ý˜Û\
›ØÛ˜[YK™ÛÙ]Ýš[™ÈŠHOH
H™]\›ˆ
›ÚY
ŠHÛÙ]Ýš[™×ÚÛÚÎÂˆYˆ
Ý˜Û\
›ØÛ˜[YK™ÛÙ]Ýš[™ÚHŠHOH
H™]\›ˆ
›ÚY
ŠHÛÙ]Ýš[™ÚWÚÛÚÎÂˆYˆ
Ý˜Û\
›ØÛ˜[YK™ÛX\Y™™\”˜[™ÙHŠHOHÝ˜Û\
›ØÛ˜[YK™ÛX\Y™™\”˜[™ÙQVŠHOHÝ˜Û\
›ØÛ˜[YK™ÛX\Y™™\”˜[™ÙPTˆŠHOH
HÂˆÑÒJ™YÛÙ]›ØÐY™\Ü×ÚÛÚÎˆÛX\Y™™\”˜[™ÙHOˆÚYÝÈY™™\ˆŠNÂˆ™]\›ˆ
›ÚY
ŠHÛX\Y™™\”˜[™ÙWÚÛÚÎÂˆBˆYˆ
Ý˜Û\
›ØÛ˜[YK™ÛX\Y™™\ˆŠHOHÝ˜Û\
›ØÛ˜[YK™ÛX\Y™™\“ÑTÈŠHOHÝ˜Û\
›ØÛ˜[YK™ÛX\Y™™\TˆŠHOH
H™]\›ˆ
›ÚY
ŠHÛX\Y™™\—ÚÛÚÎÂˆYˆ
Ý˜Û\
›ØÛ˜[YK™Û[›X\Y™™\ˆŠHOHÝ˜Û\
›ØÛ˜[YK™Û[›X\Y™™\“ÑTÈŠHOHÝ˜Û\
›ØÛ˜[YK™Û[›X\Y™™\TˆŠHOH
H™]\›ˆ
›ÚY
ŠHÛ[›X\Y™™\—ÚÛÚÎÂˆYˆ
Ý˜Û\
›ØÛ˜[YK™ÛÙ[”Ø[\\œÈŠHOHÝ˜Û\
›ØÛ˜[YK™ÛÙ[”Ø[\\œÓÑTÈŠHOH
HÂˆ\YYˆ›ÚY
ˆ

œ›ŠJÛÛœÝÚ\ŠŠNÈÝ]XÈ›ˆ™X[H•SÂˆYˆ
\™X[
H™X[H
›ŠHÞ[J•ÑQUS™YÛÙ]›ØÐY™\ÜÈŠNÂˆYˆ
™X[
HÈ›ÚY
ˆÈH™X[
›ØÛ˜[YJNÈYˆ
ÊH™]\›ˆÎÈBˆ›ÚY
ˆÈHÞ[J•ÑQUS›ØÛ˜[YJNÈYˆ
ÊH™]\›ˆÎÂˆ™]\›ˆ
›ÚY
ŠHÛÙ[”Ø[\\œ×Ù˜[˜XÚÎÂˆBˆYˆ
Ý˜Û\
›ØÛ˜[YK™Ûš[™Ø[\\ˆŠHOHÝ˜Û\
›ØÛ˜[YK™Ûš[™Ø[\\“ÑTÈŠHOH
HÂˆ\YYˆ›ÚY
ˆ

œ›ŠJÛÛœÝÚ\ŠŠNÈÝ]XÈ›ˆ™X[H•SÂˆYˆ
\™X[
H™X[H
›ŠHÞ[J•ÑQUS™YÛÙ]›ØÐY™\ÜÈŠNÂˆYˆ
™X[
HÈ›ÚY
ˆÈH™X[
›ØÛ˜[YJNÈYˆ
ÊH™]\›ˆÎÈBˆ›ÚY
ˆÈHÞ[J•ÑQUS›ØÛ˜[YJNÈYˆ
ÊH™]\›ˆÎÂˆ™]\›ˆ
›ÚY
ŠHÛš[™Ø[\\—Ù˜[˜XÚÎÂˆBˆYˆ
Ý˜Û\
›ØÛ˜[YK™Û[]TØ[\\œÈŠHOHÝ˜Û\
›ØÛ˜[YK™Û[]TØ[\\œÓÑTÈŠHOH
HÂˆ\YYˆ›ÚY
ˆ

œ›ŠJÛÛœÝÚ\ŠŠNÈÝ]XÈ›ˆ™X[H•SÂˆYˆ
\™X[
H™X[H
›ŠHÞ[J•ÑQUS™YÛÙ]›ØÐY™\ÜÈŠNÂˆYˆ
™X[
HÈ›ÚY
ˆÈH™X[
›ØÛ˜[YJNÈYˆ
ÊH™]\›ˆÎÈBˆ›ÚY
ˆÈHÞ[J•ÑQUS›ØÛ˜[YJNÈYˆ
ÊH™]\›ˆÎÂˆ™]\›ˆ
›ÚY
ŠHÛ[]TØ[\\œ×Ù˜[˜XÚÎÂˆBˆYˆ
Ý˜Û\
›ØÛ˜[YK™ÛØ[\\”\˜[Y]\šHŠHOHÝ˜Û\
›ØÛ˜[YK™ÛØ[\\”\˜[Y]\šSÑTÈŠHOH
HÂˆ\YYˆ›ÚY
ˆ

œ›ŠJÛÛœÝÚ\ŠŠNÈÝ]XÈ›ˆ™X[H•SÂˆYˆ
\™X[
H™X[H
›ŠHÞ[J•ÑQUS™YÛÙ]›ØÐY™\ÜÈŠNÂˆYˆ
™X[
HÈ›ÚY
ˆÈH™X[
›ØÛ˜[YJNÈYˆ
ÊH™]\›ˆÎÈBˆ›ÚY
ˆÈHÞ[J•ÑQUS›ØÛ˜[YJNÈYˆ
ÊH™]\›ˆÎÂˆ™]\›ˆ
›ÚY
ŠHÛØ[\\”\˜[Y]\šWÙ˜[˜XÚÎÂˆBˆYˆ
Ý˜Û\
›ØÛ˜[YK™ÛX\Y™™\”˜[™ÙHŠHOHÝ˜Û\
›ØÛ˜[YK™ÛX\Y™™\”˜[™ÙQVŠHOHÝ˜Û\
›ØÛ˜[YK™ÛX\Y™™\”˜[™ÙPTˆŠHOH
HÂˆš[Š“Ò‘Ó[šÙ\šÛÚÎˆYÛÙ]›ØÐY™\ÜÈÛÚÙYÛX\Y™™\”˜[™ÙHOˆÚYÝÈY™™\—ˆŠNÂˆ™]\›ˆ
›ÚY
ŠHÛX\Y™™\”˜[™ÙWÚÛÚÎÂˆBˆYˆ
Ý˜Û\
›ØÛ˜[YK™ÛX\Y™™\ˆŠHOHÝ˜Û\
›ØÛ˜[YK™ÛX\Y™™\“ÑTÈŠHOHÝ˜Û\
›ØÛ˜[YK™ÛX\Y™™\TˆŠHOH
HÂˆš[Š“Ò‘Ó[šÙ\šÛÚÎˆYÛÙ]›ØÐY™\ÜÈÛÚÙYÛX\Y™™\ˆOˆÚYÝÈY™™\—ˆŠNÂˆ™]\›ˆ
›ÚY
ŠHÛX\Y™™\—ÚÛÚÎÂˆBˆYˆ
Ý˜Û\
›ØÛ˜[YK™Û[›X\Y™™\ˆŠHOHÝ˜Û\
›ØÛ˜[YK™Û[›X\Y™™\“ÑTÈŠHOHÝ˜Û\
›ØÛ˜[YK™Û[›X\Y™™\TˆŠHOH
HÂˆš[Š“Ò‘Ó[šÙ\šÛÚÎˆYÛÙ]›ØÐY™\ÜÈÛÚÙYÛ[›X\Y™™\ˆOˆÚYÝÈY™™\—ˆŠNÂˆ™]\›ˆ
›ÚY
ŠHÛ[›X\Y™™\—ÚÛÚÎÂˆBˆYˆ
Ý˜Û\
›ØÛ˜[YK™ÛY[[ÜžP˜\œšY\ˆŠHOHÝ˜Û\
›ØÛ˜[YK™ÛY[[ÜžP˜\œšY\‘VŠHOH
HÂˆš[Š“Ò‘Ó[šÙ\šÛÚÎˆYÛÙ]›ØÐY™\ÜÈÛÚÙYÛY[[ÜžP˜\œšY\—ˆŠNÂˆ™]\›ˆ
›ÚY
ŠHÛY[[ÜžP˜\œšY\—ÜÝXŽÂˆBˆ\YYˆ›ÚY
ˆ

™YÛÙ]›ØÐY™\Ü×Ü›ŠJÛÛœÝÚ\ŠŠNÂˆÝ]XÈYÛÙ]›ØÐY™\Ü×Ü›ˆ™X[ÙYÛÙ]›ØÐY™\ÜÈH•SÂˆYˆ
\™X[ÙYÛÙ]›ØÐY™\ÜÊHÂˆ™X[ÙYÛÙ]›ØÐY™\ÜÈH
YÛÙ]›ØÐY™\Ü×Ü›ŠHÞ[J•ÑQUS™YÛÙ]›ØÐY™\ÜÈŠNÂˆYˆ
\™X[ÙYÛÙ]›ØÐY™\ÜÊH™X[ÙYÛÙ]›ØÐY™\ÜÈH
[Ù]›ØÐY™\Ü×Ü›ŠHÞ[J•Ó‘V™YÛÙ]›ØÐY™\ÜÈŠNÂˆBˆYˆ
™X[ÙYÛÙ]›ØÐY™\ÜÊHÈ›ÚY
ˆÞ[HH™X[ÙYÛÙ]›ØÐY™\ÜÊ›ØÛ˜[YJNÈYˆ
Þ[JH™]\›ˆÞ[NÈBˆ›ÚY
ˆÞ[HHÞ[J•ÑQUS›ØÛ˜[YJNÈYˆ
Þ[JH™]\›ˆÞ[NÂˆ™]\›ˆ
›ÚY
ŠH[š]™\œØ[ÜÝX—Ý›ÚYÂŸB‚œÝ]XÈ›Û™È™Ü[—ØYÙš^
×Ø]šX]W×Ê
[\ÙY
JH“’Q[ˆ
™[‹ˆ×Ø]šX]W×Ê
[\ÙY
JH˜Û\ÜÈÛ\ÜËˆ›Û™Èš[[˜[YWÜ‹š[›[ÙJHÂˆÛÛœÝÚ\Šˆš[[˜[YHH
ÛÛœÝÚ\ŠŠHš[[˜[YWÜŽÂˆYŠš[[˜[YHOH•S
HÂˆYŠÝ˜Û\
š[[˜[YK›X[Ø[‹œÛÈŠHOH
HÂˆš[Š“Ò‘Ó[šÙ\šÛÚÎˆ™\XÚ[™ÈØY›ÜˆX[Ø[‹œÛÈÚ]Ý\ÝÛHš]™\—ˆŠNÂˆ™]\›ˆ
›Û™ÊHÚ˜]™^X×ÛØY[Ø[‘š]™\Š
NÂˆBˆYŠÝ˜Û\
š[[˜[YK›X•\˜›ÕŒKœÛÈŠHOHÝ˜Û\
š[[˜[YK›X‘ÓœÛÈŠHOHÝ˜Û\
š[[˜[YK›X‘ÓœÛËŒHŠHOH
HÂˆš[Š“Ò‘Ó[šÙ\šÛÚÎˆ™\XÚ[™ÈÜ[‘ÓÚ]™[™\œÜXÈš]™\ˆ
	\ÊWˆ‹š[[˜[YJNÂˆÛÛœÝÚ˜]™^X×Ü™[™\œÜX×Ý
œœÜXÈHÚ˜]™^X×ÙÙ]™[™\”ÜXÊ
NÂˆYˆ
œÜXÈ	‰ˆœÜXËO™YÛØXÜ]Z\™H	‰ˆœÜXËO™YÛÜ]
HÂˆ™]\›ˆ
›Û™ÊHœÜXËO™YÛØXÜ]Z\™JœÜXËO™YÛÜ]
NÂˆBˆBˆBˆ™]\›ˆ
›Û™ÊHÜ[Šš[[˜[YK
[
Z›[ÙJNÂŸB‚œÝ]XÈ›Û™È™Þ[WÚÛÚÊ×Ø]šX]W×Ê
[\ÙY
JH“’Q[ˆ
™[‹ˆ×Ø]šX]W×Ê
[\ÙY
JH˜Û\ÜÈÛ\ÜËˆ›Û™È[™K›Û™ÈÞ[X›ÛÜŠHÂˆÛÛœÝÚ\ŠˆÞ[X›ÛH
ÛÛœÝÚ\ŠŠHÞ[X›ÛÜŽÂˆYˆ
Þ[X›ÛOH•S
HÂˆYˆ
Ý˜Û\
Þ[X›Û™YÛÙ]\œ›ÜˆŠHOH
HÂˆš[Š“Ò‘Ó[šÙ\šÛÚÎˆÛÚÙYYÛÙ]\œ›ÜˆOˆ™]\›š[™ÈQÓÔÕPÐÑTÔÈ
È3³) \n");
            return (jlong) eglGetError_stub;
        }
        if (strcmp(symbol, "eglGetProcAddress") == 0) {
            printf("LWJGL linkerhook: hooked eglGetProcAddress\n");
            return (jlong) eglGetProcAddress_hook;
        }
        if (strcmp(symbol, "glfwInit") == 0) {
            printf("LWJGL linkerhook: hooked glfwInit for TurboV1 Android Vulkan mode\n");
            typedef void (*glfwInitHint_pfn)(int, int);
            typedef int (*glfwInit_pfn)(void);

            glfwInitHint_pfn real_glfwInitHint = (glfwInitHint_pfn) dlsym((void*) handle, "glfwInitHint");
            if (!real_glfwInitHint) real_glfwInitHint = (glfwInitHint_pfn) dlsym(RTLD_DEFAULT, "glfwInitHint");
            if (real_glfwInitHint) {
                // Force Android native platform init hint (0x00050003 = GLFW_PLATFORM, 0x00060006 = GLVW_PLATFORM_ANDROID)
                real_glfwInitHint(0x00050003, 0x00060006);
            }

            glfwInit_pfn real_glfwInit = (glfwInit_pfn) dlsym((void*) handle, "glfwInit");
            if (!real_glfwInit) real_glfwInit = (glfwInit_pfn) dlsym(RTLD_DEFAULT, "glfwInit");
            if (real_glfwInit) real_glfwInit();

            typedef void (*glfwWindowHint_pfn)(int, int);
            glfwWindowHint_pfn real_glfwWindowHint = (glfwWindowHint_pfn) dlsym((void*) handle, "glfwWindowHint");
            if (!real_glfwWindowHint) real_glfwWindowHint = (glfwWindowHint_pfn) dlsym(RTLD_DEFAULT, "glfwWindowHint");
            if (real_glfwWindowHint) {
                real_glfwWindowHint(0x00022001 /* GLFW_CLIENT_API */, 0x00030001 /* GLFW_OPENGL_ES_API */);
                real_glfwWindowHint(0x0002200B /* GLFW_CONTEXT_CREATION_API */, 0x00036002 /* GLFW_EGL_CONTEXT_API */);
            }
            return (jlong) dlsym((void*) handle, "glfwInit");
        }
        if (strcmp(symbol, "glfwGetError") == 0) {
            printf("LWJGL linkerhook: hooked glfwGetError to suppress pre-init error bits\n");
            typedef int (*glfwGetError_pfn)(const char**);
            glfwGetError_pfn real_glfwGetError = (glfwGetError_pfn) dlsym((void*) handle, "glfwGetError");
            if (!real_glfwGetError) real_glfwGetError = (glfwGetError_pfn) dlsym(RTLD_DEFAULT, "glfwGetError");
            if (real_glfwGetError) {
                const char* description = NULL;
                int err = real_glfwGetError(&description);
                // Do not return 0 (NULL) - that gives LWJGL a null function pointer which crashes.
                // Instead falling through to return the real function pointer below.
            }
        }
        if (strcmp(symbol, "glfwCreateWindow") == 0) {
            printf("LWJGL linkerhook: returning hooked_glfwCreateWindow wrapper for Vulkan/Zink TurboV1 mode\n");
            extern void* hooked_glfwCreateWindow(int width, int height, const char* title, void* monitor, void* share);
            return (jlong) hooked_glfwCreateWindow;
        }
        if (strcmp(symbol, "eglSwapInterval") == 0) {
            printf("LWJGL linkerhook: hooked eglSwapInterval\n");
            return (jlong) eglSwapInterval_hook;
        }
        if (strcmp(symbol, "glGetString") == 0) {
            printf("LWJGL linkerhook: hooked glGetString\n");
            return (jlong) glGetString_hook;
        }
        if (strcmp(symbol, "glGetStringi") == 0) {
            printf("LWJGL linkerhook: hooked glGetStringi\n");
            return (jlong) glGetStringi_hook;
        }
        if (strcmp(symbol, "glMemoryBarrier") == 0 || strcmp(symbol, "glMemoryBarrierEXT") == 0) {
            printf("LWJGL linkerhook: hooked glMemoryBarrier\n");
            return (jlong) glMemoryBarrier_stub;
        }
        if (strcmp(symbol, "glMapBufferRange") == 0 || strcmp(symbol, "glMapBufferRangeEXT") == 0 || strcmp(symbol, "glMapBufferRangeARB") == 0) {
            printf("LWJGL linkerhook: hooked glMapBufferRange -> shadow buffer\n");
            return (jlong) glMapBufferRange_hook;
        }
        if (strcmp(symbol, "glMapBuffer") == 0 || strcmp(symbol, "glMapBufferOES") == 0 || strcmp(symbol, "glMapBufferARB") == 0) {
            printf("LWJGL linkerhook: hooked glMapBuffer -> shadow buffer\n");
            return (jlong) glMapBuffer_hook;
        }
        if (strcmp(symbol, "glUnmapBuffer") == 0 || strcmp(symbol, "glUnmapBufferOES") == 0 || strcmp(symbol, "glUnmapBufferARB") == 0) {
            printf("LWJGL linkerhook: hooked glUnmapBuffer -> shadow buffer\n");
            return (jlong) glUnmapBuffer_hook;
        }
        if (strcmp(symbol, "glGenSamplers") == 0 || strcmp(symbol, "glGenSamplersOES") == 0) {
            void* sym = dlsym((void*) handle, symbol); if (sym) return (jlong) sym;
            return (jlong) glGenSamplers_fallback;
        }
        if (strcmp(symbol, "glBindSampler") == 0 || strcmp(symbol, "glBindSamplerOES") == 0) {
            void* sym = dlsym((void*) handle, symbol); if (sym) return (jlong) sym;
            return (jlong) glBindSampler_fallback;
        }
        if (strcmp(symbol, "glDeleteSamplers") == 0 || strcmp(symbol, "glDeleteSamplersOES") == 0) {
            void* sym = dlsym((void*) handle, symbol); if (sym) return (jlong) sym;
            return (jlong) glDeleteSamplers_fallback;
        }
        if (strcmp(symbol, "glSamplerParameteri") == 0 || strcmp(symbol, "glSamplerParameteriOES") == 0) {
            void* sym = dlsym((void*) handle, symbol); if (sym) return (jlong) sym;
            return (jlong) glSamplerParameteri_fallback;
        }
    }
    void* sym = dlsym((void*) handle, symbol);
    if (!sym && symbol && strncmp(symbol, "gl", 2) == 0) return (jlong) universal_stub_void;
    return (jlong) sym;
}

void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL dlopen() and dlsym() hooks (BUILD v20260907-E)");
    printf("LWJGL linkerhook: installing dlopen/dlsym hooks (BUILD v20260907-E)\n");
    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if(dynamicLinkLoader == NULL) {
        LOGE("Failed to find the target class");
        printf("LWJGL linkerhook ERROR: Failed to find DynamicLinkLoader class\n");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod hooks[] = {
            {"ndlopen", "(JI)J", &ndlopen_bugfix},
            {"ndlsym", "(JJ)J", &ndlsym_hook}
    };
    if((*env)->RegisterNatives(env, dynamicLinkLoader, hooks, 2) != 0) {
        printf("LWJGL linkerhook: RegisterNatives failed\n");
        LOGE("Failed to register the hooked methods");
        printf("LWJGL linkerhook ERROR: Failed to register hooked methods\n");
        (*env)->ExceptionClear(env);
    }
    printf("LWJGL linkerhook: dlopen/dlsym hooks installed successfully\n");
}