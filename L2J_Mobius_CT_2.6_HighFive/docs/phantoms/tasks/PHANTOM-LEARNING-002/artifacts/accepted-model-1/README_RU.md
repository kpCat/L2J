# Принятая модель собственного TEST

`model-1.zip` — побайтовая копия финального штатного TRAIN_EXPORT после clean STOP
и холодного чтения собственной MariaDB, 2026-11-11T14:31:46Z. Исходный файл:
`model-1-0f302f513b1a4fc785d74ba54ce23a01.zip`. Экспортный smoke завершился с кодом0.

- ZIP: 69127 байт; SHA256 `10fc9c6c8c450e4974ca7abed861805a7640e2c67c8474ac5a4c96e003b493da`.
- Модель: версия1, алгоритм2, 12500 байт;
  SHA256 `e0691f728847cebea7293d87e45acebbd3db5bc2d71b76c7c5e40549e13c43d2`.
- Состав ZIP: `weights.bin`, `manifest.json`, `dataset.jsonl`, `episodes.jsonl`.
- История на момент экспорта: 34 COMMITTED / 35 INCOMPLETE, 117 уникальных NATIVE
  событий. INCOMPLETE не имеет учебного credit. Frozen dataset принятой модели — 87 строк.
- Данные только собственного disposable TEST; manifest содержит `origin=NATIVE`,
  `status=ACCEPTED_TEST`, `productionPromotion=false`, schema/catalog/dataset SHA.
- TRAIN33 / TUNING45 / независимый AUDIT9; отдельные Solo/Party проверки качества
  пройдены. Это малый аудит, а не доказательство общего улучшения DPS или PvP.

Холодный штатный GameServer загрузил ту же версию и SHA. Наблюдались реальные
перестановки 1230:28 → 1339:1 с точным HP evidence в Solo и Party. Полные сведения
и ограничения — в основном отчёте PHANTOM-LEARNING-002. Автоматической установки
в пользовательскую игру нет; архив не содержит SQL credentials.

Более ранний экспорт запроса `a8b95d6158144be0bb4c94fad125c565` тоже был успешен.
Его исходник сохранён в локальном TEST; перед обновлением этой публичной копии
сделана побайтовая SHA-проверенная копия вне checkout. Здесь доставляется один финальный ZIP.
