package mapgen.core;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Блок карты 300x300. Пиксель (x, y) внутри блока -> мировой (worldX(x), worldY(y)).
 * Принадлежит одному потоку на всё время генерации (thread-confined), синхронизация не нужна.
 *
 * <p>AI: кроме двух растровых слоёв блок накапливает список лотов — ссылок на .tbx, которые
 * уйдут в {@code <objectgroup name="Lots">} ячейки. Растр описывает землю, лоты — здания;
 * WorldEd читает и то, и другое из одного .tmx.
 */
public final class Chunk {

    /**
     * AI: здание на карте. Координаты и габарит — в тайлах, относительно левого верхнего угла
     * ячейки; в пиксели их переводит {@link mapgen.io.TmxTemplate} при записи, потому что
     * множитель зависит от ориентации карты.
     *
     * <p>{@code path} — путь к .tbx относительно каталога с .tmx, ровно в том виде, в каком он
     * попадёт в атрибут {@code type}.
     *
     * <p>Здание может вылезать за границу ячейки: WorldEd подключает лот как подкарту со
     * смещением и сам разбирается с выходом за край. Владеет зданием та ячейка, в которую
     * попал его левый верхний угол, — так оно записывается ровно один раз.
     */
    public record Lot(String path, int x, int y, int w, int h) { }

    public final int cx, cy, size;
    private final Layer base, vegetation;
    private final BitSet vegetationBlocked;
    /** AI: обычный ArrayList — блок принадлежит одному потоку, как и оба слоя. */
    private final List<Lot> lots = new ArrayList<>();

    public Chunk(int cx, int cy, int size) {
        this.cx = cx;
        this.cy = cy;
        this.size = size;
        this.base = new Layer(size, size, 0x000000);
        this.vegetation = new Layer(size, size, 0x000000);
        this.vegetationBlocked = new BitSet(size * size);
    }

    public Layer base()       { return base; }
    public Layer vegetation() { return vegetation; }

    public int worldX(int x) { return cx * size + x; }
    public int worldY(int y) { return cy * size + y; }

    public void blockVegetation(int x, int y)        { vegetationBlocked.set(y * size + x); }
    public boolean isVegetationBlocked(int x, int y) { return vegetationBlocked.get(y * size + x); }

    /** AI: добавляет здание. Координаты — в тайлах относительно угла ячейки. */
    public void addLot(String path, int x, int y, int w, int h) {
        lots.add(new Lot(path, x, y, w, h));
    }

    /** AI: здания ячейки в порядке добавления. */
    public List<Lot> lots() { return lots; }
}
