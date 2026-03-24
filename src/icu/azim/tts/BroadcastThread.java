package icu.azim.tts;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.stream.StreamType;
import com.hypixel.hytale.protocol.packets.voice.RelayedVoiceData;
import com.hypixel.hytale.server.core.modules.voice.VoiceModule;
import com.hypixel.hytale.server.core.modules.voice.VoiceModule.PositionSnapshot;
import com.hypixel.hytale.server.core.universe.PlayerRef;



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
            
            for(PlayerRef receiver : data.receivers) {

                var voiceChannel = receiver.getPacketHandler().getChannel(StreamType.Voice);
                if (voiceChannel == null || !voiceChannel.isActive()) {
                    HytaleLogger.get("TTS broadcast").atInfo().log("no active voice channel for "+receiver.getUsername()+"|"+receiver.getUuid());
                    return;
                }
                
                PositionSnapshot cachedposition = VoiceModule.get().getCachedPosition(receiver.getUuid());
                Position position = new Position(cachedposition.x(), cachedposition.y(), cachedposition.z());
                 
                RelayedVoiceData relay = new RelayedVoiceData();
                relay.entityId = 0;
                relay.sequenceNumber = sequenceNumber++;
                relay.timestamp = (int) System.currentTimeMillis();
                relay.speakerIsUnderwater = false;
                relay.speakerPosition = position;
                relay.opusData = currentFrameData;
                relay.speakerId = sender;
                voiceChannel.writeAndFlush(relay);
                
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
