package net.tfminecraft.geminfusion.goldsmith;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;

public class GoldsmithConfigLoader {

	public void load(File configFile) {
		FileConfiguration config = GoldsmithYaml.read(configFile);
		if (config == null) return;
		GoldsmithCache.station = config.getString("station");
		GoldsmithCache.brandingTool = config.getString("branding-tool");
		GoldsmithCache.permission = config.getString("permission");
		if (GoldsmithCache.permission != null && GoldsmithCache.permission.isBlank()) {
			GoldsmithCache.permission = null;
		}
		GoldsmithCache.jewelryGemStatBoost = Math.max(0,
				config.getDouble("jewelry-gem-stat-boost.amount", 10.0));
		Map<String, Double> chances = new HashMap<>();
		if (config.isConfigurationSection("jewelry-gem-stat-boost.chances")) {
			for (String rarity : config.getConfigurationSection("jewelry-gem-stat-boost.chances").getKeys(false)) {
				double chance = config.getDouble("jewelry-gem-stat-boost.chances." + rarity);
				chances.put(rarity.toLowerCase(Locale.ROOT), Math.max(0, Math.min(100, chance)));
			}
		}
		if (chances.isEmpty()) {
			chances.putAll(Map.of("common", 5.0, "rare", 15.0, "epic", 30.0, "legendary", 50.0));
		}
		GoldsmithCache.jewelryGemStatBoostChances = Map.copyOf(chances);
	}
}
