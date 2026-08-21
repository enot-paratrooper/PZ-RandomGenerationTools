package randBuildingGen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * AI: новый класс. Генерация простых крыш по сеткам комнат здания.
 *
 * Синтаксис конфигурации (блок в файле здания):
 *
 * <Roofs basic="PeakNS">
 *   <roof type="FlatTop" floor="3"/>
 *   <roof type="none" floor="2"/>
 *   <roof type="PeakWE" floor="4" x="2" y="2" width="22" height="28"/>
 * </Roofs>
 *
 * basic     - тип крыши, который ставится над всеми блоками комнат здания.
 * <roof>    - переопределение для одного этажа:
 *             type="none" отключает крышу на этаже,
 *             заданные x/y/width/height превращают элемент в ручную крышу
 *             (автоматическая на этом этаже не строится).
 *
 * Алгоритм автоматической генерации:
 *  1) по массиву клеток здания считается верхний занятый этаж каждой клетки;
 *  2) клетки с одинаковым верхним этажом собираются в прямоугольники;
 *  3) над каждым прямоугольником на этаж выше ставится крыша.
 *
 * Так крыша попадает ровно туда, где заканчивается стопка комнат, а разные
 * по высоте части здания получают свои крыши на разных этажах - как в образцах.
 */
public class RoofGenerator {

	public static final String FLAT_TOP = "FlatTop";
	public static final String PEAK_NS = "PeakNS";
	public static final String PEAK_WE = "PeakWE";
	public static final String NONE = "none";

	/** Depth в .tbx задаётся словом, а не числом. */
	private static final String[] DEPTH_NAMES = { "Zero", "One", "Two", "Three" };
	private static final int MAX_DEPTH = 3;

	// =====================================================================
	// План крыш
	// =====================================================================

	/** Разобранный блок <Roofs>. */
	public static class RoofPlan {
		/** Тип по умолчанию для всех этажей. null = автоматических крыш нет. */
		public String basic = null;
		/** Переопределение типа для отдельного этажа. */
		public Map<Integer, String> typeByFloor = new HashMap<Integer, String>();
		/** Крыши, заданные вручную координатами. */
		public List<Roof> manualRoofs = new ArrayList<Roof>();

		public boolean isEmpty() {
			return basic == null && typeByFloor.isEmpty() && manualRoofs.isEmpty();
		}

		/** Тип крыши для этажа с учётом переопределений. */
		public String typeFor(int floor) {
			String type = typeByFloor.get(Integer.valueOf(floor));
			if (type != null) {
				return type;
			}
			return basic;
		}
	}

	/**
	 * Разбор блока <Roofs>.
	 * Если блока нет, возвращается пустой план и крыши не строятся.
	 */
	public static RoofPlan parsePlan(Element buildingElement) {
		RoofPlan plan = new RoofPlan();
		NodeList roofsNodes = buildingElement.getElementsByTagName("Roofs");
		for (int i = 0; i < roofsNodes.getLength(); i++) {
			Element roofsElement = (Element) roofsNodes.item(i);

			String basic = roofsElement.getAttribute("basic").trim();
			if (!basic.isEmpty()) {
				plan.basic = normalizeType(basic);
			}

			NodeList roofNodes = roofsElement.getElementsByTagName("roof");
			for (int j = 0; j < roofNodes.getLength(); j++) {
				Element roofElement = (Element) roofNodes.item(j);

				String type = normalizeType(roofElement.getAttribute("type").trim());
				if (type.isEmpty()) {
					throw new IllegalArgumentException("У элемента roof не задан type");
				}
				String floorValue = roofElement.getAttribute("floor").trim();
				if (floorValue.isEmpty()) {
					throw new IllegalArgumentException("У элемента roof не задан floor");
				}
				int floor = Integer.parseInt(floorValue);

				boolean manual = roofElement.hasAttribute("width") || roofElement.hasAttribute("height");
				if (manual) {
					Roof roof = new Roof();
					roof.roofType = type;
					roof.floor = floor;
					roof.x = intAttribute(roofElement, "x", 0);
					roof.y = intAttribute(roofElement, "y", 0);
					roof.width = intAttribute(roofElement, "width", 0);
					roof.height = intAttribute(roofElement, "height", 0);
					roof.cappedW = boolAttribute(roofElement, "cappedW", true);
					roof.cappedN = boolAttribute(roofElement, "cappedN", true);
					roof.cappedE = boolAttribute(roofElement, "cappedE", true);
					roof.cappedS = boolAttribute(roofElement, "cappedS", true);
					roof.depth = resolveDepth(roof);
					plan.manualRoofs.add(roof);
					// Ручная крыша заменяет автоматическую на своём этаже
					plan.typeByFloor.put(Integer.valueOf(floor), NONE);
				} else {
					plan.typeByFloor.put(Integer.valueOf(floor), type);
				}
			}
		}
		return plan;
	}

	// =====================================================================
	// Генерация
	// =====================================================================

	/**
	 * Построение крыш по массиву клеток здания.
	 *
	 * @param buildingCells [этаж][y][x], 0 = нет комнаты
	 * @param plan          разобранный блок <Roofs>
	 */
	public static List<Roof> generate(int[][][] buildingCells, int width, int height, RoofPlan plan) {
		List<Roof> roofs = new ArrayList<Roof>();
		if (plan == null || plan.isEmpty()) {
			return roofs;
		}

		int floorCount = buildingCells.length;

		// 1) верхний занятый этаж каждой клетки
		int[][] topFloor = new int[height][width];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				topFloor[y][x] = -1;
			}
		}
		for (int floor = 0; floor < floorCount; floor++) {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					if (buildingCells[floor][y][x] != 0) {
						topFloor[y][x] = floor;
					}
				}
			}
		}

		// 2) клетки с одинаковым верхним этажом собираются в прямоугольники
		for (int floor = 0; floor < floorCount; floor++) {
			int roofFloor = floor + 1;
			String type = plan.typeFor(roofFloor);
			if (type == null || NONE.equals(type)) {
				continue;
			}
			boolean[][] mask = new boolean[height][width];
			boolean any = false;
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					if (topFloor[y][x] == floor) {
						mask[y][x] = true;
						any = true;
					}
				}
			}
			if (!any) {
				continue;
			}
			// 3) над каждым прямоугольником - своя крыша
			for (Roof rect : splitIntoRectangles(mask, width, height)) {
				rect.roofType = type;
				rect.floor = roofFloor;
				rect.depth = resolveDepth(rect);
				roofs.add(rect);
			}
		}

		roofs.addAll(plan.manualRoofs);
		return roofs;
	}

	/**
	 * Разбиение множества клеток на прямоугольники жадным проходом:
	 * от первой свободной клетки растём вправо, затем вниз, пока строки целиком
	 * входят в множество. Для прямоугольных комнат из образцов это даёт ровно
	 * один прямоугольник на комнату, для Г-образных - несколько.
	 */
	private static List<Roof> splitIntoRectangles(boolean[][] mask, int width, int height) {
		List<Roof> rectangles = new ArrayList<Roof>();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (!mask[y][x]) {
					continue;
				}
				int w = 0;
				while (x + w < width && mask[y][x + w]) {
					w++;
				}
				int h = 1;
				while (y + h < height && rowCovered(mask, x, y + h, w)) {
					h++;
				}
				for (int cy = y; cy < y + h; cy++) {
					for (int cx = x; cx < x + w; cx++) {
						mask[cy][cx] = false;
					}
				}
				rectangles.add(new Roof(FLAT_TOP, x, y, w, h, 0));
			}
		}
		return rectangles;
	}

	private static boolean rowCovered(boolean[][] mask, int x, int y, int w) {
		for (int cx = x; cx < x + w; cx++) {
			if (!mask[y][cx]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Глубина ската.
	 *
	 * У плоской крыши скатов нет - Depth="Zero".
	 * У PeakNS конёк идёт с севера на юг, скаты смотрят на запад и восток,
	 * поэтому глубина ограничена половиной ширины. У PeakWE - наоборот.
	 * В образцах при размере 22x28 получается Depth="Three" - максимум TileZed.
	 */
	private static String resolveDepth(Roof roof) {
		if (FLAT_TOP.equals(roof.roofType)) {
			return DEPTH_NAMES[0];
		}
		int span;
		if (PEAK_NS.equals(roof.roofType)) {
			span = roof.width;
		} else if (PEAK_WE.equals(roof.roofType)) {
			span = roof.height;
		} else {
			throw new IllegalArgumentException("Неизвестный тип крыши: " + roof.roofType);
		}
		int depth = Math.min(MAX_DEPTH, span / 2);
		if (depth <= 0) {
			System.err.println("Предупреждение: для крыши " + roof.roofType + " размером "
					+ roof.width + "x" + roof.height + " не хватает места на скаты, "
					+ "поставлена плоская крыша");
			roof.roofType = FLAT_TOP;
			return DEPTH_NAMES[0];
		}
		return DEPTH_NAMES[depth];
	}

	// =====================================================================
	// Вспомогательное
	// =====================================================================

	/** Приведение написания типа к тому, что ждёт TileZed. */
	private static String normalizeType(String type) {
		String trimmed = type.trim();
		if (trimmed.isEmpty()) {
			return trimmed;
		}
		if (trimmed.equalsIgnoreCase(FLAT_TOP)) {
			return FLAT_TOP;
		}
		if (trimmed.equalsIgnoreCase(PEAK_NS)) {
			return PEAK_NS;
		}
		if (trimmed.equalsIgnoreCase(PEAK_WE)) {
			return PEAK_WE;
		}
		if (trimmed.equalsIgnoreCase(NONE)) {
			return NONE;
		}
		throw new IllegalArgumentException("Неизвестный тип крыши: '" + trimmed
				+ "'. Поддерживаются FlatTop, PeakNS, PeakWE, none");
	}

	private static int intAttribute(Element element, String name, int defaultValue) {
		if (!element.hasAttribute(name)) {
			return defaultValue;
		}
		String value = element.getAttribute(name).trim();
		if (value.isEmpty()) {
			return defaultValue;
		}
		return Integer.parseInt(value);
	}

	private static boolean boolAttribute(Element element, String name, boolean defaultValue) {
		if (!element.hasAttribute(name)) {
			return defaultValue;
		}
		String value = element.getAttribute(name).trim();
		if (value.isEmpty()) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value);
	}
}
