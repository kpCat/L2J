# Независимый review

03.10.2026. Reviewer `/root/final_native_review`, skill requesting-code-review. Read-only inspection разрешённых native source/tests/diff; собственных TEST, PLAY или source edits reviewer не выполнял. Вердикт: **BLOCKED; production candidate не готов к commit/deploy**.

1. **P1 Q01/Q05:** PhantomMaterializedPlayer cleanup дренирует admitted actions, однако Attackable.java:326 / EventDispatcher.java:151 оставляют delayed ON_ATTACKABLE_KILL вне этого счётчика. Реальный TEST подтверждает PREPARE и release ownership до native Quest writer. Complete finite drain в разрешённых seams не найден. PLAN workstream 4 требует BLOCKED при широком producer ownership design. Fixture валидна для native event/writer; exact full Q266 replay не проверен.
2. **P1 Travel:** PhantomVisibleFarmTravel.java:329–354 удаляет Journey, исключая journey_deadline из resolution. Новый вызов начинает новый budget. Native TEST воспроизводит это, однако callback в fixture лишь повторяет shape из PhantomSystem.java:456–459. Сам production composition не исполнен; raw report wording «actual production callback» завышено и уточнено здесь.
3. **P1 Grading:** extracted Get-NativeM1Grades сохраняет прежние false PASS по flags/чужому actor/FAILED cohort. Captured offline log показывает все четыре A01–A04 нарушения. Первоначальный log 0 bytes не является evidence; опубликован только корректно захваченный повторный запуск, 621 bytes, exit 1.
4. **P2 Diagnostic hook:** ACTION_DRAIN устанавливается без сброса _cleanupHook. green-incident-mage report содержит phase ACTION_DRAIN с hook AFTER_ACTION_ADMISSION. Семь E assertions не доказывают exact hook во всех фазах.
5. **P2 Ordinary restart:** finishAutoPlayRegistrationStop проверяет generation перед OfflinePlay SQL/summon effects. Concurrent start между check и effect остаётся возможен. Это static interleaving; новый ordinary regression не выполнялся.

В проверенном manager mutation code nested manager locks не обнаружены; native callouts перенесены за manager monitor. Store/abort primary Error preservation в проверенных paths соответствует intended shape. Это не exhaustive concurrency approval и не закрывает оставшиеся controls.

Рекомендация принята: остановить unsafe production integration, сохранить 13-path candidate patch (включая новый DTO), исключить SQL/config/geodata/binaries и опубликовать честный docs-only BLOCKED report. Artifact/READY gate остаётся незавершённым. Дополнительные fixes после D5 stop не выполнялись.

Дополнительный publication review подтвердил 90 staged docs paths, отсутствие
production/SQL/binaries, полноту 13-path U0 patch и точность DTO/source hashes;
privacy scan не нашёл credential/token/private-key markers. Указанный reviewer
premature-publication claim исправлен на «подготовлено» до push; фактические SHA
и push receipts записываются после исполнения. TEST/PLAY reviewer не запускал.
