package randRoomGen.ObjectTiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.w3c.dom.Element;

import static tools.StringTools.parseRanges;
import static tools.RandTools.chance;

public abstract class ObjectTile implements InitializeTile {

	public String type = "";
	/** Ссылка на tile_entry. -1 = взять значение по умолчанию из параметров комнаты. */
	public List<TileParameter> TileParamers = new ArrayList<TileParameter>();
	public int x;
	public int y;
	private List<Integer> rangeX;
	private List<Integer> rangeY;
	public String direction;
	/** Слой отрисовки в .tbx (например WallFurniture). null = слой по умолчанию. */
	public String layer = null;
	/** false = объект выпал по атрибуту void и размещаться не должен. */
	public boolean placeble = true;
	/** AI: этаж здания, на который попадёт объект. */
	public int floor = 0;
	/** AI: true = этаж задан атрибутом floor и не должен перекрываться этажом родителя. */
	public boolean floorExplicit = false;
	/**
	 * AI: индекс комнаты-владельца в CommonData.randomRooms.
	 * -1 = объект уровня здания, наборы тайлов по умолчанию берутся из параметров здания.
	 */
	public int roomIndex = -1;

	private static Random random = new Random();

	public void initialize(Element element) {
		this.type = element.getAttribute("type");
		this.direction = element.getAttribute("orient");
		this.rangeX = parseRanges(element.getAttribute("x"));
		this.rangeY = parseRanges(element.getAttribute("y"));
		if (element.hasAttribute("layer")) {
			this.layer = element.getAttribute("layer");
		}
		// AI: этаж отдельного объекта
		if (element.hasAttribute("floor")) {
			String floorValue = element.getAttribute("floor").trim();
			if (!floorValue.isEmpty()) {
				this.floor = Integer.parseInt(floorValue);
				this.floorExplicit = true;
			}
		}
		if (element.hasAttribute("void")) {
			this.placeble = chance(element.getAttribute("void"));
		}
		this.TileParamers.add(getTileParameter(element, "Tile"));
		initializeCoord();
	}

	/**
	 * Чтение необязательного числового атрибута.
	 * Раньше Door/Windows падали с NumberFormatException, если атрибут не задан.
	 */
	protected static TileParameter getTileParameter(Element element, String attributeName) {
		if (!element.hasAttribute(attributeName)) {
			return new TileParameter(attributeName);
		}
		String value = element.getAttribute(attributeName).trim();
		if (value.isEmpty()) {
			return new TileParameter(attributeName);
		}
		try {
			if(value.startsWith("link")) {
				return new TileParameter(attributeName, true, value.substring(5));
			}
			return new TileParameter(attributeName,Integer.parseInt(value));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					"Некорректное значение атрибута '" + attributeName + "': " + value, e);
		}
	}

	private void initializeCoord() {
		if (rangeX == null || rangeX.isEmpty() || rangeY == null || rangeY.isEmpty()) {
			throw new IllegalArgumentException(
					"Не заданы или некорректны координаты объекта типа '" + type + "' (x='"
							+ rangeX + "', y='" + rangeY + "')");
		}
		this.x = rangeX.get(random.nextInt(rangeX.size()));
		this.y = rangeY.get(random.nextInt(rangeY.size()));
	}
	
	public int getTile() {
		return this.TileParamers.get(0).TileNum;
	}
	public boolean hasLinkTile() {
		return this.TileParamers.get(0).hasLink;
	}
}
