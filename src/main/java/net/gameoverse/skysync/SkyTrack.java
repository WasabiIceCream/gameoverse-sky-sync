package net.gameoverse.skysync;

/** Which real-sky source a timeline track sampler reads instead of the world clock. */
public enum SkyTrack {
    /** Untouched: the world clock, as vanilla. */
    CLOCK,
    /** The real moon phase ({@code minecraft:moon}: phase and surface slime chance). */
    MOON,
    /** The world clock warped to today's daylight length ({@code minecraft:day}, villager schedule). */
    DAY;

    /** Implemented on {@code AttributeTrackSampler} by mixin. */
    public interface Tagged {
        void gameoverse$setSkyTrack(SkyTrack track);
    }
}
