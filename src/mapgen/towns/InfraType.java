package mapgen.towns;

/**
 * Инфраструктурное здание: занимает участок целиком или, если не влезает, целый квартал.
 * Размер задан минимальным следом в пикселях, {@code preferred} — район, в котором его
 * пытаются разместить в первую очередь.
 *
 * <p>{@code outdoorOk} разрешает вынос за черту города, если внутри места не нашлось:
 * склад или кладбище на отшибе нормальны, полицейский участок или торговый центр — нет.
 *
 * <p>AI: добавлен {@code available} — есть ли под этот тип хоть одна постройка в пуле .tbx.
 * Отключённые типы {@link TownField} пропускает молча: раньше они честно перебирали
 * 50 попыток внутри города и 50 за чертой, сыпали в stderr и оседали в
 * {@link Town#missingInfra()}, хотя закрыть их всё равно нечем — постройки не существует.
 * Сами константы оставлены на месте, чтобы шаблоны городов продолжали читаться как ТЗ:
 * когда нужный .tbx появится в пуле, достаточно вернуть {@code true}.
 */
public enum InfraType {
    HOSPITAL     ("больница",                  90, 70, DistrictType.DOWNTOWN,    true,  0xFFFFFF, true),
    CLINIC       ("поликлиника",               55, 45, DistrictType.COMMERCIAL,  true,  0xE8E8E8, true),
    SCHOOL       ("школа",                    110, 80, DistrictType.RESIDENTIAL, true,  0xFFE066, true),
    POLICE       ("полицейский участок",       60, 45, DistrictType.DOWNTOWN,    false, 0x8FB8FF, true),
    FIRE_STATION ("пожарная часть",            60, 50, DistrictType.RESIDENTIAL, false, 0xFF6A4D, true),
    SUPERMARKET  ("супермаркет",               90, 70, DistrictType.COMMERCIAL,  false, 0x9FE0FF, true),
    /** AI: ОТКЛЮЧЁН — молла в пуле нет, есть только секции стрип-молла {@code lot_plaza_*_mall}. */
    MALL         ("торговый центр",           160,120, DistrictType.COMMERCIAL,  false, 0xC8A8FF, false),
    GAS_STATION  ("заправка",                  45, 35, DistrictType.COMMERCIAL,  true,  0xFFB020, true),
    WAREHOUSE    ("склад",                    120, 80, DistrictType.INDUSTRIAL,  true,  0xBFAE60, true),
    /** AI: электростанции как таковой нет, встаёт фабрика 60x60 — замена приемлемая. */
    POWER_STATION("электростанция",           130,110, DistrictType.INDUSTRIAL,  true,  0xF2F26A, true),
    WATER_TOWER  ("водонапорная башня",        34, 34, DistrictType.INDUSTRIAL,  true,  0xB8D8E8, true),
    CHURCH       ("церковь",                   55, 45, DistrictType.RESIDENTIAL, true,  0xE8D8B0, true),
    /** AI: ОТКЛЮЧЁН — в PARK_PUBLIC только беседки, площадки и остановки, кладбища нет. */
    CEMETERY     ("кладбище",                 120,100, DistrictType.PARK,        true,  0x6E8C6E, false),
    MOTEL        ("мотель",                    90, 60, DistrictType.COMMERCIAL,  true,  0xFF9ED2, true),
    /** AI: ОТКЛЮЧЁН — 0 совпадений по {@code prison|jail|penitentiary} во всём пуле. */
    PRISON       ("тюрьма",                   200,160, DistrictType.MILITARY,    true,  0x9A5050, false),
    ARMORY       ("оружейный склад",           90, 70, DistrictType.MILITARY,    false, 0xFF4040, true),
    CHECKPOINT   ("КПП",                       36, 30, DistrictType.MILITARY,    true,  0xFF8080, true);

    public final String label;
    /** минимальный след здания, px */
    public final int minW, minH;
    public final DistrictType preferred;
    /** можно ли выносить за черту города */
    public final boolean outdoorOk;
    public final int color;
    /** AI: есть ли под этот тип постройка в пуле .tbx */
    public final boolean available;

    private static final InfraType[] VALUES = values();

    InfraType(String label, int minW, int minH, DistrictType preferred, boolean outdoorOk,
              int color, boolean available) {
        this.label = label;
        this.minW = minW;
        this.minH = minH;
        this.preferred = preferred;
        this.outdoorOk = outdoorOk;
        this.color = color;
        this.available = available;
    }

    public static InfraType byOrdinal(int i) { return VALUES[i]; }
    public static int count() { return VALUES.length; }
}
