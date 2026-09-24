# data part 2 (NEW_INC_B)
NEW_INC_B = '''        src = pipe->texture_map(pipe, info->src.resource, info->src.level,
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

        fprintf(stderr, "PANFORKSOFTBLIT: %ux%ue -> %ux%u bpp=%u ss=%u ds=%u\\n",
                sw, sh, (info->src.box.height < 0) ? "(flip)" : "",
                dw, dh, (info->dst.box.height < 0) ? "(flip)" : "",
                src_bpp, ss, ds);

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
