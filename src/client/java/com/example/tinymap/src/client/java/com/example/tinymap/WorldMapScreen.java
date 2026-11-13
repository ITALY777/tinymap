package com.example.tinymap;

import java.util.Arrays;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Samples the surface colours around a point and merges them into as few
 * rectangles as possible, so drawing the map needs far fewer fill() calls
 * (this is what removes the lag).
 */
public final class MapUtil {
    public static final int UNLOADED = 0xFF202020;

    private MapUtil() {
    }

    /** A pre-computed map: rects holds [x, y, w, h, color] groups, in blocks. */
    public static final class Raster {
        public final int size;
        public final int left;   // world X of the left edge
        public final int top;    // world Z of the top edge
        public final int[] rects;
        public final int count;

        Raster(int size, int left, int top, int[] rects, int count) {
            this.size = size;
            this.left = left;
            this.top = top;
            this.rects = rects;
            this.count = count;
        }
    }

    /** Builds a map of (radius * 2) x (radius * 2) blocks centred on (cx, cz). */
    public static Raster build(ClientLevel level, int cx, int cz, int radius) {
        int size = radius * 2;
        int left = cx - radius;
        int top = cz - radius;
        int[] colors = new int[size * size];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                colors[z * size + x] = sample(level, pos, left + x, top + z);
            }
        }
        return merge(colors, size, left, top);
    }

    private static int sample(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return UNLOADED;

        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockState state = level.getBlockState(pos.set(x, y, z));
        int rgb = state.getMapColor(level, pos).col;
        if (rgb == 0) return UNLOADED;

        // Relief shading in 5 steps (few steps = bigger merged rectangles).
        int yNorth = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) - 1;
        int diff = Math.max(-2, Math.min(2, y - yNorth));
        double shade = 1.0 + diff * 0.1;

        int r = clamp255((int) (((rgb >> 16) & 0xFF) * shade));
        int g = clamp255((int) (((rgb >> 8) & 0xFF) * shade));
        int b = clamp255((int) ((rgb & 0xFF) * shade));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Greedy merge: grow each unused cell right, then down, while the colour matches. */
    private static Raster merge(int[] colors, int size, int left, int top) {
        boolean[] used = new boolean[size * size];
        int[] rects = new int[512 * 5];
        int count = 0;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int idx = y * size + x;
                if (used[idx]) continue;

                int c = colors[idx];
                int w = 1;
                while (x + w < size && !used[idx + w] && colors[idx + w] == c) {
                    w++;
                }

                int h = 1;
                boolean grow = true;
                while (grow && y + h < size) {
                    int rowStart = (y + h) * size + x;
                    for (int i = 0; i < w; i++) {
                        if (used[rowStart + i] || colors[rowStart + i] != c) {
                            grow = false;
                            break;
                        }
                    }
                    if (grow) h++;
                }

                for (int yy = 0; yy < h; yy++) {
                    for (int xx = 0; xx < w; xx++) {
                        used[(y + yy) * size + x + xx] = true;
                    }
                }

                if ((count + 1) * 5 > rects.length) {
                    rects = Arrays.copyOf(rects, rects.length * 2);
                }
                int b = count * 5;
                rects[b] = x;
                rects[b + 1] = y;
                rects[b + 2] = w;
                rects[b + 3] = h;
                rects[b + 4] = c;
                count++;
            }
        }
        return new Raster(size, left, top, rects, count);
    }

    /** Draws the raster with its top-left corner at (x0, y0), `scale` pixels per block. */
    public static void draw(GuiGraphicsExtractor graphics, Raster r, int x0, int y0, int scale) {
        for (int i = 0; i < r.count; i++) {
            int b = i * 5;
            int rx = r.rects[b];
            int ry = r.rects[b + 1];
            int rw = r.rects[b + 2];
            int rh = r.rects[b + 3];
            int color = r.rects[b + 4];
            graphics.fill(
                    x0 + rx * scale, y0 + ry * scale,
                    x0 + (rx + rw) * scale, y0 + (ry + rh) * scale,
                    color);
        }
    }
}
