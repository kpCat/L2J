# Goal 019 — Russian semantic understanding

Status: `SUCCESS`.

## Summary

Реализован immutable Russian Semantic Pack understanding: strict hashed
XML+TSV, Unicode normalization, data-declared Russian/translit/slang aliases,
bounded fuzzy intent/slot matching, context-safe player references и grounding
только через current Game Knowledge, topology и party-role authority.

Actions, chat packets, text generation, Player/Party/World/DB/social mutations,
runtime LLM и production workers не добавлены. Social production не изменялась
и не потребляется.

Goal 018 зафиксирован как `ACCEPT_WITH_ACTIVATION_GATE`; temporal/idempotency и
first canonical `party.member.joined` emission gate документирован для
обязательного закрытия перед Goal 020.

## Git

- Branch: `feature/phantom-world`.
- Required parent: `d30b657a9351d8cb099548e959854bf826b7d1d1`.
- Required subject: `feat(phantoms): add russian semantic understanding`.
- Seed: `19001901`.
- Git-команды разрешены прямым Goal и workflow contract.
- Read-only inspection: status, branch/upstream, rev-parse, diff/name-status,
  ls-files и verifier-internal show/log/merge-base.
- Mutation после финальных gates: exact allowlist add, ordinary commit и push;
  amend/rebase/squash/merge/force push запрещены.

## Read-first evidence

- `AGENTS.md`, master plan, workflow contract, task package standard.
- Полный Goal 019 task package и Goal 018 report.
- Semantic act/domain-ref contracts и Game Knowledge query/model/hash paths.
- Topology query/node/snapshot hashes и strict party-role catalog.
- PhantomSystem startup/shutdown/snapshot ranges.
- Test launcher/build/verifier 018 patterns.
- Skeleton, test registry/assertions и ближайшие Game Knowledge/topology/Party
  suites.

`README.md`, общий code-map и pattern-файлы не найдены; повторный поиск не
выполнялся.

## Reused local patterns

- strict XXE-safe content-addressed XML loader и immutable publication;
- current snapshot/query hashes как authority generation;
- bounded lifecycle operation claims и inactive System snapshot;
- mode-based test launcher, focused Ant targets и descendant verifier.

## Changed files

Production/data:

- `phantoms/semantic/understanding/PhantomSemanticModel.java`;
- `PhantomSemanticGrounding.java`;
- `PhantomSemanticNormalizer.java`;
- `PhantomSemanticPack.java`;
- `PhantomSemanticUnderstandingService.java`;
- targeted `phantoms/PhantomSystem.java`;
- semantic XML и 240-case TSV corpus.

Tests/build/tools:

- `PhantomSemanticSuite.java`;
- targeted `PhantomSkeletonSuite.java`, `PhantomTestLauncher.java`;
- `build.xml`, `verify-task-019.ps1`.

Docs:

- Goal 018 review, semantic architecture contract, this report;
- status-only master plan update;
- user-supplied Goal 019 task package included unchanged.

## Architecture decisions

- XML/TSV raw bytes have independent SHA-256 identities.
- Pack validates all aliases before atomic publication and pins knowledge,
  topology and party-role hashes.
- NFKC, Locale.ROOT lower-case, direct `ё → е`, punctuation/whitespace and
  code-point spans are deterministic and idempotent.
- Intent phrases, transliteration, abbreviations/slang, entity aliases and
  typo policy live in XML; Java contains only generic matching.
- Evidence precedence: exact, transliteration, abbreviation, bounded fuzzy.
- Short tokens, mixed script, malformed/forbidden Unicode and overflow fail
  closed; ties and missing targets/slots request clarification.
- Exact entity alias from another typed slot blocks unsafe fuzzy reinterpretation.
- Player identity is accepted only from bounded immutable context and retained
  as the original `PhantomDomainRef`.
- Start publication имеет один explicit claim: concurrent start не вызывает
  второй loader и не может изменить уже опубликованный `RUNNING` state.
- Service starts after authorities and stops before their destruction.

## DB, schema and config

- DB access/writes: none.
- Schema/migrations/config changes: none.
- Phantom World remains disabled by default.

## Tests and measurements

- Production compile: PASS, 2092 sources.
- Test compile: PASS, 74 sources.
- Seven-mode semantic aggregate: 21/21 PASS.
- Corpus: 240 cases, intent accuracy 9800 bp, slot precision 10000 bp,
  clarification/negative safety 10000 bp.
- Determinism: forward/reverse/seeded shuffle byte-identical.
- Performance smoke: 100000 parses in 2623366700 ns; DB writes 0; workers 0.
- Exact affected: 190/190 PASS across Game Knowledge, topology, party-role,
  disabled System и shutdown suites.
- Historical verifier 018: `TASK018_VERIFIER_OK`.
- Working verifier 019: `TASK019_VERIFIER_OK`; scope 23, production 8,
  new production/data 7.
- Final semantic aggregate: 22/22 PASS, `BUILD SUCCESSFUL`, 24 s.
- Первый frozen-tree full `ant verify`: `BUILD SUCCESSFUL`, 13 min 30 s.
- Cached-diff review выявил concurrent-start publication race; exact lifecycle
  regression 4/4 PASS после bounded fix.
- Второй и последний full `ant verify`: `BUILD SUCCESSFUL`, 14 min 10 s.
- Final standalone `ant jar`: `BUILD SUCCESSFUL`, 18 s.
- Post-commit verifier 019 2x byte-identical выполняется после commit/push;
  exact SHA и evidence остаются в final response.

## Encoding checks

- mojibake-маркеры в изменённых файлах проверены: совпадений нет.
- escaped Cyrillic в изменённых файлах проверены: совпадений нет.

## Deviations, limitations and risks

- Class alias не объявлен: current authority не предоставляет требуемый exact
  bounded class-ID set через разрешённый seam.
- Semantic result не подключён к consumer; Goal 020 не начат.
- Goal 018 activation gate остаётся обязательным перед Goal 020.
- Второй full verify использован только после реального lifecycle race fix;
  лимит двух full runs не превышен. Pre-fix standalone jar также был зелёным,
  но final evidence относится к повторному jar на исправленном tree.
- Commit/push и post-commit evidence выполняются после фиксации отчёта.

## Next step

Независимый review Goal 019. До принятия не начинать Goal 020.
