# Отдельное baseline исключение005

Статус: APPROVED_AND_APPLIED. Пользователь явно разрешил: «Разрешаю новый baseline005 и native испытания». Сами protected файлы и исторические manifests не менялись.

Единственный drift207 — original `dist/game/config/OwnerConfig.json`: expected5776e838… / actuald8dbe7f2…. Actual byte SHA совпадает с текущим tracked профилем в clone004/005; canonical owner-profile38 validator PASS. Другие206 hashes совпали.

Создана отдельная новая аттестованная COPY protected207 для EVAL005, с заменой только одного expected SHA на уже существующий actual. Исторический manifest SHA9777ab48bc8d14bdee77d4aca8e7e650cb099f03ca33fea108d5cdb941c59dcd остаётся immutable. Geo203 и сами207 файлов не меняются.

Новый manifest SHA: **a523f7ffe87682d2c1bbb0d23f40cad8db9634f7f2bfced83232c3e6703d754a**. Ровно1 из207 entries отличается, остальные206 byte values неизменны. Полная copy остаётся приватной, в Git не публикуется.

Source allowlist расширен с9 до10 файлов: минимальный EVAL005-only pinned hash path в `TrainingOwner.ps1`; небольшие изменения уже разрешённых `Training.ps1`/`Evaluation005.ini` для выбора только этой exact COPY в own stopped TEST. V1/V3 исходные pins/defaults и старые среды не меняются; generic skip/accept-any-hash не добавляется.

Это явно согласованное изменение исходного G0 baseline: исходные SAFETY/ACCEPTANCE требуют прежний protected207 unchanged и fail-closed. Новая COPY прошла Preflight и staging всех207 файлов и203 Geo. Wrong-pin negative control отклонён; default V1/V3 pin остался прежним. MAIN/PLAY/source OwnerConfig не изменялись. Ошибка асинхронного writer drain исправлена ожиданием по существующему bounded15s паттерну; собственный GameServer действительно запущен.
