package net.tfminecraft.geminfusion.goldsmith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.geminfusion.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.*;
import org.bukkit.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class ConfigLoadersTest {
  @TempDir Path temp;
  Map<java.lang.reflect.Field, Object> cacheBefore = new HashMap<>();

  @BeforeEach
  void captureCache() throws Exception {
    for (var field : GoldsmithCache.class.getFields()) cacheBefore.put(field, field.get(null));
  }

  @AfterEach
  void clearRegistries() throws Exception {
    for (var entry : cacheBefore.entrySet()) entry.getKey().set(null, entry.getValue());
    GoldsmithHitTypeLoader.get().clear();
    GoldsmithHitLoader.get().clear();
    GoldsmithMaterialLoader.get().clear();
    GoldsmithMaterialTypeLoader.get().clear();
    JewelryProjectLoader.get().clear();
    ProjectTierLoader.get().clear();
    QualityLoader.get().clear();
    ConfigLoader.loadedGems.clear();
    ConfigLoader.loadedRarities.clear();
    ConfigLoader.stations.clear();
    ConfigLoader.locations.clear();
  }

  File yaml(String contents) throws Exception {
    return Files.writeString(temp.resolve(UUID.randomUUID() + ".yml"), contents).toFile();
  }

  YamlConfiguration config(String text) throws Exception {
    YamlConfiguration c = new YamlConfiguration();
    c.loadFromString(text);
    return c;
  }

  void basicDefinitions() throws Exception {
    new GoldsmithHitTypeLoader().load(yaml("gold:\n  name: Gold work\n"));
    new GoldsmithHitLoader().load(yaml("hit:\n  tool: m.hammer\n  type: gold\n  name: Hammer\n"));
    new GoldsmithMaterialLoader()
        .load(yaml("gold:\n  path: m.gold\n  name: Gold\n  hits: [hit.2, hit.3]\n"));
  }

  @Test
  void shippedConfigurationDefinitionsLoadAndReferenceEachOther() throws Exception {
    Path root = Path.of("src/main/resources/goldsmithing");
    new GoldsmithHitTypeLoader().load(root.resolve("hit-types.yml").toFile());
    new GoldsmithHitLoader().load(root.resolve("hits.yml").toFile());
    new GoldsmithMaterialTypeLoader().load(root.resolve("material-types.yml").toFile());
    new GoldsmithMaterialLoader().load(root.resolve("materials.yml").toFile());
    new ProjectTierLoader().load(root.resolve("tiers.yml").toFile());
    new QualityLoader().load(root.resolve("qualities.yml").toFile());
    new JewelryProjectLoader().load(root.resolve("projects.yml").toFile());
    assertFalse(JewelryProjectLoader.get().isEmpty());
    for (JewelryProject p : JewelryProjectLoader.get().values()) {
      assertNotNull(p.getId());
      assertNotNull(p.getName());
      assertNotNull(p.getItem());
      assertNotNull(p.getTierId());
      assertTrue(p.getTierMultiplier() > 0);
      assertFalse(p.getRecipe().isEmpty());
      assertFalse(p.getMaterialsByType().isEmpty());
      assertEquals(!p.getId().equals("gold_key"), p.requiresGem());
      assertThrows(UnsupportedOperationException.class, () -> p.getRecipe().clear());
    }
    for (GoldsmithMaterial m : GoldsmithMaterialLoader.get().values()) {
      assertNotNull(m.getId());
      assertNotNull(m.getName());
      assertNotNull(m.getPath());
      assertNotNull(m.getType());
      assertFalse(m.getHits().isEmpty());
      assertThrows(UnsupportedOperationException.class, () -> m.getHits().clear());
    }
    GoldsmithHit hit = GoldsmithHitLoader.getByString("hit");
    assertEquals("hit", hit.getId());
    assertNotNull(hit.getName());
    assertNotNull(hit.getType().getId());
    assertNotNull(hit.getType().getName());
    assertSame(hit, GoldsmithHitLoader.getByTool(hit.getTool().toUpperCase()));
    GoldsmithMaterialType type = GoldsmithMaterialTypeLoader.get().values().iterator().next();
    assertNotNull(type.getId());
    assertEquals(type.getName(), GoldsmithMaterialTypeLoader.display(type.getId()));
    assertEquals(100, QualityLoader.resolveStatFactor(100));
    assertEquals(100, QualityLoader.resolveStatFactor(150));
    // A perfect piece that misses a Masterwork drops to the tier below it.
    assertSame(QualityLoader.getByString("epic"), QualityLoader.below(QualityLoader.getByString("legendary")));
    assertNull(QualityLoader.below(QualityLoader.getByString("crude")));
    assertNull(QualityLoader.below(null));
    for (Quality q : QualityLoader.get().values()) {
      assertEquals(q, QualityLoader.getByString(q.getId()));
      assertNotNull(q.getName());
      assertTrue(q.isValid(q.getAmount()));
      assertFalse(q.isValid(q.getAmount() - .1));
    }
    assertNull(GoldsmithHitLoader.getByString(null));
    assertNull(GoldsmithHitTypeLoader.getByString(null));
    assertNull(GoldsmithMaterialLoader.getByString(null));
    assertNull(GoldsmithMaterialTypeLoader.getByString(null));
    assertNull(JewelryProjectLoader.getByString(null));
    assertNull(QualityLoader.getByString(null));
    assertNull(GoldsmithHitLoader.getByTool(null));
    assertNull(GoldsmithHitLoader.getByTool("absent"));
    assertEquals("Materials", GoldsmithMaterialTypeLoader.display(null));
    assertEquals("Materials", GoldsmithMaterialTypeLoader.display(" "));
    assertEquals("Silver Materials", GoldsmithMaterialTypeLoader.display("silver"));
  }

  @Test
  void missingUnreadableAndMalformedYamlAreLoggedWithoutCrashing() throws Exception {
    try (var log = mockStatic(GoldsmithLog.class)) {
      assertNull(GoldsmithYaml.read(null));
      assertNull(GoldsmithYaml.read(temp.resolve("missing").toFile()));
      assertNull(GoldsmithYaml.read(yaml("x: [")));
      assertNull(GoldsmithYaml.read(temp.toFile()));
      assertNotNull(GoldsmithYaml.read(yaml("x: 1")));
      new GoldsmithHitTypeLoader().load(null);
      new GoldsmithHitLoader().load(null);
      new GoldsmithMaterialLoader().load(null);
      new GoldsmithMaterialTypeLoader().load(null);
      new JewelryProjectLoader().load(null);
      new QualityLoader().load(null);
      new ProjectTierLoader().load(null);
      new GoldsmithConfigLoader().load(null);
      assertTrue(GoldsmithHitLoader.get().isEmpty());
      assertTrue(QualityLoader.get().isEmpty());
      assertEquals(1, QualityLoader.resolveStatFactor(20));
    }
  }

  @Test
  void invalidDefinitionsAreSkippedAndValidHitCountsAreMerged() throws Exception {
    basicDefinitions();
    try (var log = mockStatic(GoldsmithLog.class)) {
      new GoldsmithHitLoader()
          .load(
              yaml(
                  "badType:\n"
                      + "  type: missing\n"
                      + "  tool: x\n"
                      + "noTool:\n"
                      + "  type: gold\n"
                      + "hit:\n"
                      + "  type: gold\n"
                      + "  tool: m.hammer\n"));
      assertEquals(Set.of("hit"), GoldsmithHitLoader.get().keySet());
      new GoldsmithMaterialLoader()
          .load(
              yaml(
                  "noPath:\n"
                      + "  name: Bad\n"
                      + "gold:\n"
                      + "  path: m.gold\n"
                      + "  hits: [hit.2, hit.3, invalid, .2, 'hit.', hit.zero, hit.0, hit.-1,"
                      + " unknown.3]\n"));
      assertEquals(Set.of("gold"), GoldsmithMaterialLoader.get().keySet());
      assertEquals(
          5,
          GoldsmithMaterialLoader.getByString("gold")
              .getHits()
              .get(GoldsmithHitLoader.getByString("hit")));
      new JewelryProjectLoader()
          .load(
              yaml(
                  "scalar: 1\n"
                      + "noItem:\n"
                      + "  name: Bad\n"
                      + "ring:\n"
                      + "  item: m.ring\n"
                      + "  recipe: [gold.1, gold.2, invalid, .2, 'gold.', gold.zero, gold.0,"
                      + " gold.-1, unknown.3]\n"
                      + "blankTier:\n"
                      + "  item: m.ring\n"
                      + "  tier: ' '\n"
                      + "gemNoTier:\n"
                      + "  item: m.ring\n"
                      + "  gem: 1\n"));
      assertEquals(
          Set.of("ring", "blankTier", "gemNoTier"), JewelryProjectLoader.get().keySet());
      assertEquals("greater", JewelryProjectLoader.getByString("gemNoTier").getTierId());
      assertTrue(JewelryProjectLoader.getByString("gemNoTier").requiresGem());
      JewelryProject ring = JewelryProjectLoader.getByString("ring");
      assertEquals("greater", ring.getTierId());
      assertEquals("greater", JewelryProjectLoader.getByString("blankTier").getTierId());
      assertFalse(ring.requiresGem());
      assertEquals(Map.of("gold", 3), ring.getMaterialsByType());
    }
  }

  @Test
  void nullListEntriesAreRejectedByModelParsers() {
    ConfigurationSection c = mock(ConfigurationSection.class);
    when(c.getString("name", "gold")).thenReturn("Gold");
    when(c.getString("type", "gold")).thenReturn("gold");
    when(c.getStringList("hits")).thenReturn(Arrays.asList((String) null));
    try (var log = mockStatic(GoldsmithLog.class)) {
      assertTrue(new GoldsmithMaterial("gold", c).getHits().isEmpty());
      when(c.getStringList("recipe")).thenReturn(Arrays.asList((String) null));
      assertTrue(new JewelryProject("gold", c).getRecipe().isEmpty());
    }
  }

  @Test
  void itemLookupSkipsNonmatchingToolsAndMissingItems() throws Exception {
    basicDefinitions();
    ItemStack item = mock(ItemStack.class);
    when(item.getType()).thenReturn(Material.STICK);
    ItemAPI api = mock(ItemAPI.class, RETURNS_DEEP_STUBS);
    try (var libs = mockStatic(TLibs.class)) {
      libs.when(TLibs::getItemAPI).thenReturn(api);
      ItemStack air = mock(ItemStack.class);
      when(air.getType()).thenReturn(Material.AIR);
      assertNull(GoldsmithHitLoader.getByItem(null));
      assertNull(GoldsmithHitLoader.getByItem(air));
      assertNull(GoldsmithHitLoader.getByItem(item));
      when(api.getChecker().checkItemWithPath(item, "m.hammer")).thenReturn(true);
      assertSame(GoldsmithHitLoader.getByString("hit"), GoldsmithHitLoader.getByItem(item));
    }
  }

  @Test
  void tierDefaultsIgnoreSectionsAndResolveCaseInsensitively() throws Exception {
    try (var log = mockStatic(GoldsmithLog.class)) {
      new ProjectTierLoader().load(yaml("Major: 0.8\nsection:\n  nested: 2\n"));
      assertEquals(Map.of("major", .8), ProjectTierLoader.get());
      assertEquals(.8, ProjectTierLoader.getMultiplier("MAJOR"));
      assertEquals(1, ProjectTierLoader.getMultiplier(null));
      assertEquals(1, ProjectTierLoader.getMultiplier(" "));
      assertEquals(1, ProjectTierLoader.getMultiplier("missing"));
    }
  }

  @Test
  void qualityInterpolationHonorsThresholdsAndFinalTier() throws Exception {
    new QualityLoader()
        .load(
            yaml(
                "low:\n"
                    + "  amount: 0\n"
                    + "  value: 1\n"
                    + "  stat-min: 10\n"
                    + "  stat-max: 30\n"
                    + "high:\n"
                    + "  amount: 50\n"
                    + "  value: 2\n"
                    + "  stat-min: 40\n"
                    + "  stat-max: 80\n"));
    assertNull(QualityLoader.getByAmount(-1));
    assertEquals("low", QualityLoader.getByAmount(0).getId());
    assertEquals("high", QualityLoader.getByAmount(50).getId());
    assertEquals(20, QualityLoader.resolveStatFactor(25));
    assertEquals(60, QualityLoader.resolveStatFactor(75));
    assertEquals(1, QualityLoader.resolveStatFactor(-1));
    new QualityLoader()
        .load(yaml("perfect:\n  amount: 10\n  value: 1\n  stat-min: 100\n  stat-max: 100\n"));
    assertEquals(100, QualityLoader.resolveStatFactor(50));
  }

  @Test
  void removedQualityTierLeavesEqualOrInvertedCachedThresholdSafe() throws Exception {
    for (int nextAmount : new int[] {40, 50}) {
      new QualityLoader()
          .load(
              yaml(
                  "low:\n  amount: 50\n  value: 1\n  stat-min: 10\n  stat-max: 30\n"
                      + "high:\n  amount: "
                      + nextAmount
                      + "\n  value: 2\n  stat-min: 40\n  stat-max: 80\n"));
      // The public mutable registry can change independently of the cached sorted tiers.
      QualityLoader.get().remove("high");
      assertEquals("low", QualityLoader.getByAmount(50).getId());
      assertEquals(10, QualityLoader.resolveStatFactor(50));
      assertEquals(10, QualityLoader.resolveStatFactor(75));
    }
  }

  @Test
  void extremeConfiguredQualityValuesKeepDegenerateInterpolationFinite() throws Exception {
    for (int nextAmount : new int[] {40, 50}) {
      new QualityLoader()
          .load(
              yaml(
                  "top:\n  amount: 50\n  value: 2147483647\n  stat-min: 10\n  stat-max: 30\n"
                      + "wrapped:\n  amount: "
                      + nextAmount
                      + "\n  value: -2147483648\n  stat-min: 40\n  stat-max: 80\n"));
      assertEquals("top", QualityLoader.getByAmount(50).getId());
      // The next-value lookup wraps at MAX_VALUE even without registry mutations.
      assertEquals(10, QualityLoader.resolveStatFactor(50));
      assertEquals(10, QualityLoader.resolveStatFactor(75));
    }
  }

  @Test
  void mainGoldsmithConfigLoadsDefaultsOverridesAndBlankPermission() throws Exception {
    GoldsmithConfigLoader loader = new GoldsmithConfigLoader();
    loader.load(yaml("{}"));
    assertNull(GoldsmithCache.permission);
    assertEquals(10, GoldsmithCache.jewelryGemStatBoost);
    assertEquals(5, GoldsmithCache.jewelryGemStatBoostChance("common"));
    assertEquals(15, GoldsmithCache.jewelryGemStatBoostChance("RARE"));
    assertEquals(30, GoldsmithCache.jewelryGemStatBoostChance("epic"));
    assertEquals(50, GoldsmithCache.jewelryGemStatBoostChance("legendary"));
    assertEquals(0, GoldsmithCache.jewelryGemStatBoostChance("unknown"));
    assertEquals(0, GoldsmithCache.jewelryGemStatBoostChance(null));
    loader.load(yaml("permission: ' '\n"));
    assertNull(GoldsmithCache.permission);
    loader.load(
        yaml(
            "station: table\n"
                + "branding-tool: branding\n"
                + "permission: smith\n"
                + "jewelry-gem-stat-boost:\n"
                + "  amount: 12\n"
                + "  chances:\n"
                + "    common: -4\n"
                + "    legendary: 120\n"));
    assertEquals("table", GoldsmithCache.station);
    assertEquals("branding", GoldsmithCache.brandingTool);
    assertEquals("smith", GoldsmithCache.permission);
    assertEquals(12, GoldsmithCache.jewelryGemStatBoost);
    assertEquals(0, GoldsmithCache.jewelryGemStatBoostChance("common"));
    assertEquals(100, GoldsmithCache.jewelryGemStatBoostChance("legendary"));
    assertEquals(0, GoldsmithCache.jewelryGemStatBoostChance("rare"));
  }

  @Test
  void jewelryGemBoostOnlyImprovesOnSuccessfulRarityRollAndCapsAtOneHundred() {
    assertEquals(50, GoldsmithCache.applyJewelryGemStatBoost(50, "common", 5));
    assertEquals(50, GoldsmithCache.applyJewelryGemStatBoost(50, "unknown", 0));
    assertEquals(50, GoldsmithCache.applyJewelryGemStatBoost(50, null, 0));
    assertEquals(60, GoldsmithCache.applyJewelryGemStatBoost(50, "common", 4.99));
    GoldsmithCache.jewelryGemStatBoost = 25;
    assertEquals(100, GoldsmithCache.applyJewelryGemStatBoost(90, "legendary", 49.99));
  }

  @Test
  void infusionConfigReloadClearsOldStateAndResolvesCaseInsensitiveIds() throws Exception {
    YamlConfiguration c =
        config(
            "location_specific: true\n"
                + "infusion_item: STAFF.INFUSER\n"
                + "locations: ['1,2,3']\n"
                + "infusion_blocks: [enchanting_table]\n"
                + "gems:\n"
                + "  ruby:\n"
                + "    name: Ruby\n"
                + "    colour: RED\n"
                + "    socket_colour: Red\n"
                + "    socket_name_colour: GOLD\n"
                + "    gem: GEM_STONE.RUBY\n"
                + "    rarities:\n"
                + "      common:\n"
                + "        stat: ATTACK_DAMAGE\n"
                + "        min: 2\n"
                + "        max: 4\n"
                + "      bad: {}\n"
                + "rarities:\n"
                + "  common:\n"
                + "    name: Common\n"
                + "    chance: 1\n"
                + "    announce: true\n");
    new ConfigLoader().loadConfig(c);
    assertEquals(1, ConfigLoader.loadedGems.size());
    assertTrue(ConfigLoader.useLocations);
    assertEquals("STAFF.INFUSER", ConfigLoader.infusionStaff);
    assertEquals(List.of(Material.ENCHANTING_TABLE), ConfigLoader.stations);
    assertEquals(List.of("1,2,3"), ConfigLoader.locations);
    Gemstone gem = ConfigLoader.loadedGems.getFirst();
    assertEquals("ruby", gem.getId());
    assertEquals("Ruby", gem.getName());
    assertEquals("Red", gem.getSocketColour());
    assertEquals(ChatColor.RED, gem.getColour());
    assertEquals(ChatColor.GOLD, gem.getSocketNameColour());
    assertFalse(gem.isLocationSpecific());
    assertEquals("none", gem.getLocation());
    assertEquals(1, gem.getStats().size());
    assertEquals("ATTACK_DAMAGE", gem.getStats().getFirst().getStatId());
    assertSame(gem, ConfigLoader.findGemByMmoItem("gem_stone", "ruby"));
    assertNull(ConfigLoader.findGemByMmoItem(null, "ruby"));
    assertNull(ConfigLoader.findGemByMmoItem("GEM_STONE", null));
    Gemstone absent = new Gemstone();
    Gemstone malformed = new Gemstone();
    malformed.setMMOItem("missing");
    ConfigLoader.loadedGems.addFirst(absent);
    ConfigLoader.loadedGems.addFirst(malformed);
    assertNull(ConfigLoader.findGemByMmoItem("other", "ruby"));
    assertNull(ConfigLoader.findGemByMmoItem("GEM_STONE", "other"));
    assertSame(gem, ConfigLoader.findGemByMmoItem("GEM_STONE", "ruby"));
    GemRarity rarity = ConfigLoader.loadedRarities.getFirst();
    assertSame(rarity, ConfigLoader.findRarityById("COMMON"));
    assertNull(ConfigLoader.findRarityById(null));
    assertNull(ConfigLoader.findRarityById("other"));
    assertTrue(rarity.shouldAnnounce());
    assertEquals("Common", rarity.getName());
    assertEquals(1, rarity.getChance());
    c.set("gems.ruby.location_specific", true);
    c.set("gems.ruby.location", "1,2,3");
    c.set("rarities.common.announce", null);
    new ConfigLoader().loadConfig(c);
    assertEquals(1, ConfigLoader.loadedGems.size());
    assertTrue(ConfigLoader.loadedGems.getFirst().isLocationSpecific());
    assertEquals("1,2,3", ConfigLoader.loadedGems.getFirst().getLocation());
    assertFalse(ConfigLoader.loadedRarities.getFirst().shouldAnnounce());
  }

  @Test
  void cancelledBreakDispatcherPreservesStation() {
    var server = MockBukkit.mock();
    String previous = GoldsmithCache.station;
    try {
      var plugin = MockBukkit.createMockPlugin();
      var player = server.addPlayer();
      var block = server.addSimpleWorld("protected").getBlockAt(1, 2, 3);
      GoldsmithCache.station = "table";
      GoldsmithStationManager manager = new GoldsmithStationManager();
      GoldsmithStation station = manager.getOrCreate(block.getLocation());
      BlockAPI api = mock(BlockAPI.class, RETURNS_DEEP_STUBS);
      try (var libs = mockStatic(TLibs.class);
          var store = mockStatic(GoldsmithStationStore.class)) {
        libs.when(TLibs::getBlockAPI).thenReturn(api);
        when(api.getChecker().checkBlock(block, "table")).thenReturn(true);
        server.getPluginManager().registerEvents(manager, plugin);
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        assertSame(station, manager.get(block.getLocation()));
        store.verifyNoInteractions();
      }
    } finally {
      GoldsmithCache.station = previous;
      MockBukkit.unmock();
    }
  }
}
