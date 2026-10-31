package gascraft.compat;

import gascraft.gas.GasFields;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.common.NeoForge;

/** Optional APIs are linked only when their owning mod is present. */
public final class GasMagicCompat {
    private static Method ironData,ironCasting,ironReset,ironCancel,ironMobCasting;
    public static void initialize() {
        register("com.hollingsworth.arsnouveau.api.event.SpellCastEvent",false);
        register("com.hollingsworth.arsnouveau.api.event.SpellResolveEvent$Pre",true);
        register("io.redspace.ironsspellbooks.api.events.SpellPreCastEvent",false);
        try {
            var type=Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            ironData=type.getMethod("getPlayerMagicData",LivingEntity.class);ironCasting=type.getMethod("isCasting");ironReset=type.getMethod("resetCastingState");
            ironCancel=Class.forName("io.redspace.ironsspellbooks.api.entity.IMagicEntity").getMethod("cancelCast");
            ironMobCasting=ironCancel.getDeclaringClass().getMethod("isCasting");
        } catch(ClassNotFoundException ignored) {}catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported Iron's Spells casting API",e);}
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void register(String name,boolean shooter) {
        try {
            Class<? extends Event> type=(Class<? extends Event>)Class.forName(name);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,(Class)type,(java.util.function.Consumer<Event>)(event->{
                try {
                    Object caster=shooter?event.getClass().getField("shooter").get(event):event.getClass().getMethod("getEntity").invoke(event);
                    if(caster instanceof LivingEntity entity&&(GasFields.blocksMagic(entity)||GasFields.blocksAttacks(entity))&&event instanceof ICancellableEvent cancel)cancel.setCanceled(true);
                }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot read spell caster",e);}
            }));
        }catch(ClassNotFoundException ignored){}
    }
    public static void interrupt(LivingEntity entity) {
        if(entity.isUsingItem()&&magicItem(entity.getUseItem()))entity.stopUsingItem();
        if(ironData!=null)try {
            if(ironCancel.getDeclaringClass().isInstance(entity)){if(Boolean.TRUE.equals(ironMobCasting.invoke(entity)))ironCancel.invoke(entity);}
            else {Object data=ironData.invoke(null,entity);if(data!=null&&Boolean.TRUE.equals(ironCasting.invoke(data)))ironReset.invoke(data);}
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot interrupt Iron's Spells cast",e);}
        if(entity instanceof gascraft.gas.GasCastInterrupt caster)caster.gascraft$interruptCast();
    }
    public static boolean magicItem(net.minecraft.world.item.ItemStack stack) {
        if(stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,gascraft.gas.GasCatalog.id("magic_casting_items"))))return true;
        for(Class<?> type=stack.getItem().getClass();type!=null;type=type.getSuperclass())if(Set.of("elucent.rootsclassic.item.StaffItem","elucent.rootsclassic.item.CrystalStaffItem","com.sammy.malum.common.item.curiosities.weapons.staff.AbstractStaffItem","com.stal111.forbidden_arcanus.common.item.MagicWandItem").contains(type.getName()))return true;
        return false;
    }
    public static boolean magicProjectile(net.minecraft.world.entity.projectile.Projectile entity) {
        if(entity instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile||entity instanceof net.minecraft.world.entity.projectile.ShulkerBullet||entity instanceof net.minecraft.world.entity.projectile.ThrownPotion)return true;
        if(entity.getType().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,gascraft.gas.GasCatalog.id("magic_projectiles"))))return true;
        for(Class<?> type=entity.getClass();type!=null;type=type.getSuperclass())if(type.getName().equals("io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile"))return true;
        return false;
    }
    private GasMagicCompat(){}
}
