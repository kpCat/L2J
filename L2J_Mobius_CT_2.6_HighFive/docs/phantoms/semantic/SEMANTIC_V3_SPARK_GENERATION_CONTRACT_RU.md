# Semantic Pack v3 — контракт генерации корпуса для Spark

Этот документ относится только к следующей content-only задаче. POST-002 создал загрузчик, строгую валидацию и небольшой seed; массовое наполнение здесь не выполняется.

## Модель и границы

- Модель следующей задачи: `GPT-5.3-Codex-Spark`, thinking `Medium`.
- Разрешены только перечисленные ниже XML-сегменты и `semantic/humanized/v3/manifest.xml` для добавления этих сегментов в детерминированный порядок.
- Запрещены Java, `build.xml`, v1/v2, custom overlay, конфигурация, SQL, runtime LLM/API и любые другие файлы.
- После каждой тематической партии обязателен быстрый gate: `ant phantom-humanized-v3-content-validate`.

## Allowlist и категории

Semantic-файлы находятся в `dist/game/data/phantoms/semantic/humanized/v3/segments/`, response-файлы — в `dist/game/data/phantoms/conversation/humanized/v3/segments/`.

Разрешённые категории и имена файлов:

- `lineage/{leveling,farming,party,classes,buffs,crafting,trade,enchant,pvp,clan,raids,quests}.xml`;
- `life/{greeting,mood,food,music,movies,work,sleep,everyday}.xml`;
- `social/{friendly,teasing,rivalry,angry,apology,reconcile}.xml`.

Один путь может существовать с обеих сторон — semantic и conversation. Порядок загрузки задаётся только `<segments>` manifest; обход каталогов не является контрактом. Новые пути добавляются в manifest тематическими парами `SEMANTIC`, затем `CONVERSATION`.

## Схема semantic-сегмента

Корень:

```xml
<humanizedV3SemanticSegment id="v3.lineage.leveling.semantic" version="3" category="lineage.leveling">
  <aliases>...</aliases>
  <patterns>...</patterns>
</humanizedV3SemanticSegment>
```

`aliases` необязателен. Alias имеет только `from` и `to`; обе стороны — один нормализуемый токен не длиннее 32 символов. Pattern:

```xml
<pattern id="v3.lineage.leveling.request.0001"
         topic="plans" act="smalltalk.reply" phrase="..."
         salience="500" ttlMinutes="0" priority="500"/>
```

Допустимы необязательные `fact` и `recall` только со значениями `NAME`, `LOCATION`, `HOBBY`, `MUSIC`, `MOVIE`, `GAME`, `FOOD`, `PLAN`. Разрешён не более чем один маркер `{value}`, только в начале или в конце phrase. Topic и act должны быть заранее объявлены в manifest; новые значения нельзя придумывать молча.

## Схема conversation-сегмента

Корень:

```xml
<humanizedV3ConversationSegment id="v3.lineage.leveling.conversation" version="3" category="lineage.leveling" mature="false">
  <templates>...</templates>
  <profanity>...</profanity>
</humanizedV3ConversationSegment>
```

Template:

```xml
<template id="v3.lineage.leveling.reply.0001"
          act="smalltalk.reply" band="NEUTRAL" register="CASUAL"
          profanity="NONE" text="..."/>
```

Разрешённые band: `UNKNOWN`, `NEUTRAL`, `FAMILIAR`, `TRUSTED`, `RIVAL`, `TENSE`, `HOSTILE`. Register: `NEUTRAL`, `CASUAL`. Profanity: `NONE`, `MILD`, `CONTEXTUAL`. Разрешённые placeholders: `{name}`, `{value}`, `{memory}`, `{interest}`. Любые другие фигурные placeholders запрещены.

Mature-контент должен находиться в отдельном сегменте с `mature="true"`; значение template обязано совпадать с сегментом. Profanity entry использует `id`, `level`, CSV-список объявленных `acts` и `text`.

## Идентификаторы, текст и дубликаты

- ID: `v3.<category>.<назначение>.<четыре цифры>`, только lower-case ASCII, цифры, точки, `_` или `-`; ID глобально уникален среди v1/v2/v3.
- XML сохраняется как настоящий UTF-8 с читаемой кириллицей. Запрещены mojibake, `\u04xx`, `\u05xx`, `&#x04xx;`, `&#x05xx;` и HTML-экранирование кириллицы.
- Нельзя повторять нормализованный точный pattern или нормализованный response text. Перефразирование должно менять смысловую форму, а не только пунктуацию, регистр или `ё/е`.
- `override` в v3-сегментах запрещён. Он существует только в загружаемом последним пользовательском custom overlay и обязан быть явным.

## План объёма

Цель полного корпуса после всех content-only партий: не менее 5 000 patterns и 20 000 templates, оставаясь внутри manifest limits: 64 файла, 1 MiB на файл, 32 MiB суммарно, 8 192 patterns, 32 768 templates, 2 048 aliases, 1 024 profanity entries.

Для каждой тематической партии ориентир: 160–220 patterns и 650–850 templates. Распределение длины templates: примерно 55% коротких (до 55 символов), 35% средних (56–110), 10% длинных (111–180). Поддерживайте реальное покрытие band/register, но не размножайте одну фразу механической заменой обращения.

## Редакционные правила

- Lineage-категории говорят только о механиках и быте High Five: прокачке, фарме, группе, классах, бафах, ремесле, торговле, заточке, PvP, клане, рейдах и квестах.
- Life/social-категории не должны выдумывать игровые факты, награды, предметы, отношения или настроение. Личное отношение определяется runtime social authority.
- Избегайте канцелярита и AI-клише: «безусловно», «отличный вопрос», «я всегда готов помочь», чрезмерных вводных, одинаковых трёхчастных ответов и повторяющихся эмодзи.
- Не обещайте действие, выдачу предмета, телепорт, баф или результат. Functional execution отвечает отдельно и формирует правдивый итог.
- Внутри одной партии не используйте одинаковые начала более чем у 5% templates; не создавайте серии, отличающиеся одним существительным.

## Обязательный цикл партии

1. Изменить только allowlisted v3 XML и, если добавлены файлы, manifest.
2. Запустить `ant phantom-humanized-v3-content-validate`.
3. Исправить все duplicate/schema/UTF-8/mature/path/count ошибки до следующей партии.
4. Не запускать массовую следующую партию, пока текущая не проходит validator.
