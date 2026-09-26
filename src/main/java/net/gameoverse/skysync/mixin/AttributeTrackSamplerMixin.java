package net.gameoverse.skysync.mixin;

import net.gameoverse.skysync.RealSky;
import net.gameoverse.skysync.SkyTrack;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.timeline.AttributeTrackSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feeds a tagged sampler the real sky's time instead of the world clock. */
@Mixin(AttributeTrackSampler.class)
public class AttributeTrackSamplerMixin implements SkyTrack.Tagged {
    @Unique
    private SkyTrack gameoverse$skyTrack = SkyTrack.CLOCK;

    @Unique
    private SkyTrack.EclipseEffect gameoverse$eclipseEffect = SkyTrack.EclipseEffect.NONE;

    @Override
    public void gameoverse$setSkyTrack(SkyTrack track) {
        gameoverse$skyTrack = track;
    }

    @Override
    public void gameoverse$setEclipseEffect(SkyTrack.EclipseEffect effect) {
        gameoverse$eclipseEffect = effect;
    }

    /** Darkens the sky during a solar eclipse (visual attributes only). */
    @Inject(method = "applyTimeBased", at = @At("RETURN"), cancellable = true)
    private void gameoverse$eclipse(Object value, int tickId, CallbackInfoReturnable<Object> cir) {
        if (gameoverse$eclipseEffect != SkyTrack.EclipseEffect.NONE) {
            cir.setReturnValue(gameoverse$eclipseEffect.apply(cir.getReturnValue()));
        }
    }

    @Redirect(method = "applyTimeBased", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/clock/ClockManager;getTotalTicks(Lnet/minecraft/core/Holder;)J"))
    private long gameoverse$realSkyTicks(ClockManager clockManager, Holder<WorldClock> clock) {
        return switch (gameoverse$skyTrack) {
            case MOON -> RealSky.moonTimelineTicks();
            case DAY -> RealSky.warpDayTicks(clockManager.getTotalTicks(clock));
            case CLOCK -> clockManager.getTotalTicks(clock);
        };
    }
}
