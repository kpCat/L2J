# Для владельца — что изменится после corrective

Нынешний Goal022 уже доказал два native-эпизода и 188→206→216, но старый `continuation022-v2.json` мешает следующему Start. Corrective должен открыть **новую отдельную** TEST-кампанию, не разрешая повтор старой V2.

Оба switch доступны в обычном `Training.ps1`. Подготовка проверяет настоящий committed ledger, cold seal, native source и curriculum. Она создаёт отдельную кампанию только в указанной private копии, затем возвращает `NOT_STARTED` до broker. Используйте заранее подготовленную metadata-копию, не исходный исторический TEST:

```powershell
# СТРОГО offline preview на тестовой копии, никогда не на историческом MAIN:
.\tools\phantom-runtime\training\Training.ps1 -Action ActionValue022 -ActionValue022Command Start -ActionValue022NewCampaign -ActionValue022PrepareOnly -ActionValue022Minutes 480 -ActionValue022BootLimit 2 -Root $privateCopyHistoryRoot -Json
```

Без `PrepareOnly` — **реальный запуск**, в corrective не выполнять. Перед будущим восьмичасовым пробным запуском нужна отдельная ручная приёмка владельца и учёт многократных GameServer boots (пока один boot на эпизод; возможны большие накладные расходы). Контекст только Solo/DAMAGE, production OFF, SUPERIORITY=NOT_EVALUATED; никакого автоматического обновления production AI.

Будущая команда после отдельного решения владельца: та же команда с `-ActionValue022NewCampaign`, явно выбранными минутами и boot limit, но без `-ActionValue022PrepareOnly`. В этой работе она не выполнялась. Два boots — ограниченный пример; это не обещание непрерывных 480 минут и не разрешение на запуск.

Без `NewCampaign` завершённая V2 получает REJECT. Повторный Start активной или уже подготовленной кампании сохраняет ID/config/deadline. Pause/Stop сохраняют срок; Resume не продлевает его и отказывает при исчерпанном бюджете. V2 authority/claim и три исторических receipt неизменяемы.

`TRAIN022_ADMISSION_INCOMPLETE` означает сохранённую незавершённую подготовку: прежний active pointer остаётся доступным. Ничего не удалять и не переписывать автоматически; сначала изучить admission receipt и новый private root. Отсутствующий или подменённый cursor/HEAD/source не восстанавливается через fallback0/2.
