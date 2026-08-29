package mapgen.core;

/** Шаг генерации одного блока. Всё, что нужно знать о соседях, берётся из World по мировым координатам. */
public interface Generator {
    String name();
    void generate(World world, Chunk chunk);
}
