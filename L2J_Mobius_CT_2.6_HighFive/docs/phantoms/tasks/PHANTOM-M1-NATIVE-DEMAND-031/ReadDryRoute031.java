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
        if(args.length==6 && args[5].equals("SCAN"))
        {
            for(int distance:new int[]{500,2000}) for(int angle=0;angle<8;angle++)
            {
                int tx=x+(int)Math.round(Math.cos(angle*Math.PI/4)*distance),ty=y+(int)Math.round(Math.sin(angle*Math.PI/4)*distance);
                int pz=geo.getHeight(x,y,z),tz=geo.getHeight(tx,ty,pz);
                var path=PathFinding.getInstance().findPath(x,y,pz,tx,ty,tz,0,true);
                System.out.println("DRY_SCAN "+tx+","+ty+","+tz+" direct="+geo.canMoveToTarget(x,y,pz,tx,ty,tz,0)+" back="+geo.canMoveToTarget(tx,ty,tz,x,y,pz,0)+" nodes="+(path==null?-1:path.size()));
            }
            return;
        }
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
        var hopPoints = new java.util.ArrayList<int[]>();
        if(args.length==6 && args[5].startsWith("VIA:"))
        {
            String via=args[5].substring(4);
            if(!via.matches("-?[0-9]+,-?[0-9]+(;(-?[0-9]+),(-?[0-9]+)){0,4}")){throw new IllegalArgumentException("DRY_VIA_FORMAT");}
            for(String point:via.split(";")){String[] xy=point.split(",");hopPoints.add(new int[]{Integer.parseInt(xy[0]),Integer.parseInt(xy[1])});}
            hopPoints.add(new int[]{endX,endY});
        }
        else
        {
            int straightHops=(int)Math.ceil(Math.hypot(endX-x,endY-y)/2000.0);
            for(int hop=1;hop<=straightHops;hop++){hopPoints.add(new int[]{x+(int)Math.round((endX-x)*(double)hop/straightHops),y+(int)Math.round((endY-y)*(double)hop/straightHops)});}
        }
        int hops=hopPoints.size();
        if(hops<1 || hops>6){throw new IllegalStateException("DRY_ROUTE_HOP_BOUND");}
        for(int hop=1;hop<=hops;hop++)
        {
            int tx=hopPoints.get(hop-1)[0], ty=hopPoints.get(hop-1)[1];
            if(Math.hypot(tx-routeX,ty-routeY)>2000.01){throw new IllegalStateException("DRY_VIA_HOP_BOUND2000");}
            int tz=geo.getHeight(tx,ty,routeZ);
            var nativePath=PathFinding.getInstance().findPath(routeX,routeY,routeZ,tx,ty,tz,0,true);
            if(nativePath==null || nativePath.isEmpty()){throw new IllegalStateException("DRY_STOCK_ROUTE_ABSENT hop="+hop+" from="+routeX+","+routeY+","+routeZ+" to="+tx+","+ty+","+tz);}
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
                if(Math.abs(pz-previousZ)>200 || !geo.canMoveToTarget(previousX,previousY,previousZ,px,py,pz,0) || !geo.canMoveToTarget(px,py,pz,previousX,previousY,previousZ,0)){throw new IllegalStateException("DRY_ROUTE_NATIVE_GEOMETRY_REJECTED from="+previousX+","+previousY+","+previousZ+" to="+px+","+py+","+pz+" forward="+geo.canMoveToTarget(previousX,previousY,previousZ,px,py,pz,0)+" reverse="+geo.canMoveToTarget(px,py,pz,previousX,previousY,previousZ,0));}
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
