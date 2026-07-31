# Russian Semantic Understanding Contract

## Статус и граница

Goal 019 вводит только immutable understanding:

```text
input + immutable bounded context
→ strict normalization
→ data-declared intent/pattern matching
→ typed slot resolution
→ current authority grounding
→ ACCEPTED | CLARIFICATION_REQUIRED | REJECTED
```

Результат не выполняет action, не отправляет packet/chat, не генерирует текст и
не изменяет Player, Party, World, DB, social memory или любую gameplay-систему.
Runtime LLM, provider, worker и per-phantom thread отсутствуют.

## Authorities

XML и TSV являются byte-content-addressed immutable inputs. Pack публикуется
только после strict parsing и полной проверки aliases через текущие read-only:

- Game Knowledge для ITEM, NPC, CONTENT и CAPABILITY;
- topology snapshot для TOPOLOGY_NODE и LOCATION;
- party-role catalog для PARTY_ROLE.

Pack фиксирует hashes всех трёх authorities. Изменение hash во время загрузки
отклоняет публикацию. Semantic data не объявляет новые gameplay facts.

## Strict inputs

Loader принимает только UTF-8, точную XML-структуру и точный TSV header.
Запрещены DTD/XXE, неизвестные/повторные attributes и sections, duplicate IDs,
неизвестные intent/slot/reason/context fixture, invalid numeric bounds и aliases
без authoritative target. Размеры XML/TSV, corpus, patterns, aliases и context
явно ограничены.

Corpus `high-five-ru-corpus-v1.tsv` содержит 240 deterministic cases и является
частью versioned pack evidence. Seed: `19001901`.

## Normalization

Pipeline применяет NFKC, `Locale.ROOT` lower-case, прямое `ё → е`, единое
представление dash/quotes/whitespace, bounded tokenization и сохранение исходных
code-point spans. Repeated letters ограничены policy из XML.

Russian, transliteration, abbreviations и slang объявляются data aliases.
Java не содержит intent-specific словаря. Malformed surrogate, forbidden
control/format, oversized input, token overflow и опасный mixed-script token
fail closed со structured reason.

## Matching и ambiguity

Порядок evidence фиксирован:

1. exact;
2. declared transliteration;
3. declared abbreviation/slang;
4. bounded fuzzy.

Fuzzy работает только в границах minimum code points и edit-distance policy.
Short tokens не fuzzy. Tie по entities/intents, несколько context players,
неразрешённое местоимение и отсутствующий required slot не угадываются, а дают
`CLARIFICATION_REQUIRED` с конкретным reason.

Exact alias другого typed authority slot блокирует fuzzy reinterpretation.
Например, authoritative ITEM не может быть принят как fuzzy CAPABILITY только
потому, что intent pattern ожидает capability.

Scoring целочисленный, общий для всех intents и bounded. Alternatives не больше
четырёх; slots, evidence, tokens, patterns и candidate budget ограничены pack.

## Player identity и context

Player reference приходит только из входного immutable context и сохраняет
исходный `PhantomDomainRef`. Exact names, speaker, party leader, selected target,
unique nearby/recent player и previous accepted target разрешаются только по
явному context rule. Duplicate name, ambiguous pronoun или отсутствие context
никогда не создают identity из текста.

Context ограничен по party members, nearby/recent players и previous slots.
Semantic service не читает mutable Player/Party/World.

## Lifecycle

Production service строится после Game Knowledge, topology и party-role catalog.
Validated pack публикуется атомарно. Start failure не оставляет generation.
Operation claims прекращаются перед stop; shutdown ждёт bounded drain и очищает
pack до разрушения knowledge/topology authorities.

Disabled/inert PhantomSystem не читает semantic files и возвращает inactive
snapshot. Метрики только aggregate; per-parse logs/IDs отсутствуют.

## Verification contract

- семь focused modes и один `phantom-semantic-test` aggregate;
- corpus: intent accuracy не ниже 96%, slot precision не ниже 99%, safety 100%;
- forward/reverse/seeded-shuffle дают byte-identical result encoding;
- 100000 mixed parses без DB writes и workers;
- exact Game Knowledge/topology/party-role/System regressions;
- static verifier 019 и historical verifier 018;
- один frozen-tree full `ant verify`, standalone `ant jar`;
- два byte-identical post-commit verifier 019 run.

Goal 020 может потреблять результат только после независимого review Goal 019 и
закрытия отдельного Goal 018 activation gate. Goal 019 сам не активирует social,
conversation или actions.
