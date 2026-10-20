# Подэтап A — честный Pack-only диалог, история и контекст

## Acceptance contract
1. New lab opens only after successful import of `PackSnapshot`. Safe independent form; on missing source explicitly explain. Doesn't modify source, approvals or candidate set. The current «Диалог и обучение» functionality remains working.
2. Modes `AUTO/GAME/REAL/MIXED` with clear UI labels; default AUTO. Optional relationship band and register follow existing values. Context is editorial, **not server-compatible runtime targeting**; annotate `WORLD_ADVISORY_ONLY`.
3. Each user turn produces a trace with `UserText`, `InputWorldHint`, `PackStatus`, `PatternId`, `TemplateId`, `Act`, `Topic`, `PackFingerprint`, `Notes`, exact selected text if any; result `PACK_CATALOG_APPROXIMATE` if/only if PackPreview match supplies actual templateId+text; `NO_PACK_MATCH` or `FUNCTIONAL_OR_MEMORY_UNSUPPORTED` otherwise. No generated pack-like filler. Nil template with matched pattern is explicitly no-reply.
4. Session has bounded turns, response-repeat state, deterministic reset, current turn selected; no mass history writes. Offline (LM disabled/server offline) entire pack-only flow functional. Idle 0 HTTP calls.
5. `PackPreview` may be reused but must not misrepresent C# approximation as Java. Tokenization/alias/priority counters and reason shown where available; if source drift detected, mark session `STALE_SOURCE` and require import, do not silently switch baselines.
6. Ambiguous game/real detection bounded and explainable. Test "босс" alone -> UNKNOWN/clarification opportunity; "дроп с рейд-босса" vs "начальник на работе" shouldn't map to single universal category; game override remains override. No hidden rewrite of input before PackPreview.
7. Multi-turn C# tests on synthetic v3 pack: same text repeat and `PackPreview` result IDs, no match, functional and identity, 256-char overflow, cleared history, changing scope, source drift, cancelled operations. Confirm zero mutations to approvals/session.json and all source stamps.

## WinForms
Use separate `DialogueLabForm` .cs/.Designer.cs/.resx with `InitializeComponent` static declarations/layout/events. Controls: transcript with **distinct headings** `ПАК` vs `НАСТАВНИК`, player edit and Send, context mode ComboBox, relationship/register, trace/detail panel, Clear, Cancel, explicit Mentor toggle/buttons reserved for B, source info and status. UI may be re-arranged with usual anchor/dock standard controls. No dynamic controls factory, no IO/network in ctor/InitializeComponent. Add clickable entry point to existing dialogue tab. Track control state `busy` separately from text editing, don't hold UI thread during pack read/hash.

## Tests
RED first: demonstrate missing engine/new mode/no-match; GREEN precise assertions. Extra STA public-control test must show 3 turns (pack reply, no reply, user note), correct role labels, preserved input after Cancel and tab controls. Static Verify-Designer must check new form constructor and handlers, not only MainForm. Full physical DPI manual gate remains separately NOT_TESTED if computer-use unavailable.
