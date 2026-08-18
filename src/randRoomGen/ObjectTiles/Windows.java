package randRoomGen.ObjectTiles;

import org.w3c.dom.Element;

public class Windows extends ObjectTile {

	@Override
	public void initialize(Element element) {
		super.initialize(element);
		this.TileParamers.add(getTileParameter(element, "CurtainsTile"));
		this.TileParamers.add(getTileParameter(element, "ShuttersTile"));
	}
	public int getCurtainsTile() {
		return this.TileParamers.get(1).TileNum;
	}
	public int getShuttersTile() {
		return this.TileParamers.get(2).TileNum;
	}
	public boolean hasLinkCurtainsTile() {
		return this.TileParamers.get(1).hasLink;
	}
	public boolean hasLinkShuttersTile() {
		return this.TileParamers.get(2).hasLink;
	}
}
