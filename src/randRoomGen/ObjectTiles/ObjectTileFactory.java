package randRoomGen.ObjectTiles;
import java.lang.reflect.Constructor;
import org.w3c.dom.Element;

public class ObjectTileFactory {
	
	 private static final String PACKAGE_NAME = "randRoomGen.ObjectTiles.";
	
	public static ObjectTile createReflective(Element element, int numberCollection) {
		try {
			String objectType = element.getAttribute("type");
			String className = PACKAGE_NAME + 
                    objectType.substring(0, 1).toUpperCase() + 
                    objectType.substring(1).toLowerCase();
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
		}catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found for object type collection: " + numberCollection, e);
        } 
	    catch (Exception e) {
            throw new RuntimeException("Failed to create object collection: " + numberCollection, e);
        }
		
	}

}
