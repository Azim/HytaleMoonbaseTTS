package icu.azim.tts;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import icu.azim.hyyap.HyYapPlugin;

public class TTSPlugin extends JavaPlugin {

    private final Config<TTSConfig> config = this.withConfig("HytaleMoonbaseTTSConfig", TTSConfig.CODEC);
    private HashMap<Ref<EntityStore>, CompletableFuture<Void>> playersSpeech = new HashMap<>(); // store who is already speaking as to not overlap audio from the same speaker

    public TTSPlugin(JavaPluginInit init) {
        super(init);
        config.load().thenRun(() -> config.save()); // im doing config wrong aint i
    }

    @Override
    protected void setup() {

        this.getEventRegistry().registerGlobal(PlayerChatEvent.class, event -> {

            HytaleLogger.get("moonbase chat").atInfo().log("got player chat");

            Ref<EntityStore> ref = event.getSender().getReference();
            String message = config.get().getCommandPrefix() + event.getContent() + config.get().getCommandSuffix();
            ref.getStore().getExternalData().getWorld().execute(() -> {
                HytaleLogger.get("moonbase chat").atInfo().log("execute on world");
                CompletableFuture<Void> playerFuture = playersSpeech.getOrDefault(ref, CompletableFuture.completedFuture(null));

                CompletableFuture<List<byte[]>> spokenFuture = HyYapPlugin.getInstance().getDectalk().speakAndEncode(message);

                CompletableFuture<Void> combined = playerFuture.handle((_, _) -> null)// is over one way or another
                        .thenCombine(spokenFuture, (_, frames) -> frames) // wait for tts to generate
                        .thenCompose(frames -> { // broadcast
                            HytaleLogger.get("moonbase chat").atInfo().log("then compose");
                            var targets = event.getTargets();
                            if(!config.get().canHearYourself()) {
                                targets.removeIf(t -> t.getReference().equals(ref));
                            }
                            
                            if (config.get().isPositionalAudioEnabled()) {
                                HytaleLogger.get("moonbase chat").atInfo().log("broadcast at speaker");
                                return HyYapPlugin.getInstance().getBroadcastThread().broadcastAtSpeaker(ref, frames, targets);
                            } else {
                                HytaleLogger.get("moonbase chat").atInfo().log("broadcast positionless");
                                return HyYapPlugin.getInstance().getBroadcastThread().broadcastPositionless(ref, frames, targets);
                            }
                        }).exceptionally((ex) -> { // completable futures swallow exceptions, handle them
                            ex.printStackTrace();
                            return null;
                        });
                
                playersSpeech.put(ref, combined);
            });

        });

         

    }

    @Override
    protected void shutdown() {
        super.shutdown();
    }

}
