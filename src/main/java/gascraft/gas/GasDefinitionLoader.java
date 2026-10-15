package gascraft.gas;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Loads definitions from installed mods, local config and server datapacks, in dependency order. */
public final class GasDefinitionLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<ResourceLocation, JsonObject> AGENTS = new LinkedHashMap<>();
    private static Map<ResourceLocation, JsonObject> baseAgents;
    private static final Map<String, Map<ResourceLocation, JsonObject>> DATA = new LinkedHashMap<>();
    private static List<GasCatalog.GasDefinition> baseGases;
    private static List<GasCatalog.BrewingMix> baseMixes;
    private static net.neoforged.neoforge.common.conditions.ICondition.IContext conditionContext=net.neoforged.neoforge.common.conditions.ICondition.IContext.EMPTY;
    private final String kind;
    private GasDefinitionLoader(String kind) { super(GSON, "gascraft_" + kind); this.kind = kind; }
    public static void initialize() {
        baseGases = List.copyOf(GasCatalog.gasDefinitions()); baseMixes = List.copyOf(GasCatalog.brewingMixes());
        for (String agent : List.of("heal", "lachrymator", "nerve", "pulmonary", "poison_i", "poison_ii", "poison_iii")) {
            try (Reader reader = new InputStreamReader(Objects.requireNonNull(GasDefinitionLoader.class.getResourceAsStream("/assets/gascraft/agents/" + agent + ".json")))) {
                AGENTS.put(GasCatalog.id(agent), JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        AGENTS.put(GasCatalog.id("misted_meat"),JsonParser.parseString("{\"factory\":\"misted_meat\",\"toxic\":true}").getAsJsonObject());
        baseAgents = Map.copyOf(AGENTS);
        reloadCombinedDefinitions();
    }
    public static void addListeners(AddReloadListenerEvent event) {
        conditionContext=event.getConditionContext();
        event.addListener(new GasDefinitionLoader("agents")); event.addListener(new GasDefinitionLoader("gases")); event.addListener(new GasDefinitionLoader("recipes"));
    }
    @Override protected void apply(Map<ResourceLocation, JsonElement> definitions, ResourceManager manager, ProfilerFiller profiler) {
        DATA.put(kind, new LinkedHashMap<>());
        definitions.forEach((id, json) -> { if (!id.getPath().startsWith("_") && enabled(json.getAsJsonObject())) DATA.get(kind).put(id, json.getAsJsonObject()); });
        if (kind.equals("recipes")) reloadCombinedDefinitions();
    }
    private static void reloadCombinedDefinitions() {
        GasCatalog.replaceGases(baseGases); GasCatalog.replaceMixes(baseMixes);
        AGENTS.clear(); AGENTS.putAll(baseAgents);
        Map<String, Map<ResourceLocation, JsonObject>> combined = new LinkedHashMap<>();
        for (String kind : List.of("agents", "gases", "recipes")) {
            Map<ResourceLocation, JsonObject> definitions = new LinkedHashMap<>(); combined.put(kind, definitions);
            for (var file : ModList.get().getModFiles()) {
                Path assets = file.getFile().findResource("assets");
                if (!Files.isDirectory(assets)) continue;
                try (var namespaces = Files.list(assets)) {
                    namespaces.filter(Files::isDirectory).forEach(ns -> readDefinitionDirectory(ns.resolve("gascraft_" + kind), ns.getFileName().toString(), definitions));
                } catch (IOException e) { LogUtils.getLogger().error("Cannot load mod gas definitions", e); }
            }
            Path config = FMLPaths.CONFIGDIR.get().resolve("gascraft/custom_" + kind);
            try {
                Files.createDirectories(config);
                Path example = config.resolve("_example.json");
                if (!Files.exists(example)) Files.writeString(example, definitionExample(kind));
            } catch (IOException e) { LogUtils.getLogger().error("Cannot create gas definition examples", e); }
            readDefinitionDirectory(config, "gascraft", definitions);
            definitions.putAll(DATA.getOrDefault(kind, Map.of()));
        }
        AGENTS.putAll(combined.get("agents"));
        combined.get("gases").forEach((id, json) -> tryLoadDefinition(id, () -> loadGasDefinition(id, json)));
        combined.get("recipes").forEach((id, json) -> tryLoadDefinition(id, () -> GasCatalog.addMix(parseBrewingMix(json))));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new gascraft.api.event.GasDefinitionRegistrationEvent());
    }
    private static void readDefinitionDirectory(Path root, String namespace, Map<ResourceLocation, JsonObject> definitions) {
        if (!Files.isDirectory(root)) return;
        try (var files = Files.walk(root)) {
            files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).forEach(path -> {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (Arrays.stream(relative.split("/")).anyMatch(s -> s.startsWith("_"))) return;
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, relative.substring(0, relative.length() - 5));
                tryLoadDefinition(id, () -> { JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject(); if (enabled(json)) definitions.put(id, json); });
            });
        } catch (IOException e) { LogUtils.getLogger().error("Cannot read gas definitions {}", root, e); }
    }
    /** Chemical IDs must be registered before world datapacks load; effects retain their normal reload lifecycle. */
    public static Map<ResourceLocation, Integer> startupChemicalDefinitions() {
        Map<ResourceLocation, Integer> colors = new LinkedHashMap<>();
        GasCatalog.gasDefinitions().forEach(gas -> colors.put(gas.id(), gas.color()));
        Map<ResourceLocation, JsonObject> definitions = new LinkedHashMap<>();
        for (var file : ModList.get().getModFiles()) {
            Path assets = file.getFile().findResource("assets");
            if (!Files.isDirectory(assets)) continue;
            try (var namespaces = Files.list(assets)) {
                namespaces.filter(Files::isDirectory).forEach(ns -> readDefinitionDirectory(ns.resolve("gascraft_gases"), ns.getFileName().toString(), definitions));
            } catch (IOException e) { LogUtils.getLogger().error("Cannot read startup chemical definitions", e); }
        }
        readDefinitionDirectory(FMLPaths.CONFIGDIR.get().resolve("gascraft/custom_gases"), "gascraft", definitions);
        definitions.forEach((id, json) -> tryLoadDefinition(id, () -> colors.put(id, (int)Long.decode(json.get("color").getAsString()).longValue())));
        return Map.copyOf(colors);
    }
    public static void loadGasDefinition(ResourceLocation id, JsonObject json) {
        List<GasCatalog.AgentDefinition> agents = new ArrayList<>();
        if (json.has("agents")) for (JsonElement element : json.getAsJsonArray("agents")) {
            JsonObject effect = element.getAsJsonObject(); ResourceLocation agentId = ResourceLocation.parse(effect.get("agent").getAsString());
            JsonObject definition = AGENTS.get(agentId);
            if (definition == null && GasAgentRegistry.getAgent(agentId) != null) { agents.add(GasAgentRegistry.carrying(GasAgentRegistry.getAgent(agentId), effect.get("potency").getAsFloat())); continue; }
            if (definition == null) throw new IllegalArgumentException("Unknown agent " + agentId);
            agents.add(GasCatalog.parseAgentDefinition(agentId.toString(), definition, effect.get("potency").getAsFloat()));
        }
        boolean smoke = json.get("gasType").getAsString().equals("SMOKE");
        int color = (int)Long.decode(json.get("color").getAsString()).longValue();
        List<String> tooltip = new ArrayList<>(); if (json.has("tooltipLines")) json.getAsJsonArray("tooltipLines").forEach(line -> tooltip.add(line.getAsString()));
        GasCatalog.register(new GasCatalog.GasDefinition(id, smoke, color, json.has("canisterStripeColor") ? (int)Long.decode(json.get("canisterStripeColor").getAsString()).longValue() : color,
            List.copyOf(agents), json.has("particleType") ? json.get("particleType").getAsString() : smoke ? "SMOKE" : "GAS", List.copyOf(tooltip), json.has("suspendable") && json.get("suspendable").getAsBoolean(), TimedCloudEffect.parseEffects(json)));
    }
    public static GasCatalog.BrewingMix parseBrewingMix(JsonObject json) {
        JsonObject input = json.getAsJsonObject("input"), ingredient = json.getAsJsonObject("ingredient");
        return new GasCatalog.BrewingMix(ResourceLocation.parse(json.get(json.has("gas")?"gas":"result").getAsString()), string(input,"gas"), string(input,"item"), string(ingredient,"item"), string(ingredient,"tag"), string(input,"potion"));
    }
    private static String string(JsonObject json, String key) { return json.has(key) ? json.get(key).getAsString() : ""; }
    private static boolean enabled(JsonObject json) {
        String key=json.has("neoforge:conditions")?"neoforge:conditions":"conditions";
        if (!json.has(key)) return true;
        for (JsonElement condition : json.getAsJsonArray(key)) {
            JsonObject c = condition.getAsJsonObject().deepCopy();
            c.addProperty("type",c.get("type").getAsString().replaceFirst("^forge:","neoforge:"));
            var parsed=net.neoforged.neoforge.common.conditions.ICondition.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,c).getOrThrow();
            if(!parsed.test(conditionContext))return false;
        }
        return true;
    }
    private static String definitionExample(String kind) {
        return switch (kind) {
            case "agents" -> "{\"factory\":\"potion\",\"potion\":\"minecraft:wither\",\"duration\":30,\"amplifier\":2}";
            case "gases" -> "{\"gasType\":\"GAS\",\"color\":\"0x55CAFE66\",\"canisterStripeColor\":\"0x75DADD10\",\"agents\":[{\"agent\":\"gascraft:nerve\",\"potency\":0.375}],\"tooltipLines\":[\"Custom gas\"]}";
            default -> "{\"gas\":\"gascraft:sarin_gas\",\"input\":{\"gas\":\"gascraft:smoke\"},\"ingredient\":{\"item\":\"minecraft:spider_eye\"}}";
        };
    }
    private interface Checked { void run() throws Exception; }
    public static void restoreLocal() { DATA.clear(); reloadCombinedDefinitions(); }
    private static void tryLoadDefinition(ResourceLocation id, Checked task) {
        try { task.run(); } catch (Exception e) { LogUtils.getLogger().error("Invalid gas definition {}", id, e); }
    }
    private GasDefinitionLoader() { this("gases"); }
}
