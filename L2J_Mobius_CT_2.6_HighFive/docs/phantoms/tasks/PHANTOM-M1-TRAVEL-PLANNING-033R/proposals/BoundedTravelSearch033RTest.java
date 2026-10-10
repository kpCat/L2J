import java.util.*;
import static java.util.List.of;

public final class BoundedTravelSearch033RTest {
    static int checks;
    static void check(boolean ok, String text) { checks++; if (!ok) throw new AssertionError(text); }
    static BoundedTravelSearch033R.Node n(String id, int x, int y, int z) {
        return new BoundedTravelSearch033R.Node(id, 0, x, y, z);
    }
    static BoundedTravelSearch033R.Edge e(String id, BoundedTravelSearch033R.Node a,
        BoundedTravelSearch033R.Node b, long ms, long fee, int scrolls, boolean verified,
        BoundedTravelSearch033R.Mode mode) {
        return new BoundedTravelSearch033R.Edge(id,a,b,ms,fee,scrolls,verified,mode);
    }
    static BoundedTravelSearch033R.Result search(List<BoundedTravelSearch033R.Edge> edges,
        BoundedTravelSearch033R.Node a, BoundedTravelSearch033R.Node b, long adena, int scrolls) {
        return BoundedTravelSearch033R.find(edges,a,b,adena,scrolls,256,()->false);
    }
    public static void main(String[] args) {
        var walk=BoundedTravelSearch033R.Mode.WALK;
        var gk=BoundedTravelSearch033R.Mode.GATEKEEPER;
        var back=BoundedTravelSearch033R.Mode.RETURN_TOWN;
        var s=n("start",0,0,0); var away=n("away",-10,0,0); var top=n("pass",-10,10,0); var g=n("farm",10,0,0);
        var detour=of(e("blocked-direct",s,g,1,0,0,false,walk),e("out",s,away,10,0,0,true,walk),e("turn",away,top,10,0,0,true,walk),e("in",top,g,10,0,0,true,walk));
        var r=search(detour,s,g,0,0);
        check(r.status()==BoundedTravelSearch033R.Status.FOUND,"Обход U-препятствия обязан допускать первый шаг ОТ цели");
        check(r.edges().size()==3 && r.millis()==30,"Выбран весь доказанный обход, не прямой луч");
        check(r.edges().stream().noneMatch(x->x.id().equals("blocked-direct")),"Непроверенное ребро исключено");
        check(search(detour,g,s,0,0).status()==BoundedTravelSearch033R.Status.NO_ROUTE_IN_GRAPH,"Обратный путь не выводится из прямого");
        var upstairs=n("above",0,0,200);
        check(search(detour,s,upstairs,0,0).status()==BoundedTravelSearch033R.Status.NO_ROUTE_IN_GRAPH,"Одинаковые XY не склеивают этажи");
        var town=n("town",50,50,0);
        var choices=of(e("walk",s,g,100,0,0,true,walk), e("scroll",s,town,10,0,1,true,back),e("gk",town,g,10,50,0,true,gk));
        check(search(choices,s,g,50,1).millis()==20,"Доступный return+GK может быть быстрее пешего пути");
        check(search(choices,s,g,49,1).millis()==100,"Недостаток adena сохраняет пешую альтернативу");
        check(search(choices,s,g,50,0).millis()==100,"Нет scroll — маршрут с расходом scroll недоступен");
        var toll=n("toll",4,0,0);
        var fees=of(e("fast-spend",s,toll,1,10,0,true,walk),e("slow-save",s,toll,2,0,0,true,walk),e("need-fee",toll,g,1,10,0,true,gk));
        r=search(fees,s,g,10,0);
        check(r.status()==BoundedTravelSearch033R.Status.FOUND && r.millis()==3,"Более медленная, но дешёвая метка не отбрасывается");
        check(r.adena()==10,"Суммируется цена всего плана, а не каждого ребра отдельно");
        var cyclic=new ArrayList<>(detour); cyclic.add(e("cycle",away,s,2,0,0,true,walk));
        check(search(cyclic,s,g,0,0).millis()==30,"Цикл не создаёт бесконечный поиск");
        check(BoundedTravelSearch033R.find(detour,s,g,0,0,1,()->false).status()==BoundedTravelSearch033R.Status.SEARCH_LIMIT,"Лимит поиска не маскируется под недостижимость карты");
        check(BoundedTravelSearch033R.find(detour,s,g,0,0,256,()->true).status()==BoundedTravelSearch033R.Status.CANCELLED,"Отмена не отдаёт частичный маршрут");
        check(search(of(),s,s,0,0).edges().isEmpty(),"Уже в конечном узле — пустой путь");
        try { r.edges().clear(); throw new AssertionError("Список маршрута должен быть immutable"); }
        catch (UnsupportedOperationException expected) { checks++; }
        System.out.println("REFERENCE_CHECKS_PASS="+checks);
    }
}
