package randRoomGen;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static tools.StringTools.parseRanges;
import static tools.StringTools.tileName;

public class Tileset {

	private String tilesetName;
	private List<Integer> Indexs = new ArrayList<>();
	private List<Element> RuleOfPlacing = new ArrayList<>();
	private boolean hasRuleOfPlacing = false;
	private boolean rulesResolved = false;
	/** Слой отрисовки для мебели этого набора (например WallFurniture). */
	private String layer = null;

	public String getName()
	{
		return this.tilesetName;
	}
	public void SetName(String name)
	{
		this.tilesetName = name;
	}
	public String getLayer()
	{
		return this.layer;
	}
	public void setLayer(String layer)
	{
		if(layer != null && !layer.trim().isEmpty()) {
			this.layer = layer.trim();
		}
	}
	public List<Integer> getIndexs()
	{
		return this.Indexs;
	}
	public void parseRangesIndexs(String ranges) {
		this.Indexs = parseRanges(ranges, Indexs);
	}
	public int GetIndexsCount()
	{
		return Indexs.size();
	}
	public void TrimIdexs(int rangeMax,int dirCount)
	{
		int rangeMin = rangeMax-dirCount; 
	    if (rangeMin < 0 || rangeMax < 0 || rangeMin > rangeMax-1) {
	            throw new IllegalArgumentException(
	                String.format("Некорректный диапазон индексов: [%d, %d]", rangeMin, rangeMax)
	                );
	     }
	     // Проверка, что индексы в пределах списка
	     if (rangeMax-1 >= Indexs.size()) {
	            throw new IndexOutOfBoundsException(
	                String.format("Индекс %d выходит за пределы списка (размер: %d)", rangeMax, Indexs.size())
	            );
	     }
	     
	     List<Integer> sublist= Indexs.subList(rangeMin, rangeMax);
	     Indexs = new ArrayList<>(sublist);
	     sublist.clear();
	}
	public void setIndexs(List<Integer> newIndexs) {
		this.Indexs = newIndexs;
		
	}
	public void setRules(NodeList Rules) {
		hasRuleOfPlacing = true;
		for(int i=0;i<Rules.getLength();i++)
        {
			Element RuleElement = (Element)Rules.item(i);
			RuleOfPlacing.add(RuleElement);
        }
       }
	/**
	 * Тот же setRules, но для уже собранного списка. Нужен NonrandomElement,
	 * который строит правила из блока furniture комнаты, а не из файла коллекции.
	 */
	public void setRules(List<Element> Rules) {
		hasRuleOfPlacing = true;
		RuleOfPlacing.addAll(Rules);
	}
	public boolean IsHaveRuleOfPlacing()
	{
		return hasRuleOfPlacing;
	}
	public List<Element> getRuleOfPlacing()
	{
		return RuleOfPlacing;
	}
	/**
	 * Разворачивает правила размещения: заменяет позиции в атрибуте name
	 * на полные имена тайлов. Общий код для RandomCollection и NonrandomElement.
	 * Повторный вызов безопасен - имена подставляются только один раз.
	 */
	public ArrayList<Element> getFurnitureWithRules()
	{
		ArrayList<Element> Rules = new ArrayList<>(RuleOfPlacing);
		if(rulesResolved) {
			return Rules;
		}
		for(Element entry:Rules) {
			NodeList tiles = entry.getElementsByTagName("tile");
			if(tiles.getLength()==0) {
				throw new IllegalArgumentException("Незаданы tile " + tilesetName);
			}
			for(int i=0;i<tiles.getLength();i++) {
				Element tileElement = (Element)tiles.item(i);
				int position = Integer.parseInt(tileElement.getAttribute("name"));
				if(position < 0 || position >= Indexs.size()) {
					throw new IndexOutOfBoundsException(
						"Правило размещения набора '" + tilesetName + "' ссылается на позицию "
						+ position + ", а в наборе " + Indexs.size() + " тайлов");
				}
				tileElement.setAttribute("name", tileName(tilesetName, Indexs.get(position)));
			}
		}
		rulesResolved = true;
		return Rules;
	}
	/** Имена тайлов набора в порядке направлений. */
	public ArrayList<String> getTileNames()
	{
		ArrayList<String> names = new ArrayList<>();
		for(Integer j:Indexs)
		{
			names.add(tileName(tilesetName, j));
		}
		return names;
	}
}
