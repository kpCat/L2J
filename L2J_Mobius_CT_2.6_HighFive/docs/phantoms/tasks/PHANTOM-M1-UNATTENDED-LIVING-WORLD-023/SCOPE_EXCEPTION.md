# Bounded TASK023 scope

TASK.md и прямое сообщение пользователя разрешают связанные fixes одного M1 slice,
max20 production paths, max2 новых production classes и ограниченные дополнительные
существующие test paths из SOURCE_MAP. Это зафиксированное исключение из общего порога
8–10 файлов; отдельные artifact families и housekeeping не добавлены.

Exact allowlist: SOURCE_EXACT_ALLOWLIST.txt, 11 production + 7 test paths.
Новых production classes: 0. Дополнительных affected test paths: 4 из разрешённых 5:
NativeContextHandoff, Background, NativeEvidenceContinuation, ServerShutdownHandoff.
LivingWorld023, DynamicRecipientChecks и NativeFarmContinuation прямо перечислены.
Task-local scripts, diagnostics и evidence находятся только в этом TASK023 пакете.

Player.java изменён только в разрешённом doAutoLoot; Party.java — только item-drop
distribution; PhantomVisibleAutoPlay — только stock short/long range setting.
ThreadPool, EventDispatcher, PlayerStatus, QoL, GeoEngine, config gameplay rates,
combat engine и основные AI-политики не изменены. Новых framework/API dependencies нет.

Read-first: TASK/READ_FIRST/DESIGN/PLAN/RUNBOOK/SOURCE_MAP/SCENARIOS/ACCEPTANCE,
README/build.xml, исходные native wrappers, Party/Attackable reward code, headless
fixtures, existing clone/synthetic/graceful-stop scripts. AGENTS.md в предках/module
не найден. Повторный поиск не выполнялся. Переиспользованы ParticipantWork,
PhantomNativeWorkScope, versioned background codecs/RecoveryClaim, stock scheduler,
existing native skill tree и existing runtime ownership scripts. Java/Ant/JDK25.

Исторические task artifacts и основной checkout read-only. Private snapshots,
runtime config credentials, dumps SQL и clone DB не входят в Git staging.
Источник ограничения: TASK.md; утверждение gate пользователем не подменяется.
