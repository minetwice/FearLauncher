package net.kdt.pojavlaunch.quasar.gl.dsa;

import android.util.Log;

/**
 * Direct State Access (GL 4.5 / ARB_direct_state_access) emulator for GLES.
 * named* calls → bind target → classical op → restore.
 */
public final class DSAEmulator {
    private static final String TAG = "Quasar-DSA";

    public enum NamedTarget {
        BUFFER_ARRAY(0x8892),
        BUFFER_ELEMENT(0x8893),
        BUFFER_UNIFORM(0x8A11),
        BUFFER_SSBO(0x90D2),
        BUFFER_COPY_READ(0x8F36),
        BUFFER_COPY_WRITE(0x8F37),
        BUFFER_PIXEL_PACK(0x88EB),
        BUFFER_PIXEL_UNPACK(0x88EC),
        BUFFER_TRANSFORM(0x8C8E),
        TEXTURE_2D(0x0DE1),
        TEXTURE_3D(0x806F),
        TEXTURE_2D_ARRAY(0x8C1A),
        TEXTURE_CUBE(0x8513),
        FRAMEBUFFER_DRAW(0x8CA9),
        FRAMEBUFFER_READ(0x8CA8),
        FRAMEBUFFER(0x8D40),
        RENDERBUFFER(0x8D41),
        VERTEX_ARRAY(0x85B5);

        public final int glEnum;
        NamedTarget(int glEnum) { this.glEnum = glEnum; }
    }

    private static final ThreadLocal<int[]> BIND_STACK = ThreadLocal.withInitial(() -> new int[16]);
    private static final ThreadLocal<Integer> STACK_TOP = ThreadLocal.withInitial(() -> 0);

    private DSAEmulator() {}

    public static void markActive() {
        Log.i(TAG, "DSA emulator path active — named* calls will bind-then-op on GLES");
    }

    public static String classicalSequence(String dsaFunction) {
        if (dsaFunction == null) return "unknown";
        switch (dsaFunction) {
            case "glCreateBuffers": return "glGenBuffers";
            case "glNamedBufferData":
                return "glBindBuffer(target,id); glBufferData(...); glBindBuffer(target,prev)";
            case "glNamedBufferSubData": return "glBindBuffer; glBufferSubData; restore";
            case "glMapNamedBufferRange": return "glBindBuffer; glMapBufferRange; restore";
            case "glCreateTextures": return "glGenTextures";
            case "glTextureStorage2D":
                return "glBindTexture(TEXT_2D,id); glTexStorage2D; restore";
            case "glTextureSubImage2D": return "glBindTexture; glTexSubImage2D; restore";
            case "glTextureParameteri": return "glBindTexture; glTexParameteri; restore";
            case "glCreateFramebuffers": return "glGenFramebuffers";
            case "glNamedFramebufferTexture":
                return "glBindFramebuffer; glFramebufferTexture2D; restore";
            case "glNamedFramebufferDrawBuffers":
                return "glBindFramebuffer; glDrawBuffers; restore";
            case "glCreateVertexArrays": return "glGenVertexArrays";
            case "glEnableVertexArrayAttrib":
                return "glBindVertexArray; glEnableVertexAttribArray; restore";
            case "glVertexArrayVertexBuffer":
                return "glBindVertexArray; glBindBuffer; glVertexAttribPointer; restore";
            default:
                return "bind-target; classical; restore [" + dsaFunction + "]";
        }
    }

    public static void pushBinding(int name) {
        int[] st = BIND_STACK.get();
        int top = STACK_TOP.get();
        if (top < st.length) {
            st[top] = name;
            STACK_TOP.set(top + 1);
        }
    }

    public static int popBinding() {
        int top = STACK_TOP.get();
        if (top <= 0) return 0;
        top--;
        STACK_TOP.set(top);
        return BIND_STACK.get()[top];
    }
}
