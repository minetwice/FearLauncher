package com.fear.skin.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * The launcher's own authlib patch, without shipping anyone else's library.
 *
 * authlib-injector already rewrites authlib at load time; this is a second, much smaller
 * transformer alongside it. It targets the one class that turns a GameProfile into a
 * session-server request and adds a single call at the top of the lookup methods, handing
 * the profile - name and all - to FearSkinBridge.
 *
 * It is written to give up quietly. A method that is not there, a class that does not
 * match, a version where authlib moved things around: all of them leave the original
 * bytecode untouched rather than risk a launch.
 */
public final class FearSkinAgent {

    /** The session service that owns the profile lookup. */
    private static final String TARGET = "com/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService";

    private static final String PROFILE_DESC = "Lcom/mojang/authlib/GameProfile;";

    private FearSkinAgent() {}

    public static void premain(String args, Instrumentation instrumentation) {
        try {
            instrumentation.addTransformer(new Transformer(), false);
            log("installed");
        } catch (Throwable t) {
            log("could not install: " + t);
        }
    }

    static final class Transformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> beingRedefined,
                                ProtectionDomain domain, byte[] classfileBuffer) {
            if (!TARGET.equals(className)) return null;
            try {
                ClassReader reader = new ClassReader(classfileBuffer);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                final boolean[] hooked = { false };
                ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc,
                                                     String signature, String[] exceptions) {
                        MethodVisitor next = super.visitMethod(access, name, desc, signature, exceptions);
                        // Hook any method that takes a GameProfile as its first
                        // argument. The lookup method was renamed across authlib
                        // versions (fillProfileProperties/getTextures in old authlib,
                        // fetchProfile(GameProfile, boolean) in modern authlib), so
                        // matching by name silently stopped working and the agent
                        // gave up with "no lookup method found". note() only reads the
                        // profile's id and name, so hooking every profile-taking
                        // method is safe.
                        boolean takesProfile = desc.startsWith("(" + PROFILE_DESC);
                        if (!takesProfile) return next;
                        hooked[0] = true;
                        final boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
                        return new MethodVisitor(Opcodes.ASM9, next) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                visitVarInsn(Opcodes.ALOAD, isStatic ? 0 : 1);
                                visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "com/fear/skin/agent/FearSkinBridge", "note",
                                        "(Ljava/lang/Object;)V", false);
                            }
                        };
                    }
                };
                reader.accept(visitor, ClassReader.EXPAND_FRAMES);
                if (!hooked[0]) {
                    log("no lookup method found; leaving authlib alone");
                    return null;
                }
                log("patched " + className);
                return writer.toByteArray();
            } catch (Throwable t) {
                log("failed, leaving authlib alone: " + t);
                return null;
            }
        }
    }

    static void log(String message) {
        System.out.println("[FearSkinAgent] " + message);
    }
}
