package randRoomGen;

public class UsedFurniture {
	public String FurnitureTiles;
	public String orient;
	public String x;
	public String y;
	public String size;
	/** AI: этаж, на котором стоит объект. Нужен для разбиения по блокам <floor>. */
	public int floor;

	UsedFurniture(int FurnitureTiles,String orient, int x,int y)
	{
		this(FurnitureTiles,orient,x,y,0);
	}

	/** AI: конструктор с этажом. */
	UsedFurniture(int FurnitureTiles,String orient, int x,int y,int floor)
	{
		this.FurnitureTiles = Integer.toString(FurnitureTiles);
		this.orient = orient;
		this.x = Integer.toString(x);
		this.y = Integer.toString(y);
		this.floor = floor;
	}
}
