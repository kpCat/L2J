# Source contract / read-first map

**Studio exact public base**: `kpCat/PhantomSemanticStudio` main `60d48348687467c3724ae2bb010ef62987c58d4e`; feature commit `fb1f4a2cc252f2007bf46c7b31227d833852e7ba`.

- `src/PhantomSemanticStudio.Core/Models.cs`: `Candidate`, `GenerationRequest`, `PackSnapshot`/`SourceFileStamp`, `SessionState`.
- `src/PhantomSemanticStudio.Core/CandidateValidator.cs`: `Validate`, `CandidateReview.Approve/IsCurrent/Edit`, exact/lexical duplicate, gender editorial warning.
- `src/PhantomSemanticStudio.Core/PackReader.cs`: imports 65 humanized inputs, source fingerprint is a Studio hash (NOT Java combined hash), custom overlay last.
- `src/PhantomSemanticStudio.Core/ReviewExporter.cs`: old JSON-only review export. It is NOT a mechanism to install XML.
- `src/PhantomSemanticStudio.Core/WorkspaceStore.cs`, `PathSafety.cs`: local write boundary, single workspace lock, protection against source/workspace overlap and reparse points.
- `src/PhantomSemanticStudio.WinForms/MainForm.cs` + `.Designer.cs`: tabExport, confirmation patterns, protected pending edits, `RunAsync`.
- `tests/PhantomSemanticStudio.Tests/Program.cs`: 59 checks per published PSS-002 evidence; add isolated stage/Java negative tests without deleting old checks.

**L2J read-only oracle** (точные пути внутри `L2J_Mobius_CT_2.6_HighFive`):

- `java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java`: `loadV3(root,customEnabled)`, `Loader.readPatterns/readTemplates/readV3Manifest`, strict bounds and keys.
- `dist/game/data/phantoms/semantic/custom/my-social-topics.xml` (`socialTopics version="1"`), `conversation/custom/my-phrases.xml` (`phrases version="1"`).
- `dist/game/data/phantoms/semantic/humanized/v3/manifest.xml`; all 65 imported humanized artifacts, not entire server data.
- `build.xml`: target `phantom-humanized-v3-content-validate`, depends on `compile-tests`, builds into `../build` relative to its basedir.
- `test/java/org/l2jmobius/tests/phantoms/PhantomPost002SemanticV3Suite.java`: DB-free v3 loader/schema/collision checks.

**Observed source limits** (reconfirm if local Java differs): KEY `^[a-z][a-z0-9_.-]{0,63}$`; core template text maximum **240 UTF-8 bytes**, pattern phrase maximum **160 Java UTF-16 chars after Java normalization**, custom XML maximum **65,536 bytes per file**, per-catalog 8192 patterns / 32768 templates, 2048 aliases / 1024 profanity; high-level v3 files 64 / 1 MiB / total 32 MiB. Часть C# CandidateValidator лимитов шире; stage обязан проверять реальные Java bounds, а не молча усекать.

**Последние source counts по PSS-002 evidence**: 65 files, 5310 patterns, 20963 templates, 424 aliases, 104 profanity, 130 topics, 139 acts; fingerprint `43c49f49e298c7f35cfef8577f35a4dc50e585191d81073afc3f49ad3a81e322`. Значения — историческое evidence, сверять с текущими локальными файлами перед экспортом.

Ниже два разных утверждения, их нельзя смешивать:
1. C# `PackReader` может прочитать staged XML — **STRUCTURAL_STAGED**.
2. Настоящий Java `PhantomHumanizedCatalog.loadV3(stagedRoot, true)` прошёл, counters верны — **PASS_JAVA_STAGED**. Ни одно не гарантирует, что игра выберет правильную реплику или безопасно установит файлы.
