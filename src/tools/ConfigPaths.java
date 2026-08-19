package tools;

/**
 * [AI] НОВЫЙ КЛАСС (добавлен AI).
 *
 * Раньше пути к conf-файлам были захардкожены в трёх местах
 * (Room.loadRoom, RandomGroup.loadRandomGroups, RandomCollection.loadRandomCollection).
 * Для загрузки здания добавляется четвёртый путь, поэтому все пути собраны здесь.
 *
 * Достаточно поменять ROOT, чтобы проект заработал на другой машине.
 */
public class ConfigPaths {

    /** Корневая папка conf. Менять здесь и только здесь. */
    public static String ROOT = "..\\RandomRoomGenerator\\conf\\";

    public static String buildingFile(String name) {
        return ROOT + "RandomBuilding\\Building_" + name + ".xml";
    }

    public static String roomFile(String name) {
        return ROOT + "RandomRoom\\Room_" + name + ".xml";
    }

    public static String groupFile(String name) {
        return ROOT + "RandomGroups\\Group_" + name + ".xml";
    }

    public static String collectionFile(String name) {
        return ROOT + "RandomCollections\\Collection_" + name + ".xml";
    }
}
