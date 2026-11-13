package com.example.tinymap;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

/**
 * TinyMap: small minimap on the HUD + full map screen (key M) where you can
 * click to set a waypoint. The waypoint is shown as a column of glowing
 * particles in the world, a marker on the minimap and a distance label.
 */
public class TinyMapClient implements ClientModInitializer {
    public static final String MOD_ID = "tinymap";

    // ---- Minimap size: change these two to resize ----
    private static final int SIZE = 32;   // how many blocks the minimap covers (SIZE x SIZE)
    private static final int SCALE = 2;   // screen pixels per block  (SIZE * SCALE = pixels)
    private static final int MARGIN = 6;
    private static final int UPDATE_EVERY_TICKS = 5;

    // ---- Waypoint beam ----
    private static final int BEAM_HEIGHT = 80;  // blocks tall
    private static final int BEAM_STEP = 2;     // distance between particles

    // ---- Waypoint (shared with the map screen) ----
    private static boolean hasWaypoint = false;
    private static int waypointX;
    private static int waypointZ;

    private KeyMapping openMapKey;
    private MapUtil.Raster raster;
    private int tickCounter = 0;

    public static void setWaypoint(int x, int z) {
        waypointX = x;
        waypointZ = z;
        hasWaypoint = true;
    }

    public static void clearWaypoint() {
        hasWaypoint = false;
    }

    public static boolean hasWaypoint() {
        return hasWaypoint;
    }

    public static int getWaypointX() {
        return waypointX;
    }

    public static int getWaypointZ() {
        return waypointZ;
    }

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        openMapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.tinymap.open_map",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "minimap"),
                this::render);
    }

    private void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            raster = null;
            return;
        }

        while (openMapKey.consumeClick()) {
            mc.setScreen(new WorldMapScreen());
        }

        tickCounter++;

        if (tickCounter % UPDATE_EVERY_TICKS == 0) {
            raster = MapUtil.build(mc.level, mc.player.getBlockX(), mc.player.getBlockZ(), SIZE / 2);
        }

        if (hasWaypoint && tickCounter % 2 == 0) {
            spawnBeam(mc.level, mc.player.getY());
        }
    }

    private void spawnBeam(ClientLevel level, double playerY) {
        double base = level.hasChunk(waypointX >> 4, waypointZ >> 4)
                ? level.getHeight(Heightmap.Types.WORLD_SURFACE, waypointX, waypointZ)
                : playerY;
        double x = waypointX + 0.5;
        double z = waypointZ + 0.5;
        for (int h = 0; h <= BEAM_HEIGHT; h += BEAM_STEP) {
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, x, base + h, z, 0, 0, 0);
        }
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (raster == null || mc.player == null) return;

        int total = SIZE * SCALE;
        int x0 = graphics.guiWidth() - total - MARGIN;
        int y0 = MARGIN;

        // border + map
        graphics.fill(x0 - 1, y0 - 1, x0 + total + 1, y0 + total + 1, 0xFF000000);
        MapUtil.draw(graphics, raster, x0, y0, SCALE);

        // waypoint marker (clamped to the edge of the minimap if it is far away)
        if (hasWaypoint) {
            int wx = clamp((int) ((waypointX + 0.5 - raster.left) * SCALE), 0, total - 1);
            int wz = clamp((int) ((waypointZ + 0.5 - raster.top) * SCALE), 0, total - 1);
            graphics.fill(x0 + wx - 2, y0 + wz - 2, x0 + wx + 2, y0 + wz + 2, 0xFFFFFF00);
        }

        // player marker
        int px = clamp((int) ((mc.player.getX() - raster.left) * SCALE), 0, total - 1);
        int pz = clamp((int) ((mc.player.getZ() - raster.top) * SCALE), 0, total - 1);
        graphics.fill(x0 + px - 1, y0 + pz - 1, x0 + px + 2, y0 + pz + 2, 0xFFFF2020);

        // distance label
        if (hasWaypoint) {
            int dist = (int) Math.hypot(waypointX + 0.5 - mc.player.getX(), waypointZ + 0.5 - mc.player.getZ());
            graphics.text(mc.font, dist + "m", x0, y0 + total + 4, 0xFFFFFF55, true);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
