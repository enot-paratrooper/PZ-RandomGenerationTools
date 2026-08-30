package mapgen.towns;

import java.util.Random;

/**
 * Шаблон города: что за поселение, какого размера и формы, из каких районов состоит
 * и без каких зданий не обходится.
 *
 * <p>Доли районов ({@code shares}) — целевые по <b>площади</b>, а не по числу центров Вороного.
 * Генератор сначала раздаёт типы центрам по квотам, затем перекрашивает пограничные кварталы,
 * пока отклонение не уложится в {@link TownField#SHARE_TOLERANCE}.
 *
 * <p>{@code radius} — полуразмер по длинной оси; короткая получается делением на
 * коэффициент вытянутости из диапазона {@code aspect}. {@code ragged} — доля радиуса,
 * на которой кромка размывается: 0 даёт ровный периметр (база, тюрьма), 0.55 — расползшуюся
 * деревню.
 */
public enum TownTemplate {

    HAMLET("деревня", 10, Footprint.CIRCLE, 150, 240, 1.00, 1.30, 0.55, 2, 3, 0.80,
            new DistrictType[]{DistrictType.RESIDENTIAL, DistrictType.FARM, DistrictType.COMMERCIAL},
            new double[]{0.60, 0.30, 0.10},
            new InfraType[]{InfraType.CHURCH},
            new int[]{1}),

    VILLAGE("село", 10, Footprint.CIRCLE, 220, 330, 1.00, 1.40, 0.50, 3, 4, 0.85,
            new DistrictType[]{DistrictType.RESIDENTIAL, DistrictType.FARM,
                    DistrictType.COMMERCIAL, DistrictType.PARK},
            new double[]{0.50, 0.25, 0.15, 0.10},
            new InfraType[]{InfraType.SCHOOL, InfraType.GAS_STATION, InfraType.CHURCH},
            new int[]{1, 1, 1}),

    SUBURB("пригород", 9, Footprint.ELLIPSE, 300, 460, 1.20, 1.80, 0.40, 4, 6, 0.90,
            new DistrictType[]{DistrictType.RESIDENTIAL, DistrictType.PARK,
                    DistrictType.COMMERCIAL, DistrictType.OFFICE},
            new double[]{0.70, 0.12, 0.10, 0.08},
            new InfraType[]{InfraType.SCHOOL, InfraType.SUPERMARKET, InfraType.GAS_STATION},
            new int[]{2, 1, 1}),

    SMALL_TOWN("городок", 12, Footprint.ELLIPSE, 320, 480, 1.00, 1.40, 0.40, 4, 6, 1.00,
            new DistrictType[]{DistrictType.RESIDENTIAL, DistrictType.COMMERCIAL,
                    DistrictType.DOWNTOWN, DistrictType.PARK, DistrictType.INDUSTRIAL},
            new double[]{0.50, 0.18, 0.12, 0.10, 0.10},
            new InfraType[]{InfraType.SCHOOL, InfraType.POLICE, InfraType.GAS_STATION,
                    InfraType.SUPERMARKET, InfraType.CHURCH},
            new int[]{1, 1, 1, 1, 1}),

    TOWN("город", 10, Footprint.ELLIPSE, 450, 620, 1.00, 1.50, 0.35, 5, 7, 1.00,
            new DistrictType[]{DistrictType.RESIDENTIAL, DistrictType.COMMERCIAL, DistrictType.DOWNTOWN,
                    DistrictType.OFFICE, DistrictType.INDUSTRIAL, DistrictType.PARK},
            new double[]{0.42, 0.18, 0.12, 0.10, 0.10, 0.08},
            new InfraType[]{InfraType.HOSPITAL, InfraType.SCHOOL, InfraType.POLICE,
                    InfraType.FIRE_STATION, InfraType.SUPERMARKET, InfraType.GAS_STATION,
                    InfraType.CEMETERY},
            new int[]{1, 2, 1, 1, 1, 2, 1}),

    CITY("крупный город", 6, Footprint.ELLIPSE, 620, 880, 1.00, 1.40, 0.30, 7, 10, 1.05,
            new DistrictType[]{DistrictType.RESIDENTIAL, DistrictType.DOWNTOWN, DistrictType.COMMERCIAL,
                    DistrictType.OFFICE, DistrictType.INDUSTRIAL, DistrictType.PARK},
            new double[]{0.32, 0.18, 0.18, 0.16, 0.10, 0.06},
            new InfraType[]{InfraType.HOSPITAL, InfraType.MALL, InfraType.SCHOOL, InfraType.POLICE,
                    InfraType.FIRE_STATION, InfraType.GAS_STATION, InfraType.POWER_STATION,
                    InfraType.CEMETERY},
            new int[]{2, 1, 3, 2, 1, 3, 1, 1}),

    INDUSTRIAL_HUB("промышленный узел", 7, Footprint.RECT, 380, 560, 1.10, 1.80, 0.20, 4, 6, 1.15,
            new DistrictType[]{DistrictType.INDUSTRIAL, DistrictType.RESIDENTIAL,
                    DistrictType.COMMERCIAL, DistrictType.OFFICE},
            new double[]{0.62, 0.18, 0.10, 0.10},
            new InfraType[]{InfraType.WAREHOUSE, InfraType.POWER_STATION,
                    InfraType.GAS_STATION, InfraType.FIRE_STATION},
            new int[]{3, 1, 1, 1}),

    MILITARY_BASE("военная база", 5, Footprint.RECT, 300, 460, 1.00, 1.60, 0.08, 3, 5, 1.10,
            new DistrictType[]{DistrictType.MILITARY, DistrictType.OFFICE, DistrictType.RESIDENTIAL},
            new double[]{0.72, 0.14, 0.14},
            new InfraType[]{InfraType.ARMORY, InfraType.CHECKPOINT,
                    InfraType.WAREHOUSE, InfraType.WATER_TOWER},
            new int[]{1, 2, 2, 1}),

    FARMSTEAD("фермерское хозяйство", 8, Footprint.RECT, 350, 560, 1.20, 2.00, 0.35, 3, 4, 0.85,
            new DistrictType[]{DistrictType.FARM, DistrictType.RESIDENTIAL, DistrictType.COMMERCIAL},
            new double[]{0.78, 0.14, 0.08},
            new InfraType[]{InfraType.WAREHOUSE, InfraType.WATER_TOWER},
            new int[]{2, 1}),

    HIGHWAY_STOP("придорожный узел", 8, Footprint.RECT, 160, 260, 1.40, 2.20, 0.25, 2, 3, 1.00,
            new DistrictType[]{DistrictType.COMMERCIAL, DistrictType.INDUSTRIAL, DistrictType.RESIDENTIAL},
            new double[]{0.50, 0.30, 0.20},
            new InfraType[]{InfraType.GAS_STATION, InfraType.MOTEL, InfraType.WAREHOUSE},
            new int[]{2, 1, 1}),

    RESORT("курорт", 5, Footprint.ELLIPSE, 300, 450, 1.30, 2.00, 0.45, 4, 6, 0.90,
            new DistrictType[]{DistrictType.COMMERCIAL, DistrictType.PARK,
                    DistrictType.RESIDENTIAL, DistrictType.OFFICE},
            new double[]{0.34, 0.30, 0.28, 0.08},
            new InfraType[]{InfraType.MOTEL, InfraType.SUPERMARKET, InfraType.CLINIC},
            new int[]{2, 1, 1}),

    CAMPUS("студенческий городок", 5, Footprint.ELLIPSE, 380, 540, 1.00, 1.40, 0.35, 5, 7, 0.95,
            new DistrictType[]{DistrictType.RESIDENTIAL, DistrictType.OFFICE,
                    DistrictType.PARK, DistrictType.COMMERCIAL},
            new double[]{0.34, 0.30, 0.20, 0.16},
            new InfraType[]{InfraType.SCHOOL, InfraType.SUPERMARKET,
                    InfraType.CLINIC, InfraType.POLICE},
            new int[]{3, 1, 1, 1}),

    MINING_TOWN("шахтёрский посёлок", 6, Footprint.CIRCLE, 260, 400, 1.00, 1.30, 0.45, 3, 5, 0.95,
            new DistrictType[]{DistrictType.RESIDENTIAL, DistrictType.INDUSTRIAL, DistrictType.COMMERCIAL},
            new double[]{0.42, 0.40, 0.18},
            new InfraType[]{InfraType.WAREHOUSE, InfraType.POWER_STATION, InfraType.CLINIC},
            new int[]{2, 1, 1}),

    PRISON_COMPLEX("тюремный комплекс", 3, Footprint.RECT, 240, 360, 1.10, 1.70, 0.05, 3, 4, 1.00,
            new DistrictType[]{DistrictType.MILITARY, DistrictType.OFFICE, DistrictType.RESIDENTIAL},
            new double[]{0.50, 0.30, 0.20},
            new InfraType[]{InfraType.PRISON, InfraType.POLICE,
                    InfraType.WAREHOUSE, InfraType.WATER_TOWER},
            new int[]{1, 1, 1, 1});

    public final String label;
    public final int weight;
    public final Footprint footprint;
    public final int minRadius, maxRadius;
    public final double minAspect, maxAspect;
    /** доля радиуса, на которой размывается кромка */
    public final double ragged;
    public final int minSeeds, maxSeeds;
    /** общий множитель ширины улиц поселения */
    public final double streetScale;
    public final DistrictType[] districts;
    /** нормированные целевые доли по площади, параллельно districts */
    public final double[] shares;
    public final InfraType[] infra;
    public final int[] infraCount;

    private static final TownTemplate[] VALUES = values();
    private static final int TOTAL_WEIGHT;
    static {
        int w = 0;
        for (TownTemplate t : VALUES) w += t.weight;
        TOTAL_WEIGHT = w;
    }

    TownTemplate(String label, int weight, Footprint footprint, int minRadius, int maxRadius,
                 double minAspect, double maxAspect, double ragged, int minSeeds, int maxSeeds,
                 double streetScale, DistrictType[] districts, double[] shares,
                 InfraType[] infra, int[] infraCount) {
        this.label = label;
        this.weight = weight;
        this.footprint = footprint;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.minAspect = minAspect;
        this.maxAspect = maxAspect;
        this.ragged = ragged;
        this.minSeeds = minSeeds;
        this.maxSeeds = maxSeeds;
        this.streetScale = streetScale;
        this.districts = districts;
        this.infra = infra;
        this.infraCount = infraCount;
        double sum = 0;
        for (double s : shares) sum += s;
        this.shares = new double[shares.length];
        for (int i = 0; i < shares.length; i++) this.shares[i] = shares[i] / sum;   // нормируем
    }

    /** Целевая доля типа района; 0, если шаблон его не предусматривает. */
    public double share(DistrictType d) {
        for (int i = 0; i < districts.length; i++) if (districts[i] == d) return shares[i];
        return 0;
    }

    public static TownTemplate pick(Random rnd) {
        int r = rnd.nextInt(TOTAL_WEIGHT);
        for (TownTemplate t : VALUES) {
            r -= t.weight;
            if (r < 0) return t;
        }
        return VALUES[VALUES.length - 1];
    }

    /** Самый большой полуразмер среди всех шаблонов — по нему считается запас до края ячейки. */
    public static int maxHalfSize() {
        int m = 0;
        for (TownTemplate t : VALUES) m = Math.max(m, t.maxRadius);
        return m;
    }
}
