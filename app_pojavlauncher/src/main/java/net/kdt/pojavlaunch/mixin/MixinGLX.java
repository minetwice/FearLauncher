package net.kdt.pojavlaunch.mixin;

import net.kdt.pojavlaunch.utils.GLFWVulkanInitializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mojang.blaze3d.platform.GLX")
public class MixinGLX {
    
    @Inject(method = "_initGlfw", at = @At("HEAD"))
    private static void onInitGlfw(CallbackInfo ci) {
        GLFWVulkanInitializer.initGLFWForVulkan();
    }
}