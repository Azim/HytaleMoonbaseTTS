package icu.azim.tts;

import com.hypixel.hytale.codec.Codec; // Careful to not use other Codec imports
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class TTSConfig {
    public TTSConfig() {
    }
    public static final BuilderCodec<TTSConfig> CODEC = BuilderCodec.builder(TTSConfig.class, TTSConfig::new)
            .append(new KeyedCodec<String>("CommandPrefix", Codec.STRING),
                    (config, value) -> config.commandPrefix = value,
                    (config) -> config.commandPrefix).add()
            .append(new KeyedCodec<String>("CommandSuffix", Codec.STRING),
                    (config, value) -> config.commandSuffix = value,
                    (config) -> config.commandSuffix).add()
            .append(new KeyedCodec<Boolean>("PositionalAudioEnabled", Codec.BOOLEAN),
                    (config, value) -> config.positionalAudioEnabled = value,
                    (config) -> config.positionalAudioEnabled).add()
            .build();


    private String commandPrefix = "[:rate 180][:np]";
    private String commandSuffix = "[:phoneme off][:error speak][:mode spell set][:mode spell off][:punct some]";
    private boolean positionalAudioEnabled = false;
    public String getCommandPrefix() {
        return commandPrefix;
    }

    public void setCommandPrefix(String commandPrefix) {
        this.commandPrefix = commandPrefix;
    }

    public String getCommandSuffix() {
        return commandSuffix;
    }

    public void setCommandSuffix(String commandSuffix) {
        this.commandSuffix = commandSuffix;
    }

    public boolean isPositionalAudioEnabled() {
        return positionalAudioEnabled;
    }

    public void setPositionalAudioEnabled(boolean positionalAudioEnabled) {
        this.positionalAudioEnabled = positionalAudioEnabled;
    }

}