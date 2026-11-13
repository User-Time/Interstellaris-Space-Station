package net.time_cloud;

import com.interstellaris.mod.environment.Environment;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class HabitatNetwork {
    private static volatile HabitatSnapshot client = HabitatSnapshot.EMPTY;
    private static volatile Map<ResourceLocation,Environment> backgrounds = Map.of();
    private static final Map<UUID, String> ACKS = new ConcurrentHashMap<>();
    private HabitatNetwork() {}
    public static HabitatSnapshot clientSnapshot() { return client; }
    public static void resetClient() { client = HabitatSnapshot.EMPTY; backgrounds=Map.of(); HabitatRuntime.clearClient();StationClientCache.clear();net.time_cloud.navigation.StationUnlockNetwork.clear(); }
    public static Environment background(ResourceLocation dimension) { return backgrounds.get(dimension); }
    public static void resetServer() { ACKS.clear(); }
    public static void remove(UUID player) { ACKS.remove(player); }
    public static boolean acknowledged(ServerPlayer player) {
        return HabitatRuntime.serverSnapshot().revision().equals(ACKS.get(player.getUUID()));
    }
    public static void send(ServerPlayer player, HabitatSnapshot snapshot) {
        ACKS.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new SnapshotPayload(snapshot.revision(), snapshot.habitats(),HabitatRuntime.backgrounds()));
    }
    public static void broadcast(HabitatSnapshot snapshot) {
        ACKS.clear(); PacketDistributor.sendToAllPlayers(new SnapshotPayload(snapshot.revision(), snapshot.habitats(),HabitatRuntime.backgrounds()));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // Required on both sides: a missing client must not silently use incompatible movement physics.
        var registrar = event.registrar("4");
        registrar.playToClient(SnapshotPayload.TYPE, SnapshotPayload.CODEC, (payload, context) -> context.enqueueWork(() -> {
            try {
                HabitatSnapshot received = new HabitatSnapshot(payload.habitats());
                if (!received.revision().equals(payload.revision())) throw new IllegalArgumentException("revision mismatch");
                Map<ResourceLocation,Environment> base=new HashMap<>();
                for(Environment environment:payload.backgrounds()) if(base.put(environment.dimension(),environment)!=null)
                    throw new IllegalArgumentException("duplicate environment");
                backgrounds=Map.copyOf(base);client = received; HabitatRuntime.clearEnvironmentCache();
                context.reply(new AckPayload(received.revision()));
            } catch (RuntimeException exception) {
                resetClient(); context.disconnect(Component.literal("空间站环境配置校验失败，请重新连接"));
            }
        }));
        registrar.playToServer(AckPayload.TYPE, AckPayload.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && payload.revision().equals(HabitatRuntime.serverSnapshot().revision()))
                ACKS.put(player.getUUID(), payload.revision());
        }));
    }

    public record SnapshotPayload(String revision, List<Habitat> habitats,List<Environment> backgrounds) implements CustomPacketPayload {
        public static final Type<SnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HabitatRuntime.ID, "habitats"));
        public static final StreamCodec<FriendlyByteBuf, SnapshotPayload> CODEC = new StreamCodec<>() {
            @Override public SnapshotPayload decode(FriendlyByteBuf buffer) {
                String revision = buffer.readUtf(64);
                int count = buffer.readVarInt();
                if (count < 0 || count > HabitatSnapshot.MAX_HABITATS) throw new IllegalArgumentException("habitat count");
                List<Habitat> habitats = new ArrayList<>(count);
                for (int i = 0; i < count; i++) habitats.add(new Habitat(buffer.readUtf(48), buffer.readUtf(128),
                        buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                        buffer.readDouble(), buffer.readDouble(), buffer.readBoolean(),buffer.readDouble(),buffer.readDouble(),GravityMode.valueOf(buffer.readUtf(16)),null));
                int environments=buffer.readVarInt();
                if(environments<0||environments>512)throw new IllegalArgumentException("environment count");
                List<Environment> backgrounds=new ArrayList<>(environments);
                for(int i=0;i<environments;i++)backgrounds.add(Environment.read(buffer));
                return new SnapshotPayload(revision, List.copyOf(habitats),List.copyOf(backgrounds));
            }
            @Override public void encode(FriendlyByteBuf buffer, SnapshotPayload payload) {
                if (payload.habitats().size() > HabitatSnapshot.MAX_HABITATS || !payload.revision().matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("invalid snapshot");
                buffer.writeUtf(payload.revision(), 64); buffer.writeVarInt(payload.habitats().size());
                for (Habitat habitat : payload.habitats()) {
                    buffer.writeUtf(habitat.id(), 48); buffer.writeUtf(habitat.dimension(), 128);
                    buffer.writeDouble(habitat.x()); buffer.writeDouble(habitat.y()); buffer.writeDouble(habitat.z());
                    buffer.writeDouble(habitat.radius()); buffer.writeDouble(habitat.temperatureC());
                    buffer.writeDouble(habitat.gravityMultiplier()); buffer.writeBoolean(habitat.enabled());
                    buffer.writeDouble(habitat.floorDistance());
                    buffer.writeDouble(habitat.gravityRadius());buffer.writeUtf(habitat.gravityMode().name(),16);
                }
                if(payload.backgrounds().size()>512)throw new IllegalArgumentException("environment count");
                buffer.writeVarInt(payload.backgrounds().size());for(Environment environment:payload.backgrounds())environment.write(buffer);
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record AckPayload(String revision) implements CustomPacketPayload {
        public static final Type<AckPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HabitatRuntime.ID, "ack"));
        public static final StreamCodec<FriendlyByteBuf, AckPayload> CODEC = new StreamCodec<>() {
            @Override public AckPayload decode(FriendlyByteBuf buffer) { return new AckPayload(buffer.readUtf(64)); }
            @Override public void encode(FriendlyByteBuf buffer, AckPayload payload) { buffer.writeUtf(payload.revision(), 64); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
