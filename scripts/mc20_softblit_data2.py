# data part 2 (NEW_INC_B)
NEW_INC_B = '''
#include <time.h>
#include <unistd.h>
        /* MC20 v2.13: pace large present blits to ~30fps. Without this
        * uncapped render loop spins at 100+ fps doing ~m0MB of CPU copies
         * per frame, starving MC's worker threads during mod loading. */
        if ((long) sw * (long) sh > 500000L) {
                static struct timespec mc20_last;
                struct timespec now;
                long elapsed_ms;
                clock_gettime(CLOCK_MONOTONIC, &now);
                elapsed_ms = (now.tv_sec - mc20_last.tv_sec) * 1000L
                             + (now.tv_nsec - mc20_last.tv_nsec) / 1000000L;
                if (elapsed_ms < 33L && elapsed_ms >= 0L)
                        usleep((useconds_t) ((33L - elapsed_ms) * 1000L));
                clock_gettime(CLOCK_MONOTONIC, &mc20_last);
        }

        /* MC20 v2.14 GPUVERIFY: run the hardware blit (u_blitter) first,
         * then sample what it wrote into dst. The CPU soft-blit below
         * overwrites the result afterwards, so the screen stays correct
         * either way - this only tells us (via log) if the GPU blit path
         * writes anything on this device. */
        {
                struct panfrost_context *pctx = pan_context(pipe);
                struct pipe_box vbox = dbox;
                struct pipe_transfer *vtrans = NULL;
                uint8_t *vdst;
                int vstride;
                panfrost_blitter_save(pctx, info->render_condition_enable);
                util_blitter_blit(pctx->blitter, info);
                vdst = pipe->texture_map(pipe, info->dst.resource,
                                         info->dst.level, PIPE_MAP_READ,
                                         &vbox, &vtrans);
                vstride = vtrans ? vtrans->stride : (int) (dw * dst_bpp);
                if (vdst) {
                        uint32_t *pbase = (uint32_t *) vdst;
                        int cx = (int) dw / 2;
                        int cy = (int) dh / 2;
                        fprintf(stderr, "GPUVERIFY: tl=%08x c=%08x br=%08x\\n",
                               pbase[0],
                                *(uint32_t *) (vdst + (size_t) cy * vstride
                                            + (size_t) cx * 4)
                                *(uint32_t *) (vdst + (size_t) (dh - 1) * vstride
                                                 + (size_t)(dw - 1) * 4));
                        pipe->texture_unmap(pipe, vtrans);
                } else {
                        fprintf(stderr, "GPUVERIFY: dst map FAILED\\n");
                }
        }

        src = pipe->texture_map(pipe, info->src.resource, info->src.level,
                                 PIPE_MAP_READ, &sbox, &strans);
        if (src == NULL) {
                fprintf(stderr, "PANFORKSOFTBLIT: src map failed\\n");
                return false;
        }

        dst = pipe->texture_map(pipe, info->dst.resource, info->dst.level,
                                PIPE_MAP_WRITE, &dbox, &dtrans);
        if (dst == NULL) {
                fprintf(stderr, "PANFORKSOFTBLIT: dst map failed\\n");
                pipe->texture_unmap(pipe, strans);
                return false;
        }

        ss = strans->stride;
        ds = dtrans->stride;

        fprintf(stderr, "PANFORKSOFTBLIT: %ux%u -> %ux%u bpp=%u ss=%u ds=%u flip=%d\\n",
                sw, sh, dw, dh, src_bpp, ss, ds, flip_y);

        {
                int y;
                for (y = 0; y < (int) dh; y++) {
                        int sy = (y * (int) sh) / (int) dh;
                        const uint8_t *srow;
                        uint8_t *drow;
                        if (flip_y)
                                sy = (int) sh - 1 - sy;
                        srow = src + (size_t) sy * ss;
                        drow = dst + (size_t) y * ds;
                        if (sw == dw && !flip_x) {
                                memcpy(drow, srow, (size_t) sw * src_bpp);
                        } else {
                                int x;
                                for (x = 0; x < (int) dw; x++) {
                                        int sx = (x * (int) sw) / (int) dw;
                                        if (flip_x)
                                                sx = (int) sw - 1 - sx;
                                       memcpy(drow + (size_t) x * dst_bpp,
                                                 srow + (size_t) sx * src_bpp,
                                                dst_bpp);
                                 }
                        }
                }
        }

        pipe->texture_unmap(pipe, dtrans);
        pipe->texture_unmap(pipe, strans);
        return true;
}
'''
