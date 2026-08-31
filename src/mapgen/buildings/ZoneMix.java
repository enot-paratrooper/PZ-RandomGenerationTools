package mapgen.buildings;

import mapgen.towns.DistrictType;
import mapgen.towns.InfraType;

import java.util.Random;

/**
 * AI: Мост между геометрией города и пулом построек: чем застраивать район и какой .tbx
 * подставить под инфраструктурное здание.
 *
 * <p>Долевой состав районов взят из раздела 4 отчёта по пулу. Отличие одно: в отчёте районы
 * названы по-градостроительному («Downtown», «Commercial strip»), а в генераторе живёт
 * {@link DistrictType} из восьми значений, поэтому «Suburb» лёг на {@code RESIDENTIAL},
 * «Rural» — на {@code FARM}, а «Civic core» отдельным районом не выделен: его зоны
 * подмешаны в {@code DOWNTOWN} и {@code OFFICE}.
 *
 * <p>Отдельной зоны под офисы в пуле нет — офисные постройки лежат в {@code COMMERCIAL_RETAIL}
 * с ролью {@code office}, поэтому у района {@code OFFICE} основная зона та же, что у
 * коммерческого, только с другими весами.
 *
 * <p>{@code FILLER_PROP} и {@code SPECIAL_LANDMARK} в веса не входят: первый идёт вторым
 * проходом внутри уже застроенного участка, второй — по семенным точкам.
 *
 * <p>Класс без состояния, все методы статические.
 */
public final class ZoneMix {

    private ZoneMix() { }

    /** Одна строка долевого состава района. */
    private record Slice(BuildingZone zone, double weight) { }

    private static final Slice[][] BY_DISTRICT = new Slice[DistrictType.count()][];

    static {
        BY_DISTRICT[DistrictType.DOWNTOWN.ordinal()] = new Slice[]{
                new Slice(BuildingZone.COMMERCIAL_RETAIL, 0.45),
                new Slice(BuildingZone.FOOD_ENTERTAINMENT, 0.25),
                new Slice(BuildingZone.RESIDENTIAL_DENSE, 0.20),
                new Slice(BuildingZone.CIVIC, 0.10)};

        BY_DISTRICT[DistrictType.OFFICE.ordinal()] = new Slice[]{
                new Slice(BuildingZone.COMMERCIAL_RETAIL, 0.55),
                new Slice(BuildingZone.CIVIC, 0.20),
                new Slice(BuildingZone.FOOD_ENTERTAINMENT, 0.15),
                new Slice(BuildingZone.RESIDENTIAL_DENSE, 0.10)};

        BY_DISTRICT[DistrictType.COMMERCIAL.ordinal()] = new Slice[]{
                new Slice(BuildingZone.COMMERCIAL_RETAIL, 0.50),
                new Slice(BuildingZone.AUTOMOTIVE_FUEL, 0.25),
                new Slice(BuildingZone.FOOD_ENTERTAINMENT, 0.25)};

        BY_DISTRICT[DistrictType.RESIDENTIAL.ordinal()] = new Slice[]{
                new Slice(BuildingZone.RESIDENTIAL_LOW, 0.90),
                new Slice(BuildingZone.PARK_PUBLIC, 0.10)};

        BY_DISTRICT[DistrictType.INDUSTRIAL.ordinal()] = new Slice[]{
                new Slice(BuildingZone.INDUSTRIAL, 0.85),
                new Slice(BuildingZone.AUTOMOTIVE_FUEL, 0.15)};

        BY_DISTRICT[DistrictType.MILITARY.ordinal()] = new Slice[]{
                new Slice(BuildingZone.MILITARY, 1.00)};

        // AI: у PARK нулевое coverage, участков там не бывает; строка нужна только чтобы
        // таблица была полной и обращение по ordinal не давало null.
        BY_DISTRICT[DistrictType.PARK.ordinal()] = new Slice[]{
                new Slice(BuildingZone.PARK_PUBLIC, 1.00)};

        BY_DISTRICT[DistrictType.FARM.ordinal()] = new Slice[]{
                new Slice(BuildingZone.RURAL_FARM, 0.50),
                new Slice(BuildingZone.RESIDENTIAL_LOW, 0.30),
                new Slice(BuildingZone.TRAILER_PARK, 0.20)};
    }

    /**
     * Зона для очередного участка района. Веса нормируются на лету: доли в таблице заданы
     * «на глаз» и в сумме не обязаны давать ровно единицу.
     */
    public static BuildingZone zoneFor(DistrictType district, Random rnd) {
        Slice[] mix = BY_DISTRICT[district.ordinal()];
        double total = 0;
        for (Slice s : mix) total += s.weight();
        double roll = rnd.nextDouble() * total;
        for (Slice s : mix) {
            roll -= s.weight();
            if (roll < 0) return s.zone();
        }
        return mix[mix.length - 1].zone();
    }

    /** Зона, из которой берётся .tbx под инфраструктурное здание. */
    public static BuildingZone zoneFor(InfraType type) {
        return switch (type) {
            case HOSPITAL, CLINIC -> BuildingZone.MEDICAL;
            case SCHOOL, POLICE, FIRE_STATION, CHURCH -> BuildingZone.CIVIC;
            case SUPERMARKET, MALL -> BuildingZone.COMMERCIAL_RETAIL;
            case GAS_STATION -> BuildingZone.AUTOMOTIVE_FUEL;
            case WAREHOUSE, POWER_STATION, WATER_TOWER -> BuildingZone.INDUSTRIAL;
            case CEMETERY -> BuildingZone.PARK_PUBLIC;
            case MOTEL -> BuildingZone.RESIDENTIAL_DENSE;
            case PRISON, ARMORY, CHECKPOINT -> BuildingZone.MILITARY;
        };
    }

    /**
     * Роль внутри зоны или {@code null}, если подойдёт любая постройка зоны.
     *
     * <p>Строки для {@link InfraType#PRISON}, {@link InfraType#MALL} и
     * {@link InfraType#CEMETERY} оставлены, но до застройщика эти типы не доходят: у них
     * снят {@code available}, и {@code TownField} их не размещает — постройки в пуле нет.
     * Таблица держится полной, чтобы включение типа обратно требовало правки в одном месте,
     * а не поиска по двум файлам.
     */
    public static String roleFor(InfraType type) {
        return switch (type) {
            case HOSPITAL, CLINIC -> "medical";
            case SCHOOL -> "education";
            case POLICE -> "police";
            case FIRE_STATION -> "fire";
            case CHURCH -> "religion";
            case SUPERMARKET, MALL -> "retail";
            case WAREHOUSE, POWER_STATION, WATER_TOWER -> "industrial";
            case CEMETERY -> "park";
            case MOTEL -> "hospitality";
            case ARMORY, CHECKPOINT -> "military";
            case GAS_STATION, PRISON -> null;
        };
    }

    /**
     * Зона мелочи, которой добивается двор уже застроенного участка. Вынесена сюда, чтобы
     * правило «во дворе фермы стоит сарай, а во дворе промки — бытовка» жило рядом с весами.
     */
    public static BuildingZone fillerZoneFor(DistrictType district) {
        return district == DistrictType.INDUSTRIAL || district == DistrictType.MILITARY
                ? BuildingZone.INDUSTRIAL
                : BuildingZone.FILLER_PROP;
    }

    /** Роль мелочи во дворе: у жилья и ферм — хозпостройка, у промки — бытовка. */
    public static String fillerRoleFor(DistrictType district) {
        return switch (district) {
            case INDUSTRIAL, MILITARY -> "site_office";
            case FARM, RESIDENTIAL -> "outbuilding";
            default -> null;
        };
    }
}
