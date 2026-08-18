package randRoomGen.ObjectTiles;

public class TileParameter {
	public String ParametrName;
	public int TileNum = -1;
	public boolean hasLink=false;
	public String LinkName;
	
	TileParameter(String Name) {
		this.ParametrName = Name;
	}
	
	TileParameter(String Name,int Number){
		this.ParametrName = Name;
		this.TileNum = Number;
	}
	
	TileParameter(String Name, boolean link, String NameLink){
		this.ParametrName = Name;
		this.hasLink = link;
		this.LinkName = NameLink;
	}
}
