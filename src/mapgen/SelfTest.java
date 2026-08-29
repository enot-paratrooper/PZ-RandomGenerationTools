package mapgen;

import mapgen.core.WorldState;
import mapgen.rivers.River;
import mapgen.rivers.RiverSimplifier;
import mapgen.rivers.WaterMask;

import java.nio.file.Path;
import java.util.List;

/**
 * Проверки, которые дешевле держать в коде, чем перепроверять руками:
 * сколько памяти экономит сжатие и насколько силуэт из 30 отрезков совпадает
 * с полной геометрией реки (IoU по водной маске в габаритах реки).
 *
 * java -cp out mapgen.SelfTest &lt;world.state&gt;
 */
public final class SelfTest {
    public static void main(String[] args) throws Exception {
        WorldState state = WorldState.load(Path.of(args[0]));
        int budget = args.length > 1 ? Integer.parseInt(args[1]) : River.MAX_VECTORS;
        List<River> rivers = state.rivers;
        System.out.println("рек: " + rivers.size());

        long detailedBytes = 0, vectorBytes = 0;
        double iouSum = 0;
        int checked = 0, biggestNodes = 0;
        River biggest = null;

        for (River r : rivers) {
            if (r.simplified() || !r.hasTree() || r.nodeCount() < 200) continue;
            River s = RiverSimplifier.simplify(r, budget);
            if (!s.simplified()) continue;
            detailedBytes += r.approxBytes();
            vectorBytes += s.approxBytes();
            if (checked < 12) { iouSum += iou(r, s); checked++; }   // IoU дорогой, берём выборку
            if (r.nodeCount() > biggestNodes) { biggestNodes = r.nodeCount(); biggest = r; }
        }

        System.out.printf("сжимаемых рек: детально %d КБ -> векторно %d КБ (в %.1f раза)%n",
                detailedBytes >> 10, vectorBytes >> 10, detailedBytes / (double) Math.max(1, vectorBytes));
        System.out.printf("IoU силуэта по %d рекам: %.3f%n", checked, iouSum / Math.max(1, checked));
        if (biggest != null) {
            River s = RiverSimplifier.simplify(biggest, budget);
            System.out.printf("самая крупная: %d узлов -> %d векторов, IoU %.3f%n",
                    biggest.nodeCount(), s.vectorCount(), iou(biggest, s));
        }
    }

    /** Доля совпадения водных масок двух форм одной реки в её габаритах. */
    private static double iou(River a, River b) {
        WaterMask ma = new WaterMask(), mb = new WaterMask();
        ma.add(a); mb.add(b);
        ma.freeze(); mb.freeze();
        WaterMask.View va = ma.view(), vb = mb.view();
        long inter = 0, union = 0;
        int step = 1 + (a.maxX() - a.minX()) / 600;      // прореживаем, чтобы тест не считался минуту
        for (int y = a.minY(); y <= a.maxY(); y += step)
            for (int x = a.minX(); x <= a.maxX(); x += step) {
                boolean wa = va.isWater(x, y), wb = vb.isWater(x, y);
                if (wa && wb) inter++;
                if (wa || wb) union++;
            }
        return union == 0 ? 1 : inter / (double) union;
    }
}
