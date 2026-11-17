package de.powertools;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PowerToolsPlugin extends JavaPlugin implements TabExecutor, Listener {
    private NamespacedKey itemTypeKey;
    private NamespacedKey bucketUsesKey;
    private NamespacedKey bucketFilledKey;

    @Override public void onEnable() {
        itemTypeKey = new NamespacedKey(this, "tool_type");
        bucketUsesKey = new NamespacedKey(this, "bucket_uses");
        bucketFilledKey = new NamespacedKey(this, "bucket_filled");
        getCommand("power").setExecutor(this);
        getCommand("power").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("PowerTools 1.0.0 enabled.");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§6PowerTools §8» §f/power items give <player> <axe|drill|cellx|bucket>");
            return true;
        }
        if (!sender.hasPermission("powertools.admin")) {
            sender.sendMessage("§cDazu hast du keine Berechtigung.");
            return true;
        }
        if (args.length != 4 || !args[0].equalsIgnoreCase("items") || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("§cVerwendung: /power items give <player> <axe|drill|cellx|bucket>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage("§cSpieler ist nicht online.");
            return true;
        }
        ItemStack item = createTool(args[3]);
        if (item == null) {
            sender.sendMessage("§cUnbekanntes Tool. Nutze axe, drill, cellx oder bucket.");
            return true;
        }
        var leftovers = target.getInventory().addItem(item);
        leftovers.values().forEach(stack -> target.getWorld().dropItemNaturally(target.getLocation(), stack));
        sender.sendMessage("§aDu hast " + target.getName() + " §aein Tool gegeben.");
        target.sendMessage("§6PowerTools §8» §aDu hast " + item.getItemMeta().getDisplayName() + " §aerhalten.");
        return true;
    }

    private ItemStack createTool(String input) {
        String type = input.toLowerCase(Locale.ROOT);
        Material material;
        String name;
        String model;
        switch (type) {
            case "axe" -> { material = Material.NETHERITE_AXE; name = "§bBlitz Axe"; model = "blitz_axe"; }
            case "drill" -> { material = Material.NETHERITE_PICKAXE; name = "§bBlitz Drill"; model = "blitz_drill"; }
            case "cellx", "shovel", "spade" -> { material = Material.NETHERITE_SHOVEL; name = "§bBlitz CellX"; model = "blitz_cellx"; }
            case "bucket" -> { material = Material.BUCKET; name = "§bBlitz Bucket"; model = "blitz_bucket"; }
            default -> { return null; }
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setCustomModelData(type.hashCode() & 0x7fffffff);
        meta.setItemModel(NamespacedKey.fromString("powertools:" + model));
        meta.getPersistentDataContainer().set(itemTypeKey, PersistentDataType.STRING, model);
        if (type.equals("bucket")) {
            meta.getPersistentDataContainer().set(bucketUsesKey, PersistentDataType.INTEGER, 21);
            meta.getPersistentDataContainer().set(bucketFilledKey, PersistentDataType.BYTE, (byte) 0);
            meta.setLore(List.of("§7Wasser-Ladungen: §b21/21", "§8Rechtsklick auf Wasser zum Aufnehmen"));
        } else {
            meta.setLore(List.of("§7PowerTools", "§8Spezialfunktion folgt in einem Update"));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onBucketUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack held = event.getItem();
        if (held == null || held.getType() != Material.BUCKET || !isPowerBucket(held)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemMeta meta = held.getItemMeta();
        int uses = meta.getPersistentDataContainer().getOrDefault(bucketUsesKey, PersistentDataType.INTEGER, 21);
        boolean filled = meta.getPersistentDataContainer().getOrDefault(bucketFilledKey, PersistentDataType.BYTE, (byte) 0) == 1;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        if (!filled) {
            if (uses <= 0) { player.sendMessage("§cDein Blitz Bucket ist leer."); return; }
            if (clicked.getType() != Material.WATER) return;
            clicked.setType(Material.AIR);
            meta.getPersistentDataContainer().set(bucketFilledKey, PersistentDataType.BYTE, (byte) 1);
            meta.setLore(List.of("§7Wasser-Ladungen: §b" + uses + "/21", "§8Gefüllt – Rechtsklick zum Platzieren"));
        } else {
            Block place = clicked.getRelative(event.getBlockFace());
            if (!place.getType().isAir()) return;
            place.setType(Material.WATER);
            uses--;
            meta.getPersistentDataContainer().set(bucketUsesKey, PersistentDataType.INTEGER, uses);
            meta.getPersistentDataContainer().set(bucketFilledKey, PersistentDataType.BYTE, (byte) 0);
            meta.setLore(List.of("§7Wasser-Ladungen: §b" + uses + "/21", "§8Rechtsklick auf Wasser zum Aufnehmen"));
        }
        held.setItemMeta(meta);
        player.getInventory().setItemInMainHand(held);
        player.updateInventory();
    }

    private boolean isPowerBucket(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        return "blitz_bucket".equals(item.getItemMeta().getPersistentDataContainer().get(itemTypeKey, PersistentDataType.STRING));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return starts(List.of("items", "help"), args[0]);
        if (args.length == 2) return starts(List.of("give"), args[1]);
        if (args.length == 3) return starts(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        if (args.length == 4) return starts(List.of("axe", "drill", "cellx", "bucket"), args[3]);
        return List.of();
    }
    private List<String> starts(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) result.add(value);
        return result;
    }
}
