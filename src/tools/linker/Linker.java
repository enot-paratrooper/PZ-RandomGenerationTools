package tools.linker;


import java.util.ArrayList;
import java.util.List;

import randRoomGen.Collection;
import randRoomGen.RandomCollection;
import randRoomGen.Tileset;

import static tools.linker.LinkUnitParser.parseLinkUnits;

public class Linker {

	private static List<LinkUnit> linkedRandCollection = new ArrayList<>();
	public static int offset=0;
	
	public Linker(){

	}

	
	public void LoadVariables(String filename, int offset)
	{
		this.offset = offset;
		parseLinkUnits(filename,linkedRandCollection, this.offset);
	}

	public int getLinkNumber(String globalvariableName) {
		LinkUnit variableUnit = findLastByName(globalvariableName);
		return variableUnit.reference;
	}
	
	private static LinkUnit findLastByName(String targetName) {
	        if (linkedRandCollection == null || targetName == null) {
	            return null;
	        }
	        
	        // Проходим список с конца к началу для поиска последнего элемента
	        for (int i = linkedRandCollection.size() - 1; i >= 0; i--) {
	            LinkUnit unit = linkedRandCollection.get(i);
	            if (targetName.equals(unit.Name)) {
	                return unit;
	            }
	        }
	        
	        return null;
	    }

}
