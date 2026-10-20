# Read-only аудит исходного контракта High Five

Источник: kpCat/L2J, ветка feature/phantom-world.
Проверенный reference SHA: 3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25.
Это reference для чтения, НЕ required parent нового C#-проекта; не переключать/сбрасывать L2J к нему.

## Точки контракта
Внутри L2J_Mobius_CT_2.6_HighFive:
- java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java
- java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedConversationService.java
- dist/game/data/phantoms/semantic/humanized/v3/manifest.xml
- dist/game/data/phantoms/{semantic,conversation}/humanized/v3/segments/
- dist/game/data/phantoms/{semantic,conversation}/custom/
- test/java/org/l2jmobius/tests/phantoms/PhantomPost002SemanticV3Suite.java
- docs/phantoms/semantic/SEMANTIC_V3_SPARK_GENERATION_CONTRACT_RU.md

## Подтверждённое чтением кода
Загрузка: humanized v1 → v2 → v3 по manifest → шесть custom-файлов в фиксированном порядке.
У пользовательского overlay явный override; генератор не должен автоматически добавлять override=true.
V3: 64 сегмента, 1 MiB/сегмент, 32 MiB суммарно; комбинированный каталог до 8192 patterns, 32768 templates, 2048 aliases и 1024 profanity.
Нормализация: NFKC, ru lower, ё→е; буквы/цифры и фигурные скобки сохраняются, прочее разделяет слова.
Template содержит act, band, register, profanity, mature, text. Gender отсутствует в обычном TemplateBucket/select; пол есть в RuntimeIdentity/identity-ветке, это разные возможности.
Серверная conversation.personal не равна workspace редактора. Состояние отношений, память, игровые receipts и эффекты не создаются генератором.

## Различие трёх сущностей
1. Humanized text-only corpus — редактируемые кандидаты Studio.
2. Functional Semantic Pack и support/identity routing — вне scope генератора.
3. Реальный Java runtime — источник окончательной проверки, сейчас не запускается Studio.

Текущий reader Studio НЕ полная копия Java loader. Он инспектирует записи, custom precedence и provenance, но не доказывает всю schema validation, act gates, profanity gates, identity/classes и corpus assertions. PackPreview явно приблизительный; его ответы не считаются server-parity evidence.

Ссылки на первичные источники:
- https://github.com/kpCat/L2J/blob/3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25/L2J_Mobius_CT_2.6_HighFive/java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java
- https://github.com/kpCat/L2J/blob/3fd4aa5f29cf23c1c06cc91ae7b1016c820acb25/L2J_Mobius_CT_2.6_HighFive/dist/game/data/phantoms/semantic/humanized/v3/manifest.xml
- https://lmstudio.ai/docs/developer/openai-compat/structured-output
- https://lmstudio.ai/docs/developer/openai-compat
- https://learn.microsoft.com/en-us/dotnet/desktop/winforms/get-started/create-app-visual-studio

Проверка API/формата не означает, что конкретная локальная Gemma была запущена. Реальная генерация — NOT_RUN в этой среде.
