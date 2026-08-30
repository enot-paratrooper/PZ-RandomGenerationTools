package mapgen.towns;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Кэш построенных городов. Постройка города — сотни кварталов и тысячи участков, повторять
 * её на каждый блок незачем, но и класть в {@link mapgen.core.World} нельзя: там не должно
 * быть изменяемого состояния. Поэтому индекс живёт по одному на поток —
 * в {@link mapgen.core.GenContext} и в отладочном экспортёре.
 *
 * <p>Отсутствие города тоже кэшируется (значение null), иначе пустые ячейки пересчитывались бы
 * каждый раз вместе со всеми 50 попытками размещения.
 *
 * <p>Блок карты касается не более четырёх ячеек, но вынесенная за черту инфраструктура может
 * выступать за след города, поэтому диапазон опрашиваемых ячеек расширен на
 * {@link TownField#OUTSIDE_REACH}.
 */
public final class TownIndex {
    public static final int MAX_CACHED_TOWNS = 16;

    private final TownField field;
    private final LinkedHashMap<Long, Town> cache =
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Long, Town> eldest) {
                    return size() > MAX_CACHED_TOWNS;
                }
            };

    public TownIndex(TownField field) { this.field = field; }

    public Town at(int cellX, int cellY) {
        long k = ((long) cellX << 32) | (cellY & 0xFFFFFFFFL);
        if (cache.containsKey(k)) return cache.get(k);
        Town t = field.town(cellX, cellY);
        cache.put(k, t);
        return t;
    }

    /** Город, в след которого попадает точка, или null. */
    public Town townAt(int wx, int wy) {
        Town t = at(TownField.cellOf(wx), TownField.cellOf(wy));
        return t != null && t.contains(wx, wy) ? t : null;
    }

    /** Города, чьи габариты (вместе с вынесенными объектами) пересекают прямоугольник. */
    public List<Town> intersecting(int x0, int y0, int x1, int y1) {
        int r = TownField.OUTSIDE_REACH;
        List<Town> out = new ArrayList<>(4);
        for (int cy = TownField.cellOf(y0 - r); cy <= TownField.cellOf(y1 + r); cy++)
            for (int cx = TownField.cellOf(x0 - r); cx <= TownField.cellOf(x1 + r); cx++) {
                Town t = at(cx, cy);
                if (t != null && t.intersects(x0, y0, x1, y1)) out.add(t);
            }
        return out;
    }
}
