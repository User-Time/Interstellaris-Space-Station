package net.time_cloud.navigation;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.time_cloud.*;

/** Only discovery is new protocol; all flight controls remain native SicPacket messages. */
public final class StationUnlockNetwork {
    private static volatile Set<String> client=Set.of();
    private StationUnlockNetwork(){}
    public static boolean visited(String id){return client.contains(id);}
    public static void clear(){client=Set.of();StationDestinations.reset();}
    public static Set<String> visibleVisited(ServerPlayer player){
        Set<String> known=StationVisits.get(player.server).forPlayer(player.getUUID());
        Set<String> result=new HashSet<>();
        for(Habitat h:HabitatRuntime.serverSnapshot().habitats())
            if(h.enabled()&&StationDirectory.canView(h,player.getUUID())&&known.contains(h.id()))result.add(h.id());
        return Set.copyOf(result);
    }
    public static void send(ServerPlayer player){PacketDistributor.sendToPlayer(player,new Payload(visibleVisited(player)));}
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("4").playToClient(Payload.TYPE,Payload.CODEC,(p,c)->c.enqueueWork(()->client=p.ids()));
    }
    public record Payload(Set<String> ids) implements CustomPacketPayload {
        public Payload {ids=Set.copyOf(ids);if(ids.size()>512||ids.stream().anyMatch(id->!VisitLedger.validId(id)))throw new IllegalArgumentException("invalid station unlocks");}
        public static final Type<Payload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(HabitatRuntime.ID,"station_visits"));
        public static final StreamCodec<FriendlyByteBuf,Payload> CODEC=new StreamCodec<>(){
            public Payload decode(FriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>512)throw new IllegalArgumentException("station unlock count");Set<String> ids=new HashSet<>();for(int i=0;i<n;i++)if(!ids.add(b.readUtf(48)))throw new IllegalArgumentException("duplicate station unlock");return new Payload(ids);}
            public void encode(FriendlyByteBuf b,Payload p){b.writeVarInt(p.ids().size());p.ids().stream().sorted().forEach(id->b.writeUtf(id,48));}
        };
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
