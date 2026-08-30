package mapgen.colors;

import java.nio.file.Path;
import java.util.List;

/**
 * Набор цветов, которые генераторы пишут в слои.
 * Значения по умолчанию совпадают с colorsMap / colorsMap_veg;
 * метод {@link #validateAgainst} сверяет их с реальными файлами движка.
 *
 * <p>AI: покрыты все правила обоих файлов, включая асфальт, выбоины и грядки ферм, которых
 * раньше не было. Цвет здесь — не украшение: WorldEd сопоставляет пиксель с правилом по точному
 * совпадению RGB, и оттенок, которого нет в colorsMap, превращается в дырку на карте
 * (при {@code warn-unknown-colors} — ещё и в предупреждение).
 *
 * <p>Только final-поля, заполняемые при инициализации, — читается из всех потоков без синхронизации.
 */
public final class Palette {

    // ---------- базовая поверхность (bitmap 0) ----------
    public final int darkGrass   = 0x5A6423; // 90 100 35
    public final int mediumGrass = 0x75752F; // 117 117 47
    public final int lightGrass  = 0x91873C; // 145 135 60
    public final int sand        = 0xD2C8A0; // 210 200 160
    public final int dirt        = 0x784614; // 120 70 20
    public final int dirtGrass   = 0x503714; // 80 55 20
    public final int gravelDirt  = 0x8C460F; // 140 70 15
    public final int water       = 0x008AFF; // 0 138 255
    public final int rocksSmall  = 0x6E643C; // 110 100 60
    public final int rocksMedium = 0x827850; // 130 120 80
    public final int rocksLarge  = 0x96875A; // 150 135 90

    // ---------- дороги (bitmap 0) ----------
    /** AI: lightgravel — обочины, парковки, подъезды. */
    public final int lightAsphalt  = 0xA5A08C; // 165 160 140
    /** AI: street — обычная городская улица. */
    public final int mediumAsphalt = 0x787878; // 120 120 120
    /** AI: street2 — магистраль, самый тёмный асфальт. */
    public final int darkAsphalt   = 0x646464; // 100 100 100
    /** AI: blends_street_01_000 — тёмное затёртое пятно на полотне. */
    public final int darkPothole   = 0x6E6464; // 110 100 100
    /** AI: pothole — собственно выбоина. */
    public final int lightPothole  = 0x827878; // 130 120 120

    // ---------- грядки ферм (bitmap 0) ----------
    /** AI: пока не используется — ждёт застройщика ферм. */
    public final int dirtVertical   = 0x78644B; // 120 100 75
    /** AI: пока не используется — ждёт застройщика ферм. */
    public final int dirtHorizontal = 0x786E5F; // 120 110 95

    // ---------- растительность (bitmap 1) ----------
    public final int none            = 0x000000; // чёрный = ничего
    public final int trees           = 0xFF0000; // 255 0 0
    public final int denseTrees      = 0xC80000; // 200 0 0
    public final int treesGrass      = 0x7F0000; // 127 0 0
    public final int firTrees        = 0x400000; // 64 0 0
    public final int shortGrass      = 0x00FA00; // 0 250 0
    public final int longGrass       = 0x00FF00; // 0 255 0
    public final int grassFewTrees   = 0x008000; // 0 128 0
    public final int bushesFewTrees  = 0xFF00FF; // 255 0 255
    public final int denseBushes     = 0xC800C8; // 200 0 200
    public final int bushes          = 0x9600C8; // 150 0 200
    public final int sparseBushes    = 0x6400C8; // 100 0 200
    public final int farmCorn        = 0xFF8000; // 255 128 0
    public final int farmCorn2       = 0xDC6400; // 220 100 0
    public final int burnedTrees     = 0x501400; // 80 20 0

    public boolean isGrass(int base) { return base == darkGrass || base == mediumGrass || base == lightGrass; }
    public boolean isWater(int base) { return base == water; }

    /**
     * AI: покрытие дороги — асфальт любого класса вместе с износом. Пригодится тем, кто не должен
     * писать поверх полотна: озеленению, застройщику, будущим мостам.
     */
    public boolean isRoad(int base) {
        return base == darkAsphalt || base == mediumAsphalt || base == lightAsphalt
                || base == darkPothole || base == lightPothole;
    }

    /** Сверяет константы с файлами движка; печатает расхождения. */
    public void validateAgainst(Path baseMap, Path vegMap) throws Exception {
        List<ColorRule> base = ColorMapLoader.load(baseMap);
        List<ColorRule> veg  = ColorMapLoader.load(vegMap);

        check(base, "Dark Grass", darkGrass);       check(base, "Medium Grass", mediumGrass);
        check(base, "Light Grass", lightGrass);     check(base, "Sand", sand);
        check(base, "Dirt", dirt);                  check(base, "Dirt Grass", dirtGrass);
        check(base, "Gravel Dirt", gravelDirt);     check(base, "Water", water);
        check(base, "Rockssmall", rocksSmall);      check(base, "Rocksmedium", rocksMedium);
        check(base, "Rockslarge", rocksLarge);
        // AI: дороги и грядки
        check(base, "Light Asphalt", lightAsphalt); check(base, "Medium Asphalt", mediumAsphalt);
        check(base, "Dark Asphalt", darkAsphalt);   check(base, "Dark Pothole", darkPothole);
        check(base, "Light Pothole", lightPothole);
        check(base, "Dirtvertical", dirtVertical);  check(base, "Dirthorizontal", dirtHorizontal);

        check(veg, "Trees", trees);                 check(veg, "Dense Trees", denseTrees);
        check(veg, "Trees + Dark", treesGrass);     check(veg, "Fir Trees", firTrees);
        check(veg, "Dark short grass", shortGrass); check(veg, "Dark long grass", longGrass);
        check(veg, "Grass + Few Trees", grassFewTrees);
        check(veg, "Bushes, Grass, Few Trees", bushesFewTrees);
        check(veg, "Dense Bushes", denseBushes);    check(veg, "Bushes & Grass", bushes);
        check(veg, "Sparse Bushes", sparseBushes);
        check(veg, "Farm Corn (dead)", farmCorn);   check(veg, "Farm Corn 2", farmCorn2);
        check(veg, "BurnedTrees", burnedTrees);
    }

    /**
     * AI: сначала точное совпадение метки, и только потом префикс. Иначе порядок правил в файле
     * начинает влиять на результат: {@code "Dirt"} — префикс и у {@code "Dirt Grass"},
     * и у {@code "Dirtvertical"}, и стоит им переехать выше — проверка молча сверит не тот цвет.
     */
    private static void check(List<ColorRule> rules, String label, int expected) {
        rules.stream().filter(r -> r.label().equals(label)).findFirst()
                .or(() -> rules.stream().filter(r -> r.label().startsWith(label)).findFirst())
                .ifPresentOrElse(
                        r -> { if (r.color() != expected)
                            System.err.printf("ПАЛИТРА: '%s' в файле = %s, в коде = %s%n",
                                    r.label(), ColorRule.rgbToString(r.color()),
                                    ColorRule.rgbToString(expected)); },
                        () -> System.err.printf("ПАЛИТРА: правило '%s' не найдено в файле%n", label));
    }
}
