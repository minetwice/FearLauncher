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
      /* FEARPATCH: the Gallium driver screen failed to initialise (Zink on
       * Mali). Without this guard the ->context_create() below dereferences
       * NULL and SIGSEGVs at [NULL + 0x5c0]. */
      *error = ST_CONTEXT_ERROR_NO_MEMORY;
      return NULL;
   }
   pipe = fscreen->screen->context_create(fscreen->screen, NULL,''',
     'FEARPATCH_ST_API_NULLGUARD'),

    # B) Mirror mesa_log() error output to stderr (one place, covers every
    #    mesa_loge in the tree) so Zink's "why did the screen fail" reason
    #    shows up in latestlog.txt - by default it only reaches logcat.
    ('src/util/log.c',
     r'   mesa_log_v[(]level, tag, format, va[)];',
     '''   mesa_log_v(level, tag, format, va);

   /* FEARPATCH: mirror error logs to stderr for the launcher latestlog.txt */
   if (level == MESA_LOG_ERROR || level == MESA_LOG_WARN) {
      char fear_buf[MAX_LOG_MESSAGE_LENGTH];
      va_list fear_va;
      va_start(fear_va, format);
      vsnprintf(fear_buf, sizeof(fear_buf), format, fear_va);
      va_end(fear_va);
      fprintf(stderr, "FEARPATCH MESA[%s]: %s", tag ? tag : "?", fear_buf);
      fputc(10, stderr);
      fflush(stderr);
   }''',
     'FEARPATCH_LOG_MIRROR'),

    # C) The Mali vendor driver has no VK_EXT_robustness2 nullDescriptor, but
    #    Zink hard-requires it, so screen init fails and OSMesaCreateContext
    #    returns NULL. Force it ON and continue instead of bailing out.
    ('src/gallium/drivers/zink/zink_screen.c',
     '''   if [(]!screen->info.rb2_feats.nullDescriptor[)] [{]
      mesa_loge[(]"Zink requires the nullDescriptor feature of KHR/EXT robustness2."[)];
      goto fail;
   [}]''',
     '''   if (!screen->info.rb2_feats.nullDescriptor) {
      /* FEARPATCH: Mali vendor driver lacks robustness2 nullDescriptor and
       * Zink hard-requires it, so screen init fails. Force it ON and continue. */
      fprintf(stderr, "FEARPATCH: Mali lacks robustness2 nullDescriptor - forcing it ON and continuing");
      fputc(10, stderr);
      fflush(stderr);
      screen->info.rb2_feats.nullDescriptor = VK_TRUE;
   }''',
     'FEARPATCH_NULLDESC_FORCE'),

    # D1) zink_types.h: add dummy-resource handles to struct zink_screen.
    ('src/gallium/drivers/zink/zink_types.h',
     '''      bool general_layout;
   } driver_workarounds;
};''',
     '''      bool general_layout;
   } driver_workarounds;

   /* FEARPATCH: dummy resources bound in place of NULL descriptors, which the
    * Mali vendor driver cannot handle (it crashes at NULL+0x28). */
   VkImage dummy_image;
   VkDeviceMemory dummy_image_mem;
   VkImageView dummy_image_view;
   VkBuffer dummy_buffer;
   VkDeviceMemory dummy_buffer_mem;
   VkBufferView dummy_buffer_view;
};''',
     'FEARPATCH_DUMMY_FIELDS'),

    # D2) zink_screen.c: create the dummies at screen init.
    ('src/gallium/drivers/zink/zink_screen.c',
     '''   screen->frame_marker_emitted = zink_screen_debug_marker_begin(screen, "frame");

   return screen;''',
     '''   screen->frame_marker_emitted = zink_screen_debug_marker_begin(screen, "frame");

   /* FEARPATCH: create dummy 1x1 image/buffer + views to stand in for NULL
    * descriptors (the Mali vendor driver crashes on NULL descriptors). */
   {
      VkImageCreateInfo ici = {
         .sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,
         .imageType = VK_IMAGE_TYPE_2D,
         .format = VK_FORMAT_R8G8B8A8_UNORM,
         .extent = { 1, 1, 1 },
         .mipLevels = 1,
         .arrayLayers = 1,
         .samples = VK_SAMPLE_COUNT_1_BIT,
         .tiling = VK_IMAGE_TILING_OPTIMAL,
         .usage = VK_IMAGE_USAGE_SAMPLED_BIT,
         .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
         .initialLayout = VK_IMAGE_LAYOUT_UNDEFINED,
      };
      if (VKSCR(CreateImage)(screen->dev, &ici, NULL, &screen->dummy_image) == VK_SUCCESS) {
         VkMemoryRequirements mr;
         VKSCR(GetImageMemoryRequirements)(screen->dev, screen->dummy_image, &mr);
         VkMemoryAllocateInfo mai = {
            .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
            .allocationSize = mr.size,
            .memoryTypeIndex = 0,
         };
         for (uint32_t i = 0; i < screen->info.mem_props.memoryTypeCount; i++) {
            if ((mr.memoryTypeBits & (1u << i)) &&
                (screen->info.mem_props.memoryTypes[i].propertyFlags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)) {
               mai.memoryTypeIndex = i;
               break;
            }
         }
         if (VKSCR(AllocateMemory)(screen->dev, &mai, NULL, &screen->dummy_image_mem) == VK_SUCCESS) {
            VKSCR(BindImageMemory)(screen->dev, screen->dummy_image, screen->dummy_image_mem, 0);
            VkImageViewCreateInfo ivci = {
               .sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
               .image = screen->dummy_image,
               .viewType = VK_IMAGE_VIEW_TYPE_2D,
               .format = VK_FORMAT_R8G8B8A8_UNORM,
               .subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 },
            };
            VKSCR(CreateImageView)(screen->dev, &ivci, NULL, &screen->dummy_image_view);
         }
      }
      VkBufferCreateInfo bci = {
         .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
         .size = 16,
         .usage = VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT,
         .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
      };
      if (VKSCR(CreateBuffer)(screen->dev, &bci, NULL, &screen->dummy_buffer) == VK_SUCCESS) {
         VkMemoryRequirements mr;
         VKSCR(GetBufferMemoryRequirements)(screen->dev, screen->dummy_buffer, &mr);
         VkMemoryAllocateInfo mai = {
            .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
            .allocationSize = mr.size,
            .memoryTypeIndex = 0,
         };
         for (uint32_t i = 0; i < screen->info.mem_props.memoryTypeCount; i++) {
            if (mr.memoryTypeBits & (1u << i)) {
               mai.memoryTypeIndex = i;
               break;
            }
         }
         if (VKSCR(AllocateMemory)(screen->dev, &mai, NULL, &screen->dummy_buffer_mem) == VK_SUCCESS) {
            VKSCR(BindBufferMemory)(screen->dev, screen->dummy_buffer, screen->dummy_buffer_mem, 0);
            VkBufferViewCreateInfo bvci = {
               .sType = VK_STRUCTURE_TYPE_BUFFER_VIEW_CREATE_INFO,
               .buffer = screen->dummy_buffer,
               .format = VK_FORMAT_R8G8B8A8_UNORM,
               .offset = 0,
               .range = VK_WHOLE_SIZE,
            };
            VKSCR(CreateBufferView)(screen->dev, &bvci, NULL, &screen->dummy_buffer_view);
         }
      }
      fprintf(stderr, "FEARPATCH: dummy resources ready (imgview=%p bufview=%p)",
              (void *)screen->dummy_image_view, (void *)screen->dummy_buffer_view);
      fputc(10, stderr);
      fflush(stderr);
   }

   return screen;''',
     'FEARPATCH_DUMMY_CREATE'),

    # D3) zink_screen.c: destroy the dummies.
    ('src/gallium/drivers/zink/zink_screen.c',
     '''      VKSCR(DestroyDescriptorSetLayout)(screen->dev, screen->bindless_layout, NULL);

   if (screen->dev) {''',
     '''      VKSCR(DestroyDescriptorSetLayout)(screen->dev, screen->bindless_layout, NULL);

   /* FEARPATCH: destroy the dummy null-descriptor stand-ins. */
   if (screen->dev) {
      if (screen->dummy_buffer_view) VKSCR(DestroyBufferView)(screen->dev, screen->dummy_buffer_view, NULL);
      if (screen->dummy_buffer) VKSCR(DestroyBuffer)(screen->dev, screen->dummy_buffer, NULL);
      if (screen->dummy_buffer_mem) VKSCR(FreeMemory)(screen->dev, screen->dummy_buffer_mem, NULL);
      if (screen->dummy_image_view) VKSCR(DestroyImageView)(screen->dev, screen->dummy_image_view, NULL);
      if (screen->dummy_image) VKSCR(DestroyImage)(screen->dev, screen->dummy_image, NULL);
      if (screen->dummy_image_mem) VKSCR(FreeMemory)(screen->dev, screen->dummy_image_mem, NULL);
   }

   if (screen->dev) {''',
     'FEARPATCH_DUMMY_DESTROY'),

    # D4) zink_context.c: bind the dummy instead of NULL for unbound slots.
    ('src/gallium/drivers/zink/zink_context.c',
     '''      ctx->di.textures[shader][slot].imageView = VK_NULL_HANDLE;
      ctx->di.textures[shader][slot].imageLayout = VK_IMAGE_LAYOUT_UNDEFINED;''',
     '''      /* FEARPATCH: never hand the Mali driver a NULL descriptor (it crashes
       * at NULL+0x28); bind a dummy 1x1 resource instead. */
      ctx->di.textures[shader][slot].imageView = zink_screen(ctx->base.screen)->dummy_image_view;
      ctx->di.textures[shader][slot].imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;''',
     'FEARPATCH_DUMMY_TEX'),

    # D5) zink_context.c: same for the texel-buffer (tbos) null path.
    ('src/gallium/drivers/zink/zink_context.c',
     '''         ctx->di.t.tbos[shader][slot] = VK_NULL_HANDLE;''',
     '''         ctx->di.t.tbos[shader][slot] = zink_screen(ctx->base.screen)->dummy_buffer_view;''',
     'FEARPATCH_DUMMY_TBO'),

    # E1) zink_types.h: also need a dummy sampler handle.
    ('src/gallium/drivers/zink/zink_types.h',
     '''   VkBufferView dummy_buffer_view;
};''',
     '''   VkBufferView dummy_buffer_view;
   VkSampler dummy_sampler;
};''',
     'FEARPATCH_DUMMY_SAMPLER_FIELD'),

    # E2) zink_screen.c: create the dummy sampler.
    ('src/gallium/drivers/zink/zink_screen.c',
     '''      fprintf(stderr, "FEARPATCH: dummy resources ready (imgview=%p bufview=%p)",''',
     '''      VkSamplerCreateInfo sci = {
         .sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO,
         .magFilter = VK_FILTER_NEAREST,
         .minFilter = VK_FILTER_NEAREST,
         .mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST,
         .addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
         .addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
         .addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
         .maxLod = 0.0f,
      };
      VKSCR(CreateSampler)(screen->dev, &sci, NULL, &screen->dummy_sampler);
      fprintf(stderr, "FEARPATCH: dummy resources ready (imgview=%p bufview=%p) sampler=%p",''',
     'FEARPATCH_DUMMY_SAMPLER_CREATE'),

    # E2b) zink_screen.c: the fprintf now has 3 %p - supply the sampler arg too.
    ('src/gallium/drivers/zink/zink_screen.c',
     '''              (void *)screen->dummy_image_view, (void *)screen->dummy_buffer_view);''',
     '''              (void *)screen->dummy_image_view, (void *)screen->dummy_buffer_view,
              (void *)screen->dummy_sampler);''',
     'FEARPATCH_DUMMY_LOG_ARGS'),

    # E3) zink_screen.c: destroy the dummy sampler.
    ('src/gallium/drivers/zink/zink_screen.c',
     '''      if (screen->dummy_buffer_view) VKSCR(DestroyBufferView)(screen->dev, screen->dummy_buffer_view, NULL);''',
     '''      if (screen->dummy_sampler) VKSCR(DestroySampler)(screen->dev, screen->dummy_sampler, NULL);
      if (screen->dummy_buffer_view) VKSCR(DestroyBufferView)(screen->dev, screen->dummy_buffer_view, NULL);''',
     'FEARPATCH_DUMMY_SAMPLER_DESTROY'),

    # E4) zink_context.c: bind the dummy sampler for unbound sampler slots.
    ('src/gallium/drivers/zink/zink_context.c',
     '''         ctx->di.textures[shader][start_slot + i].sampler = VK_NULL_HANDLE;''',
     '''         ctx->di.textures[shader][start_slot + i].sampler = zink_screen(ctx->base.screen)->dummy_sampler;''',
     'FEARPATCH_DUMMY_SAMPLER_BIND'),

    # E5) zink_context.c: dummy image view for the texel-buffer image path.
    ('src/gallium/drivers/zink/zink_context.c',
     '''         ctx->di.t.texel_images[shader][slot] = VK_NULL_HANDLE;''',
     '''         ctx->di.t.texel_images[shader][slot] = zink_screen(ctx->base.screen)->dummy_buffer_view;''',
     'FEARPATCH_DUMMY_TEXELIMG'),

    # E6) zink_context.c: the storage-image path memsets the whole descriptor
    # (imageView = NULL); give it the dummy view + a valid layout.
    ('src/gallium/drivers/zink/zink_context.c',
     '''      memset(&ctx->di.images[shader][slot], 0, sizeof(ctx->di.images[shader][slot]));''',
     '''      memset(&ctx->di.images[shader][slot], 0, sizeof(ctx->di.images[shader][slot]));
      ctx->di.images[shader][slot].imageView = zink_screen(ctx->base.screen)->dummy_image_view;
      ctx->di.images[shader][slot].imageLayout = VK_IMAGE_LAYOUT_GENERAL;''',
     'FEARPATCH_DUMMY_STOREIMG'),

    # F1) zink_screen.c: the dummy buffer must also be usable as a UBO/SSBO.
    ('src/gallium/drivers/zink/zink_screen.c',
     '''         .size = 16,
         .usage = VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT,''',
     '''         .size = 256,
         .usage = VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_UNIFORM_TEXEL_BUFFER_BIT,''',
     'FEARPATCH_DUMMY_BUF_USAGE'),

    # F2) zink_context.c: unbound UBO slots wrote a NULL buffer.
    ('src/gallium/drivers/zink/zink_context.c',
     '''      ctx->di.t.ubos[shader][slot].buffer = VK_NULL_HANDLE;
      ctx->di.t.ubos[shader][slot].range = VK_WHOLE_SIZE;''',
     '''      ctx->di.t.ubos[shader][slot].buffer = zink_screen(ctx->base.screen)->dummy_buffer;
      ctx->di.t.ubos[shader][slot].offset = 0;
      ctx->di.t.ubos[shader][slot].range = VK_WHOLE_SIZE;''',
     'FEARPATCH_DUMMY_UBO'),

    # F3) zink_context.c: unbound SSBO slots wrote a NULL buffer.
    ('src/gallium/drivers/zink/zink_context.c',
     '''      ctx->di.t.ssbos[shader][slot].buffer = VK_NULL_HANDLE;
      ctx->di.t.ssbos[shader][slot].range = VK_WHOLE_SIZE;''',
     '''      ctx->di.t.ssbos[shader][slot].buffer = zink_screen(ctx->base.screen)->dummy_buffer;
      ctx->di.t.ssbos[shader][slot].offset = 0;
      ctx->di.t.ssbos[shader][slot].range = VK_WHOLE_SIZE;''',
     'FEARPATCH_DUMMY_SSBO'),
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
    if pat in s:
        new = s.replace(pat, repl, 1)
        n = 1
    else:
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
