package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.List;

/**
 * Various utilities assorted with the prediction classes, most notably the ability
 * to enumerate all subsets of non-conflicting changes for a given base form.
 * 
 * @author gdurrett
 *
 */
public class PredictionUtils {

  public static List<List<AnchoredMorphChange>> getPossibleMorphChangeSets(List<AnchoredMorphChange> allChanges) {
    return getPossibleMorphChangeSets(allChanges, Integer.MAX_VALUE);
  }

  public static List<List<AnchoredMorphChange>> getPossibleMorphChangeSets(List<AnchoredMorphChange> allChanges, int maxSetSize) {
    List<List<AnchoredMorphChange>> allValidCombinations = new ArrayList<List<AnchoredMorphChange>>();
    getPossibleMorphChangeSetsHelper(allChanges, new ArrayList<AnchoredMorphChange>(), allValidCombinations, maxSetSize);
    return allValidCombinations;
  }
  
  private static void getPossibleMorphChangeSetsHelper(List<AnchoredMorphChange> remainingChanges,
                                                       List<AnchoredMorphChange> alreadyApplied,
                                                       List<List<AnchoredMorphChange>> allValidCombinations,
                                                       int maxSetSize) {
    allValidCombinations.add(alreadyApplied);
    if (alreadyApplied.size() < maxSetSize) {
      for (int i = 0; i < remainingChanges.size(); i++) {
        AnchoredMorphChange selectedChange = remainingChanges.get(i);
        if (isCompatible(alreadyApplied, selectedChange)) {
          List<AnchoredMorphChange> newRemainingChanges = remainingChanges.subList(i+1, remainingChanges.size());
          List<AnchoredMorphChange> newAlreadyApplied = new ArrayList<AnchoredMorphChange>(alreadyApplied);
          alreadyApplied.add(selectedChange);
          getPossibleMorphChangeSetsHelper(newRemainingChanges, newAlreadyApplied, allValidCombinations, maxSetSize);
        }
      }
    }
  }
  
  public static boolean isCompatible(List<AnchoredMorphChange> currentChanges, AnchoredMorphChange newChange) {
    for (AnchoredMorphChange change : currentChanges) {
      if (change.spanAppliedTo.doesIntersectOrTouch(newChange.spanAppliedTo)) {
        return false;
      }
    }
    return true;
  }
  
  public static <T> List<List<T>> getSubsets(List<T> objects) {
    List<List<T>> allSubsets = getNonEmptySubsets(objects);
    allSubsets.add(0, new ArrayList<T>());
    return allSubsets;
  }
  
  public static <T> List<List<T>> getNonEmptySubsets(List<T> objects) {
    List<List<T>> subsets = new ArrayList<List<T>>();
    for (int i = 1; i < Math.pow(2, objects.size()); i++) {
      List<T> currSubset = new ArrayList<T>();
      for (int j = 0; j < objects.size(); j++) {
        if ((i & ((int)Math.pow(2, j))) != 0) {
          currSubset.add(objects.get(j));
        }
      }
      subsets.add(currSubset);
    }
    return subsets;
  }
}
