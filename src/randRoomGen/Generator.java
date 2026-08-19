package randRoomGen;

import containers.CommonData;
import randBuildingGen.Building;

/**
 * [AI] ИЗМЕНЁННЫЙ ФАЙЛ.
 *
 * Добавлен сценарий генерации здания. Старый сценарий одиночной комнаты
 * сохранён в методе generateSingleRoom() — он по-прежнему рабочий.
 *
 * Вызов XmlCreator для здания намеренно закомментирован: XmlCreator пока
 * не умеет писать несколько комнат, параметры здания и второй/третий floor.
 * См. README, раздел «Что осталось сделать в XmlCreator».
 */
public class Generator {

	public static void main(String[] args) {
		generateBuilding();
	}

	/** [AI] Новый сценарий: здание из шаблона. */
	public static void generateBuilding() {
		CommonData data = new CommonData();

		Building building = new Building(data);
		building.loadBuilding("TestBuilding");

		data.MergeCollections();
		data.InitTileSets();
		data.InitLinks();

		building.InitParameters();

		building.printLayout();

		// XmlCreator пока не трогаем:
		// XmlCreator.createBuildingXml("C:\\Users\\I\\Desktop\\1231\\test2.tbx", data, building);
	}

	/** Старый сценарий: одна комната. Работает без изменений. */
	public static void generateSingleRoom() {
		CommonData data = new CommonData();
		Room room = new Room(data);
		room.loadRoom("Kitchen_7_4");
		data.MergeCollections();
		data.InitTileSets();
		data.InitLinks();
		room.InitRoomparameters();
		// XmlCreator.createBuildingXml("C:\\Users\\I\\Desktop\\1231\\test2.tbx", data, room);
	}
}
