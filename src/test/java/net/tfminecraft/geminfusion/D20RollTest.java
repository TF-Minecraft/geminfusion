package net.tfminecraft.geminfusion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.function.IntSupplier;
import net.Indyuce.mmocore.api.player.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

class D20RollTest {
  IntSupplier previousDie;

  @BeforeEach
  void setup() {
    previousDie = D20Roll.die;
    D20Roll.load(null);
  }

  @AfterEach
  void cleanup() {
    D20Roll.die = previousDie;
    D20Roll.load(null);
    if (MockBukkit.isMocked()) MockBukkit.unmock();
  }

  static YamlConfiguration config(String text) throws Exception {
    YamlConfiguration c = new YamlConfiguration();
    c.loadFromString(text);
    return c;
  }

  @Test
  void defaultModifiersMatchTheRollTableAndClampPastItsEnds() {
    assertEquals(-5, D20Roll.modifier(-3));
    assertEquals(-5, D20Roll.modifier(0));
    assertEquals(-5, D20Roll.modifier(1));
    assertEquals(-4, D20Roll.modifier(2));
    assertEquals(0, D20Roll.modifier(10));
    assertEquals(0, D20Roll.modifier(11));
    assertEquals(2, D20Roll.modifier(14));
    assertEquals(5, D20Roll.modifier(20));
    assertEquals(5, D20Roll.modifier(25));
  }

  @Test
  void naturalTwentyIsAlwaysBestAndNaturalOneAlwaysWorst() {
    D20Roll crit = new D20Roll(20, -5);
    assertTrue(crit.isCritical());
    assertFalse(crit.isFumble());
    assertEquals(15, crit.getTotal());
    assertTrue(crit.meets(20));
    assertEquals(1, crit.position(20));
    assertEquals(20, crit.statPercent());
    assertEquals("§6Natural 20!", crit.describe());
    D20Roll fumble = new D20Roll(1, 5);
    assertTrue(fumble.isFumble());
    assertFalse(fumble.isCritical());
    // The modifier is ignored, so a +5 cannot lift it.
    assertEquals(1, fumble.getTotal());
    assertFalse(fumble.meets(1));
    assertEquals(0, fumble.position(20));
    assertEquals(-20, fumble.statPercent());
    assertEquals("§cNatural 1!", fumble.describe());
    assertEquals(1, fumble.getNatural());
    assertEquals(5, fumble.getModifier());
  }

  @Test
  void otherRollsAddTheModifierAndScaleBetweenWorstAndBest() {
    D20Roll good = new D20Roll(15, 3);
    assertEquals(18, good.getTotal());
    assertTrue(good.meets(18));
    assertFalse(good.meets(19));
    assertEquals(17 / 19.0, good.position(20), 1e-9);
    assertEquals(1, good.position(1));
    assertEquals(16, good.statPercent());
    assertEquals("§e15 §7(+3) = §e18", good.describe());
    D20Roll poor = new D20Roll(12, -4);
    assertEquals(8, poor.getTotal());
    assertEquals(-4, poor.statPercent());
    assertEquals(7 / 19.0, poor.position(20), 1e-9);
    assertEquals("§e12 §7(-4) = §e8", poor.describe());
    assertEquals("§e10 §7(+0) = §e10", new D20Roll(10, 0).describe());
    D20Roll high = new D20Roll(19, 5);
    assertEquals(1, high.position(20));
    assertEquals(20, high.statPercent());
    D20Roll low = new D20Roll(2, -5);
    assertEquals(0, low.position(20));
    assertEquals(-20, low.statPercent());
  }

  @Test
  void gemStatSpreadsAroundTheRarityRangeAndOnlyTheTopIsFlawless() throws Exception {
    GemStat block = GemStat.parse("agate", "legendary", config("stat: max_health\nmin: 0.5\nmax: 0.6"));
    assertEquals(0.4, D20Roll.spreadBottom(block));
    assertEquals(0.72, D20Roll.spreadTop(block));
    assertEquals(0.4, D20Roll.gemStat(block, 0));
    assertEquals(0.72, D20Roll.gemStat(block, 1));
    assertEquals(0.56, D20Roll.gemStat(block, .5));
    assertTrue(D20Roll.isTop(block, 0.72));
    // The best a gem from before d20 infusion could reach.
    assertFalse(D20Roll.isTop(block, 0.7198));
  }

  @Test
  void rollsReadTheBaseAttributeThroughMmoCore() {
    var server = MockBukkit.mock();
    Player player = server.addPlayer();
    D20Roll.die = () -> 12;
    assertNull(D20Roll.readBase(null, "dexterity"));
    assertNull(D20Roll.readBase(player, null));
    assertNull(D20Roll.readBase(player, " "));
    assertNull(D20Roll.readBase(player, "dexterity"));
    D20Roll none = D20Roll.roll(player, "dexterity");
    assertEquals(12, none.getNatural());
    assertEquals(0, none.getModifier());
    MockBukkit.createMockPlugin("MMOCore");
    try (var data = mockStatic(PlayerData.class)) {
      data.when(() -> PlayerData.get(player)).thenThrow(new IllegalStateException("unavailable"));
      assertNull(D20Roll.readBase(player, "dexterity"));
      assertEquals(0, D20Roll.modifierFor(player, "dexterity"));
      PlayerData record = mock(PlayerData.class, RETURNS_DEEP_STUBS);
      when(record.getAttributes().getInstance("dexterity").getBase()).thenReturn(15);
      data.when(() -> PlayerData.get(player)).thenReturn(record);
      assertEquals(15, D20Roll.readBase(player, "dexterity"));
      assertEquals(2, D20Roll.modifierFor(player, "dexterity"));
      assertEquals(2, D20Roll.roll(player, "dexterity").getModifier());
    }
  }

  @Test
  void configOverridesDefaultsAndForcedNaturalPinsTheDie() throws Exception {
    D20Roll.load(
        config(
            "attribute-modifiers:\n  0: -2\n  10: 1\n  x: 9\n"
                + "force-natural: 7\n"
                + "infusion:\n  attribute: wisdom\n  flawless-dc: 18\n  spread-percent: 10\n"
                + "goldsmithing:\n  attribute: strength\n  masterwork-dc: 22\n"
                + "  percent-per-point: 3\n  max-percent: 30\n"));
    assertEquals(-2, D20Roll.modifier(0));
    assertEquals(-2, D20Roll.modifier(9));
    assertEquals(1, D20Roll.modifier(20));
    assertEquals("wisdom", D20Roll.infusionAttribute);
    assertEquals(18, D20Roll.flawlessDc);
    assertEquals(10, D20Roll.spreadPercent);
    assertEquals("strength", D20Roll.goldsmithAttribute);
    assertEquals(22, D20Roll.masterworkDc);
    assertEquals(3, D20Roll.percentPerPoint);
    assertEquals(30, D20Roll.maxPercent);
    assertEquals(7, D20Roll.die.getAsInt());
    // Out-of-range forced values and a table with no numbers fall back to real rolls and the default table.
    D20Roll.load(config("attribute-modifiers:\n  x: 9\nforce-natural: 25\n"));
    assertEquals(5, D20Roll.modifier(20));
    assertEquals("intelligence", D20Roll.infusionAttribute);
    assertEquals(20, D20Roll.masterworkDc);
    for (int i = 0; i < 200; i++) {
      int natural = D20Roll.die.getAsInt();
      assertTrue(natural >= 1 && natural <= 20, String.valueOf(natural));
    }
    // A spread of 100% or more would push the bottom to zero or below, so it is clamped with a warning.
    D20Roll.load(config("infusion:\n  spread-percent: 150\n"));
    assertEquals(99, D20Roll.spreadPercent);
    InfusionMain previous = InfusionMain.plugin;
    try {
      InfusionMain.plugin = mock(InfusionMain.class);
      var logger = mock(java.util.logging.Logger.class);
      when(InfusionMain.plugin.getLogger()).thenReturn(logger);
      D20Roll.load(config("infusion:\n  spread-percent: -5\n"));
      assertEquals(0, D20Roll.spreadPercent);
      verify(logger).warning(contains("spread-percent"));
    } finally {
      InfusionMain.plugin = previous;
    }
    D20Roll.load(config("{}"));
    assertEquals(-5, D20Roll.modifier(0));
    int natural = D20Roll.die.getAsInt();
    assertTrue(natural >= 1 && natural <= 20);
  }
}
