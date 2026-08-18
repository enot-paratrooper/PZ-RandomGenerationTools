package randGroups;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import containers.CommonData;

import static tools.StringTools.parseRanges;
import static commonFunc.LoadFunc.loadRandColl;
import static commonFunc.LoadFunc.loadNonRandE;
import static commonFunc.LoadFunc.loadOpenings;

public class RandomGroup {
	String Name;
	private int x;
	private int y;
	private List<Integer> rangeX;
	private List<Integer> rangeY;
	private CommonData data;
		
	public RandomGroup(CommonData data){
		this.data = data;
	}
	
	public void loadRandomGroups(String groupName, String x , String y)
	{
		Name ="";
		rangeX = parseRanges(x);
		rangeY = parseRanges(y);
		initializeCoord();
		 try {
		String fileName = "C:\\Users\\I\\eclipse-workspace\\RandomRoomGenerator\\conf\\RandomGroups\\Group_" + groupName + ".xml";
        
        // Пытаемся загрузить файл из ресурсов
        InputStream inputStream = getClass().getResourceAsStream(fileName);
        
        // Если не найден в ресурсах, пытаемся загрузить из файловой системы
        if (inputStream == null) {
            inputStream = new FileInputStream(fileName);
        }
        data.linker.LoadVariables(fileName, data.RandomCollections.size()-1);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(inputStream);
        document.getDocumentElement().normalize();
        // Получаем корневой элемент group
        Element groupElement = document.getDocumentElement();
        Name = groupElement.getAttribute("name");
        // Загрузка RandomCollection
        loadRandColl(data,groupElement,this.x,this.y);
        // Загрузка NonrandomElements
        loadNonRandE(data,groupElement,this.x,this.y);
        // Загрузка дверей, окон и лестниц группы
        loadOpenings(data,groupElement,this.x,this.y);
		}catch (Exception e) {
	            System.err.println("Ошибка загрузки группы '" + groupName + "': " + e.getMessage());
	            e.printStackTrace();
	        }
	}
	private void initializeCoord() {
		if(rangeX.isEmpty() || rangeY.isEmpty()) {
			throw new IllegalArgumentException("Не заданы координаты группы");
		}
		this.x = rangeX.get(data.random.nextInt(rangeX.size()));
		this.y = rangeY.get(data.random.nextInt(rangeY.size()));
	}
}
