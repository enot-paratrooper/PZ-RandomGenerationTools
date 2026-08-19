package randBuildingGen;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import containers.CommonData;
import randGroups.RandomGroup;
import randRoomGen.Collection;
import randRoomGen.Room;

import static commonFunc.LoadFunc.loadNonRandE;
import static commonFunc.LoadFunc.loadRandColl;
import static commonFunc.LoadFunc.loadRandGroup;
import static tools.ConfigPaths.buildingFile;
import static tools.StringTools.parseRanges;

/**
 * [AI] НОВЫЙ КЛАСС (добавлен AI).
 *
 * Загрузка здания из xml-шаблона conf/RandomBuilding/Building_<Имя>.xml.
 *
 * Порядок загрузки (строго):
 *   1) параметры здания          — секция &lt;BuildingParameters&gt;
 *   2) элементы уровня здания    — секция &lt;BuildingElements&gt;
 *   3) комнаты                   — секция &lt;Rooms&gt;
 *   4) массив клеток здания      — createBuildingCells()
 *
 * Порядок важен: он определяет порядок коллекций в CommonData, а значит и
 * сквозную нумерацию tile_entry в будущем .tbx (см. iterTileSet в XmlCreator).
 *
 * Проверка пересечения комнат выполняется на шаге 3 ДО фактической загрузки
 * содержимого комнат (см. RoomPlacement) и ещё раз, как страховка, на шаге 4.
 *
 * Преобразование в .tbx намеренно не затрагивается — XmlCreator пока не
 * изменён, см. README.
 */
public class Building {

	/** Порядок соответствует порядку атрибутов элемента &lt;building&gt; в .tbx. */
	public static final String[] BUILDING_PARAMETER_NAMES = {
			"ExteriorWall", "ExteriorWallTrim", "Door", "DoorFrame", "Window",
			"Curtains", "Shutters", "Stairs", "RoofCap", "RoofSlope", "RoofTop", "GrimeWall"
	};

	/** Сколько раз пытаться разыграть координаты комнаты, если они заданы диапазоном. */
	private static final int PLACEMENT_ATTEMPTS = 100;

	private String Name = "";
	private int SizeX;
	private int SizeY;

	/** buildingCells[y][x] — номер комнаты (1..N) либо 0. */
	private int[][] buildingCells;
	private String buildingCellsString = "";

	private final List<RoomPlacement> Placements = new ArrayList<RoomPlacement>();
	private final List<Room> Rooms = new ArrayList<Room>();
	private final List<RandomGroup> RandomGroups = new ArrayList<RandomGroup>();

	private final Map<String, Integer> BuildingParameters = new LinkedHashMap<String, Integer>();

	private final CommonData data;

	public Building(CommonData data) {
		this.data = data;
		for (String parameterName : BUILDING_PARAMETER_NAMES) {
			BuildingParameters.put(parameterName, 0);
		}
	}

	// ------------------------------------------------------------------
	// Загрузка
	// ------------------------------------------------------------------

	/**
	 * Главный метод. Вызывать ДО data.MergeCollections().
	 *
	 * Полная последовательность работы с зданием:
	 *   Building building = new Building(data);
	 *   building.loadBuilding("TestHouse");
	 *   data.MergeCollections();
	 *   data.InitTileSets();
	 *   data.InitLinks();
	 *   building.InitParameters();
	 */
	public void loadBuilding(String buildingName) {
		InputStream inputStream = null;
		try {
			String fileName = buildingFile(buildingName);

			inputStream = getClass().getResourceAsStream(fileName);
			if (inputStream == null) {
				inputStream = new FileInputStream(fileName);
			}

			// Переменные (#define) уровня здания.
			// ВАЖНО: смещение считается один раз на весь файл, поэтому
			// RandomCollection в шаблоне здания должны быть пронумерованы
			// подряд 1..K в порядке появления (сначала BuildingParameters,
			// затем BuildingElements).
			data.linker.LoadVariables(fileName, data.RandomCollections.size() - 1);

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(inputStream);
			document.getDocumentElement().normalize();

			Element buildingElement = document.getDocumentElement();

			// 1) параметры здания
			loadBuildingParameters(buildingElement);
			// 2) элементы уровня здания
			loadBuildingElements(buildingElement);
			// 3) комнаты (внутри — проверка пересечений)
			loadRooms(buildingElement);
			// 4) массив клеток здания
			createBuildingCells();

		} catch (BuildingLayoutException e) {
			// ошибки компоновки не проглатываем — здание с наложенными
			// комнатами дальше по конвейеру гнать нельзя
			throw e;
		} catch (Exception e) {
			System.err.println("Ошибка загрузки здания '" + buildingName + "': " + e.getMessage());
			e.printStackTrace();
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (Exception ignored) {
				}
			}
		}
	}

	/** Шаг 1: размеры, имя и параметры здания. */
	private void loadBuildingParameters(Element buildingElement) {
		Name = buildingElement.getAttribute("name");

		String sizeX = buildingElement.getAttribute("sizeX");
		if (sizeX.isEmpty()) {
			throw new NumberFormatException("Незадан размер здания по X");
		}
		SizeX = Integer.parseInt(sizeX);

		String sizeY = buildingElement.getAttribute("sizeY");
		if (sizeY.isEmpty()) {
			throw new NumberFormatException("Незадан размер здания по Y");
		}
		SizeY = Integer.parseInt(sizeY);

		if (SizeX <= 0 || SizeY <= 0) {
			throw new IllegalArgumentException("Некорректный размер здания: " + SizeX + "x" + SizeY);
		}

		Element parametersSection = getSection(buildingElement, "BuildingParameters");
		if (parametersSection == null) {
			return; // секция необязательна, тогда все параметры останутся нулевыми
		}
		// Секции — прямые потомки корня, их поддеревья не пересекаются,
		// поэтому getElementsByTagName внутри секции безопасен.
		loadRandColl(data, parametersSection, 0, 0);
		loadNonRandE(data, parametersSection, 0, 0);
	}

	/** Шаг 2: коллекции, нерандомные элементы и группы уровня здания. */
	private void loadBuildingElements(Element buildingElement) {
		Element elementsSection = getSection(buildingElement, "BuildingElements");
		if (elementsSection == null) {
			return;
		}
		loadRandColl(data, elementsSection, 0, 0);
		loadNonRandE(data, elementsSection, 0, 0);
		loadRandGroup(data, elementsSection, RandomGroups, 0, 0);
	}

	/**
	 * Шаг 3: комнаты.
	 *
	 * Этап A — читаем только геометрию комнат, разыгрываем координаты,
	 *          проверяем габариты здания и пересечения.
	 * Этап B — загружаем содержимое комнат уже с готовыми координатами.
	 *
	 * Разделение нужно, чтобы при ошибке компоновки CommonData не оказался
	 * частично заполненным.
	 */
	private void loadRooms(Element buildingElement) throws Exception {
		Element roomsSection = getSection(buildingElement, "Rooms");
		if (roomsSection == null) {
			throw new IllegalArgumentException("В шаблоне здания '" + Name + "' отсутствует секция <Rooms>");
		}

		NodeList roomNodes = roomsSection.getElementsByTagName("Room");
		if (roomNodes.getLength() == 0) {
			throw new IllegalArgumentException("В шаблоне здания '" + Name + "' не объявлено ни одной комнаты");
		}

		// --- этап A ---
		for (int i = 0; i < roomNodes.getLength(); i++) {
			Element roomElement = (Element) roomNodes.item(i);
			String roomName = roomElement.getAttribute("name");
			if (roomName.isEmpty()) {
				throw new IllegalArgumentException("У комнаты №" + (i + 1) + " здания '" + Name + "' не задан name");
			}
			RoomPlacement placement = RoomPlacement.readGeometry(roomName);
			placement.number = Placements.size() + 1;
			placeRoom(placement, roomElement.getAttribute("x"), roomElement.getAttribute("y"));
			Placements.add(placement);
		}

		// --- этап B ---
		for (RoomPlacement placement : Placements) {
			Room room = new Room(data);
			room.loadRoom(placement.name, placement.x, placement.y);
			room.setNumber(placement.number);
			placement.room = room;
			Rooms.add(room);
		}
	}

	/**
	 * Выбор координат комнаты и проверка размещения.
	 *
	 * x и y в шаблоне могут быть как числом ("3"), так и диапазоном
	 * ("1-4;7") — формат тот же, что у tools.StringTools.parseRanges.
	 * Если координаты заданы диапазоном, делается до PLACEMENT_ATTEMPTS
	 * попыток найти вариант без пересечений.
	 */
	private void placeRoom(RoomPlacement placement, String xAttr, String yAttr) {
		List<Integer> rangeX = parseRanges(xAttr);
		List<Integer> rangeY = parseRanges(yAttr);
		if (rangeX.isEmpty() || rangeY.isEmpty()) {
			throw new IllegalArgumentException("Не заданы координаты комнаты '" + placement.name
					+ "' в здании '" + Name + "'");
		}

		String lastProblem = "";
		for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
			placement.x = rangeX.get(data.random.nextInt(rangeX.size()));
			placement.y = rangeY.get(data.random.nextInt(rangeY.size()));

			if (!isInsideBuilding(placement)) {
				lastProblem = "комната " + placement + " выходит за габариты здания " + SizeX + "x" + SizeY;
				continue;
			}
			RoomPlacement conflict = findIntersection(placement);
			if (conflict == null) {
				return; // место найдено
			}
			lastProblem = "комната " + placement + " пересекается с комнатой " + conflict;
		}

		throw new BuildingLayoutException("Здание '" + Name + "': не удалось разместить комнату '"
				+ placement.name + "' за " + PLACEMENT_ATTEMPTS + " попыток. Последняя причина: " + lastProblem);
	}

	/** Комната целиком внутри габаритов здания. */
	private boolean isInsideBuilding(RoomPlacement placement) {
		return placement.x >= 0
				&& placement.y >= 0
				&& placement.x + placement.sizeX <= SizeX
				&& placement.y + placement.sizeY <= SizeY;
	}

	/** Первая уже размещённая комната, с которой есть пересечение, либо null. */
	private RoomPlacement findIntersection(RoomPlacement placement) {
		for (RoomPlacement other : Placements) {
			if (other == placement) {
				continue;
			}
			if (placement.intersects(other)) {
				return other;
			}
		}
		return null;
	}

	/**
	 * Шаг 4: массив клеток здания.
	 *
	 * Формат совпадает с блоком &lt;rooms&gt; в .tbx (см. образец
	 * SuburbanSouth2): buildingCells[y][x] — номер комнаты (1..N) либо 0.
	 * Нумерация комнат — порядок их объявления в секции &lt;Rooms&gt;,
	 * тот же порядок должен использоваться при записи элементов
	 * &lt;room .../&gt; в .tbx.
	 */
	private void createBuildingCells() {
		buildingCells = new int[SizeY][SizeX];

		for (RoomPlacement placement : Placements) {
			for (int ly = 0; ly < placement.sizeY; ly++) {
				for (int lx = 0; lx < placement.sizeX; lx++) {
					if (placement.cells[ly][lx] == 0) {
						continue;
					}
					int gx = placement.x + lx;
					int gy = placement.y + ly;
					if (gx < 0 || gy < 0 || gx >= SizeX || gy >= SizeY) {
						throw new BuildingLayoutException("Здание '" + Name + "': клетка (" + gx + "," + gy
								+ ") комнаты " + placement + " выходит за габариты " + SizeX + "x" + SizeY);
					}
					// страховочная проверка пересечения на уровне готового массива
					if (buildingCells[gy][gx] != 0) {
						throw new BuildingLayoutException("Здание '" + Name + "': клетка (" + gx + "," + gy
								+ ") занята сразу двумя комнатами (№" + buildingCells[gy][gx]
								+ " и " + placement + ")");
					}
					buildingCells[gy][gx] = placement.number;
				}
			}
		}

		buildingCellsString = gridToString(buildingCells);
	}

	/**
	 * Массив клеток в текстовый вид формата .tbx:
	 * строки разделены запятой с переводом строки, последняя строка без запятой.
	 */
	private static String gridToString(int[][] grid) {
		StringBuilder sb = new StringBuilder();
		for (int y = 0; y < grid.length; y++) {
			for (int x = 0; x < grid[y].length; x++) {
				sb.append(grid[y][x]);
				if (x < grid[y].length - 1) {
					sb.append(",");
				}
			}
			if (y < grid.length - 1) {
				sb.append(",\n");
			}
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------
	// Параметры
	// ------------------------------------------------------------------

	/**
	 * Сквозная нумерация tile_entry для всего здания.
	 * Вызывать ПОСЛЕ data.MergeCollections(), data.InitTileSets() и data.InitLinks().
	 *
	 * Счётчик tileEntryIndex обязан идти в том же порядке и по тем же
	 * коллекциям, что и iterTileSet в XmlCreator.createBuildingXml, иначе
	 * номера в атрибутах комнат и здания разъедутся с реальными tile_entry.
	 */
	public void InitParameters() {
		try {
			for (Room room : Rooms) {
				room.resetRoomparameters();
			}
			for (String parameterName : BUILDING_PARAMETER_NAMES) {
				BuildingParameters.put(parameterName, 0);
			}

			int tileEntryIndex = 1;
			int randomCount = data.RandomCollections.size();

			for (int i = 0; i < data.Collections.size(); i++) {
				Collection coll = data.Collections.get(i);
				String type = coll.GetTypeOfObject();
				if (type == null) {
					continue;
				}
				if (type.equals("BuildingParameter")) {
					setBuildingParameter(coll.GetParameter(), tileEntryIndex);
					tileEntryIndex++;
				} else if (type.equals("RoomParameter")) {
					Room owner = findOwner(i, randomCount);
					if (owner != null) {
						owner.setParameter(coll.GetParameter(), tileEntryIndex);
					} else {
						System.err.println("RoomParameter '" + coll.GetParameter() + "' (коллекция " + i
								+ ") не принадлежит ни одной комнате. На уровне здания используйте "
								+ "type=\"BuildingParameter\".");
					}
					tileEntryIndex++;
				}
			}
		} catch (Exception e) {
			System.err.println("Ошибка инициализации параметров здания: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private Room findOwner(int collectionIndex, int randomCollectionsCount) {
		for (Room room : Rooms) {
			if (room.ownsCollection(collectionIndex, randomCollectionsCount)) {
				return room;
			}
		}
		return null;
	}

	private void setBuildingParameter(String parameterName, int tileEntryIndex) {
		if (!BuildingParameters.containsKey(parameterName)) {
			System.err.println("Неизвестный параметр здания: " + parameterName);
			return;
		}
		BuildingParameters.put(parameterName, tileEntryIndex);
	}

	// ------------------------------------------------------------------
	// Вспомогательное
	// ------------------------------------------------------------------

	/**
	 * Прямой потомок с заданным именем тега.
	 *
	 * Намеренно НЕ используется getElementsByTagName: он ищет по всему
	 * поддереву, и, например, вызов loadRandColl для корня здания подхватил бы
	 * RandomCollection сразу из всех секций, сломав порядок загрузки.
	 */
	private static Element getSection(Element parent, String tagName) {
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(tagName)) {
				return (Element) node;
			}
		}
		return null;
	}

	/** Отладочная печать раскладки здания. */
	public void printLayout() {
		System.out.println("Здание '" + Name + "' " + SizeX + "x" + SizeY);
		for (RoomPlacement placement : Placements) {
			System.out.println("  " + placement.number + ": " + placement);
		}
		System.out.println(buildingCellsString);
	}

	// ------------------------------------------------------------------
	// Геттеры
	// ------------------------------------------------------------------

	public String getName() {
		return Name;
	}

	public int getSizeX() {
		return SizeX;
	}

	public int getSizeY() {
		return SizeY;
	}

	/** buildingCells[y][x] */
	public int[][] getBuildingCells() {
		return buildingCells;
	}

	/** Готовая строка для блока &lt;rooms&gt; в .tbx. */
	public String getBuildingCellsString() {
		return buildingCellsString;
	}

	/** Комнаты в порядке объявления; индекс+1 == номер комнаты в массиве клеток. */
	public List<Room> getRooms() {
		return Rooms;
	}

	public List<RoomPlacement> getPlacements() {
		return Placements;
	}

	public List<RandomGroup> getRandomGroups() {
		return RandomGroups;
	}

	public int getBuildingParameter(String parameterName) {
		Integer value = BuildingParameters.get(parameterName);
		return value == null ? 0 : value;
	}

	public Map<String, Integer> getBuildingParameters() {
		return BuildingParameters;
	}
}
