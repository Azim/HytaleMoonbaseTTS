package icu.azim.tts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import de.maxhenkel.opus4j.OpusEncoder;
import de.maxhenkel.opus4j.UnknownPlatformException;
import dev.bytesizedfox.dectalk.TTSNative;

public class TTSThread implements Runnable {

    public record SpeechData(String text, CompletableFuture<List<byte[]>> future) {}
    
    private final ConcurrentLinkedQueue<SpeechData> messageQueue = new ConcurrentLinkedQueue<>();
    private boolean initialized;
    
    public TTSThread() { }
    
    public CompletableFuture<List<byte[]>> speakAndEncode(String text){
        if (!TTSNative.isLoaded()) {
            HytaleLogger.get("TTS processing").atInfo().log("TTSNative is not loaded! see server start logs to find out why!");
            return CompletableFuture.failedFuture(
                new IllegalStateException("TTSNative is not loaded")
            );
        }
        if(text.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<byte[]>()); //no input - no frames, no time wasted
        }
        
        CompletableFuture<List<byte[]>> result = new CompletableFuture<>();
        messageQueue.offer(new SpeechData(text, result));
        return result;
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
            HytaleLogger.get("TTS processing").atInfo().log("TTSNative is not loaded! see server start logs to find out why!");
            return;
        }
        ensureInitialized();

        SpeechData message = messageQueue.poll();
        if (message == null) {
            //no messages in queue
            return;
        }
        
        TTSNative.reset();
        TTSNative.speak("[:rate 180]" + message.text); //FIXME append reset commands after the text. move pre- and post- commands to plugin config 
        TTSNative.sync();
        
        int totalSamples = TTSNative.getAvailableSamples();
        if (totalSamples == 0) {
            message.future.complete(new ArrayList<byte[]>());
            return;
        }

        short[] audioData = new short[totalSamples];
        int copied = TTSNative.readSamples(audioData, totalSamples);

        List<byte[]> frames;
        try {
            frames = generateOpusFrames(audioData);
            message.future.complete(frames);
        } catch (IOException | UnknownPlatformException e) {
            e.printStackTrace();
            HytaleLogger.get("TTS processing").atSevere().withCause(e).log("Error encoding speech to opus frames");
            message.future.completeExceptionally(e);
        }
    }
    
    
    public static List<byte[]> generateOpusFrames(short[] samples) throws IOException, UnknownPlatformException{
        //upsample 11025 -> 48000
        int outputLength = (int) Math.round(samples.length * (double) TTSPlugin.BITRATE / 11025);
        short[] output = new short[outputLength];

        double step = (double) 11025 / TTSPlugin.BITRATE;

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
        try (OpusEncoder encoder = new OpusEncoder(TTSPlugin.BITRATE, 1, OpusEncoder.Application.VOIP)) {
            int frameSize = TTSPlugin.FRAME_SIZE; //20ms at 48kHz 
            encoder.resetState(); // only reset once
            encoder.setMaxPayloadSize(1500);

            for (int i = 0; i < output.length; i += frameSize) {
                int len = Math.min(frameSize, output.length - i);

                short[] frame = new short[frameSize];
                System.arraycopy(output, i, frame, 0, len);
                byte[] result = encoder.encode(frame);
                frames.add(result);
            }
        }
        
        return frames;
    }

}
