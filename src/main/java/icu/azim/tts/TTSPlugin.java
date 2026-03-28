package icu.azim.tts;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;

import icu.azim.hyyap.HyYapPlugin;

public class TTSPlugin extends JavaPlugin {

    private final Config<TTSConfig> config = this.withConfig("HytaleMoonbaseTTSConfig", TTSConfig.CODEC);
    private HashMap<UUID, CompletableFuture<Void>> playersSpeech = new HashMap<>(); // store who is already speaking as to not overlap audio from the same speaker

    public TTSPlugin(JavaPluginInit init) {
        super(init);
        config.load().thenRun(() -> config.save()); // im doing config wrong aint i
    }

    @Override
    protected void setup() {

        this.getEventRegistry().registerGlobal(PlayerChatEvent.class, event -> {
            Ref<EntityStore> ref = event.getSender().getReference();
            UUID sender = event.getSender().getUuid();
            Store<EntityStore> store = ref.getStore();
            String message = config.get().getCommandPrefix() + event.getContent() + config.get().getCommandSuffix();
            store.getExternalData().getWorld().execute(() -> {
                NetworkId networkIdComponent = store.getComponent(ref, NetworkId.getComponentType());

                CompletableFuture<Void> playerFuture = playersSpeech.getOrDefault(sender,CompletableFuture.completedFuture(null));

                CompletableFuture<List<byte[]>> spokenFuture = HyYapPlugin.getInstance().getDectalk().speakAndEncode(message);

                CompletableFuture<Void> combined = playerFuture.handle((v, ex) -> null)// is over one way or another
                        .thenCombine(spokenFuture, (ignored, frames) -> frames) // wait for tts to generate
                        .thenCompose(frames -> { // broadcast
                            if (config.get().isPositionalAudioEnabled()) {
                                return HyYapPlugin.getInstance().getBroadcastThread().broadcastAtSpeaker(sender, networkIdComponent.getId(), frames, event.getTargets());
                            } else {
                                return HyYapPlugin.getInstance().getBroadcastThread().broadcastPositionless(sender, networkIdComponent.getId(), frames, event.getTargets());
                            }
                        }).exceptionally((ex) -> { // completable futures swallow exceptions, handle them
                            ex.printStackTrace();
                            return null;
                        });
                
                playersSpeech.put(sender, combined);
            });

        });

    }

    @Override
    protected void shutdown() {
        super.shutdown();
    }

}
