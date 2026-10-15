package gascraft.gas;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

/** Built-in gas, agent and brewing definitions. */
public final class GasCatalog {
    public record AgentDefinition(String factory, boolean toxic, boolean ignoreBreath, float potency, String effect, int duration, int amplifier, float damage, String translationKey) {
        public AgentDefinition(String factory, boolean toxic, boolean ignoreBreath, float potency, String effect, int duration, int amplifier, float damage) { this(factory,toxic,ignoreBreath,potency,effect,duration,amplifier,damage,"agent."+effect.replace(':','.')); }
        @Override public boolean toxic() {
            return factory.equals("potion") ? net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getOptional(ResourceLocation.parse(effect)).map(e -> e.getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL).orElse(toxic) : toxic;
        }
    }
    public record GasDefinition(ResourceLocation id, boolean smoke, int color, int canisterStripeColor, List<AgentDefinition> agents, String particleType, List<String> tooltipLines, boolean suspendable, List<TimedCloudEffect> timedEffects) implements gascraft.api.IGasDefinition {
        public GasDefinition { timedEffects = timedEffects == null ? List.of() : List.copyOf(timedEffects); }
        public GasDefinition(ResourceLocation id, boolean smoke, int color, int canisterStripeColor, List<AgentDefinition> agents, String particleType, List<String> tooltipLines, boolean suspendable) {
            this(id, smoke, color, canisterStripeColor, agents, particleType, tooltipLines, suspendable, List.of());
        }
        public GasDefinition(ResourceLocation id, boolean smoke, int color, int canisterStripeColor, List<AgentDefinition> agents) {
            this(id, smoke, color, canisterStripeColor, agents, smoke ? "SMOKE" : "GAS", List.of(), id.getPath().equals("sarin_gas"));
        }
        public boolean toxic() { return agents.stream().anyMatch(AgentDefinition::toxic); }
        public void applyEffect(net.minecraft.world.entity.LivingEntity entity, float concentration, boolean forced) { GasExposure.applyGasAgents(entity,this,concentration,forced); }
    }
    public record BrewingMix(ResourceLocation result, String inputGas, String inputItem, String ingredientItem, String ingredientTag, String inputPotion) {
        public BrewingMix(ResourceLocation result, String inputGas, String inputItem, String ingredientItem, String ingredientTag) {
            this(result,inputGas,inputItem,ingredientItem,ingredientTag,"");
        }
    }
    private static volatile Map<ResourceLocation, GasDefinition> GASES = new LinkedHashMap<>();
    private static volatile List<BrewingMix> MIXES = new ArrayList<>();
    // An inert internal fallback, never a catalog gas or playable item variant.
    private static final GasDefinition EMPTY = new GasDefinition(id("empty_contents"), false, 0, 0, List.of());
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("gascraft", path); }
    public static GasDefinition gasDefinition(ResourceLocation id) { return GASES.getOrDefault(id, EMPTY); }
    public static boolean contains(ResourceLocation id) { return id != null && !id.equals(EMPTY.id()) && GASES.containsKey(id); }
    public static Collection<GasDefinition> gasDefinitions() { return Collections.unmodifiableCollection(GASES.values()); }
    public static List<BrewingMix> brewingMixes() { return Collections.unmodifiableList(MIXES); }

    public static synchronized void initialize() {
        if (!GASES.isEmpty()) return;
        for (String name : List.of("smoke", "tear_gas", "pulmonary_agent", "mustard_gas", "regenerating_mist", "healing_mist", "raid_gas", "misted_meat", "poison_i", "poison_ii", "poison_iii", "napalm", "stun_gas", "misted_tetrodotoxin_i", "misted_tetrodotoxin_ii", "misted_tetrodotoxin_iii")) {
            JsonObject json = readBuiltinDefinition("gases/" + name + ".json").getAsJsonObject();
            List<AgentDefinition> agents = new ArrayList<>();
            if (json.has("agents")) for (JsonElement element : json.getAsJsonArray("agents")) {
                JsonObject effect = element.getAsJsonObject();
                String agentName = ResourceLocation.parse(effect.get("agent").getAsString()).getPath();
                JsonObject agent = agentName.equals("misted_meat") ? new JsonObject() : readBuiltinDefinition("agents/" + agentName + ".json").getAsJsonObject();
                agents.add(parseAgentDefinition(agentName, agent, effect.get("potency").getAsFloat()));
            }
            int color = (int) Long.decode(json.get("color").getAsString()).longValue();
            int canisterStripeColor = json.has("canisterStripeColor") ? (int) Long.decode(json.get("canisterStripeColor").getAsString()).longValue() : color;
            boolean smoke = json.get("gasType").getAsString().equals("SMOKE");
            List<String> tooltip = new ArrayList<>();
            if (json.has("tooltipLines")) json.getAsJsonArray("tooltipLines").forEach(line -> tooltip.add(line.getAsString()));
            GASES.put(id(name), new GasDefinition(id(name), smoke, color, canisterStripeColor, List.copyOf(agents), json.has("particleType") ? jsonStringOrEmpty(json, "particleType") : smoke ? "SMOKE" : "GAS", List.copyOf(tooltip), false, TimedCloudEffect.parseEffects(json)));
        }
        GASES.put(id("sarin_gas"), new GasDefinition(id("sarin_gas"), false, 0xffffff, 0xffffff, List.of(parseAgentDefinition("nerve", readBuiltinDefinition("agents/nerve.json").getAsJsonObject(), 0.8F))));
        for (DyeColor color : DyeColor.values()) {
            ResourceLocation key = id("colored_smoke_" + color.getName());
            GASES.put(key, new GasDefinition(key, true, color.getTextureDiffuseColor(), color.getTextureDiffuseColor(), List.of()));
        }
        for (JsonElement element : readBuiltinDefinition("gas_recipes.json").getAsJsonArray()) {
            JsonObject json = element.getAsJsonObject();
            JsonObject input = json.getAsJsonObject("input"), ingredient = json.getAsJsonObject("ingredient");
            MIXES.add(new BrewingMix(ResourceLocation.parse(json.get("gas").getAsString()), jsonStringOrEmpty(input, "gas"), jsonStringOrEmpty(input, "item"), jsonStringOrEmpty(ingredient, "item"), jsonStringOrEmpty(ingredient, "tag"), jsonStringOrEmpty(input, "potion")));
        }
    }
    private static String jsonStringOrEmpty(JsonObject json, String key) { return json.has(key) ? json.get(key).getAsString() : ""; }
    public static boolean isPoisonGas(ResourceLocation id) {
        return id.equals(id("poison_i")) || id.equals(id("poison_ii")) || id.equals(id("poison_iii"));
    }
    public static AgentDefinition parseAgentDefinition(String name, JsonObject json, float potency) {
        return new AgentDefinition(name.equals("misted_meat") ? "misted_meat" : jsonStringOrEmpty(json, "factory"), name.equals("misted_meat") || jsonStringOrEmpty(json, "factory").equals("damage") || json.has("toxic") && json.get("toxic").getAsBoolean(), json.has("ignoreBreath") && json.get("ignoreBreath").getAsBoolean(), potency,
            json.has("sickness") ? jsonStringOrEmpty(json, "sickness") : jsonStringOrEmpty(json, "potion"), json.has("duration") ? json.get("duration").getAsInt() : 0, json.has("amplifier") ? json.get("amplifier").getAsInt() : 0, json.has("maxDamage") ? json.get("maxDamage").getAsFloat() : 0, "agent." + (jsonStringOrEmpty(json,"factory").equals("potion") ? "gascraft.potion" : name.contains(":") ? name.replace(':','.') : "gascraft."+name));
    }
    public static synchronized void register(GasDefinition gas) { Map<ResourceLocation,GasDefinition> copy=new LinkedHashMap<>(GASES); copy.put(gas.id(),gas); GASES=Collections.unmodifiableMap(copy); }
    public static synchronized void replaceGases(Collection<GasDefinition> gases) { Map<ResourceLocation,GasDefinition> copy=new LinkedHashMap<>(); gases.forEach(g->copy.put(g.id(),g)); GASES=Collections.unmodifiableMap(copy); }
    public static synchronized void addMix(BrewingMix mix) { List<BrewingMix> copy=new ArrayList<>(MIXES); copy.add(mix); MIXES=List.copyOf(copy); }
    public static void replaceMixes(List<BrewingMix> mixes) { MIXES=List.copyOf(mixes); }
    private static JsonElement readBuiltinDefinition(String path) {
        try (InputStream stream = GasCatalog.class.getResourceAsStream("/assets/gascraft/" + path)) {
            if (stream == null) throw new IllegalStateException("Missing Gascraft definition: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }
    private GasCatalog() {}
}
