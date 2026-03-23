package icu.azim.tts;

import java.io.IOException;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.stream.StreamType;
import com.hypixel.hytale.protocol.packets.voice.RelayedVoiceData;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import de.maxhenkel.opus4j.OpusEncoder;
import de.maxhenkel.opus4j.UnknownPlatformException;

public class MessageHandler {
    
    private static short sequenceNumber = 0;
    
    private static short[] generateBeepFrame(int sampleRate, double freqHz, double amplitude) {
        int frameSize = 960; // 20 ms @ 48 kHz
        short[] samples = new short[frameSize];

        double twoPiF = 2.0 * Math.PI * freqHz;
        for (int i = 0; i < frameSize; i++) {
            double t = i / (double) sampleRate;
            double s = Math.sin(twoPiF * t);
            samples[i] = (short) Math.round(s * amplitude * Short.MAX_VALUE);
        }
        return samples;
    }
    
    private static byte[] boop() throws IOException, UnknownPlatformException {
        int sampleRate = 48000;
        double freqHz = 1000.0;
        
        // Mono beep suitable for many Opus encoders
        short[] beep = generateBeepFrame(sampleRate, freqHz, 0.6);
        
        // Creates a new encoder instance with 48kHz mono VOIP
        try (OpusEncoder encoder = new OpusEncoder(sampleRate, 1, OpusEncoder.Application.VOIP)) {
            // Sets the max payload size to 1500 bytes
            encoder.setMaxPayloadSize(1500);

            // Sets the max packet loss percentage to 1% for in-band FEC
            //encoder.setMaxPacketLossPercentage(0.01F);

            // Encodes the raw audio
            byte[] result = encoder.encode(beep);

            encoder.resetState();
            encoder.close();
            return result;
        }
    }
    
    
    
    public static void onPlayerChat(PlayerChatEvent event) {
        
        PlayerRef playerRef = event.getSender();
        
        byte[] encoded;
        try {
            encoded = boop();
        } catch (IOException | UnknownPlatformException e) {
            e.printStackTrace();
            return;
        }
        
        for(PlayerRef receiver : event.getTargets()) {
            Store<EntityStore> store = receiver.getReference().getStore();
            store.getExternalData().getWorld().execute(()->{
                var voiceChannel = receiver.getPacketHandler().getChannel(StreamType.Voice);
                if (voiceChannel == null || !voiceChannel.isActive()) return;
                TransformComponent transform = store.getComponent(receiver.getReference(), TransformComponent.getComponentType());
                Position position = new Position(transform.getPosition().x, transform.getPosition().y, transform.getPosition().z);
                
                for(int i = 0; i < 20; i++) {
                    RelayedVoiceData relay = new RelayedVoiceData();
                    relay.entityId = 0;
                    relay.sequenceNumber = sequenceNumber++;
                    relay.timestamp = (int) System.currentTimeMillis()/1000;
                    relay.speakerIsUnderwater = false;
                    relay.speakerPosition = position;
                    relay.opusData = encoded;
                    relay.speakerId = playerRef.getUuid();
                    voiceChannel.writeAndFlush(relay);
                }
            });
            
            
        }
       
        
        
    }
}
