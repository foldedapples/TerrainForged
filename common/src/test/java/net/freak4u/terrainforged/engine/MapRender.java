package net.freak4u.terrainforged.engine;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import net.freak4u.terrainforged.engine.config.TerrainSettings;
import net.freak4u.terrainforged.engine.preview.ReliefRenderer;
import net.freak4u.terrainforged.engine.worldgen.TerrainEngine;

/**
 * Developer tool: renders a hill-shaded relief map so terrain can be inspected without launching the game.
 * Usage: MapRender seed centerX centerZ blocksPerPixel size outPrefix. With blocksPerPixel >= 8 it uses RTF's
 * fast preview (128x128, raw cells); otherwise it samples the eroded world {@code size x size}.
 */
public final class MapRender {
    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[0]);
        int cx = Integer.parseInt(args[1]), cz = Integer.parseInt(args[2]);
        int bpp = Integer.parseInt(args[3]);
        int size = Integer.parseInt(args[4]);
        String out = args[5];
        TerrainEngine engine = new TerrainEngine(seed, TerrainSettings.defaults());
        long t0 = System.nanoTime();
        ReliefRenderer.Map m = bpp >= 8 ? ReliefRenderer.preview(engine, cx, cz, bpp) : ReliefRenderer.sample(engine, cx, cz, bpp, size, size, () -> false);
        int[] px = ReliefRenderer.colour(m);
        BufferedImage img = new BufferedImage(m.width, m.height, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, m.width, m.height, px, 0, m.width);
        ImageIO.write(img, "png", new File(out + "_relief.png"));
        System.out.printf("rendered %dx%d @%d bpp in %d ms%n", m.width, m.height, bpp, (System.nanoTime() - t0) / 1_000_000);
    }
}
