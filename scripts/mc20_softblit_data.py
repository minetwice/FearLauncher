# data part 1
OLD_INC = '''#include "pan_context.h"
#include "pan_util.h"
#include "util/format/u_format.h"
'''
NEW_INC_A = '''#include "pan_context.h"
#include "pan_util.h"
#include "util/format/u_format.h"
#include <stdio.h>
#include <string.h>
#include <stdbool.h>

/* MC20: CPU fallback for color 2D blits. The GPU blitter path produces no
 * output on this device (Mali-G615 / Valhall v11 with the OSMesa winsys),
 * while texture_map-based readback is proven working (glReadPixels uses
 * the same path). Flip-aware: MC's present sends a NEGATIVE src box height
 * (GL Y-flip), which must be honoured by copying rows in reverse. */
static bool
panfrost_soft_blit(struct pipe_context *pipe,
                   const struct pipe_blit_info *info)
{
        struct pipe_transfer *strans = NULL, *dtrans = NULL;
        uint8_t *src = NULL, *dst = NULL;
        unsigned src_bpp, dst_bpp, sw, sh, dw, dh, ss, ds;
        bool flip_y, flip_x;

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
        if (info->src.box.width == 0 || info->src.box.height == 0 ||
            info->dst.box.width == 0 || info->dst.box.height == 0)
                return false;
        src_bpp = util_format_get_blocksize(info->src.format);
        dst_bpp = util_format_get_blocksize(info->dst.format);
        if (src_bpp != dst_bpp || src_bpp == 0)
                return false;

        sw = (info->src.box.width < 0) ? -info->src.box.width : info->src.box.width;
        sh = (info->src.box.height < 0) ? -info->src.box.height : info->src.box.height;
        dw = (info->dst.box.width < 0) ? -info->dst.box.width : info->dst.box.width;
        dh = (info->dst.box.height < 0) ? -info->dst.box.height : info->dst.box.height;
        flip_y = (info->src.box.height < 0) != (info->dst.box.height < 0);
        flip_x = (info->src.box.width < 0) != (info->dst.box.width < 0);

        struct pipe_box sbox = info->src.box;
        struct pipe_box dbox = info->dst.box;
        if (sbox.x > sbox.x + info->src.box.width)
                sbox.x = sbox.x + info->src.box.width;
        if (sbox.y > sbox.y + info->src.box.height)
                sbox.y = sbox.y + info->src.box.height;
        if (dbox.x > dbox.x + info->dst.box.width)
                dbox.x = dbox.x + info->dst.box.width;
        if (dbox.y > dbox.y + info->dst.box.height)
                dbox.y = dbox.y + info->dst.box.height;
        sbox.width = sw; sbox.height = sh;
        dbox.width = dw; dbox.height = dh;
'''
