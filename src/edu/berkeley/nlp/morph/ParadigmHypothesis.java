package edu.berkeley.nlp.morph;

import java.util.List;

import edu.berkeley.nlp.morph.util.Counter;

/**
 * Predicted paradigm annotated with some extra information about how it was
 * produced: applied morphological changes, possibly with scores and with an
 * overall score.
 * 
 * @author gdurrett
 *
 */
public class ParadigmHypothesis {
  
  public final ParadigmInstance predictedInstance;
  public final List<AnchoredMorphChange> appliedChanges;
  public final Counter<AnchoredMorphChange> scoredProposedChanges;
  public final double score;
  
  public ParadigmHypothesis(ParadigmInstance predictedInstance,
                          List<AnchoredMorphChange> appliedChanges,
                          Counter<AnchoredMorphChange> scoredProposedChanges,
                          double score) {
    this.predictedInstance = predictedInstance;
    this.appliedChanges = appliedChanges;
    this.scoredProposedChanges = scoredProposedChanges;
    this.score = score;
  }
  
  public ParadigmHypothesis copyAndRescore(double newScore) {
    return new ParadigmHypothesis(predictedInstance, appliedChanges, scoredProposedChanges, newScore); 
  }
}
