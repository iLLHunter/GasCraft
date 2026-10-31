# Changelog

## 1.0.5

- Added the italic “Make a Run for the Lavatory” line to the Taco Chime Gas Grenade, with translations in all 22 supported languages.

- Combined Kiln/Fermenter explanations with their crafting graphics and paired Smoke/Tear Gas and Raid/Regenerating Mist descriptions where they fit. Tear Gas now uses concise “Variable linger” wording in all 22 languages; its effects are unchanged.

- Grenades and all canisters, including empty/custom variants, explicitly reject enchanting and anvil enchantment books in Survival and Creative. Ordinary anvil renaming remains available.

- Corrected the Shrinking Agent Pellet and Composite accents to bright orange, retaining their resized silhouettes and cyan body pixels; Enlarging artwork is unchanged.

- Set minimum Synthesizer tiers: Iron for Tetrodotoxin I, Poison II, Mustard, Raid, Napalm, Taco Chime, Chicken and Cologne; Diamond for Tetrodotoxin II, Poison III, Sunburst, Null Magic and Moonlight. Higher tiers remain valid, and both Sunburst routes require Diamond. Updated JEI and manual recipe tiers and placement.

- Resized the original Shrinking Agent Pellet and Composite sprites to the standard material scale, preserving their colors and the larger Enlarging sprites.

- Condensed the manual with machine descriptions and crafting on one page, paired launcher parts, upgrades, 2×2 Mixer, Fermenter and Synthesizer recipes, and alphabetical reagent summaries. Reagent batch costs appear on hover; gas explanations use compact time notation in all 22 languages.
- Healing Mist now spends three reagent charges per three-canister batch.
- Regenerating Mist now has a 0:20 cloud and a full 0:10 Regeneration VI linger after exit or cloud expiry.
- Added `zombie_gas_mask_spawning_enabled`, enabled by default, alongside the existing spawn/drop percentages.

- Distinguished Void composites/pellets with internal black spiral bands and Voidrot composites/pellets with internal purple spiral bands.

- Powdered Void Rot now uses Powdered Fermented Spider Eye at its center instead of the whole Fermented Spider Eye.

- Added clear Shrinking and Enlarging Gas grenades with ten-minute size/combat packages, safe resizing, boss immunity and the approved mask/dispelling rules.
- Added their fermented production chains, Shulker Shell Synthesizer reagent, distinct pre/post-fermentation artwork, status icons, manual recipes and translations in all 22 languages.
- Fermented Spider Eyes now powderize into Powdered Fermented Spider Eye, which ferments into Lachrymator Agent with normal whole-stack timing and sky bonus.
- Added a Powder Mixer recipe for one Crying Obsidian from eight Lachrymator Agents around Obsidian, retaining shared material routes for enabled gases.

- Added the Grenade Belt capacity tooltip in all 22 languages.
- Added the missing space after the comma in the manual's Turkish page indicator, preserving Minecraft's wording and format.
- Translated English fallback names for new powders, concentrates, pellets and status effects in all supported languages, and added checks against fallback names. Taco Chime and Taco Chime Grande Box retain their English branding; Funk remains where appropriate.
- Moved manual recipes into their related machine/item sections, with grenade crafting grouped at the end. Compact crafting/processing recipes share pages in pairs; sparse recipes show every grid slot.
- The manual remembers each player's last page during the current world session, including after closing it and using another item; bookmarks are cleared when leaving the world.
- Mixing Sweet Berry powder around Honey now makes reddish Powdered Cologne Base. Ferment it into the existing purple Powdered Cologne with standard whole-stack timing and sky-exposure bonus.
- Renamed Grenade Holder to Grenade Belt in all 22 supported languages.
- Added l18n.
- Long machine and JEI labels scroll within their existing space and show their full text on hover.
- Localized the Synthesizer side-configuration column headings.
- Fixed the NeoForge requirement to allow 21.1.243 and newer while still compiling against 21.1.243.
- Added the Taco Chime Grande Box and Taco Chime Gas production chain when Cultural Delights is installed.
- Added Gravity, Misted Sunburst, Soul Ward, Null Magic, Voidrot, Misted Moonlight, Boogie, Espresso Cloud, Chicken, and Misted Cologne gases and their machine production chains.
- Added status icons for Soul Ward, vulnerability, Null Magic, and Voidrot; Soul Ward uses a very translucent cyan cloud.
- Added optional silver and coffee ingredient detection, ghast tear and crying obsidian Synthesizer reagents, and spellcasting/teleport protection hooks.
- Fixed Boogie Gas dance timing at cloud phase boundaries and kept server potion updates authoritative when a client still sees a Null Magic cloud.
- Fixed normal mob AI overriding Misted Cologne's nearest target.
- Added the Gascraft Manual, crafted from a Book and Grenade Diffuser, with linked machine and gas guides translated into all 22 supported languages.
- Shortened new gas tooltips and moved detailed effect rules into the manual.
- JEI reagent recipes show each batch's charge cost (three for three-charge recipes); reagent tooltips explain the twelve-charge item capacity.
- Reworked End Stone, Ender Pearl, Void, Void Rot, Phantom, Night, and Moonlight powder icons with distinct colors and shapes.
- Updated the remaining base powders, including Flash and Tracking Powder, to the same broader material style. Enlarged Concentrated Powdered Tetrodotoxin while leaving other concentrated powders and later stages in their existing style.
- Fixed Boogie Gas applying Glowing to Wither and Ender Dragon while preserving their dance immunity, existing longer Glowing effects, and Null Magic cleansing.
- Boogie Gas now also makes players glow, with the same five-second linger; updated the manual in all 22 languages and the gas reference tables.
- Updated gas and grenade references with all built-in gases, cloud timings, protection rules, boss exceptions, and optional-chain requirements.
- Added Powdered Gravel and Powdered Gravity, replacing iron blocks in the concentrated Gravity recipe.
- Added Void Gas: one safe random teleport within 64 blocks, plus its Toxic Binder/pellet/Eye of Ender production chain.
- Rebalanced Tetrodotoxin synthesis inputs: concentrate for I, Tetrodotoxin Pellet for II, and the new purple/magenta-striped Misted Death Pellet for III. Added its mixed pellet/concentrate composite and kiln recipe.
- Raised Soul Ward visibility to faint cyan at 40/255 particle alpha.
- Fixed localized JEI/Mekanism display names for every built-in gas, including Tetrodotoxin, across all 22 languages.
- Reworked the manual with grey illustrated default-recipe pages using existing item/GUI assets, removed machine specifications, fixed meal percentages, and added the opening modpack disclaimer.
- Added the server setting useManual (true by default); disabling it hides the book, disables its recipe and makes existing copies inert.
- Added configurable zombie Gas Mask spawning (2%) and base equipment drops (20%), including Zombie subclasses; zero disables either feature.
- Made Misted Meat invisible and changed synthesis to three Empty Canisters, 3000 mB Water and Powdered Protein.
- Renamed healing materials and IDs to Healing Agent Pellet Composite and Healing Agent Pellet, without migration.
- Replaced Ender Pearl with Powdered Ender Pearl in Sarin Pellet Composite.
- Added the italic Drakkar Noir line to the Misted Cologne grenade.
- Added an optional Ender IO recipe: three Smoke Canisters plus 3000 mB Liquid Sunshine produce three Misted Sunburst Canisters with an empty solid slot.
- Changed Sweet Berry powder to berry red.
- Gascraft TOML files now generate in `config/gascraft/`. Existing files require manual relocation; no automatic migration is performed.
- Added a 1% Taco Chime Fart meal outcome, releasing a stationary Taco Chime Gas cloud at the eater. Meal outcomes are now 20% Nourishment, 5% Nausea, 1% Fart and 74% neither; updated tooltips and the manual in all 22 languages.
- Added false-by-default disable flags for every built-in gas at the bottom of the server TOML. Disabled chains are hidden and cannot be produced, including through replacement crafting recipes; existing containers and clouds become inert. Shared materials remain for enabled gases. Changes require a world/server restart; canceled batches leave unconsumed inputs, and earlier lingering effects finish normally.

Supported languages:

- English
- Español
- Français
- Deutsch
- Português (Brazil)
- Português (Portugal)
- Italiano
- Nederlands
- Polski
- Türkçe
- Українська
- ไทย
- Русский
- العربية
- हिन्दी
- 日本語
- 한국어
- 简体中文
- 繁體中文
- Čeština
- Magyar
- Svenska
