package net.gameoverse.skysync;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Cosmos draws its stars (and Spyglass Astronomy's, when both are installed) through its
 * own {@code cosmos:pipeline/stars}, which it never registers with Iris. Under a shader
 * Iris then logs "Missing program cosmos:pipeline/stars in override list" and those stars
 * don't render. This assigns it to Iris's textured sky program (Cosmos draws each star as
 * a textured quad; the untextured sky program has no usable texture or UVs, so stars came
 * out as flat squares), with the same reflective call Spyglass Astronomy uses for planets.
 */
public class SkySyncClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("iris") || !loader.isModLoaded("cosmos")) {
            return;
        }
        try {
            RenderPipeline stars = (RenderPipeline) Class.forName("net.hollowed.cosmos.renderer.CosmosStarRendering")
                .getField("COSMOS_STARS").get(null);
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Class<?> program = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object skyTextured = Enum.valueOf((Class) program, "SKY_TEXTURED");
            api.getMethod("assignPipeline", RenderPipeline.class, program).invoke(instance, stars, skyTextured);
            RealSky.LOGGER.info("Assigned Cosmos's star pipeline to Iris's textured sky program");
        } catch (ReflectiveOperationException | ClassCastException e) {
            RealSky.LOGGER.warn("Couldn't assign Cosmos's star pipeline to Iris; stars may not show under shaders", e);
        }
    }
}
