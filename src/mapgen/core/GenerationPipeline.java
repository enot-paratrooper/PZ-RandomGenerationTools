package mapgen.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Последовательность шагов для одного блока. Потокобезопасен для чтения: список шагов
 * фиксируется до запуска генерации, состояния во время run() нет.
 *
 * <p>Тайминги не печатаются на месте, а возвращаются одной строкой: иначе вывод нескольких
 * воркеров перемешался бы построчно.
 */
public final class GenerationPipeline {
    private final List<Generator> steps = new ArrayList<>();

    public GenerationPipeline add(Generator g) { steps.add(g); return this; }

    /** @return строка отчёта по блоку */
    public String run(GenContext ctx, Chunk chunk) {
        StringBuilder sb = new StringBuilder(96);
        long t0 = System.nanoTime();
        ctx.beginChunk(chunk);
        sb.append(String.format("chunk %4d,%-4d  fields %3d", chunk.cx, chunk.cy,
                (System.nanoTime() - t0) / 1_000_000));
        for (Generator g : steps) {
            long t = System.nanoTime();
            g.generate(ctx, chunk);
            sb.append(String.format("  %s %3d", g.name(), (System.nanoTime() - t) / 1_000_000));
        }
        sb.append(String.format("  = %d ms", (System.nanoTime() - t0) / 1_000_000));
        return sb.toString();
    }
}
