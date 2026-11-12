# RUNBOOK_RU — что передать владельцу после разработки

В 005 Codex реализует и в собственном тестовом clone демонстрирует команды. **Не предлагать несуществующие команды как уже реализованные**; точный синтаксис/пути записать в итоговом отчёте.

Оператору должны быть доступны из текущего независимого clone:
- Запуск bounded paired **evaluate** с `SOLO_DAMAGE_V1` или `PARTY_HEAL_V3` и сохранённым `campaignId`, seed/объём; без изменения MAIN/PLAY.
- Status: `pairsPlanned/pairsCompleted/fairPairs/incomplete/unmatched`, каждой модели pinned version/SHA, текущий этап, budget, причины ожидания.
- Pause/Stop по штатным границам, cold Resume без повторного зачёта.
- Report на русском: реальная таблица A/B, метрики и неопределённость; Export redacted JSON/TSV/Markdown с hashes для независимой проверки.

Если расширение существующего `Training.ps1` на enum action `Evaluate` существенно затрагивает G0/Job и длительно, используйте **отдельный minimal TEST-only entrypoint вокруг существующих owned native launchers**, не второй supervisor/SQL/game. Названия/параметры установить после чтения source. Демонстрация в Codex — автоматическая, **проверка TRAIN_*.cmd пользователем отложена**.

План долгосрочных прогонов: `N` завершённых fair pairs каждую ночь на одних campaign/seed policy pins; после STOP продолжить с сохранённых идентичностей и новых независимых actor/spawn в следующий сеанс, сохранить статистику и историю; один `--resume` не должен стирать старые paired results. При изменении game JAR/models/scenario schema новая campaign или явное versioned reset — нельзя сшивать разные условия в один рейтинг.
