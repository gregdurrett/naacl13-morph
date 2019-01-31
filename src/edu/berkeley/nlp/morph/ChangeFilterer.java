package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.berkeley.nlp.morph.Pattern.PatternElement;
import edu.berkeley.nlp.morph.fig.LogInfo;

/**
 * Stores patterns for filtering potential matches of MorphChanges. When useMatchFiltering
 * is false, we get the simplest filter, which just requires that a MorphChange match only
 * where its source side is present. The more stringent matcher (useMatchFiltering = true)
 * further requires that an additional character of context be present on either side if it
 * was present everywhere in the training set. This greatly reduces the number of matches of
 * insertion-only patterns (German "ge" insertion in the past tense, for example). See
 * Section 4 in Durrett and DeNero (2013) for more details.
 * 
 * @author gdurrett
 *
 */
public class ChangeFilterer {
  private final Map<MorphChange,Pattern> morphChangeFilterPatterns;
  
  public ChangeFilterer(ExtractedModel extractedModel, boolean useMatchFiltering) {
    Set<MorphChange> morphChanges = new HashSet<MorphChange>();
    for (AnalyzedParadigmInstance analyzedInstance: extractedModel.analyzedInstances) {
      for (AnchoredMorphChange change : analyzedInstance.getCachedChanges()) {
        morphChanges.add(change.change);
      }
    }
    this.morphChangeFilterPatterns = new HashMap<MorphChange,Pattern>();
    for (MorphChange morphChange : morphChanges) {
      if (useMatchFiltering) {
        Glyph charBefore = null;
        boolean badBefore = false;
        Glyph charAfter = null;
        boolean badAfter = false;
        for (AnchoredMorphChange change : extractedModel.extractedMorphChanges.get(morphChange)) {
          Form form = change.spanAppliedTo.form;
          Glyph before = form.charAtOrBoundary(change.spanAppliedTo.start - 1);
          Glyph after = form.charAtOrBoundary(change.spanAppliedTo.end);
          if (!badBefore) {
            if (charBefore == null) {
              charBefore = before;
            } else if (!charBefore.equals(before)) {
              badBefore = true;
            }
          }
          if (!badAfter) {
            if (charAfter == null) {
              charAfter = after;
            } else if (!charAfter.equals(after)) {
              badAfter = true;
            }
          }
        }
        List<PatternElement> elts = new ArrayList<PatternElement>();
        if (!badBefore) {
          elts.add(new PatternElement(PatternType.BEFORE, charBefore));
        }
        if (!badAfter) {
          elts.add(new PatternElement(PatternType.AFTER, charAfter));
        }
        Pattern matchPattern = new Pattern(morphChange.base, elts);
        LogInfo.logss("Pattern for " + morphChange.toString() + ": " + matchPattern);
        morphChangeFilterPatterns.put(morphChange, matchPattern);
      }
      else {
        morphChangeFilterPatterns.put(morphChange, new Pattern(morphChange.base));
      }
    }
  }
  
  public Pattern getFilterPattern(MorphChange morphChange) {
    return morphChangeFilterPatterns.get(morphChange);
  }
  
  public List<AnchoredSpan> findMatchingSpans(Form baseForm, MorphChange morphChange) {
    if (!morphChangeFilterPatterns.containsKey(morphChange)) {
      throw new RuntimeException("Must call with a MorphChange that has been seen before");
    }
    Pattern thisChangePattern = morphChangeFilterPatterns.get(morphChange);
    return thisChangePattern.findMatchingSpans(baseForm);
  }
}
