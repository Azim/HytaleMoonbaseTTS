package icu.azim.tts;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.stream.StreamType;
import com.hypixel.hytale.protocol.packets.voice.RelayedVoiceData;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.voice.VoiceModule;
import com.hypixel.hytale.server.core.modules.voice.VoiceModule.PositionSnapshot;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;



public class BroadcastThread implements Runnable {

    public record BroadcastData(List<byte[]> audioData, Collection<PlayerRef> receivers) {}



    private ConcurrentHashMap<UUID, BroadcastData> toBroadcast = new ConcurrentHashMap<>();
    
    private static short sequenceNumber = 0;
    
    @Override
    public void run() {
        if(toBroadcast.isEmpty()) return;
        
        
        for(UUID sender : toBroadcast.keySet()) {
            BroadcastData data = toBroadcast.get(sender);
            byte[] currentFrameData = data.audioData.get(0);
            toBroadcast.compute(sender, (key, value) -> {
                value.audioData.remove(0);
                return value;
            });
            HytaleLogger.get("tts").atInfo().log("got into toBroadcast loop, frames left "+data.audioData.size());
            
            for(PlayerRef receiver : data.receivers) {
                HytaleLogger.get("tts").atInfo().log("got into inner loop");
                Store<EntityStore> store = receiver.getReference().getStore();
                
                var voiceChannel = receiver.getPacketHandler().getChannel(StreamType.Voice);
                HytaleLogger.get("tts").atInfo().log("got channel");
                if (voiceChannel == null || !voiceChannel.isActive()) {
                    HytaleLogger.get("tts").atInfo().log("no active voice channel, return");
                    return;
                }
                
                PositionSnapshot cachedposition = VoiceModule.get().getCachedPosition(receiver.getUuid());
                Position position = new Position(cachedposition.x(), cachedposition.y(), cachedposition.z());

                HytaleLogger.get("tts").atInfo().log("got cached pos");
                 
                RelayedVoiceData relay = new RelayedVoiceData();
                relay.entityId = 0;
                relay.sequenceNumber = sequenceNumber++;
                relay.timestamp = (int) System.currentTimeMillis();
                relay.speakerIsUnderwater = false;
                relay.speakerPosition = position;
                relay.opusData = currentFrameData;
                relay.speakerId = sender;
                voiceChannel.writeAndFlush(relay);
                HytaleLogger.get("tts").atInfo().log("sent packet");
                
            }

            
        }
        
        toBroadcast.entrySet().removeIf(entry -> entry.getValue().audioData.isEmpty());
        
    }
    

    public void broadcastSpeech(UUID sender, List<byte[]> audioData, Collection<PlayerRef> receivers) {
        if(audioData.isEmpty() || receivers.isEmpty()) return;
        BroadcastData newData = new BroadcastData(audioData, receivers);
        toBroadcast.merge(sender, newData, (v1, v2) -> { //append if user is sending many messages
            v1.audioData.addAll(v2.audioData);
            return v1;
        });
          
    }

}
