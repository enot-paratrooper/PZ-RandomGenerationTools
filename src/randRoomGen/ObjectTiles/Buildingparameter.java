package randRoomGen.ObjectTiles;

/**
 * [AI] НОВЫЙ КЛАСС (добавлен AI).
 *
 * Параметр уровня здания: ExteriorWall, ExteriorWallTrim, Door, DoorFrame,
 * Window, Curtains, Shutters, Stairs, RoofCap, RoofSlope, RoofTop, GrimeWall.
 *
 * Работает точно так же, как Roomparameter (читает атрибуты type и parameter),
 * отличается только именем типа, чтобы Building мог отделить параметры здания
 * от параметров комнаты.
 *
 * Имя класса подобрано под ObjectTileFactory.createReflective:
 *   type="BuildingParameter" -> "Buildingparameter"
 *
 * Наследование от Roomparameter обязательно: NonrandomElement.GetParameter()
 * и RandomCollection.GetParameter() делают приведение к (Roomparameter).
 */
public class Buildingparameter extends Roomparameter {
}
