package net.vulkanmod.mixin.render;

import net.vulkanmod.vulkan.FearPreRotation;

import net.minecraft.client.render.GameRenderer;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FEAR: the world's projection, turned for pre-rotation.
 *
 * <p>getBasicProjectionMatrix is public and has kept that name in 1.21.11, which is what makes
 * this safe to inject - unlike RenderSystem.setProjectionMatrix, which now takes a packed
 * GpuBufferSlice and has no Matrix4f to modify at all.
 */
@Mixin(GameRenderer.class)
public abstract class FearWorldProjectionMixin {

    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void fearRotateWorld(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        cir.setReturnValue(FearPreRotation.apply(cir.getReturnValue()));
    }
}
