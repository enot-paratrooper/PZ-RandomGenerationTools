package randRoomGen.ObjectTiles;

import org.w3c.dom.Element;

public class Windows extends ObjectTile{
	int CurtainsTile;
	int ShuttersTile;
	@Override
	 public void initialize(Element element)
		{
		 super.initialize(element);
		 this.CurtainsTile = Integer.parseInt(element.getAttribute("CurtainsTile"));
		 this.ShuttersTile = Integer.parseInt(element.getAttribute("ShuttersTile"));
		}

}
