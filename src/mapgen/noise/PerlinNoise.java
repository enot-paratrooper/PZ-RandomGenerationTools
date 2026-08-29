package mapgen.noise;

import java.util.Random;

/**
 * Классический Perlin noise 2D + fBm. Возвращает значения примерно в [-1, 1].
 * Таблица перестановок заполняется в конструкторе и дальше только читается — потокобезопасно.
 */
public final class PerlinNoise {
    private final int[] perm = new int[512];

    public PerlinNoise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    public double noise(double x, double y) {
        int xi = (int) Math.floor(x) & 255, yi = (int) Math.floor(y) & 255;
        double xf = x - Math.floor(x), yf = y - Math.floor(y);
        double u = fade(xf), v = fade(yf);
        int aa = perm[perm[xi] + yi],     ab = perm[perm[xi] + yi + 1];
        int ba = perm[perm[xi + 1] + yi], bb = perm[perm[xi + 1] + yi + 1];
        double x1 = lerp(grad(aa, xf, yf),     grad(ba, xf - 1, yf),     u);
        double x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v);
    }

    /** Фрактальный шум, нормализованный в [0, 1]. */
    public double fbm(double x, double y, int octaves, double lacunarity, double gain) {
        double sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x * freq, y * freq);
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return (sum / norm + 1) * 0.5;
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double a, double b, double t) { return a + t * (b - a); }

    private static double grad(int hash, double x, double y) {
        switch (hash & 7) {
            case 0: return  x + y;  case 1: return -x + y;
            case 2: return  x - y;  case 3: return -x - y;
            case 4: return  x;      case 5: return -x;
            case 6: return  y;      default: return -y;
        }
    }
}
