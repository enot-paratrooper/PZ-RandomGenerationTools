package mapgen.colors;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Читает XML-описание цветов (colorsMap.txt / colorsMap_veg.txt). */
public final class ColorMapLoader {
    private ColorMapLoader() {}

    public static List<ColorRule> load(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file)) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            NodeList nodes = doc.getElementsByTagName("rule");
            List<ColorRule> rules = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                rules.add(new ColorRule(
                        e.getAttribute("label"),
                        Integer.parseInt(e.getAttribute("bitmapIndex")),
                        ColorRule.parseRgb(e.getAttribute("color")),
                        Arrays.asList(e.getAttribute("tileChoices").trim().split("\\s+")),
                        e.getAttribute("targetLayer"),
                        ColorRule.parseRgb(e.getAttribute("condition"))));
            }
            return rules;
        }
    }
}
