package mapgen.core;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Растровый слой карты: пиксель = цвет RGB (0xRRGGBB). */
public final class Layer {
    private final int width, height;
    private final int[] pixels;

    public Layer(int width, int height, int fillColor) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
        Arrays.fill(pixels, fillColor);
    }

    public int width()  { return width; }
    public int height() { return height; }

    public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }

    public int get(int x, int y)            { return pixels[y * width + x]; }
    public void set(int x, int y, int rgb)  { pixels[y * width + x] = rgb; }

    public BufferedImage toImage() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, width, height, pixels, 0, width);
        return img;
    }
}
