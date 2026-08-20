package randRoomGen;


import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
	private int floor = 0;
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
			loadFurnitureTileset(tilesetElement);
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
	
	/**
	 * Разбор блока furniture нерандомного элемента.
	 *
	 * Поддерживает как однотайловую мебель (по одному tile в каждом entry, как раньше),
	 * так и многоплиточную (несколько tile с разными x/y внутри одного entry).
	 * Многоплиточный вариант превращается в правила размещения того же вида,
	 * что и hasXsize="true" в файлах коллекций: entry клонируется, а имена тайлов
	 * заменяются на позиции в списке индексов набора. Благодаря этому дальше
	 * работает общий код Tileset.getFurnitureWithRules(), и такой элемент
	 * можно связать через globalvariable с RandomCollection.
	 */
	private void loadFurnitureTileset(Element furnitureElement) {
		if(furnitureElement == null) {
			throw new IllegalArgumentException("У нерандомного элемента типа furniture отсутствует блок furniture");
		}
		if(furnitureElement.hasAttribute("layer")) {
			tileset.setLayer(furnitureElement.getAttribute("layer"));
		}
		NodeList entryNodes = furnitureElement.getElementsByTagName("entry");
		if(entryNodes.getLength()==0) {
			throw new IllegalArgumentException("В блоке furniture не задано ни одного entry");
		}
		// Первый проход: выясняем, многоплиточная ли мебель
		boolean multiTile = false;
		for(int i=0;i<entryNodes.getLength();i++) {
			Element entryElement = (Element) entryNodes.item(i);
			int tileCount = entryElement.getElementsByTagName("tile").getLength();
			if(tileCount==0) {
				throw new IllegalArgumentException("В entry не задано ни одного tile");
			}
			if(tileCount>1) {
				multiTile = true;
			}
		}
		// Второй проход: собираем индексы и, для многоплиточной мебели, правила
		List<Integer> Indexs = new ArrayList<Integer>();
		List<Element> Rules = new ArrayList<Element>();
		String baseName = null;
		for(int i=0;i<entryNodes.getLength();i++) {
			Element entryElement = (Element) entryNodes.item(i);
			// Работаем с копией, чтобы не портить исходный DOM комнаты
			Element ruleElement = (Element) entryElement.cloneNode(true);
			NodeList tileNodes = ruleElement.getElementsByTagName("tile");
			for(int j=0;j<tileNodes.getLength();j++) {
				Element tileElement = (Element) tileNodes.item(j);
				String fullName = tileElement.getAttribute("name");
				if(fullName.isEmpty()) {
					throw new IllegalArgumentException("У tile не задан атрибут name");
				}
				String currentName = removeLastUnderscorePartRegex(fullName);
				if(baseName == null) {
					baseName = currentName;
				}
				else if(!baseName.equals(currentName)) {
					throw new IllegalArgumentException(
						"Мебель нерандомного элемента должна использовать один набор тайлов: '"
						+ baseName + "' и '" + currentName + "'");
				}
				int index = Integer.parseInt(getLastPartWithoutLeadingZeros(fullName));
				int position;
				if(multiTile) {
					// Один и тот же тайл может встречаться в разных направлениях
					position = Indexs.indexOf(index);
					if(position == -1) {
						Indexs.add(index);
						position = Indexs.size()-1;
					}
				}
				else {
					Indexs.add(index);
					position = Indexs.size()-1;
				}
				tileElement.setAttribute("name", Integer.toString(position));
			}
			Rules.add(ruleElement);
		}
		tileset.SetName(baseName);
		tileset.setIndexs(Indexs);
		if(multiTile) {
			tileset.setRules(Rules);
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
		String fullName = ((Element)tilesetElement.getElementsByTagName("tile").item(0)).getAttribute("tile");
		tileset.SetName(removeLastUnderscorePartRegex(fullName));	
	}
	@Override
	public List<Integer> getIndexsOfPickedTileSet(){
		return tileset.getIndexs();
	}
	 @Override
	public ArrayList<String> getPickedTileset()
		{			
			return tileset.getTileNames();
		}
	 @Override
		public ArrayList<String> getPickedFurniture()
		{
			return tileset.getTileNames();
		}
	 @Override
	 public ArrayList<Element> getPickedFurnitureWithRules()
		{
			return tileset.getFurnitureWithRules();
		}
	 @Override
	 public boolean hasRuleOfPlacing(){
			return tileset.IsHaveRuleOfPlacing();
		}
	 @Override
	 public String getLayer(){
			return tileset.getLayer();
		}
	private void setIndexsOfPickedTileSet(Element tilesetElement){
		List<Integer> Indexs = new ArrayList<Integer>();
		NodeList tileNodes = tilesetElement.getElementsByTagName("tile");
		for(int i=0;i<tileNodes.getLength();i++)
		{
			Element tileElement = (Element) tileNodes.item(i);
			String lastBlock = getLastPartWithoutLeadingZeros(tileElement.getAttribute("tile"));
			Indexs.add(Integer.parseInt(lastBlock));
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
			UsedFurniture.add(new UsedFurniture(Tileset,object.direction,object.x,object.y,object.floor));
		}
		return UsedFurniture;
		 
	 }
	 @Override
	 public boolean isPlaceble()
	 {
		 return tilesGenerated.get(0).placeble;
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
	 @Override
	 public void setFloor(int floor) {
		 this.floor = floor;
		 for(ObjectTile tile:tilesGenerated) {
			 if(!tile.floorExplicit) {
				 tile.floor = floor;
			 }
		 }
	 }
	 @Override
	 public int getFloor() {
		 return this.floor;
	 }
	 public int getLinkNumber() {
		 return this.LinkNumber;
	 }
	 public void setTileset(Tileset newTileset) {
		 this.tileset = newTileset;
	 }
}
