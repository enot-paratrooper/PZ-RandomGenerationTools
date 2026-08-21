package commonFunc;

import static tools.StringTools.splitString;

import java.util.List;

import java.io.FileInputStream;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import containers.CommonData;
import randGroups.RandomGroup;
import randRoomGen.NonrandomElement;
import randRoomGen.RandomCollection;
import randRoomGen.RawTileEntry;
import randRoomGen.ObjectTiles.GlobalParameter;
import randRoomGen.ObjectTiles.ObjectTile;
import randRoomGen.ObjectTiles.ObjectTileFactory;
import randRoomGen.Room;
import static tools.RandTools.chance;

public class LoadFunc {

	// =====================================================================
	// RandomCollection
	// =====================================================================

	public static void loadRandColl(CommonData data, Element mainElement) {
		loadRandColl(data, mainElement, 0, 0);
	}

	public static void loadRandColl(CommonData data, Element mainElement, int x, int y) {
		loadRandColl(data, mainElement, x, y, 0);
	}

	/**
	 * AI: добавлен параметр floor - этаж по умолчанию для всех объектов блока.
	 * Отдельная коллекция может перекрыть его атрибутом floor="N".
	 */
	public static void loadRandColl(CommonData data, Element mainElement, int x, int y, int floor) {
		NodeList RandomCollectionNodes = mainElement.getElementsByTagName("RandomCollection");
        for(int i=0;i<RandomCollectionNodes.getLength();i++) {
        	Element RandomCollectionE = (Element)RandomCollectionNodes.item(i);
        	String RandomCollectionName = RandomCollectionE.getAttribute("name");
        	if(RandomCollectionName.isEmpty()) {
        		throw new IllegalArgumentException("Неверное имя коллекции - " + RandomCollectionE.getAttribute("num"));
        	}
        	RandomCollection newRandomCollection = new RandomCollection(data.random);
        	newRandomCollection.numberOfCollection = Integer.parseInt(RandomCollectionE.getAttribute("num"));
        	newRandomCollection.loadRandomCollection(splitString(RandomCollectionName));
        	NodeList ObjectTiles = RandomCollectionE.getElementsByTagName("object");
        	// Коллекция без объектов нужна только как цель ссылки #define.
        	// Раньше эта ветка была лишь в комнатной версии, и такая коллекция
        	// внутри RandomGroup валила GetTypeOfObject().
        	if(ObjectTiles.getLength()==0) {
        		newRandomCollection.AddFurniture(new GlobalParameter());
        	}
        	for(int j=0;j<ObjectTiles.getLength();j++)
        	{
        		Element FurnitureE = (Element)ObjectTiles.item(j);
        		int randCollNum = Integer.parseInt(RandomCollectionE.getAttribute("num"));
        		newRandomCollection.AddFurniture(ObjectTileFactory.createReflective(FurnitureE, randCollNum));
        	}
        	newRandomCollection.setOffsetX(x);
        	newRandomCollection.setOffsetY(y);
        	// AI: этаж коллекции
        	newRandomCollection.setFloor(resolveFloor(RandomCollectionE, floor));
        	data.RandomCollections.add(newRandomCollection);
        }
	}

	// =====================================================================
	// NonrandomElement
	// =====================================================================

	public static void loadNonRandE(CommonData data, Element mainElement) {
		loadNonRandE(data, mainElement, 0, 0);
	}

	public static void loadNonRandE(CommonData data, Element mainElement, int x, int y) {
		loadNonRandE(data, mainElement, x, y, 0);
	}

	/** AI: добавлен параметр floor. */
	public static void loadNonRandE(CommonData data, Element mainElement, int x, int y, int floor) {
		NodeList NonrandomElementsNodes = mainElement.getElementsByTagName("NonrandomElement");
        for(int i=0;i<NonrandomElementsNodes.getLength();i++) {
        	Element NonrandomElement = (Element)NonrandomElementsNodes.item(i);
        	NonrandomElement newNonrandomElement = new NonrandomElement();
        	int num = Integer.parseInt(NonrandomElement.getAttribute("num"));
        	newNonrandomElement.loadNonrandomElement(NonrandomElement, num, data.linker);
        	newNonrandomElement.setOffsetX(x);
        	newNonrandomElement.setOffsetY(y);
        	// AI: этаж элемента
        	newNonrandomElement.setFloor(resolveFloor(NonrandomElement, floor));
        	data.NonrandomElements.add(newNonrandomElement);
        }
	}

	// =====================================================================
	// Openings
	// =====================================================================

	/**
	 * Загрузка дверей, окон и лестниц из блока Openings.
	 * Набор тайлов у них общий на комнату (параметры Door/DoorFrame/Window/
	 * Curtains/Shutters/Stairs), поэтому коллекция каждому объекту не нужна.
	 */
	public static void loadOpenings(CommonData data, Element mainElement) {
		loadOpenings(data, mainElement, 0, 0);
	}

	public static void loadOpenings(CommonData data, Element mainElement, int x, int y) {
		loadOpenings(data, mainElement, x, y, 0, -1);
	}

	/**
	 * AI: добавлены floor и roomIndex.
	 * roomIndex - индекс комнаты-владельца в data.randomRooms, из параметров
	 * которой берутся наборы тайлов по умолчанию. -1 = проём уровня здания,
	 * значения по умолчанию берутся из параметров здания.
	 */
	public static void loadOpenings(CommonData data, Element mainElement, int x, int y, int floor, int roomIndex) {
		NodeList openingsNodes = mainElement.getElementsByTagName("Openings");
        for(int i=0;i<openingsNodes.getLength();i++) {
        	Element openingsElement = (Element)openingsNodes.item(i);
        	NodeList objectNodes = openingsElement.getElementsByTagName("object");
        	for(int j=0;j<objectNodes.getLength();j++) {
        		Element objectElement = (Element)objectNodes.item(j);
        		ObjectTile opening = ObjectTileFactory.createReflective(objectElement, j);
        		// Атрибут void позволяет случайно не ставить окно/дверь
        		if(!opening.placeble) {
        			continue;
        		}
        		opening.x += x;
        		opening.y += y;
        		// AI: этаж и владелец проёма
        		if(!opening.floorExplicit) {
        			opening.floor = floor;
        		}
        		opening.roomIndex = roomIndex;
        		data.Openings.add(opening);
        	}
        }
	}

	// =====================================================================
	// RandomGroup
	// =====================================================================

	public static void loadRandGroup(CommonData data, Element roomElement,List<RandomGroup> RandomGroups, int x, int y) {
		loadRandGroup(data, roomElement, RandomGroups, x, y, 0, -1);
	}

	/** AI: добавлены floor и roomIndex - группа наследует этаж и комнату-владельца. */
	public static void loadRandGroup(CommonData data, Element roomElement,List<RandomGroup> RandomGroups, int x, int y, int floor, int roomIndex) {
		NodeList randomGroupsNodes = roomElement.getElementsByTagName("RandomGroup");
        for(int i=0;i<randomGroupsNodes.getLength();i++) {
        	Element randomGroupElement = (Element)randomGroupsNodes.item(i);
        	String name = randomGroupElement.getAttribute("name");
        	NodeList groopObjectsNodes = randomGroupElement.getElementsByTagName("object");
        	for(int j=0;j<groopObjectsNodes.getLength();j++) {
            	RandomGroup newRandomGroup = new RandomGroup(data);
        		Element groopObjectsElement = (Element) groopObjectsNodes.item(j);
        		String rangeX =  groopObjectsElement.getAttribute("x");
        		String rangeY =  groopObjectsElement.getAttribute("y");
        		boolean placeble = true;
        		if (groopObjectsElement.hasAttribute("void")) {
        			placeble= chance(groopObjectsElement.getAttribute("void"));
        		}
        		if(placeble) {
        		newRandomGroup.loadRandomGroup(name,rangeX,rangeY,x,y,resolveFloor(groopObjectsElement, floor),roomIndex);
        		}
        		RandomGroups.add(newRandomGroup);
        	}
        }
	}

	// =====================================================================
	// Комнаты здания
	// =====================================================================

	/**
	 * Загрузка комнат здания.
	 *
	 * AI: теперь читается блок <Rooms> с элементами <Room .../>.
	 * Старый вариант со списком <room .../> в корне здания поддержан
	 * для совместимости со старыми конфигами.
	 *
	 * AI: комната кладётся в data.randomRooms ДО вызова loadRoom, чтобы
	 * её индекс (он же сквозной номер комнаты минус 1) был известен
	 * проёмам этой комнаты уже во время загрузки.
	 */
	public static void loadRandRoom(CommonData data, Element buildindElement) {
		NodeList randomRoomsNodes = buildindElement.getElementsByTagName("Room");
		if(randomRoomsNodes.getLength()==0) {
			randomRoomsNodes = buildindElement.getElementsByTagName("room");
		}
		if(randomRoomsNodes.getLength()==0) {
			throw new IllegalArgumentException("Ошибка загрузки задния - "+buildindElement.getAttribute("name")+" - отсутствуют комнаты");
		}
		for(int i=0;i<randomRoomsNodes.getLength();i++) {
			Element randomRoomElement = (Element)randomRoomsNodes.item(i);
        	String name = randomRoomElement.getAttribute("name");
        	if(name.isEmpty()) {
        		throw new IllegalArgumentException("Ошибка загрузки здания - у комнаты "+(i+1)+" не задано имя");
        	}
        	int x =  parseCoord(randomRoomElement, "x");
    		int y =  parseCoord(randomRoomElement, "y");
    		int floor = parseCoord(randomRoomElement, "floor");
        	Room newRoom = new Room(data);
        	int roomIndex = data.randomRooms.size();
        	data.randomRooms.add(newRoom);
        	newRoom.loadRoom(name,x,y,floor,roomIndex);
		}
	}

	// =====================================================================
	// Готовые tile_entry (наборы тайлов крыши)
	// =====================================================================

	/**
	 * AI: новая функция.
	 *
	 * Загрузка готовых блоков tile_entry из файла conf/RoofTiles/RoofTiles_<имя>.xml.
	 *
	 * В здании пишется так:
	 *   <RoofTiles name="Default"/>
	 *   <RoofTiles name="Default; Shingles"/>   - один набор выбирается случайно
	 *
	 * Файл набора:
	 *   <RoofTiles name="Default">
	 *     <TileEntry parameter="RoofCap">
	 *       <tile_entry category="roof_caps"> ... </tile_entry>
	 *     </TileEntry>
	 *     ...
	 *   </RoofTiles>
	 *
	 * Наборы крыши не проходят через Tileset: в них есть пустые тайлы,
	 * атрибуты offset и тайлы из разных тайлсетов, поэтому блок переносится
	 * в .tbx дословно.
	 */
	public static void loadRoofTiles(CommonData data, Element mainElement) {
		NodeList roofTilesNodes = mainElement.getElementsByTagName("RoofTiles");
		for(int i=0;i<roofTilesNodes.getLength();i++) {
			Element roofTilesElement = (Element)roofTilesNodes.item(i);
			String names = roofTilesElement.getAttribute("name");
			if(names.trim().isEmpty()) {
				throw new IllegalArgumentException("У блока RoofTiles не задан name");
			}
			List<String> variants = splitString(names);
			String pickedName = variants.get(CommonData.random.nextInt(variants.size()));
			loadRoofTilesFile(data, pickedName.trim());
		}
	}

	private static void loadRoofTilesFile(CommonData data, String setName) {
		try {
			String fileName = "..\\RandomRoomGenerator\\conf\\RoofTiles\\RoofTiles_" + setName + ".xml";

			InputStream inputStream = LoadFunc.class.getResourceAsStream(fileName);
			if (inputStream == null) {
				inputStream = new FileInputStream(fileName);
			}
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(inputStream);
			document.getDocumentElement().normalize();
			Element rootElement = document.getDocumentElement();

			NodeList tileEntryNodes = rootElement.getElementsByTagName("TileEntry");
			if(tileEntryNodes.getLength()==0) {
				throw new IllegalArgumentException("В наборе тайлов крыши '" + setName + "' нет ни одного TileEntry");
			}
			for(int i=0;i<tileEntryNodes.getLength();i++) {
				Element tileEntryWrapper = (Element)tileEntryNodes.item(i);
				String parameter = tileEntryWrapper.getAttribute("parameter");
				String type = tileEntryWrapper.getAttribute("type");
				if(type.trim().isEmpty()) {
					type = "BuildingParameter";
				}
				Element rawEntry = (Element) tileEntryWrapper.getElementsByTagName("tile_entry").item(0);
				RawTileEntry newRawTileEntry = new RawTileEntry();
				newRawTileEntry.load(rawEntry, parameter, type);
				data.RawTileEntries.add(newRawTileEntry);
			}
			inputStream.close();
		} catch (Exception e) {
			System.err.println("Ошибка загрузки набора тайлов крыши '" + setName + "': " + e.getMessage());
			e.printStackTrace();
		}
	}

	// =====================================================================
	// Вспомогательное
	// =====================================================================

	/** AI: этаж объекта. Атрибут floor="N" перекрывает этаж родителя. */
	private static int resolveFloor(Element element, int defaultFloor) {
		if(element.hasAttribute("floor")) {
			String value = element.getAttribute("floor").trim();
			if(!value.isEmpty()) {
				return Integer.parseInt(value);
			}
		}
		return defaultFloor;
	}

	/** AI: раньше отсутствующий атрибут ронял загрузку через NumberFormatException. */
	private static int parseCoord(Element element, String attributeName) {
		String value = element.getAttribute(attributeName).trim();
		if(value.isEmpty()) {
			return 0;
		}
		return Integer.parseInt(value);
	}
}
