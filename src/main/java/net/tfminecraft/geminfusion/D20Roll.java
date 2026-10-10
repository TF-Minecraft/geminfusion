package net.tfminecraft.geminfusion;

import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import net.Indyuce.mmocore.api.player.PlayerData;

/**
 * A d20 plus a modifier from the player's base attribute, like RPCharacters' /roll. A natural 20 always gives the
 * best result and a natural 1 always the worst, whatever the modifier.
 */
public final class D20Roll {

	/** The total that neither raises nor lowers the goldsmithing stat. */
	static final int NEUTRAL_TOTAL = 10;

	/** Rolls the die. Swapped by tests; {@code force-natural} in the config pins it for Dev checks. */
	public static IntSupplier die = D20Roll::rollDie;

	private static TreeMap<Integer, Integer> modifiers = defaultModifiers();
	private static int forcedNatural;
	public static String infusionAttribute = "intelligence";
	public static int flawlessDc = 20;
	public static double spreadPercent = 20;
	public static String goldsmithAttribute = "dexterity";
	public static int masterworkDc = 20;
	public static double percentPerPoint = 2;
	public static double maxPercent = 20;

	private final int natural;
	private final int modifier;

	public D20Roll(int natural, int modifier) {
		this.natural = natural;
		this.modifier = modifier;
	}

	public static D20Roll roll(Player player, String attributeId) {
		return new D20Roll(die.getAsInt(), modifierFor(player, attributeId));
	}

	public int getNatural() {
		return natural;
	}

	public int getModifier() {
		return modifier;
	}

	public boolean isCritical() {
		return natural >= 20;
	}

	public boolean isFumble() {
		return natural <= 1;
	}

	/** A natural 1 ignores the modifier. */
	public int getTotal() {
		return isFumble() ? natural : natural + modifier;
	}

	public boolean meets(int dc) {
		return isCritical() || (!isFumble() && getTotal() >= dc);
	}

	/** Where the roll lands from the worst result (0) to the best (1); totals at or above the DC reach the best. */
	public double position(int dc) {
		if (isCritical()) return 1;
		if (isFumble()) return 0;
		if (dc <= 1) return 1;
		return clamp((getTotal() - 1) / (double) (dc - 1), 0, 1);
	}

	/** Goldsmithing stat change in percent: percentPerPoint for each point away from 10, capped at maxPercent. */
	public double statPercent() {
		if (isCritical()) return maxPercent;
		if (isFumble()) return -maxPercent;
		return clamp((getTotal() - NEUTRAL_TOTAL) * percentPerPoint, -maxPercent, maxPercent);
	}

	/** How the roll reads in chat, e.g. "17 (+3) = 20". */
	public String describe() {
		if (isCritical()) return "§6Natural 20!";
		if (isFumble()) return "§cNatural 1!";
		return "§e" + natural + " §7(" + (modifier >= 0 ? "+" : "") + modifier + ") = §e" + getTotal();
	}

	public static int modifierFor(Player player, String attributeId) {
		Integer value = readBase(player, attributeId);
		return value == null ? 0 : modifier(value);
	}

	/** Values outside the table use its nearest end, so 25 reads like 20 and -3 like 0. */
	public static int modifier(int attributeValue) {
		int key = Math.max(modifiers.firstKey(), Math.min(modifiers.lastKey(), attributeValue));
		return modifiers.floorEntry(key).getValue();
	}

	/** The base attribute, without gear, as /roll reads it. Null when MMOCore cannot tell. */
	static Integer readBase(Player player, String attributeId) {
		if (player == null || attributeId == null || attributeId.isBlank()) return null;
		if (Bukkit.getPluginManager().getPlugin("MMOCore") == null) return null;
		try {
			return PlayerData.get(player).getAttributes().getInstance(attributeId).getBase();
		} catch (Exception ex) {
			return null;
		}
	}

	/** The lowest stat a gem of this rarity can roll: its min, spreadPercent lower. */
	public static double spreadBottom(GemStat block) {
		return round4(block.getMin() * (1 - spreadPercent / 100.0));
	}

	/** The highest stat a gem of this rarity can roll: its max, spreadPercent higher. Only a Flawless gem has it. */
	public static double spreadTop(GemStat block) {
		return round4(block.getMax() * (1 + spreadPercent / 100.0));
	}

	public static double gemStat(GemStat block, double position) {
		double bottom = spreadBottom(block);
		return round4(bottom + (spreadTop(block) - bottom) * position);
	}

	public static boolean isTop(GemStat block, double value) {
		return Math.abs(value - spreadTop(block)) < 0.00005;
	}

	public static void load(ConfigurationSection section) {
		modifiers = defaultModifiers();
		forcedNatural = 0;
		infusionAttribute = "intelligence";
		flawlessDc = 20;
		spreadPercent = 20;
		goldsmithAttribute = "dexterity";
		masterworkDc = 20;
		percentPerPoint = 2;
		maxPercent = 20;
		if (section != null) {
			ConfigurationSection table = section.getConfigurationSection("attribute-modifiers");
			if (table != null) {
				TreeMap<Integer, Integer> loaded = new TreeMap<>();
				for (String key : table.getKeys(false)) {
					try {
						loaded.put(Integer.parseInt(key.trim()), table.getInt(key));
					} catch (NumberFormatException ignored) {
						// Not an attribute value; skip it.
					}
				}
				if (!loaded.isEmpty()) modifiers = loaded;
			}
			forcedNatural = section.getInt("force-natural", 0);
			infusionAttribute = section.getString("infusion.attribute", infusionAttribute);
			flawlessDc = section.getInt("infusion.flawless-dc", flawlessDc);
			double spread = section.getDouble("infusion.spread-percent", spreadPercent);
			if (spread < 0 || spread >= 100) {
				// 100 or more would put the bottom of the spread at zero or below.
				double clamped = Math.max(0, Math.min(99, spread));
				warn("d20.infusion.spread-percent must be at least 0 and under 100, got " + spread + "; using " + clamped);
				spread = clamped;
			}
			spreadPercent = spread;
			goldsmithAttribute = section.getString("goldsmithing.attribute", goldsmithAttribute);
			masterworkDc = section.getInt("goldsmithing.masterwork-dc", masterworkDc);
			percentPerPoint = section.getDouble("goldsmithing.percent-per-point", percentPerPoint);
			maxPercent = section.getDouble("goldsmithing.max-percent", maxPercent);
		}
	}

	private static int rollDie() {
		if (forcedNatural >= 1 && forcedNatural <= 20) return forcedNatural;
		return ThreadLocalRandom.current().nextInt(1, 21);
	}

	/** The /roll table: (attribute - 10) / 2, rounded down, for 0 to 20. */
	private static TreeMap<Integer, Integer> defaultModifiers() {
		TreeMap<Integer, Integer> table = new TreeMap<>();
		for (int value = 0; value <= 20; value++) {
			table.put(value, Math.floorDiv(value - NEUTRAL_TOTAL, 2));
		}
		return table;
	}

	private static void warn(String message) {
		Logger logger = InfusionMain.plugin == null
				? Logger.getLogger("GemInfusion")
				: InfusionMain.plugin.getLogger();
		logger.warning(message);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double round4(double value) {
		return Math.round(value * 10000) / 10000.0;
	}
}
