package randBuildingGen;

/**
 * [AI] НОВЫЙ КЛАСС (добавлен AI).
 *
 * Ошибка компоновки здания: пересечение комнат или выход комнаты за габариты.
 *
 * Специально сделана отдельным типом и НЕ проглатывается внутри
 * Building.loadBuilding: молча сгенерированное здание с наложенными друг на
 * друга комнатами даст сломанный .tbx, который TileZed откроет, но который
 * будет неверным. Такую ошибку лучше увидеть сразу.
 */
public class BuildingLayoutException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public BuildingLayoutException(String message) {
		super(message);
	}
}
