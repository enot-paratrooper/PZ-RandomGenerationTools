package mapgen.core;

import mapgen.rivers.River;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Сохраняемое состояние мира: seed, сгенерированные блоки, протрассированные регионы рек и сами реки.
 * Простой текстовый формат — чтобы продолжать генерацию позже без внешних библиотек.
 */
public final class WorldState {
    public long seed;
    public final Set<Long> generatedChunks = new HashSet<>();
    public final Set<Long> tracedRegions = new HashSet<>();
    public final List<River> rivers = new ArrayList<>();

    public static long key(int x, int y) { return ((long) x << 32) | (y & 0xFFFFFFFFL); }
    public static int keyX(long k) { return (int) (k >> 32); }
    public static int keyY(long k) { return (int) k; }

    public void save(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("seed ").append(seed).append('\n');
        for (long k : generatedChunks) sb.append("chunk ").append(keyX(k)).append(' ').append(keyY(k)).append('\n');
        for (long k : tracedRegions)   sb.append("region ").append(keyX(k)).append(' ').append(keyY(k)).append('\n');
        for (River r : rivers) {
            sb.append("river ").append(r.sourceX()).append(' ').append(r.sourceY()).append(' ')
              .append(r.lakeRadius()).append(' ').append(r.path().size());
            for (int[] p : r.path()) sb.append(' ').append(p[0]).append(' ').append(p[1]).append(' ').append(p[2]);
            sb.append('\n');
        }
        Files.writeString(file, sb.toString());
    }

    public static WorldState load(Path file) throws IOException {
        WorldState s = new WorldState();
        for (String line : Files.readAllLines(file)) {
            String[] t = line.trim().split("\\s+");
            if (t[0].isEmpty()) continue;
            switch (t[0]) {
                case "seed"   -> s.seed = Long.parseLong(t[1]);
                case "chunk"  -> s.generatedChunks.add(key(Integer.parseInt(t[1]), Integer.parseInt(t[2])));
                case "region" -> s.tracedRegions.add(key(Integer.parseInt(t[1]), Integer.parseInt(t[2])));
                case "river"  -> {
                    int n = Integer.parseInt(t[4]);
                    List<int[]> path = new ArrayList<>(n);
                    for (int i = 0; i < n; i++)
                        path.add(new int[]{Integer.parseInt(t[5 + i * 3]), Integer.parseInt(t[6 + i * 3]), Integer.parseInt(t[7 + i * 3])});
                    s.rivers.add(new River(Integer.parseInt(t[1]), Integer.parseInt(t[2]), path, Integer.parseInt(t[3])));
                }
                default -> {}
            }
        }
        return s;
    }
}
