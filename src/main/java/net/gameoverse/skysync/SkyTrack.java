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

        void gameoverse$setEclipseEffect(EclipseEffect effect);
    }

    /** Visual day-timeline attributes a solar eclipse darkens. */
    public enum EclipseEffect {
        NONE, SKY_COLOR, FOG_COLOR, CLOUD_COLOR, SKY_LIGHT_FACTOR, STAR_BRIGHTNESS;

        /** The eclipse-adjusted value of this attribute (only visual ones, never gameplay). */
        public Object apply(Object value) {
            double d = RealSky.solarDarkness();
            if (this == NONE || d <= 0) {
                return value;
            }
            return switch (this) {
                case SKY_LIGHT_FACTOR -> (Float) value * (float) (1 - 0.8 * d);
                case STAR_BRIGHTNESS -> Math.max((Float) value, (float) (0.5 * Math.max(0, (d - 0.7) / 0.3)));
                case SKY_COLOR, FOG_COLOR, CLOUD_COLOR -> darken((Integer) value, 0.85 * d);
                case NONE -> value;
            };
        }

        /** Blends an ARGB colour toward a deep twilight blue. */
        private static int darken(int argb, double amount) {
            int target = 0x0F1426;
            int r = mix(argb >> 16 & 0xFF, target >> 16 & 0xFF, amount);
            int g = mix(argb >> 8 & 0xFF, target >> 8 & 0xFF, amount);
            int b = mix(argb & 0xFF, target & 0xFF, amount);
            return argb & 0xFF000000 | r << 16 | g << 8 | b;
        }

        private static int mix(int from, int to, double amount) {
            return (int) Math.round(from + (to - from) * amount);
        }
    }
}
