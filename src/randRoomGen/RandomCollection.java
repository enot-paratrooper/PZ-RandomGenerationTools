package randRoomGen;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import randRoomGen.ObjectTiles.Furniture;
import randRoomGen.ObjectTiles.ObjectTile;
import randRoomGen.ObjectTiles.Roomparameter;

public class RandomCollection extends Collection
{

	private List<Tileset> Tilesets =  new ArrayList<>();
	private int dirCount;
	private int furnitureTypeCount;
	private List<ObjectTile> tilesGenerated= new ArrayList<>();
	public int numberOfCollection;
	private Tileset pickedTileset; 
	private Random random;
    // Создаем парсер XML
	private static DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	
	public RandomCollection(Random random){
		this.random = random;
	}
	
	
	public void loadRandomCollection(ArrayList<String> collectionNames) {
	        // Сброс текущих данных
		    Tilesets.clear();	
	        dirCount = 0;
	        furnitureTypeCount = 0;	        
	        
	        try {
	        	for(String collectionName:collectionNames)
	        	{
	            // Формируем имя файла
	            String fileName = "C:\\Users\\I\\eclipse-workspace\\RandomRoomGenerator\\conf\\RandomCollections\\Collection_" + collectionName + ".xml";
	            
	            // Пытаемся загрузить файл из ресурсов
	            InputStream inputStream = getClass().getResourceAsStream(fileName);
	            
	            // Если не найден в ресурсах, пытаемся загрузить из файловой системы
	            if (inputStream == null) {
	                inputStream = new FileInputStream(fileName);
	            }
	            // Создаем документ
	            DocumentBuilder builder = factory.newDocumentBuilder();
	            Document document = builder.parse(inputStream);
	            document.getDocumentElement().normalize();
	            
	            // Получаем корневой элемент Collection
	            Element collectionElement = document.getDocumentElement();
	            
	            // Получаем все элементы Tileset
	            NodeList tilesetNodes = collectionElement.getElementsByTagName("Tileset");
	            // Обрабатываем все Tileset
	            for(int i=0;i<tilesetNodes.getLength();i++)
	            {
	            	Element tilesetElement = (Element)tilesetNodes.item(i);	      	            	
	            	Tileset newTileset = new Tileset();
	            	//Загрузка правила размещения больших объектов
                    if(tilesetElement.hasAttribute("hasXsize")) {
                    	if(tilesetElement.getAttribute("hasXsize").equals("true")){
                    		newTileset.setRules(tilesetElement.getElementsByTagName("entry"));
                    	}                   	
	            	}
	            	newTileset.SetName(tilesetElement.getAttribute("name"));
	            	String dirCountStr = tilesetElement.getAttribute("tileDirCount");
                    if (!dirCountStr.isEmpty()) {
                        dirCount = Integer.parseInt(dirCountStr);
                    }
                    else
                    {
                    	throw new IllegalArgumentException("Незадано количество направлений");
                    }
	            	NodeList tileNodes = tilesetElement.getElementsByTagName("Tile");
	            	for (int j = 0; j < tileNodes.getLength(); j++) {
	            		Element tileElement = (Element)tileNodes.item(j);
	            		String ranges = tileElement.getTextContent().trim();
	            		newTileset.parseRangesIndexs(ranges);
	            	}
	            	if(newTileset.GetIndexsCount()%dirCount != 0) {
	            		throw new NumberFormatException("Неверно заданы индексы или количество направлений");
	            	}
	            	Tilesets.add(newTileset);
	            }
	            if(GetIndexsCount()%dirCount != 0) {
            		throw new NumberFormatException("Неверно заданы индексы или количество направлений");
            	}
	            furnitureTypeCount = GetIndexsCount()/dirCount;
	            inputStream.close();
	        	}
	            
	        } catch (Exception e) {
	            System.err.println("Ошибка загрузки коллекции '" + collectionNames + "': " + e.getMessage());
	            e.printStackTrace();
	        }
	    }
	 private int GetIndexsCount()
	 {
		 int Count =0;
		 for(Tileset t: Tilesets)
		 {
			 Count+=t.GetIndexsCount();
		 }
		 return Count;
	 }
	 @Override
	 public String GetTypeOfObject()
	 {
		 List<String> types = new LinkedList<>();
		 for(ObjectTile Tile:tilesGenerated)
		 {
			 if(types.size()==0||!types.get(0).equals(Tile.type))
			 {
				 types.add(Tile.type);
			 }
		 }
		 if(types.size()>1)
		 {
			 throw new IllegalArgumentException("Коллекция - "+numberOfCollection+": В коллекции не может быть больше 2 типов");
		 }
		 return types.get(0);
	 }
	 
	 public void PrintCollectionInfo() {
	        System.out.println("Dir Count: " + dirCount);
	        System.out.println("Furniture Count: " + furnitureTypeCount);
	    }
	 
	 public void AddFurniture(ObjectTile furniture){
		 tilesGenerated.add(furniture);
	 }
	 
	 public void PickTileSet()
	 {		 
		 int numberOfTile = random.nextInt(furnitureTypeCount);
		 int indexCount=0;
		 for(int i=0;i<Tilesets.size();i++)
		 {
			 indexCount+=Tilesets.get(i).GetIndexsCount();
			 if(numberOfTile*dirCount<indexCount)
			 {
				 pickedTileset= Tilesets.get(i);
				 pickedTileset.TrimIdexs(indexCount-numberOfTile*dirCount, dirCount);
				 break;
			 }
		 }
		 
	 }
	 public Tileset getPickedTileSet() {
		 return this.pickedTileset;
	 }
	 @Override
	 public String getNameOfPickedTileSet()
	 {
		 return pickedTileset.getName();
	 }
	 @Override
	 public List<Integer> getIndexsOfPickedTileSet()
	 {
		 return pickedTileset.getIndexs();
	 } 
	 @Override
	public ArrayList<String> getPickedTileset()
		{			
			ArrayList<String> PickedTileset = new ArrayList<>();
			String NameOfPickedTileset = pickedTileset.getName();
			ArrayList<Integer> Indexs = (ArrayList<Integer>) pickedTileset.getIndexs();
			for(Integer j:Indexs)
			{
				PickedTileset.add(NameOfPickedTileset+"_0"+j);
			}
			return PickedTileset;
		} 
	 public ArrayList<Element> getPickedFurnitureWithRules()
		{			
			String NameOfPickedTileset = pickedTileset.getName();
			ArrayList<Integer> Indexs = (ArrayList<Integer>) pickedTileset.getIndexs();
			ArrayList<Element> Rules = (ArrayList<Element>) pickedTileset.getRuleOfPlacing();
            for(Element entry:Rules){
				NodeList tiles = entry.getElementsByTagName("tile");
				if(tiles.getLength()==0){
					throw new IllegalArgumentException("Незаданы tile " + NameOfPickedTileset); 
				}
				for (int i=0;i<tiles.getLength();i++) {
					Element tileElement = (Element)tiles.item(i);	
					int position = Integer.parseInt(tileElement.getAttribute("name"));
					String tileName = NameOfPickedTileset + "_0" + Indexs.get(position);
					tileElement.setAttribute("name", tileName);
				}
			}
			return Rules;
		} 
	 @Override
	 public String GetParameter()
	 {
		 return ((Roomparameter) tilesGenerated.get(0)).GetNameParameter();
	 }
	 @Override
		public ArrayList<String> getPickedFurniture()
		{
			ArrayList<String> PickedTileset = new ArrayList<>();
			String NameOfPickedTileset = pickedTileset.getName();
			ArrayList<Integer> Indexs = (ArrayList<Integer>) pickedTileset.getIndexs();
			for(Integer j:Indexs)
			{
				PickedTileset.add(NameOfPickedTileset+"_0"+j);
			}
			return PickedTileset;
		}	 
	 @Override
	 public boolean isFurniture()
	 {
		 return tilesGenerated.get(0).type.equals("furniture");
	 }
	 @Override
	 public boolean isPlaceble()
	 {
		 return ((Furniture)tilesGenerated.get(0)).placeble;
	 }
	 @Override
	 public LinkedList<UsedFurniture> getUsedFurniture(int Tileset)
	 {
		 LinkedList<UsedFurniture> UsedFurniture = new LinkedList<>();
		 for(ObjectTile Tile:tilesGenerated)
		 {
			 Furniture furniture = (Furniture) Tile;
			 UsedFurniture.add(new UsedFurniture(Tileset,furniture.direction,furniture.x,furniture.y));
		 }
		 return UsedFurniture;
	 }
	 @Override
	 public boolean hasRuleOfPlacing(){
		return pickedTileset.IsHaveRuleOfPlacing();
	}
	 public void setOffsetX(int x) {
		 for(ObjectTile tile:tilesGenerated) {
			 tile.x+=x;
		 }
	 }
	 public void setOffsetY(int y) {
		 for(ObjectTile tile:tilesGenerated) {
			 tile.y+=y;
		 }
	 }
}
