package net.gameoverse.skysync;

/**
 * Low-precision sun and moon positions (the main terms of Meeus, Astronomical
 * Algorithms, ch. 47) and the eclipse geometry built on them. Good to a few hundredths
 * of a degree around eclipses: checked against every solar and lunar eclipse of 2026-27
 * by {@code eclipse_check.py}, which runs the same series.
 */
public final class Astronomy {
    private static final double J2000_MS = 946_728_000_000L; // 2000-01-01 12:00 UTC
    private static final double OBLIQUITY = 23.44;
    private static final double SUN_RADIUS = 0.2666;
    private static final double SUN_PARALLAX = 0.00244;

    // {coefficient, D, M, M', F} for longitude (sin), latitude (sin) and parallax (cos)
    private static final double[][] LONGITUDE = {
        {6.288774, 0, 0, 1, 0}, {1.274027, 2, 0, -1, 0}, {0.658314, 2, 0, 0, 0}, {0.213618, 0, 0, 2, 0},
        {-0.185116, 0, 1, 0, 0}, {-0.114332, 0, 0, 0, 2}, {0.058793, 2, 0, -2, 0}, {0.057066, 2, -1, -1, 0},
        {0.053322, 2, 0, 1, 0}, {0.045758, 2, -1, 0, 0}, {-0.040923, 0, 1, -1, 0}, {-0.034720, 1, 0, 0, 0},
        {-0.030383, 0, 1, 1, 0}, {0.015327, 2, 0, 0, -2}, {-0.012528, 0, 0, 1, 2}, {0.010980, 0, 0, 1, -2},
        {0.010675, 4, 0, -1, 0}, {0.010034, 0, 0, 3, 0}, {0.008548, 4, 0, -2, 0}, {-0.007888, 2, 1, -1, 0},
        {-0.006766, 2, 1, 0, 0}, {-0.005163, 1, 0, -1, 0}, {0.004987, 1, 1, 0, 0}, {0.004036, 2, -1, 1, 0}};
    private static final double[][] LATITUDE = {
        {5.128122, 0, 0, 0, 1}, {0.280602, 0, 0, 1, 1}, {0.277693, 0, 0, 1, -1}, {0.173237, 2, 0, 0, -1},
        {0.055413, 2, 0, -1, 1}, {0.046271, 2, 0, -1, -1}, {0.032573, 2, 0, 0, 1}, {0.017198, 0, 0, 2, 1},
        {0.009266, 2, 0, 1, -1}, {0.008822, 0, 0, 2, -1}};
    private static final double[][] PARALLAX = {
        {0.950724, 0, 0, 0, 0}, {0.051818, 0, 0, 1, 0}, {0.009531, 2, 0, -1, 0}, {0.007843, 2, 0, 0, 0},
        {0.002824, 0, 0, 2, 0}};

    /** Everything about the sky at one moment. Angles in degrees. */
    public record Sky(double moonLongitude, double moonLatitude, double moonParallax, double sunLongitude) {
        /** How far the moon trails the sun eastward along the ecliptic: 0 new, 180 full. */
        public double elongation() {
            return mod360(moonLongitude - sunLongitude);
        }

        public double moonRadius() {
            return 0.2725 * moonParallax;
        }

        public double sunDeclination() {
            return Math.toDegrees(Math.asin(sin(OBLIQUITY) * sin(sunLongitude)));
        }

        public double moonDeclination() {
            return Math.toDegrees(Math.asin(sin(moonLatitude) * cos(OBLIQUITY)
                + cos(moonLatitude) * sin(OBLIQUITY) * sin(moonLongitude)));
        }

        /** Distance between the sun's and moon's centres seen from Earth's centre, in degrees. */
        public double sunSeparation() {
            return separation(moonLongitude, moonLatitude, sunLongitude, 0);
        }

        /** True if the moon's shadow touches Earth somewhere at this separation. */
        public boolean eclipseSomewhere(double separation) {
            return separation < moonParallax + SUN_RADIUS + moonRadius();
        }

        /**
         * Fraction of the sun's disc the moon covers for an observer on the eclipse's
         * centre line, given the smallest geocentric separation this eclipse reaches.
         * That observer sees the moon cross the sun along its track, so the local
         * separation is the along-track distance, plus however far the shadow's axis
         * misses Earth for a partial-only eclipse. Gives realistic timing: about two
         * hours of partial phases and a few minutes of totality (Aug 2 2027: peak 10:06
         * UTC, 6 minutes total; the real eclipse peaks 10:07 with 6m23s).
         */
        public double centralCoverage(double minimumSeparation) {
            if (!eclipseSomewhere(minimumSeparation)) {
                return 0.0;
            }
            double separation = sunSeparation();
            double along = Math.sqrt(Math.max(0, separation * separation - minimumSeparation * minimumSeparation));
            double miss = Math.max(0, minimumSeparation - moonParallax);
            return overlap(SUN_RADIUS, moonRadius(), Math.hypot(along, miss));
        }

        /** Central, annular and partial-only eclipses, from the smallest separation. */
        public String solarKind(double minimumSeparation) {
            if (minimumSeparation >= moonParallax) {
                return "partial";
            }
            return moonRadius() >= SUN_RADIUS ? "total" : "annular";
        }

        /** Umbral magnitude: above 0 the moon is partly in Earth's shadow, above 1 totally. */
        public double umbralMagnitude() {
            double separation = separation(moonLongitude, moonLatitude, sunLongitude + 180, 0);
            double umbra = 1.02 * (moonParallax - SUN_RADIUS + SUN_PARALLAX);
            return (umbra + moonRadius() - separation) / (2 * moonRadius());
        }

        /** Penumbral magnitude: above 0 the moon is in Earth's faint outer shadow. */
        public double penumbralMagnitude() {
            double separation = separation(moonLongitude, moonLatitude, sunLongitude + 180, 0);
            double penumbra = 1.02 * (moonParallax + SUN_RADIUS + SUN_PARALLAX);
            return (penumbra + moonRadius() - separation) / (2 * moonRadius());
        }
    }

    private Astronomy() {
    }

    public static Sky at(long epochMs) {
        double d = (epochMs - J2000_MS) / 86_400_000.0;
        double[] args = {
            297.8501921 + 12.19074912 * d,   // D, mean elongation
            357.5291092 + 0.98560028 * d,    // M, sun's mean anomaly
            134.9633964 + 13.06499295 * d,   // M', moon's mean anomaly
            93.2720950 + 13.22935024 * d};   // F, moon's argument of latitude
        double longitude = 218.3164477 + 13.17639648 * d + series(LONGITUDE, args, true);
        double latitude = series(LATITUDE, args, true);
        double parallax = series(PARALLAX, args, false);
        double sunLongitude = 280.46646 + 0.98564736 * d + 1.914602 * sin(args[1]) + 0.019993 * sin(2 * args[1]);
        return new Sky(mod360(longitude), latitude, parallax, mod360(sunLongitude));
    }

    private static double series(double[][] terms, double[] a, boolean sine) {
        double sum = 0;
        for (double[] t : terms) {
            double arg = t[1] * a[0] + t[2] * a[1] + t[3] * a[2] + t[4] * a[3];
            sum += t[0] * (sine ? sin(arg) : cos(arg));
        }
        return sum;
    }

    /** Angular distance between two ecliptic positions, in degrees. */
    private static double separation(double l1, double b1, double l2, double b2) {
        double c = sin(b1) * sin(b2) + cos(b1) * cos(b2) * cos(l1 - l2);
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c))));
    }

    /** Fraction of disc r1's area covered by disc r2 whose centre is d away. */
    static double overlap(double r1, double r2, double d) {
        if (d >= r1 + r2) {
            return 0.0;
        }
        if (d <= Math.abs(r2 - r1)) {
            return r2 >= r1 ? 1.0 : (r2 * r2) / (r1 * r1);
        }
        double a = r1 * r1 * Math.acos((d * d + r1 * r1 - r2 * r2) / (2 * d * r1))
            + r2 * r2 * Math.acos((d * d + r2 * r2 - r1 * r1) / (2 * d * r2))
            - 0.5 * Math.sqrt((-d + r1 + r2) * (d + r1 - r2) * (d - r1 + r2) * (d + r1 + r2));
        return a / (Math.PI * r1 * r1);
    }

    private static double sin(double degrees) {
        return Math.sin(Math.toRadians(degrees));
    }

    private static double cos(double degrees) {
        return Math.cos(Math.toRadians(degrees));
    }

    private static double mod360(double degrees) {
        double m = degrees % 360.0;
        return m < 0 ? m + 360.0 : m;
    }
}
