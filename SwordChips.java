package nl.swordchips;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SwordChips extends JavaPlugin implements Listener, CommandExecutor {
    private NamespacedKey swordKey, chipsKey, chipKey;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private static final int[] GUI_SLOTS = {11, 13, 15, 22};
    private static final String GUI_TITLE = ChatColor.DARK_GRAY + "Sword Chips " + ChatColor.GRAY + "• " + ChatColor.AQUA + "4 slots";

    enum Chip {
        FIRE("fire", "Fire Chip", Material.BLAZE_POWDER, ChatColor.RED, "Kans op extra vuurschade", "Vuur-effect bij een hit"),
        FROST("frost", "Frost Chip", Material.PRISMARINE_SHARD, ChatColor.AQUA, "Kans op korte slow", "Vertraagt je tegenstander"),
        LIGHTNING("lightning", "Lightning Chip", Material.GLOWSTONE_DUST, ChatColor.YELLOW, "Kans op bliksemschade", "Extra damage met bliksem-effect"),
        LIFESTEAL("lifesteal", "Lifesteal Chip", Material.GHAST_TEAR, ChatColor.LIGHT_PURPLE, "Herstelt een beetje HP", "Kleine heal bij een hit"),
        RESISTANCE("resistance", "Resistance Chip", Material.IRON_INGOT, ChatColor.GREEN, "Kans op minder schade", "Verdedigende kans terwijl je zwaard vast hebt"),
        SHARPNESS("sharpness", "Sharpness Chip", Material.IRON_SWORD, ChatColor.DARK_AQUA, "Kleine damagebonus", "Constante, beperkte damagebonus"),
        SWIFTNESS("swiftness", "Swiftness Chip", Material.RABBIT_FOOT, ChatColor.BLUE, "Speedboost na een kill", "Korte speedboost na een kill"),
        POISON("poison", "Poison Chip", Material.SPIDER_EYE, ChatColor.DARK_GREEN, "Kans op korte poison", "Korte poison, geen lange lock"),
        IMPACT("impact", "Impact Chip", Material.FIREWORK_STAR, ChatColor.GOLD, "Kans op extra knockback", "Kleine knockbackbonus"),
        CRITICAL("critical", "Critical Chip", Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE, "Kans op critical damage", "Een kans op een extra damage-hit");

        final String id, display, description, detail;
        final Material material;
        final ChatColor color;
        Chip(String id, String display, Material material, ChatColor color, String description, String detail) {
            this.id=id; this.display=display; this.material=material; this.color=color; this.description=description; this.detail=detail;
        }
        static Chip byId(String id) {
            for (Chip c : values()) if (c.id.equalsIgnoreCase(id)) return c;
            return null;
        }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        swordKey = new NamespacedKey(this, "swordchips_sword");
        chipsKey = new NamespacedKey(this, "installed_chips");
        chipKey = new NamespacedKey(this, "chip_type");
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("swordchips")).setExecutor(this);
        getLogger().info("SwordChips enabled for Paper 1.21.11.");
    }

    private ItemStack makeSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Cyber Sword");
        meta.setLore(List.of(ChatColor.GRAY + "PvP upgrade weapon", ChatColor.DARK_GRAY + "Right-click to manage chips",
                ChatColor.AQUA + "Installed chips: " + ChatColor.WHITE + "0/4"));
        meta.getPersistentDataContainer().set(swordKey, PersistentDataType.BYTE, (byte)1);
        meta.getPersistentDataContainer().set(chipsKey, PersistentDataType.STRING, "");
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeChip(Chip chip) {
        ItemStack item = new ItemStack(chip.material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(chip.color + "" + ChatColor.BOLD + chip.display);
        meta.setLore(List.of(ChatColor.GRAY + "Computer upgrade chip",
                ChatColor.DARK_GRAY + "Type: " + chip.id,
                ChatColor.WHITE + chip.description,
                ChatColor.GRAY + chip.detail,
                ChatColor.YELLOW + "Right-click a Cyber Sword GUI slot to install"));
        meta.getPersistentDataContainer().set(chipKey, PersistentDataType.STRING, chip.id);
        // Item model key is supplied by the optional resource pack.
        meta.setItemModel(new NamespacedKey("swordchips", "chip_" + chip.id));
        item.setItemMeta(meta);
        return item;
    }

    private boolean isSword(ItemStack item) {
        return item != null && item.getType() == Material.NETHERITE_SWORD && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(swordKey, PersistentDataType.BYTE);
    }
    private Chip getChip(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(chipKey, PersistentDataType.STRING);
        return id == null ? null : Chip.byId(id);
    }
    private List<Chip> getInstalled(ItemStack sword) {
        if (!isSword(sword)) return new ArrayList<>();
        String raw = sword.getItemMeta().getPersistentDataContainer().getOrDefault(chipsKey, PersistentDataType.STRING, "");
        List<Chip> result = new ArrayList<>();
        if (!raw.isBlank()) for (String id : raw.split(",")) {
            Chip chip = Chip.byId(id);
            if (chip != null && !result.contains(chip)) result.add(chip);
        }
        return result;
    }
    private void saveInstalled(ItemStack sword, List<Chip> chips) {
        ItemMeta meta = sword.getItemMeta();
        meta.getPersistentDataContainer().set(chipsKey, PersistentDataType.STRING,
                String.join(",", chips.stream().map(c -> c.id).toList()));
        List<String> lore = new ArrayList<>(List.of(ChatColor.GRAY + "PvP upgrade weapon",
                ChatColor.DARK_GRAY + "Right-click to manage chips",
                ChatColor.AQUA + "Installed chips: " + ChatColor.WHITE + chips.size() + "/4"));
        for (Chip c : chips) lore.add(c.color + "• " + c.display);
        meta.setLore(lore);
        sword.setItemMeta(meta);
    }

    private void openMenu(Player player) {
        ItemStack sword = player.getInventory().getItemInMainHand();
        if (!isSword(sword)) return;
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta(); fm.setDisplayName(" "); filler.setItemMeta(fm);
        for (int i=0;i<27;i++) inv.setItem(i, filler);
        ItemStack info = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta im = info.getItemMeta(); im.setDisplayName(ChatColor.LIGHT_PURPLE + "Cyber Sword");
        im.setLore(List.of(ChatColor.GRAY + "Klik op een chip-slot met een chip op je cursor.",
                ChatColor.GRAY + "Klik op een gevulde slot om de chip terug te krijgen.",
                ChatColor.AQUA + "Maximaal 4 chips per zwaard."));
        info.setItemMeta(im); inv.setItem(4, info);
        List<Chip> installed = getInstalled(sword);
        for (int i=0;i<4;i++) {
            if (i < installed.size()) inv.setItem(GUI_SLOTS[i], makeChip(installed.get(i)));
            else {
                ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta em=empty.getItemMeta(); em.setDisplayName(ChatColor.GRAY + "Lege chip-slot"); em.setLore(List.of(ChatColor.DARK_GRAY+"Plaats hier een computerchip")); empty.setItemMeta(em);
                inv.setItem(GUI_SLOTS[i], empty);
            }
        }
        player.openInventory(inv);
    }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            if (isSword(event.getPlayer().getInventory().getItemInMainHand())) {
                event.setCancelled(true);
                openMenu(event.getPlayer());
            }
        }
    }

    @EventHandler public void onMenuClick(InventoryClickEvent event) {
        if (!GUI_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        int slotIndex = -1;
        for (int i=0;i<GUI_SLOTS.length;i++) if (GUI_SLOTS[i] == raw) slotIndex=i;
        if (slotIndex < 0) return;
        ItemStack sword = player.getInventory().getItemInMainHand();
        if (!isSword(sword)) { player.closeInventory(); player.sendMessage(ChatColor.RED+"Houd je Cyber Sword in je hoofdhand."); return; }
        List<Chip> installed = getInstalled(sword);
        ItemStack cursor = event.getCursor();
        if (slotIndex < installed.size()) {
            Chip removed = installed.remove(slotIndex);
            saveInstalled(sword, installed);
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(makeChip(removed));
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            openMenu(player);
            return;
        }
        Chip chip = getChip(cursor);
        if (chip == null) { player.sendMessage(ChatColor.YELLOW+"Pak eerst een computerchip op je cursor."); return; }
        if (installed.size() >= 4) { player.sendMessage(ChatColor.RED+"Dit zwaard heeft al 4 chips."); return; }
        if (installed.contains(chip)) { player.sendMessage(ChatColor.RED+"Deze chip zit al in dit zwaard."); return; }
        installed.add(chip);
        saveInstalled(sword, installed);
        if (cursor.getAmount() <= 1) event.setCursor(null); else cursor.setAmount(cursor.getAmount()-1);
        openMenu(player);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
    }

    @EventHandler public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && has(victim, Chip.RESISTANCE)
                && roll(Chip.RESISTANCE) && ready(victim, Chip.RESISTANCE)) {
            event.setDamage(event.getDamage() * (1.0 - getConfig().getDouble("effects.resistance-reduction-percent",20)/100.0));
            mark(victim, Chip.RESISTANCE);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0,1,0), 8, .3,.4,.3,.1);
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker=p;
        else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker=p;
        if (attacker == null || !(event.getEntity() instanceof LivingEntity target)) return;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!isSword(weapon)) return;
        List<Chip> chips = getInstalled(weapon);
        for (Chip c : chips) {
            if (!ready(attacker,c) || !roll(c)) continue;
            switch (c) {
                case FIRE -> { target.setFireTicks(Math.max(target.getFireTicks(), getConfig().getInt("effects.fire-seconds",2)*20)); mark(attacker,c); }
                case FROST -> { target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, getConfig().getInt("effects.frost-seconds",2)*20, 0)); mark(attacker,c); }
                case LIGHTNING -> { event.setDamage(event.getDamage()+getConfig().getDouble("effects.lightning-bonus-damage",1.5)); target.getWorld().strikeLightningEffect(target.getLocation()); mark(attacker,c); }
                case LIFESTEAL -> { double max=attacker.getAttribute(Attribute.MAX_HEALTH).getValue(); attacker.setHealth(Math.min(max, attacker.getHealth()+getConfig().getDouble("effects.lifesteal-hearts",0.5)*2)); mark(attacker,c); }
                case SHARPNESS -> event.setDamage(event.getDamage()+getConfig().getDouble("effects.sharpness-bonus-damage",1.0));
                case POISON -> { target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, getConfig().getInt("effects.poison-seconds",2)*20, 0)); mark(attacker,c); }
                case IMPACT -> { Vector v=target.getVelocity(); v.add(attacker.getLocation().getDirection().setY(0).normalize().multiply(getConfig().getDouble("effects.impact-horizontal",.55)).setY(.12)); target.setVelocity(v); mark(attacker,c); }
                case CRITICAL -> { event.setDamage(event.getDamage()+getConfig().getDouble("effects.critical-bonus-damage",2.0)); target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0,1,0), 12, .3,.4,.3,.15); mark(attacker,c); }
                default -> {}
            }
        }
    }

    @EventHandler public void onKill(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player killer=event.getEntity().getKiller();
        if (killer==null || !isSword(killer.getInventory().getItemInMainHand())) return;
        if (getInstalled(killer.getInventory().getItemInMainHand()).contains(Chip.SWIFTNESS)) {
            killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, getConfig().getInt("effects.swiftness-seconds",3)*20,
                    getConfig().getInt("effects.swiftness-amplifier",0)));
        }
    }

    @EventHandler public void onMenuClose(InventoryCloseEvent event) {
        // Sword and chip data are saved immediately to the held sword; nothing is lost on close.
    }

    private boolean has(Player p, Chip c) { return isSword(p.getInventory().getItemInMainHand()) && getInstalled(p.getInventory().getItemInMainHand()).contains(c); }
    private boolean roll(Chip c) { return random.nextDouble()*100 < getConfig().getDouble("chances."+c.id, 10); }
    private long cooldown(Chip c) { return (long)(getConfig().getDouble("cooldowns-seconds."+c.id, 5)*1000); }
    private boolean ready(Player p, Chip c) { return System.currentTimeMillis() >= cooldowns.getOrDefault(UUID.nameUUIDFromBytes((p.getUniqueId()+":"+c.id).getBytes()),0L); }
    private void mark(Player p, Chip c) { cooldowns.put(UUID.nameUUIDFromBytes((p.getUniqueId()+":"+c.id).getBytes()), System.currentTimeMillis()+cooldown(c)); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length==0) { sender.sendMessage(ChatColor.AQUA+"/swordchips give [speler]"); sender.sendMessage(ChatColor.AQUA+"/swordchips chip <type> [speler]"); sender.sendMessage(ChatColor.AQUA+"/swordchips reload"); return true; }
        if (!sender.hasPermission("swordchips.admin")) { sender.sendMessage(ChatColor.RED+"Geen permissie."); return true; }
        if (args[0].equalsIgnoreCase("reload")) { reloadConfig(); sender.sendMessage(ChatColor.GREEN+"SwordChips config herladen."); return true; }
        Player target = sender instanceof Player p ? p : null;
        if (args.length>=2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("chip")) && args.length>=3) target=Bukkit.getPlayerExact(args[args.length-1]);
        if (target==null) { sender.sendMessage(ChatColor.RED+"Speler niet gevonden. Gebruik een online spelernaam."); return true; }
        if (args[0].equalsIgnoreCase("give")) {
            target.getInventory().addItem(makeSword()).values().forEach(i->target.getWorld().dropItemNaturally(target.getLocation(),i));
            sender.sendMessage(ChatColor.GREEN+"Cyber Sword gegeven aan "+target.getName()+"."); return true;
        }
        if (args[0].equalsIgnoreCase("chip") && args.length>=2) {
            String id=args[1];
            if (args.length>=3) target=Bukkit.getPlayerExact(args[2]);
            Chip chip=Chip.byId(id);
            if (chip==null || target==null) { sender.sendMessage(ChatColor.RED+"Gebruik: /swordchips chip <fire|frost|lightning|lifesteal|resistance|sharpness|swiftness|poison|impact|critical> [speler]"); return true; }
            target.getInventory().addItem(makeChip(chip)).values().forEach(i->target.getWorld().dropItemNaturally(target.getLocation(),i));
            sender.sendMessage(ChatColor.GREEN+chip.display+" gegeven aan "+target.getName()+"."); return true;
        }
        sender.sendMessage(ChatColor.RED+"Onbekend commando."); return true;
    }
}
