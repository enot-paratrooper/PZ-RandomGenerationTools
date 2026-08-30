package mapgen.towns;

/**
 * Инфраструктурное здание: занимает участок целиком или, если не влезает, целый квартал.
 * Размер задан минимальным следом в пикселях, {@code preferred} — район, в котором его
 * пытаются разместить в первую очередь.
 *
 * <p>{@code outdoorOk} разрешает вынос за черту города, если внутри места не нашлось:
 * склад или кладбище на отшибе нормальны, полицейский участок или торговый центр — нет.
 */
public enum InfraType {
    HOSPITAL     ("больница",                  90, 70, DistrictType.DOWNTOWN,    true,  0xFFFFFF),
    CLINIC       ("поликлиника",               55, 45, DistrictType.COMMERCIAL,  true,  0xE8E8E8),
    SCHOOL       ("школа",                    110, 80, DistrictType.RESIDENTIAL, true,  0xFFE066),
    POLICE       ("полицейский участок",       60, 45, DistrictType.DOWNTOWN,    false, 0x8FB8FF),
    FIRE_STATION ("пожарная часть",            60, 50, DistrictType.RESIDENTIAL, false, 0xFF6A4D),
    SUPERMARKET  ("супермаркет",               90, 70, DistrictType.COMMERCIAL,  false, 0x9FE0FF),
    MALL         ("торговый центр",           160,120, DistrictType.COMMERCIAL,  false, 0xC8A8FF),
    GAS_STATION  ("заправка",                  45, 35, DistrictType.COMMERCIAL,  true,  0xFFB020),
    WAREHOUSE    ("склад",                    120, 80, DistrictType.INDUSTRIAL,  true,  0xBFAE60),
    POWER_STATION("электростанция",           130,110, DistrictType.INDUSTRIAL,  true,  0xF2F26A),
    WATER_TOWER  ("водонапорная башня",        34, 34, DistrictType.INDUSTRIAL,  true,  0xB8D8E8),
    CHURCH       ("церковь",                   55, 45, DistrictType.RESIDENTIAL, true,  0xE8D8B0),
    CEMETERY     ("кладбище",                 120,100, DistrictType.PARK,        true,  0x6E8C6E),
    MOTEL        ("мотель",                    90, 60, DistrictType.COMMERCIAL,  true,  0xFF9ED2),
    PRISON       ("тюрьма",                   200,160, DistrictType.MILITARY,    true,  0x9A5050),
    ARMORY       ("оружейный склад",           90, 70, DistrictType.MILITARY,    false, 0xFF4040),
    CHECKPOINT   ("КПП",                       36, 30, DistrictType.MILITARY,    true,  0xFF8080);

    public final String label;
    /** минимальный след здания, px */
    public final int minW, minH;
    public final DistrictType preferred;
    /** можно ли выносить за черту города */
    public final boolean outdoorOk;
    public final int color;

    private static final InfraType[] VALUES = values();

    InfraType(String label, int minW, int minH, DistrictType preferred, boolean outdoorOk, int color) {
        this.label = label;
        this.minW = minW;
        this.minH = minH;
        this.preferred = preferred;
        this.outdoorOk = outdoorOk;
        this.color = color;
    }

    public static InfraType byOrdinal(int i) { return VALUES[i]; }
    public static int count() { return VALUES.length; }
}
