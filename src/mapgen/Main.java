package mapgen;

/**
 * AI: java -cp out mapgen.Main &lt;outDir&gt; &lt;seed&gt; &lt;cx0&gt; &lt;cy0&gt; &lt;cx1&gt; &lt;cy1&gt;
 *      [threads] [colorsMap.txt colorsMap_veg.txt] [template=&lt;файл.tmx&gt;] [debug]
 *
 * <p>Точка входа и ничего больше: сами фазы живут в {@link MapGenApp}, чтобы порядок вызовов
 * читался как оглавление, а не тонул в разборе аргументов и таймерах.
 *
 * <p>Результат: outDir/&lt;префикс&gt;_&lt;cx&gt;_&lt;cy&gt;.tmx и world.state; с флагом
 * {@code debug} дополнительно chunks/*.bmp, map.bmp, map_veg.bmp, debug_rivers.bmp, debug_towns.bmp.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        MapGenApp.Options options = MapGenApp.parseArgs(args);
        if (options == null) {
            MapGenApp.printUsage();
            return;
        }
        try (MapGenApp app = new MapGenApp(options)) {
            app.loadState();
            app.buildWorld();
            app.prepareKnownRivers();
            app.traceRivers();
            app.freezeWater();
            app.rasterizeChunks();
            app.exportDebugImages();
            app.printSummary();
        }
    }
}
