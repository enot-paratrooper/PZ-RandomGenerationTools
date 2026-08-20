package randRoomGen.ObjectTiles;

/**
 * AI: новый класс.
 *
 * Параметр уровня здания: type="BuildingParameter" parameter="ExteriorWall".
 * Наследуется от Roomparameter, потому что RandomCollection.GetParameter()
 * и NonrandomElement.GetParameter() приводят объект именно к Roomparameter.
 *
 * Имя класса не CamelCase намеренно: ObjectTileFactory собирает имя как
 * первая буква в верхнем регистре + остаток в нижнем, поэтому
 * type="BuildingParameter" превращается в класс Buildingparameter
 * (ровно так же, как RoomParameter -> Roomparameter).
 */
public class Buildingparameter extends Roomparameter {
}
