# data part 3 (anchors for pan_blit.c blit call + blit.c color loop)
OLD_BLIT = '''        if (info->render_condition_enable &&
            !panfrost_render_condition_check(ctx))
                return;

        if (!util_blitter_is_blit_supported(ctx->blitter, info))
                unreachable("Unsupported blit\\n");'''

NEW_BLIT = '''        if (info->render_condition_enable &&
            !panfrost_render_condition_check(ctx))
                return;

        if (panfrost_soft_blit(pipe, info))
                return;

        if (!util_blitter_is_blit_supported(ctx->blitter, info))
                unreachable("Unsupported blit\\n");'''

OLD_DST = '''         if (dstSurf) {
               blit.dst.resource = dstSurf->texture;
               blit.dst.level = dstSurf->u.tex.level;
               blit.dst.box.z = dstSurf->u.tex.first_layer;
               blit.dst.format = dstSurf->format;

               ctx->pipe->blit(ctx->pipe, &blit);
               dstRb->defined = true; /* front buffer tracking */
            }'''

NEW_DST = '''         if (dstSurf) {
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
