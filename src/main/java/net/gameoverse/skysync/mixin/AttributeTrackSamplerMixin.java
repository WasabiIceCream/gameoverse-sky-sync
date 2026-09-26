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
import org.spongepowered.asm.mixin.injection.Redirect;

/** Feeds a tagged sampler the real sky's time instead of the world clock. */
@Mixin(AttributeTrackSampler.class)
public class AttributeTrackSamplerMixin implements SkyTrack.Tagged {
    @Unique
    private SkyTrack gameoverse$skyTrack = SkyTrack.CLOCK;

    @Override
    public void gameoverse$setSkyTrack(SkyTrack track) {
        gameoverse$skyTrack = track;
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
