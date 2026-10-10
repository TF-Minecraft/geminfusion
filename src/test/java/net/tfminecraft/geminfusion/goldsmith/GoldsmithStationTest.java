package net.tfminecraft.geminfusion.goldsmith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;

class GoldsmithStationTest {
  private final Location location = new Location(null, 1, 2, 3);
  private final GoldsmithStation station = new GoldsmithStation(location);
  private final JewelryProject project = mock(JewelryProject.class);
  private final GoldsmithMaterial material = mock(GoldsmithMaterial.class);
  private final GoldsmithHit hit = mock(GoldsmithHit.class);
  private final GoldsmithHitType type = mock(GoldsmithHitType.class);

  @BeforeEach
  void setup() {
    when(project.getMaterialsByType()).thenReturn(Map.of("gold", 1));
    when(project.getRecipe()).thenReturn(Map.of(material, 1));
    when(material.getType()).thenReturn("gold");
    when(material.getHits()).thenReturn(Map.of(hit, 2));
    when(hit.getType()).thenReturn(type);
  }

  private ItemStack stack() {
    ItemStack s = mock(ItemStack.class), copy = mock(ItemStack.class);
    when(s.clone()).thenReturn(copy);
    when(copy.clone()).thenReturn(copy);
    return s;
  }

  private void ready() {
    station.setProject(project);
    assertEquals(GoldsmithFeedback.SUCCESS, station.addMaterial(material, null));
  }

  @Test
  void emptyStationRejectsActionsAndProjectResetClearsAllState() {
    assertSame(location, station.getLoc());
    assertNull(station.getProject());
    assertFalse(station.hasProject());
    assertNull(station.getGem());
    assertFalse(station.hasGem());
    assertFalse(station.checkItems());
    assertEquals(GoldsmithFeedback.NO_PROJECT, station.addMaterial(material, null));
    assertEquals(GoldsmithFeedback.NO_PROJECT, station.addGem(null));
    assertEquals(GoldsmithFeedback.NO_PROJECT, station.hit(hit));
    assertEquals(GoldsmithFeedback.NO_PROJECT, station.canFinish());
    assertEquals(0, station.getRecipePercent());
    assertEquals(0, station.getFinishedTotal());
    station.setProject(project);
    assertTrue(station.hasProject());
    assertSame(project, station.getProject());
    assertFalse(station.checkItems());
    assertEquals(GoldsmithFeedback.LACKING_ITEMS, station.hit(hit));
    assertEquals(GoldsmithFeedback.LACKING_ITEMS, station.canFinish());
    assertEquals(GoldsmithFeedback.WRONG_TYPE, station.addMaterial(null, null));
    assertEquals(
        GoldsmithFeedback.WRONG_TYPE, station.addMaterial(mock(GoldsmithMaterial.class), null));
    station.setProject(null);
    assertTrue(station.getTypes().isEmpty());
  }

  @Test
  void materialDepositsCopyOneItemEnforceTypeCapacityAndRefundOnce() {
    station.setProject(project);
    ItemStack input = stack();
    assertEquals(GoldsmithFeedback.SUCCESS, station.addMaterial(material, input));
    verify(input.clone()).setAmount(1);
    assertEquals(List.of(input.clone()), station.getDeposited());
    assertEquals(Map.of(material, 1), station.getDepositedByMaterial());
    assertEquals(1, station.getTypes().get("gold").getCurrent());
    assertEquals(2, station.getTotalHitNeeded());
    assertEquals(100, station.getRecipePercent());
    assertEquals(2, station.getHitTypes().get(type).getNeeded());
    assertTrue(station.checkItems());
    assertEquals(GoldsmithFeedback.CAPACITY, station.addMaterial(material, input));
    assertThrows(UnsupportedOperationException.class, () -> station.getTypes().clear());
    assertThrows(UnsupportedOperationException.class, () -> station.getHits().clear());
    assertThrows(UnsupportedOperationException.class, () -> station.getHitTypes().clear());
    assertThrows(UnsupportedOperationException.class, () -> station.getDeposited().clear());
    assertThrows(
        UnsupportedOperationException.class, () -> station.getDepositedByMaterial().clear());
    assertEquals(List.of(input.clone()), station.cancel());
    assertTrue(station.cancel().isEmpty());
    assertFalse(station.hasProject());
    assertEquals(0, station.getTotalHitNeeded());
  }

  @Test
  void gemsRequireInfusionAreCopiedAndReturnedOnCancel() {
    ItemStack gem = stack();
    station.setProject(project);
    assertEquals(GoldsmithFeedback.WRONG_TYPE, station.addGem(gem));
    when(project.requiresGem()).thenReturn(true);
    assertEquals(GoldsmithFeedback.WRONG_TYPE, station.addGem(null));
    try (var validator = mockStatic(InfusedGemValidator.class)) {
      assertEquals(GoldsmithFeedback.NOT_INFUSED, station.addGem(gem));
      validator.when(() -> InfusedGemValidator.isInfused(gem)).thenReturn(true);
      validator.when(() -> InfusedGemValidator.isInfused(gem.clone())).thenReturn(true);
      assertEquals(GoldsmithFeedback.SUCCESS, station.addGem(gem));
      assertTrue(station.hasGem());
      assertSame(gem.clone(), station.getGem());
      assertEquals(GoldsmithFeedback.CAPACITY, station.addGem(gem));
      station.addMaterial(material, null);
      assertTrue(station.checkItems());
      station.hit(hit);
      assertEquals(GoldsmithFeedback.SUCCESS, station.canFinish());
      validator.when(() -> InfusedGemValidator.isInfused(gem.clone())).thenReturn(false);
      assertFalse(station.checkItems());
      assertEquals(GoldsmithFeedback.NOT_INFUSED, station.canFinish());
      assertEquals(List.of(gem.clone()), station.cancel());
    }
    ready();
    assertFalse(station.checkItems());
    assertEquals(GoldsmithFeedback.LACKING_ITEMS, station.canFinish());
  }

  @Test
  void anyHitCountCanBeFinishedAndOnlyChangesTheHitPercent() {
    ready();
    when(project.requiresGem()).thenReturn(true);
    try (var validator = mockStatic(InfusedGemValidator.class)) {
      validator.when(() -> InfusedGemValidator.isInfused(any())).thenReturn(true);
      station.addGem(stack());
      // No hits, too few and far too many all finish; the percent only sets the quality.
      assertEquals(0, station.getHitPercent());
      assertEquals(GoldsmithFeedback.SUCCESS, station.canFinish());
      assertEquals(GoldsmithFeedback.WRONG_TYPE, station.hit(null));
      assertEquals(GoldsmithFeedback.WRONG_TYPE, station.hit(mock(GoldsmithHit.class)));
      assertEquals(GoldsmithFeedback.SUCCESS, station.hit(hit));
      assertEquals(50, station.getHitPercent());
      assertEquals(50, station.getFinishedTotal());
      assertEquals(GoldsmithFeedback.SUCCESS, station.canFinish());
      station.hit(hit);
      assertEquals(100, station.getHitPercent());
      station.hit(hit);
      station.hit(hit);
      assertEquals(4, station.getTotalHitCount());
      assertEquals(4, station.getHitTypes().get(type).getCurrent());
      assertEquals(0, station.getHitPercent());
      assertEquals(GoldsmithFeedback.SUCCESS, station.canFinish());
      for (int i = 0; i < 20; i++) station.hit(hit);
      assertEquals(0, station.getHitPercent());
      assertEquals(GoldsmithFeedback.SUCCESS, station.canFinish());
    }
  }

  @Test
  void validUnneededToolsCountTowardTotalButNotRequiredHitPercent() {
    ready();
    GoldsmithHit other = mock(GoldsmithHit.class);
    when(other.getType()).thenReturn(mock(GoldsmithHitType.class));
    assertEquals(GoldsmithFeedback.SUCCESS, station.hit(other));
    assertEquals(1, station.getTotalHitCount());
    assertEquals(0, station.getHitPercent());
    station.hit(hit);
    station.hit(hit);
    assertEquals(100, station.getHitPercent());
    assertEquals(GoldsmithFeedback.RUINED, station.canFinish());
    when(project.requiresGem()).thenReturn(true);
    try (var validator = mockStatic(InfusedGemValidator.class)) {
      validator.when(() -> InfusedGemValidator.isInfused(any())).thenReturn(true);
      station.addGem(stack());
      assertEquals(GoldsmithFeedback.SUCCESS, station.canFinish());
    }
  }

  @Test
  void restoringProgressRebuildsCountersAndCopiesGem() {
    station.setProject(project);
    ItemStack gem = stack(), deposited = stack();
    GoldsmithMaterial unrelated = mock(GoldsmithMaterial.class);
    when(unrelated.getType()).thenReturn("silver");
    GoldsmithHit other = mock(GoldsmithHit.class), untyped = mock(GoldsmithHit.class);
    when(other.getType()).thenReturn(mock(GoldsmithHitType.class));
    Map<String, Integer> materials = new LinkedHashMap<>();
    materials.put("gold", 1);
    materials.put("unknown", 1);
    materials.put("null", null);
    materials.put("silver", -1);
    Map<String, Integer> hits = new LinkedHashMap<>();
    hits.put("hit", 2);
    hits.put("other", 3);
    hits.put("untyped", 1);
    hits.put("unknown", 1);
    hits.put("null", null);
    try (var ml = mockStatic(GoldsmithMaterialLoader.class);
        var hl = mockStatic(GoldsmithHitLoader.class);
        var validator = mockStatic(InfusedGemValidator.class);
        var log = mockStatic(GoldsmithLog.class)) {
      ml.when(() -> GoldsmithMaterialLoader.getByString("gold")).thenReturn(material);
      ml.when(() -> GoldsmithMaterialLoader.getByString("null")).thenReturn(material);
      ml.when(() -> GoldsmithMaterialLoader.getByString("silver")).thenReturn(unrelated);
      hl.when(() -> GoldsmithHitLoader.getByString("hit")).thenReturn(hit);
      hl.when(() -> GoldsmithHitLoader.getByString("null")).thenReturn(hit);
      hl.when(() -> GoldsmithHitLoader.getByString("other")).thenReturn(other);
      hl.when(() -> GoldsmithHitLoader.getByString("untyped")).thenReturn(untyped);
      validator.when(() -> InfusedGemValidator.isInfused(gem)).thenReturn(true);
      station.applySavedProgress(materials, hits, List.of(deposited), gem);
      assertSame(gem.clone(), station.getGem());
      assertEquals(100, station.getHitPercent());
      assertEquals(6, station.getTotalHitCount());
      assertEquals(2, station.getHitTypes().get(type).getCurrent());
      assertEquals(List.of(deposited), station.getDeposited());
      assertEquals(0, station.getDepositedByMaterial().get(unrelated));
      station.getTypes().get("gold").setNeeded(2);
      assertEquals(GoldsmithFeedback.SUCCESS, station.addMaterial(material, null));
      assertEquals(2, station.getHitTypes().get(type).getCurrent());
      station.applySavedProgress(null, null, null, null);
      assertFalse(station.hasGem());
      assertTrue(station.getDeposited().isEmpty());
      assertEquals(0, station.getTotalHitCount());
      validator.when(() -> InfusedGemValidator.isInfused(gem)).thenReturn(false);
      station.applySavedProgress(null, null, null, gem);
      assertFalse(station.hasGem());
      log.verify(() -> GoldsmithLog.warn(contains("Skipped restoring non-infused gem")));
    }
  }

  @Test
  void additionalMaterialRecomputesRequirementsWithoutLosingExistingHits() {
    when(project.getMaterialsByType()).thenReturn(Map.of("gold", 2));
    station.setProject(project);
    station.addMaterial(material, null);
    station.addMaterial(material, null);
    assertEquals(4, station.getHits().get(hit).getNeeded());
    assertEquals(4, station.getHitTypes().get(type).getNeeded());
  }

  @Test
  void gemFreeProjectsAreRuinedWithoutAPerfectRecipeAndHits() {
    GoldsmithMaterial shiny = mock(GoldsmithMaterial.class);
    when(shiny.getType()).thenReturn("gold");
    when(shiny.getHits()).thenReturn(Map.of(hit, 2));
    when(project.getMaterialsByType()).thenReturn(Map.of("gold", 2));
    when(project.getRecipe()).thenReturn(Map.of(material, 1, shiny, 1));
    station.setProject(project);
    station.addMaterial(material, null);
    station.addMaterial(material, null);
    // An unworked piece can be finished, but it is ruined, so checking a mix still costs the gold.
    assertEquals(GoldsmithFeedback.RUINED, station.canFinish());
    for (int i = 0; i < 4; i++) station.hit(hit);
    assertEquals(50, station.getRecipePercent());
    assertEquals(GoldsmithFeedback.RUINED, station.canFinish());
    when(project.requiresGem()).thenReturn(true);
    try (var validator = mockStatic(InfusedGemValidator.class)) {
      validator.when(() -> InfusedGemValidator.isInfused(any())).thenReturn(true);
      station.addGem(stack());
      assertEquals(GoldsmithFeedback.SUCCESS, station.canFinish());
    }
    when(project.requiresGem()).thenReturn(false);
    station.cancel();
    station.setProject(project);
    station.addMaterial(material, null);
    station.addMaterial(shiny, null);
    assertEquals(GoldsmithFeedback.RUINED, station.canFinish());
    for (int i = 0; i < 3; i++) station.hit(hit);
    assertEquals(75, station.getHitPercent());
    assertEquals(GoldsmithFeedback.RUINED, station.canFinish());
    station.hit(hit);
    assertEquals(100, station.getRecipePercent());
    assertEquals(GoldsmithFeedback.SUCCESS, station.canFinish());
    station.hit(hit);
    assertEquals(GoldsmithFeedback.RUINED, station.canFinish());
    when(project.requiresGem()).thenReturn(true);
    try (var validator = mockStatic(InfusedGemValidator.class)) {
      validator.when(() -> InfusedGemValidator.isInfused(any())).thenReturn(true);
      station.addGem(stack());
      assertEquals(GoldsmithFeedback.SUCCESS, station.canFinish());
    }
  }
}
