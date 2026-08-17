package randRoomGen;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import containers.CommonData;
import randGroups.RandomGroup;

import static commonFunc.LoadFunc.loadRandColl;
import static commonFunc.LoadFunc.loadNonRandE;
import static commonFunc.LoadFunc.loadRandGroup;

public class Room {	
	
	private String Name;
	private int SizeX;
	private int SizeY;
	private int [][] roomCells;
	private String roomCellsString;
	
	private List<Integer> Color = new ArrayList<Integer>();
	private int TileSetStart=0;
	private int TileSetEnd=0;
	private int InteriorWall;
	private int InteriorWallTrim;
	private int Floor;
	private int GrimeFloor;
	private int GrimeWall;
	
	private List<RandomGroup> RandomGroups = new ArrayList<RandomGroup>();

	private CommonData data;
	public Room(CommonData data){
		this.data = data;
	}
	
	public void loadRoom(String roomName)
	{
		Name ="";
		SizeX=0;
		SizeY=0;
		 try {
		String fileName = "C:\\Users\\I\\eclipse-workspace\\RandomRoomGenerator\\conf\\RandomRoom\\" + roomName + ".xml";
        
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
        // Получаем корневой элемент room
        Element roomElement = document.getDocumentElement();
        Name = roomElement.getAttribute("name");
        String SizeX = roomElement.getAttribute("sizeX");
        if(!SizeX.isEmpty()){
        	this.SizeX = Integer.parseInt(SizeX);
        }
        else {
        	throw new NumberFormatException("Незадан размер по X");
        }
        String SizeY = roomElement.getAttribute("sizeY");
        if(!SizeY.isEmpty()){
        	this.SizeY = Integer.parseInt(SizeY);
        }
        else {
        	throw new NumberFormatException("Незадан размер по Y");
        }
        //Заполнение массива комнаты
        NodeList roomCellsNodes = roomElement.getElementsByTagName("tiles");
        Element roomCellsE= (Element) roomCellsNodes.item(0);
        roomCellsString = roomCellsE.getTextContent().trim();
        roomCells = parseGrid(roomCellsString);
        TileSetStart = data.Collections.size();
        // Загрузка RandomCollection
        loadRandColl(data,roomElement);
        // Загрузка NonrandomElements
        loadNonRandE(data,roomElement);
        // Загрузка RandomGroups
        loadRandGroup(data,roomElement,RandomGroups);
		TileSetEnd = data.RandomCollections.size() + data.NonrandomElements.size();
		}catch (Exception e) {
	            System.err.println("Ошибка загрузки комнаты '" + roomName + "': " + e.getMessage());
	            e.printStackTrace();
	        }
	}	
	public void InitRoomparameters()
	{
		try 
		{
			Color.clear();
			Color.add(data.random.nextInt(256));
			Color.add(data.random.nextInt(256));
			Color.add(data.random.nextInt(256));
			InteriorWall=0;
			InteriorWallTrim=0;
			Floor=0;
			GrimeFloor=0;
			GrimeWall=0;
			int j =1;
			for(int i=TileSetStart;i<TileSetEnd;i++) {
				Collection coll =data.Collections.get(i);
				if(coll.GetTypeOfObject().equals("RoomParameter"))
				{
					String param=coll.GetParameter();
					switch(param)
					{
					case "InteriorWall": InteriorWall =j;break;
					case "InteriorWallTrim": InteriorWallTrim =j;break;
					case "Floor": Floor =j;break;
					case "GrimeFloor": GrimeFloor =j;break;
					case "GrimeWall": GrimeWall =j;break;
					}
					j++;
				}
			}
			
		} catch(Exception e) 
		{
			 System.err.println("Ошибка загрузки параметров" + e.getMessage());
	            e.printStackTrace();
		}
	}	
	public ArrayList<String> getTypesOfTiles()
	{
		ArrayList<String> TypesOfTiles = new ArrayList<String>();
		for(Collection coll:data.Collections)
		{
			TypesOfTiles.add(coll.GetTypeOfObject());
		}
		return TypesOfTiles;
	}
	public String getTypeOfRoomparametrs(int i)
	{
		return data.Collections.get(i).GetParameter();
	}
	public static int[][] parseGrid(String gridString) {
	    // Удаляем все пробелы и переносы строк в начале и конце
	    String trimmed = gridString.trim();
	    
	    // Разбиваем на строки по запятым и переводам строк
	    String[] rows = trimmed.split("\\s*,\\s*\n\\s*");
	    
	    // Если в конце строки есть лишняя запятая, обрабатываем последнюю строку отдельно
	    if (trimmed.endsWith(",")) {
	        rows[rows.length - 1] = rows[rows.length - 1].replaceAll(",$", "");
	    }
	    
	    // Определяем количество строк и столбцов
	    int numRows = rows.length;
	    
	    // Разбиваем первую строку, чтобы определить количество столбцов
	    String[] firstRowCells = rows[0].split("\\s*,\\s*");
	    int numCols = firstRowCells.length;
	    
	    // Создаем двумерный массив
	    int[][] grid = new int[numRows][numCols];
	    
	    // Заполняем массив значениями
	    for (int i = 0; i < numRows; i++) {
	        // Разбиваем строку на ячейки
	        String[] cells;
	        if (i == 0) {
	            cells = firstRowCells; // Уже разбили
	        } else {
	            cells = rows[i].split("\\s*,\\s*");
	        }
	        
	        // Заполняем строку массива
	        for (int j = 0; j < numCols; j++) {
	            grid[i][j] = Integer.parseInt(cells[j]);
	        }
	    }
	    
	    return grid;
	}
	
	public String getName()
	{
		return Name;
	}
	public int getSizeX()
	{
		return SizeX;
	}
	public int getSizeY()
	{
		return SizeY;
	}
	public String getColor()
	{
		return Color.get(0)+" "+Color.get(1)+" "+Color.get(2);
	}
	// Get метод для InteriorWall
    public int getInteriorWall() {
        return InteriorWall;
    }
    
    // Get метод для InteriorWallTrim
    public int getInteriorWallTrim() {
        return InteriorWallTrim;
    }
    
    // Get метод для Floor
    public int getFloor() {
        return Floor;
    }
    
    // Get метод для GrimeFloor
    public int getGrimeFloor() {
        return GrimeFloor;
    }
    
    // Get метод для GrimeWall
    public int getGrimeWall() {
        return GrimeWall;
    }
    
    public String getRoomCellsString()
    {
    	return roomCellsString;
    }
}
