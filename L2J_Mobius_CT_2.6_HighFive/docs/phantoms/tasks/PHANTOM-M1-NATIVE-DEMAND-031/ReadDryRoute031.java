import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;

/** Task-only read-only stock geodata/water diagnostic; never creates a Player or DB connection. */
class ReadDryRoute031
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
        // Native buffer500 needs bounded hops. Every emitted step is checked both ways.
        var routePoints = new java.util.ArrayList<int[]>();
        int routeX=x, routeY=y, routeZ=geo.getHeight(x,y,z);
        int hops=(int)Math.ceil(Math.hypot(endX-x,endY-y)/2000.0);
        if(hops<1 || hops>6){throw new IllegalStateException("DRY_ROUTE_HOP_BOUND");}
        for(int hop=1;hop<=hops;hop++)
        {
            int tx=x+(int)Math.round((endX-x)*(double)hop/hops), ty=y+(int)Math.round((endY-y)*(double)hop/hops);
            int tz=geo.getHeight(tx,ty,routeZ);
            var nativePath=PathFinding.getInstance().findPath(routeX,routeY,routeZ,tx,ty,tz,0,true);
            if(nativePath==null || nativePath.isEmpty()){throw new IllegalStateException("DRY_STOCK_ROUTE_ABSENT");}
            for(var node:nativePath){routePoints.add(new int[]{node.getX(),node.getY(),node.getZ()});}
            routePoints.add(new int[]{tx,ty,tz}); routeX=tx;routeY=ty;routeZ=tz;
        }
        int previousX=x, previousY=y, previousZ=geo.getHeight(x,y,z), emitted=0, count=0;
        System.out.println("DRY_POINT\t"+previousX+"\t"+previousY+"\t"+previousZ);
        for(var destination:routePoints)
        {
            int sx=previousX,sy=previousY;
            int legs=(int)Math.ceil(Math.hypot(destination[0]-sx,destination[1]-sy)/250.0);
            for(int leg=1;leg<=legs;leg++)
            {
                int px=sx+(int)Math.round((destination[0]-sx)*(double)leg/legs),py=sy+(int)Math.round((destination[1]-sy)*(double)leg/legs);
                int pz=geo.getHeight(px,py,previousZ);
                if(Math.abs(pz-previousZ)>200 || !geo.canMoveToTarget(previousX,previousY,previousZ,px,py,pz,0) || !geo.canMoveToTarget(px,py,pz,previousX,previousY,previousZ,0)){throw new IllegalStateException("DRY_ROUTE_NATIVE_GEOMETRY_REJECTED");}
                int samples=Math.max(1,(int)Math.ceil(Math.hypot(px-previousX,py-previousY)/100.0)),sampleZ=previousZ;
                for(int sample=0;sample<=samples;sample++)
                {
                    int ax=previousX+(int)Math.round((px-previousX)*(double)sample/samples),ay=previousY+(int)Math.round((py-previousY)*(double)sample/samples);
                    if(!geo.hasGeo(ax,ay)){throw new IllegalStateException("DRY_PATH_MISSING_GEO");}
                    sampleZ=geo.getHeight(ax,ay,sampleZ);
                    for(var water:waters){if(ax>=water[0]&&ax<=water[1]&&ay>=water[2]&&ay<=water[3]&&sampleZ>=water[4]&&sampleZ<=water[5]){throw new IllegalStateException("DRY_PATH_WATER_BOUNDS");}}
                    count++;
                }
                if(++emitted>40){throw new IllegalStateException("DRY_ROUTE_STEP_BOUND40");}
                System.out.println("DRY_POINT\t"+px+"\t"+py+"\t"+pz);
                previousX=px;previousY=py;previousZ=pz;
            }
        }
        if(previousX!=endX || previousY!=endY){throw new IllegalStateException("DRY_ROUTE_ENDPOINT_UNPROVEN");}
        System.out.println("DRY_PATH_PASS samples="+(count+1)+";waterBounds="+waters.size()+";stockGeo=true;noDB=true");
    }
}
