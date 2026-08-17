package randRoomGen;

import changeToTBX.XmlCreator;
import containers.CommonData;


public class Generator {
	
	public static void main(String[] args)
	{		
		CommonData data = new CommonData();
		Room room = new Room(data);
		room.loadRoom("Kitchen_7_4");		
		data.MergeCollections();
		data.InitTileSets();
		data.InitLinks();	
		room.InitRoomparameters();
		XmlCreator.createBuildingXml("C:\\Users\\I\\Desktop\\1231\\test2.tbx",data,room);
	}

}
