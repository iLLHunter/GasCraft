package gascraft.gas;

import gascraft.entity.GasCloudEntity;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A release-time world flash and a separately protected screen flash. */
public final class GasFlash {
    public static Consumer<Payload> clientHandler = payload -> {};
    public record Payload(double x, double y, double z, int durationTicks, boolean screen) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(GasCatalog.id("gas_flash"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Payload::x, ByteBufCodecs.DOUBLE, Payload::y, ByteBufCodecs.DOUBLE, Payload::z,
            ByteBufCodecs.VAR_INT, Payload::durationTicks, ByteBufCodecs.BOOL, Payload::screen, Payload::new);
        @Override public Type<Payload> type() { return TYPE; }
    }
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Payload.TYPE, Payload.CODEC,
            (payload, context) -> context.enqueueWork(() -> clientHandler.accept(payload)));
    }
    public static boolean affectsScreen(LivingEntity player, GasCloudEntity cloud) {
        return cloud.gasAreaContains(player.position()) && GasParalysis.acceptsExposure(player, cloud.gasContents());
    }
    public static void release(ServerLevel level, GasCloudEntity cloud, int durationTicks) {
        for (var player : level.players()) if (player.distanceToSqr(cloud) <= 256 * 256)
            PacketDistributor.sendToPlayer(player, new Payload(cloud.getX(), cloud.getY(), cloud.getZ(), durationTicks, affectsScreen(player, cloud)));
    }
    private GasFlash() {}
}
