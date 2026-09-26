package net.gameoverse.skysync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.MonthDay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The real sky for today, from the machine's clock: the moon's age and phase, and how
 * much of the day the sun is up at the configured latitude. Only whole-day values and
 * the moon phase are used, so a client and the server agree as long as their clocks are
 * roughly right.
 */
public final class RealSky {
    public static final Logger LOGGER = LoggerFactory.getLogger("gameoverse_sky_sync");

    /** Mean synodic month, in days. */
    public static final double SYNODIC_MONTH = 29.530588853;
    /** A known new moon: 2000-01-06 18:14 UTC. */
    private static final long NEW_MOON_EPOCH_MS = 947_182_440_000L;
    /** March equinox, the start of the year for the sun's tilt and seasonal events. */
    private static final MonthDay MARCH_EQUINOX = MonthDay.of(3, 20);

    public static final int DAY_TICKS = 24000;
    private static final int NOON = 6000;
    private static final int HALF_DAY = 12000;

    private static double latitude = 40.8;
    /** Testing only: a fixed daylight fraction instead of today's (config "daylight_override"). */
    private static double daylightOverride = Double.NaN;

    static {
        loadConfig();
    }

    private static volatile long cacheExpiresMs;
    private static volatile double cachedDaylight = 0.5;

    private RealSky() {
    }

    public static double latitude() {
        return latitude;
    }

    /** Overworld clock ticks at {@link #clockSampleMs}, recorded each server tick. */
    private static volatile long clockSampleTicks = Long.MIN_VALUE;
    private static volatile long clockSampleMs;

    /** Called every server tick with the overworld's clock, to place future ticks in real time. */
    public static void recordClock(long ticks) {
        clockSampleTicks = ticks;
        clockSampleMs = System.currentTimeMillis();
    }

    /**
     * The real time a given overworld clock tick falls at, assuming 20 ticks per second
     * from the last recorded tick. Now, if no tick has been recorded (e.g. on a client).
     */
    public static long realTimeOfTick(long ticks) {
        long sample = clockSampleTicks;
        if (sample == Long.MIN_VALUE) {
            return System.currentTimeMillis();
        }
        return clockSampleMs + (ticks - sample) * 50L;
    }

    /** Days since the last new moon, 0 to {@link #SYNODIC_MONTH}. */
    public static double lunarAge() {
        return lunarAgeAt(System.currentTimeMillis());
    }

    /** Days since the last new moon at the given real time. */
    public static double lunarAgeAt(long epochMs) {
        double days = (epochMs - NEW_MOON_EPOCH_MS) / 86_400_000.0;
        double age = days % SYNODIC_MONTH;
        return age < 0 ? age + SYNODIC_MONTH : age;
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
        return moonPhaseIndexAt(System.currentTimeMillis());
    }

    /** {@link #moonPhaseIndex()} at the given real time. */
    public static int moonPhaseIndexAt(long epochMs) {
        double age = lunarAgeAt(epochMs);
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
        return LocalDate.now().toEpochDay();
    }

    /** Days since the most recent March equinox, 0 to 365. */
    public static int dayOfSolarYear() {
        LocalDate today = LocalDate.now();
        LocalDate equinox = MARCH_EQUINOX.atYear(today.getYear());
        if (today.isBefore(equinox)) {
            equinox = MARCH_EQUINOX.atYear(today.getYear() - 1);
        }
        return (int) (today.toEpochDay() - equinox.toEpochDay());
    }

    /** The sun's declination today in degrees (Cooper's approximation). */
    public static double solarDeclination() {
        int n = LocalDate.now().getDayOfYear();
        return 23.44 * Math.sin(Math.toRadians(360.0 / 365.0 * (284 + n)));
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
        double lat = Math.toRadians(latitude);
        double dec = Math.toRadians(solarDeclination());
        double cosH = (Math.sin(Math.toRadians(-0.833)) - Math.sin(lat) * Math.sin(dec))
            / (Math.cos(lat) * Math.cos(dec));
        cosH = Math.max(-1.0, Math.min(1.0, cosH));
        // Keep a sliver of night and day even at the poles, so the warp never divides by zero.
        double fraction = Math.max(0.02, Math.min(0.98, Math.toDegrees(Math.acos(cosH)) / 180.0));
        cachedDaylight = fraction;
        cacheExpiresMs = now + 60_000L;
        return fraction;
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
