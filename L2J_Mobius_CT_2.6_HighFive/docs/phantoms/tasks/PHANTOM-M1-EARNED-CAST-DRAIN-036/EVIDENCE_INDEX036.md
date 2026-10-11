# EVIDENCE_INDEX036

Все пути ниже относительно TASK036; raw не добавлены в public Git.
Core archive: C:/Users/ZBook/.codex/visualizations/2026/10/11/01a12b3f-7a16-7301-a9ab-116daca1d868/TASK036-review.zip.
SHA256=99cc1cfba46b98cdca949ce6a07fa1d6d9efe26aba9b5a3af4fde0434252abe8;7819 ZIP entries проверены.
Receipt=TASK036-archive-verification.json рядом с архивом; final publication=TASK036-publication.json.

| Доказательство | Файл/группа |
|---|---|
| исходные diagnostic/Q15 RED |evidence/BASE_E01_RED036*,BASE_E07_RED036*,BASE_Q15_036*|
| base natural probe + healthy stop |evidence/BASE_PROBE036a;BASE_STOP036a|
| J1 failures и исправленные test premises |evidence/J1_NATIVE036*,J1_ADDITIONAL036*,J1_SHARED036b*,J1_PRE_FREEZE036*|
| committed J2 native actual timelines |evidence/FINAL_J2_NATIVE036.log и native-cast-drain036.txt/xml; provenance.json|
| E10 original deadline/failure |evidence/FINAL_J2_LIFECYCLE036.log и reports|
| Q15 native rate/delivery |evidence/FINAL_J2_Q15_036*|
| полный current93 matrix |evidence/FINAL_J2_MATRIX036-matrix.tsv;18 immutable log/report/provenance groups|
| inventory regression6+4+2+9+11+2 |evidence/FINAL_J2_CAPTURE036*,PROJECTION036*,CAPACITY036*,MODEL036*,SQL036*,COMPACT036*|
| actual live whole cohort/caster/drain |evidence/FINAL_SCENE036b: baseline-cohort,primary,all-samples,capture-result,full-native/event-dispatch.tsv|
| typed DONE/COMPLETE/pools live/process stop |FINAL_SCENE036b/stock-stop/typed-stop-done.properties,stock-stop-verified.json,stock-stop.log|
| final whole-cohort SQL |FINAL_SCENE036b/stopped-sql; native per-FINALIZED *.sql.tsv|
| birth/receipt/raw SHA/terminal stream |evidence/STARTUP032b-2b9bea89fbfe4d6ab7962a71004dc625/lifetimes.jsonl; raw split between that directory and full-native|
| conservation/370 terminals/54 launch |evidence/FINAL_EVIDENCE_VERIFIED036.json|
| current native252 exact rows |evidence/FINAL_INVENTORY_ROWS036.json|
| sameDB native252 reload |evidence/FINAL_J2_SAMEDB_RELOAD036/reload.log,provenance.json|
| independent cohort outcome |FINAL_SCENE036b/continuity-v2.json;legacy-result.json|
| final OS/source/DB preservation |evidence/FRESH_CLEANUP036.json|
| historical27 selected input |tools/ops036/input034-receipt.json;selected-input034 внутри external archive|
| build/freeze/proof/helper audit |tools/ops036: FINAL_J2_BUILD036.log,Freeze-publication.json,Freeze-staged.diff,source-before.json,reuse-manifest.json|

Jar-first test snapshots сохраняются по SHA, каждый final run имеет отдельный provenance.
Immutable J0/J1/J2 Jar snapshots включены в archive; runtime configs/secrets/whole DB/JFR исключены.
Archive manifest проверяет size/SHA каждого source copy и каждого ZIP entry. Финальные публичные отчёты
исключены из core archive, чтобы избежать self-hash цикла; они сохраняются отдельными внешними файлами
и привязываются final publication receipt после commit. Это не новый confirmation commit.
