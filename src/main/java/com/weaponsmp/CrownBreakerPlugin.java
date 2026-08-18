package com.weaponsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;

public class CrownBreakerPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private NamespacedKey recipeKey;
    private NamespacedKey crownKey;
    private boolean recipeEnabled = false;
    private boolean crownBreakerExists = false;

    private final Map<UUID, Integer> killTracker = new HashMap<>();
    private final Map<UUID, Integer> targetKillsForUlt = new HashMap<>();
    private final Map<UUID, Boolean> ultReady = new HashMap<>();
    private final Map<UUID, Integer> ultHitsLeft = new HashMap<>();
    private final Set<UUID> bypassTotemSet = new HashSet<>();

    @Override
    public void onEnable() {
        this.recipeKey = new NamespacedKey(this, "crownbreaker_recipe");
        this.crownKey = new NamespacedKey(this, "is_crownbreaker");

        Objects.requireNonNull(this.getCommand("weaponsmp")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("WeaponSMP CrownBreaker Plugin Enabled Successfully!");
    }

    @Override
    public void onDisable() {
        Bukkit.removeRecipe(recipeKey);
    }

    public ItemStack createCrownBreaker() {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();

        meta.displayName(Component.text("CrownBreaker", NamedTextColor.GOLD, TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("The One and Only CrownBreaker", NamedTextColor.DARK_RED, TextDecoration.ITALIC));
        lore.add(Component.text("Ultimate Smash & Soul Reaper Power", NamedTextColor.GRAY));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(crownKey, PersistentDataType.BYTE, (byte) 1);

        meta.addEnchant(Enchantment.WIND_BURST, 3, true);
        meta.addEnchant(Enchantment.DENSITY, 5, true);
        meta.addEnchant(Enchantment.BREACH, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);

        mace.setItemMeta(meta);
        return mace;
    }

    public boolean isCrownBreaker(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte b = item.getItemMeta().getPersistentDataContainer().get(crownKey, PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    public void enableRecipe() {
        if (recipeEnabled) return;
        Bukkit.removeRecipe(recipeKey);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createCrownBreaker());
        recipe.shape("NBN", "CMC", "SPS");

        recipe.setIngredient('N', Material.NETHERITE_BLOCK);
        recipe.setIngredient('B', Material.HEAVY_CORE);
        recipe.setIngredient('C', Material.BEACON);
        recipe.setIngredient('M', Material.MACE);
        recipe.setIngredient('P', Material.BREEZE_ROD);

        ItemStack strPotion = new ItemStack(Material.POTION);
        PotionMeta pMeta = (PotionMeta) strPotion.getItemMeta();
        pMeta.setBasePotionType(PotionType.STRONG_STRENGTH);
        strPotion.setItemMeta(pMeta);

        recipe.setIngredient('S', strPotion.getType());

        Bukkit.addRecipe(recipe);
        recipeEnabled = true;
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (isCrownBreaker(event.getRecipe().getResult())) {
            if (crownBreakerExists) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(Component.text("CrownBreaker pehle se exist karti hai!", NamedTextColor.RED));
                return;
            }
            crownBreakerExists = true;
            Bukkit.removeRecipe(recipeKey);
            recipeEnabled = false;
            Bukkit.broadcast(Component.text("CrownBreaker craft ho chuki hai! Recipe Auto-Lock ho gayi.", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        }
    }

    @EventHandler
    public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && isCrownBreaker(killer.getInventory().getItemInMainHand())) {
            UUID id = killer.getUniqueId();
            int currentKills = killTracker.getOrDefault(id, 0) + 1;
            killTracker.put(id, currentKills);

            int target = targetKillsForUlt.computeIfAbsent(id, k -> new Random().nextInt(4) + 5);

            if (currentKills >= target && !ultReady.getOrDefault(id, false)) {
                ultReady.put(id, true);
                triggerUltAnnouncement(killer);
            }
        }
    }

    private void triggerUltAnnouncement(Player player) {
        Component titleComp = Component.text("☠️\nULTIMATE READY", NamedTextColor.RED, TextDecoration.BOLD);
        Component subComp = Component.text("SOUL REAPER\n", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .append(Component.text("Shift + Left Click to unleash death", NamedTextColor.WHITE));

        Title title = Title.title(titleComp, subComp, Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1)));
        player.showTitle(title);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
    }

    @EventHandler
    public void onShiftLeftClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction().name().contains("LEFT_CLICK") && player.isSneaking()) {
            if (isCrownBreaker(player.getInventory().getItemInMainHand())) {
                UUID id = player.getUniqueId();
                if (ultReady.getOrDefault(id, false)) {
                    ultReady.put(id, false);
                    ultHitsLeft.put(id, 4);
                    killTracker.put(id, 0);
                    targetKillsForUlt.put(id, new Random().nextInt(4) + 5);

                    player.sendMessage(Component.text("SOUL REAPER ACTIVE! Agle 4 hits par tabahi!", NamedTextColor.GREEN, TextDecoration.BOLD));
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                }
            }
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) return;

        if (!isCrownBreaker(attacker.getInventory().getItemInMainHand())) return;

        UUID id = attacker.getUniqueId();
        int hitsLeft = ultHitsLeft.getOrDefault(id, 0);

        if (hitsLeft > 0) {
            ultHitsLeft.put(id, hitsLeft - 1);
            event.setDamage(1000.0);

            Location loc = victim.getLocation();
            loc.getWorld().strikeLightningEffect(loc);
            loc.getWorld().createExplosion(loc, 6.0f, false, false);

            attacker.sendMessage(Component.text("SOUL REAPER HIT! (" + (hitsLeft - 1) + " Left)", NamedTextColor.DARK_RED));
            return;
        }

        if (attacker.getFallDistance() > 3.0f) {
            attacker.setFallDistance(0.0f);
            if (new Random().nextInt(100) < 3) {
                bypassTotemSet.add(victim.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onTotemPop(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player victim) {
            if (bypassTotemSet.contains(victim.getUniqueId())) {
                bypassTotemSet.remove(victim.getUniqueId());
                event.setCancelled(true);
                victim.sendMessage(Component.text("CrownBreaker ne aapka Totem Bypass kar diya!", NamedTextColor.DARK_RED));
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().title().equals(Component.text("CrownBreaker Recipe", NamedTextColor.DARK_RED))) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) return true;

        String sub = args[0].toLowerCase();

        if (sub.equals("give") && sender.isOp()) {
            if (args.length < 3) return true;
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null && args[2].equalsIgnoreCase("CrownBreaker")) {
                target.getInventory().addItem(createCrownBreaker());
                crownBreakerExists = true;
                sender.sendMessage(Component.text("CrownBreaker Given!", NamedTextColor.GREEN));
            }
            return true;
        }

        if (sub.equals("enable") && sender.isOp()) {
            enableRecipe();
            Bukkit.broadcast(Component.text("CrownBreaker recipe enabled!", NamedTextColor.GOLD));
            return true;
        }

        if (sub.equals("recipe") && sender instanceof Player player) {
            openRecipeGUI(player);
            return true;
        }

        return true;
    }

    private void openRecipeGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, Component.text("CrownBreaker Recipe", NamedTextColor.DARK_RED));

        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 45; i++) gui.setItem(i, bg);

        gui.setItem(11, new ItemStack(Material.NETHERITE_BLOCK));
        gui.setItem(12, new ItemStack(Material.HEAVY_CORE));
        gui.setItem(13, new ItemStack(Material.NETHERITE_BLOCK));
        gui.setItem(20, new ItemStack(Material.BEACON));
        gui.setItem(21, new ItemStack(Material.MACE));
        gui.setItem(22, new ItemStack(Material.BEACON));

        ItemStack strPotion = new ItemStack(Material.POTION);
        PotionMeta pMeta = (PotionMeta) strPotion.getItemMeta();
        pMeta.setBasePotionType(PotionType.STRONG_STRENGTH);
        strPotion.setItemMeta(pMeta);

        gui.setItem(29, strPotion);
        gui.setItem(30, new ItemStack(Material.BREEZE_ROD));
        gui.setItem(31, strPotion);
        gui.setItem(24, createCrownBreaker());

        player.openInventory(gui);
    }
          }
