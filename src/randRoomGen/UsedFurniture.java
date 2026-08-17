package randRoomGen;

public class UsedFurniture {
	public String FurnitureTiles;
	public String orient;
	public String x;
	public String y;
	public String size;
	UsedFurniture(int FurnitureTiles,String orient, int x,int y)
	{
		this.FurnitureTiles = Integer.toString(FurnitureTiles);
		this.orient = orient;
		this.x = Integer.toString(x);
		this.y = Integer.toString(y);
	}
}
