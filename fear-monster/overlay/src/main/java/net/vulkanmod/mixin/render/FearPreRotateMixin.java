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
 * 1.20 - which is what makes this safe to inject. The matrix is only modified when
 * fear_prerotate.txt asks for it.
 */
@Mixin(RenderSystem.class)
public abstract class FearPreRotateMixin {

    @ModifyVariable(method = "setProjectionMatrix", at = @At("HEAD"), argsOnly = true)
    private static Matrix4f fearPreRotate(Matrix4f projectionMatrix) {
        return FearPreRotation.apply(projectionMatrix);
    }
}
