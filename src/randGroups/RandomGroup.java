package randGroups;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import containers.CommonData;

import static tools.StringTools.parseRanges;
import static tools.ConfigPaths.groupFile;          // [AI]
import static commonFunc.LoadFunc.loadRandColl;
import static commonFunc.LoadFunc.loadNonRandE;

/**
 * [AI] ИЗМЕНЁННЫЙ ФАЙЛ.
 *
 * Что изменено:
 *  1. Добавлена перегрузка loadRandomGroups(name, x, y, offsetX, offsetY):
 *     группа теперь знает абсолютное смещение владельца (комнаты в здании).
 *     Старая сигнатура сохранена и вызывает новую с нулевым смещением.
 *  2. Путь к файлу группы вынесен в tools.ConfigPaths.
 *  3. Убраны неиспользуемые импорты (NodeList, List<Integer> и т.д. частично).
 */
public class RandomGroup {

	String Name;
	private int x;
	private int y;
	private List<Integer> rangeX;
	private List<Integer> rangeY;
	private CommonData data;

	public RandomGroup(CommonData data) {
		this.data = data;
	}

	public void loadRandomGroups(String groupName, String x, String y) {
		// [AI] обратная совместимость
		loadRandomGroups(groupName, x, y, 0, 0);
	}

	/** [AI] НОВАЯ ПЕРЕГРУЗКА со смещением владельца. */
	public void loadRandomGroups(String groupName, String x, String y, int offsetX, int offsetY) {
		Name = "";
		rangeX = parseRanges(x);
		rangeY = parseRanges(y);
		initializeСoord(offsetX, offsetY); // [AI] +смещение
		try {
			String fileName = groupFile(groupName); // [AI] путь из ConfigPaths

			InputStream inputStream = getClass().getResourceAsStream(fileName);
			if (inputStream == null) {
				inputStream = new FileInputStream(fileName);
			}
			data.linker.LoadVariables(fileName, data.RandomCollections.size() - 1);

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(inputStream);
			document.getDocumentElement().normalize();

			Element groupElement = document.getDocumentElement();
			Name = groupElement.getAttribute("name");

			// Загрузка RandomCollection
			loadRandColl(data, groupElement, this.x, this.y);
			// Загрузка NonrandomElements
			loadNonRandE(data, groupElement, this.x, this.y);

			inputStream.close(); // [AI] поток не закрывался
		} catch (Exception e) {
			System.err.println("Ошибка загрузки группы '" + groupName + "': " + e.getMessage());
			e.printStackTrace();
		}
	}

	/** [AI] добавлены параметры смещения. */
	private void initializeСoord(int offsetX, int offsetY) {
		this.x = rangeX.get(data.random.nextInt(rangeX.size())) + offsetX;
		this.y = rangeY.get(data.random.nextInt(rangeY.size())) + offsetY;
	}

	public String getName() {
		return Name;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}
}
