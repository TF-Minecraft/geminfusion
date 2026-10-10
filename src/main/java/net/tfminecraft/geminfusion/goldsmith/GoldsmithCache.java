package net.tfminecraft.geminfusion.goldsmith;

import java.util.Map;
import java.util.Locale;

public final class GoldsmithCache {

	public static String station;
	public static String brandingTool;
	public static String permission;
	public static double jewelryGemStatBoost = 10.0;
	public static Map<String, Double> jewelryGemStatBoostChances = Map.of(
			"common", 5.0,
			"rare", 15.0,
			"epic", 30.0,
			"legendary", 50.0);

	public static double jewelryGemStatBoostChance(String rarityId) {
		if (rarityId == null) return 0;
		return jewelryGemStatBoostChances.getOrDefault(rarityId.toLowerCase(Locale.ROOT), 0.0);
	}

	public static double applyJewelryGemStatBoost(double statFactor, String rarityId, double roll) {
		double chance = jewelryGemStatBoostChance(rarityId);
		if (chance <= 0 || roll >= chance) return statFactor;
		return Math.min(100, statFactor + jewelryGemStatBoost);
	}

	private GoldsmithCache() {
	}
}
