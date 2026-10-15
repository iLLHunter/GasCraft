package gascraft.gas;

import com.google.gson.*;
import java.util.*;

/** One cloud action, delayed from the preceding action (or cloud release for the first). */
public record TimedCloudEffect(int delayTicks, String type, float damage, int fireTicks, int knockbackLevel, int color, int durationTicks, String potion, int amplifier) {
    public TimedCloudEffect {
        potion = potion == null ? "" : potion;
        if (delayTicks < 0 || !List.of("detonation", "color", "fade", "instant_potion", "paralysis", "melee_damage", "flash", "end").contains(type)
            || !Float.isFinite(damage) || damage < 0 || fireTicks < 0 || knockbackLevel < 0 || durationTicks < 0 || amplifier < 0)
            throw new IllegalArgumentException("Invalid timed cloud effect");
        if (type.equals("fade") && durationTicks == 0) throw new IllegalArgumentException("A cloud fade needs a positive durationTicks");
        if (type.equals("paralysis") && durationTicks == 0) throw new IllegalArgumentException("Paralysis needs a positive durationTicks");
        if (type.equals("flash") && durationTicks == 0) throw new IllegalArgumentException("A flash needs a positive durationTicks");
        if (type.equals("instant_potion")) net.minecraft.resources.ResourceLocation.parse(potion);
    }
    public TimedCloudEffect(int delayTicks, String type, float damage, int fireTicks, int knockbackLevel, int color, int durationTicks) {
        this(delayTicks, type, damage, fireTicks, knockbackLevel, color, durationTicks, "", 0);
    }
    public static List<TimedCloudEffect> parseEffects(JsonObject gas) {
        return gas.has("timedEffects") ? parseEffects(gas.getAsJsonArray("timedEffects")) : List.of();
    }
    public static List<TimedCloudEffect> parseEffects(JsonArray array) {
        var effects = new ArrayList<TimedCloudEffect>();
        for (var element : array) {
            var json = element.getAsJsonObject();
            String type = json.get("type").getAsString();
            effects.add(new TimedCloudEffect(json.get("delayTicks").getAsInt(), type,
                json.has("damage") ? json.get("damage").getAsFloat() : 0,
                json.has("fireTicks") ? json.get("fireTicks").getAsInt() : 0,
                json.has("knockbackLevel") ? json.get("knockbackLevel").getAsInt() : 0,
                type.equals("color") ? (int)Long.decode(json.get("color").getAsString()).longValue() : 0,
                json.has("durationTicks") ? json.get("durationTicks").getAsInt() : 0,
                type.equals("instant_potion") ? json.get("potion").getAsString() : "",
                json.has("amplifier") ? json.get("amplifier").getAsInt() : 0));
        }
        return List.copyOf(effects);
    }
    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("delayTicks", delayTicks); json.addProperty("type", type);
        if (type.equals("detonation")) {
            json.addProperty("damage", damage); json.addProperty("fireTicks", fireTicks); json.addProperty("knockbackLevel", knockbackLevel);
        } else if (type.equals("melee_damage")) {
            json.addProperty("damage", damage);
        } else if (type.equals("color")) json.addProperty("color", "0x" + Integer.toUnsignedString(color, 16));
        else if (type.equals("instant_potion")) { json.addProperty("potion", potion); json.addProperty("amplifier", amplifier); }
        else json.addProperty("durationTicks", durationTicks);
        return json;
    }
    public static JsonArray toJson(List<TimedCloudEffect> effects) {
        var array = new JsonArray(); effects.forEach(effect -> array.add(effect.toJson())); return array;
    }
}
