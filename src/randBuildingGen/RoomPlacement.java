package randBuildingGen;

import java.io.FileInputStream;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import randRoomGen.Room;

import static tools.ConfigPaths.roomFile;

/**
 * [AI] НОВЫЙ КЛАСС (добавлен AI).
 *
 * Лёгкое описание размещения комнаты в здании: имя файла, координаты левого
 * верхнего угла, размеры и маска занятых клеток.
 *
 * Зачем отдельный класс, а не сразу Room:
 * загрузка Room имеет побочный эффект — она добавляет коллекции, нерандомные
 * элементы и группы в CommonData. Если проверять пересечения уже после
 * загрузки, то при ошибке компоновки CommonData останется «грязным».
 * Поэтому здание сначала читает из файлов комнат ТОЛЬКО геометрию
 * (sizeX, sizeY, <tiles>), раскладывает комнаты, проверяет пересечения,
 * и только затем загружает содержимое комнат.
 */
public class RoomPlacement {

	/** Имя файла комнаты без пути и расширения, например "Kitchen_7_4". */
	public String name;
	/** Координаты левого верхнего угла в клетках здания. */
	public int x;
	public int y;
	public int sizeX;
	public int sizeY;
	/** Маска клеток комнаты в локальных координатах: cells[y][x], 0 = клетка не принадлежит комнате. */
	public int[][] cells;
	/** Номер комнаты в массиве клеток здания, 1..N. */
	public int number;
	/** Заполняется на втором этапе, после реальной загрузки. */
	public Room room;

	/**
	 * Читает из конфигурации комнаты только геометрию, без загрузки коллекций.
	 */
	public static RoomPlacement readGeometry(String roomName) throws Exception {
		RoomPlacement placement = new RoomPlacement();
		placement.name = roomName;

		String fileName = roomFile(roomName);
		InputStream inputStream = RoomPlacement.class.getResourceAsStream(fileName);
		if (inputStream == null) {
			inputStream = new FileInputStream(fileName);
		}
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(inputStream);
			document.getDocumentElement().normalize();

			Element roomElement = document.getDocumentElement();

			String sizeX = roomElement.getAttribute("sizeX");
			String sizeY = roomElement.getAttribute("sizeY");
			if (sizeX.isEmpty() || sizeY.isEmpty()) {
				throw new IllegalArgumentException("Комната '" + roomName + "': не заданы sizeX/sizeY");
			}
			placement.sizeX = Integer.parseInt(sizeX);
			placement.sizeY = Integer.parseInt(sizeY);

			NodeList tilesNodes = roomElement.getElementsByTagName("tiles");
			if (tilesNodes.getLength() == 0) {
				throw new IllegalArgumentException("Комната '" + roomName + "': отсутствует блок <tiles>");
			}
			// используется тот же парсер, что и в Room, чтобы формат не разъезжался
			placement.cells = Room.parseGrid(((Element) tilesNodes.item(0)).getTextContent().trim());

			if (placement.cells.length != placement.sizeY) {
				throw new IllegalArgumentException("Комната '" + roomName + "': в <tiles> "
						+ placement.cells.length + " строк, а sizeY=" + placement.sizeY);
			}
			for (int i = 0; i < placement.cells.length; i++) {
				if (placement.cells[i].length != placement.sizeX) {
					throw new IllegalArgumentException("Комната '" + roomName + "': в строке " + i
							+ " блока <tiles> " + placement.cells[i].length + " ячеек, а sizeX=" + placement.sizeX);
				}
			}
		} finally {
			inputStream.close();
		}
		return placement;
	}

	/**
	 * Значение клетки по ГЛОБАЛЬНЫМ координатам здания.
	 * Вне габаритов комнаты возвращает 0.
	 */
	public int cellAt(int globalX, int globalY) {
		int lx = globalX - x;
		int ly = globalY - y;
		if (ly < 0 || ly >= cells.length) {
			return 0;
		}
		if (lx < 0 || lx >= cells[ly].length) {
			return 0;
		}
		return cells[ly][lx];
	}

	/**
	 * Проверка пересечения двух комнат.
	 *
	 * Сначала быстрая проверка габаритных прямоугольников, затем поклеточная —
	 * комнаты могут быть невыпуклыми (в <tiles> допустимы нули), поэтому
	 * пересечение прямоугольников само по себе ещё не значит конфликт.
	 *
	 * Комнаты, стоящие вплотную, пересечением НЕ считаются: в Project Zomboid
	 * стена рисуется по границе клетки, общая стена — нормальная ситуация.
	 */
	public boolean intersects(RoomPlacement other) {
		// габаритные прямоугольники не перекрываются
		if (x + sizeX <= other.x || other.x + other.sizeX <= x) {
			return false;
		}
		if (y + sizeY <= other.y || other.y + other.sizeY <= y) {
			return false;
		}
		// поклеточная проверка области перекрытия
		int fromX = Math.max(x, other.x);
		int toX = Math.min(x + sizeX, other.x + other.sizeX);
		int fromY = Math.max(y, other.y);
		int toY = Math.min(y + sizeY, other.y + other.sizeY);
		for (int gy = fromY; gy < toY; gy++) {
			for (int gx = fromX; gx < toX; gx++) {
				if (cellAt(gx, gy) != 0 && other.cellAt(gx, gy) != 0) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return name + "[#" + number + " x=" + x + " y=" + y + " " + sizeX + "x" + sizeY + "]";
	}
}
