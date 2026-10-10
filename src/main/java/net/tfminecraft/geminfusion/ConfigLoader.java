package net.tfminecraft.geminfusion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigLoader {
	public static List<Gemstone> loadedGems = new ArrayList<Gemstone>();
	public static List<GemRarity> loadedRarities = new ArrayList<GemRarity>();
	public static List<Material> stations = new ArrayList<Material>();
	public static List<String> locations = new ArrayList<String>();
	public static Boolean useLocations;
	public static String infusionStaff;
	
	public void loadConfig(FileConfiguration config) {
		loadedGems.clear();
		loadedRarities.clear();
		stations.clear();
		locations.clear();

		useLocations = config.getBoolean("location_specific");
		infusionStaff = config.getString("infusion_item");
		for(String s : config.getStringList("locations")) {
			locations.add(s);
		}
		for(String s : config.getStringList("infusion_blocks")) {
			stations.add(Material.valueOf(s.toUpperCase()));
		}
		Set<String> gemSet = config.getConfigurationSection("gems").getKeys(false);

		List<String> gemList = new ArrayList<String>(gemSet);
		for(String key : gemList) {
			loadedGems.add(getGemFromConfig(config, key));
		}
		Set<String> raritySet = config.getConfigurationSection("rarities").getKeys(false);

		List<String> rarityList = new ArrayList<String>(raritySet);
		for(String key : rarityList) {
			loadedRarities.add(new GemRarity(key, config.getConfigurationSection("rarities."+key)));
		}
		D20Roll.load(config.getConfigurationSection("d20"));
	}
	// Keep the existing legacy text representation, formatting, and exact-string comparisons.
	@SuppressWarnings("deprecation")
	public Gemstone getGemFromConfig(FileConfiguration config, String key) {
		Gemstone gem = new Gemstone();
		gem.setId(key);
		gem.setName(config.getConfigurationSection("gems."+key).getString("name"));
		if(config.getConfigurationSection("gems."+key).contains("location_specific")) {
			gem.setLocationSpecific(config.getConfigurationSection("gems."+key).getBoolean("location_specific"));
		} else {
			gem.setLocationSpecific(false);
		}
		if(config.getConfigurationSection("gems."+key).contains("location")) {
			gem.setLocation(config.getConfigurationSection("gems."+key).getString("location"));
		} else {
			gem.setLocation("none");
		}
		gem.setColour(ChatColor.valueOf(config.getConfigurationSection("gems."+key).getString("colour").toUpperCase()));
		gem.setSocketColour(config.getConfigurationSection("gems."+key).getString("socket_colour"));
		gem.setSocketNameColour(ChatColor.valueOf(config.getConfigurationSection("gems."+key).getString("socket_name_colour").toUpperCase()));
		gem.setMMOItem(config.getConfigurationSection("gems."+key).getString("gem"));
		Set<String> set = config.getConfigurationSection("gems."+key+".rarities").getKeys(false);

		List<String> list = new ArrayList<String>(set);
		for(String id : list) {
			GemStat stat = GemStat.parse(key, id, config.getConfigurationSection("gems."+key+".rarities."+id));
			if (stat != null) {
				gem.addStat(stat);
			}
		}
		return gem;
	}

	public static Gemstone findGemByMmoItem(String type, String id) {
		if (type == null || id == null) {
			return null;
		}
		for (Gemstone gem : loadedGems) {
			String mmo = gem.getMMOItemString();
			if (mmo == null) {
				continue;
			}
			String[] parts = mmo.split("\\.");
			if (parts.length < 2) {
				continue;
			}
			if (parts[0].equalsIgnoreCase(type) && parts[1].equalsIgnoreCase(id)) {
				return gem;
			}
		}
		return null;
	}

	public static GemRarity findRarityById(String id) {
		if (id == null) {
			return null;
		}
		for (GemRarity rarity : loadedRarities) {
			if (rarity.getId().equalsIgnoreCase(id)) {
				return rarity;
			}
		}
		return null;
	}
}
