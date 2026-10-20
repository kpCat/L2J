# PSS-009 — тесты и анти-ложный GREEN

Каждый пункт должен иметь тест/наблюдение, actual PASS/FAIL/NOT_TESTED и короткое evidence.

1. **BASELINE_REPRO:** до правки MainForm сначала видимая tabGenerate, затем остальные 5, затем обратный порядок: `btnPackQuality` offscreen, `gridLibrary` растянут, `btnGenerate` ниже, `btnPreview` offscreen — найти хотя бы одно реальное окно/Bounds assertion RED. Синтетический scope; никаких user workspace writes.
2. **FIRST_SHOW:** после fix MainForm.Shown с настоящим handle, переключение всех шести страниц, важные дети реально находятся в `ClientRectangle`/свой доступный scroll viewport; Page.ClientSize соответствует TabControl display area, не унаследованному default размеру.
3. **SECOND_SWITCH:** A→B→C→A и Settings→Library→Export→Constructor. First/second Bounds стабильны, нет накопления width/height после циклов, не выезжают кнопки.
4. **LAYOUT_RTL?** Только существующий LTR, никаких новых RTL требований. Проверить русские заголовки не выходят за кнопки; длинные path/hashes в textbox доступны через cursor/scroll без потери кнопок.
5. **GENERATE:** `grpRequest` не поглощает правый `grpGeneratorInfo`; `btnGenerate`, `btnToCandidates` и `nudCount` находятся в зоне доступа; ввод многострочного текста не скрывает кнопки.
6. **CHAT:** `txtConversation`, `txtChatInput`, `btnPreview`, `btnDialogueLab`, `btnTeach`, `grpLessons` доступны, scroll/keyboard focus; нижние controls не уходят за окно при first show.
7. **LIBRARY:** `btnPackQuality` видна, читаема и доступна; все 4 колонки gridLibrary доступны; `btnSearch` тоже видна. Проверить реальный click button создаёт соответствующую Form на синтетическом pack, либо layout только если Core read не допускает теста без side effect.
8. **CANDIDATES:** таб не регрессировал, editor/review/semantic buttons доступны; Save/Discard/Cancel, selected row evidence lazy-refresh не изменились.
9. **EXPORT:** `btnStageXml`, `btnV3Proposal`, `txtExportInfo`, `txtExportLog` корректно распложены, никакого клика на фактическое создание XML во время UI-smoke.
10. **SETTINGS:** все fields/buttons не скрыты при небольшом размере; `btnChatCorpus` доступна. Проверка только UI; не нажимать реальный Import в live user workspace.
11. **RESIZE/DPI:** baseline nominal client 1260×850, 1280×720/1366×768/1920×1080 work-area constraints; scale 100% и 150% real если доступны. При DPI size-independent assert Bounds, UI screenshots in ignored artifacts only. `Screen.WorkingArea` может быть меньше MinimumSize: доступ к кнопкам через scroll/компоновку, не максимизацию за экран.
12. **OTHERS:** все 5 modal forms inspect first show, min, resize, tabPage children `DialogueLabForm.tabsLab`; ни один критический control offscreen без scroll. Дисплейную проверку формы не подменять наличием csproj nesting.
13. **NEGATIVE_LAYOUT:** удалить *только временно в test process/mutation* защиту начального TabPage-size → dedicated layout check снова FAIL; восстановить и проверить настоящий GREEN. Не коммитить mutation.
14. **LEGACY:** Compile+122 old console checks, STA4/3/2/7/2, static designer, no Core/source/Semantic Pack change; явные exits.

Неправильные способы «добиться PASS»: grep по `Size`, `Visible` не учитывая clipping, тест только на tabCandidates, `btnPackQuality.Visible=true` при X>ClientWidth, подавление ошибок, исключение вкладки из маршрута, повышение min window до 2000px, уменьшение шрифта, отключение DPI awareness.
