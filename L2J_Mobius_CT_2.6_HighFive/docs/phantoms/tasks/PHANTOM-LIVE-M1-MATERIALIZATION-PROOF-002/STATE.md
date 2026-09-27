# STATE — PHANTOM-LIVE-M1-MATERIALIZATION-PROOF-002

Status: **BLOCKED — M1_CONNECTED_MATERIALIZATION_ENVELOPE_UNPROVEN**.

Финальный deployed code SHA `686761a73f1abe9a0d06821d98e18009f0ccad6c`, GameServer.jar SHA-256 `AFCC66D70E36AF93DED1AAC5DAE04BECF48C85E60F90C0815DB427062ADE3AC4`. Лимиты `10000/64/128/100` сохранены, LocalPlay `CONFIG PASS`/RUNNING. Два USER_CLIENT_ACTION использованы, третье не запрашивать. PLAY DB SELECT/SHOW only.

Материализация при `human.local=true` не наступила для natural profiles 59 и 450 до native visibility boundary; второй prewarm наблюдался около 67 секунд. Точный scheduler bottleneck не установлен. Видимый проход, grace, дематериализация и re-entry не доказаны. Дополнительный Pilot origin alias мешает вернуть TestAdmin к исходной точке: он оставлен на безопасном внешнем якоре `(45085,42001,-3491)`, REAL_LOGIN/online/worldPresent, Pilot idle. Продолжение требует отдельного решения о новом arm gate; этот task его не начинает. M2 не начинать.

Read-only продолжение: два JVM thread dump показали historical ecology DB transaction на scheduler control path до ready-slot processing; глубина очереди и причинный вклад ещё не измерены. Поздний snapshot profile 450 оставался `STORED`, после выхода из prewarm его `presenceReason` стал `offline`. Новых client actions и restart не было.

Подробности: `EVIDENCE.md`, `RESULT.md`, `MATERIALIZATION_ATTEMPT_1.tsv`, `MATERIALIZATION_CONNECTED_PROOF.tsv`, GeoEngine proof TSV.
