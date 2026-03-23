package icu.azim.tts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import de.maxhenkel.opus4j.OpusEncoder;
import de.maxhenkel.opus4j.UnknownPlatformException;
import dev.bytesizedfox.dectalk.TTSNative;

public class TTSThread implements Runnable {

    public record SpeechData(UUID sender, String text, Collection<PlayerRef> receivers) {}
    
    private final ConcurrentLinkedQueue<SpeechData> messageQueue = new ConcurrentLinkedQueue<>();
    private boolean initialized;
    private BroadcastThread broadcaster;
    
    public TTSThread(BroadcastThread broadcaster) {
        this.broadcaster = broadcaster;
    }
    
    public void speak(UUID sender, String text, Collection<PlayerRef> receivers) {
        if (!TTSNative.isLoaded()) {
            System.out.println("speak - native not loaded!!!");
            return;
        }
        System.out.println("speak before offer");
        messageQueue.offer(new SpeechData(sender, text, receivers));
        System.out.println("speak after offer");
    }

    private void ensureInitialized() {
        if (!initialized && TTSNative.isLoaded()) {
            TTSNative.init();
            initialized = true;
        }
    }
    
    @Override
    public void run() {
        if (!TTSNative.isLoaded()) {
            System.out.println("TTSThread run - native not loaded!!!");
            return;
        }
        ensureInitialized();

        SpeechData message = messageQueue.poll();
        if (message == null) {
            return;
        }
        
        System.out.println("TTSThread before reset");

        TTSNative.reset();
        System.out.println("TTSThread after reset");
        TTSNative.speak("[:rate 180]" + message.text);
        System.out.println("TTSThread after speak");
        TTSNative.sync();
        
        System.out.println("TTSThread after sync");

        int totalSamples = TTSNative.getAvailableSamples();

        if (totalSamples == 0) {
            System.out.println("got 0 totalSamples");
            return;
        }

        short[] audioData = new short[totalSamples];
        int copied = TTSNative.readSamples(audioData, totalSamples);

        System.out.println("copied "+copied+" samples");
        if(copied < 10) return;
        
        List<byte[]> frames;
        try {
            System.out.println("before generate opus");
            frames = generateOpusFrames(audioData);
            System.out.println("after generate opus");
            broadcaster.broadcastSpeech(message.sender, frames, message.receivers);
            System.out.println("after broadcastSpeech");
        } catch (IOException | UnknownPlatformException e) {
            e.printStackTrace();
        }
    }
    
    
    private static List<byte[]> generateOpusFrames(short[] samples) throws IOException, UnknownPlatformException{
        
        //upsample 11025 -> 12000
        int outputLength = (int) Math.round(samples.length * (double) 12000 / 11025);
        short[] output = new short[outputLength];

        double step = (double) 11025 / 12000;

        for (int i = 0; i < output.length; i++) {
            double srcPos = i * step;
            int leftIndex = (int) Math.floor(srcPos);
            double frac = srcPos - leftIndex;

            if (leftIndex >= samples.length - 1) {
                output[i] = samples[samples.length - 1];
            } else {
                int s1 = samples[leftIndex];
                int s2 = samples[leftIndex + 1];
                double sample = s1 + frac * (s2 - s1);
                output[i] = (short) Math.round(sample);
            }
        }
        
        //split into 20ms frames and encode with opus
        List<byte[]> frames = new ArrayList<byte[]>();
        try (OpusEncoder encoder = new OpusEncoder(12000, 1, OpusEncoder.Application.VOIP)) {
            int frameSize = 240; //20ms at 12kHz 

            for (int i = 0; i < output.length; i += frameSize) {
                int len = Math.min(frameSize, output.length - i);

                //short[] frame = Arrays.copyOfRange(output, i, i+len);
                short[] frame = new short[frameSize];
                System.arraycopy(output, i, frame, 0, len);

                encoder.setMaxPayloadSize(1500);
                byte[] result = encoder.encode(frame);
                frames.add(result);
                encoder.resetState();
            }
            
            encoder.close();
        }
        
        
        return frames;
    }

}
