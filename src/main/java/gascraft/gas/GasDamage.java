package gascraft.gas;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/** Nerve damage retains drowning's armor bypass while reaching every susceptible mob. */
public final class GasDamage {
    public static final ResourceKey<DamageType> NERVE_AGENT = ResourceKey.create(Registries.DAMAGE_TYPE, GasCatalog.id("neurotoxic_gas"));
    public static final ResourceKey<DamageType> TETRODOTOXIN = ResourceKey.create(Registries.DAMAGE_TYPE, GasCatalog.id("tetrodotoxin_melee"));
    public static DamageSource tetrodotoxinDamage(LivingEntity entity, net.minecraft.world.entity.Entity cloud, net.minecraft.world.entity.Entity owner) {
        return new DamageSource(entity.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TETRODOTOXIN), cloud, owner);
    }
    public static DamageSource nerveAgentDamage(LivingEntity entity) {
        return new DamageSource(entity.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(NERVE_AGENT));
    }
    public static DamageSource pulmonaryGasDamage(LivingEntity entity, GasCatalog.GasDefinition gas) {
        String message = gas.id().equals(GasCatalog.id("mustard_gas")) ? "mustard_gas" : "pulmonary_agent";
        // Keep the native drowning type and all its tags/mob rules; only the death text changes.
        return new DamageSource(entity.damageSources().drown().typeHolder()) {
            @Override public Component getLocalizedDeathMessage(LivingEntity victim) {
                return Component.translatable("death.attack.gascraft." + message, victim.getDisplayName());
            }
        };
    }
    private GasDamage() {}
}
