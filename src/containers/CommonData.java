package containers;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import randRoomGen.Collection;
import randRoomGen.NonrandomElement;
import randRoomGen.RandomCollection;
import randRoomGen.RawTileEntry;
import randRoomGen.Room;
import randRoomGen.UsedFurniture;
import randRoomGen.ObjectTiles.ObjectTile;
import randRoomGen.ObjectTiles.TileParameter;
import tools.linker.Linker;

public class CommonData {
	public List<Room> randomRooms = new ArrayList<>(10);
	public List<RandomCollection> RandomCollections = new ArrayList<>(30);
	public List<NonrandomElement> NonrandomElements = new ArrayList<>(30);
	/** AI: готовые блоки tile_entry (крыши и прочие фиксированные наборы). */
	public List<RawTileEntry> RawTileEntries = new ArrayList<>(5);
	public List<Collection> Collections = new ArrayList<>(60);
	public List<Collection> usedFurniture = new ArrayList<>(40);
	public List<Collection> usedTile = new ArrayList<>(20);
	
	public List<ObjectTile> Openings = new ArrayList<>(15);
	
	public List<Integer> UsedUserTiles= new ArrayList<>();
	public List<Integer> UsedTiles= new ArrayList<>(20);
	public List<Integer> UsedFurnitureTiles= new ArrayList<>(40);
	
	public Linker linker = new Linker();
	
	public static Random random = new Random();
	
    public String getUsedTiles()
    {
    	String UsedTile="";
    	for(int ut:UsedTiles)
    	{
    		UsedTile+=ut+" ";
    	}
    	return UsedTile;
    }
    public String getUsedFurnitureTiles()
    {
    	String UsedFurnitureTile="";
    	for(int ut:UsedFurnitureTiles)
    	{
    		UsedFurnitureTile+=ut+" ";
    	}
    	return UsedFurnitureTile;
    }
    /**
     * Список размещённой мебели.
     * Метод не идемпотентен: он дописывает UsedFurnitureTiles, поэтому вызывать
     * его на одном CommonData нужно ровно один раз за генерацию.
     * Этаж каждого объекта лежит в UsedFurniture.floor.
     */
    public LinkedList<UsedFurniture> getUsedFurniture()
    {
    	int j=0;
    	LinkedList<UsedFurniture> UsedFurniture = new LinkedList<>();
    	for(int i=0;i<RandomCollections.size();i++)
    	{
    		if(RandomCollections.get(i).isFurniture()){
    			if(RandomCollections.get(i).isPlaceble()) {
    				UsedFurniture.addAll(RandomCollections.get(i).getUsedFurniture(j));
        			UsedFurnitureTiles.add(j);      			
    			}
    			j++;
    		}
    	}
    	for(int i=0; i<NonrandomElements.size();i++) {
    		if(NonrandomElements.get(i).isFurniture()) {
    			if(NonrandomElements.get(i).isPlaceble()) {
    				UsedFurniture.addAll(NonrandomElements.get(i).getUsedFurniture(j));
        			UsedFurnitureTiles.add(j);       			
    			} 
    			j++;
    		}
    	}
		return UsedFurniture;
    }
    public void InitTileSets()
	{
		try {
		for(RandomCollection randColl:RandomCollections)
		{
			randColl.PickTileSet();
		}
		} catch(Exception e) {
			 System.err.println("Ошибка инициализации TileSet" + e.getMessage());
	            e.printStackTrace();
		}
	}
	public void InitLinks() {
		try {
		// AI: раньше цикл шёл до конца Collections и приводил каждый элемент
		// к NonrandomElement. После появления RawTileEntry в конце списка
		// это упало бы с ClassCastException, поэтому идём прямо по списку.
		for(NonrandomElement linkedCollection : NonrandomElements) {
			Integer linknumber =linkedCollection.getLinkNumber();
			if( linknumber != -1) {
				linkedCollection.setTileset(RandomCollections.get(linknumber).getPickedTileSet());
			}
		}
		}catch(Exception e) {
			 System.err.println("Ошибка создания ссылок в NonrandomElement" + e.getMessage());
	            e.printStackTrace();
		}
		try {
			for(ObjectTile Opening:Openings) {
				for(TileParameter Parameter: Opening.TileParamers) {
					if(Parameter.hasLink) {
						Parameter.TileNum = RandomCollections.get(
								linker.getLinkNumber(Parameter.LinkName))
								.relativeNumberLocation;
					}
				}
			}
			}catch(Exception e) {
				 System.err.println("Ошибка создания ссылок в Openings" + e.getMessage());
		            e.printStackTrace();
			}
	}
	public void MergeCollections()
	{       
		// Объеденение коллекций
		Collections.addAll(RandomCollections);
		Collections.addAll(NonrandomElements);
		// AI: готовые tile_entry идут последними, чтобы не сдвигать
		// уже существующую нумерацию наборов
		Collections.addAll(RawTileEntries);
	}
	/**
	 * Сквозная нумерация наборов тайлов и наборов мебели по всему зданию.
	 *
	 * AI: BuildingParameter обрабатывается так же, как RoomParameter.
	 * Без этого внешние стены и другие параметры здания не попадали
	 * в usedTile и для них не писался tile_entry.
	 */
	public void DetermineListType() {
		int iterTileSet = 1;
		int iterFurnitureTileSet = 0;
		for(Collection coll:Collections) {
			String typeOfObject = coll.GetTypeOfObject();
            if ("RoomParameter".equals(typeOfObject) || "BuildingParameter".equals(typeOfObject)) {
            	coll.relativeNumberLocation = iterTileSet;
            	usedTile.add(coll);
            	iterTileSet++;
            }
            else if("furniture".equals(typeOfObject)) {
            	coll.relativeNumberLocation = iterFurnitureTileSet;
            	usedFurniture.add(coll);
            	iterFurnitureTileSet++;
            }
		}
	}
}
