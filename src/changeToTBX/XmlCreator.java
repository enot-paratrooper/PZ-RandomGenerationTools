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
import randRoomGen.Collection;
import randRoomGen.Room;
import randRoomGen.UsedFurniture;
import randRoomGen.ObjectTiles.Door;
import randRoomGen.ObjectTiles.ObjectTile;
import randRoomGen.ObjectTiles.Windows;

public class XmlCreator {

    /**
     * Создает XML файл с помощью DOM
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

            // Запись TileSet.
            // Нумерация сквозная по всем tile_entry и должна совпадать с нумерацией
            // в Room.InitRoomparameters, иначе ссылки Door/Window/Floor уедут.
            int iterTileSet = 1;
            for (Collection coll : data.Collections) {
                String typeOfObject = coll.GetTypeOfObject();
                if ("RoomParameter".equals(typeOfObject)) {
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
                        default:
                            throw new IllegalArgumentException(
                                    "Неизвестный параметр комнаты: " + TypeOfRoomparametr);
                    }
                } else if ("furniture".equals(typeOfObject)) {
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
                Element objectElement = doc.createElement("object");
                objectElement.setAttribute("type", "furniture");
                objectElement.setAttribute("FurnitureTiles", furniture.FurnitureTiles);
                objectElement.setAttribute("orient", furniture.orient);
                objectElement.setAttribute("x", furniture.x);
                objectElement.setAttribute("y", furniture.y);
                floorElement.appendChild(objectElement);
            }
            //Запись дверей, окон и лестниц
            SetOpenings(floorElement, data, room, doc);

            usedFurnitureElement.setTextContent(data.getUsedFurnitureTiles());
            Element roomsElement = doc.createElement("rooms");
            roomsElement.setTextContent("\n" + room.getRoomCellsString() + "\n");
            floorElement.appendChild(roomsElement);
            buildingElement.appendChild(floorElement);

            // Записываем в файл
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();

            // Форматирование: с отступами
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filePath));
            transformer.transform(source, result);

            System.out.println("XML файл создан: " + filePath);

        } catch (Exception e) {
            e.printStackTrace();
        }
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

    /**
     * Запись дверей, окон и лестниц.
     * Ссылки на наборы тайлов берутся из параметров комнаты, но объект может
     * переопределить их своими атрибутами Tile/FrameTile/CurtainsTile/ShuttersTile.
     */
    private static void SetOpenings(Element floorElement, CommonData data, Room room, Document doc) {
        for (ObjectTile opening : data.Openings) {
            Element objectElement = doc.createElement("object");
            String openingType = opening.type.toLowerCase();
            switch (openingType) {
                case "door": {
                    warnIfNotSet("Door", room.getDoor());
                    objectElement.setAttribute("type", "door");
                    objectElement.setAttribute("FrameTile",
                            Integer.toString(resolveTile(((Door) opening).getFrameTile(), room.getDoorFrame())));
                    setPosition(objectElement, opening);
                    objectElement.setAttribute("Tile",
                            Integer.toString(resolveTile(opening.getTile(), room.getDoor())));
                    break;
                }
                case "window":
                case "windows": {
                    warnIfNotSet("Window", room.getWindow());
                    Windows window = (Windows) opening;
                    objectElement.setAttribute("type", "window");
                    objectElement.setAttribute("CurtainsTile",
                            Integer.toString(resolveTile(window.getCurtainsTile(), room.getCurtains())));
                    objectElement.setAttribute("ShuttersTile",
                            Integer.toString(resolveTile(window.getShuttersTile(), room.getShutters())));
                    setPosition(objectElement, opening);
                    objectElement.setAttribute("Tile",
                            Integer.toString(resolveTile(opening.getTile(), room.getWindow())));
                    break;
                }
                case "stair":
                case "stairs": {
                    warnIfNotSet("Stairs", room.getStairs());
                    objectElement.setAttribute("type", "stairs");
                    setPosition(objectElement, opening);
                    break;
                }
                default:
                    throw new IllegalArgumentException("Неизвестный тип проёма: " + opening.type);
            }
            floorElement.appendChild(objectElement);
        }
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
