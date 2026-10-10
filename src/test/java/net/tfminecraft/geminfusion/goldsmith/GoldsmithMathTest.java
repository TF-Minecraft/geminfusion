package net.tfminecraft.geminfusion.goldsmith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.tlibs.objects.utils.IntCounter;
import org.junit.jupiter.api.Test;

class GoldsmithMathTest {
  private final GoldsmithHit hit = mock(GoldsmithHit.class);

  private static IntCounter count(int value) {
    IntCounter c = new IntCounter();
    c.setCurrent(value);
    return c;
  }

  @Test
  void aggregatesMaterialHitsAndTypesWithoutExposingMutableResults() {
    GoldsmithHitType type = mock(GoldsmithHitType.class);
    when(hit.getType()).thenReturn(type);
    GoldsmithHit other = mock(GoldsmithHit.class);
    when(other.getType()).thenReturn(type);
    GoldsmithHit untyped = mock(GoldsmithHit.class);
    GoldsmithMaterial a = mock(GoldsmithMaterial.class), b = mock(GoldsmithMaterial.class);
    when(a.getHits()).thenReturn(Map.of(hit, 2, untyped, 1));
    when(b.getHits()).thenReturn(Map.of(hit, 3, other, 4));
    Map<GoldsmithMaterial, Integer> deposited = new LinkedHashMap<>();
    deposited.put(a, 2);
    deposited.put(b, 1);
    deposited.put(null, 5);
    assertEquals(Map.of(hit, 7, other, 4, untyped, 2), GoldsmithMath.requiredHits(deposited));
    assertEquals(Map.of(type, 11), GoldsmithMath.requiredHitsByType(deposited));
    assertThrows(
        UnsupportedOperationException.class, () -> GoldsmithMath.requiredHits(deposited).clear());
    for (Integer empty : Arrays.asList(null, -1, 0)) {
      deposited.clear();
      deposited.put(a, empty);
      assertTrue(GoldsmithMath.requiredHits(deposited).isEmpty());
    }
    assertTrue(GoldsmithMath.requiredHits(null).isEmpty());
    assertTrue(GoldsmithMath.requiredHits(Map.of()).isEmpty());
  }

  @Test
  void recipePercentWeightsIngredientsEquallyAndCapsExcess() {
    JewelryProject project = mock(JewelryProject.class);
    GoldsmithMaterial a = mock(GoldsmithMaterial.class), b = mock(GoldsmithMaterial.class);
    assertEquals(0, GoldsmithMath.recipePercent(null, null));
    assertEquals(0, GoldsmithMath.recipePercent(project, null));
    when(project.getRecipe()).thenReturn(Map.of(a, 2, b, 4));
    assertEquals(0, GoldsmithMath.recipePercent(project, null));
    assertEquals(50, GoldsmithMath.recipePercent(project, Map.of(a, 1, b, 2)));
    assertEquals(100, GoldsmithMath.recipePercent(project, Map.of(a, 20, b, 40)));
    assertEquals(0, GoldsmithMath.recipePercent(project, Map.of(a, -1)));
    when(project.getRecipe()).thenReturn(Map.of(a, 0, b, -1));
    assertEquals(0, GoldsmithMath.recipePercent(project, Map.of()));
  }

  @Test
  void totalsIgnoreInvalidRequirementsAndClampOnlyTheCappedVariant() {
    GoldsmithHit other = mock(GoldsmithHit.class);
    Map<GoldsmithHit, Integer> required = new LinkedHashMap<>();
    required.put(hit, 4);
    required.put(other, null);
    Map<GoldsmithHit, IntCounter> current = new LinkedHashMap<>();
    current.put(hit, count(7));
    current.put(other, null);
    assertEquals(4, GoldsmithMath.totalHitNeeded(required));
    assertEquals(4, GoldsmithMath.totalHitCurrent(required, current));
    assertEquals(7, GoldsmithMath.totalHitCurrentRaw(required, current));
    assertEquals(7, GoldsmithMath.totalHitCurrentAll(current));
    for (Integer invalid : Arrays.asList(null, 0, -1)) {
      required.put(other, invalid);
      assertEquals(4, GoldsmithMath.totalHitNeeded(required));
    }
    for (Map<GoldsmithHit, IntCounter> counts :
        Arrays.asList(null, Map.<GoldsmithHit, IntCounter>of(), Map.of(hit, count(-2)))) {
      assertEquals(0, GoldsmithMath.totalHitCurrent(required, counts));
      assertEquals(0, GoldsmithMath.totalHitCurrentRaw(required, counts));
      assertEquals(0, GoldsmithMath.totalHitCurrentAll(counts));
    }
    for (Map<GoldsmithHit, Integer> empty : Arrays.asList(null, Map.<GoldsmithHit, Integer>of())) {
      assertEquals(0, GoldsmithMath.totalHitNeeded(empty));
      assertEquals(0, GoldsmithMath.totalHitCurrent(empty, current));
      assertEquals(0, GoldsmithMath.totalHitCurrentRaw(empty, current));
    }
  }

  @Test
  void bothHitPercentApisApplyTriangularOverworkPenalty() {
    Map<GoldsmithHit, Integer> required = Map.of(hit, 10);
    int[] currents = {-1, 0, 5, 10, 15, 20, 25};
    double[] expected = {0, 0, 50, 100, 50, 0, 0};
    for (int i = 0; i < currents.length; i++) {
      assertEquals(
          expected[i], GoldsmithMath.hitPercent(required, Map.of(hit, count(currents[i]))));
      assertEquals(
          expected[i], GoldsmithMath.hitPercentFromCounts(required, Map.of(hit, currents[i])));
    }
    assertEquals(0, GoldsmithMath.hitPercent(required, null));
    assertEquals(0, GoldsmithMath.hitPercentFromCounts(required, null));
    assertEquals(0, GoldsmithMath.hitPercent(required, Map.of()));
    assertEquals(0, GoldsmithMath.hitPercentFromCounts(required, Map.of()));
    for (Map<GoldsmithHit, Integer> empty :
        Arrays.asList(null, Map.<GoldsmithHit, Integer>of(), Map.of(hit, 0), Map.of(hit, -1))) {
      assertEquals(0, GoldsmithMath.hitPercent(empty, null));
      assertEquals(0, GoldsmithMath.hitPercentFromCounts(empty, null));
    }
    Map<GoldsmithHit, Integer> nullRequired = new HashMap<>();
    nullRequired.put(hit, null);
    assertEquals(0, GoldsmithMath.hitPercent(nullRequired, null));
    assertEquals(0, GoldsmithMath.hitPercentFromCounts(nullRequired, null));
    GoldsmithHit other = mock(GoldsmithHit.class);
    assertEquals(
        75,
        GoldsmithMath.hitPercent(
            Map.of(hit, 10, other, 2), Map.of(hit, count(5), other, count(2))));
    assertEquals(
        75,
        GoldsmithMath.hitPercentFromCounts(Map.of(hit, 10, other, 2), Map.of(hit, 5, other, 2)));
  }

  @Test
  void finishUsesWeakerComponent() {
    assertEquals(40, GoldsmithMath.finishedTotal(80, 40));
    assertEquals(100, GoldsmithMath.finishedTotal(150, 200));
    assertEquals(0, GoldsmithMath.finishedTotal(-1, 50));
  }
}
