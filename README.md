# Gameoverse Sky Sync

Fabric mod (26.1.2, both sides), wholly original, MIT. The overworld's sky follows
the real world:

- **Moon phase** follows the real moon. The four principal phases (new, first
  quarter, full, third quarter) each last about two days around the exact moment,
  the crescents and gibbous phases fill the rest, as they look in the real sky.
- **Daylight length** follows the real date at the configured latitude: at the
  default 40.8N (the production server's datacenter, Secaucus NJ, looked up from its
  IP), about 12.6 minutes of daylight per 20-minute day at the June solstice, 10 at
  the equinoxes and 7.7 at the December solstice. The day stays 20 minutes long, and
  noon and midnight stay at ticks 6000 and 18000.

Companion to `gameoverse-season-sync` (seasons by calendar month) and to our fork of
Even Better Moon and Sun (`../even-better-moon-sun-fabric`: sun tilt and sky events
by the real date). Replaces Serene Seasons Plus' seasonal daylight cycle from the
1.21.1 server, which has no 26.1 build.

## How it works

26.1 drives the sky from data-driven timelines: `minecraft:day` (sun, moon and star
angles, sky light, sky colours, monsters burning, bees, eyeblossoms, ...),
`minecraft:moon` (moon phase and surface slime chance) and `minecraft:villager_schedule`.
Each track gets an `AttributeTrackSampler` that reads the world clock.

- `EnvironmentAttributeSystemBuilderMixin` tags each sampler as it's built, the one
  place the timeline's registry key is known: `moon` -> MOON, `day` and
  `villager_schedule` -> DAY.
- `AttributeTrackSamplerMixin` swaps the clock read for a tagged sampler: MOON gets
  the real phase's position on the 8-day moon timeline; DAY gets the clock warped so
  daylight fills today's fraction (`RealSky.warpDayTicks`, continuous and increasing).

Everything that reads those attributes follows automatically, including sleeping
(beds check sky darkness, from the `sky_light_level` track), mob spawning light,
the slime moon bonus, and the client's sky. `/time query day` and `/time set` still
use the raw clock.

Not covered: anything with its own day-time maths. Enhanced Celestials starts its
lunar events at a fixed tick (12500), so in summer an event can begin while the sky
is still light, and in winter a little after dark.

## Config

`config/gameoverse-sky-sync.json`: `latitude` (degrees north). `daylight_override`
(0 to 1) fixes the daylight fraction, for testing only: remove it afterwards.
Both sides read their own copy; AutoModpack ships the server's.

## Build

`./gradlew build`, then copy `build/libs/gameoverse-sky-sync-<version>.jar` to
`fabric 26.1/mods/` (AutoModpack gives it to clients too).
