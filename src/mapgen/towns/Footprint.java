package mapgen.towns;

/**
 * Форма следа города. Поворота нет: мир тайловый, оси фиксированы.
 *
 * <p>{@link #norm} возвращает нормированное расстояние от центра: 0 в центре, 1 на кромке,
 * больше 1 — снаружи. По нему работают и проверка принадлежности, и рваный край.
 * Прямоугольник считается двумя сравнениями, эллипс — при предпосчитанных полуосях
 * стоит столько же, сколько круг.
 */
public enum Footprint {
    CIRCLE("круглый"),
    ELLIPSE("эллиптический"),
    RECT("прямоугольный");

    public final String label;

    Footprint(String label) { this.label = label; }

    /** @param hw,hh полуразмеры следа по осям */
    public double norm(int cx, int cy, int hw, int hh, int x, int y) {
        double dx = (x - cx) / (double) hw, dy = (y - cy) / (double) hh;
        return this == RECT ? Math.max(Math.abs(dx), Math.abs(dy)) : Math.sqrt(dx * dx + dy * dy);
    }

    public boolean contains(int cx, int cy, int hw, int hh, int x, int y) {
        long dx = x - cx, dy = y - cy;
        if (this == RECT) return Math.abs(dx) <= hw && Math.abs(dy) <= hh;
        // без корня: (dx/hw)^2 + (dy/hh)^2 <= 1
        double a = dx / (double) hw, b = dy / (double) hh;
        return a * a + b * b <= 1.0;
    }

    /**
     * Точка на луче под углом {@code a} на доле {@code u} от центра до кромки.
     * Для прямоугольника луч продлевается до стороны, поэтому углы тоже попадают в выборку.
     */
    public double[] ray(int cx, int cy, int hw, int hh, double a, double u) {
        double c = Math.cos(a), s = Math.sin(a);
        double k = this == RECT ? 1.0 / Math.max(Math.abs(c), Math.abs(s)) : 1.0;
        return new double[]{cx + hw * u * c * k, cy + hh * u * s * k};
    }
}
