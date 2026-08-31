package mapgen.buildings;

/**
 * AI: Одна постройка пула: путь к .tbx относительно каталога {@code buildings}, габарит в тайлах
 * и метки классификации.
 *
 * <p>{@code facing} — сторона, в которую смотрит фасад <b>в исходном файле</b>. Классификатор
 * проставил её всем записям, а не только тем 46, у которых сторона зашита в имени
 * ({@code _N}/{@code _S}/{@code _E}/{@code _W}). Мы этим пользуемся: вместо запрета на поворот
 * фасад <i>доворачивается</i> к улице через {@link RotatedTbxCache}, поэтому дома вдоль улицы
 * стоят лицом к дороге, а не как попало.
 *
 * <p>{@code family} — «семейство» из отчёта: постройки с общим корнем имени. Используется как
 * штраф на соседство, чтобы пять домов одной серии не встали подряд.
 *
 * <p>Путь хранится с разделителем {@code '/'} — в манифесте он windows-овый, но в атрибут
 * {@code type} .tmx и в {@code Path.resolve} одинаково годится прямой слэш.
 */
public record BuildingDef(String path, int w, int h, String tier, String role,
                          char facing, String family, String category, BuildingZone zone) {

    /** Порядок сторон по часовой стрелке: поворот вправо сдвигает индекс на +1. */
    public static final char[] FACINGS = { 'N', 'E', 'S', 'W' };

    public int area()    { return w * h; }
    public int maxSide() { return Math.max(w, h); }
    public int minSide() { return Math.min(w, h); }

    /** Вытянутость: у «поезда» 27x197 она равна 7.3, такие в квартал не ставятся. */
    public double aspect() { return maxSide() / (double) Math.max(1, minSide()); }

    /** Ширина после {@code turns} поворотов на 90 градусов по часовой стрелке. */
    public int widthAfter(int turns)  { return (turns & 1) == 0 ? w : h; }
    /** Высота после {@code turns} поворотов на 90 градусов по часовой стрелке. */
    public int heightAfter(int turns) { return (turns & 1) == 0 ? h : w; }

    /** Помещается ли хоть в какой-нибудь ориентации в участок {@code lotW x lotH}. */
    public boolean fits(int lotW, int lotH) {
        return (w <= lotW && h <= lotH) || (h <= lotW && w <= lotH);
    }

    /** Помещается ли после заданного числа поворотов. */
    public boolean fitsAfter(int turns, int lotW, int lotH) {
        return widthAfter(turns) <= lotW && heightAfter(turns) <= lotH;
    }

    /** Индекс {@link #facing} в {@link #FACINGS}; -1, если сторона не распознана. */
    public int facingIndex() {
        for (int i = 0; i < FACINGS.length; i++) if (FACINGS[i] == facing) return i;
        return -1;
    }

    /**
     * Сколько поворотов вправо нужно, чтобы фасад смотрел в сторону {@code want}
     * (индекс в {@link #FACINGS}). Если исходная сторона неизвестна — 0.
     */
    public int turnsTo(int want) {
        int have = facingIndex();
        return have < 0 ? 0 : ((want - have) & 3);
    }

    /** Имя файла без каталогов и расширения — для диагностики и имён повёрнутых копий. */
    public String baseName() {
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
