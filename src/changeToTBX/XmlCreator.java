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

            // Запись TileSet
            int iterTileSet = 1;
            for (Collection coll : data.Collections) {
                if (coll.GetTypeOfObject().equals("RoomParameter")) {
                    String TypeOfRoomparametr = coll.GetParameter();
                    data.UsedTiles.add(iterTileSet);
                    iterTileSet++;
                    switch (TypeOfRoomparametr) {
                        case "InteriorWall":
                            Element tileWallEntryElement = doc.createElement("tile_entry");
                            tileWallEntryElement.setAttribute("category", "interior_walls");
                            SetTilesForWalls(tileWallEntryElement, coll.getPickedTileset(), doc);
                            buildingElement.appendChild(tileWallEntryElement);
                            break;
                        case "InteriorWallTrim":
                            Element tileWallTrimEntryElement = doc.createElement("tile_entry");
                            tileWallTrimEntryElement.setAttribute("category", "interior_wall_trim");
                            SetTilesForWalls(tileWallTrimEntryElement, coll.getPickedTileset(), doc);
                            buildingElement.appendChild(tileWallTrimEntryElement);
                            break;
                        case "Floor":
                            Element tileFloorEntryElement = doc.createElement("tile_entry");
                            tileFloorEntryElement.setAttribute("category", "floors");
                            SetTilesForFloors(tileFloorEntryElement, coll.getPickedTileset(), doc);
                            buildingElement.appendChild(tileFloorEntryElement);
                            break;
                        case "GrimeFloor":
                            Element tileGrimeFloorEntryElement = doc.createElement("tile_entry");
                            tileGrimeFloorEntryElement.setAttribute("category", "grime_floor");
                            SetTilesForFloorGrime(tileGrimeFloorEntryElement, coll.getPickedTileset(), doc);
                            buildingElement.appendChild(tileGrimeFloorEntryElement);
                            break;
                        case "GrimeWall":
                            Element tileGrimeWallEntryElement = doc.createElement("tile_entry");
                            tileGrimeWallEntryElement.setAttribute("category", "grime_wall");
                            SetTilesForGrimeWall(tileGrimeWallEntryElement, coll.getPickedTileset(), doc);
                            buildingElement.appendChild(tileGrimeWallEntryElement);
                            break;
                    }
                } else if (coll.GetTypeOfObject().equals("furniture")) {
                    Element furnitureElement = doc.createElement("furniture");
                    if (coll.getPickedFurniture().size() == 8) {
                        furnitureElement.setAttribute("corners", "true");
                    }
                    if (coll.hasRuleOfPlacing()) {
                         SetTilesForFurnitureWithRules(furnitureElement,coll.getPickedFurnitureWithRules(),doc);
						 buildingElement.appendChild(furnitureElement);
                    } else {
                        SetTilesForFurniture(furnitureElement, coll.getPickedFurniture(), doc);
                        buildingElement.appendChild(furnitureElement);
                    }
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
    private static final String[] directionForFurniture1Sides = {"W"};
    private static final String[] directionForFurniture2Sides = {"W", "N"};
    private static final String[] directionForFurniture4Sides = {"W", "N", "E", "S"};
    private static final String[] directionForFurniture8Sides = {"W", "N", "E", "S", "SW", "NW", "NE", "SE"};

    private static void SetTilesForWalls(Element tileEntryElement, ArrayList<String> PickedTileset, Document doc) {
        for (int i = 0; i < PickedTileset.size(); i++) {
            Element tileElement = doc.createElement("tile");
            tileElement.setAttribute("enum", directionForWalls[i]);
            tileElement.setAttribute("tile", PickedTileset.get(i));
            tileEntryElement.appendChild(tileElement);
        }

    }

    private static void SetTilesForGrimeWall(Element tileEntryElement, ArrayList<String> PickedTileset, Document doc) {
        for (int i = 0; i < PickedTileset.size(); i++) {
            Element tileElement = doc.createElement("tile");
            tileElement.setAttribute("enum", directionForGrimeWall[i]);
            tileElement.setAttribute("tile", PickedTileset.get(i));
            tileEntryElement.appendChild(tileElement);
        }

    }

    private static void SetTilesForFloors(Element tileEntryElement, ArrayList<String> PickedTileset, Document doc) {
        for (int i = 0; i < PickedTileset.size(); i++) {
            Element tileElement = doc.createElement("tile");
            tileElement.setAttribute("enum", "Floor");
            tileElement.setAttribute("tile", PickedTileset.get(i));
            tileEntryElement.appendChild(tileElement);
        }

    }

    private static void SetTilesForFloorGrime(Element tileEntryElement, ArrayList<String> PickedTileset, Document doc) {
        for (int i = 0; i < PickedTileset.size(); i++) {
            Element tileElement = doc.createElement("tile");
            tileElement.setAttribute("enum", directionForFloorGrime[i]);
            tileElement.setAttribute("tile", PickedTileset.get(i));
            tileEntryElement.appendChild(tileElement);
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
