package containers;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import randRoomGen.Collection;
import randRoomGen.NonrandomElement;
import randRoomGen.RandomCollection;
import randRoomGen.UsedFurniture;
import tools.linker.Linker;

public class CommonData {
	public ArrayList<RandomCollection> RandomCollections = new ArrayList<>();
	public List<NonrandomElement> NonrandomElements = new ArrayList<>();
	public List<Collection> Collections = new ArrayList<>();
	
	public List<Integer> UsedUserTiles= new ArrayList<>();
	public List<Integer> UsedTiles= new ArrayList<>();
	public List<Integer> UsedFurnitureTiles= new ArrayList<>();
	
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
		for(int i=RandomCollections.size();i<Collections.size();i++) {
			NonrandomElement linkedCollection = (NonrandomElement)Collections.get(i);
			Integer linknumber =linkedCollection.getLinkNumber();
			if( linknumber != -1) {
				linkedCollection.setTileset(RandomCollections.get(linknumber).getPickedTileSet());
			}
		}
		}catch(Exception e) {
			 System.err.println("Ошибка создания ссылок" + e.getMessage());
	            e.printStackTrace();
		}
	}
	public void MergeCollections()
	{       
		// Объеденение коллекций
		Collections.addAll(RandomCollections);
		Collections.addAll(NonrandomElements);
	}
}
