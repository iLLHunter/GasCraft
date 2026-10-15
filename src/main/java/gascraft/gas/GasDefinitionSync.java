package gascraft.gas;
import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server definitions and sickness state are used even when a connecting client has different local config. */
public final class GasDefinitionSync {
    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(ResourceLocation.class, new LocationAdapter()).create();
    private static final class LocationAdapter implements JsonSerializer<ResourceLocation>, JsonDeserializer<ResourceLocation> {
        public JsonElement serialize(ResourceLocation id, java.lang.reflect.Type type, JsonSerializationContext context) { return new JsonPrimitive(id.toString()); }
        public ResourceLocation deserialize(JsonElement json, java.lang.reflect.Type type, JsonDeserializationContext context) { return ResourceLocation.parse(json.getAsString()); }
    }
    private record Snapshot(List<GasCatalog.GasDefinition> gases, List<GasCatalog.BrewingMix> mixes) {}
    public record CatalogPayload(String definitions) implements CustomPacketPayload {
        public static final Type<CatalogPayload> TYPE = new Type<>(GasCatalog.id("gas_definition_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CatalogPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(1048576), CatalogPayload::definitions, CatalogPayload::new);
        public Type<CatalogPayload> type() { return TYPE; }
    }
    public record SicknessPayload(CompoundTag effects) implements CustomPacketPayload {
        public static final Type<SicknessPayload> TYPE = new Type<>(GasCatalog.id("sickness_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SicknessPayload> CODEC = StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG, SicknessPayload::effects, SicknessPayload::new);
        public Type<SicknessPayload> type() { return TYPE; }
    }
    public static volatile CompoundTag clientSickness = new CompoundTag();
    public static Runnable clientCatalogChanged = () -> {};
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(CatalogPayload.TYPE, CatalogPayload.CODEC, (payload, context) -> context.enqueueWork(() -> {
            Snapshot snapshot = GSON.fromJson(payload.definitions(), Snapshot.class); GasCatalog.replaceGases(snapshot.gases()); GasCatalog.replaceMixes(snapshot.mixes());
            clientCatalogChanged.run();
        }));
        registrar.playToClient(SicknessPayload.TYPE, SicknessPayload.CODEC, (payload, context) -> context.enqueueWork(() -> clientSickness = payload.effects()));
    }
    public static void sync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) sendCatalog(event.getPlayer());
        else for (ServerPlayer player : event.getPlayerList().getPlayers()) sendCatalog(player);
    }
    public static void sendCatalog(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CatalogPayload(GSON.toJson(new Snapshot(List.copyOf(GasCatalog.gasDefinitions()), GasCatalog.brewingMixes()))));
    }
    private GasDefinitionSync() {}
}
