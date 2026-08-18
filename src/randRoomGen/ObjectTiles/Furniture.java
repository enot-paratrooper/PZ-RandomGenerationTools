package randRoomGen.ObjectTiles;

/**
 * Мебель. Поля placeble и layer подняты в ObjectTile, поэтому здесь остаётся
 * только специфика мебели; разбор атрибутов void/layer делает базовый класс.
 */
public class Furniture extends ObjectTile {

	public int size;
}
