import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;

/** Task-only read-only stock geodata/water diagnostic; never creates a Player or DB connection. */
class ReadDryRoute031
{
    private static boolean drySegment(GeoEngine geo, java.util.List<int[]> waters,int x,int y,int z,int tx,int ty)
    {
        int count=Math.max(1,(int)Math.ceil(Math.hypot(tx-x,ty-y)/100.0));
        for(int i=0;i<=count;i++)
        {
            int px=x+(int)Math.round((tx-x)*(double)i/count),py=y+(int)Math.round((ty-y)*(double)i/count);
            if(!geo.hasGeo(px,py)){return false;}z=geo.getHeight(px,py,z);
            for(var w:waters){if(px>=w[0]&&px<=w[1]&&py>=w[2]&&py<=w[3]&&z>=w[4]&&z<=w[5]){return false;}}
        }
        return true;
    }
    public static void main(String[] args) throws Exception
    {
        int x = Integer.parseInt(args[0]), y = Integer.parseInt(args[1]), z = Integer.parseInt(args[2]);
        int endX = Integer.parseInt(args[3]), endY = Integer.parseInt(args[4]);
        ConfigLoader.init();
        var geo = GeoEngine.getInstance();
        if(args.length==6 && args[5].equals("HEIGHTS"))
        {
            for(int gy=y-7000;gy<=y+7000;gy+=1000)
            {
                var row=new StringBuilder("HEIGHT y="+gy+":");
                for(int gx=x-7000;gx<=x+7000;gx+=1000){row.append(" ").append(gx).append("=").append(geo.getHeight(gx,gy,z));}
                System.out.println(row);
            }
            return;
        }
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
        if(args.length==6 && args[5].equals("GRID"))
        {
            // Diagnostic search uses original stock GeoEngine predicates for every edge, in both directions.
            // It cannot relax water/height/40-step checks below, and never touches World or SQL.
            record Cell(int x,int y,int z){}
            record Node(Cell cell,double cost,Node parent){}
            var best=new java.util.HashMap<Cell,Node>();
            var open=new java.util.PriorityQueue<Node>(java.util.Comparator.comparingDouble(n -> n.cost()+Math.hypot(endX-n.cell().x(),endY-n.cell().y())));
            var start=new Node(new Cell(x,y,routeZ),0,null);best.put(start.cell(),start);open.add(start);
            Node found=null;int expanded=0;
            while(!open.isEmpty() && expanded++<60000)
            {
                Node current=open.poll();if(best.get(current.cell())!=current){continue;}
                var c=current.cell();int goalZ=geo.getHeight(endX,endY,c.z());
                if(drySegment(geo,waters,c.x(),c.y(),c.z(),endX,endY) && Math.hypot(endX-c.x(),endY-c.y())<=240 && Math.abs(goalZ-c.z())<=200 && geo.canMoveToTarget(c.x(),c.y(),c.z(),endX,endY,goalZ,0) && geo.canMoveToTarget(endX,endY,goalZ,c.x(),c.y(),c.z(),0))
                {found=new Node(new Cell(endX,endY,goalZ),current.cost()+Math.hypot(endX-c.x(),endY-c.y()),current);break;}
                for(int stride:new int[]{224,128,64,16})for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)
                {
                    if(dx==0&&dy==0){continue;}int nx=c.x()+dx*stride,ny=c.y()+dy*stride;
                    if(nx<Math.min(x,endX)-1600||nx>Math.max(x,endX)+1600||ny<Math.min(y,endY)-1600||ny>Math.max(y,endY)+1600||!geo.hasGeo(nx,ny)){continue;}
                    int nz=geo.getHeight(nx,ny,c.z());boolean water=false;
                    for(var w:waters){if(nx>=w[0]&&nx<=w[1]&&ny>=w[2]&&ny<=w[3]&&nz>=w[4]&&nz<=w[5]){water=true;break;}}
                    if(water||!drySegment(geo,waters,c.x(),c.y(),c.z(),nx,ny)||Math.abs(nz-c.z())>200||!geo.canMoveToTarget(c.x(),c.y(),c.z(),nx,ny,nz,0)||!geo.canMoveToTarget(nx,ny,nz,c.x(),c.y(),c.z(),0)){continue;}
                    var cell=new Cell(nx,ny,nz);double cost=current.cost()+Math.hypot(dx*stride,dy*stride);var prior=best.get(cell);
                    if(prior!=null&&prior.cost()<=cost){continue;}var node=new Node(cell,cost,current);best.put(cell,node);open.add(node);
                }
            }
            if(found==null){throw new IllegalStateException("DRY_BIDIRECTIONAL_SEARCH_ABSENT expanded="+expanded);}
            var grid=new java.util.ArrayList<Cell>();for(Node node=found;node!=null;node=node.parent()){grid.add(node.cell());}java.util.Collections.reverse(grid);
            for(int at=0;at<grid.size()-1;)
            {
                var from=grid.get(at);int next=at+1;
                for(int candidate=at+1;candidate<grid.size();candidate++)
                {
                    var to=grid.get(candidate);if(Math.hypot(to.x()-from.x(),to.y()-from.y())>300){break;}
                    if(Math.abs(to.z()-from.z())<=200&&geo.canMoveToTarget(from.x(),from.y(),from.z(),to.x(),to.y(),to.z(),0)&&geo.canMoveToTarget(to.x(),to.y(),to.z(),from.x(),from.y(),from.z(),0)){next=candidate;}
                }
                var point=grid.get(next);routePoints.add(new int[]{point.x(),point.y(),point.z()});at=next;
            }
            System.out.println("DRY_SEARCH expanded="+expanded+" points="+routePoints.size()+" stockBidirectional=true");
        }
        else
        {
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
        }
        int previousX=x, previousY=y, previousZ=geo.getHeight(x,y,z), emitted=0, count=0;
        System.out.println("DRY_POINT\t"+previousX+"\t"+previousY+"\t"+previousZ);
        for(var destination:routePoints)
        {
            int sx=previousX,sy=previousY;
            int legs=(int)Math.ceil(Math.hypot(destination[0]-sx,destination[1]-sy)/300.0);
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
