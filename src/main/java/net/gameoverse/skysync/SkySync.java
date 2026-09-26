package net.gameoverse.skysync;

import net.fabricmc.api.ModInitializer;

public class SkySync implements ModInitializer {
    @Override
    public void onInitialize() {
        RealSky.LOGGER.info("Real sky at latitude {}: moon phase {} (lunar day {}), daylight {}% of the day",
            RealSky.latitude(), RealSky.moonPhaseIndex(), RealSky.lunarDay(),
            Math.round(RealSky.daylightFraction() * 1000) / 10.0);
    }
}
