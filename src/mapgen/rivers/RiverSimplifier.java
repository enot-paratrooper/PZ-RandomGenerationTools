package mapgen.rivers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Сжимает дерево реки в набор отрезков (не более {@link River#MAX_VECTORS}).
 *
 * <p>Зачем: детальная река — до 15000 узлов по 4 int (x, y, width, parent) = ~240 КБ.
 * Реки, чьи габариты дальше {@code 2 * MAX_BRANCH_LENGTH + запас} от генерируемой области,
 * в этом прогоне не попадут ни в один блок — их геометрия нужна только для того, чтобы
 * новые реки не проросли сквозь них, а для этого хватает грубого силуэта.
 *
 * <p>Алгоритм. Дерево разбирается на ветви: первая — самая длинная от устья (ствол), дальше
 * следующие по глубине листья до ближайшего уже занятого узла. Каждая ветвь стартует одним
 * отрезком «начало—конец», после чего оставшийся бюджет раздаётся жадно: на каждом шаге
 * делится тот отрезок, у которого максимально отклонение реального русла от хорды
 * (Ramer–Douglas–Peucker с общим бюджетом вместо порога). Так вершины садятся на изгибы, а не
 * равномерно по индексу — равномерная нарезка на меандрах срезала бы петли.
 *
 * <p>Ширина отрезка — средняя по покрытым узлам: максимум раздувал бы всё русло до ширины
 * ствола, минимум рвал бы связность.
 */
public final class RiverSimplifier {
    private RiverSimplifier() {}

    public static River simplify(River r, int maxVectors) {
        if (r.simplified() || !r.hasTree()) return r;
        int n = r.nodeCount();
        if (n < 2) return r;

        List<int[]> branches = decompose(r, n);
        if (branches.isEmpty()) return r;

        long total = 0;
        for (int[] b : branches) total += b.length - 1;
        if (total <= maxVectors) return r;                     // уже проще целевой формы

        // ветвей может быть больше бюджета — оставляем самые длинные (список уже отсортирован)
        if (branches.size() > maxVectors) branches = new ArrayList<>(branches.subList(0, maxVectors));

        // стартовое разбиение: по одному отрезку на ветвь
        List<List<Integer>> cuts = new ArrayList<>(branches.size());
        PriorityQueue<Split> heap = new PriorityQueue<>(
                Comparator.<Split>comparingDouble(s -> -s.dev())
                        .thenComparingInt(Split::branch).thenComparingInt(Split::from));
        for (int bi = 0; bi < branches.size(); bi++) {
            List<Integer> c = new ArrayList<>();
            c.add(0);
            c.add(branches.get(bi).length - 1);
            cuts.add(c);
            offer(heap, r, branches.get(bi), bi, 0, branches.get(bi).length - 1);
        }

        int budget = maxVectors - branches.size();
        while (budget > 0 && !heap.isEmpty()) {
            Split s = heap.poll();
            List<Integer> c = cuts.get(s.branch());
            int pos = c.indexOf(s.from());
            if (pos < 0 || pos + 1 >= c.size() || c.get(pos + 1) != s.to()) continue;  // отрезок уже поделён
            c.add(pos + 1, s.at());
            offer(heap, r, branches.get(s.branch()), s.branch(), s.from(), s.at());
            offer(heap, r, branches.get(s.branch()), s.branch(), s.at(), s.to());
            budget--;
        }

        List<int[]> out = new ArrayList<>();
        for (int bi = 0; bi < branches.size(); bi++) {
            int[] b = branches.get(bi);
            List<Integer> c = cuts.get(bi);
            for (int i = 0; i + 1 < c.size(); i++) {
                int a = c.get(i), z = c.get(i + 1);
                long sum = 0;
                for (int k = a; k <= z; k++) sum += r.nodeWidth(b[k]);
                int w = Math.max(1, (int) Math.round(sum / (double) (z - a + 1)));
                out.add(new int[]{r.nodeX(b[a]), r.nodeY(b[a]), r.nodeX(b[z]), r.nodeY(b[z]), w});
            }
        }

        int[] vectors = new int[out.size() * 5];
        for (int i = 0; i < out.size(); i++) System.arraycopy(out.get(i), 0, vectors, i * 5, 5);
        return River.vectorized(r.mouthX(), r.mouthY(), r.lakeRadius(), vectors);
    }

    private record Split(int branch, int from, int to, int at, double dev) {}

    /** Кладёт в очередь точку максимального отклонения русла от хорды [from, to]. */
    private static void offer(PriorityQueue<Split> heap, River r, int[] b, int bi, int from, int to) {
        if (to - from < 2) return;
        double x0 = r.nodeX(b[from]), y0 = r.nodeY(b[from]);
        double dx = r.nodeX(b[to]) - x0, dy = r.nodeY(b[to]) - y0;
        double len = Math.hypot(dx, dy);
        int at = -1;
        double best = -1;
        for (int i = from + 1; i < to; i++) {
            double px = r.nodeX(b[i]) - x0, py = r.nodeY(b[i]) - y0;
            double d = len < 1e-9 ? Math.hypot(px, py) : Math.abs(px * dy - py * dx) / len;
            if (d > best) { best = d; at = i; }
        }
        if (at > from) heap.add(new Split(bi, from, to, at, best));
    }

    /** Разбор дерева на ветви: индексы узлов от развилки (или устья) к листу, длинные первыми. */
    private static List<int[]> decompose(River r, int n) {
        int[] parent = r.parentRaw();
        int[] childCount = new int[n];
        for (int i = 0; i < n; i++) if (parent[i] >= 0) childCount[parent[i]]++;

        int[] depth = new int[n];
        for (int i = 0; i < n; i++) depth[i] = parent[i] >= 0 ? depth[parent[i]] + 1 : 0;

        List<Integer> leaves = new ArrayList<>();
        for (int i = 0; i < n; i++) if (childCount[i] == 0) leaves.add(i);
        // самый глубокий лист первым -> первая ветвь и есть ствол; при равенстве — по индексу
        leaves.sort(Comparator.<Integer>comparingInt(i -> -depth[i]).thenComparingInt(i -> i));

        boolean[] used = new boolean[n];
        List<int[]> branches = new ArrayList<>();
        for (int leaf : leaves) {
            if (used[leaf]) continue;
            List<Integer> path = new ArrayList<>();
            int cur = leaf;
            while (cur >= 0) {
                path.add(cur);
                boolean stop = used[cur];
                used[cur] = true;
                if (stop) break;                       // дошли до уже занятого узла — это развилка
                cur = parent[cur];
            }
            if (path.size() < 2) continue;
            int[] b = new int[path.size()];
            for (int i = 0; i < b.length; i++) b[i] = path.get(b.length - 1 - i);  // от развилки к листу
            branches.add(b);
        }
        branches.sort(Comparator.comparingInt(a -> -a.length));
        return branches;
    }
}
