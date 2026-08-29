package mapgen.colors;

import java.util.List;

/** Одна строка <rule .../> из colorsMap / colorsMap_veg. */
public record ColorRule(String label, int bitmapIndex, int color, List<String> tileChoices,
                        String targetLayer, int condition) {

    /** "90 100 35" -> 0x5A6423 */
    public static int parseRgb(String s) {
        String[] p = s.trim().split("\\s+");
        return (Integer.parseInt(p[0]) << 16) | (Integer.parseInt(p[1]) << 8) | Integer.parseInt(p[2]);
    }

    public static String rgbToString(int rgb) {
        return ((rgb >> 16) & 255) + " " + ((rgb >> 8) & 255) + " " + (rgb & 255);
    }
}
