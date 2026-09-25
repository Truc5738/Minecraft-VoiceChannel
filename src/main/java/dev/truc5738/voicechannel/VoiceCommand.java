package dev.truc5738.voicechannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

public final class VoiceCommand implements CommandExecutor, TabCompleter {
    private final VoiceMenu menu;
    private final VoiceManager manager;

    public VoiceCommand(VoiceMenu menu, VoiceManager manager) {
        this.menu = menu;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        if (!player.hasPermission("voicechannel.use")) {
            player.sendMessage("You do not have permission to use voice channels.");
            return true;
        }
        if (args.length == 0) {
            menu.open(player);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "join" -> {
                if (args.length < 2) {
                    player.sendMessage("Usage: /voicechannel join <channel>");
                } else if (manager.joinChannel(player, args[1])) {
                    player.sendMessage("Joined voice channel: " + args[1]);
                } else {
                    player.sendMessage("That voice channel does not exist.");
                }
            }
            case "private" -> {
                if (args.length < 3) {
                    player.sendMessage("Usage: /voicechannel private <name> <password>");
                } else if (manager.createPrivateChannel(player, args[1], args[2])) {
                    player.sendMessage("Private voice channel created: " + args[1]);
                } else {
                    player.sendMessage("Could not create that private channel.");
                }
            }
            case "privatejoin" -> {
                if (args.length < 3) {
                    player.sendMessage("Usage: /voicechannel privatejoin <name> <password>");
                } else if (manager.joinPrivateChannel(player, args[1], args[2])) {
                    player.sendMessage("Joined private voice channel: " + args[1]);
                } else {
                    player.sendMessage("Invalid private channel or password.");
                }
            }
            case "leave" -> manager.joinDefaultChannel(player);
            case "mic" -> player.sendMessage("Microphone: " + (manager.toggleMic(player) ? "Muted" : "Active"));
            case "output" -> player.sendMessage("Voice output: " + (manager.toggleOutput(player) ? "Muted" : "Active"));
            case "pair" -> {
                VoiceChannelPlugin vp = (VoiceChannelPlugin) player.getServer().getPluginManager().getPlugin("Minecraft-VoiceChannel");
                if (vp == null || vp.getVoiceGateway() == null) {
                    player.sendMessage("Voice gateway is unavailable.");
                } else {
                    String code = vp.getVoiceGateway().createPairCode(player.getUniqueId());
                    int port = vp.getConfig().getInt("voice.gateway.port", 26467);
                    player.sendMessage("Bedrock pairing code: " + code);
                    player.sendMessage("Code expires in 120 seconds. Gateway port: " + port);
                }
            }
            case "status" -> {
                boolean bedrock = false;
                try {
                    bedrock = FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
                } catch (Throwable ignored) {
                }
                player.sendMessage("Voice status: " + (manager.isConnected(player.getUniqueId()) ? "Connected" : "Disconnected"));
                player.sendMessage("Platform: " + (bedrock ? "Bedrock" : "Java"));
                VoiceChannelPlugin vp = (VoiceChannelPlugin) player.getServer().getPluginManager().getPlugin("Minecraft-VoiceChannel");
                int port = vp != null ? vp.getConfig().getInt("voice.gateway.port", 26467) : 26467;
                player.sendMessage("Gateway port: " + port);
                if (vp != null && vp.getVoiceGateway() != null) {
                    VoiceGateway gateway = vp.getVoiceGateway();
                    player.sendMessage("Gateway listener: " + (gateway.isRunning() ? "Online" : "Offline"));
                    player.sendMessage("Gateway bind: " + gateway.getBindHost() + ":" + gateway.getPort());
                    player.sendMessage("Active voice sessions: " + gateway.getConnectedClients());
                }
            }
            default -> menu.open(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !player.hasPermission("voicechannel.use")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> values = List.of("join", "private", "privatejoin", "leave", "mic", "output", "pair", "status");
            List<String> result = new ArrayList<>();
            for (String value : values) {
                if (value.startsWith(args[0].toLowerCase())) result.add(value);
            }
            return result;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("join")
                || args[0].equalsIgnoreCase("privatejoin"))) {
            List<String> result = new ArrayList<>();
            for (String channel : manager.getChannels()) {
                if (channel.toLowerCase().startsWith(args[1].toLowerCase())) result.add(channel);
            }
            return result;
        }
        return Collections.emptyList();
    }
}
