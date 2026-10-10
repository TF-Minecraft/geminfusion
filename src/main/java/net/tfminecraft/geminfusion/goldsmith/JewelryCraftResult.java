package net.tfminecraft.geminfusion.goldsmith;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.geminfusion.D20Roll;

public final class JewelryCraftResult {

	private final ItemStack item;
	private final double recipePercent;
	private final double hitPercent;
	private final double finishedTotal;
	private final double statCarryPercent;
	private final Quality quality;
	private final D20Roll craftRoll;

	public JewelryCraftResult(ItemStack item, double recipePercent, double hitPercent, double finishedTotal,
			double statCarryPercent, Quality quality) {
		this(item, recipePercent, hitPercent, finishedTotal, statCarryPercent, quality, null);
	}

	public JewelryCraftResult(ItemStack item, double recipePercent, double hitPercent, double finishedTotal,
			double statCarryPercent, Quality quality, D20Roll craftRoll) {
		this.item = item;
		this.recipePercent = recipePercent;
		this.hitPercent = hitPercent;
		this.finishedTotal = finishedTotal;
		this.statCarryPercent = statCarryPercent;
		this.quality = quality;
		this.craftRoll = craftRoll;
	}

	public ItemStack getItem() {
		return item;
	}

	public double getRecipePercent() {
		return recipePercent;
	}

	public double getHitPercent() {
		return hitPercent;
	}

	public double getFinishedTotal() {
		return finishedTotal;
	}

	public double getStatCarryPercent() {
		return statCarryPercent;
	}

	public Quality getQuality() {
		return quality;
	}

	/** The goldsmith's d20 roll, or null for gem-free pieces, which have no stat to roll for. */
	public D20Roll getCraftRoll() {
		return craftRoll;
	}
}
