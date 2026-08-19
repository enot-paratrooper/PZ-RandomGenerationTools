package randRoomGen;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import containers.CommonData;
import randGroups.RandomGroup;

import static commonFunc.LoadFunc.loadRandColl;
import static commonFunc.LoadFunc.loadNonRandE;
import static commonFunc.LoadFunc.loadRandGroup;
import static tools.ConfigPaths.roomFile; // [AI]

/**
 * [AI] ИЗМЕНЁННЫЙ ФАЙЛ.
 *
 * Что изменено:
 *  1. Добавлена перегрузка loadRoom(name, offsetX, offsetY) — комната умеет
 *     размещаться внутри здания. Старая loadRoom(name) вызывает её с (0,0).
 *  2. УДАЛЕНЫ поля TileSetStart / TileSetEnd. Они были ошибочны для здания:
 *     TileSetStart брался из data.Collections.size(), но Collections
 *     заполняется только в MergeCollections() (после загрузки), поэтому
 *     всегда равнялся 0; а TileSetEnd считался как
 *     RandomCollections.size()+NonrandomElements.size(). Для одной комнаты
 *     это случайно работало, для нескольких — диапазон «залезал» в данные
 *     соседних комнат, потому что Collections = [все RandomCollections,
 *     затем все NonrandomElements].
 *     Вместо них хранятся два ЯВНЫХ диапазона:
 *       randCollFrom/To  — индексы в data.RandomCollections
 *       nonRandFrom/To   — индексы в data.NonrandomElements
 *     и метод ownsCollection(index, randomCollectionsCount).
 *  3. InitRoomparameters() переписан: индекс tile_entry теперь считается
 *     ГЛОБАЛЬНО по всему data.Collections (как это делает XmlCreator через
 *     iterTileSet), а не с единицы внутри комнаты. Иначе в здании из
 *     нескольких комнат все комнаты ссылались бы на первые tile_entry.
 *  4. Добавлены resetRoomparameters() и setParameter(name, index) — их
 *     использует Building для сквозной нумерации параметров всего здания.
 *  5. Добавлены offsetX/offsetY, number, getRoomCells(), getRoomFileName().
 *  6. Путь к файлу комнаты вынесен в tools.ConfigPaths.
 */
public class Room {

	private String Name;
	private String roomFileName; // [AI] имя конфигурационного файла комнаты
	private int SizeX;
	private int SizeY;
	private int[][] roomCells;
	private String roomCellsString;

	// [AI] положение комнаты внутри здания и её номер в массиве клеток здания
	private int offsetX = 0;
	private int offsetY = 0;
	private int number = 0;

	private List<Integer> Color = new ArrayList<Integer>();

	// [AI] вместо TileSetStart/TileSetEnd — явные диапазоны владения
	private int randCollFrom = 0;
	private int randCollTo = 0;
	private int nonRandFrom = 0;
	private int nonRandTo = 0;

	private int InteriorWall;
	private int InteriorWallTrim;
	private int Floor;
	private int GrimeFloor;
	private int GrimeWall;

	private List<RandomGroup> RandomGroups = new ArrayList<RandomGroup>();

	private CommonData data;

	public Room(CommonData data) {
		this.data = data;
	}

	public void loadRoom(String roomName) {
		// [AI] обратная совместимость: одиночная комната в начале координат
		loadRoom(roomName, 0, 0);
	}

	/**
	 * [AI] НОВАЯ ПЕРЕГРУЗКА.
	 * offsetX/offsetY — левый верхний угол комнаты в координатах здания.
	 * Смещение прокидывается во все коллекции, нерандомные элементы и группы.
	 */
	public void loadRoom(String roomName, int offsetX, int offsetY) {
		Name = "";
		SizeX = 0;
		SizeY = 0;
		this.roomFileName = roomName;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		try {
			String fileName = roomFile(roomName); // [AI] путь из ConfigPaths

			InputStream inputStream = getClass().getResourceAsStream(fileName);
			if (inputStream == null) {
				inputStream = new FileInputStream(fileName);
			}

			data.linker.LoadVariables(fileName, data.RandomCollections.size() - 1);

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(inputStream);
			document.getDocumentElement().normalize();

			Element roomElement = document.getDocumentElement();
			Name = roomElement.getAttribute("name");

			String SizeX = roomElement.getAttribute("sizeX");
			if (!SizeX.isEmpty()) {
				this.SizeX = Integer.parseInt(SizeX);
			} else {
				throw new NumberFormatException("Незадан размер по X");
			}
			String SizeY = roomElement.getAttribute("sizeY");
			if (!SizeY.isEmpty()) {
				this.SizeY = Integer.parseInt(SizeY);
			} else {
				throw new NumberFormatException("Незадан размер по Y");
			}

			// Заполнение массива комнаты
			NodeList roomCellsNodes = roomElement.getElementsByTagName("tiles");
			Element roomCellsE = (Element) roomCellsNodes.item(0);
			roomCellsString = roomCellsE.getTextContent().trim();
			roomCells = parseGrid(roomCellsString);

			// [AI] проверка соответствия сетки объявленному размеру
			checkGrid(roomName);

			// [AI] запоминаем начало диапазонов владения
			randCollFrom = data.RandomCollections.size();
			nonRandFrom = data.NonrandomElements.size();

			// Загрузка RandomCollection
			loadRandColl(data, roomElement, this.offsetX, this.offsetY);     // [AI] +смещение
			// Загрузка NonrandomElements
			loadNonRandE(data, roomElement, this.offsetX, this.offsetY);     // [AI] +смещение
			// Загрузка RandomGroups
			loadRandGroup(data, roomElement, RandomGroups, this.offsetX, this.offsetY); // [AI] +смещение

			// [AI] запоминаем конец диапазонов владения
			randCollTo = data.RandomCollections.size();
			nonRandTo = data.NonrandomElements.size();

			inputStream.close(); // [AI] поток не закрывался

		} catch (Exception e) {
			System.err.println("Ошибка загрузки комнаты '" + roomName + "': " + e.getMessage());
			e.printStackTrace();
		}
	}

	/** [AI] Сетка <tiles> должна совпадать с sizeX/sizeY. */
	private void checkGrid(String roomName) {
		if (roomCells.length != SizeY) {
			throw new IllegalArgumentException("Комната '" + roomName + "': в <tiles> " + roomCells.length
					+ " строк, а sizeY=" + SizeY);
		}
		for (int i = 0; i < roomCells.length; i++) {
			if (roomCells[i].length != SizeX) {
				throw new IllegalArgumentException("Комната '" + roomName + "': в строке " + i + " блока <tiles> "
						+ roomCells[i].length + " ячеек, а sizeX=" + SizeX);
			}
		}
	}

	/**
	 * [AI] Принадлежит ли коллекция с глобальным индексом index (индекс в
	 * data.Collections) этой комнате.
	 * randomCollectionsCount — data.RandomCollections.size() ПОСЛЕ MergeCollections.
	 */
	public boolean ownsCollection(int index, int randomCollectionsCount) {
		if (index < randomCollectionsCount) {
			return index >= randCollFrom && index < randCollTo;
		}
		int nr = index - randomCollectionsCount;
		return nr >= nonRandFrom && nr < nonRandTo;
	}

	/** [AI] Сброс параметров комнаты и розыгрыш цвета. */
	public void resetRoomparameters() {
		Color.clear();
		Color.add(data.random.nextInt(256));
		Color.add(data.random.nextInt(256));
		Color.add(data.random.nextInt(256));
		InteriorWall = 0;
		InteriorWallTrim = 0;
		Floor = 0;
		GrimeFloor = 0;
		GrimeWall = 0;
	}

	/**
	 * [AI] Установка одного параметра комнаты.
	 * tileEntryIndex — СКВОЗНОЙ номер tile_entry в готовом tbx
	 * (тот же счётчик, что iterTileSet в XmlCreator).
	 */
	public void setParameter(String param, int tileEntryIndex) {
		switch (param) {
			case "InteriorWall":
				InteriorWall = tileEntryIndex;
				break;
			case "InteriorWallTrim":
				InteriorWallTrim = tileEntryIndex;
				break;
			case "Floor":
				Floor = tileEntryIndex;
				break;
			case "GrimeFloor":
				GrimeFloor = tileEntryIndex;
				break;
			case "GrimeWall":
				GrimeWall = tileEntryIndex;
				break;
			default:
				System.err.println("Неизвестный параметр комнаты '" + Name + "': " + param);
		}
	}

	/**
	 * [AI] ПЕРЕПИСАН. Оставлен для сценария «одна комната без здания»
	 * (см. Generator). Внутри здания вместо него вызывается
	 * Building.InitParameters(), который делает то же самое сразу для всех комнат.
	 * Вызывать ТОЛЬКО после data.MergeCollections().
	 */
	public void InitRoomparameters() {
		try {
			resetRoomparameters();
			int tileEntryIndex = 1;
			int randomCount = data.RandomCollections.size();
			for (int i = 0; i < data.Collections.size(); i++) {
				Collection coll = data.Collections.get(i);
				String type = coll.GetTypeOfObject();
				if (type == null) {
					continue;
				}
				if (type.equals("RoomParameter")) {
					if (ownsCollection(i, randomCount)) {
						setParameter(coll.GetParameter(), tileEntryIndex);
					}
					tileEntryIndex++;
				} else if (type.equals("BuildingParameter")) {
					// [AI] параметры здания тоже занимают tile_entry
					tileEntryIndex++;
				}
			}
		} catch (Exception e) {
			System.err.println("Ошибка загрузки параметров" + e.getMessage());
			e.printStackTrace();
		}
	}

	public ArrayList<String> getTypesOfTiles() {
		ArrayList<String> TypesOfTiles = new ArrayList<String>();
		for (Collection coll : data.Collections) {
			TypesOfTiles.add(coll.GetTypeOfObject());
		}
		return TypesOfTiles;
	}

	public String getTypeOfRoomparametrs(int i) {
		return data.Collections.get(i).GetParameter();
	}

	public static int[][] parseGrid(String gridString) {
		// Удаляем все пробелы и переносы строк в начале и конце
		String trimmed = gridString.trim();

		// Разбиваем на строки по запятым и переводам строк
		String[] rows = trimmed.split("\\s*,\\s*\n\\s*");

		// Если в конце строки есть лишняя запятая, обрабатываем последнюю строку отдельно
		if (trimmed.endsWith(",")) {
			rows[rows.length - 1] = rows[rows.length - 1].replaceAll(",$", "");
		}

		int numRows = rows.length;

		String[] firstRowCells = rows[0].split("\\s*,\\s*");
		int numCols = firstRowCells.length;

		int[][] grid = new int[numRows][numCols];

		for (int i = 0; i < numRows; i++) {
			String[] cells;
			if (i == 0) {
				cells = firstRowCells;
			} else {
				cells = rows[i].split("\\s*,\\s*");
			}
			for (int j = 0; j < numCols; j++) {
				grid[i][j] = Integer.parseInt(cells[j]);
			}
		}

		return grid;
	}

	public String getName() {
		return Name;
	}

	/** [AI] */
	public String getRoomFileName() {
		return roomFileName;
	}

	public int getSizeX() {
		return SizeX;
	}

	public int getSizeY() {
		return SizeY;
	}

	/** [AI] */
	public int getOffsetX() {
		return offsetX;
	}

	/** [AI] */
	public int getOffsetY() {
		return offsetY;
	}

	/** [AI] Номер комнаты в массиве клеток здания (1..N, 0 = не размещена). */
	public int getNumber() {
		return number;
	}

	/** [AI] */
	public void setNumber(int number) {
		this.number = number;
	}

	/** [AI] Сетка комнаты в локальных координатах: roomCells[y][x]. */
	public int[][] getRoomCells() {
		return roomCells;
	}

	public String getColor() {
		return Color.get(0) + " " + Color.get(1) + " " + Color.get(2);
	}

	public int getInteriorWall() {
		return InteriorWall;
	}

	public int getInteriorWallTrim() {
		return InteriorWallTrim;
	}

	public int getFloor() {
		return Floor;
	}

	public int getGrimeFloor() {
		return GrimeFloor;
	}

	public int getGrimeWall() {
		return GrimeWall;
	}

	public String getRoomCellsString() {
		return roomCellsString;
	}
}
