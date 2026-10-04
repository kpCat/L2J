# Paused candidate — UNACCEPTED

**PAUSED_BY_USER_BUDGET / M1_OPEN**. Сохранение source bytes/evidence; не готовый fix, deploy package или runtime permission. Source HEAD/base: `d924a7d2c2f1e06af1cd54bedf1bee861560044f`. Final docs-only commit сообщён отдельно; clean accepted codeSHA/JAR отсутствует.

| Файл | SHA256 |
|---|---|
| [SOURCE_MANIFEST.tsv](candidate-paused/SOURCE_MANIFEST.tsv):161 snapshots | `5CE23E62A5E595FF6224D27EF0691883635ECFBC95CA2A0A0A2378ACB1209350` |
| [TRACKED_CANDIDATE.patch](candidate-paused/TRACKED_CANDIDATE.patch) | `5A824A1539965FB26F38C1017C789E9CEAE3C5DC92354AEC71673C6A3E7880C0` |
| [LOCAL_EVIDENCE_MANIFEST.tsv](candidate-paused/LOCAL_EVIDENCE_MANIFEST.tsv):320 local files | `F566252613239ED0AE6A45C0C7AABD19160B6A70EC73BF312AD144F2556AE064` |
| INPUT_HANDOFF_BEFORE.md | `D006F02D1326527302605549E573AC031D07546C2979DA2D9FF80F0E9FE1C20B` |
| Last native TXT | `807CBC5584B7943BA220837858ABBD7E713B8837FAF011AFA4EFB2FAC6329B1E` |
| Last native XML | `1FF04251FAED8B9408BC413B7E3C18CE782F584E065ABADC5D3558D9181A9E37` |

`candidate-paused/files/<module-relative-path>` содержит полные точные bytes:55 modified tracked sources,8 prerequisite byte-only files,40 new sources и58 pending. Все pending совпадают с integrated bytes; differing/unintegrated pending0. Manifest различает эти состояния. Proposals/findings не становятся integrated кодом.

Patch содержит55 Git-visible tracked diffs. Normal Git diff скрывает8 prerequisite byte/EOL изменений; их full snapshots сохранены отдельно. Это repository DDL/test bootstrap/population XML, не DB dumps и не разрешение production schema change. Manifest/full snapshots определяют точные bytes.

LOCAL_EVIDENCE_MANIFEST фиксирует inventory до замены input HANDOFF и создания pause docs. Old HANDOFF сохранён отдельно. Все older raw reports/private intermediates остались локально; минимальный remote checkpoint публикует last report, нужные review/proposal notes и полный text source artifact, не повторяя320 reports.

Не публикуются credentials/config/INI, приватные DB dumps/before-image data, geodata, JAR/class/exe/zip и runtime logs. Worktrees/private directories не удалялись. Snapshots под docs не входят в production compile; production source вне docs не staged/committed. Автоматически применять candidate/proposals нельзя.

Локальный task007 `.gitattributes` запрещает EOL conversion только для candidate-paused и двух last report файлов, чтобы опубликованные Git bytes сохраняли перечисленные SHA256. Он не меняет attributes production source. Docs-only Git archive/export используется лишь для сверки artifact bytes, без build/tests.

Проверки публикации ограничены bytes/inventory, безопасным exact-path staging, кодировкой и normal push. Дополнительные инженерные audits/reviews/tests запрещены pause-запросом. Resume требует прямого указания пользователя.
