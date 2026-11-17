# Safety / отмена

- Запрещены **любой** GameServer/SQL native/start в corrective, изменение `TRAIN_CONFIG.ini` старых private roots, старых checkpoints/cold-seals, `continuation022-v2.json` или `.claim.json`, `protected207`, Geo203, MAIN/PLAY, shared DB, пользовательских QoL/INI, V3 и PhantomSemanticStudio.
- Не трактовать моё согласие «да давай» как разрешение реально запустить 8-часовое обучение. Это разрешение создать corrective task и в будущем готовить отдельную TEST-кампанию. Её runtime требует отдельного решения владельца.
- На любой неизвестной identity/process, конфликте checkout, отсутствии physical 216 or sealed source, рассогласованном cursor, недоказанных writer-set/restart semantics — STOP с явной диагностикой. Не делать hard reset, clean, stash pop/drop, force-push, массовую замену веток или удаление копий.
- Вся mutating offline демонстрация работает только на собственной **изолированной private TEST fixture/copy**. Папки GAME, SQL, previous active user TEST считать READ-ONLY. Проверки текущего lineage — только чтение+SHA. Не публиковать private raw/SQL, пароли, nonces, JAR, weights.
- Новый session budget не даёт права обнулить исторические 3 boots V2; запрет четвёртого boot именно V2 должен оставаться проверяемым. Нельзя создавать новые bootstrap claim в основном TEST под видом тестов.
- При неудачной prepare-only публикации/невыполнимой atomic active pointer смене не удалять неполный каталог; оставить evidence и прежний pointer, ручной recovery. Жизнь истории важнее зелёного статуса.
- Разрешено ограниченное scoped commit/push в `feature/phantom-world` после убедительных offline тестов, без других путей. При BLOCKED опубликовать только безопасный код/тест/отчёт, внятно назвать gate, который не закрыт.
