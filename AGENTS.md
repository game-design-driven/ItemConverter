# AGENTS.md

## Scope
- Applies to the entire repository at `/Users/kcw/GitHub/ItemConverter`.
- No nested `AGENTS.md` files are present.
- This project is a Kotlin-based Minecraft mod for Forge 1.20.1.

## Project Snapshot
- Build system: Gradle (Groovy DSL) via wrapper `./gradlew`.
- Language/runtime: Kotlin + Java toolchain 17.
- Core plugin: `net.neoforged.moddev.legacyforge`.
- Serialization: `kotlinx.serialization`.
- Mod ID constant: `item_converter`.
- Root package: `settingdust.item_converter`.

## Build, Lint, and Test Commands
- Always use the wrapper: `./gradlew <task>`.
- Build everything: `./gradlew build`.
- Assemble jars only: `./gradlew assemble`.
- Run verification tasks: `./gradlew check`.
- Clean outputs: `./gradlew clean`.
- Compile main Kotlin only: `./gradlew compileKotlin`.
- Compile test Kotlin only: `./gradlew compileTestKotlin`.
- Run all tests: `./gradlew test`.
- Run a single test class: `./gradlew test --tests "package.ClassName"`.
- Run a single test method: `./gradlew test --tests "package.ClassName.methodName"`.
- Run tests by wildcard: `./gradlew test --tests "*ClassName*"`.
- Show available tasks: `./gradlew tasks --all`.
- Print computed mod version: `./gradlew printVersion`.

## Lint/Static Analysis Status
- No dedicated lint task is configured (`ktlint`, `detekt`, `spotless` are not present).
- Use `./gradlew check` as the baseline verification command.
- Treat Kotlin compiler warnings and Gradle warnings as actionable.

## Run Configurations
- Start client dev runtime: `./gradlew runClient`.
- Start server dev runtime: `./gradlew runServer`.
- Prepare client run assets/classpath: `./gradlew prepareClientRun`.
- Prepare server run assets/classpath: `./gradlew prepareServerRun`.

## Source Layout
- Main Kotlin sources: `src/main/kotlin`.
- Resources: `src/main/resources`.
- Entrypoint object: `settingdust.item_converter.ItemConverter`.
- Client-only code: `settingdust.item_converter.client`.
- Networking packets/channel: `settingdust.item_converter.networking`.
- AE2 compatibility: `settingdust.item_converter.compat.ae2`.

## Formatting Conventions
- Use 4-space indentation; no tabs.
- Keep braces on the same line for declarations and control blocks.
- Use blank lines between logical blocks, not between every statement.
- Prefer short expression bodies for trivial functions.
- Wrap long argument lists one argument per line.
- Keep trailing commas consistent with nearby code.
- Keep line length reasonable; wrap before readability drops.

## Import Conventions
- Do not use wildcard imports.
- Keep imports explicit and minimal.
- Remove unused imports.
- Follow IDE-default Kotlin ordering.
- Keep internal project imports (`settingdust.*`) consistent with existing style.

## Naming Conventions
- Packages: lowercase, underscore-separated where already used.
- Classes/objects/enums: `PascalCase`.
- Functions/properties: `camelCase`.
- Constants: `UPPER_SNAKE_CASE` with `const val` when possible.
- Boolean names: `isX`, `hasX`, `allowX`, `shouldX`.
- Files should match primary type or feature area.

## Types and Nullability
- Prefer `val` over `var` unless mutation is required.
- Add explicit types for public APIs and non-obvious generics.
- Use nullable types (`T?`) instead of sentinel values.
- Prefer safe calls (`?.`) and guarded `if` checks over `!!`.
- Use early returns for invalid state.
- For count multiplication (`count * outputCount`), use `Long` to avoid overflow.

## Error Handling and Logging
- Use `runCatching` for recoverable file/network packet paths.
- Do not use exceptions for normal control flow.
- Log unexpected failures via `ItemConverter.LOGGER`.
- Keep failure logs specific (context + operation).
- In packet handlers, validate all prerequisites before mutating state.

## Networking Rules
- Register packets in `Networking` with stable numeric IDs.
- Keep channel version (`VERSION`) changes intentional and coordinated.
- Keep serialization codec and runtime packet fields in sync.
- Packet handlers must:
  - enqueue server work,
  - validate sender/container/slot/item,
  - validate conversion targets,
  - mark packet handled,
  - broadcast container changes after mutation.
- Preserve current action semantics: `REPLACE`, `TO_INVENTORY`, `DROP`.

## Client/Server Boundaries
- Annotate client-only classes/objects with `@OnlyIn(Dist.CLIENT)`.
- Guard client init using `FMLEnvironment.dist == Dist.CLIENT`.
- Keep shared gameplay logic outside client-only packages.
- Keep AE2 class usage behind mod-loaded checks in compat code.

## Config and Serialization
- Config models are `@Serializable` data classes.
- Keep defaults in constructor parameters.
- Keep config files under `FMLPaths.CONFIGDIR`.
- Ensure config files exist before decode; initialize with `{}` when missing.
- Use shared JSON options:
  - `encodeDefaults = true`,
  - `prettyPrint = true`,
  - `ignoreUnknownKeys = true`.
- Persist changes with `json.encodeToStream`.
- Preserve compatibility by adding new fields with defaults.

## UI and Rendering Conventions
- Derive layout sizes from constants (`BORDER`, `SLOT_SIZE`, texture dims).
- Clamp positions with `coerceIn` against screen bounds.
- Keep render-loop logic allocation-light.
- Validate hovered slot/item before acting.
- Close conversion screens if the source item changes.
- Keep conversion input snapshot immutable per screen instance.

## Conversion Domain Rules
- Reject empty input stacks early.
- Only allow recipe types configured in `CommonConfig`.
- Preserve 1:N conversion output counts.
- Use special-tag prioritization rules when ordering targets.
- Compare item + components/tags for conversion validity.
- Avoid duplicate outputs; deduplicate by stable key.

## Dependency and Build File Rules
- Do not add dependencies without clear, immediate need.
- Keep optional compat integrations isolated under `compat`.
- Keep `gradle.properties` metadata aligned with resource expansion values.
- Do not hardcode release version strings in source; version comes from git.

## Current Test Suite Status
- No `src/test` sources are currently present.
- Test tasks exist and are runnable through Gradle.
- When adding tests, make them runnable by class and by method via `--tests`.

## Cursor/Copilot Rules
- `.cursor/rules/`: not found.
- `.cursorrules`: not found.
- `.github/copilot-instructions.md`: not found.

## Agent Execution Notes
- Read the target file/function before editing.
- Match existing package structure and feature boundaries.
- Keep changes local and direct; avoid unnecessary abstraction.
- Preserve packet IDs, config keys, and translation keys unless intentionally changing behavior.
- Prefer defensive checks around external/game-state inputs.
