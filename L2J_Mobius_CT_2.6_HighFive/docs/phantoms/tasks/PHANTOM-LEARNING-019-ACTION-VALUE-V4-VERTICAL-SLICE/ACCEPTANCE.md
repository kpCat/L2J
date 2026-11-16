# Приёмка019

`FULL019_REAL_ACTION_LEARNS_AND_SCORES` допускается **только** при реальной сквозной цепочке. Счётчик изменённых файлов/тестов не является доказательством обучения. Если один обязательный gate не прошёл — честный PARTIAL/BLOCKED с точным первым причинным blocker, а не «всё почти готово».

- **G0**: README/AGENTS, fresh HEAD/FF, existing SQL/model history safety, read-only source access; no MAIN writes.
- **G1**: хотя бы один подлинный, independently validated `TacticalReceiptV3` из 016/нового native; `sourceKind=NATIVE`, SHA/episode binding, exp/sp не требуются. Synthetic fixture => FAIL.
- **G2**: lightweight V4 scorer/trainer существует; фактический optimizer update>0 на source G1, изменённые веса/checkpoint SHA; данные не fabricated. Показывает old/new score.
- **G3**: cold reload воспроизводит candidate bytes и scores; repeat input = 0 double credit; crash/tamper cases fail-closed.
- **G4**: trained candidate может влиять на evaluation of legal alternatives в `TacticalPolicyV3` при V4 explicit TEST enable; OFF/missing/malformed/critical fallback stock; no gameplay bypass.
- **G5**: ≤1 короткий реальный GS proof, в котором V4 scorer реально вызван, native engine исполнил выбранное законное действие и зафиксировал paid effect; `MODEL_REORDERED` отмечать отдельно от `MODEL_CONSULTED`. Не заменять native proof synthetic.
- **G6**: старыe Adaм/TEST2/14/pointer12/183SQL/Geo/protected/016 historical exact preserved; нет миграции, old FIT/EVAL/prod promotion=0, TEST OFF/own0.
- **G7**: targeted Ant/PS и native укладываются в лимиты; нет 15min тестов/несанкционированных длинных прогонов. Failure respects budgets.
- **G8**: scoped source/files + compact evidence, commit/push только `feature/phantom-world` normal fast-forward, независимый GitHub HEAD/parents/diff/source full-body check и manual gate `REQUIRED`.

Успех `FULL019` **не заявляет**: наученная модель победила всех; все классы/баффы/дебаффы/танки/осады/PvP обучены; ночной trainer готов; улучшение на независимой игровой популяции доказано. Всё это следующие этапы.

Для FAIL, не скрывать что достигнуто: например `PARTIAL019_OFFLINE_LEARNED_NATIVE_UNPROVEN`, `STOP019_NATIVE_SOURCE_UNAVAILABLE`. Не писать `TRAINED_MODEL=YES`, если обучены лишь тестовые hand-assigned веса, даже если файлы различаются.

Полезный owner-facing результат (1–2 страницы): что именно из подлинной игры стало примером, `examplesNative/uniqueEpisodes/updates/modelShaBefore/After`, lawful stock vs V4 score и action, native observed effects, сохранность и возможный следующий шаг. Большие private artifacts не публиковать.
