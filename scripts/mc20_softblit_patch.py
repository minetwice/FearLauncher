#!/usr/bin/env python3
"""MC20 soft-blit patch for panfork (PojavLauncherTeam/panfork_offscreen_rootless@csf).

Applies two patches to a Mesa checkout (arg = path to mesa root):
1. src/gallium/drivers/panfrost/pan_blit.c: color 2D blits go through a CPU
   copy (transfer_map read + write, same mechanism as the proven-working
   glReadPixels). The GPU blitter path produces no output on this device.
2. src/mesa/main/blit.c: MC20BLIT debug lines so the latestlog shows exactly
   which blits reach pipe->blit and which are skipped (NULL surfaces).

Idempotent: safe to run twice.
"""
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'mesa')
assert (root / 'src').exists(), 'not a mesa checkout: %s' % root

# ---------- patch 1: pan_blit.c ----------
p = root / 'src/gallium/drivers/panfrost/pan_blit.c'
t = p.read_text()

if 'panfrost_soft_blit' in t:
    print('pan_blit.c: already patched')
else:
    old_inc = '''#include "pan_context.h"
#include "pan_util.h"
#include "util/format/u_format.h"
'''
    new_inc = '''#include "pan_context.h"
#include "pan_util.h"
#include "util/format/u_format.h"
#include <stdio.h>
#include <string.h>
#include <stdbool.h>

/* MC20: CPU fallback for color 2D blits. The GPU blitter path produces no
 * output on this device (Mali-G615 / Valhall v11 with the OSMesa winsys),
 * while transfer_map-based readback is proven working (glReadPixels uses
 * the same path). Same-size blits become a row-by-row memcpy; scaled blits
 * use nearest-neighbour sampling. */
static bool
panfrost_soft_blit(struct pipe_context *pipe,
                   const struct pipe_blit_info *info)
{
        struct pipe_transfer *strans = NULL, *dtrans = NULL;
        uint8_t *src = NULL, *dst = NULL;
        unsigned src_bpp, dst_bpp, sw, sh, dw, dh, ss, ds;
        unsigned x, y;

        if ((info->mask & PIPE_MASK_RGBA) == 0)
                return false;
        if (info->mask & (PIPE_MASK_Z | PIPE_MASK_S))
                return false;
        if (info->scissor_enable)
                return false;
        if (info->src.box.depth != 1 || info->dst.box.depth != 1)
                return false;
        if (info->src.resource->nr_samples > 1 ||
            info->dst.resource->nr_samples > 1)
                return false;
        if (info->src.box.width <= 0 || info->src.box.height <= 0 ||
            info->dst.box.width <= 0 || info->dst.box.height <= 0)
                return false;
        src_bpp = util_format_get_blocksize(info->src.format);
        dst_bpp = util_format_get_blocksize(info->dst.format);
        if (src_bpp != dst_bpp || src_bpp == 0)
                return false;

        src = pipe->transfer_map(pipe, info->src.resource, info->src.level,
                                 PIPE_MAP_READ, &info->src.box, &strans);
        if (src == NULL) {
                fprintf(stderr, "PANFORKSOFTBLIT: src map failed\\n");
                return false;
        }

        dst = pipe->transfer_map(pipe, info->dst.resource, info->dst.level,
                                 PIPE_MAP_WRITE, &info->dst.box, &dtrans);
        if (dst == NULL) {
                fprintf(stderr, "PANFORKSOFTBLIT: dst map failed\\n");
                pipe->transfer_unmap(pipe, strans);
                return false;
        }

        sw = info->src.box.width;
        sh = info->src.box.height;
        dw = info->dst.box.width;
        dh = info->dst.box.height;
        ss = strans->stride;
        ds = dtrans->stride;

        fprintf(stderr, "PANFORKSOFTBLIT: %ux%u -> %ux%u bpp=%u ss=%u ds=%u\\n",
                sw, sh, dw, dh, src_bpp, ss, ds);

        if (sw == dw && sh == dh) {
                for (y = 0; y < sh; y++)
                        memcpy(dst + (size_t) y * ds,
                               src + (size_t) y * ss,
                               (size_t) sw * src_bpp);
        } else {
                for (y = 0; y < dh; y++) {
                        const uint8_t *srow =
                                src + (size_t) ((y * sh) / dh) * ss;
                        uint8_t *drow = dst + (size_t) y * ds;
                        for (x = 0; x < dw; x++) {
                                unsigned sx = (x * sw) / dw;
                                memcpy(drow + (size_t) x * dst_bpp,
                                       srow + (size_t) sx * src_bpp,
                                       dst_bpp);
                        }
                }
        }

        pipe->transfer_unmap(pipe, dtrans);
        pipe->transfer_unmap(pipe, strans);
        return true;
}
'''
    assert old_inc in t, 'pan_blit.c include anchor missing'
    t = t.replace(old_inc, new_inc, 1)

    old_blit = '''        if (info->render_condition_enable &&
            !panfrost_render_condition_check(ctx))
                return;

        if (!util_blitter_is_blit_supported(ctx->blitter, info))
                unreachable("Unsupported blit\\n");'''
    new_blit = '''        if (info->render_condition_enable &&
            !panfrost_render_condition_check(ctx))
                return;

        if (panfrost_soft_blit(pipe, info))
                return;

        if (!util_blitter_is_blit_supported(ctx->blitter, info))
                unreachable("Unsupported blit\\n");'''
    assert old_blit in t, 'panfrost_blit anchor missing'
    t = t.replace(old_blit, new_blit, 1)
    p.write_text(t)
    print('pan_blit.c: soft-blit patched')

# ---------- patch 2: mesa main/blit.c diagnostics ----------
p = root / 'src/mesa/main/blit.c'
t = p.read_text()

if 'MC20BLIT' in t:
    print('blit.c: already patched')
else:
    if '#include <stdio.h>' not in t.split('#include "main/glheader.h"')[0][-200:]:
        pass
    first_include = t.index('#include')
    t = t[:first_include] + '#include <stdio.h>\n' + t[first_include:]

    old_dst = '''            if (dstSurf) {
               blit.dst.resource = dstSurf->texture;
               blit.dst.level = dstSurf->u.tex.level;
               blit.dst.box.z = dstSurf->u.tex.first_layer;
               blit.dst.format = dstSurf->format;

               ctx->pipe->blit(ctx->pipe, &blit);
               dstRb->defined = true; /* front buffer tracking */
            }'''
    new_dst = '''            if (dstSurf) {
               blit.dst.resource = dstSurf->texture;
               blit.dst.level = dstSurf->u.tex.level;
               blit.dst.box.z = dstSurf->u.tex.first_layer;
               blit.dst.format = dstSurf->format;

               fprintf(stderr, "MC20BLIT: color blit %dx%d -> %dx%d\\n",
                       blit.src.box.width, blit.src.box.height,
                       blit.dst.box.width, blit.dst.box.height);
               ctx->pipe->blit(ctx->pipe, &blit);
               dstRb->defined = true; /* front buffer tracking */
            } else {
               fprintf(stderr, "MC20BLIT: NULL dst surface, color blit skipped\\n");
            }'''
    if old_dst not in t:
        # try 3-space variant
        old_dst = old_dst.replace('            if (dstSurf) {', '         if (dstSurf) {')
        # normalize all lines to 9-space base
        lines = old_dst.split('\n')
        fixed = [ln if not ln.startswith('         ') else ln for ln in lines]
        old_dst = '\n'.join(fixed)
    assert old_dst in t, 'blit.c color dst anchor missing'
    t = t.replace(old_dst, new_dst, 1)
    p.write_text(t)
    print('blit.c: MC20BLIT diagnostics patched')

print('soft-blit patch: OK')
