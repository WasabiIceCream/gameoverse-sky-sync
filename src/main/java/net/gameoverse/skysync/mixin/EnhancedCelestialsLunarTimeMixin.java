package net.gameoverse.skysync.mixin;

import net.gameoverse.skysync.RealSky;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enhanced Celestials works out the moon phase from the day count itself instead of
 * reading the game's, so its full-moon rules (the Super events) and forecast would
 * stay on the old 8-day cycle. Point both overloads at the real moon. Its forecast only
 * looks a few game days (about an hour of real time) ahead, so today's phase stands in
 * for those nights too. Optional: does nothing if Enhanced Celestials isn't installed.
 */
@Pseudo
@Mixin(targets = "dev.corgitaco.enhancedcelestials2core.util.LunarTime", remap = false)
public class EnhancedCelestialsLunarTimeMixin {
    @Inject(method = "moonPhase(J)I", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gameoverse$realMoonPhase(long dayTime, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RealSky.moonPhaseIndex());
    }

    @Inject(method = "moonPhase(Lnet/minecraft/world/level/Level;)I", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gameoverse$realMoonPhaseForLevel(Level level, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(RealSky.moonPhaseIndex());
    }
}
