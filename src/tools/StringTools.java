package tools;

import java.util.ArrayList;
import java.util.List;

public class StringTools {
    public static ArrayList<String> splitString(String input) {
        ArrayList<String> result = new ArrayList<>();
        
        if (input == null || input.trim().isEmpty()) {
            return result;
        }
        
        // Разделяем строку по точке с запятой
        String[] parts = input.split(";");
        
        for (String part : parts) {
            // Убираем лишние пробелы и добавляем непустые строки
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        
        return result;
    }
    
    /**
     * Полное имя тайла: имя набора + индекс, дополненный нулями до трёх знаков.
     * Раньше склейка была "имя" + "_0" + индекс, из-за чего индекс 5 давал
     * "..._05" вместо "..._005", а индекс 100 - "..._0100".
     */
    public static String tileName(String tilesetName, int index) {
        return String.format("%s_%03d", tilesetName, index);
    }
    
    public static String removeLastUnderscorePartRegex(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        // Регулярное выражение: ищет последнее подчеркивание и все символы после него
        return input.replaceAll("_[^_]*$", "");
    }
    
    public static String getLastPartWithoutLeadingZeros(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        // Находим индекс последнего подчеркивания
        int lastUnderscoreIndex = input.lastIndexOf('_');
        
        // Если подчеркивание не найдено, возвращаем пустую строку
        if (lastUnderscoreIndex == -1) {
            return "";
        }
        
        // Получаем часть после последнего подчеркивания
        String lastPart = input.substring(lastUnderscoreIndex + 1);
        
        // Удаляем ведущие нули
        return removeLeadingZeros(lastPart);
    }
    
    // Вспомогательный метод для удаления ведущих нулей
    private static String removeLeadingZeros(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        // Находим первый символ, не являющийся нулем
        int i = 0;
        while (i < str.length() && str.charAt(i) == '0') {
            i++;
        }
        
        // Если все символы - нули, возвращаем "0"
        if (i == str.length()) {
            return "0";
        }
        
        // Возвращаем подстроку без ведущих нулей
        return str.substring(i);
    }
    
    public static List<Integer> parseRanges(String rangesText, List<Integer> Indexs) {
        // Удаляем лишние пробелы и разбиваем по запятым
        String[] rangeParts = rangesText.split(";");
        
        for (String rangePart : rangeParts) {
            rangePart = rangePart.trim();
            if (rangePart.isEmpty()) continue;
            
            // Проверяем формат диапазона "start-end"
            if (rangePart.contains("-")) {
                String[] bounds = rangePart.split("-");
                if (bounds.length == 2) {
                    try {
                        int start = Integer.parseInt(bounds[0].trim());
                        int end = Integer.parseInt(bounds[1].trim());
                        
                        // Добавляем все числа в диапазоне
                        for (int i = start; i <= end; i++) {
                        	Indexs.add(i);
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Некорректный формат диапазона: " + rangePart);
                    }
                }
            } else {
                // Если это одиночное число
                try {
                    int singleIndex = Integer.parseInt(rangePart.trim());
                    Indexs.add(singleIndex);
                } catch (NumberFormatException e) {
                    System.err.println("Некорректный индекс: " + rangePart);
                }
            }
        }
        return Indexs;
    }
    
    public static List<Integer> parseRanges(String rangesText) {
    	ArrayList<Integer> Indexs= new ArrayList<>();
    	if (rangesText == null) {
    		return Indexs;
    	}
        return parseRanges(rangesText, Indexs);
    }
}
