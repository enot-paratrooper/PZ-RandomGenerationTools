package randRoomGen.ObjectTiles;

import org.w3c.dom.Element;
import static tools.RandTools.chance;

public class Furniture extends ObjectTile  {
	 public int size;
	 public boolean placeble = true;
	 @Override
	 public void initialize(Element element)
		{
		 super.initialize(element);
		 if(element.hasAttribute("void")) {
			 
			 placeble = chance(element.getAttribute("void"));

			}
		}
	 

}

