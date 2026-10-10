package net.tfminecraft.geminfusion.goldsmith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.geminfusion.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.*;
import net.tfminecraft.tlibs.objects.utils.IntCounter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockito.*;

class GoldsmithStationManagerTest {
  ServerMock server;
  World world;
  Location loc;
  Player player;
  PlayerInventory inventory;
  Block block;
  GoldsmithStationManager manager;
  GoldsmithStation station;
  JewelryProject project;
  ItemStack hand;
  ItemAPI items;
  BlockAPI blocks;
  MockedStatic<TLibs> libs;
  MockedStatic<Permissions> perms;
  MockedStatic<GoldsmithStationStore> store;
  MockedStatic<GoldsmithHitLoader> hits;
  MockedStatic<GoldsmithMaterialLoader> materials;
  MockedStatic<InfusedGemValidator> validator;
  MockedConstruction<GoldsmithInventoryManager> menus;
  InfusionMain previous;
  String oldStation;
  String oldTool;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    world = server.addSimpleWorld("world");
    loc = new Location(world, 1, 2, 3);
    block = world.getBlockAt(loc);
    previous = InfusionMain.plugin;
    InfusionMain.plugin = mock(InfusionMain.class);
    when(InfusionMain.plugin.getName()).thenReturn("GemInfusion");
    when(InfusionMain.plugin.namespace()).thenReturn("geminfusion");
    player = mock(Player.class);
    inventory = mock(PlayerInventory.class);
    hand = new ItemStack(Material.STICK);
    when(player.getInventory()).thenReturn(inventory);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getLocation()).thenReturn(loc);
    when(player.getWorld()).thenReturn(world);
    when(inventory.getItemInMainHand()).thenReturn(hand);
    when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
    items = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    blocks = mock(BlockAPI.class, RETURNS_DEEP_STUBS);
    libs = mockStatic(TLibs.class);
    libs.when(TLibs::getItemAPI).thenReturn(items);
    libs.when(TLibs::getBlockAPI).thenReturn(blocks);
    perms = mockStatic(Permissions.class);
    perms.when(() -> Permissions.canUseGoldsmith(player)).thenReturn(true);
    store = mockStatic(GoldsmithStationStore.class);
    hits = mockStatic(GoldsmithHitLoader.class);
    materials = mockStatic(GoldsmithMaterialLoader.class);
    validator = mockStatic(InfusedGemValidator.class);
    menus = mockConstruction(GoldsmithInventoryManager.class);
    manager = new GoldsmithStationManager();
    station = mock(GoldsmithStation.class);
    project = mock(JewelryProject.class);
    when(station.getLoc()).thenReturn(loc);
    when(station.hasProject()).thenReturn(true);
    when(station.getProject()).thenReturn(project);
    when(project.getName()).thenReturn("Ring");
    oldStation = GoldsmithCache.station;
    oldTool = GoldsmithCache.brandingTool;
    GoldsmithCache.brandingTool = "branding";
    // Reuse configured block representation if set; tests use a controlled checker.
    GoldsmithCache.station = "minecraft.SMITHING_TABLE";
    when(blocks.getChecker().checkBlock(eq(block), any())).thenReturn(true);
  }

  @AfterEach
  void cleanup() {
    menus.close();
    validator.close();
    materials.close();
    hits.close();
    store.close();
    perms.close();
    libs.close();
    GoldsmithCache.station = oldStation;
    GoldsmithCache.brandingTool = oldTool;
    InfusionMain.plugin = previous;
    MockBukkit.unmock();
  }

  PlayerInteractEvent click(Action action) {
    return new PlayerInteractEvent(player, action, hand, block, BlockFace.UP, EquipmentSlot.HAND);
  }

  void reset() {
    manager.clear();
    manager.put(station);
  }

  void branding(boolean value) {
    when(items.getChecker().checkItemWithPath(hand, "branding")).thenReturn(value);
  }

  @Test
  void lookupNormalizesLocationsAndLifecyclePersistsOnlyWhenDirty() {
    assertNull(manager.get(null));
    assertNull(manager.getOrCreate(null));
    assertNull(manager.remove(null));
    assertNull(GoldsmithStationManager.key(null));
    assertEquals(
        new Location(null, -1, 2, 3),
        GoldsmithStationManager.key(new Location(null, -.1, 2.9, 3.9)));
    GoldsmithStation created = manager.getOrCreate(loc.clone().add(.5, .5, .5));
    assertSame(created, manager.get(loc));
    assertSame(created, manager.getOrCreate(loc));
    manager.put(null);
    GoldsmithStation noLocation = mock(GoldsmithStation.class);
    manager.put(noLocation);
    assertEquals(1, manager.getStations().size());
    manager.flush(false);
    store.verifyNoInteractions();
    manager.markDirty();
    manager.flush(false);
    store.verify(() -> GoldsmithStationStore.saveAll(any()), times(1));
    manager.flush(false);
    manager.flush(true);
    store.verify(() -> GoldsmithStationStore.saveAll(any()), times(2));
    assertSame(created, manager.remove(loc));
    assertNull(manager.remove(loc));
    store.verify(() -> GoldsmithStationStore.delete(loc));
    store.when(GoldsmithStationStore::loadAll).thenReturn(List.of(station));
    manager.loadPersisted();
    assertSame(station, manager.get(loc));
    manager.clear();
    assertTrue(manager.getStations().isEmpty());
  }

  @Test
  void irrelevantInteractionsAndMissingStationConfigurationAreIgnored() {
    assertFalse(manager.isGoldsmithStation(null));
    GoldsmithCache.station = null;
    assertFalse(manager.isGoldsmithStation(block));
    manager.onInteract(
        new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, hand, null, BlockFace.UP));
    manager.onInteract(click(Action.PHYSICAL));
    manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
    verify(player, never()).sendMessage(anyString());
    assertTrue(manager.getStations().isEmpty());
  }

  @Test
  void rightClickOpensProjectMenuAndCooldownPreventsDuplicateAction() {
    PlayerInteractEvent click = click(Action.RIGHT_CLICK_BLOCK);
    manager.onInteract(click);
    manager.onInteract(click);
    assertTrue(click.isCancelled());
    assertNotNull(manager.get(loc));
    verify(menus.constructed().getFirst()).openMenu(player);
    verify(player).sendMessage(contains("No goldsmithing projects"));
    manager.clear();
    when(menus.constructed().getFirst().openMenu(player)).thenReturn(2);
    manager.onInteract(click);
    verify(menus.constructed().getFirst(), times(2)).openMenu(player);
  }

  @Test
  void existingEmptyBenchReopensMenuAfterCooldownExpires() throws Exception {
    manager.getOrCreate(loc);
    manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
    Thread.sleep(225);
    manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
    verify(menus.constructed().getFirst(), times(2)).openMenu(player);
  }

  @Test
  void permissionDenialsAreCancelledAndThrottledForBothActions() {
    perms.when(() -> Permissions.canUseGoldsmith(player)).thenReturn(false);
    PlayerInteractEvent right = click(Action.RIGHT_CLICK_BLOCK);
    manager.onInteract(right);
    manager.onInteract(right);
    assertTrue(right.isCancelled());
    verify(player, times(1)).sendMessage(Permissions.NOT_SKILLED_GOLDSMITH);
    manager.clear();
    branding(true);
    PlayerInteractEvent left = click(Action.LEFT_CLICK_BLOCK);
    manager.onInteract(left);
    manager.onInteract(left);
    assertTrue(left.isCancelled());
    verify(player, times(2)).sendMessage(Permissions.NOT_SKILLED_GOLDSMITH);
    manager.clear();
    branding(false);
    manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
    verify(player, times(2)).sendMessage(Permissions.NOT_SKILLED_GOLDSMITH);
  }

  @Test
  void brandingShowsGoldGemAndHitsDoneWithoutSpoilers() {
    manager.put(station);
    branding(true);
    when(project.requiresGem()).thenReturn(true);
    when(station.hasGem()).thenReturn(true);
    IntCounter counter = new IntCounter();
    counter.setNeeded(2);
    counter.setCurrent(1);
    when(station.getTypes()).thenReturn(Map.of("gold", counter));
    manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
    verify(player).sendMessage("§bGem§7: §e1/1");
    verify(player).sendMessage("§7Hits done:");
    verify(player).sendMessage("§7Total: §e0");
    // The mix and needed hits are for the player to work out; status must not reveal them.
    verify(station, never()).getRecipePercent();
    verify(station, never()).getHitPercent();
    verify(station, never()).getTotalHitNeeded();
    verify(station, never()).getDepositedByMaterial();
    verify(player, never()).sendMessage(contains("Recipe"));
    verify(player, never()).sendMessage(contains("SHIFT + RIGHT"));
    reset();
    when(station.hasGem()).thenReturn(false);
    manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
    verify(player).sendMessage("§bGem§7: §e0/1");
    reset();
    when(project.requiresGem()).thenReturn(false);
    manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
    verify(player, times(3)).sendMessage("§7Project: Ring");
  }

  @Test
  void brandingListsHitsDoneForEveryToolWithoutNeededCountsSneakingOrNot() {
    manager.put(station);
    branding(true);
    IntCounter gold = new IntCounter();
    gold.setNeeded(4);
    gold.setCurrent(4);
    when(station.getTypes()).thenReturn(Map.of("gold", gold));
    GoldsmithHit hammer = mock(GoldsmithHit.class), small = mock(GoldsmithHit.class);
    GoldsmithHit tinker = mock(GoldsmithHit.class), stale = mock(GoldsmithHit.class);
    when(hammer.getId()).thenReturn("hit");
    when(hammer.getName()).thenReturn("§7Hit");
    when(small.getId()).thenReturn("small_hit");
    when(small.getName()).thenReturn("§7Small Hit");
    when(tinker.getId()).thenReturn("tinker");
    when(tinker.getName()).thenReturn("§7Tinker");
    // A hit object from before a reload still counts under its id.
    when(stale.getId()).thenReturn("hit");
    LinkedHashMap<String, GoldsmithHit> all = new LinkedHashMap<>();
    all.put("hit", hammer);
    all.put("small_hit", small);
    all.put("tinker", tinker);
    hits.when(GoldsmithHitLoader::get).thenReturn(all);
    IntCounter three = new IntCounter(), two = new IntCounter(), one = new IntCounter();
    three.setCurrent(3);
    three.setNeeded(8);
    two.setCurrent(2);
    one.setCurrent(1);
    Map<GoldsmithHit, IntCounter> done = new LinkedHashMap<>();
    done.put(hammer, three);
    done.put(small, two);
    done.put(stale, one);
    when(station.getHits()).thenReturn(done);
    when(station.getTotalHitCount()).thenReturn(6);
    List<String> expected =
        List.of(
            "§7Project: Ring",
            // Other tests may have loaded the gold type, so take its display name from the loader.
            GoldsmithMaterialTypeLoader.display("gold") + "§7: §e4/4",
            "§7Hits done:",
            "§7Hit§7: §e4",
            "§7Small Hit§7: §e2",
            "§7Tinker§7: §e0",
            "§7Total: §e6",
            "§7Left-click branding to finish",
            "§cSHIFT + LEFT CLICK with the branding tool to cancel the project!");
    // Sneaking or not, a mid-project branding right-click shows the same progress.
    for (boolean sneaking : List.of(false, true)) {
      clearInvocations(player);
      reset();
      when(player.isSneaking()).thenReturn(sneaking);
      manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
      ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
      verify(player, atLeastOnce()).sendMessage(sent.capture());
      assertEquals(expected, sent.getAllValues());
    }
    verify(station, never()).getTotalHitNeeded();
    verify(station, never()).getHitPercent();
    verify(station, never()).getRecipePercent();
  }

  @Test
  void gemDepositsReportEveryFeedbackAndOnlyConsumeOnSuccess() {
    when(project.requiresGem()).thenReturn(true);
    validator.when(() -> InfusedGemValidator.isGemstoneCandidate(hand)).thenReturn(true);
    for (GoldsmithFeedback feedback : GoldsmithFeedback.values()) {
      reset();
      hand.setAmount(3);
      when(station.addGem(hand)).thenReturn(feedback);
      manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
      assertEquals(
          feedback == GoldsmithFeedback.SUCCESS ? 2 : 3, hand.getAmount(), feedback.name());
    }
  }

  @Test
  void materialDepositsResolveMatchingItemsAndConsumeOnlyOnSuccess() {
    GoldsmithMaterial material = mock(GoldsmithMaterial.class);
    when(material.getPath()).thenReturn("gold");
    when(material.getType()).thenReturn("gold");
    when(material.getName()).thenReturn("Gold");
    materials
        .when(GoldsmithMaterialLoader::get)
        .thenReturn(new LinkedHashMap<>(Map.of("gold", material)));
    reset();
    manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
    verify(station, never()).addMaterial(any(), any());
    when(items.getChecker().checkItemWithPath(hand, "gold")).thenReturn(true);
    for (GoldsmithFeedback feedback : GoldsmithFeedback.values()) {
      reset();
      hand.setAmount(3);
      when(station.addMaterial(material, hand)).thenReturn(feedback);
      manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
      assertEquals(
          feedback == GoldsmithFeedback.SUCCESS ? 2 : 3, hand.getAmount(), feedback.name());
    }
    IntCounter counter = new IntCounter();
    counter.setNeeded(2);
    counter.setCurrent(1);
    when(station.getTypes()).thenReturn(Map.of("gold", counter));
    reset();
    when(station.addMaterial(material, hand)).thenReturn(GoldsmithFeedback.SUCCESS);
    manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
    reset();
    when(inventory.getItemInMainHand()).thenReturn(null);
    manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
    when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
    manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
  }

  @Test
  void toolHitsReportProgressAndFeedback() {
    GoldsmithHit hit = mock(GoldsmithHit.class);
    hits.when(() -> GoldsmithHitLoader.getByItem(hand)).thenReturn(hit);
    manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
    verify(player).sendMessage(contains("choose a project"));
    reset();
    when(station.hasProject()).thenReturn(false);
    manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
    when(station.hasProject()).thenReturn(true);
    for (GoldsmithFeedback feedback : GoldsmithFeedback.values()) {
      reset();
      when(station.hit(hit)).thenReturn(feedback);
      manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
    }
    verify(player).sendMessage("§cYou have to add all the gold before working");
    reset();
    when(station.hit(hit)).thenReturn(GoldsmithFeedback.SUCCESS);
    manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
    manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
    verify(station, times(GoldsmithFeedback.values().length + 1)).hit(hit);
    // Working a piece gives no advice beyond the hit count; only the finish shows percents.
    verify(player, never()).sendMessage(contains("worked this piece"));
  }

  @Test
  void brandingCancellationRefundsItemsAndDropsInventoryOverflow() {
    reset();
    branding(true);
    when(player.isSneaking()).thenReturn(true);
    ItemStack refund = new ItemStack(Material.GOLD_INGOT);
    when(station.cancel()).thenReturn(List.of(refund));
    when(inventory.addItem(refund)).thenReturn(new HashMap<>(Map.of(0, refund)));
    manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
    assertNull(manager.get(loc));
    verify(inventory).addItem(refund);
    assertEquals(1, world.getEntitiesByClass(org.bukkit.entity.Item.class).size());
  }

  @Test
  void ruinedGemFreePiecesLoseTheGoldAndShowOnlyTheirPercents() {
    branding(true);
    when(station.canFinish()).thenReturn(GoldsmithFeedback.RUINED);
    when(station.getRecipePercent()).thenReturn(50.0);
    when(station.getHitPercent()).thenReturn(75.0);
    when(station.getTotalHitNeeded()).thenReturn(4);
    when(station.getTotalHitCount()).thenReturn(3);
    reset();
    manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
    assertNull(manager.get(loc));
    verify(station).cancel();
    verify(inventory, never()).addItem(any(ItemStack.class));
    verify(player).sendTitle(eq("§cThe piece was ruined"), contains("Ring"), eq(5), eq(40), eq(10));
    verify(player).sendMessage("§7Recipe: §e50%");
    verify(player).sendMessage("§7Hits: §e75%");
    verify(player).sendMessage("§7Total: §e50%");
    // Hits with unneeded tools leave the hit percent at 100, but still show as short of it.
    when(station.getRecipePercent()).thenReturn(100.0);
    when(station.getHitPercent()).thenReturn(100.0);
    when(station.getTotalHitCount()).thenReturn(5);
    reset();
    manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
    verify(player).sendMessage("§7Hits: §e80%");
    verify(player).sendMessage("§7Total: §e80%");
  }

  @Test
  void finishingReportsMissingRequirementsAndFailedOutputWithoutClearingStation() {
    branding(true);
    for (GoldsmithFeedback feedback :
        List.of(
            GoldsmithFeedback.LACKING_ITEMS, GoldsmithFeedback.NOT_INFUSED)) {
      reset();
      when(station.canFinish()).thenReturn(feedback);
      manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
      assertSame(station, manager.get(loc));
    }
    verify(player).sendMessage("§cYou have to add all the gold before finishing");
    reset();
    when(project.requiresGem()).thenReturn(true);
    when(station.canFinish()).thenReturn(GoldsmithFeedback.LACKING_ITEMS);
    manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
    verify(player).sendMessage("§cYou have to add all the gold and the gem before finishing");
    try (var output = mockStatic(JewelryOutput.class)) {
      reset();
      when(station.canFinish()).thenReturn(GoldsmithFeedback.SUCCESS);
      manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
      assertSame(station, manager.get(loc));
      verify(station, never()).cancel();
      verify(player).sendMessage(contains("Could not create"));
    }
  }

  @Test
  void successfulCraftDropsOutputAndClearsProjectWithOptionalQuality() {
    branding(true);
    when(station.canFinish()).thenReturn(GoldsmithFeedback.SUCCESS);
    Quality quality = mock(Quality.class);
    when(quality.getName()).thenReturn("Fine");
    try (var output = mockStatic(JewelryOutput.class)) {
      for (Quality chosen : Arrays.asList(null, quality)) {
        reset();
        // Gem-free projects (keys) have no quality and no stat carry line.
        when(project.requiresGem()).thenReturn(chosen != null);
        // Gem pieces carry the goldsmith's craft roll; gem-free ones have none.
        JewelryCraftResult result =
            chosen == null
                ? new JewelryCraftResult(new ItemStack(Material.DIAMOND), 90, 80, 80, 60, null)
                : new JewelryCraftResult(
                    new ItemStack(Material.DIAMOND), 90, 80, 80, 60, chosen, new D20Roll(17, 3));
        output.when(() -> JewelryOutput.build(station, player)).thenReturn(result);
        manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
        assertNull(manager.get(loc));
      }
      assertEquals(2, world.getEntitiesByClass(org.bukkit.entity.Item.class).size());
      verify(station, times(2)).cancel();
      verify(player).sendMessage("§7Quality: Fine");
      verify(player).sendMessage("§7Stat carry: §e60%");
      verify(player).sendMessage("§7Craft roll: §e17 §7(+3) = §e20");
    }
  }

  @Test
  void breakingActiveStationRefundsProjectAndRemovesPersistence() {
    reset();
    ItemStack refund = new ItemStack(Material.GOLD_INGOT);
    when(station.cancel()).thenReturn(List.of(refund));
    manager.onBreak(new BlockBreakEvent(block, player));
    assertNull(manager.get(loc));
    verify(inventory).addItem(refund);
    store.verify(() -> GoldsmithStationStore.delete(loc));
    manager.onBreak(new BlockBreakEvent(block, player));
    reset();
    when(station.hasProject()).thenReturn(false);
    manager.onBreak(new BlockBreakEvent(block, player));
    verify(station, times(1)).cancel();
  }

  @Test
  void breakWithoutPlayerDropsRefundAtBenchAndUnrelatedBlocksAreIgnored() {
    reset();
    ItemStack refund = new ItemStack(Material.GOLD_INGOT);
    when(station.cancel()).thenReturn(List.of(refund));
    manager.onBreak(new BlockBreakEvent(block, null));
    assertNull(manager.get(loc));
    assertEquals(1, world.getEntitiesByClass(org.bukkit.entity.Item.class).size());
    when(blocks.getChecker().checkBlock(eq(block), any())).thenReturn(false);
    manager.onBreak(new BlockBreakEvent(block, player));
    verify(station, times(1)).cancel();
  }

  @Test
  void emptyHandsAreNotStationToolsAndNoncandidateGemsFallThrough() {
    reset();
    when(inventory.getItemInMainHand()).thenReturn(null);
    manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
    when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
    manager.onInteract(click(Action.LEFT_CLICK_BLOCK));
    when(inventory.getItemInMainHand()).thenReturn(hand);
    when(project.requiresGem()).thenReturn(true);
    manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
    verify(station, never()).addGem(any());
  }

  @Test
  void removingBenchInvalidatesItsOpenMenuButPreservesOtherBenchMenus() {
    manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
    Player second = mock(Player.class);
    when(second.getUniqueId()).thenReturn(UUID.randomUUID());
    when(second.getInventory()).thenReturn(inventory);
    perms.when(() -> Permissions.canUseGoldsmith(second)).thenReturn(true);
    Block other = world.getBlockAt(4, 2, 3);
    when(blocks.getChecker().checkBlock(eq(other), any())).thenReturn(true);
    manager.onInteract(
        new PlayerInteractEvent(
            second, Action.RIGHT_CLICK_BLOCK, hand, other, BlockFace.UP, EquipmentSlot.HAND));
    manager.remove(loc);
    assertNotNull(manager.get(other.getLocation()));
    InventoryClickEvent event = mock(InventoryClickEvent.class);
    InventoryView view = mock(InventoryView.class);
    when(event.getView()).thenReturn(view);
    when(view.getTitle()).thenReturn(GoldsmithInventoryManager.TITLE);
    ItemStack icon = new ItemStack(Material.DIAMOND);
    var meta = icon.getItemMeta();
    meta.getPersistentDataContainer()
        .set(
            new NamespacedKey(InfusionMain.plugin, "gi_project"),
            PersistentDataType.STRING,
            "ring");
    icon.setItemMeta(meta);
    when(event.getCurrentItem()).thenReturn(icon);
    try (var projects = mockStatic(JewelryProjectLoader.class)) {
      projects.when(() -> JewelryProjectLoader.getByString("ring")).thenReturn(project);
      when(event.getWhoClicked()).thenReturn(player);
      manager.onMenuClick(event);
      verify(player).sendMessage(contains("no longer available"));
      when(event.getWhoClicked()).thenReturn(second);
      manager.onMenuClick(event);
      assertSame(project, manager.get(other.getLocation()).getProject());
    }
  }

  @Test
  void cancelledBreakDoesNotClearOrRefundProtectedStation() throws Exception {
    reset();
    BlockBreakEvent event = new BlockBreakEvent(block, player);
    event.setCancelled(true);
    var handler =
        GoldsmithStationManager.class
            .getMethod("onBreak", BlockBreakEvent.class)
            .getAnnotation(org.bukkit.event.EventHandler.class);
    if (!handler.ignoreCancelled()) manager.onBreak(event);
    assertSame(station, manager.get(loc), "A protected station must survive a cancelled break");
    verify(station, never()).cancel();
  }

  @Test
  void menuSelectionRequiresMatchingTitlePermissionMetadataProjectAndBench() {
    InventoryClickEvent event = mock(InventoryClickEvent.class);
    InventoryView view = mock(InventoryView.class);
    when(event.getView()).thenReturn(view);
    when(view.getTitle()).thenReturn("other");
    manager.onMenuClick(event);
    verify(event, never()).setCancelled(true);
    when(view.getTitle()).thenReturn(GoldsmithInventoryManager.TITLE);
    manager.onMenuClick(event);
    verify(event).setCancelled(true);
    when(event.getWhoClicked()).thenReturn(player);
    perms.when(() -> Permissions.canUseGoldsmith(player)).thenReturn(false);
    manager.onMenuClick(event);
    verify(player).closeInventory();
    perms.when(() -> Permissions.canUseGoldsmith(player)).thenReturn(true);
    manager.onMenuClick(event);
    ItemStack withoutMeta = mock(ItemStack.class);
    when(event.getCurrentItem()).thenReturn(withoutMeta);
    manager.onMenuClick(event);
    ItemStack icon = new ItemStack(Material.DIAMOND);
    var meta = icon.getItemMeta();
    meta.setDisplayName("Ring");
    icon.setItemMeta(meta);
    when(event.getCurrentItem()).thenReturn(icon);
    manager.onMenuClick(event);
    meta.getPersistentDataContainer()
        .set(
            new NamespacedKey(InfusionMain.plugin, "gi_project"),
            PersistentDataType.STRING,
            "ring");
    icon.setItemMeta(meta);
    try (var projects = mockStatic(JewelryProjectLoader.class)) {
      manager.onMenuClick(event);
      projects.when(() -> JewelryProjectLoader.getByString("ring")).thenReturn(project);
      manager.onMenuClick(event);
      verify(player).sendMessage(contains("no longer available"));
      manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
      GoldsmithStation actual = manager.get(loc);
      actual.setProject(project);
      manager.onMenuClick(event);
      verify(player).sendMessage(contains("already has a project"));
      manager.clear();
      manager.onInteract(click(Action.RIGHT_CLICK_BLOCK));
      manager.onMenuClick(event);
      assertSame(project, manager.get(loc).getProject());
      verify(player).sendMessage(contains("Selected"));
    }
  }
}
