# GOAL

На базе experiment/m1-candidate007-observe008 HEAD
`78cfd521174e383b5054efdc2ceb644c9d09646c`:

- реализовать fail-closed auto-attach реального IN_GAME Player к LocalPlayPilotService
  только при explicit allowlist private LocalPlay config;
- оставить существующий `.playtest arm` полностью рабочим как fallback;
- любой обычный реальный вход должен по-прежнему получать REAL_LOGIN ownership
  независимо от pilot allowlist;
- собрать новый private observation runtime с
  MaxMaterializedPhantoms=8 и PhantomPopulationActiveTarget=8;
- после server startup попросить пользователя вручную войти TestAdmin;
- без `.playtest arm` получить server-side ARMED_IDLE для TestAdmin;
- выполнить 10–15 минут фактического наблюдения и попытку 5 автономных farm cycles.

Никаких AI/history/ecology/persistence исправлений в этой задаче.
