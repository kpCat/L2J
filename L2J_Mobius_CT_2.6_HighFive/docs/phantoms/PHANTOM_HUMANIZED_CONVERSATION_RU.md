# Humanized RU: настройка разговоров фантомов

Goal038 добавляет версионированный русский social/off-topic слой поверх функционального игрового понимания Goal018–020. Игровые запросы и команды всегда разбираются старым semantic/action path первыми. Custom pack не может добавлять игровые действия или обходить их authorization — это намеренное ограничение безопасности.

## Включение и режимы

Настройки находятся в `dist/game/config/Custom/PhantomPlayers.ini`:

```ini
EnablePhantomHumanizedConversation = True
EnablePhantomCustomConversationPack = True
PhantomConversationRegister = CASUAL
PhantomConversationProfanity = CONTEXTUAL
PhantomConversationVariation = HIGH
EnablePhantomMatureConversation = False
```

Допустимые значения: register — `NEUTRAL` или `CASUAL`; profanity — `NONE`, `MILD` или `CONTEXTUAL`; variation — `LOW`, `MEDIUM` или `HIGH`. Mature-слой требует явного `True`, работает только в приватном канале при доверенных отношениях и поставляется выключенным.

Чтобы полностью выключить мат, задайте `PhantomConversationProfanity = NONE`. Для редких выражений только в подходящем эмоциональном контексте оставьте `CONTEXTUAL`. Некорректное значение не подменяется догадкой: конфигурация fail-closed отключает Phantom system.

## Прямо редактируемые файлы

Core-файлы в `semantic/humanized` и `conversation/humanized` версионированы и не предназначены для локальной правки. Свои изменения храните только в шести файлах:

- `dist/game/data/phantoms/semantic/custom/my-ru-aliases.xml` — обычные однословные алиасы;
- `dist/game/data/phantoms/semantic/custom/my-slang.xml` — разговорный сленг;
- `dist/game/data/phantoms/semantic/custom/my-social-topics.xml` — social/off-topic patterns;
- `dist/game/data/phantoms/conversation/custom/my-phrases.xml` — варианты ответов;
- `dist/game/data/phantoms/conversation/custom/my-profanity.xml` — contextual profanity;
- `dist/game/data/phantoms/conversation/custom/my-mature-dialogue.xml` — отдельные mature-фразы.

После редактирования нужен штатный перезапуск Phantom services, но Java recompilation не нужна. Loader читает UTF-8, проверяет фиксированные пути, версию, допустимые атрибуты, лимиты, коллизии и XXE.

## Примеры

Новый сленговый алиас в `my-slang.xml`:

```xml
<slang version="1">
    <alias from="хай" to="привет" override="false"/>
</slang>
```

Новая social topic phrase в `my-social-topics.xml` использует только существующий разговорный act:

```xml
<socialTopics version="1">
    <pattern id="music.custom.question" topic="music" act="smalltalk.reply"
             phrase="что у тебя сейчас играет" salience="100"
             ttlMinutes="0" priority="850" override="false"/>
</socialTopics>
```

Ещё один ответ про музыку в `my-phrases.xml`:

```xml
<phrases version="1">
    <template id="smalltalk.music.custom.01" act="smalltalk.reply"
              band="UNKNOWN" register="CASUAL" profanity="NONE"
              text="Сегодня хочется включить что-нибудь спокойное."
              override="false"/>
</phrases>
```

Явное переопределение core-фразы возможно только по тому же `id` и только с `override="true"`:

```xml
<phrases version="1">
    <template id="greet.01" act="greet.reply"
              band="UNKNOWN" register="NEUTRAL" profanity="NONE"
              text="Привет. Рад нашей встрече." override="true"/>
</phrases>
```

Contextual profanity в `my-profanity.xml`:

```xml
<profanity version="1">
    <entry id="profanity.custom.surprise.01" level="CONTEXTUAL"
           acts="surprise.reply" text="Вот это, блин, поворот."
           override="false"/>
</profanity>
```

Mature-фраза хранится отдельно; атрибут `mature` там не нужен, потому что весь файл mature-only:

```xml
<matureDialogue version="1">
    <template id="flirt.mature.custom.01" act="flirt.light"
              band="TRUSTED" register="CASUAL" profanity="NONE"
              text="В привате я могу быть откровеннее." override="false"/>
</matureDialogue>
```

## Если XML не загружается

Сначала проверьте, что файл остаётся UTF-8, корневой элемент и `version="1"` сохранены, а имена enum написаны в верхнем регистре. Новый `id` должен быть уникальным; замена существующего `id` требует `override="true"`. Неизвестный атрибут, лишний XML-элемент, DOCTYPE/ENTITY, превышение лимита или попытка смешать social phrase с игровой командой отклоняют слой целиком. Причина появляется в startup diagnostics; исправьте указанный `my-*.xml` и перезапустите services.

Личные предпочтения и настроение сохраняются только как ограниченные structured facts в generic profile component `conversation.personal` schema 1. Исходные сообщения и raw chat log не сохраняются.
