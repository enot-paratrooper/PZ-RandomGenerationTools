package tools.linker;



import java.util.HashMap;
import java.util.Map;

import static tools.linker.LinkUnitParser.parseLinkUnits;

public class Linker {

	private Map<String, LinkUnit> linkedRandCollection = new HashMap<>();
	public int offset=0;
	
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
	
	private LinkUnit findLastByName(String targetName) {
	        if (linkedRandCollection == null || targetName == null) {
	            return null;
	        }
	        
	        return linkedRandCollection.get(targetName);
	    }

}
