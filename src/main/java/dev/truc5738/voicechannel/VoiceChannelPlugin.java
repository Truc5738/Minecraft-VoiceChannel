package dev.truc5738.voicechannel;

import org.bukkit.plugin.java.JavaPlugin;

public final class VoiceChannelPlugin extends JavaPlugin {
    private VoiceManager voiceManager;
    private VoiceMenu voiceMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        voiceManager = new VoiceManager(this);
        voiceMenu = new VoiceMenu(this, voiceManager);

        VoiceCommand command = new VoiceCommand(voiceMenu);
        getCommand("voicechannel").setExecutor(command);
        getCommand("voicechannel").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(voiceMenu, this);
        getLogger().info("Minecraft-VoiceChannel enabled.");
    }

    @Override
    public void onDisable() {
        if (voiceManager != null) {
            voiceManager.shutdown();
        }
    }

    public VoiceManager getVoiceManager() {
        return voiceManager;
    }
}
