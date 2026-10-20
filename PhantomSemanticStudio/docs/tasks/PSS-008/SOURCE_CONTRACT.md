# Исходные контракты / exact references для PSS-008

## Студия: immutable required base

- `kpCat/PhantomSemanticStudio`, `main` SHA **`0344c9c0671d53a7bb757db7a92b1dfd03246e9c`** (confirmed by GitHub). Root under `C:\Users\ZBook\PhantomSemanticStudio`, local HEAD must match before edits.
- Important source owners: `Core/PackReader.cs`, `TextRules.cs`, `CandidateValidator.cs`, `StageBatchSelection.cs`, `IsolatedPackStager.cs` (custom-only shadow), `SemanticDuplicateScout.cs`, `DialogueLab.cs`, `DialogueLabStore.cs`, `CorpusStore.cs`, `WorkspaceStore.cs`, `PathSafety.cs`, `WinForms/MainForm.cs`, `StageSelectionForm`, `DialogueLabForm`, `ChatCorpusForm`.
- Existing frozen file safety: PSS003 `scripts/Test-PSS003-Java.ps1` only accepts `workspace/proposals/<id>` and validates custom; don't change its allowlist to sneak v3. Build-Verify should still cover 107 ordinary console cases + new focused cases.

## L2J: only for reading, public reference != local authority

- `kpCat/L2J`, branch `feature/phantom-world`, public checked reference SHA `3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25`; local checkout can be **newer** and does not require checkout/fetch/update. Verify exact installed file bytes and loader source in local L2J under read-only boundary; if different, adjust safe gates or BLOCKED_CONTRACT.
- High Five Java `java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java`: `loadV3(phantomDataRoot,true)` loads v1 → v2 → v3 manifest segments in explicit deterministic order → six custom files. `readV3Semantic`/`readV3Conversation` require strict XML roots/attributes; non-custom v3 elements **do not allow override**. Templates selected by act/band/register/implicit mature and other Java logic, **not by per-Template topic or gender**.
- Manifest `dist/game/data/phantoms/semantic/humanized/v3/manifest.xml`: exact base id/version/bounds; `<topics>`, `<acts>`, `<segments>` ordering. Public reference has **52** segment paths: 26 `SEMANTIC`, 26 `CONVERSATION`, as 26 mirrored category pairs (12 lineage, 8 life, 6 social), hard max 64. Public numbers are reference only; LOCAL actual count is authoritative.
- Example paths: `semantic/humanized/v3/segments/social/friendly.xml` and `conversation/humanized/v3/segments/social/friendly.xml`; category in root `social.friendly`. `SEMANTIC` root `<humanizedV3SemanticSegment id="..." version="3" category="..."><aliases/><patterns/></...>`; `CONVERSATION` root `<humanizedV3ConversationSegment id="..." version="3" category="..." mature="false"><templates/></...>`. Other segments' actual layout must be inspected rather than guessed. `manifest.xml` path/section symbols are a strict validator contract.
- `build.xml` only shadow Ant target `phantom-humanized-v3-content-validate`, transitively `compile-tests`. Default `build=../build` is dangerous on original path; all CLI `-Dbuild` paths must be forced inside physical shadow, audited before execution; never run on L2J original.
- Hard loader v3: 64 files, 1MiB each, 32MiB total, 8192 PATTERN, 32768 TEMPLATE, 2048 aliases, 1024 profanity, 256 pattern index bucket, 4096 template bucket. Custom XML **different** 65,536 byte cap. Do not conflate.
- Existing PSS006 real chat import was 685498 public / 336473 private skipped; database remains local private and PSS008 must never report raw messages, nicks, ZIP entry names or share selected quotes automatically with LM/Git.

## Reality vs approximation

Java content-probe proves reader acceptance and bounded catalog selection in physical isolated copy, **not** server's player/social memory/cooldowns/gameplay actions or dialogue quality. `DialogueLab` is an approximate C# inspector with explicit `NOT_JAVA_RUNTIME_PARITY`. `Gender!=ANY` and unknown placeholders are disallowed by v3 stage, not silently rewritten.
