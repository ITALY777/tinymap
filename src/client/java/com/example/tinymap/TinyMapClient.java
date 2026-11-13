package com.example.tinymap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Simple top-down minimap drawn on the HUD (top-right corner).
 * Samples the surface block colour around the player every few ticks.
 */
public class TinyMapClient implements ClientModInitializer {
    public static final String MOD_ID = "tinymap";

    private static final int SIZE = 64;      // blocks covered (SIZE x SIZE)
    private static final int SCALE = 2;      // screen pixels per block
    private static final int MARGIN = 8;
    private static final int UPDATE_EVERY_TICKS = 10;
    private static final int UNLOADED = 0xFF202020;

    private final int[] colors = new int[SIZE * SIZE];
    private int tickCounter = 0;
    private boolean hasData = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "minimap"),
                this::render);
    }

    private void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            hasData = false;
            return;
        }
        if (tickCounter++ % UPDATE_EVERY_TICKS != 0) return;

        ClientLevel level = mc.level;
        int px = mc.player.getBlockX();
        int pz = mc.player.getBlockZ();
        int half = SIZE / 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dz = 0; dz < SIZE; dz++) {
            for (int dx = 0; dx < SIZE; dx++) {
                int wx = px - half + dx;
                int wz = pz - half + dz;
                colors[dz * SIZE + dx] = sample(level, pos, wx, wz);
            }
        }
        hasData = true;
    }

    private int sample(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return UNLOADED;

        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockState state = level.getBlockState(pos.set(x, y, z));
        int rgb = state.getMapColor(level, pos).col;
        if (rgb == 0) return UNLOADED; // air / no colour

        // Simple relief shading: compare with the block to the north.
        int yNorth = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) - 1;
        double shade = 1.0 + Math.max(-3, Math.min(3, y - yNorth)) * 0.08;

        int r = clamp((int) (((rgb >> 16) & 0xFF) * shade));
        int g = clamp((int) (((rgb >> 8) & 0xFF) * shade));
        int b = clamp((int) ((rgb & 0xFF) * shade));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        if (!hasData) return;

        int total = SIZE * SCALE;
        int x0 = graphics.guiWidth() - total - MARGIN;
        int y0 = MARGIN;

        // border
        graphics.fill(x0 - 1, y0 - 1, x0 + total + 1, y0 + total + 1, 0xFF000000);

        // draw rows, merging horizontal runs of the same colour to cut draw calls
        for (int row = 0; row < SIZE; row++) {
            int runStart = 0;
            int runColor = colors[row * SIZE];
            for (int col = 1; col <= SIZE; col++) {
                int c = col < SIZE ? colors[row * SIZE + col] : 0;
                if (col == SIZE || c != runColor) {
                    graphics.fill(
                            x0 + runStart * SCALE, y0 + row * SCALE,
                            x0 + col * SCALE, y0 + (row + 1) * SCALE,
                            runColor);
                    runStart = col;
                    runColor = c;
                }
            }
        }

        // player marker in the centre
        int cx = x0 + total / 2;
        int cy = y0 + total / 2;
        graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFFFF2020);
    }
}
