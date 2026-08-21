package randRoomGen;

import org.w3c.dom.Element;

/**
 * AI: новый класс.
 *
 * Готовый блок <tile_entry>, который переписывается в .tbx как есть.
 *
 * Нужен для наборов, которые не ложатся на механизм Tileset:
 * roof_caps (32 enum-а, часть тайлов пустая, тайлы из двух разных тайлсетов),
 * roof_slopes (38 enum-ов с атрибутами offset), roof_tops (6 enum-ов с offset).
 * Разбирать такие наборы в индексы бессмысленно - они фиксированные,
 * случайность в них не нужна.
 *
 * В остальном ведёт себя как обычная Collection: попадает в usedTile через
 * CommonData.DetermineListType и получает сквозной номер relativeNumberLocation,
 * поэтому на него можно ссылаться из параметров здания.
 */
public class RawTileEntry extends Collection {

	private Element tileEntry;
	private String parameter = "";
	private String typeOfObject = "BuildingParameter";

	/**
	 * @param tileEntryElement элемент <tile_entry> (будет склонирован)
	 * @param parameter        имя параметра, например RoofCap
	 * @param typeOfObject     BuildingParameter или RoomParameter
	 */
	public void load(Element tileEntryElement, String parameter, String typeOfObject) {
		if (tileEntryElement == null) {
			throw new IllegalArgumentException("Не задан tile_entry для параметра '" + parameter + "'");
		}
		if (parameter == null || parameter.trim().isEmpty()) {
			throw new IllegalArgumentException("Не задан parameter у готового tile_entry");
		}
		this.tileEntry = (Element) tileEntryElement.cloneNode(true);
		this.parameter = parameter.trim();
		if (typeOfObject != null && !typeOfObject.trim().isEmpty()) {
			this.typeOfObject = typeOfObject.trim();
		}
	}

	/** Элемент для дословной записи в .tbx. */
	public Element getRawTileEntry() {
		return tileEntry;
	}

	@Override
	public String GetTypeOfObject() {
		return typeOfObject;
	}

	@Override
	public String GetParameter() {
		return parameter;
	}

	@Override
	public String getNameOfPickedTileSet() {
		return tileEntry == null ? "" : tileEntry.getAttribute("category");
	}

	@Override
	public boolean isFurniture() {
		return false;
	}
}
