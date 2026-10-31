package gascraft.gas;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.DeferredItem;

/** Stable registry identities; optional ingredients are resolved against live item tags. */
public final class GasFieldsContent {
    public record Material(String name, String gas, int color, String texture) {}
    public record Profile(String gas, int expand, int hold, int fade, int contract, int density) {
        public float radius(int age) { return contract > 0 ? Math.max(0,1-age/(float)contract) : expand == 0 ? 1 : Math.min(1,age/(float)expand); }
        public float opacity(int age) { return fade == 0 ? 1 : Math.clamp((160-age)/(float)fade,0,1); }
        public boolean dancing(int age) { return age < 60 || age >= 100; }
    }
    public static final List<Material> MATERIALS = read("field_materials.json", Material[].class);
    public static final List<Profile> PROFILES = read("field_profiles.json", Profile[].class);
    public static final Map<String, DeferredItem<Item>> ITEMS = new LinkedHashMap<>();
    public static Profile profile(ResourceLocation gas) { return gas.getNamespace().equals("gascraft") ? PROFILES.stream().filter(p->p.gas().equals(gas.getPath())).findFirst().orElse(null) : null; }
    private static <T> List<T> read(String name, Class<T[]> type) {
        try(var stream=Objects.requireNonNull(GasFieldsContent.class.getResourceAsStream("/assets/gascraft/"+name));var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            return List.of(new Gson().fromJson(reader,type));
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    public static void register(net.neoforged.neoforge.registries.DeferredRegister.Items registry) { for(var m:MATERIALS) ITEMS.put(m.name(),registry.registerSimpleItem(m.name())); }
    public static boolean available(ResourceLocation gas) {return GasChainControls.enabled(gas)&&ingredientsAvailable(gas);}
    public static boolean ingredientsAvailable(ResourceLocation gas) {
        if(!gas.getNamespace().equals("gascraft")) return true;
        String tag=switch(gas.getPath()) { case "misted_moonlight"->"c:nuggets/silver";case "espresso_cloud"->"gascraft:espresso_ingredients";default->""; };
        return tag.isEmpty() || BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM,ResourceLocation.parse(tag))).map(s->s.size()>0).orElse(false);
    }
    public static boolean hidden(ItemStack stack) {
        if(!GasChainControls.itemAllowed(stack))return true;
        if(stack.is(gascraft.ModContent.GASCRAFT_MANUAL.get())&&!gascraft.GascraftConfig.manualEnabled())return true;
        if(gascraft.compat.CulturalDelightsCompat.hidden(stack)) return true;
        ResourceLocation gas=stack.get(gascraft.ModContent.GAS_CONTENTS);
        if(gas!=null&&!available(gas))return true;
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("gascraft") && MATERIALS.stream().anyMatch(m->m.name().equals(id.getPath())&&!ingredientsAvailable(GasCatalog.id(m.gas())));
    }
    public static boolean hiddenForClient(ItemStack stack){return stack.is(gascraft.ModContent.GASCRAFT_MANUAL.get())?!gascraft.GascraftConfig.clientManualEnabled():hidden(stack);}
    public static boolean musicDisc(ItemStack stack) { return stack.has(DataComponents.JUKEBOX_PLAYABLE); }
    private GasFieldsContent() {}
}
