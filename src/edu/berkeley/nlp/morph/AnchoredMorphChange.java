package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper around a morphological change that anchors it to a particular base form.
 * 
 * @author gdurrett
 *
 */
public class AnchoredMorphChange {

  public final MorphChange change;
  public final AnchoredSpan spanAppliedTo;
  // The following information should only be used for rendering / error analysis purposes, and
  // in general is allowed to be null
  public AnalyzedParadigmInstance instanceAppliedTo;
  
  public AnchoredMorphChange(MorphChange change, AnchoredSpan spanAppliedTo) {
    this(change, spanAppliedTo, null);
  }
  
  public AnchoredMorphChange(MorphChange change, AnchoredSpan spanAppliedTo, AnalyzedParadigmInstance instanceAppliedTo) {
    this.change = change;
    this.spanAppliedTo = spanAppliedTo;
    this.instanceAppliedTo = instanceAppliedTo;
  }
  
  public List<AnchoredMorphChange> getConflictingChanges(List<AnchoredMorphChange> otherChanges) {
    List<AnchoredMorphChange> conflictingChanges = new ArrayList<AnchoredMorphChange>();
    for (AnchoredMorphChange change : otherChanges) {
      if (this.spanAppliedTo.doesIntersect(change.spanAppliedTo)) {
        conflictingChanges.add(change);
      }
    }
    return conflictingChanges;
  }
  
  @Override
  public String toString() {
    return this.spanAppliedTo.toString() + ": " + this.change.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof AnchoredMorphChange)) {
      return false;
    }
    AnchoredMorphChange rhs = (AnchoredMorphChange)other;
    return this.change.equals(rhs.change) && this.spanAppliedTo.equals(rhs.spanAppliedTo);
  }
  
  @Override
  public int hashCode() {
    return change.hashCode() * 91229 + spanAppliedTo.hashCode();
  }
}
