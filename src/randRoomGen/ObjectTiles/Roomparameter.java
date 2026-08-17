package randRoomGen.ObjectTiles;

import org.w3c.dom.Element;

public class Roomparameter extends ObjectTile{
	private String nameParameter;
	@Override
	 public void initialize(Element element)
		{
		 super.type = element.getAttribute("type");
		 this.nameParameter = element.getAttribute("parameter");
		}
	public String GetNameParameter()
	{
		return this.nameParameter;
	}
}