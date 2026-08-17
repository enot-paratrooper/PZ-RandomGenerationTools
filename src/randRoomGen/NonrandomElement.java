package randRoomGen;


import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import randRoomGen.ObjectTiles.Furniture;
import randRoomGen.ObjectTiles.ObjectTile;
import randRoomGen.ObjectTiles.ObjectTileFactory;
import randRoomGen.ObjectTiles.Roomparameter;
import tools.linker.Linker;

import static tools.StringTools.removeLastUnderscorePartRegex;
import static tools.StringTools.getLastPartWithoutLeadingZeros;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class NonrandomElement extends Collection{
	
	private Integer LinkNumber=-1;
	private Tileset tileset = new Tileset();
	private List<ObjectTile> tilesGenerated = new ArrayList<ObjectTile>();		
	
	public void loadNonrandomElement(Element NonrandomElement, int nrndNum, Linker linker) {
		try {
		NodeList objectNode = NonrandomElement.getElementsByTagName("object");
		for(int i=0;i<objectNode.getLength();i++) {
			Element objectElement = (Element) objectNode.item(i);
			this.tilesGenerated.add(ObjectTileFactory.createReflective(objectElement, nrndNum));
		}
		if(NonrandomElement.hasAttribute("globalvariable")) {
			LinkNumber = linker.getLinkNumber(NonrandomElement.getAttribute("globalvariable"));
			return;
		}
		else if(this.tilesGenerated.get(0).type.equals("furniture")) {
			Element tilesetElement = (Element) NonrandomElement.getElementsByTagName("furniture").item(0);
			setNameOfPickedTileSet(tilesetElement);
			setIndexsOfPickedTileSet(tilesetElement);
		}
		else {
			Element tilesetElement  = (Element) NonrandomElement.getElementsByTagName("tile_entry").item(0);
			setNameOfPickedTileSet(tilesetElement);
			setIndexsOfPickedTileSet(tilesetElement);
		}
		} catch (Exception e) {
			 System.err.println("Ошибка загрузки не случайного элемента '" + nrndNum + "': " + e.getMessage());
	            e.printStackTrace();
		}
		
	}
	@Override
	public String GetTypeOfObject() {
		return tilesGenerated.get(0).type;
	}
	
	@Override
	public String GetParameter()
	{
		return ((Roomparameter) tilesGenerated.get(0)).GetNameParameter();
	}
	@Override
	public String getNameOfPickedTileSet() {
		return tileset.getName();		
	}
	private void setNameOfPickedTileSet(Element tilesetElement) {
		String fullName;
		if(this.tilesGenerated.get(0).type.equals("furniture")) {
			fullName = ((Element)((Element)tilesetElement
					.getElementsByTagName("entry").item(0))
					.getElementsByTagName("tile").item(0)).getAttribute("name");
		}
		else {
			fullName = ((Element)tilesetElement.getElementsByTagName("tile").item(0)).getAttribute("tile");
		}
		tileset.SetName(removeLastUnderscorePartRegex(fullName));	
	}
	@Override
	public List<Integer> getIndexsOfPickedTileSet(){
		return tileset.getIndexs();
	}
	 @Override
	public ArrayList<String> getPickedTileset()
		{			
			ArrayList<String> PickedTileset = new ArrayList<String>();
			String NameOfPickedTileset = tileset.getName();
			ArrayList<Integer> Indexs = (ArrayList<Integer>) tileset.getIndexs();
			for(Integer j:Indexs)
			{
				PickedTileset.add(NameOfPickedTileset+"_0"+j);
			}
			return PickedTileset;
		}
	 @Override
		public ArrayList<String> getPickedFurniture()
		{
			ArrayList<String> PickedTileset = new ArrayList<String>();
			String NameOfPickedTileset = tileset.getName();
			ArrayList<Integer> Indexs = (ArrayList<Integer>) tileset.getIndexs();
			for(Integer j:Indexs)
			{
				PickedTileset.add(NameOfPickedTileset+"_0"+j);
			}
			return PickedTileset;
		}
	private void setIndexsOfPickedTileSet(Element tilesetElement){
		List<Integer> Indexs = new ArrayList<Integer>();
		NodeList tileNodes;
		if(this.tilesGenerated.get(0).type.equals("furniture")) {
			tileNodes = tilesetElement.getElementsByTagName("entry");
			for(int i=0;i<tileNodes.getLength();i++) {
				Element tileElement = (Element) ((Element)tileNodes.item(i)).getElementsByTagName("tile").item(0);
				String lastBlock = getLastPartWithoutLeadingZeros(tileElement.getAttribute("name"));
				Indexs.add(Integer.parseInt(lastBlock));
			}
		}	
		else {
			tileNodes = tilesetElement.getElementsByTagName("tile");
			for(int i=0;i<tileNodes.getLength();i++)
			{
				Element tileElement = (Element) tileNodes.item(i);
				String lastBlock = getLastPartWithoutLeadingZeros(tileElement.getAttribute("tile"));
				Indexs.add(Integer.parseInt(lastBlock));
			}
		}
		tileset.setIndexs(Indexs);
	}
	@Override
	public boolean isFurniture() {
		return tilesGenerated.get(0).type.equals("furniture");		
	}
	 @Override
	 public LinkedList<UsedFurniture> getUsedFurniture(int Tileset){
		LinkedList<UsedFurniture> UsedFurniture = new LinkedList<UsedFurniture>();
		for(ObjectTile object:tilesGenerated) {
		Furniture furniture = (Furniture) object;
		UsedFurniture.add(new UsedFurniture(Tileset,furniture.direction,furniture.x,furniture.y));
		}
		return UsedFurniture;
		 
	 }
	 @Override
	 public boolean isPlaceble()
	 {
		 return ((Furniture)tilesGenerated.get(0)).placeble;
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
	 public int getLinkNumber() {
		 return this.LinkNumber;
	 }
	 public void setTileset(Tileset newTileset) {
		 this.tileset = newTileset;
	 }
}
