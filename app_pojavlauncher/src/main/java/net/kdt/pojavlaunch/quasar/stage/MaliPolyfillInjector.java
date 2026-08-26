package net.kdt.pojavlaunch.quasar.stage;

import net.kdt.pojavlaunch.quasar.capability.CapabilityTable;

/**
 * MaliPolyfillInjector provides polyfills for functionality missing on Mali GPUs.
 * This is used in Stage 4 (GPU Refinement) to add workarounds for Mali limitations.
 */
public class MaliPolyfillInjector {
    private static final String TAG = "Quasar-MaliPolyfill";

    /**
     * Inject Mali-specific polyfills into the shader source.
     * 
     * @param source The shader source code
     * @param capability The device capability table
     * @return The source with Mali polyfills injected
     */
    public static String injectPolyfills(String source, CapabilityTable capability) {
        if (source == null || capability == null) {
            return source;
        }

        String vendor = capability.getGpuVendor().toLowerCase();
        
        // Only apply Mali-specific polyfills
        if (!vendor.contains("mali")) {
            return source;
        }

        StringBuilder polyfills = new StringBuilder();
        
        // 1. gl_ClipDistance emulation (Mali lacks GL_EXT_clip_cull_distance)
        if (source.contains("gl_ClipDistance")) {
            polyfills.append(
                "// Mali gl_ClipDistance polyfill\n" +
                "#ifndef QUASAR_CLIP_DISTANCE_POLYFILL\n" +
                "#define QUASAR_CLIP_DISTANCE_POLYFILL\n" +
                "vec4 gl_ClipDistance = vec4(0.0);\n" +
                "#endif\n"
            );
        }

        // 2. Image load/store emulation (Mali has limited support)
        if (source.contains("imageLoad") || source.contains("imageStore")) {
            polyfills.append(
                "// Mali image load/store polyfill\n" +
                "#ifndef QUASAR_IMAGE_POLYFILL\n" +
                "#define QUASAR_IMAGE_POLYFILL\n" +
                "#define imageLoad(img, coord) texture(img, coord)\n" +
                "#define imageStore(img, coord, data) /* imageStore not supported on Mali */\n" +
                "#endif\n"
            );
        }

        // 3. Atomic operations emulation (Mali has limited atomic support)
        if (source.contains("atomicAdd") || source.contains("atomicCompSwap")) {
            polyfills.append(
                "// Mali atomic operations polyfill\n" +
                "#ifndef QUASAR_ATOMIC_POLYFILL\n" +
                "#define QUASAR_ATOMIC_POLYFILL\n" +
                "#define atomicAdd(mem, data) (mem += data, mem)\n" +
                "#define atomicCompSwap(mem, compare, data) (mem == compare ? (mem = data, mem) : compare)\n" +
                "#endif\n"
            );
        }

        // 4. Geometry shader emulation (Mali lacks geometry shaders in GLES)
        if (source.contains("gl_in") || source.contains("gl_PrimitiveID")) {
            polyfills.append(
                "// Mali geometry shader emulation\n" +
                "#ifndef QUASAR_GEOMETRY_POLYFILL\n" +
                "#define QUASAR_GEOMETRY_POLYFILL\n" +
                "#define gl_in gl_in_emulated\n" +
                "#define gl_PrimitiveID 0\n" +
                "#endif\n"
            );
        }

        // 5. Compute shader workgroup emulation
        if (source.contains("gl_WorkGroupID") || source.contains("gl_LocalInvocationID")) {
            polyfills.append(
                "// Mali compute shader emulation\n" +
                "#ifndef QUASAR_COMPUTE_POLYFILL\n" +
                "#define QUASAR_COMPUTE_POLYFILL\n" +
                "#ifndef gl_WorkGroupID\n" +
                "#define gl_WorkGroupID (gl_GlobalInvocationID / vec3(u_computeWorkGroupSize))\n" +
                "#endif\n" +
                "#ifndef gl_LocalInvocationID\n" +
                "#define gl_LocalInvocationID (gl_GlobalInvocationID % vec3(u_computeWorkGroupSize))\n" +
                "#endif\n" +
                "#endif\n"
            );
        }

        if (polyfills.length() > 0) {
            // Insert polyfills after the version line
            int versionIndex = source.indexOf("#version");
            if (versionIndex != -1) {
                int lineEnd = source.indexOf("\n", versionIndex);
                if (lineEnd != -1) {
                    source = source.substring(0, lineEnd + 1) + 
                            "\n" + polyfills.toString() + 
                            source.substring(lineEnd + 1);
                }
            } else {
                source = polyfills.toString() + "\n" + source;
            }
        }

        return source;
    }
}
