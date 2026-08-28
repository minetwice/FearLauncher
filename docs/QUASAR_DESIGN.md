# Quasar Architecture Design Document
## OpenGL 4.6 → GLES 3.2 Translator for Android Mali

> Inspired by Zink (lazy state, IR-based shaders, disk caching), ANGLE (dirty-bit handler tables, 4-level program cache, driver-uniform emulation), and gl4ES (dlsym shim model, main-FBO, PSA binary cache). All code is original — no dependencies on Zink/ANGLE/gl4ES.

---

## 1. Design Philosophy

**Quasar is a dlsym-shim translator.** It replaces `libGL.so` for LWJGL3, intercepts every desktop GL call, translates it to GLES 3.2, and forwards to the real Mali driver via `dlopen("libGLESv3.so") + dlsym`.

Three pillars learned from researching open-source translators:

| Pillar | Inspired by | Quasar Implementation |
|--------|------------|----------------------|
| **IR-based shader translation** | Zink (NIR→SPIR-V), ANGLE (AST→SPIR-V) | glslang → SPIR-V → SPIRV-Cross → GLSL ES 3.20 |
| **Lazy dirty-bit state** | Zink (dirty flags), ANGLE (handler table) | Per-context state struct + dirty bitmask + handler function table |
| **dlsym shim + main FBO** | gl4ES (libGL replacement, internal FBO) | libquasar_gl.so with NO GLES link deps, internal main FBO |

**What we do NOT do:**
- No string/regex shader conversion (gl4ES's weakness — caps at GL 2.1)
- No Vulkan dependency (Zink fails on Mali — missing logicOp, fillModeNonSolid, shaderClipDistance)
- No Mesa/Gallium dependency (too large for Android)
- No system driver replacement (ANGLE's approach — needs platform control)

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Minecraft / LWJGL3                 │
│         (calls gl*() via dlsym on libquasar_gl.so)   │
└──────────────────────┬──────────────────────────────┘
                       │ gl*() calls
                       ▼
┌─────────────────────────────────────────────────────┐
│                  libquasar_gl.so                      │
│                                                       │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │  GL Export  │  │ State Tracker │  │   Shader    │ │
│  │   Layer     │──│   (dirty bits │──│ Translator  │ │
│  │ (dlsym API) │  │  + handlers)  │  │ (glslang→    │ │
│  │             │  │              │  │  SPIRV-Cross)│ │
│  └──────┬──────┘  └──────┬───────┘  └──────┬───────┘ │
│         │                │                  │        │
│  ┌──────▼──────────────────▼──────────────────▼────┐  │
│  │           GLES Translation Layer               │  │
│  │  • Feature emulation (noperspective, MRT, etc)  │  │
│  │  • FBO management (main FBO + blit)            │  │
│  │  • Program cache (L0-L3 hierarchy)             │  │
│  └──────────────────────┬──────────────────────────┘  │
│                         │ gles_*() via dlsym          │
└─────────────────────────┼───────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────┐
│           libGLESv3.so (Mali Driver)                 │
│           libEGL.so (Android EGL)                    │
└─────────────────────────────────────────────────────┘
```

---

## 3. Module Breakdown

### 3.1 GL Export Layer (`quasar_gl_core.c`)

**Purpose:** Export ALL OpenGL 4.6 symbols so `dlsym(libquasar_gl_handle, "glXxx")` finds OUR function, never Mali's.

**Key design decisions (learned the hard way):**
- Do NOT link against `libGLESv3.so` or `libEGL.so` in CMakeLists.txt
- Resolve real GLES/EGL functions at runtime via `dlopen + dlsym`
- Use `-fvisibility=default` to ensure all symbols are exported
- Every GL function is a thin wrapper that calls into State Tracker → GLES Translation Layer

**Function categories:**
1. **Spoofed queries** — `glGetString`, `glGetStringi`, `glGetIntegerv`, `glGetBooleanv`, etc. return hardcoded GL 4.6 capabilities
2. **Direct passthrough** — `glBindBuffer`, `glBufferData`, `glEnable`, `glDisable`, etc. → forward to real GLES
3. **Translated calls** — `glShaderSource` (shader transpilation), `glBlitFramebuffer` (FBO blit), `glDrawBuffers` (MRT mapping)
4. **Emulated calls** — `glPolygonMode`, `glShadeModel`, display lists, etc.

### 3.2 State Tracker (`quasar_state.c` / `quasar_state.h`)

**Purpose:** Track OpenGL state per-context, translate to GLES state lazily.

**Inspired by:** Zink's dirty-flag system + ANGLE's handler-table.

**Design:**

```c
typedef enum {
    QUASAR_DIRTY_VIEWPORT       = 1 << 0,
    QUASAR_DIRTY_SCISSOR        = 1 << 1,
    QUASAR_DIRTY_BLEND          = 1 << 2,
    QUASAR_DIRTY_DEPTH           = 1 << 3,
    QUASAR_DIRTY_STENCIL         = 1 << 4,
    QUASAR_DIRTY_CULL            = 1 << 5,
    QUASAR_DIRTY_POLYGON_MODE    = 1 << 6,
    QUASAR_DIRTY_TEXTURES        = 1 << 7,
    QUASAR_DIRTY_PROGRAM         = 1 << 8,
    QUASAR_DIRTY_FRAMEBUFFER     = 1 << 9,
    QUASAR_DIRTY_VERTEX_ARRAY    = 1 << 10,
    QUASAR_DIRTY_UNIFORMS        = 1 << 11,
    QUASAR_DIRTY_DRAW_BUFFERS    = 1 << 12,
} quasar_dirty_bit_t;

typedef struct quasar_context {
    uint64_t dirty_bits;
    GLint viewport[4];
    GLdouble depth_near, depth_far;
    GLint scissor_box[4];
    GLboolean scissor_enabled;
    GLboolean blend_enabled;
    GLenum blend_src_rgb, blend_dst_rgb, blend_src_a, blend_dst_a;
    GLfloat blend_color[4];
    GLboolean depth_test;
    GLenum depth_func;
    GLboolean depth_mask;
    GLboolean stencil_test;
    GLboolean cull_face;
    GLenum cull_mode, front_face;
    GLenum polygon_mode_front, polygon_mode_back;
    GLuint bound_textures[32];
    GLenum texture_targets[32];
    GLint active_texture_unit;
    GLuint current_program;
    GLuint draw_fbo, read_fbo;
    GLenum draw_buffers[8];
    GLint draw_buffer_count;
    GLuint current_vao;
    struct quasar_caps caps;
} quasar_context_t;

// Handler table — one function per dirty bit
typedef void (*quasar_state_handler_t)(quasar_context_t *ctx);
static const quasar_state_handler_t state_handlers[] = {
    [0]  = handle_viewport_dirty,
    [1]  = handle_scissor_dirty,
    [2]  = handle_blend_dirty,
    [3]  = handle_depth_dirty,
    // ...
};

// Called at draw time — iterate only set bits
void quasar_flush_state(quasar_context_t *ctx) {
    while (ctx->dirty_bits) {
        int bit = __builtin_ctzll(ctx->dirty_bits);
        state_handlers[bit](ctx);
        ctx->dirty_bits &= ~(1ULL << bit);
    }
}
```

### 3.3 Shader Translator (`quasar_shader.c`)

**Purpose:** Translate desktop GLSL 4.60 → GLSL ES 3.20.

**Pipeline:**
```
Desktop GLSL 4.60 source
    │
    ▼  glslang (GLSL → SPIR-V)
    │  • Parses GLSL 4.60 with full AST
    │  • Handles #version, #extension, built-ins
    │  • Outputs SPIR-V binary
    │
    ▼  SPIRV-Cross (SPIR-V → GLSL ES 3.20)
    │  • Removes unsupported decorations (noperspective → smooth)
    │  • Converts geometry/tessellation shaders (if unsupported, expand in vertex shader)
    │  • Emits GLSL ES 3.20 compatible source
    │  • Handles precision qualifiers
    │
    ▼  Quasar post-processing
    │  • Inject driver uniforms (viewport flip, depth range)
    │  • Patch binding points
    │  • Add emulation code (noperspective via 1/w trick if needed)
    │
    ▼  GLSL ES 3.20 source → real GLES driver
```

**noperspective emulation (the #1 crash cause on Mali):**

Strategy 1 (default — strip): Remove `noperspective` qualifier. Works for 90% of shaders.

Strategy 2 (accurate — 1/w trick): For shaders where noperspective matters:
```glsl
// Emulated noperspective:
smooth out vec2 texCoord;
smooth out float quasar_inv_w;
// In vertex: quasar_inv_w = 1.0 / gl_Position.w; texCoord *= quasar_inv_w;
// In fragment: texCoord /= quasar_inv_w;
```

### 3.4 FBO Manager (`quasar_fbo.c`)

**Purpose:** Manage framebuffer emulation, main FBO, and blit operations.

**Inspired by:** gl4ES (main FBO concept), ANGLE (deferred clears, feedback-loop awareness).

**Main FBO strategy:**
- GLES default framebuffer (FBO 0) cannot be manipulated like desktop GL's
- Quasar creates an internal "main FBO" used as FBO 0
- On swap (eglSwapBuffers), blit main FBO → real surface
- This allows glReadPixels from FBO 0, glBlitFramebuffer to/from FBO 0, etc.

### 3.5 GLES Function Loader (`quasar_loader.c`)

**Purpose:** Dynamically load real GLES 3.2 + EGL functions via dlopen/dlsym.

```c
typedef struct quasar_gles_funcs {
    void (*glDrawArrays)(GLenum mode, GLint first, GLsizei count);
    void (*glDrawElements)(GLenum mode, GLsizei count, GLenum type, const void *indices);
    // ... 300+ function pointers
} quasar_gles_funcs_t;

static quasar_gles_funcs_t g_gles;
static void *g_gles_handle = NULL;

void quasar_loader_init(void) {
    g_gles_handle = dlopen("libGLESv3.so", RTLD_NOW | RTLD_LOCAL);
    if (!g_gles_handle) g_gles_handle = dlopen("libGLESv2.so", RTLD_NOW | RTLD_LOCAL);
    #define LOAD(name) g_gles.name = dlsym(g_gles_handle, #name)
    LOAD(glDrawArrays);
    // ... 300+ loads
    #undef LOAD
}
```

### 3.6 Capability Probe (`quasar_caps.c`)

Query real GLES capabilities at init, determine what GL features we can expose.

---

## 4. File Structure

```
app_pojavlauncher/src/main/jni/quasar_v2/
├── quasar_gl_core.c          # GL export layer (all gl* symbols)
├── quasar_gl_core.h          # Public GL function declarations
├── quasar_state.c            # State tracker (dirty bits + handlers)
├── quasar_state.h            # State struct, dirty bit enum, handler API
├── quasar_shader.c           # Shader translator (glslang→SPIRV-Cross)
├── quasar_shader.h           # Shader translator API
├── quasar_shader_hook.c      # glShaderSource interception
├── quasar_fbo.c              # FBO manager (main FBO + blit)
├── quasar_fbo.h              # FBO manager API
├── quasar_loader.c           # GLES function loader (dlopen+dlsym)
├── quasar_loader.h           # Loader API
├── quasar_caps.c             # Capability probe
├── quasar_caps.h             # Cap struct
├── quasar_cache.c            # Program cache (L0-L3 hierarchy)
├── quasar_cache.h            # Cache API
└── quasar_log.h              # Logging macros
```

---

## 5. Implementation Roadmap

### Phase 1: Foundation (CURRENT)
- [x] GL export layer with ~40 passthrough functions
- [x] Spoofed glGetString/glGetStringi (GL 4.6)
- [x] Spoofed glGetIntegerv (hardcoded limits)
- [x] eglGetCurrentContext check (no crash when no context)
- [x] No GLES/EGL link deps (dlopen only)
- [x] -fvisibility=default

### Phase 2: Core State Tracker
- [ ] quasar_state.c — dirty-bit state struct
- [ ] quasar_loader.c — dynamic GLES function loading
- [ ] quasar_caps.c — capability probing
- [ ] Expand GL exports to ~200 functions
- [ ] Per-context state (not global)

### Phase 3: Shader Translation
- [ ] Integrate glslang + SPIRV-Cross into quasar_shader.c
- [ ] Implement glShaderSource → transpile → real glShaderSource
- [ ] noperspective stripping (simple) → 1/w emulation (accurate)
- [ ] Shader program cache (L2 hash map)
- [ ] Disk cache (L3 — glProgramBinary blobs)

### Phase 4: FBO Management
- [ ] quasar_fbo.c — main FBO + FBO tracking
- [ ] glBlitFramebuffer passthrough (GLES 3.0+ direct)
- [ ] Shader-based blit fallback (flips, format conversion)
- [ ] eglSwapBuffers hook (blit main FBO → surface)

### Phase 5: Feature Emulation
- [ ] glPolygonMode (wireframe via shader)
- [ ] glShadeModel(GL_FLAT) → flat qualifier injection
- [ ] MRT (glDrawBuffers) mapping
- [ ] Transform feedback passthrough
- [ ] SSBO/Image load/store passthrough

### Phase 6: Full GL 4.6 Coverage
- [ ] Expand to 500+ GL functions
- [ ] Geometry shader emulation (vertex amplification if no EXT)
- [ ] Tessellation emulation (CPU expansion if no EXT)
- [ ] Multi-draw indirect
- [ ] DSA (Direct State Access) translation

### Phase 7: Optimization
- [ ] 4-level program cache (L0 current, L1 transition, L2 hash, L3 disk)
- [ ] Deferred clears (batch into render pass LOAD_OP_CLEAR)
- [ ] Dirty-bit handler table (iterate only changed bits)
- [ ] Precision qualifier optimization (highp/mediump per-varying)

---

## 6. Key Lessons Applied

### From Zink:
- **Lazy state**: Don't push state to GLES until draw time. Track changes with dirty bits, flush only at draw.
- **IR over text**: Never parse GLSL with regex. Use a real compiler (glslang) to get SPIR-V, then generate GLSL ES.
- **Disk cache**: Cache compiled/linked programs to disk. Shader link cost dominates on Mali.

### From ANGLE:
- **Dirty-bit handler table**: Use a function-pointer table indexed by dirty bit position. At draw time, iterate only set bits.
- **Driver-uniform injection**: Inject uniforms for viewport flip, depth range, etc. into transpiled shaders.
- **4-level cache**: L0 (current), L1 (transition table), L2 (hash map), L3 (disk binary).

### From gl4ES:
- **dlsym shim**: Be the GL library. Export all symbols. dlopen real GLES. No link deps.
- **Main FBO**: Render to internal FBO, blit to surface on swap. GLES default fb can't be manipulated.
- **PSA caching**: Use GL_OES_get_program_binary / glProgramBinary for disk cache.
- **Texture matrix in translator**: Handle texture matrices ourselves, not in GLES.

### From PojavLauncher Mali experience:
- **Target GLES 3.2, not Vulkan**: Mali Vulkan drivers miss Zink's base requirements.
- **GL 4.6 is the differentiator**: gl4ES caps at GL 2.1. Modern shaderpacks need GL 4.6.
- **Shader link cost dominates**: On Mali, glLinkProgram is the bottleneck. Caching linked program binaries is essential.

---

## 7. What Makes Quasar Different

| Feature | gl4ES | LTW | Zink | **Quasar** |
|---------|-------|-----|------|-----------|
| Target | GLES 2.0/3.x | GLES 3.x | Vulkan | **GLES 3.2** |
| Shader translation | String/regex | None (passthrough) | NIR→SPIR-V | **glslang→SPIRV-Cross** |
| GL coverage | 2.1 + partial 3.x | 3.x (thin) | 4.6 (if Vulkan supports) | **4.6** |
| Mali support | Yes (slow) | Yes | No (driver gaps) | **Yes** |
| State tracking | Per-context struct | None | Lazy dirty bits | **Dirty bits + handlers** |
| Program cache | PSA (binary) | None | Disk + in-memory | **4-level (L0-L3)** |
| noperspective | Not supported | Crashes | Via SPIR-V | **IR-level strip/1/w** |
| Geometry/tess | No | No | Yes (if Vulkan) | **Emulate or passthrough** |
| FBO management | Main FBO | None | Lazy render passes | **Main FBO + deferred** |
| Dependency | None | None | Mesa (huge) | **glslang + SPIRV-Cross** |

Quasar is the only translator that combines:
- gl4ES's proven dlsym-shim model (works on Android without system-level access)
- Zink's IR-based shader translation (real GL 4.6 coverage)
- ANGLE's state management efficiency (dirty bits, caching)
- Targeting GLES 3.2 (the realistic Mali target, avoiding Vulkan driver gaps)

---

## 8. noperspective Emulation — Detailed

`noperspective` is the #1 crash cause for shaderpacks on Android. GLES treats it as a reserved keyword that causes a compile error.

**Strategy 1 (default — strip):** Remove `noperspective` qualifier via SPIRV-Cross. Works for 90% of shaders.

**Strategy 2 (accurate — 1/w trick):** For shaders where noperspective matters:
```glsl
smooth out vec2 texCoord;
smooth out float quasar_inv_w;
// In vertex: quasar_inv_w = 1.0 / gl_Position.w; texCoord *= quasar_inv_w;
// In fragment: texCoord /= quasar_inv_w;
```
This gives mathematically correct noperspective interpolation on ANY GLES hardware.

Decision: Start with Strategy 1 (strip). If a shaderpack shows visual artifacts, switch to Strategy 2.

---

## 9. Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| glslang warm-up cost (~20ms) | Load lazily on first glShaderSource call, not at init |
| SPIRV-Cross binary size | Compile only with SPIRV_CROSS_C_API_GLSL=1 |
| Shader link cost on Mali | L3 disk cache (glProgramBinary blobs), L1 transition table |
| Too many GL functions to implement | Use code generation — define all functions with a macro |
| Memory pressure from shader cache | LRU eviction on L2, size limit on L3 disk cache |
| Context switch issues | Per-context state (not global), support shared objects |
