package net.vulkanmod.mixin.render;

import net.vulkanmod.vulkan.FearPreRotation;

import net.minecraft.client.renderer.GameRenderer;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FEAR: the world's projection, turned for pre-rotation.
 *
 * <p>method_22973 is getBasicProjectionMatrix(float) in intermediary. The config ships without a
 * refMap, so nothing translates the readable name at runtime and the first attempt failed with
 * "could not find any targets matching 'getBasicProjectionMatrix' in net.minecraft.class_757" -
 * the class was right, the method name was not.
 *
 * <p>The GUI hook already proved this is the pattern: class and method both have to be named the
 * way the game loads them.
 */
@Mixin(GameRenderer.class)
public abstract class FearWorldProjectionMixin {

    @Inject(method = "method_22973", at = @At("RETURN"), cancellable = true)
    private void fearRotateWorld(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        cir.setReturnValue(FearPreRotation.apply(cir.getReturnValue()));
    }
}
