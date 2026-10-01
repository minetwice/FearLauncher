#!/usr/bin/env python3
"""MESAPATCH: best-effort zink quirk toggles for the Mesa Android build.

Adds runtime env-var switches so zink-on-proprietary-Vulkan issues can be
bisected from the launcher without rebuilding:

  ZINK_MALI_NOCOHERENT=1       always vkFlush/vkInvalidate mapped memory
  ZINK_MALI_NOBINDLESS=1       disable bindless / descriptor-indexing caps
  ZINK_MALI_NOCOMPUTEUPLOAD=1  staging-blit-only texture uploads
  ZINK_MALI_NOSLAB=1           never sub-allocate buffers from slabs
  ZINK_MALI_NOREUSE=1          never reclaim/reuse buffers

Every edit is best-effort: Mesa source moves between releases, so a pattern
that no longer matches is reported as a warning and skipped, never a failure.
Usage: python3 tools/fearrender/mesapatch.py <mesa-dir>
"""
import re
import sys

root = sys.argv[1] if len(sys.argv) > 1 else 'mesa'

# (file, regex-pattern, replacement, note)
EDITS = [
    ('src/gallium/drivers/zink/zink_resource.c',
     r'obj->coherent = screen->info\.mem_props\.memoryTypes\[obj->bo->base(\.base)?\.placement\]\.propertyFlags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;',
     'obj->coherent = !debug_get_bool_option("ZINK_MALI_NOCOHERENT", false) &&\n      (screen->info.mem_props.memoryTypes[obj->bo->base.base.placement].propertyFlags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);',
     'ZINK_MALI_NOCOHERENT'),
    ('src/gallium/drivers/zink/zink_screen.c',
     r'caps->bindless_texture =\n      \(zink_descriptor_mode',
     'caps->bindless_texture = !debug_get_bool_option("ZINK_MALI_NOBINDLESS", false) &&\n      (zink_descriptor_mode',
     'ZINK_MALI_NOBINDLESS'),
    ('src/gallium/drivers/zink/zink_screen.c',
     r'enum pipe_texture_transfer_mode mode = PIPE_TEXTURE_TRANSFER_BLIT;\n      if \(!screen->is_cpu &&',
     'enum pipe_texture_transfer_mode mode = PIPE_TEXTURE_TRANSFER_BLIT;\n      if (!debug_get_bool_option("ZINK_MALI_NOCOMPUTEUPLOAD", false) &&\n          !screen->is_cpu &&',
     'ZINK_MALI_NOCOMPUTEUPLOAD'),
    ('src/gallium/drivers/zink/zink_bo.c',
     r'if \(!\(flags & \(ZINK_ALLOC_NO_SUBALLOC \| ZINK_ALLOC_SPARSE\)\)\)\s*&&\s*size <= max_slab_entry_size\) \{',
     'if (!(flags & (ZINK_ALLOC_NO_SUBALLOC | ZINK_ALLOC_SPARSE)) &&\n       size <= max_slab_entry_size && !getenv("ZINK_MALI_NOSLAB")) {',
     'ZINK_MALI_NOSLAB'),
    ('src/gallium/drivers/zink/zink_bo.c',
     r'return zink_screen_usage_check_completion\(screen, bo->reads\.u\) && zink_screen_usage_check_completion\(screen, bo->writes\.u\);',
     'if (getenv("ZINK_MALI_NOREUSE"))\n      return false;\n   return zink_screen_usage_check_completion(screen, bo->reads.u) && zink_screen_usage_check_completion(screen, bo->writes.u);',
     'ZINK_MALI_NOREUSE'),

    # ---- FearLauncher rootless Mali stability fixes ----
    # A) Stop the SIGSEGV when the Gallium driver screen failed to initialise.
    #    st_api_create_context() runs fscreen->screen->context_create(...) with
    #    no NULL check; on Mali, Zink's screen can fail to init and
    #    fscreen->screen is left NULL -> SIGSEGV at [NULL + 0x5c0].
    ('src/mesa/state_tracker/st_manager.c',
     r'pipe = fscreen->screen->context_create[(]fscreen->screen, NULL,',
     '''if (fscreen->screen == NULL) {
      fprintf(stderr, "FEARPATCH: st_api_create_context: fscreen->screen is NULL - driver screen failed to init; returning error instead of crashing");
      fputc(10, stderr);
      *error = ST_CONTEXT_ERROR_NO_MEMORY;
      return NULL;
   }
   pipe = fscreen->screen->context_create(fscreen->screen, NULL,''',
     'FEARPATCH_ST_API_NULLGUARD'),

    # B) Mirror every mesa_loge() in zink_screen.c to stderr so Zink's
    #    "why did the screen fail" reason shows up in latestlog.txt (by
    #    default mesa_loge only reaches logcat and is never captured).
    ('src/gallium/drivers/zink/zink_screen.c',
     r'#include "util/u_cpu_detect.h"',
     '''#include "util/u_cpu_detect.h"

/* FEARPATCH: mirror Zink error logs to stderr so they land in latestlog.txt */
#undef mesa_loge
#define mesa_loge(...) do { mesa_log(MESA_LOG_ERROR, __VA_ARGS__); fprintf(stderr, "FEARPATCH ZINK: " __VA_ARGS__); fputc(10, stderr); fflush(stderr); } while (0)''',
     'FEARPATCH_ZINK_STDERR'),
]

applied = 0
for path, pat, repl, note in EDITS:
    full = root + '/' + path
    try:
        s = open(full).read()
    except OSError as e:
        print("MESAPATCH WARN: cannot read %s (%s) - skipping %s" % (path, e, note))
        continue
    if ('"%s"' % note) in s:
        print("MESAPATCH SKIP: %s already patched" % path)
        applied += 1
        continue
    new, n = re.subn(pat, repl, s, count=1)
    if n == 0:
        print("MESAPATCH WARN: pattern not found in %s - %s toggle NOT applied (source moved?)" % (path, note))
        for i, l in enumerate(s.splitlines()):
            if 'coherent' in l or 'bindless_texture' in l or 'texture_transfer_mode' in l or 'max_slab_entry_size' in l or 'bo->reads.u' in l:
                print("  %d: %s" % (i + 1, l))
        continue
    open(full, 'w').write(new)
    print("MESAPATCH OK: %s (%s)" % (path, note))
    applied += 1

print("MESAPATCH DONE: %d/%d quirk toggles applied (warnings are non-fatal)" % (applied, len(EDITS)))
