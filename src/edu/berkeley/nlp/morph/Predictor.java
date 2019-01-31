package edu.berkeley.nlp.morph;

import java.util.SortedSet;

/**
 * Interface for inflecting base form with given sets of morphological attributes.
 * 
 * @author gdurrett
 *
 */
public interface Predictor {
  
  /**
   * @param baseForm
   * @param attrs
   * @param goldInstance The correct instance; used for oracle prediction and also useful for
   * error analysis or debugging, but generally leaving this as null is acceptable
   * @return
   */
  public ParadigmHypothesis predict(Form baseForm, SortedSet<Attributes> attrs, ParadigmInstance goldInstance);
}
