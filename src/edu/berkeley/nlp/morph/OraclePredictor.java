package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;

import edu.berkeley.nlp.morph.fig.LogInfo;
import edu.berkeley.nlp.morph.util.Counter;

/**
 * Finds the set of predicted rule applications that produces a paradigm instance
 * closest to the gold. This would be easy except the gold isn't always reachable
 * and sometimes the set of changes used is slightly different (if the analysis is
 * ambiguous). To find the best in general, you have to manually inspect every possible
 * change sequence, which is exponential in the number of possible changes that can
 * coexist, so this requires a few clever pruning tricks to run quickly, like first
 * adding rules that exactly match gold rules and pruning those that don't individually
 * improve overall edit distance.
 * 
 * @author gdurrett
 *
 */
public class OraclePredictor implements Predictor {
  
  public final ExtractedModel extractedModel;
  public final ChangeFilterer changeFilterer;

  public OraclePredictor(ExtractedModel extractedModel) {
    this.extractedModel = extractedModel;
    this.changeFilterer = new ChangeFilterer(extractedModel, true);
  }
  
  @Override
  public ParadigmHypothesis predict(Form baseForm, SortedSet<Attributes> attrs, ParadigmInstance goldInstance) {
    List<AnchoredMorphChange> sortedMorphChanges = new ArrayList<AnchoredMorphChange>();
    for (MorphChange change : extractedModel.extractedMorphChanges.keySet()) {
      for (AnchoredSpan match : changeFilterer.findMatchingSpans(baseForm, change)) {
        sortedMorphChanges.add(new AnchoredMorphChange(change, match));
      }
    }
    // Sort the changes by ending index
    Collections.sort(sortedMorphChanges, new Comparator<AnchoredMorphChange>() {
      @Override
      public int compare(AnchoredMorphChange first, AnchoredMorphChange second) {
        return first.spanAppliedTo.end - second.spanAppliedTo.end;
      }
    });
    // Analyze the gold and commit to any proposed change that also appears in the gold; this could
    // only mess up under extremely tenuous circumstances (e.g. something like .->te,,,te, etc.
    // is committed to but that blocks using XX->YYet,YY,,,YYet, etc.)
    AnalyzedParadigmInstance analyzedInst = new AnalyzedParadigmInstance(goldInstance);
    analyzedInst.analyzeConsistent();
    List<AnchoredMorphChange> definiteChanges = analyzedInst.extractAndCacheChanges(true);
    definiteChanges.retainAll(sortedMorphChanges);
    List<AnchoredMorphChange> stillPossibleChanges = new ArrayList<AnchoredMorphChange>();
    for (AnchoredMorphChange sortedChange : sortedMorphChanges) {
      if (PredictionUtils.isCompatible(definiteChanges, sortedChange)) {
        stillPossibleChanges.add(sortedChange);
      }
    }
    // From morph changes that are left, prune those that don't improve edit distance
    List<AnchoredMorphChange> prunedMorphChanges = new ArrayList<AnchoredMorphChange>();
    int baselineEd = new ParadigmInstance(baseForm, attrs, definiteChanges).editDistanceToGold(goldInstance, true);
    for (AnchoredMorphChange anchoredChange : stillPossibleChanges) {
      List<AnchoredMorphChange> proposedChangesList = new ArrayList<AnchoredMorphChange>(definiteChanges);
      proposedChangesList.add(anchoredChange);
      ParadigmInstance instanceThisChangeApplied = new ParadigmInstance(baseForm, attrs, proposedChangesList);
      int edToGold = instanceThisChangeApplied.editDistanceToGold(goldInstance, true);
      if (edToGold <= baselineEd) {
        prunedMorphChanges.add(anchoredChange);
      }
    }
    stillPossibleChanges = prunedMorphChanges;
    if (stillPossibleChanges.size() >= 20) {
      LogInfo.logss("Lots of morph changes: " + sortedMorphChanges.size());
    }
    // Now enumerate all combinations
    List<List<AnchoredMorphChange>> allCombinations = PredictionUtils.getPossibleMorphChangeSets(stillPossibleChanges);
    if (allCombinations.size() >= 1000000) {
      LogInfo.logss("Too many combinations: " + allCombinations.size());
      Counter<AnchoredMorphChange> scores = new Counter<AnchoredMorphChange>();
      for (AnchoredMorphChange change : definiteChanges) {
        scores.setCount(change, 0.0);
      }
      return new ParadigmHypothesis(new ParadigmInstance(baseForm, attrs, definiteChanges), definiteChanges, scores, 0.0);
    } else {
      // Try all subsets and pick the best one
      ParadigmInstance bestInst = null;
      List<AnchoredMorphChange> bestChanges = null;
      int bestNumMatches = 0;
      for (List<AnchoredMorphChange> combination : allCombinations) {
        combination.addAll(definiteChanges);
        ParadigmInstance predInstance = new ParadigmInstance(baseForm, attrs, combination);
        int numMatches = predInstance.countMatchesGold(goldInstance, false);
        if (numMatches > bestNumMatches) {
          bestInst = predInstance;
          bestChanges = combination;
          bestNumMatches = numMatches;
        }
      }
      Counter<AnchoredMorphChange> scores = new Counter<AnchoredMorphChange>();
      for (AnchoredMorphChange change : bestChanges) {
        scores.setCount(change, 0.0);
      }
      return new ParadigmHypothesis(bestInst, bestChanges, scores, 0.0);
    }
  }
}
