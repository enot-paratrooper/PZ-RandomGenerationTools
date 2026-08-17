package tools.linker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkUnitParser {
    public static void parseLinkUnits(String filename, List<LinkUnit> units, int offset) {
        Pattern pattern = Pattern.compile(
            "#define\\s+(\\w+)\\s*=\\s*(\\w+)\\.num\\((\\d+)\\);"
        );
        
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher matcher = pattern.matcher(line.trim());
                if (matcher.matches()) {
                    LinkUnit unit = new LinkUnit();
                    unit.Name = matcher.group(1);      // variableTest
                    unit.type = matcher.group(2);      // RandomCollection
                    unit.reference = Integer.parseInt(matcher.group(3)) + offset; // number
                    //unit.reference--;
                    units.add(unit);
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка чтения файла: " + e.getMessage());
        }
        
        //return units;
    }
}
