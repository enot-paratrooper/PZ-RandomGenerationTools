package mapgen.towns;

import mapgen.core.NoiseField;
import mapgen.core.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Города как чистая функция от (seed, координаты). Ни состояния, ни кэшей, ни
 * зависимости от порядка обхода блоков — поэтому города считаются прямо в фазе
 * растеризации и не требуют отдельной фазы трассировки, как реки. Кэш живёт в
 * {@link TownIndex}, по одному на поток.
 *
 * <p>
 * <b>Сетка.</b> Мир нарезан на ячейки {@link #CELL}, в каждой не более одного
 * города. След джиттерится внутри ячейки так, чтобы целиком в неё влезть —
 * тогда пиксель принадлежит максимум одному городу. Вынесенная за черту
 * инфраструктура тоже удерживается внутри ячейки.
 *
 * <p>
 * <b>Порядок постройки</b> (он же порядок потребления случайных чисел, менять
 * нельзя — иначе все ранее сгенерированные блоки карты перестанут стыковаться с
 * новыми):
 * <ol>
 * <li>жребий на существование, выбор {@link TownTemplate}, размеров и
 * формы;</li>
 * <li>размещение: до {@link #MAX_PLACEMENT_ATTEMPTS} попыток уйти с воды;</li>
 * <li>центры районов и раздача им типов по квотам шаблона;</li>
 * <li>нарезка BSP с сеткой, зависящей от района;</li>
 * <li>отбор кварталов: след, вода, рваная кромка;</li>
 * <li>подгонка долей районов перекраской пограничных кварталов до
 * {@link #SHARE_TOLERANCE};</li>
 * <li>участки;</li>
 * <li>подрезка улиц по выжившим кварталам;</li>
 * <li>инфраструктура: {@link #INFRA_ATTEMPTS} попыток в городе, столько же за
 * чертой, иначе отказ с сообщением в stderr.</li>
 * </ol>
 *
 * <p>
 * Проверка воды идёт по чистым полям {@link World#height}, то есть по природным
 * озёрам и морю. Русла рек {@code World} не знает, поэтому улицы и участки на
 * реке просто не рисуются.
 */
public final class TownField {

	// ---------- сетка и размещение ----------
	/**
	 * Сторона ячейки сетки городов, px. Должна быть больше 2 * maxHalfSize + 2 *
	 * EDGE_GAP.
	 */
	public static final int CELL = 2048;
	/** Доля ячеек, в которых вообще пытаются поставить город. */
	public static final double TOWN_CHANCE = 0.55;
	/** Потолок попыток смещения; исчерпан — город не генерируется. */
	public static final int MAX_PLACEMENT_ATTEMPTS = 50;
	/**
	 * После стольких неудач след ужимается — так город влезает в береговой карман.
	 */
	public static final int SHRINK_AFTER = 25;
	public static final double SHRINK_FACTOR = 0.65;
	/** Допустимая доля непригодных точек следа (вода + скалы). */
	public static final double MAX_WATER_FRACTION = 0.06;
	/** Насколько выше уровня моря начинается сухая земля. */
	public static final double SHORE_MARGIN = 0.004;
	/** Зазор между следом и границей ячейки. */
	public static final int EDGE_GAP = 16;

	// ---------- нарезка ----------
	/**
	 * Потолок глубины BSP. Город 1760 px с кварталами по 45 px требует около десяти
	 * уровней на ось; при 11 рекурсия упиралась в потолок и выбрасывала
	 * недорезанные кварталы в 170 px, которые ломали подгонку долей.
	 */
	public static final int MAX_DEPTH = 16;
	/**
	 * Вероятность остановить деление квартала, уже влезающего в maxBlock своего
	 * района.
	 */
	public static final double STOP_CHANCE = 0.65;
	/**
	 * Потолок стороны квартала как доля короткой стороны поселения. Сетка районов
	 * задана в абсолютных пикселях, но поселения различаются по размеру в шесть
	 * раз: без этого потолка фермерский квартал в 400 px занимал бы половину
	 * деревни, и доли районов невозможно было бы уложить в допуск — один квартал
	 * давал бы 30% площади.
	 */
	public static final int BLOCK_CAP_DIVISOR = 8;
	public static final int MIN_BLOCK_CAP = 40;
	/**
	 * Сетка выборок при оценке площадей районов (REGION_SAMPLES^2 точек на город).
	 */
	public static final int REGION_SAMPLES = 48;

	// ---------- доли районов ----------
	/** Допустимое отклонение фактической доли района от целевой. */
	public static final double SHARE_TOLERANCE = 0.025;
	public static final int SHARE_REPAIR_STEPS = 500;

	// ---------- инфраструктура ----------
	public static final int INFRA_ATTEMPTS = 50;
	/** Максимальное удаление вынесенного за черту здания от кромки города, px. */
	public static final int OUTSIDE_REACH = 200;
	/**
	 * То же для зданий, которым за чертой не место (полиция, супермаркет): жмутся к
	 * городу.
	 */
	public static final int OUTSIDE_REACH_TIGHT = 60;
	public static final int OUTSIDE_MIN_GAP = 20;
	public static final int ACCESS_ROAD_WIDTH = 7;
	/** Отступ здания от края занятого им участка или квартала. */
	public static final int FACILITY_INSET = 3;

	private static final int[] NO_LOTS = new int[0];
	private static final Town.Block[] NO_BLOCKS = new Town.Block[0];
	private static final Town.Facility[] NO_FACILITIES = new Town.Facility[0];
	private static final InfraType[] NO_MISSING = new InfraType[0];
	// ---------- сшивка улиц ----------
	/** AI: соседние прямоугольники BSP расходятся ровно на 1 px — это не разрыв. */
	public static final int TOUCH_EPS = 1;
	/**
	 * AI: на сколько конец улицы дотягивается до перекрёстка. Чуть больше самой
	 * широкой полосы (14 * 1.15), чтобы концы, срезанные на углу квартала, сошлись,
	 * но недостаточно, чтобы дорога уползла в поле.
	 */
	public static final int JUNCTION_REACH = 24;
	/**
	 * AI: потолок проходов сшивки. Рост монотонен и зажат габаритами полосы, обычно
	 * хватает двух.
	 */
	public static final int JUNCTION_PASSES = 6;

	// ---------- размещение объектов ----------
	/** AI: минимальный зазор между вынесенными за черту объектами. */
	public static final int OUTSIDE_CLEARANCE = 16;
	/** AI: минимальный зазор между объектом и чужой дорогой. */
	public static final int ROAD_CLEARANCE = 4;
	/**
	 * AI: сколько ближайших улиц перебирается под подъезд, прежде чем место
	 * бракуется.
	 */
	public static final int ACCESS_ROAD_CANDIDATES = 8;
	/** AI: шаг обхода периметра в проверке «прямоугольник задевает след». */
	public static final int FOOTPRINT_SAMPLE_STEP = 8;

	static {
		int need = 2 * TownTemplate.maxHalfSize() + 2 * EDGE_GAP;
		if (need >= CELL)
			throw new ExceptionInInitializerError("CELL=" + CELL + " мал для шаблонов: нужно > " + need);
	}

	private final World world;
	/**
	 * Шум для рваной кромки: период ~130 px даёт лопасти размером в несколько
	 * кварталов.
	 */
	private final NoiseField edgeField;

	/**
	 * @param world используется только для чтения чистых полей высоты;
	 *              конструируется последним полем {@link World}, когда шумовые поля
	 *              уже готовы.
	 */
	public TownField(World world) {
		this.world = world;
		this.edgeField = new NoiseField(world.seed() + 0x51EDL, 130, 2, 1.0);
	}

	public static int cellOf(int worldCoord) {
		return Math.floorDiv(worldCoord, CELL);
	}

	/**
	 * @return город ячейки или null, если его тут нет (не выпал жребий либо не
	 *         нашлось суши).
	 */
	public Town town(int cellX, int cellY) {
		Random rnd = new Random(
				world.seed() ^ (cellX * 0x9E3779B97F4A7C15L) ^ (cellY * 0xC2B2AE3D27D4EB4FL) ^ 0x544F574E5F5632L); // соль
																													// "TOWN_V2"
		if (rnd.nextDouble() > TOWN_CHANCE)
			return null;

		// ---------- 1. шаблон и размеры ----------
		TownTemplate tpl = TownTemplate.pick(rnd);
		Footprint shape = tpl.footprint;
		int longHalf = tpl.minRadius + rnd.nextInt(tpl.maxRadius - tpl.minRadius + 1);
		double aspect = tpl.minAspect + rnd.nextDouble() * (tpl.maxAspect - tpl.minAspect);
		int shortHalf = Math.max(60, (int) Math.round(longHalf / aspect));
		boolean wide = rnd.nextBoolean();
		int baseW = wide ? longHalf : shortHalf, baseH = wide ? shortHalf : longHalf;
		int ox = cellX * CELL, oy = cellY * CELL;

		// ---------- 2. размещение: до 50 попыток уйти с воды ----------
		int cx = 0, cy = 0, hw = 0, hh = 0, attempts = 0;
		boolean placed = false;
		for (int attempt = 1; attempt <= MAX_PLACEMENT_ATTEMPTS; attempt++) {
			double k = attempt <= SHRINK_AFTER ? 1.0 : SHRINK_FACTOR;
			int w = (int) Math.round(baseW * k), h = (int) Math.round(baseH * k);
			int m = Math.max(w, h);
			int lo = m + EDGE_GAP, hi = CELL - m - EDGE_GAP;
			int x = ox + lo + rnd.nextInt(hi - lo + 1);
			int y = oy + lo + rnd.nextInt(hi - lo + 1);
			attempts = attempt;
			if (siteFits(shape, x, y, w, h)) {
				cx = x;
				cy = y;
				hw = w;
				hh = h;
				placed = true;
				break;
			}
		}
		if (!placed)
			return null;

		// ---------- 3. центры районов и раздача типов по квотам ----------
		int k = Math.max(tpl.districts.length, tpl.minSeeds + rnd.nextInt(tpl.maxSeeds - tpl.minSeeds + 1));
		int[] seeds = new int[k * 3];
		int jitter = Math.max(1, Math.min(hw, hh) / 6);
		seeds[0] = cx + rnd.nextInt(2 * jitter + 1) - jitter;
		seeds[1] = cy + rnd.nextInt(2 * jitter + 1) - jitter;
		for (int i = 1; i < k; i++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			double u = 0.25 + 0.70 * Math.sqrt(rnd.nextDouble());
			double[] p = shape.ray(cx, cy, hw, hh, a, u);
			seeds[i * 3] = (int) Math.round(p[0]);
			seeds[i * 3 + 1] = (int) Math.round(p[1]);
		}
		assignTypes(tpl, regionAreas(shape, cx, cy, hw, hh, seeds), seeds);

		// ---------- 4. нарезка с сеткой по району ----------
		List<int[]> rawStreets = new ArrayList<>();
		List<int[]> rawBlocks = new ArrayList<>();
		int blockCap = Math.max(MIN_BLOCK_CAP, 2 * Math.min(hw, hh) / BLOCK_CAP_DIVISOR);
		carve(cx - hw, cy - hh, cx + hw, cy + hh, 0, rnd, seeds, tpl.streetScale, blockCap, rawStreets, rawBlocks);

		// ---------- 5. отбор кварталов: след, вода, рваная кромка ----------
		List<BB> kept = new ArrayList<>(rawBlocks.size());
		for (int[] b : rawBlocks) {
			int bx = (b[0] + b[2]) >> 1, by = (b[1] + b[3]) >> 1;
			double t = shape.norm(cx, cy, hw, hh, bx, by);
			if (t > 1)
				continue;
			double edge = (tpl.ragged <= 0 || t <= 1 - tpl.ragged) ? 0 : (t - (1 - tpl.ragged)) / tpl.ragged;
			if (edge > 0 && edgeField.at(bx, by) < 0.25 + 0.5 * edge)
				continue; // кромка размыта
			if (isWet(bx, by))
				continue; // квартал в воде
			int r1 = Town.nearestIndex(seeds, bx, by), r2 = Town.secondNearestIndex(seeds, bx, by);
			kept.add(new BB(b[0], b[1], b[2], b[3], r2, edge, DistrictType.byOrdinal(seeds[r1 * 3 + 2])));
		}
		if (kept.isEmpty())
			return null;

		// ---------- 6. подгонка долей районов ----------
		seedMissingTypes(kept, tpl, seeds);
		repairShares(kept, tpl, seeds);

		// ---------- 7. участки ----------
		for (BB b : kept) {
			b.lots = lots(b, rnd, 1.0 - 0.45 * b.edge);
			b.lotUsed = new boolean[b.lotCount()];
		}

		// ---------- 8. подрезка улиц по кварталам и сшивка перекрёстков ----------
		List<int[]> streets = trimStreets(rawStreets, kept);
		int cityStreets = streets.size(); // AI: всё, что добавится дальше, — подъезды

		// ---------- 9. инфраструктура ----------
		List<InfraType> missing = new ArrayList<>();
		List<Town.Facility> facilities = placeInfra(tpl, kept, streets, cityStreets, shape, cx, cy, hw, hh, cellX,
				cellY, rnd, missing);

		Town.Block[] blocks = new Town.Block[kept.size()];
		for (int i = 0; i < blocks.length; i++) {
			BB b = kept.get(i);
			// AI: участки под инфраструктурой и перерезанные подъездом в экспорт не идут,
			// иначе рядовая застройка встанет поверх объекта или поперёк дороги
			blocks[i] = new Town.Block(b.x0, b.y0, b.x1, b.y1, b.type, exportLots(b, streets, cityStreets));
		}
		return new Town(cellX, cellY, cx, cy, hw, hh, shape, tpl, attempts, seeds, flatten(streets), blocks,
				facilities.isEmpty() ? NO_FACILITIES : facilities.toArray(NO_FACILITIES),
				missing.isEmpty() ? NO_MISSING : missing.toArray(NO_MISSING));
	}

	// ------------------------------------------------------------------
	// пригодность места

	private boolean isWet(int x, int y) {
		return world.height(x, y) < world.seaLevel() + SHORE_MARGIN;
	}

	private boolean isRock(int x, int y) {
		return world.height(x, y) > world.rockLevel();
	}

	private boolean bad(int x, int y) {
		return isWet(x, y) || isRock(x, y);
	}

	/** Центр должен быть сухим, а по следу доля воды/скал — не выше порога. */
	private boolean siteFits(Footprint shape, int cx, int cy, int hw, int hh) {
		if (bad(cx, cy))
			return false;
		int badCount = 0, total = 0;
		for (int ring = 1; ring <= 4; ring++) {
			double u = ring / 4.0;
			for (int i = 0; i < 12; i++) {
				double[] p = shape.ray(cx, cy, hw, hh, i * Math.PI / 6, u);
				total++;
				if (bad((int) Math.round(p[0]), (int) Math.round(p[1])))
					badCount++;
			}
		}
		return badCount <= total * MAX_WATER_FRACTION;
	}

	/** Прямоугольник целиком на суше: углы, центр и середины сторон. */
	private boolean dryRect(int x0, int y0, int x1, int y1) {
		int mx = (x0 + x1) >> 1, my = (y0 + y1) >> 1;
		return !(bad(x0, y0) || bad(x1, y0) || bad(x0, y1) || bad(x1, y1) || bad(mx, my) || bad(mx, y0) || bad(mx, y1)
				|| bad(x0, my) || bad(x1, my));
	}

	// ------------------------------------------------------------------ районы

	/** Оценка площади каждого региона Вороного выборкой по сетке внутри следа. */
	private static double[] regionAreas(Footprint shape, int cx, int cy, int hw, int hh, int[] seeds) {
		double[] area = new double[seeds.length / 3];
		int n = REGION_SAMPLES;
		for (int j = 0; j < n; j++) {
			int y = cy - hh + 2 * hh * j / (n - 1);
			for (int i = 0; i < n; i++) {
				int x = cx - hw + 2 * hw * i / (n - 1);
				if (!shape.contains(cx, cy, hw, hh, x, y))
					continue;
				area[Town.nearestIndex(seeds, x, y)]++;
			}
		}
		return area;
	}

	/**
	 * Раздаёт типы районов центрам Вороного под целевые доли шаблона: центр города
	 * получает DOWNTOWN, затем каждому типу достаётся по одному центру начиная с
	 * самых крупных долей (иначе тип с малой долей мог бы не появиться вовсе),
	 * остальные центры уходят типу с наибольшим недобором. Точность этого шага
	 * грубая — её добирает {@link #repairShares}.
	 */
	private static void assignTypes(TownTemplate tpl, double[] area, int[] seeds) {
		int k = area.length;
		double total = 0;
		for (double a : area)
			total += a;
		double[] remaining = new double[tpl.districts.length];
		for (int i = 0; i < remaining.length; i++)
			remaining[i] = tpl.shares[i] * total;

		int[] bigFirst = orderDesc(area);
		boolean[] used = new boolean[k];
		int[] assigned = new int[k];
		Arrays.fill(assigned, -1);

		int downtown = indexOf(tpl.districts, DistrictType.DOWNTOWN);
		if (downtown >= 0) {
			assigned[0] = downtown;
			used[0] = true;
			remaining[downtown] -= area[0];
		}
		for (int t : orderDesc(tpl.shares)) {
			if (t == downtown)
				continue;
			int pick = -1;
			for (int idx : bigFirst)
				if (!used[idx]) {
					pick = idx;
					break;
				}
			if (pick < 0)
				break;
			used[pick] = true;
			assigned[pick] = t;
			remaining[t] -= area[pick];
		}
		for (int idx : bigFirst) {
			if (used[idx])
				continue;
			int best = 0;
			for (int t = 1; t < remaining.length; t++)
				if (remaining[t] > remaining[best])
					best = t;
			assigned[idx] = best;
			used[idx] = true;
			remaining[best] -= area[idx];
		}
		for (int i = 0; i < k; i++)
			seeds[i * 3 + 2] = tpl.districts[assigned[i]].ordinal();
	}

	/**
	 * Добивает доли районов до допуска, перекрашивая <b>граничные</b> кварталы из
	 * избыточного типа в дефицитный. Граница берётся геометрическая (соседство
	 * прямоугольников с зазором на улицу), а не по второму ближайшему центру
	 * Вороного: центров мало, и по ним у половины пар районов общей границы просто
	 * не находится.
	 *
	 * <p>
	 * Шаг применяется, только если уменьшает максимальную ошибку, поэтому цикл не
	 * осциллирует и всегда завершается. Достижимая точность ограничена снизу
	 * площадью самого мелкого граничного квартала — отсюда потолок размера квартала
	 * {@link #BLOCK_CAP_DIVISOR}.
	 *
	 * <p>
	 * Побочный эффект приятный: границы районов перестают быть ровными линиями
	 * Вороного.
	 */
	private static void repairShares(List<BB> kept, TownTemplate tpl, int[] seeds) {
		int n = DistrictType.count();
		long total = 0;
		long[] area = new long[n];
		for (BB b : kept) {
			total += b.area();
			area[b.type.ordinal()] += b.area();
		}
		if (total == 0)
			return;

		int[][] adj = adjacency(kept);
		double[] err = new double[n];
		for (int t = 0; t < n; t++)
			err[t] = area[t] / (double) total - tpl.share(DistrictType.byOrdinal(t));

		for (int step = 0; step < SHARE_REPAIR_STEPS; step++) {
			double worst = 0;
			for (int t = 0; t < n; t++)
				worst = Math.max(worst, Math.abs(err[t]));
			if (worst <= SHARE_TOLERANCE)
				return;

			// лучший ход: перекрасить граничный квартал из профицитного типа в дефицитный,
			// максимально уменьшив сумму модулей ошибок. Перебираем все пары, а не только
			// крайние типы: у самого профицитного района может не быть общей границы
			// с самым дефицитным, а у следующего за ним — быть.
			int pick = -1, target = -1;
			double bestGain = -1e-12;
			for (int i = 0; i < kept.size(); i++) {
				BB b = kept.get(i);
				int from = b.type.ordinal();
				if (err[from] <= 0)
					continue; // тип не в профиците
				double a = b.area() / (double) total;
				for (int j : adj[i]) {
					int to = kept.get(j).type.ordinal();
					if (to == from || err[to] >= 0)
						continue; // сосед не в дефиците
					// не перелетаем цель: ход не должен загонять ни один из двух типов
					// в ошибку хуже прежней (кроме мелочи в пределах допуска)
					if (Math.abs(err[from] - a) > Math.max(Math.abs(err[from]), SHARE_TOLERANCE))
						continue;
					if (Math.abs(err[to] + a) > Math.max(Math.abs(err[to]), SHARE_TOLERANCE))
						continue;
					double gain = (Math.abs(err[from]) - Math.abs(err[from] - a))
							+ (Math.abs(err[to]) - Math.abs(err[to] + a));
					if (gain > bestGain) {
						bestGain = gain;
						pick = i;
						target = to;
					}
				}
			}
			if (pick < 0) {
				// смежного хода нет: район-дефицит может лежать в другом конце города.
				// Тогда переносим ближайший к его центру квартал профицитного типа —
				// получается анклав (отдельный завод у жилого массива), это правдоподобно.
				int worstType = -1;
				double worstErr = -SHARE_TOLERANCE;
				for (int t = 0; t < n; t++)
					if (err[t] < worstErr) {
						worstErr = err[t];
						worstType = t;
					}
				if (worstType < 0)
					return;
				int sx = Integer.MIN_VALUE, sy = 0;
				for (int j = 0; j < seeds.length; j += 3)
					if (seeds[j + 2] == worstType) {
						sx = seeds[j];
						sy = seeds[j + 1];
						break;
					}
				if (sx == Integer.MIN_VALUE)
					return;
				long bd = Long.MAX_VALUE;
				for (int i = 0; i < kept.size(); i++) {
					BB b = kept.get(i);
					int from = b.type.ordinal();
					if (from == worstType || err[from] <= 0)
						continue;
					double a = b.area() / (double) total;
					if (Math.abs(err[from] - a) > Math.max(Math.abs(err[from]), SHARE_TOLERANCE))
						continue;
					if (Math.abs(err[worstType] + a) > Math.max(Math.abs(err[worstType]), SHARE_TOLERANCE))
						continue;
					long dx = ((b.x0 + b.x1) >> 1) - sx, dy = ((b.y0 + b.y1) >> 1) - sy;
					long dd = dx * dx + dy * dy;
					if (dd < bd) {
						bd = dd;
						pick = i;
						target = worstType;
					}
				}
				if (pick < 0)
					return; // улучшающих ходов не осталось
			}

			BB b = kept.get(pick);
			double a = b.area() / (double) total;
			err[b.type.ordinal()] -= a;
			err[target] += a;
			b.type = DistrictType.byOrdinal(target);
		}
	}

	/**
	 * Если район шаблона не получил ни одного квартала (его регион целиком съела
	 * вода или рваная кромка), перекраска его не воскресит: она умеет только
	 * расширять существующие районы через границу. Поэтому такому типу выдаётся
	 * один квартал — ближайший к его центру Вороного, дальше он растёт обычным
	 * порядком.
	 */
	private static void seedMissingTypes(List<BB> kept, TownTemplate tpl, int[] seeds) {
		int n = DistrictType.count();
		for (DistrictType d : tpl.districts) {
			long total = 0;
			long[] area = new long[n];
			int[] count = new int[n];
			for (BB b : kept) {
				total += b.area();
				area[b.type.ordinal()] += b.area();
				count[b.type.ordinal()]++;
			}
			if (count[d.ordinal()] > 0 || total == 0)
				continue;

			int sx = Integer.MIN_VALUE, sy = 0;
			for (int j = 0; j < seeds.length; j += 3)
				if (seeds[j + 2] == d.ordinal()) {
					sx = seeds[j];
					sy = seeds[j + 1];
					break;
				}
			if (sx == Integer.MIN_VALUE)
				continue;

			// донор выбирается по трём убывающим предпочтениям, иначе посев одного района
			// обнулял бы другой, у которого квартал был единственным
			BB best = null;
			int bestRank = -1;
			long bd = Long.MAX_VALUE;
			for (BB b : kept) {
				int t = b.type.ordinal();
				boolean surplus = area[t] / (double) total - tpl.share(DistrictType.byOrdinal(t)) > 0;
				int rank = surplus && count[t] > 1 ? 2 : count[t] > 1 ? 1 : 0;
				long dx = ((b.x0 + b.x1) >> 1) - sx, dy = ((b.y0 + b.y1) >> 1) - sy;
				long dd = dx * dx + dy * dy;
				if (rank > bestRank || (rank == bestRank && dd < bd)) {
					bestRank = rank;
					bd = dd;
					best = b;
				}
			}
			if (best != null)
				best.type = d;
		}
	}

	/**
	 * Соседи каждого квартала: прямоугольники, расходящиеся не более чем на улицу.
	 */
	private static int[][] adjacency(List<BB> kept) {
		int n = kept.size();
		int gap = 20;
		int[] deg = new int[n];
		int[][] tmp = new int[n][8];
		for (int i = 0; i < n; i++) {
			BB a = kept.get(i);
			for (int j = i + 1; j < n; j++) {
				BB b = kept.get(j);
				if (a.x0 > b.x1 + gap || b.x0 > a.x1 + gap)
					continue;
				if (a.y0 > b.y1 + gap || b.y0 > a.y1 + gap)
					continue;
				if (deg[i] == tmp[i].length)
					tmp[i] = Arrays.copyOf(tmp[i], deg[i] * 2);
				if (deg[j] == tmp[j].length)
					tmp[j] = Arrays.copyOf(tmp[j], deg[j] * 2);
				tmp[i][deg[i]++] = j;
				tmp[j][deg[j]++] = i;
			}
		}
		int[][] out = new int[n][];
		for (int i = 0; i < n; i++)
			out[i] = Arrays.copyOf(tmp[i], deg[i]);
		return out;
	}

	// ------------------------------------------------------------------ нарезка

	/**
	 * Базовая ширина улицы по глубине разреза: магистраль -> проспект -> проезд.
	 */
	private static int baseStreetWidth(int depth) {
		return depth <= 1 ? 14 : depth <= 3 ? 10 : 7;
	}

	/**
	 * Рекурсивно режет прямоугольник вдоль длинной стороны, откладывая полосу
	 * улицы. Сетка берётся у района, которому принадлежит центр прямоугольника:
	 * жилой мелкий, промышленный и фермерский крупные. Порядок рекурсии фиксирован,
	 * поэтому поток случайных чисел детерминирован.
	 */
	private static void carve(int x0, int y0, int x1, int y1, int depth, Random rnd, int[] seeds, double streetScale,
			int blockCap, List<int[]> streets, List<int[]> blocks) {
		DistrictType d = DistrictType
				.byOrdinal(seeds[Town.nearestIndex(seeds, (x0 + x1) >> 1, (y0 + y1) >> 1) * 3 + 2]);
		int maxB = Math.min(d.maxBlock, blockCap);
		int minB = Math.min(d.minBlock, maxB * 3 / 5);
		int w = x1 - x0 + 1, h = y1 - y0 + 1;
		int sw = Math.max(4, (int) Math.round(baseStreetWidth(depth) * d.streetScale * streetScale));
		boolean canX = w >= 2 * minB + sw;
		boolean canY = h >= 2 * minB + sw;
		boolean small = w <= maxB && h <= maxB;
		if (depth >= MAX_DEPTH || (!canX && !canY) || (small && rnd.nextDouble() < STOP_CHANCE)) {
			blocks.add(new int[] { x0, y0, x1, y1 });
			return;
		}
		boolean vertical = canX && (!canY || w >= h);
		if (vertical) {
			int span = w - 2 * minB - sw;
			int cut = x0 + minB + (span > 0 ? rnd.nextInt(span + 1) : 0);
			streets.add(new int[] { cut, y0, cut + sw - 1, y1 });
			carve(x0, y0, cut - 1, y1, depth + 1, rnd, seeds, streetScale, blockCap, streets, blocks);
			carve(cut + sw, y0, x1, y1, depth + 1, rnd, seeds, streetScale, blockCap, streets, blocks);
		} else {
			int span = h - 2 * minB - sw;
			int cut = y0 + minB + (span > 0 ? rnd.nextInt(span + 1) : 0);
			streets.add(new int[] { x0, cut, x1, cut + sw - 1 });
			carve(x0, y0, x1, cut - 1, depth + 1, rnd, seeds, streetScale, blockCap, streets, blocks);
			carve(x0, cut + sw, x1, y1, depth + 1, rnd, seeds, streetScale, blockCap, streets, blocks);
		}
	}

	/**
	 * Обрезает каждую улицу до промежутка, вдоль которого остались кварталы, и
	 * сшивает перекрёстки. Улица, к которой не примыкает ни один выживший квартал,
	 * выбрасывается.
	 *
	 * <p>
	 * AI: раньше на обрезке всё и заканчивалось, и в этом была ошибка. Границы
	 * берутся по углам крайних кварталов, а перпендикулярная полоса лежит за этим
	 * углом. Внутри города пересечение перекрывает следующий квартал, но у рваной
	 * кромки его нет — улица обрывалась на углу, и последний перекрёсток оставался
	 * неасфальтированным.
	 *
	 * <p>
	 * Сшивка: если две перпендикулярные выжившие полосы расходятся не более чем на
	 * {@link #JUNCTION_REACH} по обеим осям, каждая дотягивается до полосы другой.
	 * Рост монотонный и зажат исходными габаритами из BSP, порядок обхода
	 * фиксирован — проходы сходятся, результат детерминирован.
	 */
	private static List<int[]> trimStreets(List<int[]> raw, List<BB> kept) {
		int n = raw.size();
		boolean[] vertical = new boolean[n];
		boolean[] alive = new boolean[n];
		int[] lo = new int[n], hi = new int[n];

		for (int i = 0; i < n; i++) {
			int[] s = raw.get(i);
			boolean v = (s[2] - s[0]) < (s[3] - s[1]);
			vertical[i] = v;
			int a = Integer.MAX_VALUE, b = Integer.MIN_VALUE;
			for (BB bb : kept) {
				if (v) {
					if (bb.x1 != s[0] - TOUCH_EPS && bb.x0 != s[2] + TOUCH_EPS)
						continue;
					if (bb.y1 < s[1] || bb.y0 > s[3])
						continue;
					a = Math.min(a, Math.max(s[1], bb.y0));
					b = Math.max(b, Math.min(s[3], bb.y1));
				} else {
					if (bb.y1 != s[1] - TOUCH_EPS && bb.y0 != s[3] + TOUCH_EPS)
						continue;
					if (bb.x1 < s[0] || bb.x0 > s[2])
						continue;
					a = Math.min(a, Math.max(s[0], bb.x0));
					b = Math.max(b, Math.min(s[2], bb.x1));
				}
			}
			alive[i] = a <= b;
			lo[i] = a;
			hi[i] = b;
		}

		// AI: сшивка перекрёстков. Мёртвые полосы якорями не служат — иначе дорога
		// потянулась бы к улице, которую сама же обрезка и выбросила.
		for (int pass = 0; pass < JUNCTION_PASSES; pass++) {
			boolean changed = false;
			for (int i = 0; i < n; i++) {
				if (!alive[i])
					continue;
				int[] si = raw.get(i);
				int ib0 = vertical[i] ? si[0] : si[1]; // полоса i поперёк её оси
				int ib1 = vertical[i] ? si[2] : si[3];
				int il0 = vertical[i] ? si[1] : si[0]; // исходные габариты вдоль оси
				int il1 = vertical[i] ? si[3] : si[2];
				for (int j = i + 1; j < n; j++) {
					if (!alive[j] || vertical[j] == vertical[i])
						continue;
					int[] sj = raw.get(j);
					int jb0 = vertical[j] ? sj[0] : sj[1];
					int jb1 = vertical[j] ? sj[2] : sj[3];
					int jl0 = vertical[j] ? sj[1] : sj[0];
					int jl1 = vertical[j] ? sj[3] : sj[2];

					// перекрёсток «почти есть»: концы разошлись не дальше допуска по обеим осям
					if (jb0 > hi[i] + JUNCTION_REACH || jb1 < lo[i] - JUNCTION_REACH)
						continue;
					if (ib0 > hi[j] + JUNCTION_REACH || ib1 < lo[j] - JUNCTION_REACH)
						continue;

					int ni0 = Math.max(il0, Math.min(lo[i], jb0));
					int ni1 = Math.min(il1, Math.max(hi[i], jb1));
					int nj0 = Math.max(jl0, Math.min(lo[j], ib0));
					int nj1 = Math.min(jl1, Math.max(hi[j], ib1));
					if (ni0 != lo[i] || ni1 != hi[i] || nj0 != lo[j] || nj1 != hi[j])
						changed = true;
					lo[i] = ni0;
					hi[i] = ni1;
					lo[j] = nj0;
					hi[j] = nj1;
				}
			}
			if (!changed)
				break;
		}

		List<int[]> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			if (!alive[i])
				continue;
			int[] s = raw.get(i);
			out.add(vertical[i] ? new int[] { s[0], lo[i], s[2], hi[i] } : new int[] { lo[i], s[1], hi[i], s[3] });
		}
		return out;
	}

	/**
	 * Участки внутри квартала: сетка со стороной около {@code lotSide} района, с
	 * отступом от улицы и зазором между соседями. Доля застроенных участков падает
	 * к кромке города через {@code coverageScale}, чтобы окраина редела, а не
	 * обрывалась.
	 */
	private static int[] lots(BB b, Random rnd, double coverageScale) {
		DistrictType type = b.type;
		if (!type.buildable())
			return NO_LOTS;
		int margin = 3, gap = 1;
		int ix0 = b.x0 + margin, iy0 = b.y0 + margin;
		int w = b.x1 - margin - ix0 + 1, h = b.y1 - margin - iy0 + 1;
		if (w < 12 || h < 12)
			return NO_LOTS;

		int cols = Math.max(1, (int) Math.round(w / (double) type.lotSide));
		int rows = Math.max(1, (int) Math.round(h / (double) type.lotSide));
		double coverage = type.coverage * coverageScale;
		int[] out = new int[cols * rows * 4];
		int n = 0;
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cols; c++) {
				int lx0 = ix0 + w * c / cols, ly0 = iy0 + h * r / rows;
				int lx1 = ix0 + w * (c + 1) / cols - 1 - gap;
				int ly1 = iy0 + h * (r + 1) / rows - 1 - gap;
				if (lx1 - lx0 < 5 || ly1 - ly0 < 5)
					continue;
				if (rnd.nextDouble() > coverage)
					continue;
				out[n++] = lx0;
				out[n++] = ly0;
				out[n++] = lx1;
				out[n++] = ly1;
			}
		}
		return n == out.length ? out : Arrays.copyOf(out, n);
	}

	// ------------------------------------------------------------------
	// инфраструктура

	/**
	 * Обязательные здания шаблона. На каждое — {@link #INFRA_ATTEMPTS} попыток
	 * внутри города (первая половина попыток требует профильного района, вторая
	 * берёт любой), затем столько же за чертой, не дальше {@link #OUTSIDE_REACH} px
	 * от кромки, с подъездной дорогой до ближайшей улицы. Если и это не вышло —
	 * здание не размещается, а отказ уходит в stderr и в
	 * {@link Town#missingInfra()}.
	 */
	private List<Town.Facility> placeInfra(TownTemplate tpl, List<BB> kept, List<int[]> streets, int cityStreets,
			Footprint shape, int cx, int cy, int hw, int hh, int cellX, int cellY, Random rnd,
			List<InfraType> missing) {
		List<Town.Facility> out = new ArrayList<>();
		for (int i = 0; i < tpl.infra.length; i++) {
			InfraType type = tpl.infra[i];
			// AI: тюрьма, торговый центр и кладбище отключены — постройки в пуле .tbx нет,
			// закрывать отказ нечем. Раньше на них уходило 50 попыток в городе и 50 за чертой,
			// а результат всё равно оседал в stderr и Town.missingInfra().
			if (!type.available) continue;
			for (int copy = 0; copy < tpl.infraCount[i]; copy++) {
				Town.Facility f = placeInside(type, kept, streets, cityStreets, rnd);
				if (f == null)
					f = placeOutside(type, shape, cx, cy, hw, hh, cellX, cellY, streets, out, rnd);
				if (f == null) {
					missing.add(type);
					System.err.printf(
							"ГОРОД %s (%d,%d): не удалось разместить «%s» — " + "%d попыток в городе и %d за чертой%n",
							tpl.label, cx, cy, type.label, INFRA_ATTEMPTS, INFRA_ATTEMPTS);
				} else {
					out.add(f);
				}
			}
		}
		return out;
	}

	/**
	 * Пытается занять свободный участок подходящего размера, иначе целый квартал.
	 *
	 * <p>
	 * AI: добавлена проверка на подъездные дороги. Объект, вынесенный за черту
	 * раньше, мог протянуть подъезд через этот квартал, и здание встало бы поперёк
	 * полотна.
	 */
	private static Town.Facility placeInside(InfraType type, List<BB> kept, List<int[]> streets, int cityStreets,
			Random rnd) {
		int w = type.minW, h = type.minH;
		for (int attempt = 1; attempt <= INFRA_ATTEMPTS; attempt++) {
			BB b = kept.get(rnd.nextInt(kept.size()));
			if (b.taken)
				continue;
			if (attempt <= INFRA_ATTEMPTS / 2 && b.type != type.preferred)
				continue;

			int lotCount = b.lotCount();
			if (lotCount > 0) {
				int start = rnd.nextInt(lotCount);
				for (int j = 0; j < lotCount; j++) {
					int li = (start + j) % lotCount;
					if (b.lotUsed[li])
						continue;
					int lx0 = b.lots[li * 4], ly0 = b.lots[li * 4 + 1];
					int lx1 = b.lots[li * 4 + 2], ly1 = b.lots[li * 4 + 3];
					if (lx1 - lx0 + 1 < w || ly1 - ly0 + 1 < h)
						continue;
					if (hitsRoad(streets, cityStreets, lx0, ly0, lx1, ly1, ROAD_CLEARANCE))
						continue; // AI
					b.lotUsed[li] = true;
					b.anyLotUsed = true;
					return new Town.Facility(type, lx0, ly0, lx1, ly1, false);
				}
			}
            // участок не подошёл — отдаём под здание весь квартал, если он ещё цел
			if (!b.anyLotUsed && b.w() >= w + 2 * FACILITY_INSET && b.h() >= h + 2 * FACILITY_INSET) {
				int x0 = b.x0 + FACILITY_INSET, y0 = b.y0 + FACILITY_INSET;
				int x1 = b.x1 - FACILITY_INSET, y1 = b.y1 - FACILITY_INSET;
				if (hitsRoad(streets, cityStreets, x0, y0, x1, y1, ROAD_CLEARANCE))
					continue; // AI
				b.taken = true;
				b.lots = NO_LOTS;
				b.lotUsed = new boolean[0];
				return new Town.Facility(type, x0, y0, x1, y1, false);
			}
		}
		return null;
	}

	/**
	 * Выносит здание за черту города и прокладывает к нему подъездную дорогу.
	 *
	 * <p>
	 * AI: место теперь обязано пройти четыре проверки сверх прежних трёх — не
	 * наложиться на уже поставленный объект (зазор {@link #OUTSIDE_CLEARANCE}), не
	 * встать ни на одну дорогу, включая подъезды к другим объектам
	 * ({@link #ROAD_CLEARANCE}), и допустить прокладку собственного подъезда, ни
	 * одно колено которого не режет чужие здания. Отбраковка идёт через
	 * {@code continue} без обращений к {@code rnd}, поэтому на попытку по-прежнему
	 * приходится ровно два случайных числа.
	 */
	private Town.Facility placeOutside(InfraType type, Footprint shape, int cx, int cy, int hw, int hh, int cellX,
			int cellY, List<int[]> streets, List<Town.Facility> placed, Random rnd) {
		int reach = type.outdoorOk ? OUTSIDE_REACH : OUTSIDE_REACH_TIGHT;
		int w = type.minW + 2 * FACILITY_INSET, h = type.minH + 2 * FACILITY_INSET;
		int cx0 = cellX * CELL, cy0 = cellY * CELL;

		for (int attempt = 1; attempt <= INFRA_ATTEMPTS; attempt++) {
			double a = rnd.nextDouble() * Math.PI * 2;
			int dist = OUTSIDE_MIN_GAP + rnd.nextInt(reach - OUTSIDE_MIN_GAP + 1);
			double[] edge = shape.ray(cx, cy, hw, hh, a, 1.0);
			int fx = (int) Math.round(edge[0] + dist * Math.cos(a));
			int fy = (int) Math.round(edge[1] + dist * Math.sin(a));
			int x0 = fx - w / 2, y0 = fy - h / 2, x1 = x0 + w - 1, y1 = y0 + h - 1;

			if (x0 < cx0 || y0 < cy0 || x1 >= cx0 + CELL || y1 >= cy0 + CELL)
				continue;
			if (touchesFootprint(shape, cx, cy, hw, hh, x0, y0, x1, y1))
				continue;
			if (!dryRect(x0, y0, x1, y1))
				continue;
			if (hitsFacility(placed, x0, y0, x1, y1, OUTSIDE_CLEARANCE))
				continue; // AI
			if (hitsRoad(streets, 0, x0, y0, x1, y1, ROAD_CLEARANCE))
				continue; // AI
			if (!addAccessRoad(streets, placed, x0, y0, x1, y1))
				continue; // AI
			return new Town.Facility(type, x0, y0, x1, y1, true);
		}
		return null;
	}

	/**
	 * Прямоугольник задевает след города.
	 *
	 * <p>
	 * AI: раньше проверялись девять точек, крупному объекту этого не хватало —
	 * кромка следа проходила между выборками. Теперь обходится весь периметр с
	 * шагом {@link #FOOTPRINT_SAMPLE_STEP}: для выпуклого следа этого достаточно,
	 * потому что след заведомо не помещается внутрь прямоугольника целиком.
	 */
	private static boolean touchesFootprint(Footprint shape, int cx, int cy, int hw, int hh, int x0, int y0, int x1,
			int y1) {
		for (int x = x0;; x = Math.min(x1, x + FOOTPRINT_SAMPLE_STEP)) {
			if (shape.contains(cx, cy, hw, hh, x, y0))
				return true;
			if (shape.contains(cx, cy, hw, hh, x, y1))
				return true;
			if (x == x1)
				break;
		}
		for (int y = y0;; y = Math.min(y1, y + FOOTPRINT_SAMPLE_STEP)) {
			if (shape.contains(cx, cy, hw, hh, x0, y))
				return true;
			if (shape.contains(cx, cy, hw, hh, x1, y))
				return true;
			if (y == y1)
				break;
		}
		return shape.contains(cx, cy, hw, hh, (x0 + x1) >> 1, (y0 + y1) >> 1);
	}

	/**
	 * Г-образная подъездная дорога от стены здания до оси улицы.
	 *
	 * <p>
	 * AI: изменены две вещи. Первая — дорога начинается от края здания, а не от его
	 * центра, поэтому объект больше не стоит на собственном подъезде и инвариант
	 * «ни один объект не лежит на дороге» стал проверяемым
	 * ({@link Town#overlapProblem()}). Вторая — перебираются
	 * {@link #ACCESS_ROAD_CANDIDATES} ближайших улиц и оба порядка колен; берётся
	 * первый маршрут, не задевающий уже поставленные здания. Если маршрута нет,
	 * дорога не строится и место под здание бракуется целиком.
	 */
	private static boolean addAccessRoad(List<int[]> streets, List<Town.Facility> placed, int bx0, int by0, int bx1,
			int by1) {
		int n = streets.size();
		if (n == 0)
			return false;
		int fx = (bx0 + bx1) >> 1, fy = (by0 + by1) >> 1;
		boolean[] tried = new boolean[n];
		for (int c = 0; c < ACCESS_ROAD_CANDIDATES; c++) {
			int nearest = -1;
			long best = Long.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				if (tried[i])
					continue;
				int[] s = streets.get(i);
				long dx = (long) ((s[0] + s[2]) >> 1) - fx, dy = (long) ((s[1] + s[3]) >> 1) - fy;
				long d = dx * dx + dy * dy;
				if (d < best) {
					best = d;
					nearest = i;
				}
			}
			if (nearest < 0)
				break;
			tried[nearest] = true;
			if (route(streets, placed, streets.get(nearest), bx0, by0, bx1, by1))
				return true;
		}
		return false;
	}

	/**
	 * AI: одно колено подъезда; порядок «сначала X», затем запасной «сначала Y».
	 */
	private static boolean route(List<int[]> streets, List<Town.Facility> placed, int[] street, int bx0, int by0,
			int bx1, int by1) {
		int sx = (street[0] + street[2]) >> 1, sy = (street[1] + street[3]) >> 1;
		int half = ACCESS_ROAD_WIDTH / 2;

		if (sx < bx0 - half || sx > bx1 + half) {
			int ay = axis(sy, by0, by1, half);
			int ex = sx < bx0 ? bx0 - 1 : bx1 + 1;
			int[] h = { Math.min(ex, sx), ay - half, Math.max(ex, sx), ay + half };
			int[] v = { sx - half, Math.min(ay, sy), sx + half, Math.max(ay, sy) };
			if (clearOfFacilities(placed, h) && clearOfFacilities(placed, v)) {
				streets.add(h);
				streets.add(v);
				return true;
			}
		}
		if (sy < by0 - half || sy > by1 + half) {
			int ax = axis(sx, bx0, bx1, half);
			int ey = sy < by0 ? by0 - 1 : by1 + 1;
			int[] v = { ax - half, Math.min(ey, sy), ax + half, Math.max(ey, sy) };
			int[] h = { Math.min(ax, sx), sy - half, Math.max(ax, sx), sy + half };
			if (clearOfFacilities(placed, v) && clearOfFacilities(placed, h)) {
				streets.add(v);
				streets.add(h);
				return true;
			}
		}
		return false;
	}

	/**
	 * AI: ось колена, отодвинутая от края здания на полширины — дорога упирается в
	 * стену.
	 */
	private static int axis(int v, int lo, int hi, int half) {
		int a = lo + half, b = hi - half;
		return a > b ? (lo + hi) >> 1 : Math.max(a, Math.min(b, v));
	}

	// ------------------------------------------------------------------ мелочи

	/**
	 * Квартал на время постройки: тип ещё может смениться, участки ещё не нарезаны.
	 */
	private static final class BB {
		final int x0, y0, x1, y1;
		final int second;
		final double edge; // 0 в глубине города, 1 у самой кромки
		DistrictType type;
		int[] lots = NO_LOTS;
		boolean[] lotUsed = new boolean[0];
		boolean anyLotUsed, taken;

		BB(int x0, int y0, int x1, int y1, int second, double edge, DistrictType type) {
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
			this.second = second;
			this.edge = edge;
			this.type = type;
		}

		int w() {
			return x1 - x0 + 1;
		}

		int h() {
			return y1 - y0 + 1;
		}

		long area() {
			return (long) w() * h();
		}

		int lotCount() {
			return lots.length / 4;
		}
	}

	/**
	 * Индексы по убыванию значения; при равенстве — по возрастанию индекса
	 * (детерминизм).
	 */
	private static int[] orderDesc(double[] v) {
		int n = v.length;
		int[] idx = new int[n];
		for (int i = 0; i < n; i++)
			idx[i] = i;
		for (int i = 1; i < n; i++) { // вставками: n мал (<= 10)
			int cur = idx[i];
			int j = i - 1;
			while (j >= 0 && v[idx[j]] < v[cur]) {
				idx[j + 1] = idx[j];
				j--;
			}
			idx[j + 1] = cur;
		}
		return idx;
	}

	private static int indexOf(DistrictType[] a, DistrictType d) {
		for (int i = 0; i < a.length; i++)
			if (a[i] == d)
				return i;
		return -1;
	}

	private static int[] flatten(List<int[]> list) {
		int[] out = new int[list.size() * 4];
		for (int i = 0; i < list.size(); i++)
			System.arraycopy(list.get(i), 0, out, i * 4, 4);
		return out;
	}

	/** AI: прямоугольники пересекаются, если раздуть первый на margin. */
	private static boolean overlaps(int ax0, int ay0, int ax1, int ay1, int bx0, int by0, int bx1, int by1,
			int margin) {
		return ax0 - margin <= bx1 && bx0 <= ax1 + margin && ay0 - margin <= by1 && by0 <= ay1 + margin;
	}

	/** AI: прямоугольник задевает уже поставленный объект. */
	private static boolean hitsFacility(List<Town.Facility> placed, int x0, int y0, int x1, int y1, int margin) {
		for (Town.Facility f : placed)
			if (overlaps(x0, y0, x1, y1, f.x0(), f.y0(), f.x1(), f.y1(), margin))
				return true;
		return false;
	}

	/**
	 * AI: прямоугольник задевает дорогу, начиная с индекса {@code from}. Для
	 * участков внутри города {@code from = cityStreets}: к городским улицам
	 * кварталы и прижимаются, а вот подъезды к вынесенным объектам режут застройку.
	 */
	private static boolean hitsRoad(List<int[]> streets, int from, int x0, int y0, int x1, int y1, int margin) {
		for (int i = from; i < streets.size(); i++) {
			int[] s = streets.get(i);
			if (overlaps(x0, y0, x1, y1, s[0], s[1], s[2], s[3], margin))
				return true;
		}
		return false;
	}

	/** AI: сегмент дороги не задевает ни одного уже поставленного здания. */
	private static boolean clearOfFacilities(List<Town.Facility> placed, int[] seg) {
		for (Town.Facility f : placed)
			if (overlaps(seg[0], seg[1], seg[2], seg[3], f.x0(), f.y0(), f.x1(), f.y1(), ROAD_CLEARANCE))
				return false;
		return true;
	}

	/**
	 * AI: участки, свободные под рядовую застройку: без занятых инфраструктурой и
	 * без перерезанных подъездом.
	 */
	private static int[] exportLots(BB b, List<int[]> streets, int cityStreets) {
		int n = b.lotCount();
		if (n == 0)
			return NO_LOTS;
		int[] out = new int[n * 4];
		int k = 0;
		for (int i = 0; i < n; i++) {
			if (i < b.lotUsed.length && b.lotUsed[i])
				continue;
			int lx0 = b.lots[i * 4], ly0 = b.lots[i * 4 + 1];
			int lx1 = b.lots[i * 4 + 2], ly1 = b.lots[i * 4 + 3];
			if (hitsRoad(streets, cityStreets, lx0, ly0, lx1, ly1, 0))
				continue;
			out[k++] = lx0;
			out[k++] = ly0;
			out[k++] = lx1;
			out[k++] = ly1;
		}
		return k == 0 ? NO_LOTS : k == out.length ? out : Arrays.copyOf(out, k);
	}
}
