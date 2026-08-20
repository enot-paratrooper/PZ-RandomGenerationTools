package randRoomGen;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
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
import static commonFunc.LoadFunc.loadOpenings;

public class Room {	
	
	private String Name;
	private int SizeX;
	private int SizeY;
	private int x;
	private int y;
	private int floor;
	/** AI: индекс комнаты в data.randomRooms. Сквозной номер в .tbx = roomIndex+1. */
	private int roomIndex = 0;
	private int [][] roomCells;
	private String roomCellsString;
	
	private List<Integer> Color = new ArrayList<Integer>();
	// AI: TileSetStart/TileSetEnd заменены на две пары границ.
	// Коллекции комнаты идут подряд внутри RandomCollections и внутри
	// NonrandomElements, но в объединённом Collections они не соседи,
	// поэтому одного диапазона на многокомнатное здание не хватало.
	private int randStart=0;
	private int randEnd=0;
	private int nonRandStart=0;
	private int nonRandEnd=0;
	private int InteriorWall;
	private int InteriorWallTrim;
	private int Floor;
	private int GrimeFloor;
	private int GrimeWall;
	// Наборы тайлов проёмов. Задаются такими же RoomParameter, как стены и пол.
	private int Door;
	private int DoorFrame;
	private int Window;
	private int Curtains;
	private int Shutters;
	private int Stairs;
	
	private List<RandomGroup> RandomGroups = new ArrayList<RandomGroup>();

	private CommonData data;
	public Room(CommonData data){
		this.data = data;
	}
	
	/** AI: совместимость со старыми тестами (Generator). */
	public void loadRoom(String roomName)
	{
		loadRoom(roomName,0,0,0,0);
	}
	
	public void loadRoom(String roomName, int x, int y, int z)
	{
		loadRoom(roomName,x,y,z,0);
	}
	
	/** AI: добавлен roomIndex - нужен проёмам комнаты для поиска её параметров. */
	public void loadRoom(String roomName, int x, int y, int z, int roomIndex)
	{
		this.Name ="";
		this.SizeX=0;
		this.SizeY=0;
		this.x=x;
		this.y=y;
		this.floor=z;
		this.roomIndex=roomIndex;
		 try {
		String fileName = "..\\RandomRoomGenerator\\conf\\RandomRoom\\Room_" + roomName + ".xml";
        
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
        // AI: начало диапазонов коллекций комнаты
        randStart = data.RandomCollections.size();
        nonRandStart = data.NonrandomElements.size();
        // Загрузка RandomCollection
        loadRandColl(data,roomElement,this.x,this.y,this.floor);
        // Загрузка NonrandomElements
        loadNonRandE(data,roomElement,this.x,this.y,this.floor);
        // Загрузка дверей, окон и лестниц
        loadOpenings(data,roomElement,this.x,this.y,this.floor,this.roomIndex);
        // Загрузка RandomGroups
        loadRandGroup(data,roomElement,RandomGroups,this.x,this.y,this.floor,this.roomIndex);
        // AI: конец диапазонов коллекций комнаты (включая коллекции групп)
		randEnd = data.RandomCollections.size();
		nonRandEnd = data.NonrandomElements.size();
		inputStream.close();
		}catch (Exception e) {
	            System.err.println("Ошибка загрузки комнаты '" + roomName + "': " + e.getMessage());
	            e.printStackTrace();
	        }
	}	
	
	/**
	 * Разбор объектов type="RoomParameter".
	 *
	 * AI: номер набора тайлов берётся из relativeNumberLocation, то есть из
	 * сквозной нумерации tile_entry по всему зданию. Локальный счётчик j,
	 * начинавшийся с 1 в каждой комнате, во втором и следующих помещениях
	 * указывал бы на чужие наборы.
	 *
	 * Вызывать строго после CommonData.DetermineListType().
	 */
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
			Door=0;
			DoorFrame=0;
			Window=0;
			Curtains=0;
			Shutters=0;
			Stairs=0;
			for(int i=randStart;i<randEnd;i++) {
				applyRoomParameter(data.RandomCollections.get(i));
			}
			for(int i=nonRandStart;i<nonRandEnd;i++) {
				applyRoomParameter(data.NonrandomElements.get(i));
			}
		} catch(Exception e) 
		{
			 System.err.println("Ошибка загрузки параметров" + e.getMessage());
	            e.printStackTrace();
		}
	}
	
	/** AI: вынесено из InitRoomparameters, чтобы обойти оба диапазона одним кодом. */
	private void applyRoomParameter(Collection coll)
	{
		if(!"RoomParameter".equals(coll.GetTypeOfObject()))
		{
			return;
		}
		int j = coll.relativeNumberLocation;
		if(j < 1)
		{
			throw new IllegalStateException(
				"Не определён номер набора тайлов. InitRoomparameters вызван до DetermineListType");
		}
		String param=coll.GetParameter();
		switch(param)
		{
		case "InteriorWall": InteriorWall =j;break;
		case "InteriorWallTrim": InteriorWallTrim =j;break;
		case "Floor": Floor =j;break;
		case "GrimeFloor": GrimeFloor =j;break;
		case "GrimeWall": GrimeWall =j;break;
		case "Door": if(Door==0) {Door=j;}; break;
		case "DoorFrame": if(DoorFrame==0) {DoorFrame=j;}break;
		case "Window": if(Window==0) { Window=j;}break;
		case "Curtains": if(Curtains==0) {Curtains=j;}break;
		case "Shutters": if(Shutters==0) {Shutters=j;}break;
		case "Stairs": if(Stairs==0) { Stairs=j;}break;
		default:
			throw new IllegalArgumentException("Неизвестный параметр комнаты: " + param);
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
	/** AI: координаты комнаты внутри здания. */
	public int getX()
	{
		return x;
	}
	public int getY()
	{
		return y;
	}
	/**
	 * AI: этаж комнаты.
	 * Назван getLevel, а не getFloor: getFloor уже занят номером набора тайлов пола.
	 */
	public int getLevel()
	{
		return floor;
	}
	/** AI: сквозной номер комнаты в .tbx. */
	public int getRoomNumber()
	{
		return roomIndex+1;
	}
	public int[][] getRoomCells()
	{
		return roomCells;
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
    
    // Get метод для Door
    public int getDoor() {
        return Door;
    }
    
    // Get метод для DoorFrame
    public int getDoorFrame() {
        return DoorFrame;
    }
    
    // Get метод для Window
    public int getWindow() {
        return Window;
    }
    
    // Get метод для Curtains
    public int getCurtains() {
        return Curtains;
    }
    
    // Get метод для Shutters
    public int getShutters() {
        return Shutters;
    }
    
    // Get метод для Stairs
    public int getStairs() {
        return Stairs;
    }
    
    public String getRoomCellsString()
    {
    	return roomCellsString;
    }
}
