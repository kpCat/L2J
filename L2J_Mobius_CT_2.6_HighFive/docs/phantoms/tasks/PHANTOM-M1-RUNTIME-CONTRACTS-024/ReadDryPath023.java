import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.geoengine.GeoEngine;

/** Task-only read-only stock geodata/water diagnostic; never creates a Player or DB connection. */
class ReadDryPath023
{
    public static void main(String[] args) throws Exception
    {
        int x = Integer.parseInt(args[0]), y = Integer.parseInt(args[1]), z = Integer.parseInt(args[2]);
        int endX = Integer.parseInt(args[3]), endY = Integer.parseInt(args[4]);
        ConfigLoader.init();
        var geo = GeoEngine.getInstance();
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var waters = new java.util.ArrayList<int[]>();
        try (var files = java.nio.file.Files.list(Path.of("data/zones")))
        {
            for (Path file : files.filter(path -> path.toString().endsWith(".xml")).toList())
            {
                var zones = factory.newDocumentBuilder().parse(file.toFile()).getElementsByTagName("zone");
                for (int i = 0; i < zones.getLength(); i++)
                {
                    var zone = (Element) zones.item(i);
                    if (!zone.getAttribute("type").equals("WaterZone")) { continue; }
                    var nodes = zone.getElementsByTagName("node");
                    int minX=Integer.MAX_VALUE, maxX=Integer.MIN_VALUE, minY=Integer.MAX_VALUE, maxY=Integer.MIN_VALUE;
                    for (int j=0; j<nodes.getLength(); j++)
                    {
                        var node=(Element)nodes.item(j);
                        int px=Integer.parseInt(node.getAttribute("X")), py=Integer.parseInt(node.getAttribute("Y"));
                        minX=Math.min(minX,px); maxX=Math.max(maxX,px); minY=Math.min(minY,py); maxY=Math.max(maxY,py);
                    }
                    waters.add(new int[]{minX,maxX,minY,maxY,Integer.parseInt(zone.getAttribute("minZ")),Integer.parseInt(zone.getAttribute("maxZ"))});
                }
            }
        }
        int count=(int)Math.ceil(Math.hypot(endX-x,endY-y)/100.0), previousX=x, previousY=y, previousZ=z;
        for(int i=0;i<=count;i++)
        {
            int px=x+(int)Math.round((endX-x)*(double)i/count), py=y+(int)Math.round((endY-y)*(double)i/count);
            if(!geo.hasGeo(px,py)){throw new IllegalStateException("DRY_PATH_MISSING_GEO");}
            int pz=geo.getHeight(px,py,previousZ);
            for(var water:waters){if(px>=water[0]&&px<=water[1]&&py>=water[2]&&py<=water[3]&&pz>=water[4]&&pz<=water[5]){throw new IllegalStateException("DRY_PATH_WATER_BOUNDS");}}
            if(Math.abs(pz-previousZ)>200 || !geo.canMoveToTarget(previousX,previousY,previousZ,px,py,pz,0) || !geo.canMoveToTarget(px,py,pz,previousX,previousY,previousZ,0)){throw new IllegalStateException("DRY_PATH_NATIVE_GEOMETRY_REJECTED");}
            System.out.println("DRY_POINT\t"+px+"\t"+py+"\t"+pz);
            previousX=px; previousY=py; previousZ=pz;
        }
        System.out.println("DRY_PATH_PASS samples="+(count+1)+";waterBounds="+waters.size()+";stockGeo=true;noDB=true");
    }
}
