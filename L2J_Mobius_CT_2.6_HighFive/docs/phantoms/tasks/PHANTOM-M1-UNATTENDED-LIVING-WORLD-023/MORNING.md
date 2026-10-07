# Утро после TASK023

Статус **BLOCKED**, SERVER_M1_PASS=false, M1=OPEN, REAL_FINAL_PASS=NOT_RUN.
Полная визуальная приёмка не объявлена. Сохранённый запуск предназначен для инспекции
экспериментального кандидата; earned store/continuous farm ещё не прошли серверный контракт.

Verified production SHA: **1b71464a9e09a80dad890e2bfd39df0f3b5d3b96**.
GameServer.jar SHA256: F558DED66B5AFB17F2F8E90D42CE08FD3B48808D6C4EC02B02D99291CCEB2A91.
Production tree:801590cd9bee413ecfdb3a19b759de0537a33cde.
Normal published branch:experiment/m1-candidate007-observe008; final artifact HEAD и exact
elapsed записаны локально в PUBLISH_RECEIPT.txt после проверки remote; код не менялся.

Рабочая копия:
`C:\Users\ZBook\.codex\worktrees\m1-overnight-023\L2J_Mobius`.
Reviewed runtime:
`L2J_Mobius_CT_2.6_HighFive\.phantom-local\night023b\runtime` внутри этой копии.
Retained DB: **l2jmobiush5_localplay_night023b**, MariaDB127.0.0.1:3308.
Ownership runtime fingerprint:d3ae0469183b64b24b89fc29cf94436f2d550fee6303617d4c6c585f1b092756.
Config/data fingerprint:48772DE9C4034EA475F9D3ECDD7258EFD770956899D63368503A25DAF787AE27.
Start-Reviewed023.ps1 уже запускался успешно: сверяет pinned SHA/JAR/302 files/own DB,
затем existing Start-LocalPlay.ps1 и его ownership/port guards. Rebuild для запуска не нужен.

Готовая команда PowerShell7:

```powershell
pwsh -NoProfile -File 'C:\Users\ZBook\.codex\worktrees\m1-overnight-023\L2J_Mobius\L2J_Mobius_CT_2.6_HighFive\docs\phantoms\tasks\PHANTOM-M1-UNATTENDED-LIVING-WORLD-023\Start-Reviewed023.ps1' -Background
```

Серверы сейчас STOPPED; stale PID records сохранены как evidence. Ночью использована
только ordinary copy TestAdmin268492939. Её original position/vitals/EXP/SP восстановлены
existing Synthetic STOP; не kpCat. Реальный TestAdmin client login оставлен будущим действием
пользователя и не являлся ночным gate. QoL AllowedAccounts не использовались как Pilot consent.

В [RESULT.md](RESULT.md) и [LIFECYCLE_EVIDENCE.md](LIFECYCLE_EVIDENCE.md) указаны actual
retaliation guard failure, exactAnchor/store/topology failure и четыре потерянных durable
EXP/SP totals. Последний empty drain был успешен, но эти failures остаются. Natural death
recovery и полный soft return не доказаны; после restart nearby census не получил actors.

Все собственные a/b/c runtime и DB сохранены. c использована для единственного planned
crash, восстановлена на той же DB и штатно остановлена. b failed-stop полный SQL snapshot:
`.phantom-local\night023b\after-failed-stop-R4.sql`; исходные PLAY snapshots и credentials
остаются privately рядом с runtime, в Git не включены. Unknown receipts не удалены.
Main checkout/PLAY/foreign processes не менялись. Protected kpCat values совпадают в PLAY/a/b/c.
