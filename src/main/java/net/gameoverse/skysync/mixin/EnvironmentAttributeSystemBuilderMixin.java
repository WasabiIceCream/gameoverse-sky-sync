package net.gameoverse.skysync.mixin;

import net.gameoverse.skysync.SkyTrack;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.timeline.AttributeTrackSampler;
import net.minecraft.world.timeline.Timeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tags each timeline track sampler with where its time should come from. This is the
 * one place a sampler is created with its timeline's registry key in reach.
 */
@Mixin(EnvironmentAttributeSystem.Builder.class)
public class EnvironmentAttributeSystemBuilderMixin {
    private static final Identifier MOON = Identifier.withDefaultNamespace("moon");
    private static final Identifier DAY = Identifier.withDefaultNamespace("day");
    private static final Identifier VILLAGER_SCHEDULE = Identifier.withDefaultNamespace("villager_schedule");

    @Redirect(method = "addTimelineLayerForAttribute", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/timeline/Timeline;createTrackSampler(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/world/clock/ClockManager;)Lnet/minecraft/world/timeline/AttributeTrackSampler;"))
    private AttributeTrackSampler<?, ?> gameoverse$tagSampler(Timeline timeline, EnvironmentAttribute<?> attribute,
            ClockManager clockManager, Holder<Timeline> holder, EnvironmentAttribute<?> sameAttribute,
            ClockManager sameClockManager) {
        AttributeTrackSampler<?, ?> sampler = timeline.createTrackSampler(attribute, clockManager);
        Identifier id = holder.unwrapKey().map(key -> key.identifier()).orElse(null);
        if (MOON.equals(id)) {
            ((SkyTrack.Tagged) sampler).gameoverse$setSkyTrack(SkyTrack.MOON);
        } else if (DAY.equals(id) || VILLAGER_SCHEDULE.equals(id)) {
            ((SkyTrack.Tagged) sampler).gameoverse$setSkyTrack(SkyTrack.DAY);
        }
        if (DAY.equals(id)) {
            SkyTrack.EclipseEffect effect = attribute == EnvironmentAttributes.SKY_COLOR ? SkyTrack.EclipseEffect.SKY_COLOR
                : attribute == EnvironmentAttributes.FOG_COLOR ? SkyTrack.EclipseEffect.FOG_COLOR
                : attribute == EnvironmentAttributes.CLOUD_COLOR ? SkyTrack.EclipseEffect.CLOUD_COLOR
                : attribute == EnvironmentAttributes.SKY_LIGHT_FACTOR ? SkyTrack.EclipseEffect.SKY_LIGHT_FACTOR
                : attribute == EnvironmentAttributes.STAR_BRIGHTNESS ? SkyTrack.EclipseEffect.STAR_BRIGHTNESS
                : SkyTrack.EclipseEffect.NONE;
            ((SkyTrack.Tagged) sampler).gameoverse$setEclipseEffect(effect);
        }
        return sampler;
    }
}
