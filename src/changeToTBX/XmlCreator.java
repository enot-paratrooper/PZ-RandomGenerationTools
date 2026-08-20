package changeToTBX;

import containers.CommonData;
import java.io.File;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import randBuildingGen.Building;
import randRoomGen.Collection;
import randRoomGen.Room;
import randRoomGen.UsedFurniture;
import randRoomGen.ObjectTiles.Door;
import randRoomGen.ObjectTiles.ObjectTile;
import randRoomGen.ObjectTiles.Windows;

public class XmlCreator {

    /**
     * Создает XML файл с помощью DOM.
     *
     * Версия для одной комнаты. Оставлена для тестов (roomGenerator, Generator):
     * одна комната, один этаж, параметры здания нулевые.
     */
    public static void createBuildingXml(String filePath, CommonData data, Room room) {
        try {
            // Создаем DocumentBuilder
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Создаем документ
            Document doc = docBuilder.newDocument();

            // Создаем корневой элемент building
            Element buildingElement = doc.createElement("building");
            doc.appendChild(buildingElement);

            // Добавляем атрибуты
            buildingElement.setAttribute("version", "2");
            buildingElement.setAttribute("width", Integer.toString(room.getSizeX()));
            buildingElement.setAttribute("height", Integer.toString(room.getSizeY()));
            // Внешние стены и крыша обрабатываются отдельным классом
            buildingElement.setAttribute("ExteriorWall", "0");
            buildingElement.setAttribute("ExteriorWallTrim", "0");
            buildingElement.setAttribute("Door", "0");
            buildingElement.setAttribute("DoorFrame", "0");
            buildingElement.setAttribute("Window", "0");
            buildingElement.setAttribute("Curtains", "0");
            buildingElement.setAttribute("Shutters", "0");
            buildingElement.setAttribute("Stairs", "0");
            buildingElement.setAttribute("RoofCap", "0");
            buildingElement.setAttribute("RoofSlope", "0");
            buildingElement.setAttribute("RoofTop", "0");
            buildingElement.setAttribute("GrimeWall", "0");

            // Запись TileSet и мебели
            appendTileSets(doc, buildingElement, data);
            appendFurnitureSets(doc, buildingElement, data);

            //Запись использованых user_tiles и user_tiles 
            Element usedUserTiles = doc.createElement("user_tiles");
            buildingElement.appendChild(usedUserTiles);

            Element usedTilesElement = doc.createElement("used_tiles");
            usedTilesElement.setTextContent(data.getUsedTiles());
            buildingElement.appendChild(usedTilesElement);

            // Инициализация used_furniture
            Element usedFurnitureElement = doc.createElement("used_furniture");
            buildingElement.appendChild(usedFurnitureElement);

            //Запись параметров room
            Element parametersRoomElement = doc.createElement("room");
            parametersRoomElement.setAttribute("Name", room.getName());
            parametersRoomElement.setAttribute("InternalName", room.getName().toLowerCase());
            parametersRoomElement.setAttribute("Color", room.getColor());
            parametersRoomElement.setAttribute("InteriorWall", Integer.toString(room.getInteriorWall()));
            parametersRoomElement.setAttribute("InteriorWallTrim", Integer.toString(room.getInteriorWallTrim()));
            parametersRoomElement.setAttribute("Floor", Integer.toString(room.getFloor()));
            parametersRoomElement.setAttribute("GrimeFloor", Integer.toString(room.getGrimeFloor()));
            parametersRoomElement.setAttribute("GrimeWall", Integer.toString(room.getGrimeWall()));
            buildingElement.appendChild(parametersRoomElement);

            //Запись rooms
            Element floorElement = doc.createElement("floor");
            //Запись использованной мебели
            for (UsedFurniture furniture : data.getUsedFurniture()) {
                floorElement.appendChild(createFurnitureObject(doc, furniture));
            }
            //Запись дверей, окон и лестниц
            SetOpenings(floorElement, data, room, doc);

            usedFurnitureElement.setTextContent(data.getUsedFurnitureTiles());
            Element roomsElement = doc.createElement("rooms");
            roomsElement.setTextContent("\n" + room.getRoomCellsString() + "\n");
            floorElement.appendChild(roomsElement);
            buildingElement.appendChild(floorElement);

            writeDocument(doc, filePath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * AI: новая перегрузка. Создание .tbx для здания целиком.
     *
     * Отличия от версии для одной комнаты:
     *  - размеры и параметры берутся из Building;
     *  - в шапку пишется по элементу <room .../> на каждую комнату,
     *    порядок совпадает со сквозной нумерацией в Building.buildingCells;
     *  - на каждый этаж пишется свой блок <floor> со своими объектами
     *    и своей сеткой <rooms>.
     *
     * Ожидаемый порядок вызовов:
     *   building.loadBuilding(name);
     *   data.MergeCollections();
     *   data.DetermineListType();
     *   data.InitTileSets();
     *   data.InitLinks();
     *   building.InitBuildingparameters();
     *   for (Room room : data.randomRooms) room.InitRoomparameters();
     *   XmlCreator.createBuildingXml(path, data, building);
     */
    public static void createBuildingXml(String filePath, CommonData data, Building building) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            Element buildingElement = doc.createElement("building");
            doc.appendChild(buildingElement);

            // --- Параметры здания ---
            buildingElement.setAttribute("version", Integer.toString(building.getVersion()));
            buildingElement.setAttribute("width", Integer.toString(building.getWidth()));
            buildingElement.setAttribute("height", Integer.toString(building.getHeight()));
            buildingElement.setAttribute("ExteriorWall", Integer.toString(building.getExteriorWall()));
            buildingElement.setAttribute("ExteriorWallTrim", Integer.toString(building.getExteriorWallTrim()));
            buildingElement.setAttribute("Door", Integer.toString(building.getDoor()));
            buildingElement.setAttribute("DoorFrame", Integer.toString(building.getDoorFrame()));
            buildingElement.setAttribute("Window", Integer.toString(building.getWindow()));
            buildingElement.setAttribute("Curtains", Integer.toString(building.getCurtains()));
            buildingElement.setAttribute("Shutters", Integer.toString(building.getShutters()));
            buildingElement.setAttribute("Stairs", Integer.toString(building.getStairs()));
            buildingElement.setAttribute("RoofCap", Integer.toString(building.getRoofCap()));
            buildingElement.setAttribute("RoofSlope", Integer.toString(building.getRoofSlope()));
            buildingElement.setAttribute("RoofTop", Integer.toString(building.getRoofTop()));
            buildingElement.setAttribute("GrimeWall", Integer.toString(building.getGrimeWall()));

            // --- Наборы тайлов и мебели, общие для всего здания ---
            appendTileSets(doc, buildingElement, data);
            appendFurnitureSets(doc, buildingElement, data);

            Element usedUserTiles = doc.createElement("user_tiles");
            buildingElement.appendChild(usedUserTiles);

            Element usedTilesElement = doc.createElement("used_tiles");
            usedTilesElement.setTextContent(data.getUsedTiles());
            buildingElement.appendChild(usedTilesElement);

            Element usedFurnitureElement = doc.createElement("used_furniture");
            buildingElement.appendChild(usedFurnitureElement);

            // --- Описания комнат. Порядок задаёт сквозную нумерацию в сетке rooms ---
            for (Room room : data.randomRooms) {
                Element parametersRoomElement = doc.createElement("room");
                // Имя должно быть уникальным, иначе TileZed схлопнет одинаковые комнаты.
                parametersRoomElement.setAttribute("Name", room.getName() + "_" + room.getRoomNumber());
                parametersRoomElement.setAttribute("InternalName", room.getName().toLowerCase());
                parametersRoomElement.setAttribute("Color", room.getColor());
                parametersRoomElement.setAttribute("InteriorWall", Integer.toString(room.getInteriorWall()));
                parametersRoomElement.setAttribute("InteriorWallTrim", Integer.toString(room.getInteriorWallTrim()));
                parametersRoomElement.setAttribute("Floor", Integer.toString(room.getFloor()));
                parametersRoomElement.setAttribute("GrimeFloor", Integer.toString(room.getGrimeFloor()));
                parametersRoomElement.setAttribute("GrimeWall", Integer.toString(room.getGrimeWall()));
                buildingElement.appendChild(parametersRoomElement);
            }

            // --- Этажи ---
            // getUsedFurniture() дописывает UsedFurnitureTiles, поэтому вызываем
            // его один раз и дальше раскладываем результат по этажам.
            java.util.List<UsedFurniture> allFurniture = data.getUsedFurniture();
            usedFurnitureElement.setTextContent(data.getUsedFurnitureTiles());

            for (int floor = 0; floor < building.getFloorCount(); floor++) {
                Element floorElement = doc.createElement("floor");

                for (UsedFurniture furniture : allFurniture) {
                    if (furniture.floor != floor) {
                        continue;
                    }
                    floorElement.appendChild(createFurnitureObject(doc, furniture));
                }

                SetOpenings(floorElement, data, building, doc, floor);

                Element roomsElement = doc.createElement("rooms");
                roomsElement.setTextContent("\n" + building.getRoomCellsString(floor) + "\n");
                floorElement.appendChild(roomsElement);

                buildingElement.appendChild(floorElement);
            }

            writeDocument(doc, filePath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =====================================================================
    // Общие части обеих перегрузок
    // =====================================================================

    /**
     * Запись TileSet.
     * Нумерация сквозная по всем tile_entry и должна совпадать с нумерацией
     * в Room.InitRoomparameters и Building.InitBuildingparameters,
     * иначе ссылки Door/Window/Floor уедут.
     */
    private static void appendTileSets(Document doc, Element buildingElement, CommonData data) {
        int iterTileSet = 1;
        for (Collection coll : data.usedTile) {
            String TypeOfRoomparametr = coll.GetParameter();
            data.UsedTiles.add(iterTileSet);
            iterTileSet++;
            switch (TypeOfRoomparametr) {
            case "InteriorWall":
                appendTileEntry(doc, buildingElement, "interior_walls", directionForWalls, coll);
                break;
            case "InteriorWallTrim":
                appendTileEntry(doc, buildingElement, "interior_wall_trim", directionForWalls, coll);
                break;
            // AI: параметры уровня здания
            case "ExteriorWall":
                appendTileEntry(doc, buildingElement, "exterior_walls", directionForWalls, coll);
                break;
            case "ExteriorWallTrim":
                appendTileEntry(doc, buildingElement, "exterior_wall_trim", directionForWalls, coll);
                break;
            case "Floor":
                appendTileEntry(doc, buildingElement, "floors", directionForFloors, coll);
                break;
            case "GrimeFloor":
                appendTileEntry(doc, buildingElement, "grime_floor", directionForFloorGrime, coll);
                break;
            case "GrimeWall":
                appendTileEntry(doc, buildingElement, "grime_wall", directionForGrimeWall, coll);
                break;
            case "Door":
                appendTileEntry(doc, buildingElement, "doors", directionForDoors, coll);
                break;
            case "DoorFrame":
                appendTileEntry(doc, buildingElement, "door_frames", directionForDoorFrames, coll);
                break;
            case "Window":
                appendTileEntry(doc, buildingElement, "windows", directionForWindows, coll);
                break;
            case "Curtains":
                appendTileEntry(doc, buildingElement, "curtains", directionForCurtains, coll);
                break;
            case "Shutters":
                appendTileEntry(doc, buildingElement, "shutters", directionForShutters, coll);
                break;
            case "Stairs":
                appendTileEntry(doc, buildingElement, "stairs", directionForStairs, coll);
                break;
            // AI: крыша пока не поддержана - у roof_caps и roof_slopes десятки
            // enum-ов, часть с пустыми тайлами, механизм коллекций так не умеет.
            case "RoofCap":
            case "RoofSlope":
            case "RoofTop":
                throw new IllegalArgumentException("Параметр крыши '" + TypeOfRoomparametr
                        + "' пока не поддержан, крыша обрабатывается отдельным классом");
            default:
                throw new IllegalArgumentException("Неизвестный параметр комнаты: " + TypeOfRoomparametr);
            }
        }
    }

    private static void appendFurnitureSets(Document doc, Element buildingElement, CommonData data) {
        for (Collection coll : data.usedFurniture) {

            Element furnitureElement = doc.createElement("furniture");
            // Слой отрисовки, например WallFurniture для настенного декора
            String layer = coll.getLayer();
            if (layer != null && !layer.isEmpty()) {
                furnitureElement.setAttribute("layer", layer);
            }
            if (coll.hasRuleOfPlacing()) {
                SetTilesForFurnitureWithRules(furnitureElement, coll.getPickedFurnitureWithRules(), doc);
            } else {
                // corners относится только к мебели с восемью направлениями.
                // У многоплиточной мебели восемь индексов - это несколько
                // направлений по несколько тайлов, и corners там не нужен.
                if (coll.getPickedFurniture().size() == 8) {
                    furnitureElement.setAttribute("corners", "true");
                }
                SetTilesForFurniture(furnitureElement, coll.getPickedFurniture(), doc);
            }
            buildingElement.appendChild(furnitureElement);

        }
    }

    private static Element createFurnitureObject(Document doc, UsedFurniture furniture) {
        Element objectElement = doc.createElement("object");
        objectElement.setAttribute("type", "furniture");
        objectElement.setAttribute("FurnitureTiles", furniture.FurnitureTiles);
        objectElement.setAttribute("orient", furniture.orient);
        objectElement.setAttribute("x", furniture.x);
        objectElement.setAttribute("y", furniture.y);
        return objectElement;
    }

    private static void writeDocument(Document doc, String filePath) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();

        // Форматирование: с отступами
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(filePath));
        transformer.transform(source, result);

        System.out.println("XML файл создан: " + filePath);
    }

    private static final String[] directionForWalls = {"West", "North", "NorthWest", "SouthEast", "WestWindow", "NorthWindow",
        "WestDoor", "NorthDoor"};
    private static final String[] directionForGrimeWall = {"West", "North", "NorthWest", "SouthEast", "WestWindow", "NorthWindow",
        "WestDoor", "NorthDoor", "WestTrim", "NorthTrim", "NorthWestTrim", "SouthEastTrim", "WestDoubleLeft", "WestDoubleRight",
        "NorthDoubleLeft", "NorthDoubleRight"};
    private static final String[] directionForFloorGrime = {"West", "North", "East", "South", "SouthWest", "NorthWest",
        "NorthEast", "SouthEast"};
    private static final String[] directionForFloors = {"Floor"};
    private static final String[] directionForDoors = {"West", "North", "WestOpen", "NorthOpen"};
    private static final String[] directionForDoorFrames = {"West", "North"};
    private static final String[] directionForWindows = {"West", "North"};
    private static final String[] directionForCurtains = {"West", "East", "North", "South"};
    private static final String[] directionForShutters = {"WestBelow", "WestAbove", "NorthLeft", "NorthRight"};
    private static final String[] directionForStairs = {"West1", "West2", "West3", "North1", "North2", "North3"};
    private static final String[] directionForFurniture1Sides = {"W"};
    private static final String[] directionForFurniture2Sides = {"W", "N"};
    private static final String[] directionForFurniture4Sides = {"W", "N", "E", "S"};
    private static final String[] directionForFurniture8Sides = {"W", "N", "E", "S", "SW", "NW", "NE", "SE"};

    /**
     * Общая запись tile_entry для любой категории.
     * Заменяет разошедшиеся SetTilesForWalls/Floors/GrimeWall/FloorGrime и
     * проверяет, что в наборе ровно столько тайлов, сколько ждёт категория.
     */
    private static void appendTileEntry(Document doc, Element buildingElement, String category,
            String[] tileEnums, Collection coll) {
        ArrayList<String> pickedTileset = coll.getPickedTileset();
        int size = (pickedTileset == null) ? 0 : pickedTileset.size();
        if (size != tileEnums.length) {
            throw new IllegalArgumentException("Категория '" + category + "' ожидает "
                    + tileEnums.length + " тайлов, а в наборе '" + coll.getNameOfPickedTileSet()
                    + "' их " + size + ". Проверьте tileDirCount в файле коллекции.");
        }
        Element tileEntryElement = doc.createElement("tile_entry");
        tileEntryElement.setAttribute("category", category);
        for (int i = 0; i < size; i++) {
            Element tileElement = doc.createElement("tile");
            tileElement.setAttribute("enum", tileEnums[i]);
            tileElement.setAttribute("tile", pickedTileset.get(i));
            tileEntryElement.appendChild(tileElement);
        }
        buildingElement.appendChild(tileEntryElement);
    }

    // =====================================================================
    // Проёмы
    // =====================================================================

    /** AI: наборы тайлов по умолчанию для проёма. */
    private static class TileDefaults {
        int door;
        int doorFrame;
        int window;
        int curtains;
        int shutters;
        int stairs;
    }

    private static TileDefaults defaultsFromRoom(Room room) {
        TileDefaults defaults = new TileDefaults();
        defaults.door = room.getDoor();
        defaults.doorFrame = room.getDoorFrame();
        defaults.window = room.getWindow();
        defaults.curtains = room.getCurtains();
        defaults.shutters = room.getShutters();
        defaults.stairs = room.getStairs();
        return defaults;
    }

    private static TileDefaults defaultsFromBuilding(Building building) {
        TileDefaults defaults = new TileDefaults();
        defaults.door = building.getDoor();
        defaults.doorFrame = building.getDoorFrame();
        defaults.window = building.getWindow();
        defaults.curtains = building.getCurtains();
        defaults.shutters = building.getShutters();
        defaults.stairs = building.getStairs();
        return defaults;
    }

    /**
     * Запись дверей, окон и лестниц.
     * Ссылки на наборы тайлов берутся из параметров комнаты, но объект может
     * переопределить их своими атрибутами Tile/FrameTile/CurtainsTile/ShuttersTile.
     */
    private static void SetOpenings(Element floorElement, CommonData data, Room room, Document doc) {
        TileDefaults defaults = defaultsFromRoom(room);
        for (ObjectTile opening : data.Openings) {
            appendOpening(floorElement, opening, defaults, doc);
        }
    }

    /**
     * AI: версия для здания.
     * Пишутся только проёмы указанного этажа. Значения по умолчанию берутся
     * из параметров комнаты-владельца, а для проёмов уровня здания
     * (roomIndex = -1) - из параметров здания.
     */
    private static void SetOpenings(Element floorElement, CommonData data, Building building,
            Document doc, int floor) {
        TileDefaults buildingDefaults = defaultsFromBuilding(building);
        for (ObjectTile opening : data.Openings) {
            if (opening.floor != floor) {
                continue;
            }
            TileDefaults defaults = buildingDefaults;
            if (opening.roomIndex >= 0 && opening.roomIndex < data.randomRooms.size()) {
                defaults = defaultsFromRoom(data.randomRooms.get(opening.roomIndex));
            }
            appendOpening(floorElement, opening, defaults, doc);
        }
    }

    private static void appendOpening(Element floorElement, ObjectTile opening,
            TileDefaults defaults, Document doc) {
        Element objectElement = doc.createElement("object");
        String openingType = opening.type.toLowerCase();
        switch (openingType) {
            case "door": {
                warnIfNotSet("Door", defaults.door);
                objectElement.setAttribute("type", "door");
                objectElement.setAttribute("FrameTile",
                        Integer.toString(resolveTile(((Door) opening).getFrameTile(), defaults.doorFrame)));
                setPosition(objectElement, opening);
                objectElement.setAttribute("Tile",
                        Integer.toString(resolveTile(opening.getTile(), defaults.door)));
                break;
            }
            case "window":
            case "windows": {
                warnIfNotSet("Window", defaults.window);
                Windows window = (Windows) opening;
                objectElement.setAttribute("type", "window");
                objectElement.setAttribute("CurtainsTile",
                        Integer.toString(resolveTile(window.getCurtainsTile(), defaults.curtains)));
                objectElement.setAttribute("ShuttersTile",
                        Integer.toString(resolveTile(window.getShuttersTile(), defaults.shutters)));
                setPosition(objectElement, opening);
                objectElement.setAttribute("Tile",
                        Integer.toString(resolveTile(opening.getTile(), defaults.window)));
                break;
            }
            case "stair":
            case "stairs": {
                warnIfNotSet("Stairs", defaults.stairs);
                objectElement.setAttribute("type", "stairs");
                setPosition(objectElement, opening);
                // AI: раньше у лестницы не писался Tile, и TileZed брал набор 0
                objectElement.setAttribute("Tile",
                        Integer.toString(resolveTile(opening.getTile(), defaults.stairs)));
                break;
            }
            default:
                throw new IllegalArgumentException("Неизвестный тип проёма: " + opening.type);
        }
        floorElement.appendChild(objectElement);
    }

    private static void setPosition(Element objectElement, ObjectTile opening) {
        objectElement.setAttribute("x", Integer.toString(opening.x));
        objectElement.setAttribute("y", Integer.toString(opening.y));
        objectElement.setAttribute("dir", opening.direction);
    }

    /** -1 у объекта означает "взять набор тайлов, заданный параметром комнаты". */
    private static int resolveTile(int objectValue, int roomDefault) {
        return (objectValue >= 0) ? objectValue : roomDefault;
    }

    private static void warnIfNotSet(String parameterName, int value) {
        if (value == 0) {
            System.err.println("Предупреждение: размещён объект, которому нужен параметр комнаты '"
                    + parameterName + "', но соответствующая RandomCollection не задана");
        }
    }

    private static void SetTilesForFurniture(Element tileEntryElement, ArrayList<String> PickedTileset, Document doc) {

        String[] Sides;
        switch (PickedTileset.size()) {
            case 1:
                Sides = directionForFurniture1Sides;
                break;
            case 2:
                Sides = directionForFurniture2Sides;
                break;
            case 4:
                Sides = directionForFurniture4Sides;
                break;
            case 8:
                Sides = directionForFurniture8Sides;
                break;
            default:
                throw new IllegalArgumentException("Некоректное количество сторон у мебели");
        }
        for (int i = 0; i < PickedTileset.size(); i++) {
            Element furnitureTileElement = doc.createElement("entry");
            furnitureTileElement.setAttribute("orient", Sides[i]);
            Element tileElement = doc.createElement("tile");
            tileElement.setAttribute("x", "0");
            tileElement.setAttribute("y", "0");
            tileElement.setAttribute("name", PickedTileset.get(i));
            furnitureTileElement.appendChild(tileElement);
            tileEntryElement.appendChild(furnitureTileElement);
        }
    }
    private static void SetTilesForFurnitureWithRules(Element tileEntryElement, ArrayList<Element> PickedTileset, Document doc) {        
        for (int i = 0; i < PickedTileset.size(); i++) {
            tileEntryElement.appendChild(doc.importNode(PickedTileset.get(i),true));
        }
    }
}
