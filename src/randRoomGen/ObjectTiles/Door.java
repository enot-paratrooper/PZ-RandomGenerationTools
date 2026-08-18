package randRoomGen.ObjectTiles;

import org.w3c.dom.Element;

public class Door extends ObjectTile {

	@Override
	public void initialize(Element element) {
		super.initialize(element);
		this.TileParamers.add(getTileParameter(element, "FrameTile"));
	}
	
	public int getFrameTile() {
		return this.TileParamers.get(1).TileNum;
	}
	public boolean hasLinkFrameTile() {
		return this.TileParamers.get(1).hasLink;
	}
}
