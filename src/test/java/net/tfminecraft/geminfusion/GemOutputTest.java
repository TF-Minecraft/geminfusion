package net.tfminecraft.geminfusion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import java.util.function.Consumer;
import net.Indyuce.mmoitems.*;
import net.Indyuce.mmoitems.api.item.mmoitem.*;
import net.Indyuce.mmoitems.stat.data.*;
import net.Indyuce.mmoitems.stat.type.*;
import net.tfminecraft.geminfusion.goldsmith.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.*;

class GemOutputTest {
  InfusionMain previous;
  MMOItems previousMmo;
  io.lumine.mythic.lib.MythicLib previousLib;
  java.util.function.IntSupplier previousDie;
  MockedStatic<NBTItem> nbtApi;
  NBTItem nbt;
  Gemstone gem;
  GemRarity rarity;
  Player player;

  @BeforeEach
  void setup() throws Exception {
    MockBukkit.mock();
    previousLib = io.lumine.mythic.lib.MythicLib.plugin;
    io.lumine.mythic.lib.MythicLib.plugin =
        mock(io.lumine.mythic.lib.MythicLib.class, RETURNS_DEEP_STUBS);
    previous = InfusionMain.plugin;
    InfusionMain.plugin = mock(InfusionMain.class);
    when(InfusionMain.plugin.namespace()).thenReturn("geminfusion");
    when(InfusionMain.plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    previousMmo = MMOItems.plugin;
    MMOItems.plugin = mock(MMOItems.class, RETURNS_DEEP_STUBS);
    when(MMOItems.plugin.namespace()).thenReturn("mmoitems");
    when(io.lumine.mythic.lib.MythicLib.plugin.namespace()).thenReturn("mythiclib");
    previousDie = D20Roll.die;
    D20Roll.load(null);
    nbt = mock(NBTItem.class);
    nbtApi = mockStatic(NBTItem.class);
    nbtApi.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbt);
    gem = new Gemstone();
    gem.setId("ruby");
    gem.setName("Ruby");
    gem.setMMOItem("GEM_STONE.RUBY");
    gem.setSocketColour("Red");
    gem.setSocketNameColour(ChatColor.RED);
    rarity = new GemRarity("rare", config("name: Rare\nchance: 1"));
    player = mock(Player.class);
    ConfigLoader.loadedGems.clear();
    ConfigLoader.loadedGems.add(gem);
    when(nbt.hasType()).thenReturn(true);
    when(nbt.getType()).thenReturn("GEM_STONE");
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("RUBY");
    var attack = ItemStats.ATTACK_DAMAGE;
    when(MMOItems.plugin.getStats().get("ATTACK_DAMAGE")).thenReturn(attack);
  }

  @AfterEach
  void cleanup() {
    if (nbtApi != null) nbtApi.close();
    io.lumine.mythic.lib.MythicLib.plugin = previousLib;
    InfusionMain.plugin = previous;
    MMOItems.plugin = previousMmo;
    D20Roll.die = previousDie;
    D20Roll.load(null);
    ConfigLoader.loadedGems.clear();
    MockBukkit.unmock();
  }

  static YamlConfiguration config(String text) throws Exception {
    YamlConfiguration c = new YamlConfiguration();
    c.loadFromString(text);
    return c;
  }

  GemStat stat(String rarityId, String id, double value) throws Exception {
    return GemStat.parse("ruby", rarityId, config("stat: " + id + "\nmin: " + value));
  }

  ItemStack tagged(Material material) {
    ItemStack item = new ItemStack(material);
    var meta = item.getItemMeta();
    meta.setDisplayName("Original");
    item.setItemMeta(meta);
    return item;
  }

  MockedConstruction<LiveMMOItem> live(Consumer<LiveMMOItem> configure) {
    return mockConstruction(
        LiveMMOItem.class,
        withSettings().defaultAnswer(RETURNS_DEEP_STUBS),
        (m, ctx) -> {
          when(m.computeStatHistory(any())).thenReturn(null);
          when(m.getData(any())).thenReturn(null);
          configure.accept(m);
        });
  }

  @Test
  void statRollUsesMatchingRarityAndTheD20ThenClampsSuccessRange() throws Exception {
    gem.addStat(stat("other", "ATTACK_DAMAGE", 9));
    gem.addStat(GemStat.parse("ruby", "rare", config("stat: ATTACK_DAMAGE\nmin: 1\nmax: 2")));
    MMOItem output = mock(MMOItem.class);
    var value = ArgumentCaptor.forClass(DoubleData.class);
    // The rare range 1-2 spreads to 0.8-2.4: a natural 20 is the Flawless top, a natural 1 the bottom,
    // and a 10 with no modifier (no MMOCore here) lands 9/19 of the way up.
    D20Roll.die = () -> 20;
    InfusedGemBuilder.rollStats(output, gem, rarity, 100, player);
    D20Roll.die = () -> 1;
    InfusedGemBuilder.rollStats(output, gem, rarity, 100, player);
    D20Roll.die = () -> 10;
    InfusedGemBuilder.rollStats(output, gem, rarity, 100, player);
    verify(output, times(3)).setData(eq(ItemStats.ATTACK_DAMAGE), value.capture());
    assertEquals(
        List.of(2.4, 0.8, 1.5579),
        value.getAllValues().stream().map(DoubleData::getValue).toList());
    verify(output, times(3))
        .setData(eq(ItemStats.SUCCESS_RATE), argThat(v -> ((DoubleData) v).getValue() == 40));
    gem.setStats(new ArrayList<>());
    InfusedGemBuilder.rollStats(output, gem, rarity, 1, player);
    verify(output, times(4)).setData(eq(ItemStats.SUCCESS_RATE), any());
    gem.addStat(stat("rare", "MISSING", 1));
    when(MMOItems.plugin.getStats().get("MISSING")).thenReturn(null);
    InfusedGemBuilder.rollStats(output, gem, rarity, 1, player);
    InfusionMain.plugin = null;
    InfusedGemBuilder.rollStats(output, gem, rarity, 1, player);
  }

  @Test
  void cosmeticsUpdateNameHistoryLoreAndRarityWithoutChangingStats() {
    MMOItem output = mock(MMOItem.class);
    InfusedGemBuilder.applyCosmetics(output, gem, rarity);
    var name = ArgumentCaptor.forClass(StringData.class);
    verify(output).replaceData(eq(ItemStats.NAME), name.capture());
    assertEquals("Rare Infused Ruby", name.getValue().getString());
    StringData existing = new StringData("Old");
    when(output.getData(ItemStats.NAME)).thenReturn(existing);
    StatHistory history = mock(StatHistory.class);
    NameData original = new NameData("Old");
    when(history.getOriginalData()).thenReturn(original);
    when(output.computeStatHistory(ItemStats.NAME)).thenReturn(history);
    InfusedGemBuilder.applyCosmetics(output, gem, rarity);
    assertEquals("Rare Infused Ruby", existing.getString());
    assertEquals("Rare Infused Ruby", original.getString());
    verify(output).setStatHistory(ItemStats.NAME, history);
    verify(output, times(2)).setData(eq(ItemStats.LORE), any(StringListData.class));
    MMOItem unknown = mock(MMOItem.class);
    InfusedGemBuilder.applyCosmetics(unknown, gem, null);
    verify(unknown).replaceData(eq(ItemStats.NAME), name.capture());
    assertEquals("Infused Ruby", name.getValue().getString());
    var lore = ArgumentCaptor.forClass(StringListData.class);
    verify(unknown).setData(eq(ItemStats.LORE), lore.capture());
    assertEquals(1, lore.getValue().getList().size());
  }

  @Test
  void buildsAndRebuildsGemWithCosmeticsGlintAndPdc() {
    ItemStack blank = tagged(Material.DIAMOND), result = tagged(Material.DIAMOND);
    Gemstone template = mock(Gemstone.class);
    when(template.getMMOItem()).thenReturn(mock(MMOItem.class, RETURNS_DEEP_STUBS));
    when(template.getMMOItem().newBuilder().build()).thenReturn(blank);
    when(template.getName()).thenReturn("Ruby");
    when(template.getSocketColour()).thenReturn("Red");
    when(template.getSocketNameColour()).thenReturn(ChatColor.RED);
    try (var constructed = live(m -> when(m.newBuilder().build()).thenReturn(result))) {
      assertSame(result, InfusedGemBuilder.buildInfusedGem(template, rarity, 3, player));
      assertEquals("rare", GemRarityPdc.read(result));
      assertTrue(result.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS));
      blank.setAmount(2);
      assertSame(result, InfusedGemBuilder.applyCosmeticsToItem(blank, gem, rarity));
      assertEquals(2, result.getAmount());
      assertSame(result, InfusedGemBuilder.applyCosmeticsToItem(blank, gem, null));
    }
    try (var constructed = live(m -> when(m.newBuilder().build()).thenReturn(null))) {
      assertNull(InfusedGemBuilder.applyCosmeticsToItem(blank, gem, rarity));
    }
    assertNull(InfusedGemBuilder.applyCosmeticsToItem(null, gem, rarity));
    ItemStack air = new ItemStack(Material.AIR);
    assertSame(air, InfusedGemBuilder.applyCosmeticsToItem(air, gem, rarity));
    assertSame(blank, InfusedGemBuilder.applyCosmeticsToItem(blank, null, rarity));
    assertNull(InfusedGemBuilder.finalizeItem(null, "rare"));
    assertSame(air, InfusedGemBuilder.finalizeItem(air, "rare"));
  }

  @Test
  void infusedValidatorRecognizesOnlyConfiguredInfusedGemsAndCandidates() {
    ItemStack item = tagged(Material.DIAMOND);
    assertFalse(InfusedGemValidator.isInfused(null));
    assertFalse(InfusedGemValidator.isInfused(new ItemStack(Material.AIR)));
    assertFalse(InfusedGemValidator.isGemstoneCandidate(null));
    assertFalse(InfusedGemValidator.isGemstoneCandidate(new ItemStack(Material.AIR)));
    when(nbt.hasType()).thenReturn(false);
    assertFalse(InfusedGemValidator.isInfused(item));
    assertFalse(InfusedGemValidator.isGemstoneCandidate(item));
    when(nbt.hasType()).thenReturn(true);
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("Blank Gemstone");
    assertFalse(InfusedGemValidator.isInfused(item));
    assertTrue(InfusedGemValidator.isGemstoneCandidate(item));
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("Infused Gemstone");
    assertTrue(InfusedGemValidator.isInfused(item));
    assertTrue(InfusedGemValidator.isGemstoneCandidate(item));
    ConfigLoader.loadedGems.clear();
    assertFalse(InfusedGemValidator.isInfused(item));
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("other");
    assertFalse(InfusedGemValidator.isGemstoneCandidate(item));
    ConfigLoader.loadedGems.add(gem);
    assertTrue(InfusedGemValidator.isGemstoneCandidate(item));
  }

  @Test
  void blankGemKeepingItsInfusionStatCountsAsInfusedAndReset() throws Exception {
    ItemStack item = tagged(Material.DIAMOND);
    String path = ItemStats.ATTACK_DAMAGE.getNBTPath();
    gem.addStat(stat("common", "MISSING", 1));
    gem.addStat(stat("rare", "ATTACK_DAMAGE", 2));
    when(MMOItems.plugin.getStats().get("MISSING")).thenReturn(null);
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("Blank Gemstone");
    assertFalse(InfusedGemValidator.isInfused(item));
    assertFalse(InfusedGemValidator.isReset(item));
    when(nbt.hasTag(path)).thenReturn(true);
    assertFalse(InfusedGemValidator.isReset(item));
    when(nbt.getDouble(path)).thenReturn(4.1);
    assertTrue(InfusedGemValidator.isInfused(item));
    assertTrue(InfusedGemValidator.isReset(item));
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("Sword");
    assertFalse(InfusedGemValidator.isInfused(item));
    assertFalse(InfusedGemValidator.isReset(item));
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("Infused Gemstone");
    assertFalse(InfusedGemValidator.isReset(item));
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("Blank Gemstone");
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("OTHER");
    assertFalse(InfusedGemValidator.isReset(item));
    when(nbt.hasType()).thenReturn(false);
    assertFalse(InfusedGemValidator.isReset(item));
    assertFalse(InfusedGemValidator.isReset(null));
    assertFalse(InfusedGemValidator.isReset(new ItemStack(Material.AIR)));
  }

  @Test
  void restoreResetRebuildsOnlyResetGemsKeepingKnownRarityAndAmount() throws Exception {
    ItemStack item = tagged(Material.DIAMOND), withRarity = tagged(Material.DIAMOND);
    String path = ItemStats.ATTACK_DAMAGE.getNBTPath();
    gem.addStat(stat("rare", "ATTACK_DAMAGE", 2));
    when(nbt.getString("MMOITEMS_DISPLAYED_TYPE")).thenReturn("Blank Gemstone");
    assertSame(item, InfusedGemBuilder.restoreReset(item));
    when(nbt.hasTag(path)).thenReturn(true);
    when(nbt.getDouble(path)).thenReturn(4.1);
    ConfigLoader.loadedRarities.add(rarity);
    item.setAmount(3);
    try (var constructed = live(m -> when(m.newBuilder().build()).thenReturn(withRarity))) {
      GemRarityPdc.write(item, "rare");
      assertSame(withRarity, InfusedGemBuilder.restoreReset(item));
      assertEquals("rare", GemRarityPdc.read(withRarity));
      assertEquals(3, withRarity.getAmount());
      verify(constructed.constructed().get(0))
          .replaceData(eq(ItemStats.NAME), argThat(d -> "Rare Infused Ruby".equals(((StringData) d).getString())));
    }
    ItemStack unknown = tagged(Material.DIAMOND), withoutRarity = tagged(Material.DIAMOND);
    try (var constructed = live(m -> when(m.newBuilder().build()).thenReturn(withoutRarity))) {
      assertSame(withoutRarity, InfusedGemBuilder.restoreReset(unknown));
      assertNull(GemRarityPdc.read(withoutRarity));
      verify(constructed.constructed().get(0))
          .replaceData(eq(ItemStats.NAME), argThat(d -> "Infused Ruby".equals(((StringData) d).getString())));
    }
    ConfigLoader.loadedRarities.clear();
  }

  @Test
  void jewelryRejectsMissingInputsInvalidGemsAndUnreadableStats() {
    GoldsmithStation station = mock(GoldsmithStation.class);
    JewelryProject project = mock(JewelryProject.class);
    when(project.requiresGem()).thenReturn(true);
    ItemStack gemItem = tagged(Material.DIAMOND);
    try (var log = mockStatic(GoldsmithLog.class);
        var valid = mockStatic(InfusedGemValidator.class);
        var constructed = live(m -> when(m.getStats()).thenReturn(Set.of()))) {
      assertNull(JewelryOutput.build(station, player));
      when(station.getProject()).thenReturn(project);
      assertNull(JewelryOutput.build(station, player));
      when(station.getGem()).thenReturn(gemItem);
      assertNull(JewelryOutput.build(station, player));
      valid.when(() -> InfusedGemValidator.isInfused(gemItem)).thenReturn(true);
      when(nbt.hasType()).thenReturn(false);
      assertNull(JewelryOutput.build(station, player));
      when(nbt.hasType()).thenReturn(true);
      assertNull(JewelryOutput.build(station, player));
      ConfigLoader.loadedGems.clear();
      assertNull(JewelryOutput.build(station, player));
    }
  }

  @Test
  void jewelryCarriesScaledStatIntoFreshHistoryAndQualityLore() throws Exception {
    // The rarity boost is a random roll; switch it off so the carry is exact.
    var boostChances = GoldsmithCache.jewelryGemStatBoostChances;
    GoldsmithCache.jewelryGemStatBoostChances = Map.of();
    GoldsmithStation station = mock(GoldsmithStation.class);
    JewelryProject project = mock(JewelryProject.class);
    when(project.requiresGem()).thenReturn(true);
    ItemStack gemItem = tagged(Material.DIAMOND),
        base = tagged(Material.GOLD_INGOT),
        out = tagged(Material.GOLD_INGOT);
    when(station.getProject()).thenReturn(project);
    when(station.getGem()).thenReturn(gemItem);
    when(station.getRecipePercent()).thenReturn(90.0);
    when(station.getHitPercent()).thenReturn(80.0);
    when(project.getItem()).thenReturn("m.ring");
    when(project.getTierMultiplier()).thenReturn(.5);
    // A natural 20 craft roll gives the top +20%.
    D20Roll.die = () -> 20;
    gem.addStat(stat("other", "MISSING", 3));
    gem.addStat(stat("rare", "ATTACK_DAMAGE", 10));
    GemRarityPdc.write(gemItem, "rare");
    ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    when(api.getCreator().getItemFromPath("m.ring")).thenReturn(base);
    Quality quality = mock(Quality.class);
    when(quality.getName()).thenReturn("Fine");
    StatHistory history = mock(StatHistory.class);
    DoubleData original = new DoubleData(7);
    when(history.getOriginalData()).thenReturn(original);
    try (var libs = mockStatic(TLibs.class);
        var valid = mockStatic(InfusedGemValidator.class);
        var qualityApi = mockStatic(QualityLoader.class);
        var constructed =
            live(
                m -> {
                  when(m.getData(ItemStats.ATTACK_DAMAGE)).thenReturn(new DoubleData(10));
                  when(m.newBuilder().build()).thenReturn(out);
                  when(m.computeStatHistory(ItemStats.ATTACK_DAMAGE)).thenReturn(history);
                })) {
      libs.when(TLibs::getItemAPI).thenReturn(api);
      valid.when(() -> InfusedGemValidator.isInfused(gemItem)).thenReturn(true);
      qualityApi.when(() -> QualityLoader.getByAmount(80)).thenReturn(quality);
      qualityApi.when(() -> QualityLoader.resolveStatFactor(80)).thenReturn(50.0);
      GoldsmithMaterial material = mock(GoldsmithMaterial.class);
      when(material.getPath()).thenReturn("m.materials.shiny_gold");
      when(station.getDepositedByMaterial()).thenReturn(Map.of(material, 3));
      JewelryCraftResult result = JewelryOutput.build(station, player);
      assertNotNull(result);
      assertSame(out, result.getItem());
      assertEquals(Map.of("m.materials.shiny_gold", 3), GoldsmithProvenance.read(out));
      assertEquals(80, result.getFinishedTotal());
      assertEquals(90, result.getRecipePercent());
      assertEquals(80, result.getHitPercent());
      assertEquals(50, result.getStatCarryPercent());
      assertSame(quality, result.getQuality());
      assertTrue(result.getCraftRoll().isCritical());
      assertEquals(0, original.getValue());
      verify(constructed.constructed().getLast())
          .setData(
              eq(ItemStats.ATTACK_DAMAGE),
              argThat(v -> v instanceof DoubleData d && d.getValue() == 3.0));
      verify(history)
          .registerExternalData(argThat(v -> v instanceof DoubleData d && d.getValue() == 3.0));
    } finally {
      GoldsmithCache.jewelryGemStatBoostChances = boostChances;
    }
  }

  @Test
  void masterworkNeedsPerfectWorkAFlawlessGemAndACraftRollAtTheDc() throws Exception {
    var boostChances = GoldsmithCache.jewelryGemStatBoostChances;
    GoldsmithCache.jewelryGemStatBoostChances = Map.of();
    GoldsmithStation station = mock(GoldsmithStation.class);
    JewelryProject project = mock(JewelryProject.class);
    when(project.requiresGem()).thenReturn(true);
    ItemStack gemItem = tagged(Material.DIAMOND),
        base = tagged(Material.GOLD_INGOT),
        out = tagged(Material.GOLD_INGOT);
    when(station.getProject()).thenReturn(project);
    when(station.getGem()).thenReturn(gemItem);
    when(station.getRecipePercent()).thenReturn(100.0);
    when(station.getHitPercent()).thenReturn(100.0);
    when(project.getItem()).thenReturn("m.ring");
    when(project.getTierMultiplier()).thenReturn(1.0);
    // Rare 2-3 spreads to 1.6-3.6, so only 3.6 is Flawless; epic and a different stat never match it.
    gem.addStat(GemStat.parse("ruby", "legendary", config("stat: OTHER\nmin: 3\nmax: 3")));
    gem.addStat(GemStat.parse("ruby", "epic", config("stat: ATTACK_DAMAGE\nmin: 3\nmax: 4")));
    gem.addStat(GemStat.parse("ruby", "rare", config("stat: ATTACK_DAMAGE\nmin: 2\nmax: 3")));
    when(MMOItems.plugin.getStats().get("OTHER")).thenReturn(null);
    GemRarityPdc.write(gemItem, "rare");
    ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    when(api.getCreator().getItemFromPath("m.ring")).thenReturn(base);
    Quality masterwork = mock(Quality.class), gleaming = mock(Quality.class);
    when(gleaming.getStatMax()).thenReturn(65.0);
    double[] gemValue = {3.6};
    int dc = D20Roll.masterworkDc;
    try (var libs = mockStatic(TLibs.class);
        var valid = mockStatic(InfusedGemValidator.class);
        var qualityApi = mockStatic(QualityLoader.class);
        var constructed =
            live(
                m -> {
                  when(m.getData(ItemStats.ATTACK_DAMAGE))
                      .thenAnswer(i -> new DoubleData(gemValue[0]));
                  when(m.getStats()).thenReturn(Set.of(ItemStats.ATTACK_DAMAGE));
                  when(m.newBuilder().build()).thenReturn(out);
                })) {
      libs.when(TLibs::getItemAPI).thenReturn(api);
      valid.when(() -> InfusedGemValidator.isInfused(gemItem)).thenReturn(true);
      qualityApi.when(() -> QualityLoader.getByAmount(100)).thenReturn(masterwork);
      qualityApi.when(() -> QualityLoader.resolveStatFactor(100)).thenReturn(100.0);
      qualityApi.when(() -> QualityLoader.below(masterwork)).thenReturn(gleaming);
      // Each craft: the quality, the stat carry and the stat written to the jewelry.
      record Craft(Quality quality, double carry, double stat) {}
      java.util.function.Supplier<Craft> craft =
          () -> {
            JewelryCraftResult r = JewelryOutput.build(station, player);
            var written = ArgumentCaptor.forClass(DoubleData.class);
            // The original is zeroed first, then the crafted stat is written.
            verify(constructed.constructed().getLast(), times(2))
                .setData(eq(ItemStats.ATTACK_DAMAGE), written.capture());
            return new Craft(r.getQuality(), r.getStatCarryPercent(), written.getValue().getValue());
          };
      // Flawless gem, natural 20: Masterwork at the absolute max, 3.6 x 100% x 1.2.
      D20Roll.die = () -> 20;
      assertEquals(new Craft(masterwork, 100, 4.32), craft.get());
      // A total at the DC without a natural 20 still gets the full +20%.
      D20Roll.masterworkDc = 15;
      D20Roll.die = () -> 16;
      assertEquals(new Craft(masterwork, 100, 4.32), craft.get());
      // Flawless gem, natural 1: Gleaming at its top carry, then -20%.
      D20Roll.die = () -> 1;
      assertEquals(new Craft(gleaming, 65, 1.872), craft.get());
      // A gem below the top can never make a Masterwork, even on a natural 20.
      gemValue[0] = 3.5;
      D20Roll.die = () -> 20;
      assertEquals(new Craft(gleaming, 65, 2.73), craft.get());
      // With no tier below, perfect work keeps its tier.
      qualityApi.when(() -> QualityLoader.below(masterwork)).thenReturn(null);
      assertEquals(new Craft(masterwork, 100, 4.2), craft.get());
      qualityApi.when(() -> QualityLoader.below(masterwork)).thenReturn(gleaming);
      // A reset gem with no stored rarity is checked against every rarity of its gem.
      ItemStack reset = tagged(Material.DIAMOND);
      when(station.getGem()).thenReturn(reset);
      valid.when(() -> InfusedGemValidator.isInfused(reset)).thenReturn(true);
      gemValue[0] = 3.6;
      assertEquals(new Craft(masterwork, 100, 4.32), craft.get());
      // A gem that is no longer configured cannot be Flawless.
      ConfigLoader.loadedGems.clear();
      assertEquals(new Craft(gleaming, 65, 2.808), craft.get());
    } finally {
      D20Roll.masterworkDc = dc;
      GoldsmithCache.jewelryGemStatBoostChances = boostChances;
      ConfigLoader.loadedGems.add(gem);
    }
  }

  @Test
  void jewelryFallsBackAfterMatchingNonnumericStatAndHandlesFailedBuild() throws Exception {
    GoldsmithStation station = mock(GoldsmithStation.class);
    JewelryProject project = mock(JewelryProject.class);
    when(project.requiresGem()).thenReturn(true);
    ItemStack gemItem = tagged(Material.DIAMOND), base = tagged(Material.DIAMOND);
    when(station.getProject()).thenReturn(project);
    when(station.getGem()).thenReturn(gemItem);
    when(project.getItem()).thenReturn("m.ring");
    gem.addStat(stat("rare", "NAME", 1));
    gem.addStat(stat("other", "ATTACK_DAMAGE", 2));
    GemRarityPdc.write(gemItem, "rare");
    when(MMOItems.plugin.getStats().get("NAME")).thenReturn(ItemStats.NAME);
    ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    when(api.getCreator().getItemFromPath("m.ring")).thenReturn(base);
    StatHistory history = mock(StatHistory.class);
    when(history.getOriginalData()).thenReturn(new StringData("unexpected legacy value"));
    try (var libs = mockStatic(TLibs.class);
        var valid = mockStatic(InfusedGemValidator.class);
        var quality = mockStatic(QualityLoader.class);
        var log = mockStatic(GoldsmithLog.class);
        var constructed =
            live(
                m -> {
                  when(m.getData(ItemStats.NAME)).thenReturn(new StringData("Name"));
                  when(m.getData(ItemStats.ATTACK_DAMAGE)).thenReturn(new DoubleData(2));
                  when(m.newBuilder().build()).thenReturn(null);
                  when(m.computeStatHistory(ItemStats.ATTACK_DAMAGE)).thenReturn(history);
                })) {
      libs.when(TLibs::getItemAPI).thenReturn(api);
      valid.when(() -> InfusedGemValidator.isInfused(gemItem)).thenReturn(true);
      assertNull(JewelryOutput.build(station, player));
      log.verify(() -> GoldsmithLog.warn(contains("Could not build jewelry item")));
      GemRarityPdc.write(gemItem, "absent");
      assertNull(JewelryOutput.build(station, player));
    }
  }

  @Test
  void jewelryHandlesFallbackStatsInvalidOutputPathsAndAbsentHistory() throws Exception {
    GoldsmithStation station = mock(GoldsmithStation.class);
    JewelryProject project = mock(JewelryProject.class);
    when(project.requiresGem()).thenReturn(true);
    ItemStack gemItem = tagged(Material.DIAMOND),
        base = tagged(Material.DIAMOND),
        out = tagged(Material.DIAMOND);
    when(station.getProject()).thenReturn(project);
    when(station.getGem()).thenReturn(gemItem);
    when(project.getTierMultiplier()).thenReturn(1.0);
    gem.addStat(stat("other", "SUCCESS_RATE", 1));
    gem.addStat(stat("rare", "ATTACK_DAMAGE", 1));
    gem.addStat(stat("x", "MISSING", 1));
    when(MMOItems.plugin.getStats().get("MISSING")).thenReturn(null);
    ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    when(api.getCreator().getItemFromPath(any())).thenReturn(base);
    ItemStat<?, ?> nullId = mock(ItemStat.class);
    ItemStat<?, ?> dash = mock(ItemStat.class);
    when(dash.getId()).thenReturn("SUCCESS-RATE");
    LinkedHashSet<ItemStat> all =
        new LinkedHashSet<>(
            Arrays.asList(
                null,
                nullId,
                ItemStats.SUCCESS_RATE,
                dash,
                ItemStats.NAME,
                ItemStats.ATTACK_DAMAGE));
    try (var libs = mockStatic(TLibs.class);
        var valid = mockStatic(InfusedGemValidator.class);
        var quality = mockStatic(QualityLoader.class);
        var log = mockStatic(GoldsmithLog.class);
        var constructed =
            live(
                m -> {
                  when(m.getStats()).thenReturn(all);
                  when(m.getData(ItemStats.NAME)).thenReturn(new StringData("Name"));
                  when(m.getData(ItemStats.ATTACK_DAMAGE)).thenReturn(new DoubleData(2));
                  when(m.newBuilder().build()).thenReturn(out);
                })) {
      libs.when(TLibs::getItemAPI).thenReturn(api);
      valid.when(() -> InfusedGemValidator.isInfused(gemItem)).thenReturn(true);
      assertNotNull(JewelryOutput.build(station, player));
      gem.setStats(List.of(stat("x", "SUCCESS-RATE", 1), stat("x", "MISSING", 1)));
      assertNotNull(JewelryOutput.build(station, player));
      when(project.getItem()).thenReturn("ia.bad");
      when(api.getCreator().getItemFromPath("ia.bad")).thenReturn(new ItemStack(Material.DIRT));
      assertNull(JewelryOutput.build(station, player));
      when(api.getCreator().getItemFromPath("ia.bad")).thenReturn(null);
      assertNull(JewelryOutput.build(station, player));
      when(api.getCreator().getItemFromPath("ia.bad")).thenReturn(base);
      when(MMOItems.plugin.getStats().get("ATTACK_DAMAGE")).thenReturn(null);
      assertNull(JewelryOutput.build(station, player));
    }
  }

  @Test
  void gemFreeProjectGivesPlainItemWithProvenanceAndNoQuality() {
    GoldsmithStation station = mock(GoldsmithStation.class);
    JewelryProject project = mock(JewelryProject.class);
    ItemStack key = tagged(Material.GOLD_NUGGET);
    key.setAmount(3);
    when(station.getProject()).thenReturn(project);
    when(station.getRecipePercent()).thenReturn(90.0);
    when(station.getHitPercent()).thenReturn(80.0);
    when(project.getItem()).thenReturn("m.keys.gold_key");
    GoldsmithMaterial material = mock(GoldsmithMaterial.class);
    when(material.getPath()).thenReturn("m.materials.rough_gold");
    when(station.getDepositedByMaterial()).thenReturn(Map.of(material, 5));
    ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    when(api.getCreator().getItemFromPath("m.keys.gold_key")).thenReturn(key);
    try (var libs = mockStatic(TLibs.class);
        var log = mockStatic(GoldsmithLog.class);
        var constructed = live(m -> {})) {
      libs.when(TLibs::getItemAPI).thenReturn(api);
      JewelryCraftResult result = JewelryOutput.build(station, player);
      assertNotNull(result);
      assertSame(key, result.getItem());
      assertEquals(1, key.getAmount());
      assertEquals("Original", key.getItemMeta().getDisplayName());
      assertEquals(Map.of("m.materials.rough_gold", 5), GoldsmithProvenance.read(key));
      assertEquals(GoldsmithMath.finishedTotal(90, 80), result.getFinishedTotal());
      assertEquals(0, result.getStatCarryPercent());
      assertNull(result.getQuality());
      assertTrue(constructed.constructed().isEmpty());
      verify(station, never()).getGem();
      when(api.getCreator().getItemFromPath("m.keys.gold_key")).thenReturn(null);
      assertNull(JewelryOutput.build(station, player));
      log.verify(() -> GoldsmithLog.warn(contains("Could not build output")));
    }
  }
}
