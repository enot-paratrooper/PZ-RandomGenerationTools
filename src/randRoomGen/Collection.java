package randRoomGen;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.w3c.dom.Element;

public abstract class Collection {
	
	public String GetTypeOfObject() {
		return null;
	}
	public String GetParameter()
	{
		return null;	
	}
	public String getNameOfPickedTileSet() {
		return null;		
	}
	public List<Integer> getIndexsOfPickedTileSet(){
		return null;	
	}
	public boolean isFurniture() {
		return false;
		
	}
	public LinkedList<UsedFurniture> getUsedFurniture(int Tileset){
		return null;		
	}
	public ArrayList<String> getPickedTileset(){
		return null;
	}
	public ArrayList<String> getPickedFurniture(){
		return null;
	}
	public ArrayList<Element> getPickedFurnitureWithRules(){
		return null;
	}
	public boolean isPlaceble() {
	    return true;		 
	}
    public boolean hasRuleOfPlacing(){
		return false;
	}
}
