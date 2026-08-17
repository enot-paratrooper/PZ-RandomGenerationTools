package randRoomGen;

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static tools.StringTools.parseRanges;

public class Tileset {
	
	private String tilesetName;
	private List<Integer> Indexs = new ArrayList<>();
	private List<Element> RuleOfPlacing = new ArrayList<>();
	private boolean hasRuleOfPlacing = false;
	
	public String getName()
	{
		return this.tilesetName;
	}
	public void SetName(String name)
	{
		this.tilesetName = name;
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
	public boolean IsHaveRuleOfPlacing()
	{
		return hasRuleOfPlacing;
	}
	public List<Element> getRuleOfPlacing()
	{
		return RuleOfPlacing;
	}
}
