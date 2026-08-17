package randRoomGen.ObjectTiles;

import java.util.List;
import java.util.Random;

import org.w3c.dom.Element;

import static tools.StringTools.parseRanges;

public abstract class ObjectTile implements InitializeTile{
	public String type = "";
	public int Tile;
	public int x;
	public int y;
	private List<Integer> rangeX;
	private List<Integer> rangeY;
	public String direction;
	
	private static Random random = new Random();
	
	public void initialize(Element element){
		this.type = element.getAttribute("type");
		this.direction = element.getAttribute("orient");
		this.rangeX = parseRanges(element.getAttribute("x"));
		this.rangeY = parseRanges(element.getAttribute("y"));
		initializeСoord();
	};

	private void initializeСoord() {
		this.x = rangeX.get(random.nextInt(rangeX.size()));
		this.y = rangeY.get(random.nextInt(rangeY.size()));
	}
}
