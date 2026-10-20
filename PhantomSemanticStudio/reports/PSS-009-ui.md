# PSS-009 UI evidence

Primary operator scenario: Full HD 1920x1080, 100%, default font (user clarification during this task). The live STA process reports Screen.Bounds 1920x1080, WorkingArea 1920x1040 and DeviceDpi 96. This is framework metadata and live HWND geometry, not a screenshot/click visual acceptance.

## Proven initial defect

At construction, before Show: all page clients already1252x712, but five pages had started at default200x100 when child anchors were computed. tabCandidates had explicit Size and was correct. Delta +1052 horizontal and +612 vertical matches every affected anchored control.

| Control | RED actual Bounds | GREEN nominal Bounds |
|---|---|---|
| grpRequest | 16,54,1816,1150 | 16,54,764,538 |
| btnGenerate | 16,1226,294,40 | 16,614,294,40 |
| btnPreview | 2112,1014,166,70 | 1060,402,166,70 |
| btnPackQuality | 1918,58,360,30 | 866,58,360,30 |
| gridLibrary | 16,96,2262,962 | 16,96,1210,350 |

Initial RED: 32 distinct inaccessible controls, exit1. Only five Size assignments changed in the first root proof: the same live test passed1/0, exit0. First bad transition was initialization inside the constructor; forward and reverse switches retained it.

## Designer fix and additional reproduced defects

All six pages receive1252x712 before child attachment. Each page has its own Designer-native1x1 TableLayoutPanel with AutoSize row/column and Dock.Fill, holding a Dock.Fill contentPanel with a scalable MinimumSize equal to the designed extent. Five separate forms use the same root pattern. Original right/bottom anchors inside content are restored; conversation additionally anchors Right to grow while retaining the lessons column gap. Tables own native AutoScroll. There is no runtime layout builder or resize handler.

The AutoSize strips preserve the minimum content extent on small windows and expand it on larger ones. This avoids unscaled AutoScrollMinSize and the default layout's exclusion of right/bottom anchors from scroll extent. Header labels constrain to the header width and use ellipsis; tab headers are multiline. Parent minimum sizes are760x420. These properties remain editable in Designer.

Each control keeps its original name, handler, caption and semantic behavior. The content panels also isolate NumericUpDown children from intermediate outer scrolling. ChatCorpus statistics ended at281 while its clear button started265: button moved to294 and editors to338, preserving their original bottom656. No model, database, approval, source or staging behavior changed.
During testing the operator reported Parameter is not valid in Label.OnPaint. The harness had disposed a Font still assigned to a live form. Fonts now survive until every tested form is disposed; ThrowException mode is installed before handles. Raw JIT text remains private. No continued exception-dialog run is treated as GREEN.

## Automated contract and limits

Final scope follows the operator's latest instruction: only standard window plus maximized Full HD1920x1080 at unchanged100% and default font. No further DPI, Font, minimum-viewport or Control.Scale stress is run in the final route. Earlier exploratory stress diagnostics remain ignored and do not define final acceptance.

Real forms and public controls in the existing STA console runner. All six main tabs first/repeated forward+reverse at normal/maximized/restored states; DialogueLab all three pages; all five separate forms normal/maximized/restored. Final7 PASS/0 FAIL, exit0. Control-level repeated Bounds snapshots recurse through groups, content panels and layout tables.

Actual fullscreen growth: library1210x350→1870x517; request764x538→1424x705; conversation784x320→1444x487; StageSelection list650x282→1450x559; DialogueLab tabs1256x690→1896x867; ChatCorpus list800x318→1440x501; PackQuality list948x320→1888x607; V3 preview948x198→1888x405. Growth assertions run for main and all five forms.
Assertions: page/display equality, actual parent/virtual containment, sibling intersections, drift, native scrolling to whole buttons and both client-content edges of wide editors, programmatic Focus/ContainsFocus, button hit test and ancestor clipping. Library has four visible full-width columns. Long multiline instruction is synthetic. Disabled safety actions stay disabled. No model/import/stage/release actions clicked.

Physical capture and desktop-control APIs failed to initialize with sandbox helper errors; no running VS instance was found. Bounded attempts stopped. PHYSICAL_UI_NOT_TESTED; PHYSICAL_DPI_NOT_TESTED; VS_DESIGNER_NOT_TESTED. Other scales/fonts are excluded by the operator. Physical100% visual acceptance and VS remain NOT_TESTED.

## Operator checklist at1920x1080 /100%

Open the final Release application. Check each row on first selection, switch away and back, then maximize/restore where supported (StageSelection keeps its existing disabled maximize button; its maximized geometry was exercised programmatically). Use visible scrollbars and Tab/Shift+Tab to reach lower/right controls. Check complete Russian captions visually; programmatic focus does not prove physical key traversal or caption rendering.

| Surface | Check |
|---|---|
| Конструктор | request fields, batch count, Generate, To candidates; groups separate |
| Диалог и обучение | conversation/input, Preview, Teach, DialogueLab, lessons; lower actions reachable |
| Библиотека | Quality map, Search, all4 grid columns and details |
| Кандидаты | text/note, Save/Validate/Approve/Reject, similarity/semantic review |
| Экспорт для ревью | info/log, source/export/stage/v3 buttons; do not stage during layout review |
| Настройки | all fields, numeric limits, Save/check/import/ChatCorpus buttons; no real import needed |
| StageSelection | available/selected/detail, Create/Cancel; consent behavior unchanged |
| DialogueLab | all3 pages, input/mentor/editor/corpus controls and lower actions |
| ChatCorpus | statistics/clear button separate, lists/details, filter/source/cancel |
| PackQuality | filters, list/details, advisory analysis/cancel |
| V3Proposal | full consent texts, preview/proof, stage/release/cancel remain safely gated |

VS2026 manual gate: open each form Designer, save, reopen, then Release build; verify controls/events/resx nesting. This gate is NOT_TESTED here. Live LM NOT_RUN; Java NOT_RUN; L2J untouched.
