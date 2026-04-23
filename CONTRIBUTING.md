# Contributing

Thanks for wanting to help out with Cobblemon Showdown.

## Getting started

- Fork the repo and clone your fork
- JDK 17 is required (Forge 1.20.1 constraint)
- Use the gradle wrapper: `./gradlew build`
- `./gradlew runClient` / `./gradlew runServer` to test changes in-dev
- `./gradlew genIntellijRuns` (or `genEclipseRuns`) to generate IDE run configs
- (Cobblemon)[https://gitlab.com/cable-mc/cobblemon] is a compile-time-only dependency in dev, drop a matching Cobblemon jar in `run/mods/` to actually run battles

## Submitting changes

- Branch off `main` and keep PRs focused on one thing
- Before opening a PR, run `./gradlew build` to confirm it runs

## Translations

New languages are very welcome. Lang files live in `src/main/resources/assets/cobblemon_showdown/lang/`. The structure follows the pattern established in the initial i18n PR (`en_us.json` + `ko_kr.json`).

**Adding a language:**

1. Copy `en_us.json` to `<locale>_<region>.json`, all lowercase (e.g. `fr_fr.json`, `ja_jp.json`, `pt_br.json`)
2. Preserve all `%s`, `%1$s`, `%2$s` format placeholders
3. Preserve Minecraft `§` color codes and any emoji or symbols (`★`, `⚔`, `✓`, etc.)

**Testing:**

Set your client language in Minecraft's language settings, launch with `./gradlew runClient`, and walk through battles, the PC sort panel, pokemon tooltips, `/showdown` commands, and the keybinds menu. Any missing keys will fall back to English.
