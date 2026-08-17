package commonFunc;

import static tools.StringTools.splitString;

import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import containers.CommonData;
import randGroups.RandomGroup;
import randRoomGen.NonrandomElement;
import randRoomGen.RandomCollection;
import randRoomGen.ObjectTiles.GlobalParameter;
import randRoomGen.ObjectTiles.ObjectTileFactory;

public class LoadFunc {

	public static void loadRandColl(CommonData data, Element mainElement) {
		NodeList RandomCollectionNodes = mainElement.getElementsByTagName("RandomCollection");
        for(int i=0;i<RandomCollectionNodes.getLength();i++) {
        	Element RandomCollectionE = (Element)RandomCollectionNodes.item(i);
        	String RandomCollectionName = RandomCollectionE.getAttribute("name");
        	if(RandomCollectionName.isEmpty()) {
        		throw new IllegalArgumentException("Неверное имя коллекции - " + RandomCollectionE.getAttribute("num"));
        	}
        	RandomCollection newRandomCollection = new RandomCollection(data.random);
        	newRandomCollection.numberOfCollection = Integer.parseInt(RandomCollectionE.getAttribute("num"));
        	newRandomCollection.loadRandomCollection(splitString(RandomCollectionName));
        	NodeList ObjectTiles = RandomCollectionE.getElementsByTagName("object");
        	if(ObjectTiles.getLength()==0) {
        		newRandomCollection.AddFurniture(new GlobalParameter());
        	}
        	for(int j=0;j<ObjectTiles.getLength();j++)
        	{
        		Element FurnitureE = (Element)ObjectTiles.item(j);
        		int randCollNum = Integer.parseInt(RandomCollectionE.getAttribute("num"));
        		newRandomCollection.AddFurniture(ObjectTileFactory.createReflective(FurnitureE, randCollNum));
        	}
        	data.RandomCollections.add(newRandomCollection);
        }
	}
	public static void loadRandColl(CommonData data, Element mainElement, int x, int y) {
		NodeList RandomCollectionNodes = mainElement.getElementsByTagName("RandomCollection");
        for(int i=0;i<RandomCollectionNodes.getLength();i++) {
        	Element RandomCollectionE = (Element)RandomCollectionNodes.item(i);
        	String RandomCollectionName = RandomCollectionE.getAttribute("name");
        	if(RandomCollectionName.isEmpty()) {
        		throw new IllegalArgumentException("Неверное имя коллекции - " + RandomCollectionE.getAttribute("num"));
        	}
        	RandomCollection newRandomCollection = new RandomCollection(data.random);
        	newRandomCollection.numberOfCollection = Integer.parseInt(RandomCollectionE.getAttribute("num"));
        	newRandomCollection.loadRandomCollection(splitString(RandomCollectionName));
        	NodeList ObjectTiles = RandomCollectionE.getElementsByTagName("object");
        	for(int j=0;j<ObjectTiles.getLength();j++)
        	{
        		Element FurnitureE = (Element)ObjectTiles.item(j);
        		int randCollNum = Integer.parseInt(RandomCollectionE.getAttribute("num"));
        		newRandomCollection.AddFurniture(ObjectTileFactory.createReflective(FurnitureE, randCollNum));
        	}
        	newRandomCollection.setOffsetX(x);
        	newRandomCollection.setOffsetY(y);
        	data.RandomCollections.add(newRandomCollection);
        }
	}
	
	public static void loadNonRandE(CommonData data, Element mainElement) {
		NodeList nonrandomElementsNodes = mainElement.getElementsByTagName("NonrandomElement");
        for(int i=0;i<nonrandomElementsNodes.getLength();i++) {
        	Element nonrandomElement = (Element)nonrandomElementsNodes.item(i);
        	NonrandomElement newNonrandomElement = new NonrandomElement();
        	int num = Integer.parseInt(nonrandomElement.getAttribute("num"));
        	newNonrandomElement.loadNonrandomElement(nonrandomElement, num, data.linker);
        	data.NonrandomElements.add(newNonrandomElement);
        }
	}
	
	public static void loadNonRandE(CommonData data, Element mainElement, int x, int y) {
		NodeList NonrandomElementsNodes = mainElement.getElementsByTagName("NonrandomElement");
        for(int i=0;i<NonrandomElementsNodes.getLength();i++) {
        	Element NonrandomElement = (Element)NonrandomElementsNodes.item(i);
        	NonrandomElement newNonrandomElement = new NonrandomElement();
        	int num = Integer.parseInt(NonrandomElement.getAttribute("num"));
        	newNonrandomElement.loadNonrandomElement(NonrandomElement, num, data.linker);
        	newNonrandomElement.setOffsetX(x);
        	newNonrandomElement.setOffsetY(y);
        	data.NonrandomElements.add(newNonrandomElement);
        }
	}
	
	public static void loadRandGroup(CommonData data, Element roomElement,List<RandomGroup> RandomGroups) {
		NodeList randomGroupsNodes = roomElement.getElementsByTagName("RandomGroup");
        for(int i=0;i<randomGroupsNodes.getLength();i++) {
        	Element randomGroupElement = (Element)randomGroupsNodes.item(i);
        	int num = Integer.parseInt(randomGroupElement.getAttribute("num"));
        	String name = randomGroupElement.getAttribute("name");
        	NodeList groopObjectsNodes = randomGroupElement.getElementsByTagName("object");
        	for(int j=0;j<groopObjectsNodes.getLength();j++) {
            	RandomGroup newRandomGroup = new RandomGroup(data);
        		Element groopObjectsElement = (Element) groopObjectsNodes.item(j);
        		String x =  groopObjectsElement.getAttribute("x");
        		String y =  groopObjectsElement.getAttribute("y");
        		newRandomGroup.loadRandomGroups(name,x,y);
        		RandomGroups.add(newRandomGroup);
        	}
        }
	}
}
