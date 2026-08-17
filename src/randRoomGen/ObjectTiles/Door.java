package randRoomGen.ObjectTiles;

import org.w3c.dom.Element;

public class Door extends ObjectTile{
	
	int FrameTile;
	
	@Override
	 public void initialize(Element element)
		{
		 super.initialize(element);
		 this.FrameTile = Integer.parseInt(element.getAttribute("FrameTile"));
		}

}
