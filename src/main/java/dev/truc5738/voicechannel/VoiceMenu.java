package dev.truc5738.voicechannel;

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
                .button("Close");

        form.validResultHandler(result -> handleBedrockResult(player, result.clickedButtonId()));
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private void handleBedrockResult(Player player, int id) {
        switch (id) {
            case 0 -> manager.toggleMic(player);
            case 1 -> manager.toggleOutput(player);
            case 3 -> manager.setRange(player, manager.getRange(player) >= 64 ? 32 : 64);
            case 4 -> manager.setVolume(player, manager.getVolume(player) >= 1.5 ? 1.0 : 1.5);
            default -> {
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> open(player));
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
            case 14 -> manager.setRange(player, manager.getRange(player) >= 64 ? 32 : 64);
            case 15 -> manager.setVolume(player, manager.getVolume(player) >= 1.5 ? 1.0 : 1.5);
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.remove(event.getPlayer());
    }

    private void set(Inventory inventory, int slot, String name, String lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ChatColor.WHITE + name);
        meta.lore(List.of(ChatColor.GRAY + lore));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
