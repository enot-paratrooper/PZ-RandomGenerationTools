package randRoomGen.ObjectTiles;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;

public class ObjectTileFactory {

	private static final String PACKAGE_NAME = "randRoomGen.ObjectTiles.";

	/**
	 * Типы, у которых имя класса не выводится из имени типа по общему правилу.
	 * Без этого type="window" превращался в несуществующий класс Window.
	 */
	private static final Map<String, String> CLASS_ALIASES = new HashMap<String, String>();
	static {
		CLASS_ALIASES.put("window", "Windows");
		CLASS_ALIASES.put("windows", "Windows");
		CLASS_ALIASES.put("stair", "Stairs");
		CLASS_ALIASES.put("stairs", "Stairs");
	}

	public static ObjectTile createReflective(Element element, int numberCollection) {
		String objectType = element.getAttribute("type");
		if (objectType == null || objectType.trim().isEmpty()) {
			throw new IllegalArgumentException("Не задан type объекта в коллекции: " + numberCollection);
		}
		objectType = objectType.trim();
		String className = PACKAGE_NAME + resolveClassName(objectType);
		try {
			Class<?> clazz = Class.forName(className);
			if (!ObjectTile.class.isAssignableFrom(clazz)) {
				throw new IllegalArgumentException("Class " + className + " does not implement ObjectTile");
			}
			// Получаем конструктор без параметров
			Constructor<?> constructor = clazz.getDeclaredConstructor();
			// Создаем экземпляр
			ObjectTile obj = (ObjectTile) constructor.newInstance();

			// Инициализируем объект
			obj.initialize(element);

			return obj;
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException(
					"Class not found for object type '" + objectType + "' collection: " + numberCollection, e);
		} catch (Exception e) {
			throw new RuntimeException("Failed to create object collection: " + numberCollection, e);
		}
	}

	private static String resolveClassName(String objectType) {
		String alias = CLASS_ALIASES.get(objectType.toLowerCase());
		if (alias != null) {
			return alias;
		}
		return objectType.substring(0, 1).toUpperCase() + objectType.substring(1).toLowerCase();
	}
}
