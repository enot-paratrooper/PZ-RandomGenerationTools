package randRoomGen;

import changeToTBX.XmlCreator;
import containers.CommonData;

//Только для тестов
public class roomGenerator {
	
	public static void main(String[] args)
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

}
