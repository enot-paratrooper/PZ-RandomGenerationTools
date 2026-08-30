package mapgen.towns;

/**
 * Тип района. Цвет используется отладочной картой, остальные поля — генерацией:
 * {@code minBlock}/{@code maxBlock} задают сетку улиц (жилая мелкая, промышленная крупная),
 * {@code lotSide} и {@code coverage} — нарезку участков и плотность застройки.
 *
 * <p>Число кварталов растёт как обратный квадрат стороны, поэтому дифференцировать сетку
 * стоит вверх (крупнее у промышленности и ферм), а не вниз: уменьшение жилого {@code minBlock}
 * вдвое учетверяет число кварталов и участков.
 */
public enum DistrictType {
    /** центр — оранжевый */
    DOWNTOWN   ("центр",         0xFF8C00,  40,  95, 1.00, 34, 0.85),
    /** офисный — голубой */
    OFFICE     ("офисный",       0x55CCEE,  55, 125, 1.00, 46, 0.70),
    /** коммерческий — синий */
    COMMERCIAL ("коммерческий",  0x2255DD,  60, 135, 1.00, 40, 0.65),
    /** жилой — зелёный */
    RESIDENTIAL("жилой",         0x3CB44B,  48, 110, 0.85, 26, 0.55),
    /** промышленный — жёлтый */
    INDUSTRIAL ("промышленный",  0xE6D22D, 120, 230, 1.15, 62, 0.60),
    /** военный — красный */
    MILITARY   ("военный",       0xDD2222, 140, 260, 1.10, 70, 0.35),
    /** парк — тёмно-зелёный, застройки нет */
    PARK       ("парк",          0x0B6B2A,  90, 200, 0.80,  0, 0.00),
    /** ферма — коричневый, редкие постройки на больших участках */
    FARM       ("ферма",         0xB07030, 200, 420, 0.90, 96, 0.12);

    public final String label;
    public final int color;
    /** минимальная и максимальная сторона квартала, px */
    public final int minBlock, maxBlock;
    /** множитель ширины улицы относительно базовой для глубины разреза */
    public final double streetScale;
    /** целевая сторона участка, px; 0 — застройки нет */
    public final int lotSide;
    /** доля участков квартала, которые застраиваются */
    public final double coverage;

    private static final DistrictType[] VALUES = values();

    DistrictType(String label, int color, int minBlock, int maxBlock,
                 double streetScale, int lotSide, double coverage) {
        this.label = label;
        this.color = color;
        this.minBlock = minBlock;
        this.maxBlock = maxBlock;
        this.streetScale = streetScale;
        this.lotSide = lotSide;
        this.coverage = coverage;
    }

    public boolean buildable() { return coverage > 0 && lotSide > 0; }

    public static DistrictType byOrdinal(int i) { return VALUES[i]; }

    public static int count() { return VALUES.length; }
}
