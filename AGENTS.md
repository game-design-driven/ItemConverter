# AGENTS.md

## Scope
- Applies to the entire repository.
- No nested AGENTS.md files exist.

## Build, Test, Lint (Gradle)
- Use the Gradle wrapper: `./gradlew`.
- Build: `./gradlew build`.
- Assemble only: `./gradlew assemble`.
- Clean: `./gradlew clean`.
- Run checks: `./gradlew check`.
- Unit tests: `./gradlew test` (no `src/test` currently).
- Single test class: `./gradlew test --tests "package.ClassName"`.
- Single test method: `./gradlew test --tests "package.ClassName.methodName"`.
- Inspect available tasks: `./gradlew tasks`.
- Lint: no dedicated lint task configured in `build.gradle.kts`.

## Project Stack
- Language: Kotlin (JVM) with Java toolchain 17.
- Build system: Gradle Kotlin DSL.
- Minecraft Forge mod using Unimined + KotlinForForge.
- Serialization: `kotlinx.serialization`.
- Logging: Log4j via `LogManager.getLogger()`.

## Source Layout
- Kotlin sources: `src/main/kotlin`.
- Package root: `settingdust.item_converter`.
- Client-only code under `settingdust.item_converter.client`.
- Network packets under `settingdust.item_converter.networking`.
- Compat integrations under `settingdust.item_converter.compat`.
- Resources under `src/main/resources`.

## Kotlin Formatting
- Indentation: 4 spaces.
- Braces on the same line as declarations.
- Blank lines between logical blocks and functions.
- Multi-line argument lists place one argument per line.
- Align closing parens with the start of the call.
- Use trailing commas only if already present nearby.
- Prefer KDoc (`/** */`) for public or complex APIs.

## Imports
- No wildcard imports.
- Use IntelliJ/Kotlin default ordering.
- Group imports by package origin without extra spacing unless existing style requires it.
- Keep `settingdust.*` imports after external libraries.

## Naming
- Packages: lowercase with underscores (e.g., `settingdust.item_converter`).
- Classes/objects: `PascalCase`.
- Functions/fields: `camelCase`.
- Boolean fields: `isX`, `hasX`, or `allowX`.
- Constants: `UPPER_SNAKE_CASE` with `const val`.
- File names match the primary class/object.

## Types and Nullability
- Prefer `val` over `var` for immutability.
- Use explicit types for public API surfaces and complex generics.
- Use Kotlin nullability (`?`) instead of sentinel values.
- Prefer safe calls (`?.`) and `let` over `!!`.
- Use `!!` only when upstream guarantees non-null.
- Favor early returns for invalid state.

## Error Handling
- Use `runCatching {}` or early returns for recoverable IO issues.
- Avoid throwing exceptions for control flow.
- Log unexpected states via `ItemConverter.LOGGER` when needed.
- Keep network handlers defensive: validate packet contents before use.

## Configuration & Serialization
- `ClientConfig` and `CommonConfig` are `@Serializable` data classes.
- Config JSON is stored in the Forge config dir via `FMLPaths.CONFIGDIR`.
- Keep defaults in data class constructors.
- Call `json.encodeToStream` after updates to persist changes.
- Prefer `Json { encodeDefaults = true; prettyPrint = true; ignoreUnknownKeys = true }`.

## Networking
- Packet registration lives in `settingdust.item_converter.networking.Networking`.
- Channel version is a simple string (`VERSION = "1"`).
- Use codecs for packet serialization.
- Keep packet IDs stable and sequential.

## Client vs Server
- Annotate client-only types with `@OnlyIn(Dist.CLIENT)`.
- Guard client initialization with `if (FMLEnvironment.dist == Dist.CLIENT)`.
- Keep server logic out of `client` package.

## UI/Screen Code
- Calculate layout values from constants (slot size, border).
- Use `coerceIn` for screen bounds.
- Keep UI state fields private.
- Avoid heavy allocations per frame.

## Collections and Iteration
- Use Kotlin collection builders (`mutableListOf`, `mutableSetOf`).
- Prefer `for` loops when avoiding extra allocations.
- Use `sortedWith(compareBy(...))` for multi-key sorting.
- Avoid repeated registry lookups inside hot loops when possible.

## Resource Locations and IDs
- Use `ItemConverter.id(path)` to build `ResourceLocation`s.
- Keep IDs lowercase and namespaced.

## Dependencies
- Do not add new dependencies without clear need.
- Keep compatibility code isolated under `compat`.

## Tests
- No test sources found under `src/test`.
- No test dependencies are declared in `build.gradle.kts`.
- Tests, when added, run via the `test` task.

## Cursor/Copilot Rules
- No `.cursor/rules`, `.cursorrules`, or `.github/copilot-instructions.md` found.

## Repo-specific Patterns
- `ItemConverter` object acts as the mod entrypoint and ID source.
- Use `object` for singletons (`ItemConverter`, `Networking`).
- Use `data class` for simple value carriers (`ConversionTarget`).
- Use `companion object` for config state and reload helpers.
- Use `internal` for module-scoped values (e.g., `json`).

## Kotlin Style Extras
- Prefer expression bodies when a function is a single expression.
- Use `when` for keycode mappings and enums.
- Avoid unnecessary temporary variables.
- Keep line lengths reasonable; wrap long calls.

## File IO
- Use `kotlin.io.path` utilities (`createFile`, `inputStream`, `outputStream`).
- Ensure config files exist before decode.
- Write minimal JSON (`{}`) when creating new config files.

## Defensive Defaults
- Validate external inputs (recipe types, tag lists).
- Guard against empty `ItemStack` values.
- Return empty collections instead of null.

## Gradle Properties
- Use `gradle.properties` for mod metadata.
- Keep `archive_name`, `id`, `name`, `description`, `source` in sync.

## Versioning
- Mod version is derived from git via `com.palantir.git-version`.
- Avoid hardcoding version strings in code.

## Serialization Compatibility
- Leave `ignoreUnknownKeys = true` enabled.
- Add new fields with defaults to preserve config compatibility.
