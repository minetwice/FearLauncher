package net.vulkanmod.mixin.render;

import net.vulkanmod.vulkan.FearPreRotation;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FEAR: the GUI's projection, turned for pre-rotation.
 *
 * <p>This is the one that matters - the title screen, its buttons and the HUD are what a player
 * cannot use when the image is a quarter turn out.
 *
 * <p>targets = the intermediary name on purpose. Importing the class meant guessing which package
 * Mojang mappings put it in, and the guess was wrong - the build failed with "cannot find symbol
 * ProjectionMatrix2" under net.minecraft.client.renderer. A string target has no import to get
 * wrong: it is resolved against whatever name the game actually loads, which is the intermediary
 * one the mappings give as class_11278.
 *
 * <p>getMatrix is where the ortho matrix is built before ProjectionMatrix2 packs it into a GPU
 * buffer, so it is the last point at which a Matrix4f exists. Both guiProjectionMatrix and
 * itemsProjectionMatrix are ProjectionMatrix2, so one hook covers both.
 */
@Mixin(targets = "net.minecraft.class_11278")
public abstract class FearGuiProjectionMixin {

    @Inject(method = "getMatrix", at = @At("RETURN"), cancellable = true)
    private void fearRotateGui(float width, float height, CallbackInfoReturnable<Matrix4f> cir) {
        cir.setReturnValue(FearPreRotation.apply(cir.getReturnValue()));
    }
}
