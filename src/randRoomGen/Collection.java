package randRoomGen;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.w3c.dom.Element;

public abstract class Collection {
	public int relativeNumberLocation = -1;
	
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
    /**
     * Слой отрисовки мебели в .tbx (например WallFurniture).
     * null - слой по умолчанию, атрибут layer не пишется.
     */
    public String getLayer(){
		return null;
	}
    /**
     * AI: этаж коллекции.
     * Проставляется при загрузке и определяет, в какой блок <floor>
     * попадут объекты коллекции.
     */
    public void setFloor(int floor){
    }
    public int getFloor(){
		return 0;
	}
    /**
     * AI: готовый блок <tile_entry> для дословной записи в .tbx.
     * null - набор собирается обычным способом из Tileset.
     * Переопределён только в RawTileEntry.
     */
    public org.w3c.dom.Element getRawTileEntry(){
		return null;
	}
}
