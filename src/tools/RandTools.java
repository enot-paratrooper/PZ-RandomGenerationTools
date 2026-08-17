package tools;

public class RandTools {
	 public static boolean chance(String Strpercent) {
		    double percent = Double.parseDouble(Strpercent.replace(',', '.'));
		    if (percent < 0 || percent > 100) {
		        throw new IllegalArgumentException("Процент должен быть от 0 до 100");
		    }
		    return Math.random() * 100 > percent;
		}
}
