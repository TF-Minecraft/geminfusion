package net.tfminecraft.geminfusion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.tfminecraft.geminfusion.goldsmith.GoldsmithLog;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class DomainHelpersTest {
  @AfterEach
  void cleanup() {
    MockBukkit.unmock();
  }

  YamlConfiguration config(String text) throws Exception {
    YamlConfiguration c = new YamlConfiguration();
    c.loadFromString(text);
    return c;
  }

  @Test
  void statParserAcceptsNewAndLegacyFormatsAndRejectsMalformedEntries() throws Exception {
    InfusionMain previous = InfusionMain.plugin;
    try {
      InfusionMain.plugin = null;
      assertNull(GemStat.parse("ruby", "rare", null));
      assertNull(GemStat.parse("ruby", "rare", config("{}")));
      GemStat stat = GemStat.parse("ruby", "rare", config("stat: ATTACK_DAMAGE\nmin: 2\nmax: 4"));
      assertEquals("rare", stat.getId());
      assertEquals("ATTACK_DAMAGE", stat.getStatId());
      assertEquals(2, stat.getMin());
      assertEquals(4, stat.getMax());
      assertEquals(
          2, GemStat.parse("ruby", "rare", config("stat: ATTACK_DAMAGE\nmin: 2")).getMax());
      assertNull(GemStat.parse("ruby", "rare", config("stat: ' '")));
      ConfigurationSection broken = mock(ConfigurationSection.class);
      when(broken.contains("stat")).thenReturn(true);
      assertNull(GemStat.parse("ruby", "rare", broken));
      when(broken.contains("stat")).thenReturn(false);
      when(broken.getStringList("stats")).thenReturn(null);
      assertNull(GemStat.parse("ruby", "rare", broken));
      for (String entry :
          Arrays.asList(
              null,
              "invalid",
              "(1-2)",
              "damage(1-2",
              "damage(2)",
              "damage(x-2)",
              "damage(1-x)",
              "damage(2)")) {
        when(broken.getStringList("stats")).thenReturn(Arrays.asList(entry));
        assertNull(GemStat.parse("ruby", "rare", broken));
      }
      stat = GemStat.parse("ruby", "rare", config("stats: ['damage(1-2)', 'ignored(2-3)']"));
      assertEquals("damage", stat.getStatId());
      assertEquals(1, stat.getMin());
      assertEquals(2, stat.getMax());
      InfusionMain.plugin = mock(InfusionMain.class);
      var logger = mock(java.util.logging.Logger.class);
      when(InfusionMain.plugin.getLogger()).thenReturn(logger);
      GemStat.parse("ruby", "rare", null);
      verify(logger).warning(anyString());
    } finally {
      InfusionMain.plugin = previous;
    }
  }

  @Test
  void gemAndStationAccessorsPreserveAssignedValuesAndLookupMmoTemplate() {
    MMOItems previous = MMOItems.plugin;
    try {
      MMOItems.plugin = mock(MMOItems.class, RETURNS_DEEP_STUBS);
      Gemstone gem = new Gemstone();
      gem.setMMOItem("gem_stone.ruby");
      MMOItem template = mock(MMOItem.class);
      when(MMOItems.plugin
              .getItems()
              .getMMOItem(MMOItems.plugin.getTypes().get("GEM_STONE"), "RUBY"))
          .thenReturn(template);
      assertSame(template, gem.getMMOItem());
      gem.addStat(null);
      assertTrue(gem.getStats().isEmpty());
      InfusionBlock block = new InfusionBlock();
      block.setToken(true);
      assertTrue(block.hasToken());
      block.setCurrentGems(new ArrayList<>(List.of(gem)));
      assertEquals(List.of(gem), block.getCurrentItems());
      GemRarity rarity = mock(GemRarity.class, CALLS_REAL_METHODS);
      rarity.setId("rare");
      rarity.setName("Rare");
      rarity.setChance(2);
      rarity.setAnnounce(true);
      assertEquals("rare", rarity.getId());
      assertEquals("Rare", rarity.getName());
      assertEquals(2, rarity.getChance());
      assertTrue(rarity.shouldAnnounce());
    } finally {
      MMOItems.plugin = previous;
    }
  }

  @Test
  void loggingUsesPluginLoggerOrFallback() {
    InfusionMain previous = InfusionMain.plugin;
    try {
      InfusionMain.plugin = null;
      assertNotNull(GoldsmithLog.get());
      GoldsmithLog.info("Coverage fallback logger");
      GoldsmithLog.warn("Coverage fallback warning");
      InfusionMain.plugin = mock(InfusionMain.class);
      var logger = mock(java.util.logging.Logger.class);
      when(InfusionMain.plugin.getLogger()).thenReturn(logger);
      GoldsmithLog.info("info");
      GoldsmithLog.warn("warning");
      verify(logger).info("info");
      verify(logger).warning("warning");
    } finally {
      InfusionMain.plugin = previous;
    }
  }
}
