package net.gameoverse.skysync;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class SkySync implements ModInitializer {
    @Override
    public void onInitialize() {
        RealSky.LOGGER.info("Real sky at latitude {}: moon phase {} (lunar day {}), daylight {}% of the day",
            RealSky.latitude(), RealSky.moonPhaseIndex(), RealSky.lunarDay(),
            Math.round(RealSky.daylightFraction() * 1000) / 10.0);
        ServerTickEvents.END_SERVER_TICK.register(server ->
            RealSky.recordClock(server.overworld().getDefaultClockTime()));
    }
}
