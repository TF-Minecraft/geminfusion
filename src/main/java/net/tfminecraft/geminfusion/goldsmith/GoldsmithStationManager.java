package net.tfminecraft.geminfusion.goldsmith;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.geminfusion.Permissions;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.utils.IntCounter;

public class GoldsmithStationManager implements Listener {

	private static final long CLICK_COOLDOWN_MS = 200L;

	private final HashMap<Location, GoldsmithStation> stations = new HashMap<>();
	private final HashMap<UUID, Long> clickCooldown = new HashMap<>();
	private final HashMap<UUID, GoldsmithStation> openMenu = new HashMap<>();
	private final GoldsmithInventoryManager menus = new GoldsmithInventoryManager();
	private boolean dirty;

	public GoldsmithStation get(Location loc) {
		Location key = key(loc);
		if (key == null) return null;
		return stations.get(key);
	}

	public GoldsmithStation getOrCreate(Location loc) {
		Location key = key(loc);
		if (key == null) return null;
		GoldsmithStation station = stations.get(key);
		if (station == null) {
			station = new GoldsmithStation(key);
			stations.put(key, station);
		}
		return station;
	}

	public GoldsmithStation remove(Location loc) {
		Location key = key(loc);
		if (key == null) return null;
		GoldsmithStation removed = stations.remove(key);
		if (removed != null) {
			openMenu.entrySet().removeIf(e -> e.getValue() == removed);
			GoldsmithStationStore.delete(key);
			markDirty();
		}
		return removed;
	}

	public void put(GoldsmithStation station) {
		if (station == null || station.getLoc() == null) return;
		stations.put(key(station.getLoc()), station);
	}

	public Collection<GoldsmithStation> getStations() {
		return stations.values();
	}

	public void clear() {
		stations.clear();
		clickCooldown.clear();
		openMenu.clear();
		dirty = false;
	}

	public void markDirty() {
		dirty = true;
	}

	/** Restores tables from disk into this manager. Call after configs are loaded. */
	public void loadPersisted() {
		for (GoldsmithStation station : GoldsmithStationStore.loadAll()) {
			put(station);
		}
		dirty = false;
	}

	/**
	 * Writes live in-progress tables and drops leftover files.
	 * @param force write even when nothing changed
	 */
	public void flush(boolean force) {
		if (!force && !dirty) return;
		GoldsmithStationStore.saveAll(stations.values());
		dirty = false;
	}

	public boolean isGoldsmithStation(Block block) {
		if (block == null || GoldsmithCache.station == null) return false;
		return TLibs.getBlockAPI().getChecker().checkBlock(block, GoldsmithCache.station);
	}

	public static Location key(Location loc) {
		if (loc == null) return null;
		if (loc.getWorld() == null) {
			return new Location(null, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
		}
		return loc.getBlock().getLocation();
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onInteract(PlayerInteractEvent e) {
		if (e.getClickedBlock() == null) return;
		Action action = e.getAction();
		if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;
		if (!isGoldsmithStation(e.getClickedBlock())) return;

		Player player = e.getPlayer();
		if (action == Action.RIGHT_CLICK_BLOCK) {
			e.setCancelled(true);
			if (!Permissions.canUseGoldsmith(player)) {
				if (!onCooldown(player)) {
					player.sendMessage(Permissions.NOT_SKILLED_GOLDSMITH);
					markCooldown(player);
				}
				return;
			}
			if (onCooldown(player)) return;
			handleRightClick(e);
			return;
		}

		if (!isStationTool(player.getInventory().getItemInMainHand())) {
			return;
		}
		e.setCancelled(true);
		if (!Permissions.canUseGoldsmith(player)) {
			if (!onCooldown(player)) {
				player.sendMessage(Permissions.NOT_SKILLED_GOLDSMITH);
				markCooldown(player);
			}
			return;
		}
		if (onCooldown(player)) return;
		handleLeftClick(e);
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	@EventHandler
	public void onMenuClick(InventoryClickEvent e) {
		if (!e.getView().getTitle().equals(GoldsmithInventoryManager.TITLE)) return;
		e.setCancelled(true);
		if (!(e.getWhoClicked() instanceof Player p)) return;
		if (!Permissions.canUseGoldsmith(p)) {
			p.sendMessage(Permissions.NOT_SKILLED_GOLDSMITH);
			p.closeInventory();
			return;
		}
		ItemStack clicked = e.getCurrentItem();
		if (clicked == null || !clicked.hasItemMeta()) return;
		ItemMeta meta = clicked.getItemMeta();
		String projectId = meta.getPersistentDataContainer().get(GoldsmithInventoryManager.projectKey(), PersistentDataType.STRING);
		if (projectId == null) return;

		JewelryProject project = JewelryProjectLoader.getByString(projectId);
		if (project == null) return;

		GoldsmithStation station = openMenu.get(p.getUniqueId());
		if (station == null) {
			p.sendMessage("§cThat bench is no longer available.");
			p.closeInventory();
			return;
		}
		if (station.hasProject()) {
			p.sendMessage("§cThis bench already has a project. SHIFT + LEFT CLICK with the branding tool to cancel first.");
			p.closeInventory();
			return;
		}
		station.setProject(project);
		markDirty();
		openMenu.remove(p.getUniqueId());
		p.closeInventory();
		p.sendMessage("§aSelected " + project.getName() + " §aas the current goldsmithing project");
		p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.8f, 2f);
	}

	@EventHandler(ignoreCancelled = true)
	public void onBreak(BlockBreakEvent e) {
		if (!isGoldsmithStation(e.getBlock())) return;
		Location loc = e.getBlock().getLocation();
		GoldsmithStation station = get(loc);
		if (station == null) return;
		List<ItemStack> refund = station.hasProject() ? station.cancel() : List.of();
		remove(loc);
		Player breaker = e.getPlayer();
		if (breaker != null) {
			giveOrDrop(breaker, refund);
		} else {
			dropAt(loc, refund);
		}
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private void handleRightClick(PlayerInteractEvent e) {
		Player p = e.getPlayer();
		ItemStack hand = p.getInventory().getItemInMainHand();
		GoldsmithStation existing = get(e.getClickedBlock().getLocation());

		if (existing == null || !existing.hasProject()) {
			markCooldown(p);
			GoldsmithStation station = getOrCreate(e.getClickedBlock().getLocation());
			openMenu.put(p.getUniqueId(), station);
			int projectCount = menus.openMenu(p);
			if (projectCount == 0) {
				p.sendMessage("§cNo goldsmithing projects are available. Contact staff.");
			}
			return;
		}

		// Mid-project, right-click and sneak right-click with the branding tool both show the progress.
		if (isBranding(hand)) {
			markCooldown(p);
			sendStatus(p, existing);
			return;
		}

		if (existing.getProject().requiresGem() && InfusedGemValidator.isGemstoneCandidate(hand)) {
			markCooldown(p);
			GoldsmithFeedback feedback = existing.addGem(hand);
			switch (feedback) {
				case SUCCESS:
					consumeOne(p);
					markDirty();
					p.sendTitle("§aAdded gem", "§7Gem §e1/1", 5, 20, 5);
					playWorkFx(existing.getLoc(), Material.GOLD_BLOCK);
					p.getWorld().playSound(existing.getLoc(), Sound.ITEM_AXE_WAX_OFF, 0.7f, 2f);
					break;
				case CAPACITY:
					p.sendMessage("§cThis bench already has a gem");
					p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
					break;
				case NOT_INFUSED:
					p.sendMessage("§cYou need an infused gem for jewelry.");
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					break;
				case WRONG_TYPE:
					p.sendMessage("§cThis project does not need a gem");
					p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
					break;
				case NO_PROJECT:
					p.sendMessage("§cThis bench has no project. Right-click the table to choose one.");
					break;
				default:
					break;
			}
			return;
		}

		GoldsmithMaterial material = matchMaterial(hand);
		if (material == null) return;

		markCooldown(p);
		GoldsmithFeedback feedback = existing.addMaterial(material, hand);
		switch (feedback) {
			case SUCCESS:
				consumeOne(p);
				markDirty();
				IntCounter bucket = existing.getTypes().get(material.getType());
				String progress = bucket == null ? "" : bucket.getCurrent() + "/" + bucket.getNeeded();
				p.sendTitle("§aAdded " + material.getName(), GoldsmithMaterialTypeLoader.display(material.getType()) + " §e" + progress, 5, 20, 5);
				playWorkFx(existing.getLoc(), Material.GOLD_BLOCK);
				p.getWorld().playSound(existing.getLoc(), Sound.ITEM_AXE_WAX_OFF, 0.7f, 2f);
				break;
			case CAPACITY:
				p.sendMessage("§cThe piece already has enough of that material");
				p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
				break;
			case WRONG_TYPE:
				p.sendMessage("§cThis piece does not call for that material");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				break;
			case NO_PROJECT:
				p.sendMessage("§cThis bench has no project. Right-click the table to choose one.");
				break;
			default:
				break;
		}
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private void handleLeftClick(PlayerInteractEvent e) {
		Player p = e.getPlayer();
		GoldsmithStation station = get(e.getClickedBlock().getLocation());
		if (station == null || !station.hasProject()) {
			p.sendMessage("§7Right-click the bench to choose a project.");
			markCooldown(p);
			return;
		}

		ItemStack hand = p.getInventory().getItemInMainHand();
		if (isBranding(hand)) {
			markCooldown(p);
			if (p.isSneaking()) {
				List<ItemStack> refund = station.cancel();
				remove(station.getLoc());
				giveOrDrop(p, refund);
				p.sendMessage("§cProject cancelled");
				p.getWorld().playSound(station.getLoc(), Sound.ITEM_SHIELD_BREAK, 0.4f, 1f);
				return;
			}
			GoldsmithFeedback finish = station.canFinish();
			if (finish == GoldsmithFeedback.LACKING_ITEMS) {
				p.sendMessage(lackingItemsMessage(station, "finishing"));
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
			if (finish == GoldsmithFeedback.RUINED) {
				ruinCraft(p, station);
				return;
			}
			if (finish == GoldsmithFeedback.NOT_INFUSED) {
				p.sendMessage("§cYou need an infused gem for jewelry.");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				return;
			}
			completeCraft(p, station);
			return;
		}

		GoldsmithHit hit = GoldsmithHitLoader.getByItem(hand);

		markCooldown(p);
		GoldsmithFeedback feedback = station.hit(hit);
		switch (feedback) {
			case SUCCESS:
				p.sendTitle("§7Hits §e" + station.getTotalHitCount(), "", 5, 20, 5);
				playWorkFx(station.getLoc(), Material.GOLD_BLOCK);
				p.getWorld().playSound(station.getLoc(), Sound.BLOCK_ANVIL_USE, 0.4f, 1f);
				markDirty();
				break;
			case LACKING_ITEMS:
				p.sendMessage(lackingItemsMessage(station, "working"));
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				break;
			case WRONG_TYPE:
				p.sendMessage("§cYou cannot work the piece with that tool");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
				break;
			default:
				break;
		}
	}

	private void sendStatus(Player p, GoldsmithStation station) {
		p.sendMessage("§7Project: " + station.getProject().getName());
		for (Map.Entry<String, IntCounter> e : station.getTypes().entrySet()) {
			IntCounter c = e.getValue();
			p.sendMessage(GoldsmithMaterialTypeLoader.display(e.getKey()) + "§7: §e" + c.getCurrent() + "/" + c.getNeeded());
		}
		if (station.getProject().requiresGem()) {
			p.sendMessage("§bGem§7: §e" + (station.hasGem() ? "1/1" : "0/1"));
		}
		sendHitsDone(p, station);
		// Players have to find the mix and hits themselves, so only the finished piece shows its percents.
		p.sendMessage("§7Left-click branding to finish");
		p.sendMessage("§cSHIFT + LEFT CLICK with the branding tool to cancel the project!");
	}

	/** Lists every goldsmith tool with the hits done so far. Needed counts stay hidden, so this spoils nothing. */
	private void sendHitsDone(Player p, GoldsmithStation station) {
		// By id, so counts survive a reload that rebuilds the hit objects.
		Map<String, Integer> done = new HashMap<>();
		for (Map.Entry<GoldsmithHit, IntCounter> e : station.getHits().entrySet()) {
			done.merge(e.getKey().getId(), e.getValue().getCurrent(), Integer::sum);
		}
		p.sendMessage("§7Hits done:");
		for (GoldsmithHit hit : GoldsmithHitLoader.get().values()) {
			p.sendMessage(hit.getName() + "§7: §e" + done.getOrDefault(hit.getId(), 0));
		}
		p.sendMessage("§7Total: §e" + station.getTotalHitCount());
	}

	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private void completeCraft(Player p, GoldsmithStation station) {
		JewelryCraftResult result = JewelryOutput.build(station, p);
		if (result == null) {
			p.sendMessage("§cCould not create that item. Contact an administrator.");
			p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}
		Location drop = station.getLoc().clone().add(0.5, 1, 0.5);
		drop.getWorld().dropItemNaturally(drop, result.getItem());
		drop.getWorld().playSound(drop, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
		drop.getWorld().playSound(drop, Sound.BLOCK_ANVIL_PLACE, 1f, 1f);
		p.sendTitle("§aYou made a " + station.getProject().getName(), "", 5, 40, 10);
		p.sendMessage("§7Recipe: §e" + Math.round(result.getRecipePercent()) + "%");
		p.sendMessage("§7Hits: §e" + Math.round(result.getHitPercent()) + "%");
		p.sendMessage("§7Total: §e" + Math.round(result.getFinishedTotal()) + "%");
		if (result.getCraftRoll() != null) {
			p.sendMessage("§7Craft roll: " + result.getCraftRoll().describe());
		}
		if (result.getQuality() != null) {
			p.sendMessage("§7Quality: " + result.getQuality().getName());
		}
		if (station.getProject().requiresGem()) {
			p.sendMessage("§7Stat carry: §e" + Math.round(result.getStatCarryPercent()) + "%");
		}
		station.cancel();
		remove(station.getLoc());
	}

	/** A gem-free piece short of a perfect mix or hits is ruined: the gold is lost and only its percents are shown. */
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	private void ruinCraft(Player p, GoldsmithStation station) {
		double recipe = station.getRecipePercent();
		double hits = station.getHitPercent();
		int needed = station.getTotalHitNeeded();
		int total = station.getTotalHitCount();
		// Hits with tools the piece does not need leave the percent at 100, so count them against it.
		if (total > needed) hits = Math.min(hits, Math.floor(100.0 * needed / total));
		p.sendTitle("§cThe piece was ruined", "§7" + station.getProject().getName() + " §7needs the exact gold mix and hits", 5, 40, 10);
		p.sendMessage("§7Recipe: §e" + Math.round(recipe) + "%");
		p.sendMessage("§7Hits: §e" + Math.round(hits) + "%");
		p.sendMessage("§7Total: §e" + Math.round(Math.min(recipe, hits)) + "%");
		p.getWorld().playSound(station.getLoc(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
		station.cancel();
		remove(station.getLoc());
	}

	private static String lackingItemsMessage(GoldsmithStation station, String action) {
		String gem = station.getProject().requiresGem() ? " and the gem" : "";
		return "§cYou have to add all the gold" + gem + " before " + action;
	}

	private boolean isBranding(ItemStack item) {
		return TLibs.getItemAPI().getChecker().checkItemWithPath(item, GoldsmithCache.brandingTool);
	}

	private boolean isStationTool(ItemStack item) {
		if (item == null || item.getType().isAir()) return false;
		return isBranding(item) || GoldsmithHitLoader.getByItem(item) != null;
	}

	private GoldsmithMaterial matchMaterial(ItemStack item) {
		if (item == null || item.getType().isAir()) return null;
		for (GoldsmithMaterial material : GoldsmithMaterialLoader.get().values()) {
			if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, material.getPath())) {
				return material;
			}
		}
		return null;
	}

	private void consumeOne(Player p) {
		ItemStack hand = p.getInventory().getItemInMainHand();
		hand.setAmount(hand.getAmount() - 1);
	}

	private void giveOrDrop(Player p, List<ItemStack> items) {
		for (ItemStack item : items) {
			HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(item);
			for (ItemStack extra : leftover.values()) {
				p.getWorld().dropItemNaturally(p.getLocation(), extra);
			}
		}
	}

	private void dropAt(Location loc, List<ItemStack> items) {
		Location drop = loc.clone().add(0.5, 1, 0.5);
		for (ItemStack item : items) {
			loc.getWorld().dropItemNaturally(drop, item);
		}
	}

	private void playWorkFx(Location loc, Material dust) {
		loc.getWorld().spawnParticle(
				Particle.BLOCK,
				loc.clone().add(0.5, 1, 0.5),
				20, 0.1, 0.2, 0.1,
				dust.createBlockData());
	}

	private boolean onCooldown(Player p) {
		Long until = clickCooldown.get(p.getUniqueId());
		return until != null && System.currentTimeMillis() < until;
	}

	private void markCooldown(Player p) {
		clickCooldown.put(p.getUniqueId(), System.currentTimeMillis() + CLICK_COOLDOWN_MS);
	}
}
