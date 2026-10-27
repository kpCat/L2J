# Паспорт независимой генерации Phantom World v4

Контракт `pw-semantic-batch/1`. Этот файл самодостаточен: прикрепите только его в новом чате ChatGPT. Версия реестра: `187dff03c830ca90cad69feb352c15092c96d2c46e74fa5554fca063cc460518`. Паспорт создаётся из того же машинного реестра и допустимых значений, что проверяет импортёр.

## Инструкция для нового ChatGPT

По простому русскому запросу пользователя выбери одну точную leaf-категорию из таблицы ниже и нужный режим. Например: «200 смешных ответов про завышенные цены на эпик» → `hybrid.trade.absurd_offers`, REPLY. «200 фраз игрока про дорогой эпик, которые надо понимать» → та же категория, TRIGGER. «20 благодарностей за полученную помощь» → `social.friendship.support`, INITIATIVE, событие `pvp.help.received`. Если подходящих категорий несколько, уточни смысл. Если категории нет, верни предложение новой категории для отдельного review; не объявляй его готовым импортируемым файлом.

Создай настоящий скачиваемый файл `.jsonl`: строгий UTF-8 без BOM, переводы строк LF, завершающий LF. Один HEADER и ровно запрошенное число отдельных JSON объектов. Не возвращай только fenced Markdown, не добавляй комментарии, пустые строки после HEADER, объяснения или ID внутрь файла. Если чат не умеет создавать вложения, прямо сообщи об этом ограничении; не выдавай текстовый блок за созданный файл. До передачи проверь существование файла, JSON каждой строки, count, отсутствие дублей и разнообразие. Итоговый файл всё равно проверит локальный импортёр, гарантию за него не выдумывай.

Один файл — одна категория и один режим. HEADER содержит ровно `schema,category,mode,act,language,count`. schema=`pw-semantic-batch/1`, language=`ru`, act бери из таблицы категории для любого режима. count — целое 1..10000 (обычно 100..300), равно числу записей. Не придумывай серийные номера, act, intent, голоса или события.

## Режимы и поля

TRIGGER — входящая фраза реального игрока, которую следует распознавать; не произносится фантомом. Обязательны `text,intent`; intent бери из таблицы категории. Допустимы только дополнительные `tags,ambiguity`. ambiguity: `NONE`, `POSSIBLE_JOKE`, `POSSIBLE_THREAT`; по умолчанию NONE. Распознавание точное после NFKC, нижнего регистра и свёртки пробелов; это не языковая модель. Functional команды имеют приоритет.

REPLY — возможный исходящий ответ. Обязательны `text,stance`. Допустимы только дополнительные `tone,relations,voices,facets,profanity,maturity,tags,weight`.

INITIATIVE — исходящая фраза только по подтверждённому событию существующего владельца игры. Обязательны `text,stance,requiredEvent`; дополнительные поля как у REPLY. Только коды событий из таблицы ниже. Нельзя выдавать таймер, фразу игрока, выдуманную добычу или награду за событие. Одни REPLY не обучают пониманию входящих сообщений; для него нужен отдельный TRIGGER batch.

stance: `AMUSED`, `NEUTRAL`, `OFFENDED`, `SET_BOUNDARY`, `SUPPORTIVE`, `AGGRESSIVE_BANTER`, `DISENGAGE`, `CURIOUS`, `FLIRT`, `REFUSE`.

tone: `CALM`, `PLAYFUL`, `DRY`, `ROUGH`, `WARM`, `SARCASTIC`, `SHY`, `FORMAL`, `DIRECT`; по умолчанию CALM.

relations: массив из `UNKNOWN`, `NEUTRAL`, `FAMILIAR`, `TRUSTED`, `RIVAL`, `TENSE`, `HOSTILE`; по умолчанию [UNKNOWN].

voices: массив точных ключей из таблицы голосов либо [ANY]; ANY не смешивать с другими. По умолчанию [ANY]. Не создавай отдельную копию общего корпуса на каждый голос.

facets: объект с любым подмножеством ключей `aggression`, `sarcasm`, `humor`, `reserved`, `swagger`, `timidity`, `romance`, `roughness`, `cunning`, `gossip`; целые пороги 0..100. По умолчанию {} (без порога).

profanity: `NONE`, `MILD`, `STRONG`; по умолчанию NONE. maturity: `GENERAL`, `MATURE`; по умолчанию GENERAL. weight: целое 1..10, по умолчанию 1. tags: необязательный массив 1..16 разных ASCII ключей `[a-z][a-z0-9_.-]{0,63}`, например irony,bargain,refusal. Если тегов нет, поле опусти. relations/voices также 1..16 неповторяющихся элементов. Никаких null или лишних полей.

Включи в запрошенное число строк безопасный fallback для каждой исходящей комбинации category/mode/stance/requiredEvent и отношений: voices [ANY], без facets, profanity NONE, maturity GENERAL, tone CALM (либо DRY/WARM/SHY/FORMAL/DIRECT). Обычный спокойный ответ уже подходит; один fallback может перечислять все допустимые relations. Без него файл можно принять для редактирования, но build целого пака будет отклонён: редкий подходящий голос или контекст не должен теряться в большом корпусе. Не повышай запрошенный count ради fallback.

Runtime выполняет ограниченный индексированный подбор, учитывает недавние ответы и может промолчать, если безопасные неповторённые варианты исчерпаны. Это не обещание полного перебора всех потенциально подходящих фраз.

## Текст и безопасность

Пиши естественно по-русски, прямой читаемой кириллицей. Допускаются русские/украинские и латинские буквы, обычная пунктуация, ASCII цифры. TRIGGER: 1..1000 UTF-16 символов, только буквальный входящий текст, без placeholders. REPLY/INITIATIVE: не более 100 Unicode codepoints и 240 UTF-8 bytes после подстановки. Только в REPLY/INITIATIVE разрешён placeholder `{name}`; заложи до 64 символов и 192 bytes для имени. Проще писать без placeholders и нейтрально по полу. Нельзя TAB/CR/LF внутри text, управляющие/скрытые символы, испорченный Unicode, HTML/XML/скрипты, обратные кавычки и обратный слеш, URL, credentials или куски исходников. Строка JSON не может превышать 16 KiB, файл 8 MiB, глубина JSON 8. Дубли ключей JSON запрещены.

`{name}` означает подтверждённое имя самого фантома, не имя игрока-собеседника. Не используй эту подстановку как обращение к чужому персонажу.

Уникальность проверяется глобально после Unicode NFKC, нижнего регистра и свёртки пробелов. Внутри batch дубль отклоняет файл. Повторный импорт тех же данных даёт ноль новых записей; противоречивые метаданные для того же текста отклоняются. Не делай разнообразие простой нумерацией одинаковой фразы: варьируй речевую цель, лексику и ситуацию. Лексическая близость вызывает предупреждение для человека, не автоматическое утверждение о тождестве смысла.

Мат и грубый игровой юмор допустимы как размеченный художественный текст; intensity собственного голоса не означает терпимость к чужим оскорблениям. Сексуальный юмор требует MATURE; неизвестное согласие реального игрока не выдумывай. Угрозы в реальной жизни не одобряй и не усиливай: возможны SET_BOUNDARY, DISENGAGE, REFUSE. Шутка про покупку не совершает покупку, не меняет деньги, квесты или инвентарь. stance — намерение реплики, не прямое право менять social state. Не утверждай реальные имена/цены/предметы/места/факты, если их нет в подтверждённом контексте. Общие ответы должны иметь безопасный нейтральный вариант ANY/UNKNOWN/NONE/GENERAL.

## Пример готового файла

Ниже только учебное представление; пользователю передай настоящий файл.

```jsonl
{"schema":"pw-semantic-batch/1","category":"hybrid.trade.absurd_offers","mode":"REPLY","act":"hybrid.trade.absurd_offers.reply","language":"ru","count":1}
{"text":"За такой лот ещё полгорода попроси.","stance":"AMUSED","tone":"PLAYFUL","tags":["irony"]}
```

## Реестр leaf-категорий

| Категория | Русское название | act для HEADER | intent для TRIGGER |
|---|---|---|---|
| hybrid.absurd.chaotic_barter | безумный торг | hybrid.absurd.chaotic_barter.reply | hybrid.absurd.chaotic_barter.observe |
| hybrid.absurd.life_as_quest | жизнь как квест | hybrid.absurd.life_as_quest.reply | hybrid.absurd.life_as_quest.observe |
| hybrid.absurd.meme_crossovers | смешение мемов и игры | hybrid.absurd.meme_crossovers.reply | hybrid.absurd.meme_crossovers.observe |
| hybrid.life.game_addiction_jokes | шутки о гринде и реальной жизни | hybrid.life.game_addiction_jokes.reply | hybrid.life.game_addiction_jokes.observe |
| hybrid.life.pets_as_summons | питомцы как саммоны | hybrid.life.pets_as_summons.reply | hybrid.life.pets_as_summons.observe |
| hybrid.life.relationships_vs_l2 | личная жизнь против игры | hybrid.life.relationships_vs_l2.reply | hybrid.life.relationships_vs_l2.observe |
| hybrid.life.sleep_vs_raid | сон или рейд | hybrid.life.sleep_vs_raid.reply | hybrid.life.sleep_vs_raid.observe |
| hybrid.pvp.offline_threats | офлайн угрозы в PvP | hybrid.pvp.offline_threats.reply | hybrid.pvp.offline_threats.observe |
| hybrid.pvp.schoolyard | школьные разборки через игровую драку | hybrid.pvp.schoolyard.reply | hybrid.pvp.schoolyard.observe |
| hybrid.pvp.sports_metaphors | спорт как PvP | hybrid.pvp.sports_metaphors.reply | hybrid.pvp.sports_metaphors.observe |
| hybrid.romance.adena_flirting | подкаты с аденой | hybrid.romance.adena_flirting.reply | hybrid.romance.adena_flirting.observe |
| hybrid.romance.epic_gifts | эпик как подарок | hybrid.romance.epic_gifts.reply | hybrid.romance.epic_gifts.observe |
| hybrid.romance.noblesse_love | романтика ноблесса | hybrid.romance.noblesse_love.reply | hybrid.romance.noblesse_love.observe |
| hybrid.trade.absurd_offers | абсурдные предложения в торговле | hybrid.trade.absurd_offers.reply | hybrid.trade.absurd_offers.observe |
| hybrid.trade.life_for_adena | жизнь в обмен на адену | hybrid.trade.life_for_adena.reply | hybrid.trade.life_for_adena.observe |
| hybrid.trade.real_money_jokes | шутки про реальные деньги | hybrid.trade.real_money_jokes.reply | hybrid.trade.real_money_jokes.observe |
| hybrid.trade.romantic_barter | флирт через игровые покупки | hybrid.trade.romantic_barter.reply | hybrid.trade.romantic_barter.observe |
| hybrid.work.boss_as_rb | начальник как рейдбосс | hybrid.work.boss_as_rb.reply | hybrid.work.boss_as_rb.observe |
| hybrid.work.clan_as_job | клан как работа | hybrid.work.clan_as_job.reply | hybrid.work.clan_as_job.observe |
| hybrid.work.loot_as_salary | дроп как зарплата | hybrid.work.loot_as_salary.reply | hybrid.work.loot_as_salary.observe |
| hybrid.work.raid_as_shift | рейд как рабочая смена | hybrid.work.raid_as_shift.reply | hybrid.work.raid_as_shift.observe |
| life.daily.chores | бытовуха | life.daily.chores.reply | life.daily.chores.observe |
| life.daily.neighbors | соседи | life.daily.neighbors.reply | life.daily.neighbors.observe |
| life.daily.sleep_schedule | режим дня | life.daily.sleep_schedule.reply | life.daily.sleep_schedule.observe |
| life.daily.weather | погода | life.daily.weather.reply | life.daily.weather.observe |
| life.family.kids | дети | life.family.kids.reply | life.family.kids.observe |
| life.family.parents | родители | life.family.parents.reply | life.family.parents.observe |
| life.family.partner | отношения с партнёром | life.family.partner.reply | life.family.partner.observe |
| life.family.pets | домашние животные | life.family.pets.reply | life.family.pets.observe |
| life.food.cooking | готовка | life.food.cooking.reply | life.food.cooking.observe |
| life.food.delivery | доставка еды | life.food.delivery.reply | life.food.delivery.observe |
| life.food.hunger | голод | life.food.hunger.reply | life.food.hunger.observe |
| life.health.exercise | спорт | life.health.exercise.reply | life.health.exercise.observe |
| life.health.fatigue | усталость | life.health.fatigue.reply | life.health.fatigue.observe |
| life.health.sleep | сон | life.health.sleep.reply | life.health.sleep.observe |
| life.health.stress | стресс | life.health.stress.reply | life.health.stress.observe |
| life.leisure.films | кино | life.leisure.films.reply | life.leisure.films.observe |
| life.leisure.memes | мемы | life.leisure.memes.reply | life.leisure.memes.observe |
| life.leisure.music | музыка | life.leisure.music.reply | life.leisure.music.observe |
| life.leisure.travel | путешествия | life.leisure.travel.reply | life.leisure.travel.observe |
| life.leisure.weekend | выходные | life.leisure.weekend.reply | life.leisure.weekend.observe |
| life.love.breakups | расставания | life.love.breakups.reply | life.love.breakups.observe |
| life.love.dating | знакомства | life.love.dating.reply | life.love.dating.observe |
| life.love.flirting | флирт | life.love.flirting.reply | life.love.flirting.observe |
| life.love.jealousy | ревность | life.love.jealousy.reply | life.love.jealousy.observe |
| life.money.budget | финансовые трудности | life.money.budget.reply | life.money.budget.observe |
| life.money.debts | долги | life.money.debts.reply | life.money.debts.observe |
| life.money.purchases | покупки | life.money.purchases.reply | life.money.purchases.observe |
| life.school.exams | экзамены | life.school.exams.reply | life.school.exams.observe |
| life.school.homework | домашка | life.school.homework.reply | life.school.homework.observe |
| life.school.teachers | преподаватели | life.school.teachers.reply | life.school.teachers.observe |
| life.work.boss | начальство | life.work.boss.reply | life.work.boss.observe |
| life.work.burnout | выгорание | life.work.burnout.reply | life.work.burnout.observe |
| life.work.deadlines | дедлайны | life.work.deadlines.reply | life.work.deadlines.observe |
| life.work.salary | зарплата | life.work.salary.reply | life.work.salary.observe |
| life.work.shift | смены | life.work.shift.reply | life.work.shift.observe |
| lineage.clan.drama | драмы в клане | lineage.clan.drama.reply | lineage.clan.drama.observe |
| lineage.clan.leaders | кланлидер | lineage.clan.leaders.reply | lineage.clan.leaders.observe |
| lineage.clan.recruitment | набор в клан | lineage.clan.recruitment.reply | lineage.clan.recruitment.observe |
| lineage.clan.sieges | осады | lineage.clan.sieges.reply | lineage.clan.sieges.observe |
| lineage.classes.choice | выбор профессии | lineage.classes.choice.reply | lineage.classes.choice.observe |
| lineage.classes.noblesse | ноблесс | lineage.classes.noblesse.reply | lineage.classes.noblesse.observe |
| lineage.classes.skills | обсуждение скиллов | lineage.classes.skills.reply | lineage.classes.skills.observe |
| lineage.classes.subclass | сабклассы | lineage.classes.subclass.reply | lineage.classes.subclass.observe |
| lineage.classes.summons | саммоны и петы | lineage.classes.summons.reply | lineage.classes.summons.observe |
| lineage.commerce.bargaining | торг | lineage.commerce.bargaining.reply | lineage.commerce.bargaining.observe |
| lineage.commerce.manufacture | частное производство | lineage.commerce.manufacture.reply | lineage.commerce.manufacture.observe |
| lineage.commerce.offline | оффлайн лавки | lineage.commerce.offline.reply | lineage.commerce.offline.observe |
| lineage.commerce.prices | цены | lineage.commerce.prices.reply | lineage.commerce.prices.observe |
| lineage.commerce.scammers | мошенничество | lineage.commerce.scammers.reply | lineage.commerce.scammers.observe |
| lineage.craft.failures | неудачный крафт | lineage.craft.failures.reply | lineage.craft.failures.observe |
| lineage.craft.materials | материалы | lineage.craft.materials.reply | lineage.craft.materials.observe |
| lineage.craft.recipes | рецепты | lineage.craft.recipes.reply | lineage.craft.recipes.observe |
| lineage.craft.workshop | крафт | lineage.craft.workshop.reply | lineage.craft.workshop.observe |
| lineage.enchant.fail | сломанная вещь | lineage.enchant.fail.reply | lineage.enchant.fail.observe |
| lineage.enchant.gamble | риск и азарт | lineage.enchant.gamble.reply | lineage.enchant.gamble.observe |
| lineage.enchant.streaks | серии везения | lineage.enchant.streaks.reply | lineage.enchant.streaks.observe |
| lineage.enchant.success | удачная заточка | lineage.enchant.success.reply | lineage.enchant.success.observe |
| lineage.farming.competition | конкуренция за спот | lineage.farming.competition.reply | lineage.farming.competition.observe |
| lineage.farming.failed_drop | пустой дроп | lineage.farming.failed_drop.reply | lineage.farming.failed_drop.observe |
| lineage.farming.loot | редкий дроп | lineage.farming.loot.reply | lineage.farming.loot.observe |
| lineage.farming.mana | нехватка маны | lineage.farming.mana.reply | lineage.farming.mana.observe |
| lineage.farming.pickup | подбор дропа | lineage.farming.pickup.reply | lineage.farming.pickup.observe |
| lineage.farming.shots | соски и расходники | lineage.farming.shots.reply | lineage.farming.shots.observe |
| lineage.farming.spoiling | спойл и свип | lineage.farming.spoiling.reply | lineage.farming.spoiling.observe |
| lineage.farming.spot | выбор спота | lineage.farming.spot.reply | lineage.farming.spot.observe |
| lineage.leveling.alts | прокачка твинков | lineage.leveling.alts.reply | lineage.leveling.alts.observe |
| lineage.leveling.delevel | потеря опыта | lineage.leveling.delevel.reply | lineage.leveling.delevel.observe |
| lineage.leveling.fatigue | усталость от кача | lineage.leveling.fatigue.reply | lineage.leveling.fatigue.observe |
| lineage.leveling.grind | долгий кач | lineage.leveling.grind.reply | lineage.leveling.grind.observe |
| lineage.leveling.newbies | первые уровни | lineage.leveling.newbies.reply | lineage.leveling.newbies.observe |
| lineage.leveling.progress | счастье от уровня | lineage.leveling.progress.reply | lineage.leveling.progress.observe |
| lineage.party.buffs | бафы и танцы | lineage.party.buffs.reply | lineage.party.buffs.observe |
| lineage.party.disband | распад пати | lineage.party.disband.reply | lineage.party.disband.observe |
| lineage.party.healer | работа хилера | lineage.party.healer.reply | lineage.party.healer.observe |
| lineage.party.mistakes | ошибки участников | lineage.party.mistakes.reply | lineage.party.mistakes.observe |
| lineage.party.recruitment | набор группы | lineage.party.recruitment.reply | lineage.party.recruitment.observe |
| lineage.party.roles | распределение ролей | lineage.party.roles.reply | lineage.party.roles.observe |
| lineage.party.tank_hate | танк и агр | lineage.party.tank_hate.reply | lineage.party.tank_hate.observe |
| lineage.party.voice_chat | общение в группе | lineage.party.voice_chat.reply | lineage.party.voice_chat.observe |
| lineage.pvp.duel | дуэли | lineage.pvp.duel.reply | lineage.pvp.duel.observe |
| lineage.pvp.pks | ПК и карма | lineage.pvp.pks.reply | lineage.pvp.pks.observe |
| lineage.pvp.retreat | отступление | lineage.pvp.retreat.reply | lineage.pvp.retreat.observe |
| lineage.pvp.revenge | месть | lineage.pvp.revenge.reply | lineage.pvp.revenge.observe |
| lineage.pvp.rivalry | соперничество | lineage.pvp.rivalry.reply | lineage.pvp.rivalry.observe |
| lineage.pvp.trash_talk | провокации | lineage.pvp.trash_talk.reply | lineage.pvp.trash_talk.observe |
| lineage.quests.grind_items | квестовый дроп | lineage.quests.grind_items.reply | lineage.quests.grind_items.observe |
| lineage.quests.minigames | миниигры | lineage.quests.minigames.reply | lineage.quests.minigames.observe |
| lineage.quests.routines | повторяемые квесты | lineage.quests.routines.reply | lineage.quests.routines.observe |
| lineage.raids.drop_split | распределение дропа | lineage.raids.drop_split.reply | lineage.raids.drop_split.observe |
| lineage.raids.epics | эпики | lineage.raids.epics.reply | lineage.raids.epics.observe |
| lineage.raids.healing | хил на рейде | lineage.raids.healing.reply | lineage.raids.healing.observe |
| lineage.raids.recruitment | сбор рейда | lineage.raids.recruitment.reply | lineage.raids.recruitment.observe |
| lineage.raids.tank | танкование рейдбосса | lineage.raids.tank.reply | lineage.raids.tank.observe |
| social.banter.absurd | абсурдный юмор | social.banter.absurd.reply | social.banter.absurd.observe |
| social.banter.double_meanings | двусмысленности | social.banter.double_meanings.reply | social.banter.double_meanings.observe |
| social.banter.friendly | дружеские подколы | social.banter.friendly.reply | social.banter.friendly.observe |
| social.banter.rough | грубоватые шутки | social.banter.rough.reply | social.banter.rough.observe |
| social.banter.sarcasm | сарказм | social.banter.sarcasm.reply | social.banter.sarcasm.observe |
| social.boundaries.personal_attacks | реакция на личные нападки | social.boundaries.personal_attacks.reply | social.boundaries.personal_attacks.observe |
| social.boundaries.profanity | отношение к мату | social.boundaries.profanity.reply | social.boundaries.profanity.observe |
| social.boundaries.real_threats | реакция на угрозы в реальности | social.boundaries.real_threats.reply | social.boundaries.real_threats.observe |
| social.boundaries.sexual_humor | отношение к пошлости | social.boundaries.sexual_humor.reply | social.boundaries.sexual_humor.observe |
| social.conflict.apology | извинения | social.conflict.apology.reply | social.conflict.apology.observe |
| social.conflict.boundaries | личные границы | social.conflict.boundaries.reply | social.conflict.boundaries.observe |
| social.conflict.deescalation | успокоение конфликта | social.conflict.deescalation.reply | social.conflict.deescalation.observe |
| social.conflict.insult | оскорбления | social.conflict.insult.reply | social.conflict.insult.observe |
| social.conflict.reconcile | примирение | social.conflict.reconcile.reply | social.conflict.reconcile.observe |
| social.conversation.followups | уточнение темы | social.conversation.followups.reply | social.conversation.followups.observe |
| social.conversation.interruptions | перебивания | social.conversation.interruptions.reply | social.conversation.interruptions.observe |
| social.conversation.silence | молчание | social.conversation.silence.reply | social.conversation.silence.observe |
| social.conversation.topic_switch | смена темы | social.conversation.topic_switch.reply | social.conversation.topic_switch.observe |
| social.flirt.bold | дерзкий флирт | social.flirt.bold.reply | social.flirt.bold.observe |
| social.flirt.light | лёгкий флирт | social.flirt.light.reply | social.flirt.light.observe |
| social.flirt.rejection | вежливый отказ | social.flirt.rejection.reply | social.flirt.rejection.observe |
| social.friendship.jealousy | ревность к друзьям | social.friendship.jealousy.reply | social.friendship.jealousy.observe |
| social.friendship.loyalty | преданность | social.friendship.loyalty.reply | social.friendship.loyalty.observe |
| social.friendship.support | поддержка друга | social.friendship.support.reply | social.friendship.support.observe |
| social.friendship.trust | доверие | social.friendship.trust.reply | social.friendship.trust.observe |
| social.greeting.casual | простое приветствие | social.greeting.casual.reply | social.greeting.casual.observe |
| social.greeting.return | возвращение знакомого | social.greeting.return.reply | social.greeting.return.observe |
| social.greeting.teasing | приветствие с подколом | social.greeting.teasing.reply | social.greeting.teasing.observe |

## Голоса и независимые личные границы

Числа 0..100: терпимость к мату, личным оскорблениям, сексуальному юмору, стиль конфликта, порог доверия. Это независимые параметры, не разрешение использовать такую речь. Legacy: calm/open/direct/empathetic. Последний столбец содержит десять style facets.

| Ключ | Название | Legacy | Мат | Оскорбления | Пошлость | Конфликт | Доверие | Facets |
|---|---|---|---:|---:|---:|---:|---:|---|
| bold_rival | Дерзкий соперник | direct | 80 | 70 | 35 | 85 | 75 | aggression=85,cunning=50,gossip=20,humor=55,reserved=25,romance=25,roughness=80,sarcasm=70,swagger=95,timidity=5 |
| calm | Спокойный | calm | 20 | 20 | 15 | 20 | 60 | aggression=15,cunning=35,gossip=30,humor=40,reserved=65,romance=25,roughness=15,sarcasm=25,swagger=20,timidity=30 |
| cautious_trader | Осторожный торговец | calm | 40 | 20 | 10 | 40 | 85 | aggression=25,cunning=95,gossip=45,humor=40,reserved=70,romance=10,roughness=25,sarcasm=45,swagger=30,timidity=20 |
| cheerful_optimist | Весёлый оптимист | open | 60 | 65 | 45 | 20 | 30 | aggression=15,cunning=20,gossip=65,humor=90,reserved=10,romance=45,roughness=30,sarcasm=25,swagger=50,timidity=10 |
| curious_scholar | Любознательный знаток | calm | 20 | 20 | 15 | 20 | 65 | aggression=10,cunning=70,gossip=35,humor=45,reserved=65,romance=20,roughness=10,sarcasm=40,swagger=25,timidity=25 |
| direct | Прямолинейный | direct | 55 | 25 | 25 | 65 | 65 | aggression=55,cunning=25,gossip=15,humor=30,reserved=45,romance=15,roughness=65,sarcasm=35,swagger=55,timidity=10 |
| dry_wit | Сухой острослов | calm | 50 | 35 | 30 | 40 | 60 | aggression=25,cunning=65,gossip=30,humor=65,reserved=70,romance=20,roughness=30,sarcasm=90,swagger=35,timidity=15 |
| empathetic | Чуткий | empathetic | 25 | 15 | 20 | 10 | 45 | aggression=10,cunning=20,gossip=45,humor=45,reserved=30,romance=55,roughness=10,sarcasm=15,swagger=15,timidity=30 |
| formal_diplomat | Учтивый дипломат | calm | 5 | 10 | 5 | 15 | 65 | aggression=10,cunning=75,gossip=30,humor=25,reserved=80,romance=20,roughness=5,sarcasm=30,swagger=35,timidity=15 |
| gossip_weaver | Любитель слухов | open | 45 | 50 | 45 | 25 | 45 | aggression=15,cunning=70,gossip=95,humor=70,reserved=5,romance=50,roughness=25,sarcasm=55,swagger=40,timidity=15 |
| loyal_companion | Верный товарищ | empathetic | 65 | 50 | 30 | 35 | 30 | aggression=35,cunning=25,gossip=40,humor=60,reserved=25,romance=35,roughness=45,sarcasm=25,swagger=40,timidity=15 |
| open | Общительный | open | 45 | 40 | 35 | 35 | 40 | aggression=25,cunning=35,gossip=75,humor=70,reserved=15,romance=45,roughness=30,sarcasm=40,swagger=45,timidity=15 |
| patient_mentor | Терпеливый наставник | empathetic | 35 | 20 | 15 | 20 | 50 | aggression=15,cunning=45,gossip=25,humor=40,reserved=50,romance=15,roughness=20,sarcasm=20,swagger=30,timidity=15 |
| playful_tease | Игривый насмешник | open | 65 | 75 | 65 | 40 | 40 | aggression=25,cunning=55,gossip=70,humor=85,reserved=10,romance=65,roughness=45,sarcasm=75,swagger=60,timidity=10 |
| proud_knight | Гордый рыцарь | direct | 20 | 5 | 10 | 80 | 90 | aggression=60,cunning=25,gossip=10,humor=20,reserved=70,romance=30,roughness=45,sarcasm=20,swagger=90,timidity=5 |
| quiet_veteran | Молчаливый ветеран | calm | 75 | 30 | 20 | 60 | 80 | aggression=50,cunning=60,gossip=5,humor=30,reserved=95,romance=10,roughness=70,sarcasm=50,swagger=35,timidity=10 |
| romantic_dreamer | Романтичный мечтатель | empathetic | 20 | 15 | 60 | 10 | 55 | aggression=5,cunning=20,gossip=40,humor=45,reserved=35,romance=95,roughness=5,sarcasm=15,swagger=20,timidity=45 |
| rough_joker | Грубоватый шутник | open | 90 | 75 | 70 | 55 | 40 | aggression=40,cunning=40,gossip=55,humor=90,reserved=15,romance=35,roughness=85,sarcasm=65,swagger=60,timidity=10 |
| rough_sensitive | Резкий с личными границами | direct | 90 | 5 | 10 | 70 | 85 | aggression=55,cunning=35,gossip=15,humor=40,reserved=50,romance=10,roughness=95,sarcasm=45,swagger=55,timidity=10 |
| shy_observer | Робкий наблюдатель | calm | 10 | 10 | 5 | 10 | 90 | aggression=5,cunning=30,gossip=20,humor=35,reserved=90,romance=40,roughness=5,sarcasm=15,swagger=5,timidity=95 |
| silver_tongue | Обаятельный переговорщик | open | 50 | 45 | 50 | 25 | 50 | aggression=20,cunning=85,gossip=75,humor=65,reserved=20,romance=70,roughness=20,sarcasm=50,swagger=65,timidity=10 |
| skeptical_realist | Скептичный реалист | direct | 65 | 35 | 25 | 55 | 80 | aggression=40,cunning=70,gossip=20,humor=40,reserved=65,romance=10,roughness=50,sarcasm=80,swagger=35,timidity=10 |
| stern_guard | Строгий защитник | direct | 25 | 10 | 10 | 80 | 80 | aggression=65,cunning=40,gossip=10,humor=15,reserved=80,romance=10,roughness=60,sarcasm=20,swagger=60,timidity=5 |
| warm_friend | Тёплый друг | empathetic | 50 | 60 | 45 | 15 | 30 | aggression=10,cunning=25,gossip=60,humor=65,reserved=15,romance=60,roughness=25,sarcasm=25,swagger=20,timidity=20 |

## Подтверждённые события

Источник: PhantomPvpSocialBridge.EventKind → штатная память Social owner; только недавнее непогашенное событие с evidence, а не текстовое обещание.

| requiredEvent | Значение |
|---|---|
| pvp.attack.received | Получена атака |
| pvp.death.suffered | Пережитая смерть |
| pvp.help.received | Получена помощь |
| pvp.kill.caused | Подтверждённая победа |

## После генерации

Импортируй файл в offline Studio или CLI: проверить → просмотреть → принять в отдельный workspace → build → verify. Это создаёт immutable stage, не устанавливает его в сервер. Java content consumer проверяется отдельно; C# preview приблизителен. Для нового leaf оператор отдельно делает taxonomy propose → review хеша → approve → export-passport; исходники Java не нужны. Этот паспорт описывает доступный реестр, не обещает, что все категории уже имеют TRIGGER/REPLY/INITIATIVE coverage.
