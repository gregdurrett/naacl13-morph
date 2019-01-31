package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;

import edu.berkeley.nlp.morph.fig.LogInfo;
import edu.berkeley.nlp.morph.util.Counter;
import edu.berkeley.nlp.morph.util.CounterMap;
import edu.berkeley.nlp.morph.fig.Pair;

/**
 * 
 * @author gdurrett
 *
 */
public class BaselinePredictor implements Predictor {
  
  public final ExtractedModel extractedModel;
  public final ChangeFilterer changeFilterer;
  public final List<Pair<Pattern,MorphChange>> prefixPatternsToConsider;
  public final List<Pair<Pattern,MorphChange>> suffixPatternsToConsider;
  
  public BaselinePredictor(ExtractedModel extractedModel) {
    this.extractedModel = extractedModel;
    // Always use match filtering
    this.changeFilterer = new ChangeFilterer(extractedModel, true);
    this.prefixPatternsToConsider = new ArrayList<Pair<Pattern,MorphChange>>();
    this.suffixPatternsToConsider = new ArrayList<Pair<Pattern,MorphChange>>();
    int numWithPrefixChange = 0;
    int numWithSuffixChange = 0;
    int totalCount = extractedModel.analyzedInstances.size();
    final CounterMap<Pattern,MorphChange> morphChangeCounts = new CounterMap<Pattern,MorphChange>();
    for (MorphChange change : extractedModel.extractedMorphChanges.keySet()) {
      Pattern pattern = changeFilterer.getFilterPattern(change);
      int numChangeAppliesTo = extractedModel.extractedMorphChanges.get(change).size();
      if (pattern.isStartPattern()) {
        morphChangeCounts.incrementCount(pattern, change, numChangeAppliesTo);
        numWithPrefixChange += numChangeAppliesTo;
      }
      if (pattern.isEndPattern()) {
        morphChangeCounts.incrementCount(pattern, change, numChangeAppliesTo);
        numWithSuffixChange += numChangeAppliesTo;
      }
    }
    for (Pattern pattern : morphChangeCounts.keySet()) {
      if (pattern.isStartPattern()) {
        prefixPatternsToConsider.add(Pair.makePair(pattern, morphChangeCounts.getCounter(pattern).argMax()));
      } else {
        suffixPatternsToConsider.add(Pair.makePair(pattern, morphChangeCounts.getCounter(pattern).argMax()));
      }
    }
    // Only use prefix and suffix changes if something should be changed most of the time
    if (numWithPrefixChange < totalCount * 0.5) {
      LogInfo.logss("Clearing prefix patterns");
      prefixPatternsToConsider.clear();
    }
    if (numWithSuffixChange < totalCount * 0.5) {
      LogInfo.logss("Clearing suffix patterns");
      suffixPatternsToConsider.clear();
    }
    Comparator<Pair<Pattern,MorphChange>> frequencyComparator = new Comparator<Pair<Pattern,MorphChange>>() {
      @Override
      public int compare(Pair<Pattern, MorphChange> o1, Pair<Pattern, MorphChange> o2) {
        // More frequent things should appear earlier, so return -1 if o1 is more frequent
        return (int)Math.signum(morphChangeCounts.getCount(o2.getFirst(), o2.getSecond()) -
                                morphChangeCounts.getCount(o1.getFirst(), o1.getSecond()));
      }
      
    };
    Collections.sort(prefixPatternsToConsider, frequencyComparator);
    Collections.sort(suffixPatternsToConsider, frequencyComparator);
    LogInfo.logss("PREFIX PATTERNS: " + prefixPatternsToConsider);
    LogInfo.logss("SUFFIX PATTERNS: " + suffixPatternsToConsider);
  }

  @Override
  public ParadigmHypothesis predict(Form baseForm, SortedSet<Attributes> attrs, ParadigmInstance goldInstance) {
    // Don't use oracle info
    return predict(baseForm, attrs);
  }

  public ParadigmHypothesis predict(Form baseForm, SortedSet<Attributes> attrs) {
    // Apply the most frequent prefix and suffix changes that apply
    List<AnchoredMorphChange> appliedChanges = new ArrayList<AnchoredMorphChange>();
    for (Pair<Pattern,MorphChange> patternAndChange : prefixPatternsToConsider) {
      List<AnchoredSpan> matches = patternAndChange.getFirst().findMatchingSpans(baseForm);
      if (!matches.isEmpty()) {
        assert matches.size() == 1;
        appliedChanges.add(new AnchoredMorphChange(patternAndChange.getSecond(), matches.get(0)));
        break;
      }
    }
    for (Pair<Pattern,MorphChange> patternAndChange : suffixPatternsToConsider) {
      List<AnchoredSpan> matches = patternAndChange.getFirst().findMatchingSpans(baseForm);
      if (!matches.isEmpty()) {
        assert matches.size() == 1;
        appliedChanges.add(new AnchoredMorphChange(patternAndChange.getSecond(), matches.get(0)));
        break;
      }
    }
    ParadigmInstance predictedInstance = new ParadigmInstance(baseForm, attrs, appliedChanges);
    return new ParadigmHypothesis(predictedInstance, appliedChanges, new Counter<AnchoredMorphChange>(), 0.0);
  }

}
