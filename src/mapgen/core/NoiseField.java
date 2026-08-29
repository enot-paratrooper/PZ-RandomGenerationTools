package mapgen.core;

import mapgen.noise.PerlinNoise;

/** Бесконечное детерминированное поле [0,1] в мировых координатах: значение зависит только от seed и (wx, wy). */
public final class NoiseField {
    private final PerlinNoise noise;
    private final double scale, power;
    private final int octaves;

    public NoiseField(long seed, double scale, int octaves, double power) {
        this.noise = new PerlinNoise(seed);
        this.scale = scale;
        this.octaves = octaves;
        this.power = power;
    }

    public float at(int wx, int wy) {
        double v = noise.fbm(wx / scale, wy / scale, octaves, 2.0, 0.5);
        return (float) (power == 1.0 ? v : Math.pow(v, power));
    }
}
