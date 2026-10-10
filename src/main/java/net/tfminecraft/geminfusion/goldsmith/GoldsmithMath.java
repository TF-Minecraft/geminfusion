package net.tfminecraft.geminfusion.goldsmith;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.tfminecraft.tlibs.objects.utils.IntCounter;

public final class GoldsmithMath {

	private GoldsmithMath() {
	}

	public static Map<GoldsmithHit, Integer> requiredHits(Map<GoldsmithMaterial, Integer> deposited) {
		if (deposited == null || deposited.isEmpty()) {
			return Collections.emptyMap();
		}
		LinkedHashMap<GoldsmithHit, Integer> map = new LinkedHashMap<>();
		for (Map.Entry<GoldsmithMaterial, Integer> entry : deposited.entrySet()) {
			GoldsmithMaterial material = entry.getKey();
			int amount = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
			if (material == null || amount <= 0) continue;
			for (Map.Entry<GoldsmithHit, Integer> hit : material.getHits().entrySet()) {
				map.merge(hit.getKey(), hit.getValue() * amount, Integer::sum);
			}
		}
		return Collections.unmodifiableMap(map);
	}

	public static Map<GoldsmithHitType, Integer> requiredHitsByType(Map<GoldsmithMaterial, Integer> deposited) {
		LinkedHashMap<GoldsmithHitType, Integer> map = new LinkedHashMap<>();
		for (Map.Entry<GoldsmithHit, Integer> entry : requiredHits(deposited).entrySet()) {
			GoldsmithHitType type = entry.getKey().getType();
			if (type == null) continue;
			map.merge(type, entry.getValue(), Integer::sum);
		}
		return Collections.unmodifiableMap(map);
	}

	public static double recipePercent(JewelryProject project, Map<GoldsmithMaterial, Integer> deposited) {
		if (project == null) return 0;
		Map<GoldsmithMaterial, Integer> recipe = project.getRecipe();
		if (recipe.isEmpty()) return 0;

		double total = 0;
		for (Map.Entry<GoldsmithMaterial, Integer> entry : recipe.entrySet()) {
			int required = entry.getValue();
			if (required <= 0) continue;
			int have = deposited == null ? 0 : Math.max(0, deposited.getOrDefault(entry.getKey(), 0));
			total += Math.min(have, required) / (double) required;
		}
		return capPercent(total / recipe.size() * 100.0);
	}

	public static int totalHitNeeded(Map<GoldsmithHit, Integer> required) {
		if (required == null || required.isEmpty()) return 0;
		int needed = 0;
		for (Map.Entry<GoldsmithHit, Integer> entry : required.entrySet()) {
			int amount = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
			if (amount <= 0) continue;
			needed += amount;
		}
		return needed;
	}

	public static int totalHitCurrent(Map<GoldsmithHit, Integer> required, Map<GoldsmithHit, IntCounter> current) {
		if (required == null || required.isEmpty()) return 0;
		int done = 0;
		for (Map.Entry<GoldsmithHit, Integer> entry : required.entrySet()) {
			int lineNeeded = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
			if (lineNeeded <= 0) continue;
			IntCounter counter = current == null ? null : current.get(entry.getKey());
			int lineCurrent = counter == null ? 0 : Math.max(0, counter.getCurrent());
			done += Math.min(lineCurrent, lineNeeded);
		}
		return done;
	}

	public static int totalHitCurrentRaw(Map<GoldsmithHit, Integer> required, Map<GoldsmithHit, IntCounter> current) {
		if (required == null || required.isEmpty()) return 0;
		int done = 0;
		for (Map.Entry<GoldsmithHit, Integer> entry : required.entrySet()) {
			int lineNeeded = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
			if (lineNeeded <= 0) continue;
			IntCounter counter = current == null ? null : current.get(entry.getKey());
			done += counter == null ? 0 : Math.max(0, counter.getCurrent());
		}
		return done;
	}

	public static int totalHitCurrentAll(Map<GoldsmithHit, IntCounter> current) {
		if (current == null || current.isEmpty()) return 0;
		int done = 0;
		for (IntCounter counter : current.values()) {
			if (counter == null) continue;
			done += Math.max(0, counter.getCurrent());
		}
		return done;
	}

	public static double hitPercent(Map<GoldsmithHit, Integer> required, Map<GoldsmithHit, IntCounter> current) {
		return hitPercentPenalized(required, current);
	}

	public static double hitPercentPenalized(Map<GoldsmithHit, Integer> required, Map<GoldsmithHit, IntCounter> current) {
		if (required == null || required.isEmpty()) return 0;
		int lineCount = 0;
		double amount = 0;
		for (Map.Entry<GoldsmithHit, Integer> entry : required.entrySet()) {
			int lineNeeded = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
			if (lineNeeded <= 0) continue;
			lineCount++;
			IntCounter counter = current == null ? null : current.get(entry.getKey());
			int lineCurrent = counter == null ? 0 : Math.max(0, counter.getCurrent());
			double d = lineHitPercentage(lineCurrent, lineNeeded);
			if (d >= 200.0) continue;
			amount += d <= 100.0 ? d : 200.0 - d;
		}
		if (lineCount <= 0) return 0;
		return Math.round(amount / lineCount);
	}

	public static double hitPercentFromCounts(Map<GoldsmithHit, Integer> required, Map<GoldsmithHit, Integer> currentCounts) {
		if (required == null || required.isEmpty()) return 0;
		int lineCount = 0;
		double amount = 0;
		for (Map.Entry<GoldsmithHit, Integer> entry : required.entrySet()) {
			int lineNeeded = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
			if (lineNeeded <= 0) continue;
			lineCount++;
			int lineCurrent = currentCounts == null ? 0 : Math.max(0, currentCounts.getOrDefault(entry.getKey(), 0));
			double d = lineHitPercentage(lineCurrent, lineNeeded);
			if (d >= 200.0) continue;
			amount += d <= 100.0 ? d : 200.0 - d;
		}
		if (lineCount <= 0) return 0;
		return Math.round(amount / lineCount);
	}

	public static double finishedTotal(double recipePercent, double hitPercent) {
		return Math.min(capPercent(recipePercent), capPercent(hitPercent));
	}

	private static double lineHitPercentage(int current, int needed) {
		return Math.round((double) current / needed * 100.0);
	}

	private static double capPercent(double value) {
		if (value <= 0) return 0;
		if (value >= 100) return 100;
		return value;
	}
}
