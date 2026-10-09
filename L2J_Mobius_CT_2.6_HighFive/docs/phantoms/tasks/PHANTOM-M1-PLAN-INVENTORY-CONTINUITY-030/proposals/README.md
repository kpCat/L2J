# Вспомогательный code030

`audit_lifetimes030.py` — потоковый read-only **структурный** аудит нормализованных
REGISTER/PREPARED/FINALIZED/TERMINAL JSONL. История хранится в SQLite в рабочей папке
аудита, не в MariaDB игры. По умолчанию CLI создаёт новый output и новый .sqlite;
перезапись запрещена. DB-файл не включать в repository/package.

Схему входа показывают тесты. RunKey обязан включать реальную incarnation в producer.
RUN_END.native_births берётся из независимого счётчика actual native birth callback,
не вычисляется из уже сохранённого REGISTER. Иначе проверка покрытия тавтологична.
Для новых типов terminal-before-write или background/projection graph используйте
существующий raw native event model и отдельный validator; не маскируйте их этим helper.
Нельзя выдавать coverage_pass за Whole Save, native parity или M1 PASS.

Применение:
```
python -m unittest -v test_audit_lifetimes030.py
python audit_lifetimes030.py normalized-native-events.jsonl --output ledger-audit.json
```

Положительные и отрицательные тесты: больше32профилей/128эпох; exactduplicate; conflicting
receipt; cross-JVM namespace; missingbirth; temporaryseal; pending receipt; lateevents.
Эти тесты проверяют helper, не runtime Mobius. Обязательные composed P/E/R из SCENARIOS
выполняет Codex на actual source/server. Никаких fabricated native witnesses.
