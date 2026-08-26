package net.kdt.pojavlaunch.quasar.shield;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ShieldTransforms — 50 specialized, fast, pure-string GLSL transforms.
 * CPU-side only for Mali/Adreno GLES (Complementary, Solas, Iris packs).
 */
public final class ShieldTransforms {

    private ShieldTransforms() {}

    private static final Pattern P_EXT_LINE = Pattern.compile(
            "(?m)^\\s*#\\s*extension\\s+([A-Za-z0-9_]+)\\s*:\\s*(\\w+)\\s*;?\\s*$");
    private static final Pattern P_VERSION = Pattern.compile(
            "(?m)^\\s*#\\s*version\\s+(\\d+)(?:\\s+(core|compatibility|es))?\\s*$");
    private static final Pattern P_NOPERSPECTIVE = Pattern.compile("\\bnoperspective\\b");
    private static final Pattern P_SMOOTH = Pattern.compile("\\bsmooth\\s+");
    private static final Pattern P_CENTROID = Pattern.compile("\\bcentroid\\s+");
    private static final Pattern P_SAMPLE = Pattern.compile("\\bsample\\s+");
    private static final Pattern P_PRECISE = Pattern.compile("\\bprecise\\s+");
    private static final Pattern P_TEXTURE2D = Pattern.compile("\\btexture2D\\s*\\(");
    private static final Pattern P_TEXTURE2DLOD = Pattern.compile("\\btexture2DLod\\s*\\(");
    private static final Pattern P_TEXTURE2DGRAD = Pattern.compile("\\btexture2DGrad(?:ARB|EXT)?\\s*\\(");
    private static final Pattern P_TEXTURECUBE = Pattern.compile("\\btextureCube\\s*\\(");
    private static final Pattern P_TEXTURECUBELOD = Pattern.compile("\\btextureCubeLod\\s*\\(");
    private static final Pattern P_SHADOW2D = Pattern.compile("\\bshadow2D\\s*\\(");
    private static final Pattern P_SHADOW2DLOD = Pattern.compile("\\bshadow2DLod\\s*\\(");
    private static final Pattern P_TEXTURE3D = Pattern.compile("\\btexture3D\\s*\\(");
    private static final Pattern P_FTRANSFORM = Pattern.compile("\\bftransform\\s*\\(\\s*\\)");
    private static final Pattern P_ATTRIBUTE = Pattern.compile("(?m)^(\\s*)attribute\\s+");
    private static final Pattern P_VARYING = Pattern.compile("(?m)^(\\s*)varying\\s+");
    private static final Pattern P_DOUBLE = Pattern.compile("\\bdouble\\b");
    private static final Pattern P_DVEC = Pattern.compile("\\bdvec([234])\\b");
    private static final Pattern P_DMAT = Pattern.compile("\\bdmat([234])\\b");
    private static final Pattern P_UINT64 = Pattern.compile("\\buint64_t\\b");
    private static final Pattern P_INT64 = Pattern.compile("\\bint64_t\\b");
    private static final Pattern P_SUBROUTINE = Pattern.compile("\\bsubroutine\\b");
    private static final Pattern P_EARLY_TESTS = Pattern.compile(
            "layout\\s*\\(\\s*early_fragment_tests\\s*\\)");
    private static final Pattern P_BINDING = Pattern.compile(
            "layout\\s*\\([^)]*binding\\s*=\\s*\\d+[^)]*\\)");
    private static final Pattern P_SHARED = Pattern.compile("\\bshared\\s+");
    private static final Pattern P_COHERENT = Pattern.compile("\\bcoherent\\s+");
    private static final Pattern P_VOLATILE = Pattern.compile("\\bvolatile\\s+");
    private static final Pattern P_RESTRICT = Pattern.compile("\\brestrict\\s+");
    private static final Pattern P_READONLY = Pattern.compile("\\breadonly\\s+");
    private static final Pattern P_WRITEONLY = Pattern.compile("\\bwriteonly\\s+");
    private static final Pattern P_ATOMIC_UINT = Pattern.compile("\\batomic_uint\\b");
    private static final Pattern P_PATCH = Pattern.compile("\\bpatch\\s+");
    private static final Pattern P_PRECISION_FLOAT = Pattern.compile(
            "(?m)^\\s*precision\\s+(lowp|mediump|highp)\\s+float\\s*;");
    private static final Pattern P_PRECISION_INT = Pattern.compile(
            "(?m)^\\s*precision\\s+(lowp|mediump|highp)\\s+int\\s*;");
    private static final Pattern P_MULTIPLE_BLANK = Pattern.compile("\n{3,}");
    private static final Pattern P_TRAILING_WS = Pattern.compile("[ \\t]+$", Pattern.MULTILINE);

    public static String t01_stripHostileExtensions(String s, boolean gles) {
        Matcher m = P_EXT_LINE.matcher(s);
        StringBuffer sb = new StringBuffer(s.length());
        while (m.find()) {
            String name = m.group(1);
            if (ExtensionVault.shouldStrip(name, gles)) {
                m.appendReplacement(sb, "// Shield: stripped " + name);
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String t02_joinExtensions(String s) {
        Matcher m = P_EXT_LINE.matcher(s);
        StringBuffer sb = new StringBuffer(s.length());
        while (m.find()) {
            String name = m.group(1);
            String mode = m.group(2);
            String join = ExtensionVault.joinOrNull(name);
            if (join != null && !ExtensionVault.shouldStrip(name, true)) {
                m.appendReplacement(sb, "#extension " + join + " : " + mode);
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String t03_softenVersion(String s) {
        Matcher m = P_VERSION.matcher(s);
        if (m.find()) return m.replaceFirst("#version " + m.group(1));
        return s;
    }

    public static String t04_ensureVersion(String s) {
        if (P_VERSION.matcher(s).find()) return s;
        return "#version 330\n" + s;
    }

    public static String t05_midShaderExtensionComment(String s) {
        String[] lines = s.split("\n", -1);
        StringBuilder out = new StringBuilder(s.length());
        boolean seenCode = false;
        for (String line : lines) {
            String t = line.trim();
            if (!seenCode) {
                if (!t.isEmpty() && !t.startsWith("#") && !t.startsWith("//")) seenCode = true;
                out.append(line).append('\n');
            } else if (t.startsWith("#extension")) {
                out.append("// Shield mid-shader ext: ").append(line).append('\n');
            } else {
                out.append(line).append('\n');
            }
        }
        if (out.length() > 0 && out.charAt(out.length() - 1) == '\n') out.setLength(out.length() - 1);
        return out.toString();
    }

    public static String t06_requireToWarn(String s) {
        return s.replaceAll("(?m)^(\\s*#\\s*extension\\s+\\S+\\s*:\\s*)require\\b", "$1warn");
    }

    public static String t07_dropDisableNoise(String s) {
        return s.replaceAll("(?m)^\\s*#\\s*extension\\s+\\S+\\s*:\\s*disable\\s*;?\\s*$", "");
    }

    public static String t08_injectBanner(String s) {
        Matcher m = P_VERSION.matcher(s);
        if (m.find()) return m.replaceFirst(m.group(0) + "\n/* Quasar ShaderShield active */");
        return "/* Quasar ShaderShield active */\n" + s;
    }

    public static String t09_collapseExtComments(String s) {
        return s.replaceAll("(?m)(// Shield: stripped [^\\n]+\\n){2,}", "// Shield: stripped multiple desktop extensions\n");
    }

    public static String t10_guardGlEsDefines(String s) { return s; }

    public static String t11_noperspective(String s) {
        return P_NOPERSPECTIVE.matcher(s).replaceAll("/*noperspective*/");
    }

    public static String t12_flatSafe(String s) { return s; }

    public static String t13_smoothStrip(String s) {
        return P_SMOOTH.matcher(s).replaceAll("");
    }

    public static String t14_centroidStrip(String s) {
        return P_CENTROID.matcher(s).replaceAll("");
    }

    public static String t15_sampleStrip(String s) {
        return P_SAMPLE.matcher(s).replaceAll("");
    }

    public static String t16_preciseStrip(String s) {
        return P_PRECISE.matcher(s).replaceAll("");
    }

    public static String t17_attributeToIn(String s) {
        return P_ATTRIBUTE.matcher(s).replaceAll("$1in ");
    }

    public static String t18_varyingToOut(String s) {
        return P_VARYING.matcher(s).replaceAll("$1out ");
    }

    public static String t19_subroutineStrip(String s) {
        return P_SUBROUTINE.matcher(s).replaceAll("/*subroutine*/");
    }

    public static String t20_patchStrip(String s) {
        return P_PATCH.matcher(s).replaceAll("/*patch*/ ");
    }

    public static String t21_texture2D(String s) {
        return P_TEXTURE2D.matcher(s).replaceAll("texture(");
    }

    public static String t22_texture2DLod(String s) {
        return P_TEXTURE2DLOD.matcher(s).replaceAll("textureLod(");
    }

    public static String t23_texture2DGrad(String s) {
        return P_TEXTURE2DGRAD.matcher(s).replaceAll("textureGrad(");
    }

    public static String t24_textureCube(String s) {
        return P_TEXTURECUBE.matcher(s).replaceAll("texture(");
    }

    public static String t25_textureCubeLod(String s) {
        return P_TEXTURECUBELOD.matcher(s).replaceAll("textureLod(");
    }

    public static String t26_shadow2D(String s) {
        return P_SHADOW2D.matcher(s).replaceAll("texture(");
    }

    public static String t27_shadow2DLod(String s) {
        return P_SHADOW2DLOD.matcher(s).replaceAll("textureLod(");
    }

    public static String t28_texture3D(String s) {
        return P_TEXTURE3D.matcher(s).replaceAll("texture(");
    }

    public static String t29_ftransform(String s) {
        return P_FTRANSFORM.matcher(s).replaceAll("(gl_ModelViewProjectionMatrix * gl_Vertex)");
    }

    public static String t30_glFragColor(String s) { return s; }

    public static String t31_doubleToFloat(String s) {
        return P_DOUBLE.matcher(s).replaceAll("float");
    }

    public static String t32_dvec(String s) {
        return P_DVEC.matcher(s).replaceAll("vec$1");
    }

    public static String t33_dmat(String s) {
        return P_DMAT.matcher(s).replaceAll("mat$1");
    }

    public static String t34_uint64(String s) {
        return P_UINT64.matcher(s).replaceAll("uint");
    }

    public static String t35_int64(String s) {
        return P_INT64.matcher(s).replaceAll("int");
    }

    public static String t36_injectHighp(String s) {
        boolean hasF = P_PRECISION_FLOAT.matcher(s).find();
        boolean hasI = P_PRECISION_INT.matcher(s).find();
        if (hasF && hasI) return s;
        Matcher m = P_VERSION.matcher(s);
        if (!m.find()) return s;
        StringBuilder inject = new StringBuilder();
        inject.append(m.group(0)).append('\n');
        inject.append("#ifdef GL_ES\n");
        if (!hasF) inject.append("precision highp float;\n");
        if (!hasI) inject.append("precision highp int;\n");
        inject.append("#endif\n");
        return m.replaceFirst(Matcher.quoteReplacement(inject.toString()));
    }

    public static String t37_promoteMediump(String s) {
        return s.replaceAll("(?m)^\\s*precision\\s+mediump\\s+float\\s*;", "precision highp float;");
    }

    public static String t38_coherent(String s) {
        return P_COHERENT.matcher(s).replaceAll("");
    }

    public static String t39_volatileRestrict(String s) {
        s = P_VOLATILE.matcher(s).replaceAll("");
        return P_RESTRICT.matcher(s).replaceAll("");
    }

    public static String t40_rwOnly(String s) {
        s = P_READONLY.matcher(s).replaceAll("");
        return P_WRITEONLY.matcher(s).replaceAll("");
    }

    public static String t41_shared(String s) {
        return P_SHARED.matcher(s).replaceAll("/*shared*/ ");
    }

    public static String t42_image2D(String s) { return s; }

    public static String t43_atomicUint(String s) {
        return P_ATOMIC_UINT.matcher(s).replaceAll("uint");
    }

    public static String t44_earlyTests(String s) {
        return P_EARLY_TESTS.matcher(s).replaceAll("/*early_fragment_tests*/");
    }

    public static String t45_geoBuiltins(String s) { return s; }

    public static String t46_stripBindingOptional(String s, boolean aggressive) {
        if (!aggressive) return s;
        return P_BINDING.matcher(s).replaceAll("layout(/*binding stripped*/)");
    }

    public static String t47_fragData(String s) { return s; }

    public static String t48_collapseBlank(String s) {
        return P_MULTIPLE_BLANK.matcher(s).replaceAll("\n\n");
    }

    public static String t49_trimTrailing(String s) {
        return P_TRAILING_WS.matcher(s).replaceAll("");
    }

    public static String t50_finalize(String s) {
        if (s == null || s.isEmpty()) return "#version 330\nvoid main(){}\n";
        if (s.charAt(s.length() - 1) != '\n') return s + "\n";
        return s;
    }

    public static boolean needsShield(String s) {
        if (s == null || s.isEmpty()) return false;
        String lower = s.length() > 4096 ? s.substring(0, 4096) : s;
        return lower.contains("noperspective")
                || lower.contains("GL_NV_shader_noperspective")
                || lower.contains("GL_ARB_shader_texture_lod")
                || lower.contains("texture2D")
                || lower.contains("GL_ARB_gpu_shader5")
                || lower.contains("subroutine")
                || lower.contains("atomic_uint")
                || lower.contains("dvec")
                || lower.contains("double ");
    }
}
