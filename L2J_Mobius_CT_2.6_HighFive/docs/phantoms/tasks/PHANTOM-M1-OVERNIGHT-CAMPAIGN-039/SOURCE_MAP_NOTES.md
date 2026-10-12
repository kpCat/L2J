# Как читать SOURCE_MAP

Все исходники относятся к BASE8bf4df23. PIN_BASE_PATH означает: до изменения зафиксировать
`git rev-parse BASE:module/path` и фактический source hash в OWN. Это не выдуманный известный
blob. Координатор прочитал основные action, transaction, verifier и owned-service paths;
условные downstream-файлы исполнитель обязан прочитать перед правкой.

Conditional permission — право на связанное исправление, не требование менять каждый файл.
Сначала минимальный producer. Не более16 production файлов, включая один optional новый
immutable helper. Вне карты — только чтение. Если необходимая правка вне согласованной
области, вернуть BLOCKED_SAFE_SCOPE для этой полосы; другую независимую полосу можно закончить.
Нельзя выпускать новый native root без owner guard ради обхода границы.

Контракты035/036 защищены. NativeEvidence observer permission не позволяет сбрасывать
sticky errors или менять счётчики. Transaction observer permission не позволяет менять
SQL mutation/lock order/rewards/replay/commit policy. Предпочитать nested immutable result
или существующий observer, а не перестраивать public API. Shared ThreadPool, Player,
Creature, Party, EventDispatcher не входят в write scope.

Новые проверки можно добавить в suite039 вместо изменения старых assertions. Native guard
039t добавляется адресно; глобальные настройки не подменяют проверку. Не копировать старые
blob hashes038 для файлов, которые изменились в038: BASE фиксирует нынешнюю версию.
