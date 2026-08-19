package commonFunc;

import static tools.StringTools.splitString;

import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import containers.CommonData;
import randGroups.RandomGroup;
import randRoomGen.NonrandomElement;
import randRoomGen.RandomCollection;
import randRoomGen.ObjectTiles.GlobalParameter;
import randRoomGen.ObjectTiles.ObjectTileFactory;

/**
 * [AI] ИЗМЕНЁННЫЙ ФАЙЛ.
 *
 * Что изменено (всё помечено метками [AI]):
 *  1. loadRandColl(data, element) и loadRandColl(data, element, x, y) больше не
 *     дублируют друг друга — обе делегируют в общую реализацию. Раньше версия
 *     со смещением НЕ добавляла GlobalParameter при пустом списке object,
 *     из-за чего RandomCollection.GetTypeOfObject() падал с
 *     IndexOutOfBoundsException для коллекций вида
 *     <RandomCollection num="4" name="..."/> внутри групп/зданий.
 *  2. То же самое сделано для loadNonRandE.
 *  3. Добавлен loadRandGroup(..., offsetX, offsetY) — без него группы внутри
 *     комнаты, размещённой в здании, ставились бы в координатах комнаты,
 *     а не в координатах здания.
 */
public class LoadFunc {

	public static void loadRandColl(CommonData data, Element mainElement) {
		// [AI] делегирование в общую реализацию с нулевым смещением
		loadRandColl(data, mainElement, 0, 0);
	}

	public static void loadRandColl(CommonData data, Element mainElement, int x, int y) {
		NodeList RandomCollectionNodes = mainElement.getElementsByTagName("RandomCollection");
		for (int i = 0; i < RandomCollectionNodes.getLength(); i++) {
			Element RandomCollectionE = (Element) RandomCollectionNodes.item(i);
			String RandomCollectionName = RandomCollectionE.getAttribute("name");
			if (RandomCollectionName.isEmpty()) {
				throw new IllegalArgumentException("Неверное имя коллекции - " + RandomCollectionE.getAttribute("num"));
			}
			RandomCollection newRandomCollection = new RandomCollection(data.random);
			newRandomCollection.numberOfCollection = Integer.parseInt(RandomCollectionE.getAttribute("num"));
			newRandomCollection.loadRandomCollection(splitString(RandomCollectionName));
			NodeList ObjectTiles = RandomCollectionE.getElementsByTagName("object");
			// [AI] перенесено из версии без смещения: коллекция без <object>
			// является «чистым» источником тайлов для link-переменных
			if (ObjectTiles.getLength() == 0) {
				newRandomCollection.AddFurniture(new GlobalParameter());
			}
			for (int j = 0; j < ObjectTiles.getLength(); j++) {
				Element FurnitureE = (Element) ObjectTiles.item(j);
				int randCollNum = Integer.parseInt(RandomCollectionE.getAttribute("num"));
				newRandomCollection.AddFurniture(ObjectTileFactory.createReflective(FurnitureE, randCollNum));
			}
			newRandomCollection.setOffsetX(x);
			newRandomCollection.setOffsetY(y);
			data.RandomCollections.add(newRandomCollection);
		}
	}

	public static void loadNonRandE(CommonData data, Element mainElement) {
		// [AI] делегирование в общую реализацию с нулевым смещением
		loadNonRandE(data, mainElement, 0, 0);
	}

	public static void loadNonRandE(CommonData data, Element mainElement, int x, int y) {
		NodeList NonrandomElementsNodes = mainElement.getElementsByTagName("NonrandomElement");
		for (int i = 0; i < NonrandomElementsNodes.getLength(); i++) {
			Element NonrandomElementE = (Element) NonrandomElementsNodes.item(i);
			NonrandomElement newNonrandomElement = new NonrandomElement();
			int num = Integer.parseInt(NonrandomElementE.getAttribute("num"));
			newNonrandomElement.loadNonrandomElement(NonrandomElementE, num, data.linker);
			newNonrandomElement.setOffsetX(x);
			newNonrandomElement.setOffsetY(y);
			data.NonrandomElements.add(newNonrandomElement);
		}
	}

	public static void loadRandGroup(CommonData data, Element roomElement, List<RandomGroup> RandomGroups) {
		// [AI] делегирование в общую реализацию с нулевым смещением
		loadRandGroup(data, roomElement, RandomGroups, 0, 0);
	}

	/**
	 * [AI] НОВАЯ ПЕРЕГРУЗКА.
	 * offsetX/offsetY — абсолютное смещение владельца группы (комнаты в здании
	 * либо самого здания). Координаты внутри <object x=".." y=".."/> группы
	 * по-прежнему могут быть диапазонами, смещение прибавляется уже после
	 * розыгрыша случайной позиции.
	 */
	public static void loadRandGroup(CommonData data, Element roomElement, List<RandomGroup> RandomGroups,
			int offsetX, int offsetY) {
		NodeList randomGroupsNodes = roomElement.getElementsByTagName("RandomGroup");
		for (int i = 0; i < randomGroupsNodes.getLength(); i++) {
			Element randomGroupElement = (Element) randomGroupsNodes.item(i);
			String name = randomGroupElement.getAttribute("name");
			NodeList groopObjectsNodes = randomGroupElement.getElementsByTagName("object");
			for (int j = 0; j < groopObjectsNodes.getLength(); j++) {
				RandomGroup newRandomGroup = new RandomGroup(data);
				Element groopObjectsElement = (Element) groopObjectsNodes.item(j);
				String x = groopObjectsElement.getAttribute("x");
				String y = groopObjectsElement.getAttribute("y");
				newRandomGroup.loadRandomGroups(name, x, y, offsetX, offsetY); // [AI] +смещение
				RandomGroups.add(newRandomGroup);
			}
		}
	}
}
