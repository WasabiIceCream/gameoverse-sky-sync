package net.gameoverse.skysync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The real sky, from the machine's clock: the sun's and moon's positions (see
 * {@link Astronomy}), the moon's phase, how long each is above the horizon at the
 * configured latitude, and any eclipse under way. A client and the server agree as long
 * as their clocks are roughly right.
 */
public final class RealSky {
    public static final Logger LOGGER = LoggerFactory.getLogger("gameoverse_sky_sync");

    /** Mean synodic month, in days. */
    public static final double SYNODIC_MONTH = 29.530588853;
    /** March equinox, the start of the year for the sun's tilt and seasonal events. */
    private static final MonthDay MARCH_EQUINOX = MonthDay.of(3, 20);

    public static final int DAY_TICKS = 24000;
    private static final int NOON = 6000;
    private static final int HALF_DAY = 12000;

    private static double latitude = 40.8;
    /** Testing only: a fixed daylight fraction instead of today's (config "daylight_override"). */
    private static double daylightOverride = Double.NaN;
    /** Testing only: a fixed solar declination in degrees (config "declination_override"). */
    private static double declinationOverride = Double.NaN;
    /**
     * Testing only (config "sky_time_override", an ISO instant like 2027-08-02T08:40:00Z):
     * the sky starts at that moment when the game starts and runs on from there.
     */
    private static long timeOffsetMs;

    static {
        loadConfig();
    }

    private static volatile long cacheExpiresMs;
    private static volatile double cachedDaylight = 0.5;
    private static volatile long skyExpiresMs;
    private static volatile Astronomy.Sky cachedSky;

    private RealSky() {
    }

    public static double latitude() {
        return latitude;
    }

    /** The sky's "now": the real time, or the testing override's moving time. */
    public static long now() {
        return System.currentTimeMillis() + timeOffsetMs;
    }

    /** The sun and moon right now, recomputed at most once a second. */
    public static Astronomy.Sky sky() {
        long wall = System.currentTimeMillis();
        Astronomy.Sky sky = cachedSky;
        if (sky == null || wall >= skyExpiresMs) {
            sky = Astronomy.at(now());
            cachedSky = sky;
            skyExpiresMs = wall + 1000L;
        }
        return sky;
    }

    private static LocalDate today() {
        return Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** Overworld clock ticks at {@link #clockSampleMs}, recorded each server tick. */
    private static volatile long clockSampleTicks = Long.MIN_VALUE;
    private static volatile long clockSampleMs;

    /** Called every server tick with the overworld's clock, to place future ticks in real time. */
    public static void recordClock(long ticks) {
        clockSampleTicks = ticks;
        clockSampleMs = now();
    }

    /**
     * The real time a given overworld clock tick falls at, assuming 20 ticks per second
     * from the last recorded tick. Now, if no tick has been recorded (e.g. on a client).
     */
    public static long realTimeOfTick(long ticks) {
        long sample = clockSampleTicks;
        if (sample == Long.MIN_VALUE) {
            return now();
        }
        return clockSampleMs + (ticks - sample) * 50L;
    }

    /** Days since the last new moon, 0 to {@link #SYNODIC_MONTH}. */
    public static double lunarAge() {
        return sky().elongation() / 360.0 * SYNODIC_MONTH;
    }

    /** Days since the last new moon at the given real time, from the true elongation. */
    public static double lunarAgeAt(long epochMs) {
        return Astronomy.at(epochMs).elongation() / 360.0 * SYNODIC_MONTH;
    }

    /** Whole lunar day, 0 (new moon) to 29. */
    public static int lunarDay() {
        return (int) Math.floor(lunarAge());
    }

    /**
     * Today's phase as a vanilla {@code MoonPhase} index (0 full, 1 waning gibbous,
     * 2 third quarter, 3 waning crescent, 4 new, 5 waxing crescent, 6 first quarter,
     * 7 waxing gibbous). The four principal phases each last about two days around the
     * exact moment, the way they look in the real sky.
     */
    public static int moonPhaseIndex() {
        return phaseIndexForAge(lunarAge());
    }

    /** {@link #moonPhaseIndex()} at the given real time. */
    public static int moonPhaseIndexAt(long epochMs) {
        return phaseIndexForAge(lunarAgeAt(epochMs));
    }

    private static int phaseIndexForAge(double age) {
        double q = SYNODIC_MONTH / 4;
        if (age < 1 || age >= SYNODIC_MONTH - 1) return 4;
        if (age < q - 1) return 5;
        if (age < q + 1) return 6;
        if (age < 2 * q - 1) return 7;
        if (age < 2 * q + 1) return 0;
        if (age < 3 * q - 1) return 1;
        if (age < 3 * q + 1) return 2;
        return 3;
    }

    /** Position on the vanilla moon timeline (8 days of 24000 ticks) for today's phase. */
    public static long moonTimelineTicks() {
        return moonPhaseIndex() * (long) DAY_TICKS + HALF_DAY;
    }

    /** Days since 1970-01-01 by the local calendar. */
    public static long epochDay() {
        return today().toEpochDay();
    }

    /** Days since the most recent March equinox, 0 to 365. */
    public static int dayOfSolarYear() {
        LocalDate today = today();
        LocalDate equinox = MARCH_EQUINOX.atYear(today.getYear());
        if (today.isBefore(equinox)) {
            equinox = MARCH_EQUINOX.atYear(today.getYear() - 1);
        }
        return (int) (today.toEpochDay() - equinox.toEpochDay());
    }

    /** The sun's declination right now, in degrees. */
    public static double solarDeclination() {
        if (!Double.isNaN(declinationOverride)) {
            return declinationOverride;
        }
        return sky().sunDeclination();
    }

    /**
     * Fraction of the day the sun is above the horizon today at {@link #latitude()},
     * sunrise to sunset (upper limb, with refraction). 0.5 at the equinoxes.
     */
    public static double daylightFraction() {
        if (!Double.isNaN(daylightOverride)) {
            return daylightOverride;
        }
        long now = System.currentTimeMillis();
        if (now < cacheExpiresMs) {
            return cachedDaylight;
        }
        double fraction = upFraction(solarDeclination());
        cachedDaylight = fraction;
        cacheExpiresMs = now + 60_000L;
        return fraction;
    }

    /**
     * Fraction of a day a body at the given declination spends above the horizon at
     * {@link #latitude()} (upper limb, with refraction). Clamped so the warp never
     * divides by zero, even at polar latitudes.
     */
    public static double upFraction(double declinationDegrees) {
        double lat = Math.toRadians(latitude);
        double dec = Math.toRadians(declinationDegrees);
        double cosH = (Math.sin(Math.toRadians(-0.833)) - Math.sin(lat) * Math.sin(dec))
            / (Math.cos(lat) * Math.cos(dec));
        cosH = Math.max(-1.0, Math.min(1.0, cosH));
        return Math.max(0.02, Math.min(0.98, Math.toDegrees(Math.acos(cosH)) / 180.0));
    }

    /** How far the moon trails the sun eastward, in degrees: 0 new, 180 full. */
    public static double moonElongation() {
        return sky().elongation();
    }

    /** The sun's ecliptic longitude, in degrees from the March equinox. */
    public static double sunLongitude() {
        return sky().sunLongitude();
    }

    /**
     * The moon's declination in degrees, including its 5 degree orbital tilt. It swings
     * north and south each month: a winter full moon rides high, a summer one low.
     */
    public static double moonDeclination() {
        return sky().moonDeclination();
    }

    /** Fraction of the sun covered by the moon (0 to 1), where the eclipse is deepest. */
    public static double solarCoverage() {
        return sky().solarCoverage();
    }

    /** Lunar eclipse umbral magnitude: above 0 partial, above 1 total. */
    public static double umbralMagnitude() {
        return sky().umbralMagnitude();
    }

    /** Lunar eclipse penumbral magnitude: above 0 the moon is in Earth's outer shadow. */
    public static double penumbralMagnitude() {
        return sky().penumbralMagnitude();
    }

    /**
     * How dark the daytime sky gets from a solar eclipse, 0 to 1. Barely noticeable
     * until most of the sun is covered, then dropping steeply to full dark at totality.
     */
    public static double solarDarkness() {
        double c = solarCoverage();
        return c < 0.8 ? c * 0.3 : 0.24 + (c - 0.8) / 0.2 * 0.76;
    }

    /** One eclipse: when its deepest point is, what kind it is, and how deep. */
    public record Eclipse(long peakMs, String kind, double magnitude) {
    }

    private static volatile long nextSearchExpiresMs;
    private static volatile Eclipse nextSolar;
    private static volatile Eclipse nextLunar;

    /** The next solar eclipse from now (or the one under way), within about a year. */
    public static Eclipse nextSolarEclipse() {
        refreshNextEclipses();
        return nextSolar;
    }

    /** The next lunar eclipse from now (or the one under way), within about a year. */
    public static Eclipse nextLunarEclipse() {
        refreshNextEclipses();
        return nextLunar;
    }

    private static void refreshNextEclipses() {
        long wall = System.currentTimeMillis();
        if (wall < nextSearchExpiresMs) {
            return;
        }
        Eclipse solar = null;
        Eclipse lunar = null;
        double bestSolar = 0;
        double bestLunar = 0;
        long start = now() - 6 * 3_600_000L;
        for (long t = start; t < start + 400L * 86_400_000L && (solar == null || lunar == null); t += 600_000L) {
            Astronomy.Sky s = Astronomy.at(t);
            if (solar == null) {
                double c = s.solarCoverage();
                if (c > bestSolar) {
                    bestSolar = c;
                    solarPeak = t;
                } else if (bestSolar > 0 && c == 0) {
                    Astronomy.Sky peak = Astronomy.at(solarPeak);
                    boolean annular = peak.moonRadius() < 0.2666;
                    solar = new Eclipse(solarPeak, bestSolar >= 0.999 ? "total" : annular && bestSolar > 0.8
                        ? "annular" : "partial", bestSolar);
                }
            }
            if (lunar == null) {
                double p = s.penumbralMagnitude();
                if (p > bestLunar) {
                    bestLunar = p;
                    lunarPeak = t;
                } else if (bestLunar > 0 && p <= 0) {
                    double u = Astronomy.at(lunarPeak).umbralMagnitude();
                    lunar = new Eclipse(lunarPeak, u >= 1 ? "total" : u > 0 ? "partial" : "penumbral",
                        u > 0 ? u : bestLunar);
                }
            }
        }
        nextSolar = solar;
        nextLunar = lunar;
        nextSearchExpiresMs = wall + 3_600_000L;
    }

    private static long solarPeak;
    private static long lunarPeak;

    /**
     * The moon's vanilla sky angle in degrees at the given (fractional) clock tick. It is
     * where the sun would be when delayed by the elongation, warped to the moon's own
     * time above the horizon. At full moon this is vanilla's "opposite the sun".
     */
    public static double moonAngleDegrees(double totalTicks) {
        double lagTicks = moonElongation() / 360.0 * DAY_TICKS;
        double ticks = warpDayTicks(totalTicks - lagTicks, upFraction(moonDeclination()));
        double fromNoon = ((ticks - NOON) % DAY_TICKS + DAY_TICKS) % DAY_TICKS;
        return fromNoon / DAY_TICKS * 360.0;
    }

    /**
     * {@link #warpDayTicks(long)} for any up-fraction and fractional ticks, so a body
     * moves smoothly between frames.
     */
    public static double warpDayTicks(double totalTicks, double upFraction) {
        double noonIndex = Math.floor((totalTicks + NOON) / DAY_TICKS);
        double fromNoon = totalTicks - (NOON + noonIndex * DAY_TICKS);
        double actualHalfDay = HALF_DAY * upFraction;
        double distance = Math.abs(fromNoon);
        double vanilla = distance <= actualHalfDay
            ? distance * NOON / actualHalfDay
            : NOON + (distance - actualHalfDay) * NOON / (HALF_DAY - actualHalfDay);
        return NOON + noonIndex * DAY_TICKS + Math.copySign(vanilla, fromNoon);
    }

    /**
     * Maps a clock's total ticks onto the vanilla day timeline so daylight fills
     * {@link #daylightFraction()} of each day. Noon stays at tick 6000 and midnight at
     * 18000; only the daylight and night halves are stretched or squeezed around them.
     * Continuous and increasing, so the day count and every keyframe stay in order.
     */
    public static long warpDayTicks(long totalTicks) {
        double daylight = daylightFraction();
        long noonIndex = Math.floorDiv(totalTicks + NOON, DAY_TICKS);
        long fromNoon = totalTicks - (NOON + noonIndex * DAY_TICKS); // [-12000, 12000)
        double actualHalfDay = HALF_DAY * daylight;
        double distance = Math.abs(fromNoon);
        double vanilla;
        if (distance <= actualHalfDay) {
            vanilla = distance * NOON / actualHalfDay;
        } else {
            vanilla = NOON + (distance - actualHalfDay) * NOON / (HALF_DAY - actualHalfDay);
        }
        return NOON + noonIndex * DAY_TICKS + Math.round(Math.copySign(vanilla, fromNoon));
    }

    private static void loadConfig() {
        double fallback = latitude;
        Path path = FabricLoader.getInstance().getConfigDir().resolve("gameoverse-sky-sync.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) {
                JsonObject json = gson.fromJson(Files.readString(path), JsonObject.class);
                if (json != null && json.has("latitude")) {
                    latitude = Math.max(-89.0, Math.min(89.0, json.get("latitude").getAsDouble()));
                    if (json.has("daylight_override") && !json.get("daylight_override").isJsonNull()) {
                        daylightOverride = Math.max(0.02, Math.min(0.98, json.get("daylight_override").getAsDouble()));
                        LOGGER.warn("daylight_override {} is set in {}: daylight length is fixed, not seasonal",
                            daylightOverride, path);
                    }
                    if (json.has("sky_time_override") && !json.get("sky_time_override").isJsonNull()) {
                        Instant start = Instant.parse(json.get("sky_time_override").getAsString());
                        timeOffsetMs = start.toEpochMilli() - System.currentTimeMillis();
                        LOGGER.warn("sky_time_override {} is set in {}: the sky runs from that moment, not today",
                            start, path);
                    }
                    if (json.has("declination_override") && !json.get("declination_override").isJsonNull()) {
                        declinationOverride = Math.max(-23.44, Math.min(23.44, json.get("declination_override").getAsDouble()));
                        LOGGER.warn("declination_override {} is set in {}: the sun's tilt is fixed, not seasonal",
                            declinationOverride, path);
                    }
                    return;
                }
            }
            JsonObject json = new JsonObject();
            json.addProperty("_comment", "Latitude whose daylight length the overworld follows. "
                + "Default: the production server's location (New York City area, from its IP).");
            json.addProperty("latitude", fallback);
            Files.createDirectories(path.getParent());
            Files.writeString(path, gson.toJson(json));
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Couldn't read {}, using latitude {}: {}", path, fallback, e.toString());
        }
    }
}
