package mapgen.rivers;

/**
 * Речная система. Хранится в одной из двух форм.
 *
 * <p><b>Детальная</b> — {@code nodes} = тройки {x, y, width} по одной на клетку русла,
 * {@code parent} = индекс родителя в дереве (у устья -1). Именно её строит {@link RiverTracer}.
 *
 * <p><b>Векторная</b> — {@code vectors} = пятёрки {x0, y0, x1, y1, width}, не более
 * {@link #MAX_VECTORS} штук. В неё сжимаются реки, удалённые от генерируемой области:
 * их геометрия всё равно не попадёт ни в один блок, а держать в памяти 15000 узлов ради
 * этого незачем. См. {@link RiverSimplifier}.
 *
 * <p>Обе формы растеризуются одинаково — как объединение дисков (у вектора диски идут
 * вдоль отрезка), поэтому {@link WaterMask} не различает их на уровне маски.
 *
 * <p>Массивы плоские, а не List&lt;int[]&gt;: на 15000 узлов список даёт ~600 КБ накладных
 * расходов на объекты против 180 КБ полезных данных.
 */
public final class River {
    /** Потолок на число отрезков в сжатой форме. */
    public static final int MAX_VECTORS = 30;

    private final int mouthX, mouthY, lakeRadius;
    private final int[] nodes;     // {x, y, width} * n, либо null
    private final int[] parent;    // n элементов; null у форм без структуры дерева
    private final int[] vectors;   // {x0, y0, x1, y1, width} * m, либо null
    private final int minX, minY, maxX, maxY;

    private River(int mouthX, int mouthY, int lakeRadius, int[] nodes, int[] parent, int[] vectors) {
        this.mouthX = mouthX;
        this.mouthY = mouthY;
        this.lakeRadius = lakeRadius;
        this.nodes = nodes;
        this.parent = parent;
        this.vectors = vectors;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        if (nodes != null) {
            for (int i = 0; i < nodes.length; i += 3) {
                int r = nodes[i + 2];
                x0 = Math.min(x0, nodes[i] - r);     y0 = Math.min(y0, nodes[i + 1] - r);
                x1 = Math.max(x1, nodes[i] + r);     y1 = Math.max(y1, nodes[i + 1] + r);
            }
        } else {
            for (int i = 0; i < vectors.length; i += 5) {
                int r = (vectors[i + 4] - 1) >> 1;      // в пятёрке лежит ширина, как и у узла
                x0 = Math.min(x0, Math.min(vectors[i], vectors[i + 2]) - r);
                y0 = Math.min(y0, Math.min(vectors[i + 1], vectors[i + 3]) - r);
                x1 = Math.max(x1, Math.max(vectors[i], vectors[i + 2]) + r);
                y1 = Math.max(y1, Math.max(vectors[i + 1], vectors[i + 3]) + r);
            }
        }
        this.minX = x0; this.minY = y0; this.maxX = x1; this.maxY = y1;
    }

    public static River detailed(int mouthX, int mouthY, int lakeRadius, int[] nodes, int[] parent) {
        return new River(mouthX, mouthY, lakeRadius, nodes, parent, null);
    }

    public static River vectorized(int mouthX, int mouthY, int lakeRadius, int[] vectors) {
        return new River(mouthX, mouthY, lakeRadius, null, null, vectors);
    }

    public int mouthX() { return mouthX; }
    public int mouthY() { return mouthY; }
    public int lakeRadius() { return lakeRadius; }

    public boolean simplified()   { return vectors != null; }
    /** Дерево известно — реку можно сжать. Файлы формата v1 приходят без него. */
    public boolean hasTree()      { return parent != null; }
    public int nodeCount()        { return nodes == null ? 0 : nodes.length / 3; }
    public int vectorCount()      { return vectors == null ? 0 : vectors.length / 5; }
    public int[] nodesRaw()       { return nodes; }
    public int[] parentRaw()      { return parent; }
    public int[] vectorsRaw()     { return vectors; }

    public int nodeX(int i)     { return nodes[i * 3]; }
    public int nodeY(int i)     { return nodes[i * 3 + 1]; }
    public int nodeWidth(int i) { return nodes[i * 3 + 2]; }

    public int minX() { return minX; }
    public int minY() { return minY; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }

    /** Расстояние Чебышёва от габаритов реки до прямоугольника; 0 — пересекаются. */
    public int distanceTo(int rx0, int ry0, int rx1, int ry1) {
        int dx = Math.max(0, Math.max(rx0 - maxX, minX - rx1));
        int dy = Math.max(0, Math.max(ry0 - maxY, minY - ry1));
        return Math.max(dx, dy);
    }

    /** Приблизительный вес в памяти, байт — для отчёта об экономии. */
    public long approxBytes() {
        return 64L + (nodes == null ? 0 : nodes.length * 4L)
                   + (parent == null ? 0 : parent.length * 4L)
                   + (vectors == null ? 0 : vectors.length * 4L);
    }
}
