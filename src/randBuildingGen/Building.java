package randBuildingGen;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import containers.CommonData;
import randRoomGen.Collection;
import randRoomGen.Room;

import static commonFunc.LoadFunc.loadRandColl;
import static commonFunc.LoadFunc.loadNonRandE;
import static commonFunc.LoadFunc.loadOpenings;
import static commonFunc.LoadFunc.loadRandRoom;
import static commonFunc.LoadFunc.loadRoofTiles;

/**
 * Здание целиком: параметры, объекты уровня здания, комнаты и массив клеток.
 *
 * AI: класс дописан полностью (был только список полей и заготовка buildinCells).
 *
 * Порядок загрузки (loadBuilding):
 *   1) параметры здания        - loadBuildingAttributes
 *   2) объекты уровня здания   - loadRandColl / loadNonRandE / loadOpenings
 *   3) комнаты                 - loadRandRoom
 *   4) массив клеток здания    - InitBuildingCells (сквозная нумерация комнат)
 *   5) крыши                   - InitRoofs (AI: добавлен шаг)
 *
 * Порядок 1-2-3 обязателен: коллекции уровня здания должны попасть
 * в data.RandomCollections раньше комнатных, иначе смещение #define
 * (Linker.LoadVariables получает offset = RandomCollections.size()-1)
 * укажет не на ту коллекцию.
 */
public class Building {

	private int version = 2;
	private String Name = "";
	private int width;
	private int height;

	private int ExteriorWall = 0;
	private int ExteriorWallTrim = 0;
	private int Door = 0;
	private int DoorFrame = 0;
	private int Window = 0;
	private int Curtains = 0;
	private int Shutters = 0;
	private int Stairs = 0;
	private int RoofCap = 0;
	private int RoofSlope = 0;
	private int RoofTop = 0;
	private int GrimeWall = 0;

	/** AI: [этаж][y][x]. Значение - сквозной номер комнаты (1..N), 0 - нет комнаты. */
	private int[][][] buildingCells;
	private int floorCount = 1;

	/** AI: крыши здания. */
	private List<Roof> roofs = new ArrayList<Roof>();
	private RoofGenerator.RoofPlan roofPlan = new RoofGenerator.RoofPlan();

	/** AI: диапазоны коллекций, загруженных на уровне здания (для InitBuildingparameters). */
	private int randStart = 0;
	private int randEnd = 0;
	private int nonRandStart = 0;
	private int nonRandEnd = 0;
	/** AI: диапазон готовых tile_entry (наборы тайлов крыши). */
	private int rawStart = 0;
	private int rawEnd = 0;

	private CommonData data;

	public Building(CommonData data) {
		this.data = data;
	}

	// =====================================================================
	// Загрузка
	// =====================================================================

	public void loadBuilding(String buildingName) {
		try {
			String fileName = "..\\RandomRoomGenerator\\conf\\RandomBuilding\\Building_" + buildingName + ".xml";

			// Пытаемся загрузить файл из ресурсов
			InputStream inputStream = getClass().getResourceAsStream(fileName);

			// Если не найден в ресурсах, пытаемся загрузить из файловой системы
			if (inputStream == null) {
				inputStream = new FileInputStream(fileName);
			}

			// Переменные #define уровня здания.
			// Здание грузится первым, поэтому offset здесь всегда -1.
			data.linker.LoadVariables(fileName, data.RandomCollections.size() - 1);

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(inputStream);
			document.getDocumentElement().normalize();
			Element buildingElement = document.getDocumentElement();

			// --- 1) Параметры здания ---
			loadBuildingAttributes(buildingElement);

			// --- 2) Объекты уровня здания ---
			randStart = data.RandomCollections.size();
			nonRandStart = data.NonrandomElements.size();
			rawStart = data.RawTileEntries.size();

			// Этаж по умолчанию 0, конкретный объект может задать floor="N".
			loadRandColl(data, buildingElement, 0, 0, 0);
			loadNonRandE(data, buildingElement, 0, 0, 0);
			// roomIndex = -1: у проёма уровня здания нет комнаты-владельца,
			// наборы тайлов по умолчанию берутся из параметров здания.
			loadOpenings(data, buildingElement, 0, 0, 0, -1);
			// AI: готовые наборы тайлов крыши
			loadRoofTiles(data, buildingElement);

			randEnd = data.RandomCollections.size();
			nonRandEnd = data.NonrandomElements.size();
			rawEnd = data.RawTileEntries.size();

			// AI: план крыш читается здесь, а строятся крыши после сетки клеток
			roofPlan = RoofGenerator.parsePlan(buildingElement);

			// --- 3) Комнаты ---
			loadRandRoom(data, buildingElement);

			// --- 4) Массив клеток здания ---
			InitBuildingCells();

			// --- 5) Крыши ---
			InitRoofs();

			inputStream.close();
		} catch (Exception e) {
			System.err.println("Ошибка загрузки здания '" + buildingName + "': " + e.getMessage());
			e.printStackTrace();
		}
	}

	/** Шаг 1: имя и размеры. */
	private void loadBuildingAttributes(Element buildingElement) {
		this.Name = buildingElement.getAttribute("name");

		String sizeX = buildingElement.getAttribute("sizeX");
		if (sizeX.isEmpty()) {
			throw new NumberFormatException("Не задан размер здания по X");
		}
		this.width = Integer.parseInt(sizeX.trim());

		String sizeY = buildingElement.getAttribute("sizeY");
		if (sizeY.isEmpty()) {
			throw new NumberFormatException("Не задан размер здания по Y");
		}
		this.height = Integer.parseInt(sizeY.trim());

		if (this.width <= 0 || this.height <= 0) {
			throw new IllegalArgumentException("Некорректный размер здания: " + this.width + "x" + this.height);
		}

		// Необязательное явное количество этажей. Если не задано - считается по комнатам.
		String floors = buildingElement.getAttribute("floors");
		if (!floors.isEmpty()) {
			this.floorCount = Integer.parseInt(floors.trim());
		}
	}

	// =====================================================================
	// Шаг 4: массив клеток
	// =====================================================================

	/**
	 * Сборка buildingCells.
	 *
	 * Нумерация комнат сквозная по всему зданию и совпадает с порядком
	 * элементов <room .../> в шапке .tbx (см. XmlCreator.createBuildingXml).
	 * Первая комната получает 1, потому что 0 в сетке rooms означает
	 * "клетка не принадлежит ни одной комнате".
	 *
	 * Любое ненулевое значение в сетке комнаты считается клеткой этой комнаты.
	 */
	public void InitBuildingCells() {
		InitBuildingCells(true);
	}

	/** AI: verbose=false при повторной сборке после надстройки этажей под крыши. */
	public void InitBuildingCells(boolean verbose) {
		List<Room> rooms = data.randomRooms;

		int neededFloors = this.floorCount;
		for (Room room : rooms) {
			neededFloors = Math.max(neededFloors, room.getLevel() + 1);
		}
		this.floorCount = neededFloors;

		buildingCells = new int[floorCount][height][width];

		int roomNumber = 1;
		for (Room room : rooms) {
			int level = room.getLevel();
			int[][] cells = room.getRoomCells();
			if (cells == null) {
				if (verbose) System.err.println("Предупреждение: у комнаты '" + room.getName()
						+ "' (номер " + roomNumber + ") нет сетки клеток, комната пропущена");
				roomNumber++;
				continue;
			}
			for (int cy = 0; cy < cells.length; cy++) {
				for (int cx = 0; cx < cells[cy].length; cx++) {
					if (cells[cy][cx] == 0) {
						continue;
					}
					int gx = room.getX() + cx;
					int gy = room.getY() + cy;
					if (gx < 0 || gx >= width || gy < 0 || gy >= height) {
						if (verbose) System.err.println("Предупреждение: комната '" + room.getName() + "' (номер "
								+ roomNumber + ") выходит за границы здания в точке "
								+ gx + "," + gy + " - клетка пропущена");
						continue;
					}
					if (buildingCells[level][gy][gx] != 0 && verbose) {
						System.err.println("Предупреждение: комнаты " + buildingCells[level][gy][gx]
								+ " и " + roomNumber + " перекрываются на этаже " + level
								+ " в точке " + gx + "," + gy);
					}
					buildingCells[level][gy][gx] = roomNumber;
				}
			}
			roomNumber++;
		}
	}

	// =====================================================================
	// Шаг 5: крыши
	// =====================================================================

	/**
	 * AI: новый метод.
	 *
	 * Построение крыш по блоку <Roofs> и по массиву клеток здания.
	 *
	 * Двускатной крыше (PeakNS, PeakWE) нужен пустой этаж сверху, иначе
	 * TileZed обрежет скаты. Если такого этажа нет, здание надстраивается
	 * и сетка клеток пересобирается - лишний этаж остаётся пустым,
	 * как в образцах samp1 и samp2. Плоской крыше надстройка не нужна
	 * (samp3: крыши на этажах 2 и 3, всего 4 этажа).
	 */
	public void InitRoofs() {
		roofs = RoofGenerator.generate(buildingCells, width, height, roofPlan);

		int requiredFloors = floorCount;
		for (Roof roof : roofs) {
			requiredFloors = Math.max(requiredFloors, roof.requiredFloorCount());
		}
		if (requiredFloors > floorCount) {
			floorCount = requiredFloors;
			// Пересобираем сетку клеток под новое количество этажей.
			// Комнаты не меняются, добавленные этажи остаются пустыми.
			InitBuildingCells(false);
		}
	}

	public List<Roof> getRoofs() {
		return roofs;
	}

	/** Крыши указанного этажа. */
	public List<Roof> getRoofs(int floor) {
		List<Roof> result = new ArrayList<Roof>();
		for (Roof roof : roofs) {
			if (roof.floor == floor) {
				result.add(roof);
			}
		}
		return result;
	}

	// =====================================================================
	// Параметры здания из коллекций
	// =====================================================================

	/**
	 * Разбор объектов type="BuildingParameter".
	 *
	 * Вызывать строго после CommonData.DetermineListType(): номер набора тайлов
	 * берётся из relativeNumberLocation, то есть из сквозной нумерации tile_entry
	 * по всему зданию, а не из локального счётчика.
	 */
	public void InitBuildingparameters() {
		try {
			ExteriorWall = 0;
			ExteriorWallTrim = 0;
			Door = 0;
			DoorFrame = 0;
			Window = 0;
			Curtains = 0;
			Shutters = 0;
			Stairs = 0;
			RoofCap = 0;
			RoofSlope = 0;
			RoofTop = 0;
			GrimeWall = 0;

			for (int i = randStart; i < randEnd; i++) {
				applyBuildingParameter(data.RandomCollections.get(i));
			}
			for (int i = nonRandStart; i < nonRandEnd; i++) {
				applyBuildingParameter(data.NonrandomElements.get(i));
			}
			// AI: готовые tile_entry крыши тоже задают параметры здания
			for (int i = rawStart; i < rawEnd; i++) {
				applyBuildingParameter(data.RawTileEntries.get(i));
			}
		} catch (Exception e) {
			System.err.println("Ошибка загрузки параметров здания: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void applyBuildingParameter(Collection coll) {
		if (!"BuildingParameter".equals(coll.GetTypeOfObject())) {
			return;
		}
		int j = coll.relativeNumberLocation;
		String param = coll.GetParameter();
		switch (param) {
		case "ExteriorWall":      if (ExteriorWall == 0)     { ExteriorWall = j; }     break;
		case "ExteriorWallTrim":  if (ExteriorWallTrim == 0) { ExteriorWallTrim = j; } break;
		case "Door":              if (Door == 0)             { Door = j; }             break;
		case "DoorFrame":         if (DoorFrame == 0)        { DoorFrame = j; }         break;
		case "Window":            if (Window == 0)           { Window = j; }           break;
		case "Curtains":          if (Curtains == 0)         { Curtains = j; }         break;
		case "Shutters":          if (Shutters == 0)         { Shutters = j; }         break;
		case "Stairs":            if (Stairs == 0)           { Stairs = j; }           break;
		case "RoofCap":           if (RoofCap == 0)          { RoofCap = j; }          break;
		case "RoofSlope":         if (RoofSlope == 0)        { RoofSlope = j; }        break;
		case "RoofTop":           if (RoofTop == 0)          { RoofTop = j; }          break;
		case "GrimeWall":         if (GrimeWall == 0)        { GrimeWall = j; }        break;
		default:
			throw new IllegalArgumentException("Неизвестный параметр здания: " + param);
		}
	}

	// =====================================================================
	// Вывод
	// =====================================================================

	/**
	 * Сетка комнат одного этажа в формате .tbx:
	 * строки разделены ",\n", последняя строка без запятой в конце.
	 */
	public String getRoomCellsString(int floor) {
		if (buildingCells == null) {
			throw new IllegalStateException("Массив клеток здания не построен, вызовите InitBuildingCells()");
		}
		StringBuilder sb = new StringBuilder();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				sb.append(buildingCells[floor][y][x]);
				if (x < width - 1) {
					sb.append(",");
				}
			}
			if (y < height - 1) {
				sb.append(",\n");
			}
		}
		return sb.toString();
	}

	/** Отладочная печать этажа: точка вместо 0, номер комнаты иначе. */
	public String getFloorMap(int floor) {
		StringBuilder sb = new StringBuilder();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int cell = buildingCells[floor][y][x];
				sb.append(cell == 0 ? "." : Integer.toString(cell % 10));
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	// =====================================================================
	// Геттеры
	// =====================================================================

	public int getVersion()          { return version; }
	public String getName()          { return Name; }
	public int getWidth()            { return width; }
	public int getHeight()           { return height; }
	public int getFloorCount()       { return floorCount; }
	public int[][][] getBuildingCells() { return buildingCells; }

	public int getExteriorWall()     { return ExteriorWall; }
	public int getExteriorWallTrim() { return ExteriorWallTrim; }
	public int getDoor()             { return Door; }
	public int getDoorFrame()        { return DoorFrame; }
	public int getWindow()           { return Window; }
	public int getCurtains()         { return Curtains; }
	public int getShutters()         { return Shutters; }
	public int getStairs()           { return Stairs; }
	public int getRoofCap()          { return RoofCap; }
	public int getRoofSlope()        { return RoofSlope; }
	public int getRoofTop()          { return RoofTop; }
	public int getGrimeWall()        { return GrimeWall; }
}
