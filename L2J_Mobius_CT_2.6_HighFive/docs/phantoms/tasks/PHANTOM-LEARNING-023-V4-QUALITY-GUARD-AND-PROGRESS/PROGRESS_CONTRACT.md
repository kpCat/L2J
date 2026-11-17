# Простой способ следить за результатом обучения

Владелец хочет **один файл, который можно открыть двойным щелчком**. Пример: `<private-owned-train022>/campaigns/<active>/report022.html`; отдельный JSON и MD для автоматики. Генерация на безопасной границе batch/status, HTML полностью офлайн, простая таблица/плашки без внешних CDN/скриптов; безопасное HTML escaping.

Показывать одной строкой решение:
- `Нужны данные`: нет настоящих независимых сопоставимых оценок;
- `Можно продолжать ограниченную проверку`: действуют quotas, работа тестовая;
- `Пауза — недостаточно оценок`: сбор TRAIN приостановлен автоматически;
- `Пауза — подтверждена регрессия`: не включать последнего кандидата;
- `Плато — новых доказанных улучшений нет`: три корректных окна, стоп;
- `Подтверждён прогресс`: только если есть действительно независимый достоверный native comparison; показать к чему он относится (class94 Solo/DAMAGE), sample size и доверительный интервал.

Поля: campaign ID, текущая фаза, duration, next boot allowance, last STOP reason, нативных боёв/отказов/смертей/убийств, TRAIN/TUNING/AUDIT с подтверждённым источником, оплаченных и неподтверждённых действий, model candidate/reference/best SHA и глобальные updates, observed HP/MP/time (отдельно по режиму и при доказанной сопоставимости), `MODEL_REORDERED` vs `EXPERIMENT_REORDERED`, variance+coverage+uncertainty, `lastQualityEvidenceSha`, `qualityReason`, `bestVerified=null` если доказательств нет. Кнопка здесь не нужна: простой отчёт, не управление сервером. Никакого псевдо-прогресса «87% обучено» или обещания, что дальнейшая тренировка точно полезна.

Уже сейчас у владельца есть status-команда, которая не запускает сервер:

```powershell
$tr = '.\tools\phantom-runtime\training\Training.ps1'
$history = '<ABSOLUTE_APPROVED_PRIVATE_TRAIN_ROOT>'
& $tr -Action ActionValue022 -ActionValue022Command Status -Root $history -Json
& $tr -Action ActionValue022 -ActionValue022Command Report -Root $history -Json
```

Принимать решение «достаточно» только по **устойчивому улучшению на независимых проверках и отсутствию ухудшения в безопасности/ресурсах**, не по updates. При отсутствии доказательств честный статус `QUALITY_UNPROVEN`, даже если 1000 новых образцов.
