package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces features over anchored spans with the given maximum n-gram order and distance.
 * Currently only supports n-grams up to order 5 due to some hard-coding.
 * 
 * @author gdurrett
 *
 */
public class AnchoredSpanFeaturizer {
  
  private final int ngramOrder;
  private final int maxDistance;
  
  public AnchoredSpanFeaturizer(int ngramOrder, int maxDistance) {
    this.ngramOrder = ngramOrder;
    this.maxDistance = maxDistance;
  }

  public List<String> getFeatures(AnchoredSpan anchoredSpan) {
    List<String> features = new ArrayList<String>();
    for (int i = anchoredSpan.start - maxDistance; i < anchoredSpan.start; i++) {
      int offset = i - anchoredSpan.start;
      if (ngramOrder >= 1) {
        features.add("BUNI:" + offset + "-" + anchoredSpan.form.charAtOrBoundary(i));
      }
      if (ngramOrder >= 2) {
        features.add("BBI:" + offset + "-" + anchoredSpan.form.charAtOrBoundary(i) + anchoredSpan.form.charAtOrBoundary(i+1));
      }
      if (ngramOrder >= 3) {
        features.add("BTRI:" + offset + "-" + anchoredSpan.form.charAtOrBoundary(i) + anchoredSpan.form.charAtOrBoundary(i+1) + anchoredSpan.form.charAtOrBoundary(i+2));
      }
      if (ngramOrder >= 4) {
        features.add("BFOUR:" + offset + "-" + anchoredSpan.form.charAtOrBoundary(i) + anchoredSpan.form.charAtOrBoundary(i+1) + anchoredSpan.form.charAtOrBoundary(i+2) + anchoredSpan.form.charAtOrBoundary(i+3));
      }
      if (ngramOrder >= 5) {
        throw new RuntimeException("N-gram order 5 and above not supported");
      }
    }
    for (int i = anchoredSpan.end; i < anchoredSpan.end + maxDistance; i++) {
      int offset = i - anchoredSpan.end;
      if (ngramOrder >= 1) {
        features.add("AUNI:" + offset + "-" + anchoredSpan.form.charAtOrBoundary(i));
      }
      if (ngramOrder >= 2) {
        features.add("ABI:" + offset + "-" + anchoredSpan.form.charAtOrBoundary(i-1) + anchoredSpan.form.charAtOrBoundary(i));
      }
      if (ngramOrder >= 3) {
        features.add("ATRI:" + offset + "-" + anchoredSpan.form.charAtOrBoundary(i-2) + anchoredSpan.form.charAtOrBoundary(i-1) + anchoredSpan.form.charAtOrBoundary(i));
      }
      if (ngramOrder >= 4) {
        features.add("AFOUR:" + offset + "-" + anchoredSpan.form.charAtOrBoundary(i-3) + anchoredSpan.form.charAtOrBoundary(i-2) + anchoredSpan.form.charAtOrBoundary(i-1) + anchoredSpan.form.charAtOrBoundary(i));
      }
      if (ngramOrder >= 5) {
        throw new RuntimeException("N-gram order 5 and above not supported");
      }
    }
    return features;
  }
}
