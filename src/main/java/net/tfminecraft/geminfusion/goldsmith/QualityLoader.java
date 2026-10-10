package net.tfminecraft.geminfusion.goldsmith;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.bukkit.configuration.file.FileConfiguration;

public class QualityLoader {

	private static final HashMap<String, Quality> map = new HashMap<>();
	private static List<Quality> sortedByValue = List.of();

	public static HashMap<String, Quality> get() {
		return map;
	}

	public static Quality getByString(String id) {
		if (id == null) return null;
		return map.get(id);
	}

	public static Quality getByAmount(double score) {
		Quality best = null;
		int prev = -1;
		for (Quality quality : map.values()) {
			if (quality.isValid(score) && quality.getValue() > prev) {
				best = quality;
				prev = quality.getValue();
			}
		}
		return best;
	}

	public static double resolveStatFactor(double finishedTotal) {
		if (finishedTotal >= 100) {
			return 100;
		}
		Quality tier = getByAmount(finishedTotal);
		if (tier == null) {
			return 1;
		}
		if (tier.getStatMin() >= 100) {
			return 100;
		}
		Quality next = getByValue(tier.getValue() + 1);
		double upper = next == null ? 100 : next.getAmount();
		if (upper <= tier.getAmount()) {
			return clamp(tier.getStatMin(), tier.getStatMin(), tier.getStatMax());
		}
		double progress = (finishedTotal - tier.getAmount()) / (upper - tier.getAmount());
		double factor = tier.getStatMin() + progress * (tier.getStatMax() - tier.getStatMin());
		return clamp(factor, tier.getStatMin(), tier.getStatMax());
	}

	/** The tier just below the given one, or null for the lowest or an unknown tier. */
	public static Quality below(Quality quality) {
		if (quality == null) return null;
		Quality below = null;
		for (Quality candidate : sortedByValue) {
			if (candidate.getValue() < quality.getValue()) below = candidate;
		}
		return below;
	}

	private static Quality getByValue(int value) {
		for (Quality quality : sortedByValue) {
			if (quality.getValue() == value) {
				return quality;
			}
		}
		return null;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	public void load(File configFile) {
		map.clear();
		FileConfiguration config = GoldsmithYaml.read(configFile);
		if (config == null) {
			sortedByValue = List.of();
			return;
		}
		for (String key : config.getKeys(false)) {
			map.put(key, new Quality(key, config.getConfigurationSection(key)));
		}
		ArrayList<Quality> list = new ArrayList<>(map.values());
		list.sort(Comparator.comparingInt(Quality::getValue));
		sortedByValue = List.copyOf(list);
	}
}
