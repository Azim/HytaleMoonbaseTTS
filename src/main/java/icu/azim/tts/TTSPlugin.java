package icu.azim.tts;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import icu.azim.tts.util.DependencyLoader;

public class TTSPlugin extends JavaPlugin {
    public static final int BITRATE = 48000;
    public static final int FRAME_SIZE = 960;
    
    private static final ScheduledExecutorService SENDER_SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private static final ScheduledExecutorService TTS_SCHEDULER = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> senderFuture = null; 
    private ScheduledFuture<?> ttsFuture = null;
    private TTSThread ttsThread;
    private BroadcastThread senderThread;
    
    public TTSPlugin(JavaPluginInit init) {
        super(init);
        DependencyLoader.load(this);
    }
    
    @Override
    protected void setup() {
        senderThread = new BroadcastThread(); 
        senderFuture = SENDER_SCHEDULER.scheduleAtFixedRate(senderThread, 0, 20, TimeUnit.MILLISECONDS); //every 20 ms

        ttsThread = new TTSThread(senderThread);
        ttsFuture = TTS_SCHEDULER.scheduleWithFixedDelay(ttsThread, 0, 200, TimeUnit.MILLISECONDS); //5 times per second
        
        //FIXME need ttsThread reference in the message handler but i dont want to make it static so i will do that instead for now 
        this.getEventRegistry().registerGlobal(PlayerChatEvent.class, event -> { 
            Ref<EntityStore> ref = event.getSender().getReference();
            Store<EntityStore> store = ref.getStore();
            store.getExternalData().getWorld().execute(()->{
                NetworkId networkIdComponent = store.getComponent(ref, NetworkId.getComponentType());
                ttsThread.speak(event.getSender().getUuid(), networkIdComponent.getId(), event.getContent(), event.getTargets());
            });
            
        });
        
    }
    
    @Override
    protected void shutdown() {
        super.shutdown();
        if(ttsFuture != null) ttsFuture.cancel(false); 
        if(senderFuture != null) senderFuture.cancel(true);
    }
    
    public static short[] generateBeepFrame(double freqHz, double amplitude) { //i will keep it around for now, maybe will reuse for something later
        int frameSize = 960; // 20 ms @ 48 kHz
        short[] samples = new short[frameSize];
        double twoPiF = 2.0 * Math.PI * freqHz;
        for (int i = 0; i < frameSize; i++) {
            double t = i / (double) 48000;
            double s = Math.sin(twoPiF * t);
            samples[i] = (short) Math.round(s * amplitude * Short.MAX_VALUE);
        }
        return samples;
    }

}
