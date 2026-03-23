package icu.azim.tts;

import java.io.IOException;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.packets.stream.StreamType;
import com.hypixel.hytale.protocol.packets.voice.RelayedVoiceData;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import de.maxhenkel.opus4j.OpusEncoder;
import de.maxhenkel.opus4j.UnknownPlatformException;

public class BeepCommand extends AbstractPlayerCommand {

    protected BeepCommand(String name, String description) {
        super(name, description);
    }
    public static short sequenceNumber = 0;
    
    public static short[] generateBeepFrame(int sampleRate, double freqHz, double amplitude) {
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
    
    private byte[] boop() throws IOException, UnknownPlatformException {
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

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        //Player player = store.getComponent(ref, Player.getComponentType()); // also a component
        var voiceChannel = playerRef.getPacketHandler().getChannel(StreamType.Voice);
        if (voiceChannel == null || !voiceChannel.isActive()) return;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        
        context.sendMessage(Message.raw("Boop!"));
        byte[] encoded;
        try {
            encoded = boop();
        } catch (IOException | UnknownPlatformException e) {
            e.printStackTrace();
            return;
        }
        for(int i = 0; i < 20; i++) {
            RelayedVoiceData relay = new RelayedVoiceData();
            relay.entityId = 0;
            relay.sequenceNumber = sequenceNumber++;
            relay.timestamp = (int) System.currentTimeMillis()/1000;
            relay.speakerIsUnderwater = false;
            relay.speakerPosition = new Position(transform.getPosition().x, transform.getPosition().y, transform.getPosition().z);
            relay.opusData = encoded;
            relay.speakerId = playerRef.getUuid();
            voiceChannel.writeAndFlush(relay);
        }


    }

}
