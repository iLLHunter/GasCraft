package gascraft.recipe;
import com.mojang.serialization.MapCodec;
import gascraft.Gascraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.*;

public final class RecipeConditions {
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS=DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS,Gascraft.MOD_ID);
    static {CONDITIONS.register("fluid_exists",()->FluidExists.CODEC);}
    public record FluidExists(ResourceLocation fluid) implements ICondition {
        public static final MapCodec<FluidExists> CODEC=ResourceLocation.CODEC.fieldOf("fluid").xmap(FluidExists::new,FluidExists::fluid);
        @Override public boolean test(IContext context){return BuiltInRegistries.FLUID.containsKey(fluid)&&BuiltInRegistries.FLUID.get(fluid)!=Fluids.EMPTY;}
        @Override public MapCodec<? extends ICondition> codec(){return CODEC;}
    }
    public static void register(IEventBus bus){CONDITIONS.register(bus);}
}
