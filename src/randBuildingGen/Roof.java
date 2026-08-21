package randBuildingGen;

/**
 * AI: новый класс.
 *
 * Одна крыша в .tbx:
 * <object type="roof" width=".." height=".." RoofType=".." Depth=".."
 *         cappedW=".." cappedN=".." cappedE=".." cappedS=".."
 *         CapTiles=".." SlopeTiles=".." TopTiles=".." x=".." y=".."/>
 *
 * Наборы тайлов (CapTiles/SlopeTiles/TopTiles) в объекте не хранятся:
 * они общие на здание и подставляются в XmlCreator из параметров
 * RoofCap / RoofSlope / RoofTop.
 */
public class Roof {

	/** FlatTop, PeakNS или PeakWE. */
	public String roofType = RoofGenerator.FLAT_TOP;
	/** Zero, One, Two, Three. */
	public String depth = "Zero";

	public int x;
	public int y;
	public int width;
	public int height;
	/** Этаж, в блок которого попадёт объект крыши. */
	public int floor;

	public boolean cappedW = true;
	public boolean cappedN = true;
	public boolean cappedE = true;
	public boolean cappedS = true;

	public Roof() {
	}

	public Roof(String roofType, int x, int y, int width, int height, int floor) {
		this.roofType = roofType;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.floor = floor;
	}

	/**
	 * Двускатной крыше нужен пустой этаж сверху, иначе TileZed обрежет скаты.
	 * У плоской крыши такого требования нет.
	 */
	public boolean needsFloorAbove() {
		return RoofGenerator.PEAK_NS.equals(roofType) || RoofGenerator.PEAK_WE.equals(roofType);
	}

	/** Минимальное количество этажей здания, при котором крыша нарисуется целиком. */
	public int requiredFloorCount() {
		return needsFloorAbove() ? floor + 2 : floor + 1;
	}

	@Override
	public String toString() {
		return roofType + " " + width + "x" + height + " @ " + x + "," + y + " этаж " + floor
				+ " Depth=" + depth;
	}
}
