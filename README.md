# Gascraft 1.0.4

## Configuration and custom definitions

Gameplay settings are in each world's `serverconfig/gascraft-server.toml` and synchronize through NeoForge. Use `defaultconfigs/gascraft-server.toml` to supply defaults for new worlds.

| Setting | Default |
|---|---|
| `straight_line_propagation` | `false`; use paths around obstacles |
| `cloud_lifetime_ticks` | `600` ticks |
| `gas_range_blocks` | `10` blocks |
| `protective_equipment` | `[]`; item IDs or head/chest/legs/feet joined by `&`, with `*` wildcards |
| `powderizer_preferred_outputs` | `[]`; optional `material=item_id` preferences for matching existing tagged dusts |

Client settings are in `config/gascraft-client.toml`, also accessible through NeoForge's mod configuration screen: `use_shaders=true`, `render_gas_overlays=false`. Tear-gas blur scales with irritation; gas-cloud overlays can use the procedural shader or the gas haze texture.

Custom JSON definitions load from installed mods' `assets/<namespace>/gascraft_agents`, `gascraft_gases`, and `gascraft_recipes`; from `config/gascraft/custom_agents`, `custom_gases`, and `custom_recipes`; and from datapack `data/<namespace>/gascraft_agents`, `gascraft_gases`, and `gascraft_recipes`. Local config definitions use the `gascraft` namespace. Files and folders beginning with `_` are ignored; example files are generated automatically. Datapack definitions override local definitions with the same ID. Real server resource reloads replace edited/deleted definitions, and gas/recipe catalogs synchronize to clients on joining and reloading.

Agent factories include `sickness`, `damage`, `potion` and the Misted Meat feeding agent. Gas definitions support `gasType` (`GAS`/`SMOKE`), particle type (`GAS`/`SMOKE`/`MISTED_MEAT`), ARGB color, contents stripe color (`canisterStripeColor` in existing definitions), agent potency, tooltip lines, optional crouching suspension and sequential timed cloud effects. Gas effects and production recipes remain separate. Legacy recipe definitions use a `gas` result, an `input` item or gas, and an ingredient specified by item ID or tag. The Synthesizer migrates these to batches of three while preserving their base and ingredient rules; mundane-potion inputs become empty canisters plus water. Explicit `gascraft:synthesizing` datapack recipes add fluid/chemical tank inputs, minimum tiers and reagent charges. NeoForge conditions are supported.

Mekanism is optional. When installed, the 32 built-in gases and custom gas IDs found in mod assets/local config at startup are also registered as native Mekanism chemicals. Synthesizer tanks accept fluids through fluid pipes and chemicals through native chemical pipes, sharing one tank with one substance at a time. New chemical IDs require a game/server restart; gas effects and canister definitions retain their resource-reload behavior. World datapacks can use those registered IDs but cannot add a native chemical after startup.

The `gascraft.api` interfaces, standalone `GasDefinitionBuilder`, registration events and sickness/breathing accessors provide extension points. `GasExposure.breathingHandler()` exposes live breathing state; `exportSicknessState()` and `mergeSicknessState()` preserve sickness snapshot merging. Exposure start/tick/end/immunity and sickness applied/update/cleared events run on the NeoForge game bus.

Player death clears beneficial and harmful effects, gas sickness and breathing state. A grenade detonated while held follows the living player, then detaches at death and leaves its cloud there for the remaining lifetime. Mustard and other pulmonary gas deaths have gas-specific messages.

## Build and validation

Requires Java 21.

```powershell
.\gradlew.bat build --no-configuration-cache
```
