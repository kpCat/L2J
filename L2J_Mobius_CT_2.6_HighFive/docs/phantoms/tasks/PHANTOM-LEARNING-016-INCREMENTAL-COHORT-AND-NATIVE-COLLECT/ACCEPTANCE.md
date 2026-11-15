# Критерии G0–G8 и определения результата

| Gate | Условие FULL016 | Если не выполнено |
|---|---|---|
| G0 — Preservation | Fresh Git/source/TEST owner, cold 5SELECT historical rows/pointer/2&14+Adam, backups, protected207, Geo203, clean own processes | BLOCKED до любых активных операций |
| G1 — Root fix | Установлен и проверен корректный `episodesPerBatch=51`, остановка на трёх пригодных cells, TRAIN_ONLY. 015 batch84 не «переоформлен» | PARTIAL |
| G2 — Incremental durability | Новые pair receipts действительно атомарны, stop/resume до/после A/B/marker и неизменность fair selection/negative history доказаны focused tests | PARTIAL |
| G3 — Legal sealed cohort | Минимум 3 **новые** полностью paired TRAIN pre-CUT cells, 2classes, Solo+Party, actual learned ≥2 supported skills, валидный linked sealed manifest/`cells.tsv`/admission и физическая сопоставимость, свежая проверка prior to live | FAIL; GS запрещён |
| G4 — Correct native lifecycle | Отсутствует новая EVAL/probe ветка; native owner/recheck/limited GameServer permitted; all live actions lawful; STOP/restart safe | PARTIAL |
| G5 — Genuine gameplay | В TEST после G3 есть **реальный новый TRAIN CUT с ненулевыми native paid effects и достоверным исходом**, включая смерть; terminal receipts и credits точны | FAIL, даже если G3 PASS |
| G6 — Learning preservation | Сохранились selected/terminal и best TEST, раздельность TRAIN/TUNING/AUDIT; если достаточные данные есть — 1 законный FIT и cold restore; если нет — правдивый NO_FIT_INSUFFICIENT; promotion 0 | FAIL при ложном FIT, повторном credit или потере истории |
| G7 — Cost/regression | ≤3 headless/30wall минут + ≤3GS/75native минут, offline RED/GREEN, targeted Ant/JAR/PS, no unowned kill, correct historical frozen scope | FAIL |
| G8 — Delivery | GitHub feature ordinary scoped commits/push/readback, исходники/evidence/план/G0–G8, manual gate REQUIRED | FAIL |

**Статус FULL016_INCREMENTAL_NATIVE_COLLECT** означает *только* полноценную накопительную подготовку и минимум один настоящий учебный native эпизод со строгим восстановлением. Он **НЕ** означает, что завершены все 51 native episodes, что FIT обязательно был возможен или что модель стала сильнее. Отдельно писать `TRAINED_MODEL=YES/NO`, `GAME_ADVANTAGE=NO_EVAL`, `PRODUCTION=OFF`.

Если G3 получена, но G5 не получена — `PARTIAL016_SEALED_NO_NATIVE`; если G3 нет — `PARTIAL016_UNSEALED`; если G5 есть, но crash/credit safety провалена — `BLOCKED016_UNSAFE`, не «зелёная» приёмка. Owner manual gate **REQUIRED**, Codex его самостоятельно не закрывает.

**Про бюджет:** это задание №10/13, осталось 3. Retry/суффикс R1 не сбрасывает счётчик. Финальные 13 — плановый предел до обязательного пересмотра, не обещание полной реализации всех PvP/Raid/UD/Party abilities.
