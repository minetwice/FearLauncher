# data part 2 (NEW_INC_B)
NEW_INC_B = '''        if (src == NULL) {
                fprintf(stderr, "PANFORKSOFTBLIT: src map failed\\n");
                return false;
        }

        dst = pipe->texture_map(pipe, info->dst.resource, info->dst.level,
                                 PIPE_MAP_WRITE, &info->dst.box, &dtrans);
        if (dst == NULL) {
                fprintf(stderr, "PANFORKSOFTBLIT: dst map failed\\n");
                pipe->texture_unmap(pipe, strans);
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

        pipe->texture_unmap(pipe, dtrans);
        pipe->texture_unmap(pipe, strans);
        return true;
}
'''
