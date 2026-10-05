# TestAdmin admin contract

User explicitly requested that `TestAdmin` become a real admin.
User's normal/main character `kpCat` must never be modified by this operation.

High Five facts from repository:
- `characters.accesslevel` is the per-character access field.
- AccessLevels.xml level 100 = `Master`, `isGM=true`,
  `giveDamage=true`, `takeAggro=true`, `gainExp=true`.
- Level 70 = Admin; level100 is selected here so the dedicated TestAdmin has full
  local test permissions.

Because current General.ini has GM startup hide/invisible/invulnerable enabled,
observe013 PRIVATE runtime overrides those flags to False so GM status does not
distort M1 observation.

Do not modify shipped General.ini.
