package net.tfminecraft.geminfusion.goldsmith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.objects.utils.IntCounter;

public class GoldsmithStation {

	private final Location loc;
	private JewelryProject project;
	private ItemStack gem;

	private final LinkedHashMap<String, IntCounter> types = new LinkedHashMap<>();
	private final LinkedHashMap<GoldsmithHit, IntCounter> hits = new LinkedHashMap<>();
	private final LinkedHashMap<GoldsmithHitType, IntCounter> hitTypes = new LinkedHashMap<>();
	private final List<ItemStack> deposited = new ArrayList<>();
	private final LinkedHashMap<GoldsmithMaterial, Integer> depositedByMaterial = new LinkedHashMap<>();

	public GoldsmithStation(Location loc) {
		this.loc = loc;
	}

	public Location getLoc() {
		return loc;
	}

	public JewelryProject getProject() {
		return project;
	}

	public boolean hasProject() {
		return project != null;
	}

	public ItemStack getGem() {
		return gem;
	}

	public boolean hasGem() {
		return gem != null;
	}

	public Map<String, IntCounter> getTypes() {
		return Collections.unmodifiableMap(types);
	}

	public Map<GoldsmithHit, IntCounter> getHits() {
		return Collections.unmodifiableMap(hits);
	}

	public Map<GoldsmithHitType, IntCounter> getHitTypes() {
		return Collections.unmodifiableMap(hitTypes);
	}

	public List<ItemStack> getDeposited() {
		return Collections.unmodifiableList(deposited);
	}

	public Map<GoldsmithMaterial, Integer> getDepositedByMaterial() {
		return Collections.unmodifiableMap(depositedByMaterial);
	}

	public double getHitPercent() {
		return GoldsmithMath.hitPercent(GoldsmithMath.requiredHits(depositedByMaterial), hits);
	}

	public int getTotalHitCount() {
		return GoldsmithMath.totalHitCurrentAll(hits);
	}

	public int getTotalHitNeeded() {
		return GoldsmithMath.totalHitNeeded(GoldsmithMath.requiredHits(depositedByMaterial));
	}

	public double getRecipePercent() {
		return GoldsmithMath.recipePercent(project, depositedByMaterial);
	}

	public double getFinishedTotal() {
		return GoldsmithMath.finishedTotal(getRecipePercent(), getHitPercent());
	}

	public void setProject(JewelryProject project) {
		this.project = project;
		types.clear();
		hits.clear();
		hitTypes.clear();
		deposited.clear();
		depositedByMaterial.clear();
		gem = null;
		if (project == null) return;

		for (Map.Entry<String, Integer> e : project.getMaterialsByType().entrySet()) {
			IntCounter c = new IntCounter();
			c.setNeeded(e.getValue());
			types.put(e.getKey(), c);
		}
	}

	/**
	 * Applies saved counters and item copies after {@link #setProject(JewelryProject)}.
	 * Needed values stay on the live project; only currents, deposited stacks, and gem are restored.
	 */
	public void applySavedProgress(Map<String, Integer> materials, Map<String, Integer> hitCounts,
			List<ItemStack> savedDeposited, ItemStack savedGem) {
		deposited.clear();
		depositedByMaterial.clear();
		gem = null;
		hits.clear();
		hitTypes.clear();
		for (IntCounter c : types.values()) {
			c.setCurrent(0);
		}
		if (materials != null) {
			for (Map.Entry<String, Integer> e : materials.entrySet()) {
				GoldsmithMaterial material = GoldsmithMaterialLoader.getByString(e.getKey());
				if (material == null || e.getValue() == null) continue;
				int amount = Math.max(0, e.getValue());
				depositedByMaterial.put(material, amount);
				IntCounter bucket = types.get(material.getType());
				if (bucket != null) bucket.increaseCurrent(amount);
			}
		}
		recomputeRequiredHits();
		if (hitCounts != null) {
			for (Map.Entry<String, Integer> e : hitCounts.entrySet()) {
				GoldsmithHit hit = GoldsmithHitLoader.getByString(e.getKey());
				if (hit == null || e.getValue() == null) continue;
				IntCounter counter = hits.get(hit);
				if (counter == null) {
					counter = new IntCounter();
					hits.put(hit, counter);
				}
				counter.setCurrent(Math.max(0, e.getValue()));
			}
			syncHitTypeCurrents();
		}
		if (savedDeposited != null) {
			deposited.addAll(savedDeposited);
		}
		if (savedGem != null) {
			if (InfusedGemValidator.isInfused(savedGem)) {
				gem = savedGem.clone();
				gem.setAmount(1);
			} else {
				GoldsmithLog.warn("Skipped restoring non-infused gem on goldsmith station at "
						+ loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
			}
		}
	}

	public GoldsmithFeedback addMaterial(GoldsmithMaterial material, ItemStack stack) {
		if (project == null) return GoldsmithFeedback.NO_PROJECT;
		if (material == null) return GoldsmithFeedback.WRONG_TYPE;

		String type = material.getType();
		IntCounter bucket = types.get(type);
		if (bucket == null) return GoldsmithFeedback.WRONG_TYPE;
		if (bucket.isEqual()) return GoldsmithFeedback.CAPACITY;

		bucket.increaseCurrent(1);
		depositedByMaterial.merge(material, 1, Integer::sum);
		if (stack != null) {
			ItemStack copy = stack.clone();
			copy.setAmount(1);
			deposited.add(copy);
		}
		recomputeRequiredHits();
		return GoldsmithFeedback.SUCCESS;
	}

	public GoldsmithFeedback addGem(ItemStack stack) {
		if (project == null) return GoldsmithFeedback.NO_PROJECT;
		if (!project.requiresGem()) return GoldsmithFeedback.WRONG_TYPE;
		if (gem != null) return GoldsmithFeedback.CAPACITY;
		if (stack == null) return GoldsmithFeedback.WRONG_TYPE;
		if (!InfusedGemValidator.isInfused(stack)) return GoldsmithFeedback.NOT_INFUSED;
		ItemStack copy = stack.clone();
		copy.setAmount(1);
		gem = copy;
		return GoldsmithFeedback.SUCCESS;
	}

	public GoldsmithFeedback hit(GoldsmithHit hit) {
		if (project == null) return GoldsmithFeedback.NO_PROJECT;
		if (!checkItems()) return GoldsmithFeedback.LACKING_ITEMS;
		if (hit == null || hit.getType() == null) return GoldsmithFeedback.WRONG_TYPE;

		IntCounter hitCounter = hits.get(hit);
		if (hitCounter != null) {
			hitCounter.increaseCurrent(1);
		} else {
			IntCounter counter = new IntCounter();
			counter.setCurrent(1);
			hits.put(hit, counter);
		}
		IntCounter typeCounter = hitTypes.get(hit.getType());
		if (typeCounter != null) {
			typeCounter.increaseCurrent(1);
		}
		return GoldsmithFeedback.SUCCESS;
	}

	public boolean checkItems() {
		if (project == null) return false;
		for (IntCounter c : types.values()) {
			if (!c.isEqual()) return false;
		}
		if (project.requiresGem() && gem == null) return false;
		if (project.requiresGem() && !InfusedGemValidator.isInfused(gem)) return false;
		return true;
	}

	public GoldsmithFeedback canFinish() {
		if (project == null) return GoldsmithFeedback.NO_PROJECT;
		if (project.requiresGem() && gem != null && !InfusedGemValidator.isInfused(gem)) {
			return GoldsmithFeedback.NOT_INFUSED;
		}
		if (!checkItems()) return GoldsmithFeedback.LACKING_ITEMS;
		// Any number of hits can be finished; the hit percent only sets the quality.
		// Gem-free pieces have no quality to lose, so anything short of a perfect recipe and hits ruins them.
		// Hits with tools the piece does not need are only in the total, so compare it too.
		if (!project.requiresGem()
				&& (getRecipePercent() < 100 || getHitPercent() < 100 || getTotalHitCount() != getTotalHitNeeded())) {
			return GoldsmithFeedback.RUINED;
		}
		return GoldsmithFeedback.SUCCESS;
	}

	public List<ItemStack> cancel() {
		List<ItemStack> refund = new ArrayList<>(deposited);
		if (gem != null) refund.add(gem);
		project = null;
		gem = null;
		types.clear();
		hits.clear();
		hitTypes.clear();
		deposited.clear();
		depositedByMaterial.clear();
		return refund;
	}

	private void recomputeRequiredHits() {
		Map<GoldsmithHit, Integer> required = GoldsmithMath.requiredHits(depositedByMaterial);
		for (Map.Entry<GoldsmithHit, Integer> entry : required.entrySet()) {
			IntCounter counter = hits.get(entry.getKey());
			if (counter == null) {
				counter = new IntCounter();
				hits.put(entry.getKey(), counter);
			}
			counter.setNeeded(entry.getValue());
		}

		hitTypes.clear();
		for (Map.Entry<GoldsmithHitType, Integer> entry : GoldsmithMath.requiredHitsByType(depositedByMaterial).entrySet()) {
			IntCounter counter = new IntCounter();
			counter.setNeeded(entry.getValue());
			counter.setCurrent(sumHitCurrents(entry.getKey()));
			hitTypes.put(entry.getKey(), counter);
		}
	}

	private void syncHitTypeCurrents() {
		for (IntCounter counter : hitTypes.values()) {
			counter.setCurrent(0);
		}
		for (Map.Entry<GoldsmithHit, IntCounter> entry : hits.entrySet()) {
			GoldsmithHitType type = entry.getKey().getType();
			if (type == null) continue;
			IntCounter bucket = hitTypes.get(type);
			if (bucket != null) bucket.increaseCurrent(entry.getValue().getCurrent());
		}
	}

	private int sumHitCurrents(GoldsmithHitType type) {
		int total = 0;
		for (Map.Entry<GoldsmithHit, IntCounter> entry : hits.entrySet()) {
			if (type.equals(entry.getKey().getType())) {
				total += entry.getValue().getCurrent();
			}
		}
		return total;
	}
}
