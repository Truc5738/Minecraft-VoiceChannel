package dev.truc5738.voicechannel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

public final class VoiceMenu implements Listener {
    private final JavaPlugin plugin;
    private final VoiceManager manager;

    public VoiceMenu(JavaPlugin plugin, VoiceManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void open(Player player) {
        if (isBedrock(player)) openBedrock(player);
        else openJava(player);
    }

    private boolean isBedrock(Player player) {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void openJava(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27,
                plugin.getConfig().getString("ui.title", "Voice Channel"));

        set(inventory, 10, "Microphone", manager.isMicMuted(player) ? "Muted" : "Active");
        set(inventory, 11, "Voice Output", manager.isOutputMuted(player) ? "Muted" : "Active");
        set(inventory, 12, "Channel", manager.getChannel(player));
        set(inventory, 13, "Speaking Players", Integer.toString(manager.countSpeaking(player)));
        set(inventory, 14, "Voice Range", format(manager.getRange(player)));
        set(inventory, 15, "Volume", format(manager.getVolume(player)));
        set(inventory, 16, "Player Mute", "Open player list");
        set(inventory, 21, "Private Channel", "Create or join private channel");
        set(inventory, 22, "Channel Moderation", "Admin controls");
        set(inventory, 23, "Voice Settings", "Open settings");
        set(inventory, 24, "Pair Device", "Generate a Bedrock/voice client pairing code");
        set(inventory, 26, "Close", "Close this menu");

        player.openInventory(inventory);
    }

    private void openBedrock(Player player) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title(plugin.getConfig().getString("ui.title", "Voice Channel"))
                .content(buildBedrockContent(player))
                .button("Microphone")
                .button("Voice Output")
                .button("Channel")
                .button("Voice Range")
                .button("Volume")
                .button("Player Mute")
                .button("Private Channel")
                .button("Channel Moderation")
                .button("Voice Settings")
                .button("Pair Device")
                .button("Close");

        form.validResultHandler(result -> handleBedrockResult(player, result.clickedButtonId()));
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private void handleBedrockResult(Player player, int id) {
        switch (id) {
            case 0 -> manager.toggleMic(player);
            case 1 -> manager.toggleOutput(player);
            case 2 -> {
                openBedrockChannels(player);
                return;
            }
            case 3 -> manager.setRange(player, manager.getRange(player) >= 64 ? 32 : 64);
            case 4 -> manager.setVolume(player, manager.getVolume(player) >= 1.5 ? 1.0 : 1.5);
            case 5 -> {
                openBedrockMutePlayers(player);
                return;
            }
            case 6 -> {
                openBedrockPrivateChannel(player);
                return;
            }
            case 7 -> {
                openBedrockModeration(player);
                return;
            }
            case 8 -> {
                player.sendMessage(ChatColor.GRAY + "Voice settings: range " + format(manager.getRange(player))
                        + ", volume " + format(manager.getVolume(player)) + ".");
                return;
            }
            case 9 -> {
                sendPairCode(player);
                return;
            }
            default -> {
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> open(player));
    }

    private void openBedrockChannels(Player player) {
        List<String> channels = new ArrayList<>(manager.getPublicChannels());
        channels.sort(String.CASE_INSENSITIVE_ORDER);
        SimpleForm.Builder form = SimpleForm.builder()
                .title("Voice Channels")
                .content("Current channel: " + manager.getChannel(player));
        for (String channel : channels) form.button(channel);
        form.button("Back");
        form.validResultHandler(result -> {
            int id = result.clickedButtonId();
            if (id == channels.size()) {
                open(player);
                return;
            }
            if (id >= 0 && id < channels.size()) {
                String channel = channels.get(id);
                if (manager.joinChannel(player, channel)) {
                    player.sendMessage(ChatColor.GREEN + "Joined voice channel: " + channel);
                }
            }
            open(player);
        });
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private void openBedrockMutePlayers(Player player) {
        List<Player> targets = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!target.equals(player)) targets.add(target);
        }
        targets.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        SimpleForm.Builder form = SimpleForm.builder()
                .title("Player Mute")
                .content("Select a player to mute or unmute.");
        for (Player target : targets) {
            form.button(target.getName() + (manager.isMuted(player, target) ? " [Muted]" : ""));
        }
        form.button("Back");
        form.validResultHandler(result -> {
            int id = result.clickedButtonId();
            if (id == targets.size()) {
                open(player);
                return;
            }
            if (id >= 0 && id < targets.size()) {
                Player target = targets.get(id);
                manager.toggleMute(player, target);
                player.sendMessage(ChatColor.GREEN + (manager.isMuted(player, target) ? "Player muted." : "Player unmuted."));
            }
            open(player);
        });
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private void openBedrockPrivateChannel(Player player) {
        CustomForm form = CustomForm.builder()
                .title("Private Channel")
                .label("Create a new private channel or join an existing one.")
                .dropdown("Action", "Create", "Join")
                .input("Channel name", "Private channel name")
                .input("Password", "Password")
                .validResultHandler(response -> {
                    String action = response.next();
                    String name = response.next();
                    String password = response.next();
                    boolean ok = "Create".equals(action)
                            ? manager.createPrivateChannel(player, name, password)
                            : manager.joinPrivateChannel(player, name, password);
                    player.sendMessage(ok
                            ? ChatColor.GREEN + ("Create".equals(action) ? "Private channel created." : "Joined private channel.")
                            : ChatColor.RED + "Could not complete the private channel request.");
                    Bukkit.getScheduler().runTask(plugin, () -> open(player));
                })
                .build();
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private void openBedrockModeration(Player player) {
        if (!player.hasPermission("voicechannel.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission.");
            return;
        }
        List<Player> targets = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) if (!target.equals(player)) targets.add(target);
        targets.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        SimpleForm.Builder form = SimpleForm.builder()
                .title("Channel Moderation")
                .content("Select a player.");
        for (Player target : targets) form.button(target.getName());
        form.button("Back");
        form.validResultHandler(result -> {
            int id = result.clickedButtonId();
            if (id == targets.size()) {
                open(player);
                return;
            }
            if (id >= 0 && id < targets.size()) {
                Player target = targets.get(id);
                openBedrockModerationActions(player, target);
                return;
            }
            open(player);
        });
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private void openBedrockModerationActions(Player player, Player target) {
        SimpleForm form = SimpleForm.builder()
                .title("Moderation: " + target.getName())
                .content("Choose an action for the current channel.")
                .button("Toggle channel mute")
                .button("Move to default channel")
                .button("Back")
                .validResultHandler(result -> {
                    switch (result.clickedButtonId()) {
                        case 0 -> manager.toggleChannelMute(manager.getChannel(player), target.getUniqueId());
                        case 1 -> manager.kickFromChannel(manager.getChannel(player), target.getUniqueId());
                        default -> {
                        }
                    }
                    openBedrockModeration(player);
                })
                .build();
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private String buildBedrockContent(Player player) {
        return "Channel: " + manager.getChannel(player) + "\n"
                + "Microphone: " + (manager.isMicMuted(player) ? "Muted" : "Active") + "\n"
                + "Voice output: " + (manager.isOutputMuted(player) ? "Muted" : "Active") + "\n"
                + "Range: " + format(manager.getRange(player)) + "\n"
                + "Volume: " + format(manager.getVolume(player)) + "\n"
                + "Speaking players: " + manager.countSpeaking(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(plugin.getConfig().getString("ui.title", "Voice Channel"))) return;

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        switch (event.getRawSlot()) {
            case 10 -> manager.toggleMic(player);
            case 11 -> manager.toggleOutput(player);
            case 12 -> {
                openJavaChannels(player);
                return;
            }
            case 14 -> manager.setRange(player, manager.getRange(player) >= 64 ? 32 : 64);
            case 15 -> manager.setVolume(player, manager.getVolume(player) >= 1.5 ? 1.0 : 1.5);
            case 16 -> {
                openJavaMutePlayers(player);
                return;
            }
            case 21 -> {
                player.sendMessage(ChatColor.YELLOW + "Use /voicechannel private <name> <password> to create a private channel.");
                player.sendMessage(ChatColor.YELLOW + "Use /voicechannel privatejoin <name> <password> to join one.");
                return;
            }
            case 22 -> {
                openJavaModeration(player);
                return;
            }
            case 23 -> {
                player.sendMessage(ChatColor.GRAY + "Voice settings: range " + format(manager.getRange(player))
                        + ", volume " + format(manager.getVolume(player))
                        + ", microphone " + (manager.isMicMuted(player) ? "muted" : "active")
                        + ", output " + (manager.isOutputMuted(player) ? "muted" : "active") + ".");
                return;
            }
            case 24 -> {
                sendPairCode(player);
                return;
            }
            case 26 -> {
                player.closeInventory();
                return;
            }
            default -> {
                return;
            }
        }
        openJava(player);
    }

    private void openJavaChannels(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "Voice Channels");
        int slot = 10;
        List<String> channels = new ArrayList<>(manager.getPublicChannels());
        channels.sort(String.CASE_INSENSITIVE_ORDER);
        for (String channel : channels) {
            if (slot >= 17) break;
            set(inv, slot++, channel, channel.equals(manager.getChannel(player)) ? "Current channel" : "Click to join");
        }
        set(inv, 22, "Back", "Return to voice menu");
        player.openInventory(inv);
    }

    private void openJavaMutePlayers(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "Player Mute");
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player) || slot >= 45) continue;
            set(inv, slot++, target.getName(), manager.isMuted(player, target) ? "Muted: click to unmute" : "Click to mute");
        }
        set(inv, 49, "Back", "Return to voice menu");
        player.openInventory(inv);
    }

    @EventHandler
    public void onSubMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals("Voice Channels") && !title.equals("Player Mute")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 22 || slot == 49) {
            openJava(player);
            return;
        }

        if (title.equals("Voice Channels") && slot >= 10 && slot < 17) {
            List<String> channels = new ArrayList<>(manager.getPublicChannels());
            channels.sort(String.CASE_INSENSITIVE_ORDER);
            int index = slot - 10;
            if (index < channels.size() && manager.joinChannel(player, channels.get(index))) {
                player.sendMessage(ChatColor.GREEN + "Joined voice channel: " + channels.get(index));
            }
            openJava(player);
            return;
        }

        if (title.equals("Player Mute") && slot >= 0 && slot < 45) {
            List<Player> targets = new ArrayList<>();
            for (Player target : Bukkit.getOnlinePlayers()) if (!target.equals(player)) targets.add(target);
            if (slot < targets.size()) {
                Player target = targets.get(slot);
                manager.toggleMute(player, target);
                player.sendMessage(ChatColor.GREEN + (manager.isMuted(player, target) ? "Player muted." : "Player unmuted."));
                openJavaMutePlayers(player);
            }
        }
    }

    @EventHandler
    public void onModerationClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("Channel Moderation")) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 49) {
            openJava(player);
            return;
        }
        if (!player.hasPermission("voicechannel.admin") || slot < 0 || slot >= 45) return;

        List<Player> targets = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) if (!target.equals(player)) targets.add(target);
        if (slot >= targets.size()) return;

        Player target = targets.get(slot);
        String channel = manager.getChannel(player);
        if (event.isRightClick()) {
            if (manager.kickFromChannel(channel, target.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + "Player moved to the default voice channel: " + target.getName());
            }
        } else {
            manager.toggleChannelMute(channel, target.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Channel mute toggled for " + target.getName() + ".");
        }
        openJavaModeration(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        VoiceChannelPlugin voicePlugin = (VoiceChannelPlugin) plugin;
        if (voicePlugin.getVoiceGateway() != null) {
            voicePlugin.getVoiceGateway().disconnect(event.getPlayer().getUniqueId());
        }
        manager.remove(event.getPlayer());
    }

    private void sendPairCode(Player player) {
        if (plugin instanceof VoiceChannelPlugin vp && vp.getVoiceGateway() != null && vp.getVoiceGateway().isRunning()) {
            String code = vp.getVoiceGateway().createPairCode(player.getUniqueId());
            int port = vp.getConfig().getInt("voice.gateway.port", 26467);
            player.sendMessage(ChatColor.GREEN + "Pairing code: " + code);
            player.sendMessage(ChatColor.GRAY + "Expires in 120 seconds. Gateway port: " + port + ".");
        } else {
            player.sendMessage(ChatColor.RED + "Voice gateway is unavailable.");
        }
    }

    private void set(Inventory inventory, int slot, String name, String lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, net.kyori.adventure.text.format.NamedTextColor.WHITE));
        meta.lore(List.of(Component.text(lore, net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
