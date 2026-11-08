package net.vulkanmod.mixin.render;

import net.vulkanmod.vulkan.FearPreRotation;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * FEAR: turns every projection the game installs, world and GUI alike.
 *
 * <p>RenderSystem.setProjectionMatrix is the single point both of them go through, and unlike
 * the projection builders on GameRenderer it has kept this name across every version since
 * 1.20 - which is what makes this safe to inject.
 *
 * <p>index = 0 is not optional. The method is static, so its first argument is slot zero, and
 * without it Mixin cannot tell which local to modify: it reported "Found 0 candidate variables
 * but exactly 1 is required" and refused to apply. With it, the Matrix4f is unambiguous.
 */
@Mixin(RenderSystem.class)
public abstract class FearPreRotateMixin {

    @ModifyVariable(method = "setProjectionMatrix", at = @At("HEAD"), argsOnly = true, index = 0)
    private static Matrix4f fearPreRotate(Matrix4f projectionMatrix) {
        return FearPreRotation.apply(projectionMatrix);
    }
}
