import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Проверяемый пример ВЫБОРА плана по готовым доказанным рёбрам.
 * Не читает GeoEngine, не строит mesh, не двигает Player и не заменяет stock A*.
 * verified — контракт входа примера; в интеграции это реальное доказательство
 * native-геометрии/доступности транспорта, а не флаг, выставленный для PASS.
 */
public final class BoundedTravelSearch033R {
    private BoundedTravelSearch033R() {}
    public enum Mode { WALK, GATEKEEPER, RETURN_TOWN }
    public enum Status { FOUND, NO_ROUTE_IN_GRAPH, SEARCH_LIMIT, CANCELLED }
    public record Node(String id, int instanceId, int x, int y, int z) {
        public Node { if (id==null || id.isBlank() || instanceId<0) throw new IllegalArgumentException("node"); }
    }
    public record Edge(String id, Node from, Node to, long millis, long adena,
                       int scrolls, boolean verified, Mode mode) {
        public Edge {
            Objects.requireNonNull(from); Objects.requireNonNull(to); Objects.requireNonNull(mode);
            if(id==null || id.isBlank() || millis<=0 || adena<0 || scrolls<0)
                throw new IllegalArgumentException("edge");
        }
    }
    public record Result(Status status, List<Edge> edges, long millis, long adena, int scrolls) {
        public Result { edges=List.copyOf(edges); }
    }
    private record Label(Node node,long time,long money,int scrolls,Label parent,Edge incoming,long order) {}
    private static Result empty(Status status) { return new Result(status,List.of(),0,0,0); }
    private static boolean dominates(Label a, Label b) {
        return a.time<=b.time && a.money<=b.money && a.scrolls<=b.scrolls;
    }
    /**
     * Dijkstra с несколькими ресурсными метками: самый быстрый путь до узла
     * может потратить деньги, нужные дальше. Такой путь не вытесняет более
     * медленный, но дешёвый. budget ограничивает ВСЕ созданные метки.
     * NO_ROUTE_IN_GRAPH означает только отсутствие пути в ПЕРЕДАННОМ графе.
     */
    public static Result find(List<Edge> input,Node start,Node goal,long adena,int scrolls,
                              int budget,BooleanSupplier cancelled) {
        Objects.requireNonNull(input); Objects.requireNonNull(start); Objects.requireNonNull(goal);
        Objects.requireNonNull(cancelled);
        if(adena<0 || scrolls<0 || budget<1 || input.size()>4096) throw new IllegalArgumentException("bounds");
        if(cancelled.getAsBoolean()) return empty(Status.CANCELLED);
        if(start.equals(goal)) return empty(Status.FOUND);
        Map<Node,List<Edge>> graph=new HashMap<>(); Set<String> ids=new HashSet<>();
        for(Edge e:input) {
            Objects.requireNonNull(e);
            if(!ids.add(e.id)) throw new IllegalArgumentException("duplicate edge");
            if(e.verified) graph.computeIfAbsent(e.from,k->new ArrayList<>()).add(e);
        }
        graph.values().forEach(v->v.sort(Comparator.comparing(Edge::id)));
        var open=new PriorityQueue<Label>(Comparator.comparingLong(Label::time)
            .thenComparingLong(Label::money).thenComparingInt(Label::scrolls).thenComparingLong(Label::order));
        Map<Node,List<Label>> frontiers=new HashMap<>();
        Label first=new Label(start,0,0,0,null,null,0);
        frontiers.computeIfAbsent(start,k->new ArrayList<>()).add(first); open.add(first);
        int generated=1; long order=1;
        while(!open.isEmpty()) {
            if(cancelled.getAsBoolean()) return empty(Status.CANCELLED);
            Label current=open.remove();
            if(!frontiers.getOrDefault(current.node,List.of()).contains(current)) continue;
            if(current.node.equals(goal)) {
                List<Edge> path=new ArrayList<>();
                for(Label p=current;p.incoming!=null;p=p.parent) path.add(p.incoming);
                Collections.reverse(path);
                return new Result(Status.FOUND,path,current.time,current.money,current.scrolls);
            }
            for(Edge e:graph.getOrDefault(current.node,List.of())) {
                if(cancelled.getAsBoolean()) return empty(Status.CANCELLED);
                // Вычитание избегает overflow накопленных цен.
                if(e.adena>adena-current.money || e.scrolls>scrolls-current.scrolls) continue;
                if(e.millis>Long.MAX_VALUE-current.time) throw new IllegalArgumentException("duration overflow");
                Label next=new Label(e.to,current.time+e.millis,current.money+e.adena,
                    current.scrolls+e.scrolls,current,e,order++);
                List<Label> frontier=frontiers.computeIfAbsent(e.to,k->new ArrayList<>());
                if(frontier.stream().anyMatch(old->dominates(old,next))) continue;
                if(generated>=budget) return empty(Status.SEARCH_LIMIT);
                generated++;
                frontier.removeIf(old->dominates(next,old)); frontier.add(next); open.add(next);
            }
        }
        return empty(Status.NO_ROUTE_IN_GRAPH);
    }
}
