package randBuildingGen;

import changeToTBX.XmlCreator;
import containers.CommonData;
import randRoomGen.Room;

/**
 * AI: точка входа для генерации здания целиком.
 * Класс randRoomGen.roomGenerator оставлен без изменений для тестов одной комнаты.
 */
public class buildingGenerator {

	public static void main(String[] args)
	{
		CommonData data = new CommonData();
		Building building = new Building(data);

		// 1) параметры здания, 2) объекты уровня здания,
		// 3) комнаты, 4) массив клеток - всё внутри loadBuilding
		building.loadBuilding("TestBuilding");

		data.MergeCollections();
		data.DetermineListType();
		data.InitTileSets();
		data.InitLinks();

		// Параметры считаются после DetermineListType: нужен сквозной
		// номер набора тайлов (relativeNumberLocation)
		building.InitBuildingparameters();
		for(Room room : data.randomRooms) {
			room.InitRoomparameters();
		}

		// Отладочная карта этажей и список крыш
		for(int floor = 0; floor < building.getFloorCount(); floor++) {
			System.out.println("Этаж " + floor + ":");
			System.out.println(building.getFloorMap(floor));
		}
		// AI: крыши строятся внутри loadBuilding, здесь только печать
		for(Roof roof : building.getRoofs()) {
			System.out.println("Крыша: " + roof);
		}

		XmlCreator.createBuildingXml("C:\\Users\\I\\Desktop\\1231\\testBuilding.tbx", data, building);
	}
}
