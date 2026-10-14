# DESIGN043 — безопасная граница ACTIVE_ACTION и soft-away

## Реализуем только нужное

Повторно использовать существующие `PhantomHumanLocalityControl` → `PhantomMaterializationRetentionPolicy` → текущие AutoPlay/AutoUse policy → `PhantomMaterializationService` → `PhantomHistoricalBackgroundService`/existing background transaction. Никаких отдельных менеджеров, новых таймеров с принудительным reset, fork `Player/CreatureAI`, искусственной смерти или teleport-away.

## Классификация и выбор предпосылок до результата

Сначала B2 causal reconstruction с immutable evidence: actual departure, native last acquired root, out/in attack tickets, active flags, remaining HP, hard/soft reasons, earned outstanding, player AI intention, native owner/epoch, clean disappearance/death or absence. Реконструкция имеет конечный результат (без сотни снимков). Если логи не содержат нужного поля, один ограниченный current native наблюдатель на существующем optional seam; не повышать ring/cap/disk.

Предварительная допустимость G4: живой ordinary profile с подходящим background goal, настоящей native работой уже выполненной/committed; корректный same-locality native observer; отсутствие REAL_PARTY/NATIVE_VISIBLE/других действительно hard holds. Прямо перед **заранее назначенным** уходом документировать current native attack/cast ownership и внешнее агро (не выбирать после исхода «удачного» персонажа). Разрешён законный момент окончания текущего боя после зафиксированного earned work, если критерий выбран до измерения; запрещено подавлять спавн/агро/AI/HP/скорости ради него.

После soft departure никаких новых атакующих root без locality, но уже заработанные callbacks обязаны дойти до native terminal/commit. Если входящие атаки продолжаются и удержание законно, не отпускать персонажа принудительно. Записать как **negative case**; перейти к заранее предопределённой другой естественной допустимой сцене только если исходная была объективно invalid для продуктивного перехода по опубликованному ДО неё contract — не подбирать after-the-fact успешную личность. Повтор G4 разрешён только после разделяющей гипотезы, устранения подтверждённого дефекта или предопределённой смены входного класса (не более 2 полных positive attempts).

## Передача владения

После истечения RECENT_HUMAN без hard reason: existing safe soft retire, финализация всех earned/callbacks native, единственный owning JDBC commit FULL fields/items/skills/PNC, отсутствие World/native owner, настоящий background producer operation за actual elapsed time. В фон нельзя записывать, пока live owner держит запись. При законной смерти сохранить actual earned/loss и существующий recovery путь; same stable profile обязан снова совершить useful native work.

Reentry: native materialization по существующему виду спроса, тот же profile, законная новая object/epoch identity, actual native damage/cycles. Проверить, что background interval не применён дважды и ни один real reward не потерян. Независимые mini-negatives: active hard hold нельзя обойти, stale epoch/goal не может принять старого писателя; background без actual producer не засчитывается.

## Только при доказанном дефекте

Разрешён точечный fix в existing owner. Пример допустимого дефекта: после потери locality продолжается admission нового автономного атакующего root вопреки `permitsNewActions=false`; или после завершения боя/earned outstanding0 retention не освободился вследствие забытого active flag. Выбрать реальный source location, написать focused RED на старом Jar, исправить минимально, GREEN и два негативных теста, затем реальный native LIFE. При исправлении source, влияющего на combat/retention, повторить обе G2 natural группы на **том же новом финальном JAR**; старые PASS остаются историческими, но не финальными.

## Недопустимые быстрые решения

`force dematerialize`, `abortAttack/abortCast` без доказанной безопасной earned boundary, обнуление native tickets, списание EXP, forced AI reset для ухода, kill process, reset БД, изменение120/90/45/525, fake REAL_LOGIN, новые локации hardcode, кастомный NPC/класс, новые библиотеки, новый consumer/producer protocol, расширение доказательных очередей. Исходный `CONTINUITY_V2` без правок.
