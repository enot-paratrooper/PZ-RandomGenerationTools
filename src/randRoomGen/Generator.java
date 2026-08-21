package randRoomGen;

import changeToTBX.XmlCreator;
import containers.CommonData;
import randBuildingGen.Building;


public class Generator {
	
	public static void main(String[] args)
	{		
		generateBuilding_test();
	}
	
	public static void generateRoom_test() 
	{
		CommonData data = new CommonData();
		Room room = new Room(data);
		room.loadRoom("TestRoom_22_28",0,0,0);		
		data.MergeCollections();
		data.DetermineListType();
		data.InitTileSets();
		data.InitLinks();	
		room.InitRoomparameters();
		XmlCreator.createBuildingXml("C:\\Users\\I\\Desktop\\1231\\test2.tbx",data,room);
	}
	
	public static void generateBuilding_test() 
	{
		CommonData data = new CommonData();
		Building building = new Building(data);
		building.loadBuilding("TestBuilding");
		data.MergeCollections();
		data.DetermineListType();
		data.InitTileSets();
		data.InitLinks();
		building.InitBuildingparameters();
		for (Room room : data.randomRooms) room.InitRoomparameters();
		XmlCreator.createBuildingXml("C:\\Users\\I\\Desktop\\1231\\test2.tbx",data,building);
	}

}
