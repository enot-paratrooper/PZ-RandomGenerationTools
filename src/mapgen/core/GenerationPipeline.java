package mapgen.core;

import java.util.ArrayList;
import java.util.List;

public final class GenerationPipeline {
    private final List<Generator> steps = new ArrayList<>();

    public GenerationPipeline add(Generator g) { steps.add(g); return this; }

    public void run(World world, Chunk chunk) {
        for (Generator g : steps) {
            long t = System.currentTimeMillis();
            g.generate(world, chunk);
            System.out.printf("  [%-12s] chunk %d,%d  %d ms%n", g.name(), chunk.cx, chunk.cy, System.currentTimeMillis() - t);
        }
    }
}
