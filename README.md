# Actual Generators

Generators, machines and item logistics for Minecraft.

A fresh rewrite for Minecraft 1.21.1 / NeoForge. The 1.19.4 Forge version lives on at
[ActualGeneratorsOLD](https://github.com/SymoHTL/ActualGeneratorsOLD) (archived).

## Environment

| | |
|---|---|
| Minecraft | 1.21.1 |
| Mod loader | NeoForge 21.1.249 |
| Build | ModDevGradle 2.0.144, Gradle 8.14.2 |
| Mappings | Mojang official + Parchment 2024.11.17 |
| Java | 21 |

Gradle itself runs on Java 17+, but compilation and the game need a **JDK 21** toolchain.
If no JDK 21 is installed, Gradle auto-provisions one via the foojay toolchain resolver
configured in `settings.gradle`.

## Commands

```bash
./gradlew build          # build the mod jar into build/libs
./gradlew runClient      # launch a dev client
./gradlew runServer      # launch a dev server
./gradlew runData        # run data generators into src/generated/resources
./gradlew runGameTestServer  # run registered gametests headlessly
```

## Layout

```
src/main/java/dev/symo/actualgenerators/
  ActualGenerators.java        @Mod entrypoint
  registry/ModBlocks.java      block DeferredRegister
  registry/ModItems.java       item DeferredRegister
  registry/ModCreativeTabs.java creative tab
src/main/resources/assets/actualgenerators/   textures, models, lang
src/main/templates/META-INF/neoforge.mods.toml  mod metadata (gradle.properties expanded into it)
```

Mod metadata (id, name, version, dependency ranges) is edited in `gradle.properties`,
not in the toml — the toml is a template.

## License

BSD 3-Clause. See [LICENSE](LICENSE).
