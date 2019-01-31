package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a span of a particular form. Indices are fenceposts.
 * 
 * @author gdurrett
 *
 */
public class AnchoredSpan implements Comparable<AnchoredSpan> {
  
  public final Form form;
  public final int start;
  public final int end;
  // A hack that should only be used for rendering / error analysis
  public boolean scoredAtLeaf;
  
  public AnchoredSpan(Form form, int start, int end) {
    this.form = form;
    this.start = start;
    this.end = end;
  }
  
  public int length() {
    return end - start;
  }
  
  public AnchoredSpan union(AnchoredSpan other) {
    return new AnchoredSpan(this.form, Math.min(this.start, other.start), Math.max(this.end, other.end));
  }
  
  public boolean doesIntersect(AnchoredSpan other) {
    return this.start < other.end && this.end > other.start;
  }
  
  public boolean doesIntersectOrTouch(AnchoredSpan other) {
    return this.start <= other.end && this.end >= other.start;
  }
  
  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof AnchoredSpan)) {
      return false;
    }
    AnchoredSpan rhs = (AnchoredSpan)other;
    return this.form.equals(rhs.form) && this.start == rhs.start && this.end == rhs.end;
  }
  
  @Override
  public int hashCode() {
    int hc = this.form.hashCode() * 91229;
    hc = (hc + this.start) * 91229;
    hc = (hc + this.end) * 91229;
    return hc;
  }

  @Override
  public int compareTo(AnchoredSpan o) {
    if (!this.form.equals(o.form)) {
      return this.form.compareTo(o.form);
    }
    if (this.start < o.start) {
      return -1;
    } else if (this.start > o.start) {
      return 1;
    } else {
      return this.end - o.end;
    }
  }
  
  @Override
  public String toString() {
    return form.toString() + "(" + start + ", " + end + ")";
  }
  
  public static List<AnchoredSpan> getChangedSpans(Form baseForm, List<Operation> opSequence) {
    List<AnchoredSpan> anchoredSpans = new ArrayList<AnchoredSpan>();
    int srcIndex = 0;
    int currChangeStartIndex = -1;
    boolean inChangeSequence = false;
    for (Operation op : opSequence) {
      if (op != Operation.EQUAL && !inChangeSequence) {
        inChangeSequence = true;
        currChangeStartIndex = srcIndex;
      } else if (op == Operation.EQUAL && inChangeSequence) {
        // We're finishing a changing region
        inChangeSequence = false;
        anchoredSpans.add(new AnchoredSpan(baseForm, currChangeStartIndex, srcIndex));
      }
      // The source pointer advances with DELETE, SUBST, and EQUAL
      if (op != Operation.INSERT) {
        srcIndex++;
      }
    }
    // A suffix change won't see a switch back to EQUAL so add it here
    if (inChangeSequence) {
      anchoredSpans.add(new AnchoredSpan(baseForm, currChangeStartIndex, srcIndex));
    }
    return anchoredSpans;
  }
  
  /**
   * Collapses only overlapping spans
   * @param spans
   * @return
   */
  public static List<AnchoredSpan> collapseAllOverlapping(List<AnchoredSpan> spans) {
    return collapseAll(spans, false);
  }
  
  /**
   * Collapses overlapping and adjacent spans
   * @param spans
   * @return
   */
  public static List<AnchoredSpan> collapseAllTouching(List<AnchoredSpan> spans) {
    return collapseAll(spans, true);
  }
  
  /**
   * Not especially efficient, but collapses the given list of spans by iteratively
   * intersecting spans that overlap or touch (depending on the setting of
   * collapseAdjacentSpans).
   * @param spans
   * @param collapseAdjacentSpans
   * @return
   */
  public static List<AnchoredSpan> collapseAll(List<AnchoredSpan> spans, boolean collapseAdjacentSpans) {
    List<AnchoredSpan> intermediateSpans = new ArrayList<AnchoredSpan>(spans);
    List<AnchoredSpan> finalSpans = new ArrayList<AnchoredSpan>();
    OUTER: while (!intermediateSpans.isEmpty()) {
      AnchoredSpan currSpan = intermediateSpans.get(0);
      // Look at the first span and try to find candidates for intersecting
      for (int i = 1; i < intermediateSpans.size(); i++) {
        AnchoredSpan other = intermediateSpans.get(i);
        boolean shouldMerge = (collapseAdjacentSpans ? currSpan.doesIntersectOrTouch(other) : currSpan.doesIntersect(other));
        // Found two spans to merge. We have to recheck other spans in case the enlarging
        // of the first span made it intersect something else
        if (shouldMerge) {
          intermediateSpans.remove(i);
          intermediateSpans.set(0, currSpan.union(other));
          continue OUTER;
        }
      }
      // Nothing intersected so this span is done; add it to the final spans
      // and move on
      finalSpans.add(currSpan);
      intermediateSpans.remove(0);
    }
    return finalSpans;
  }
}
