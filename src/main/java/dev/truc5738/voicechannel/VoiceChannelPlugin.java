package dev.truc5738.voicechannel;

import org.bukkit.plugin.java.JavaPlugin;

public final class VoiceChannelPlugin extends JavaPlugin {
    private VoiceManager voiceManager;
    private VoiceMenu voiceMenu;
    private VoiceGateway voiceGateway;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        voiceManager = new VoiceManager(this);
        voiceGateway = new VoiceGateway(this, voiceManager);
        voiceGateway.start();

        voiceMenu = new VoiceMenu(this, voiceManager);

        VoiceCommand command = new VoiceCommand(voiceMenu, voiceManager);
        if (getCommand("voicechannel") != null) {
            getCommand("voicechannel").setExecutor(command);
            getCommand("voicechannel").setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(voiceMenu, this);
        getLogger().info("Minecraft-VoiceChannel enabled.");
    }

    @Override
    public void onDisable() {
        if (voiceGateway != null) voiceGateway.stop();
        if (voiceManager != null) voiceManager.shutdown();
    }

    public VoiceManager getVoiceManager() {
        return voiceManager;
    }

    public VoiceGateway getVoiceGateway() {
        return voiceGateway;
    }
}
