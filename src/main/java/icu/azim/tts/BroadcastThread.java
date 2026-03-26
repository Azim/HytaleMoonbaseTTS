package icu.azim.tts;

import java.util.ArrayList;
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

    public record BroadcastData(int entityId, List<byte[]> audioData, int timestamp, Collection<PlayerRef> receivers) {
        BroadcastData advance() {
            if (audioData.isEmpty()) {
                //should never be called - we clean up all entries which have empty audio data
                return this;
            }
            List<byte[]> copy = new ArrayList<>(audioData);
            copy.removeFirst();
            return new BroadcastData(entityId, copy, timestamp + TTSPlugin.FRAME_SIZE, receivers);
        }
    }
    
    private ConcurrentHashMap<UUID, BroadcastData> toBroadcast = new ConcurrentHashMap<>();
    
    private static short sequenceNumber = 0;
    
    @Override
    public void run() {
        if(toBroadcast.isEmpty()) return;
        
        
        for(UUID sender : toBroadcast.keySet()) {
            BroadcastData data = toBroadcast.get(sender);
            byte[] currentFrameData = data.audioData.get(0);
            
            for(PlayerRef receiver : data.receivers) {

                var voiceChannel = receiver.getPacketHandler().getChannel(StreamType.Voice);
                if (voiceChannel == null || !voiceChannel.isActive()) {
                    HytaleLogger.get("TTS broadcast").atInfo().log("no active voice channel for "+receiver.getUsername()+"|"+receiver.getUuid());
                    return;
                }
                
                PositionSnapshot cachedposition = VoiceModule.get().getCachedPosition(sender); //FIXME configuration on if to use positional audio or not
                Position position = new Position(cachedposition.x(), cachedposition.y(), cachedposition.z());
                
                
                RelayedVoiceData relay = new RelayedVoiceData();
                relay.entityId = data.entityId;
                relay.sequenceNumber = sequenceNumber++;
                relay.timestamp = data.timestamp;
                relay.speakerIsUnderwater = VoiceModule.get().getCachedPosition(sender).isUnderwater();
                //TODO replace with "unset" after the patch is out defaulting "no position" to "listener position"
                //TODO add many convenience methods for different TTS settings
                relay.speakerPosition = position; 
                relay.opusData = currentFrameData;
                relay.speakerId = sender;
                voiceChannel.writeAndFlush(relay);
                
            }
            
            toBroadcast.compute(sender, (key, value) -> {
                return value.advance();
            });
        }
        
        toBroadcast.entrySet().removeIf(entry -> entry.getValue().audioData.isEmpty());
        
    }
    

    public void broadcastSpeech(UUID sender, int entityId, List<byte[]> audioData, Collection<PlayerRef> receivers) {
        if(audioData.isEmpty() || receivers.isEmpty()) return;
        BroadcastData newData = new BroadcastData(entityId, audioData, 0, receivers);
        toBroadcast.merge(sender, newData, (v1, v2) -> { //append if user is sending many messages
            v1.audioData.addAll(v2.audioData);
            return v1;
        });
          
    }

}
