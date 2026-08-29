package mapgen.core;

import mapgen.rivers.River;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Сохраняемое состояние мира: seed, сгенерированные блоки, протрассированные регионы и реки.
 *
 * <p><b>Инвариант многопоточности:</b> объект изменяет только координирующий поток.
 * Воркеры растеризации его не касаются — они получают заранее замороженную водную маску.
 *
 * <p>Формат v2 (первая строка {@code version 2}):
 * <pre>
 * river  mx my lakeR n   x y w parent ...     — детальная форма (дерево известно)
 * riverv mx my lakeR m   x0 y0 x1 y1 w ...    — сжатая форма, не более River.MAX_VECTORS отрезков
 * </pre>
 * Файлы v1 читаются, но в них не было структуры дерева, поэтому такие реки нельзя сжать
 * (см. {@link River#hasTree()}); при первом же пересохранении они уходят в v2 без дерева.
 */
public final class WorldState {
    public static final int FORMAT_VERSION = 2;

    public long seed;
    public final Set<Long> generatedChunks = new HashSet<>();
    public final Set<Long> tracedRegions = new HashSet<>();
    public final List<River> rivers = new ArrayList<>();

    public static long key(int x, int y) { return ((long) x << 32) | (y & 0xFFFFFFFFL); }
    public static int keyX(long k) { return (int) (k >> 32); }
    public static int keyY(long k) { return (int) k; }

    /** Пишет во временный файл и переименовывает — прерывание не оставит битого состояния. */
    public void save(Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (BufferedWriter w = Files.newBufferedWriter(tmp)) {
            w.write("version " + FORMAT_VERSION); w.newLine();
            w.write("seed " + seed); w.newLine();
            for (long k : generatedChunks) { w.write("chunk " + keyX(k) + " " + keyY(k)); w.newLine(); }
            for (long k : tracedRegions)   { w.write("region " + keyX(k) + " " + keyY(k)); w.newLine(); }
            StringBuilder sb = new StringBuilder(1 << 16);
            for (River r : rivers) {
                sb.setLength(0);
                if (r.simplified()) {
                    int[] v = r.vectorsRaw();
                    sb.append("riverv ").append(r.mouthX()).append(' ').append(r.mouthY()).append(' ')
                      .append(r.lakeRadius()).append(' ').append(v.length / 5);
                    for (int x : v) sb.append(' ').append(x);
                } else {
                    int[] nd = r.nodesRaw();
                    int[] par = r.parentRaw();
                    int n = nd.length / 3;
                    sb.append("river ").append(r.mouthX()).append(' ').append(r.mouthY()).append(' ')
                      .append(r.lakeRadius()).append(' ').append(n);
                    for (int i = 0; i < n; i++)
                        sb.append(' ').append(nd[i * 3]).append(' ').append(nd[i * 3 + 1])
                          .append(' ').append(nd[i * 3 + 2]).append(' ').append(par == null ? -1 : par[i]);
                }
                w.write(sb.toString());
                w.newLine();
            }
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public static WorldState load(Path file) throws IOException {
        WorldState s = new WorldState();
        int version = 1;
        for (String line : Files.readAllLines(file)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] t = trimmed.split("\\s+");
            switch (t[0]) {
                case "version" -> version = Integer.parseInt(t[1]);
                case "seed"    -> s.seed = Long.parseLong(t[1]);
                case "chunk"   -> s.generatedChunks.add(key(Integer.parseInt(t[1]), Integer.parseInt(t[2])));
                case "region"  -> s.tracedRegions.add(key(Integer.parseInt(t[1]), Integer.parseInt(t[2])));
                case "river"   -> s.rivers.add(readDetailed(t, version));
                case "riverv"  -> s.rivers.add(readVectors(t));
                default -> {}
            }
        }
        return s;
    }

    private static River readDetailed(String[] t, int version) {
        int mx = Integer.parseInt(t[1]), my = Integer.parseInt(t[2]);
        int lake = Integer.parseInt(t[3]), n = Integer.parseInt(t[4]);
        int[] nodes = new int[n * 3];
        int[] parent = version >= 2 ? new int[n] : null;
        int per = version >= 2 ? 4 : 3;
        for (int i = 0; i < n; i++) {
            int b = 5 + i * per;
            nodes[i * 3]     = Integer.parseInt(t[b]);
            nodes[i * 3 + 1] = Integer.parseInt(t[b + 1]);
            nodes[i * 3 + 2] = Integer.parseInt(t[b + 2]);
            if (parent != null) parent[i] = Integer.parseInt(t[b + 3]);
        }
        if (parent != null && n > 0 && parent[0] < 0 && allNegative(parent)) parent = null;
        return River.detailed(mx, my, lake, nodes, parent);
    }

    private static boolean allNegative(int[] a) {
        for (int v : a) if (v >= 0) return false;
        return true;
    }

    private static River readVectors(String[] t) {
        int mx = Integer.parseInt(t[1]), my = Integer.parseInt(t[2]);
        int lake = Integer.parseInt(t[3]), m = Integer.parseInt(t[4]);
        int[] v = new int[m * 5];
        for (int i = 0; i < v.length; i++) v[i] = Integer.parseInt(t[5 + i]);
        return River.vectorized(mx, my, lake, v);
    }
}
