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

        src = pipe->texture_map(pipe, info->src.resource, info->src.level,
                                 PIPE_MAP_READ, &info->src.box, &strans);
        if (src == NULL) {
                fprintf(stderr, "PANFORKSOFTBLIT: src map failed\\n");
                return false;
        }

        dst = pipe->texture_map(pipe, info->dst.resource, info->dst.level,
                                 %•UôÔõu$•DRÂf–æfòÓæG7Bæ&÷‚ÂfGG&ç2“°¢–b†G7BÓÒåTÄÂ’°¢g&–çFb‡7FFW'"Â%ädõ$µ4ôeD$Ä•C¢G7BÖf–ÆVEÅÆâ"“°¢—RÓçFW‡GW&U÷VæÖ‡—RÂ7G&ç2“°¢&WGW&âfÇ6S°¢Ð ¢7rÒ–æfòÓç7&2æ&÷‚çv–GFƒ°¢6‚Ò–æfòÓç7&2æ&÷‚æ†V–v‡C°¢GrÒ–æfòÓæG7Bæ&÷‚çv–GFƒ°¢F‚Ò–æfòÓæG7Bæ&÷‚æ†V–v‡C°¢72Ò7G&ç2Óç7G&–FS°¢G2ÒGG&ç2Óç7G&–FS° ¢g&–çFb‡7FFW'"Â%ädõ$µ4ôeD$Ä•C¢WW‚WRÓâWW‚WR'ÒWR73ÒWRG3ÒWUÅÆâ"À¢7rÂ6‚ÂGrÂF‚Â7&5ö'Â72ÂG2“° ¢–b‡7rÓÒGrbb6‚ÓÒF‚’°¢f÷"‡’Ò²’Â6ƒ²’²²¢ÖVÖ7’†G7B²‡6—¦U÷B’’¢G2À¢7&2²‡6—¦U÷B’’¢72À¢‡6—¦U÷B’7r¢7&5ö'“°¢ÒVÇ6R°¢f÷"‡’Ò²’ÂFƒ²’²²’°¢6öç7BV–çC…÷B§7&÷rÐ¢7&2²‡6—¦U÷B’‚‡’¢6‚’òF‚’¢73°¢V–çC…÷B¦G&÷rÒG7B²‡6—¦U÷B’’¢G3°¢f÷"‡‚Ò²‚ÂGs²‚²²’°¢Vç6–væVB7‚Ò‡‚¢7r’òGs°¢ÖVÖ7’†G&÷r²‡6—¦U÷B’‚¢G7Eö'À¢7&÷r²‡6—¦U÷B’7‚¢7&5ö'À¢G7Eö'“°¢Ð¢Ð¢Ð ¢—RÓçFW‡GW&U÷VæÖ‡—RÂGG&ç2“°¢—RÓçFW‡GW&U÷VæÖ‡—RÂ7G&ç2“°¢&WGW&âG'VS°§Ð¢rrp¢76W'BöÆEö–æ2–âBÂwåö&Æ—Bæ2–æ6ÇVFRæ6†÷"Ö—76–ærp¢BÒBç&WÆ6R†öÆEö–æ2ÂæWuö–æ2Â ¢öÆEö&Æ—BÒrrr–b†–æfòÓç&VæFW%ö6öæF—F–öåöVæ&ÆRb`¢æg&÷7E÷&VæFW%ö6öæF—F–öåö6†V6²†7G‚’¢&WGW&ã° ¢–b‚WF–Åö&Æ—GFW%ö—5ö&Æ—E÷7W÷'FVB†7G‚Óæ&Æ—GFW"Â–æfò’¢Vç&V6†&ÆR‚%Vç7W÷'FVB&Æ—EÅÆâ"“²rrp¢æWuö&Æ—BÒrrr–b†–æfòÓç&VæFW%ö6öæF—F–öåöVæ&ÆRb`¢æg&÷7E÷&VæFW%ö6öæF—F–öåö6†V6²†7G‚’¢&WGW&ã° ¢–b‡æg&÷7E÷6ögEö&Æ—B‡—RÂ–æfò’¢&WGW&ã° ¢–b‚WF–Åö&Æ—GFW%ö—5ö&Æ—E÷7W÷'FVB†7G‚Óæ&Æ—GFW"Â–æfò’¢Vç&V6†&ÆR‚%Vç7W÷'FVB&Æ—EÅÆâ"“²rrp¢76W'BöÆEö&Æ—B–âBÂwæg&÷7Eö&Æ—Bæ6†÷"Ö—76–ærp¢BÒBç&WÆ6R†öÆEö&Æ—BÂæWuö&Æ—BÂ¢çw&—FU÷FW‡B‡B¢&–çB‚wåö&Æ—Bæ3¢6ögBÖ&Æ—BF6†VBr ¢2ÒÒÒÒÒÒÒÒÒÒF6‚#¢ÖW6Ö–âö&Æ—Bæ2F–væ÷7F–72ÒÒÒÒÒÒÒÒÒÐ§Ò&ö÷Bòw7&2öÖW6öÖ–âö&Æ—Bæ2p§BÒç&VE÷FW‡B‚ ¦–btÔ3#$Ä•Br–âC ¢&–çB‚v&Æ—Bæ3¢Ç&VG’F6†VBr¦VÇ6S ¢–br6–æ6ÇVFRÇ7FF–òæƒâræ÷B–âBç7Æ—B‚r6–æ6ÇVFR&Ö–âövÆ†VFW"æ‚"r•³Õ²Ó#¥Ó ¢70¢f—'7Eö–æ6ÇVFRÒBæ–æFW‚‚r6–æ6ÇVFRr¢BÒE³¦f—'7Eö–æ6ÇVFUÒ²r6–æ6ÇVFRÇ7FF–òæƒåÆâr²E¶f—'7Eö–æ6ÇVFS¥Ð ¢öÆEöG7BÒrrr–b†G7E7W&b’°¢&Æ—BæG7Bç&W6÷W&6RÒG7E7W&bÓçFW‡GW&S°¢&Æ—BæG7BæÆWfVÂÒG7E7W&bÓçRçFW‚æÆWfVÃ°¢&Æ—BæG7Bæ&÷‚ç¢ÒG7E7W&bÓçRçFW‚æf—'7EöÆ–W#°¢&Æ—BæG7Bæf÷&ÖBÒG7E7W&bÓæf÷&ÖC° ¢7G‚Óç—RÓæ&Æ—B†7G‚Óç—RÂf&Æ—B“°¢G7E&"ÓæFVf–æVBÒG'VS²ò¢g&öçB'VffW"G&6¶–ær¢ð¢Òrrp¢æWuöG7BÒrrr–b†G7E7W&b’°¢&Æ—BæG7Bç&W6÷W&6RÒG7E7W&bÓçFW‡GW&S°¢&Æ—BæG7BæÆWfVÂÒG7E7W&bÓçRçFW‚æÆWfVÃ°¢&Æ—BæG7Bæ&÷‚ç¢ÒG7E7W&bÓçRçFW‚æf—'7EöÆ–W#°¢&Æ—BæG7Bæf÷&ÖBÒG7E7W&bÓæf÷&ÖC° ¢g&–çFb‡7FFW'"Â$Ô3#$Ä•C¢6öÆ÷"&Æ—BVG‚VBÓâVG‚VEÅÆâ"À¢&Æ—Bç7&2æ&÷‚çv–GF‚Â&Æ—Bç7&2æ&÷‚æ†V–v‡BÀ¢&Æ—BæG7Bæ&÷‚çv–GF‚Â&Æ—BæG7Bæ&÷‚æ†V–v‡B“°¢7G‚Óç—RÓæ&Æ—B†7G‚Óç—RÂf&Æ—B“°¢G7E&"ÓæFVf–æVBÒG'VS²ò¢g&öçB'VffW"G&6¶–ær¢ð¢ÒVÇ6R°¢g&–çFb‡7FFW'"Â$Ô3#$Ä•C¢åTÄÂG7B7W&f6RÂ6öÆ÷"&Æ—B6¶—VEÅÆâ"“°¢Òrrp¢–böÆEöG7Bæ÷B–âC ¢2G'’2×76Rf&–ç@¢öÆEöG7BÒöÆEöG7Bç&WÆ6R‚r–b†G7E7W&b’²rÂr–b†G7E7W&b’²r¢2æ÷&ÖÆ—¦RÆÂÆ–æW2Fò’×76R&6P¢Æ–æW2ÒöÆEöG7Bç7Æ—B‚uÆâr¢f—†VBÒ¶Æâ–bæ÷BÆâç7F'G7v—F‚‚rr’VÇ6RÆâf÷"Æâ–âÆ–æW5Ð¢öÆEöG7BÒuÆâræ¦ö–â†f—†VB¢76W'BöÆEöG7B–âBÂv&Æ—Bæ26öÆ÷"G7Bæ6†÷"Ö—76–ærp¢BÒBç&WÆ6R†öÆEöG7BÂæWuöG7BÂ¢çw&—FU÷FW‡B‡B¢&–çB‚v&Æ—Bæ3¢Ô3#$Ä•BF–væ÷7F–72F6†VBr §&–çB‚w6ögBÖ&Æ—BF6ƒ¢ô²r 