package gascraft;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** One roll for a new zombie; native equipment loot remains authoritative. */
@EventBusSubscriber(modid=Gascraft.MOD_ID)
public final class ZombieGasMasks {
    private static final String CHECKED="gascraft:mask_spawn_checked",EQUIPPED="gascraft:spawned_gas_mask";
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide||!(event.getEntity() instanceof Zombie zombie))return;
        var data=zombie.getPersistentData();if(data.getBoolean(CHECKED))return;
        data.putBoolean(CHECKED,true);
        if(!GascraftConfig.ZOMBIE_GAS_MASK_SPAWNING_ENABLED.get())return;
        if(event.loadedFromDisk()||!zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty())return;
        double chance=GascraftConfig.ZOMBIE_GAS_MASK_SPAWN_CHANCE.get();
        if(chance<=0||zombie.getRandom().nextDouble()*100>=chance)return;
        zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModContent.GAS_MASK.get()));
        data.putBoolean(EQUIPPED,true);updateDropChance(zombie);
    }
    private static void updateDropChance(Zombie zombie) {
        // Mob's native drop code skips a zero base chance before applying Looting.
        zombie.setDropChance(EquipmentSlot.HEAD,(float)(GascraftConfig.ZOMBIE_GAS_MASK_DROP_CHANCE.get()/100.0));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if(!event.getEntity().level().isClientSide&&event.getEntity() instanceof Zombie zombie
            &&zombie.getPersistentData().getBoolean(EQUIPPED)&&zombie.getItemBySlot(EquipmentSlot.HEAD).is(ModContent.GAS_MASK.get()))updateDropChance(zombie);
    }
    private ZombieGasMasks(){}
}
