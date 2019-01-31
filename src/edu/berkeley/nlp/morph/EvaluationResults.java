package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.morph.util.Counter;
import edu.berkeley.nlp.morph.util.CounterMap;
import edu.berkeley.nlp.morph.util.GUtil;
import edu.berkeley.nlp.morph.util.Iterators;

/**
 * Sufficient statistics for all accuracy output and error analysis. Can be aggregated across
 * different experiments; this is done in the case of the Dreyer and Eisner data where results
 * are collected across 10 different train/test splits. 
 * 
 * @author gdurrett
 *
 */
public class EvaluationResults {

  public double numTemplatesCorrect = 0;
  public double numTemplates = 0;
  public double numTemplatesAchievable = 0;
  public double numTemplatesCorrectAllowStars = 0;
  public double numTemplatesAllowStars = 0;
  
  public double numFormsCorrect = 0;
  public double numForms = 0;
  public double numFormsCorrectAllowStars = 0;
  public double numFormsAllowStars = 0;
  
  public Counter<MorphChange> correctChangesCounts = new Counter<MorphChange>();
  public Counter<MorphChange> predChangesCounts = new Counter<MorphChange>();
  public Counter<MorphChange> goldChangesCounts = new Counter<MorphChange>();
  public Map<MorphChange,List<Form>> precErrors = new HashMap<MorphChange,List<Form>>();
  public Map<MorphChange,List<Form>> recErrors = new HashMap<MorphChange,List<Form>>();
  
  public CounterMap<MorphChange,MorphChange> precChangeConfusions = new CounterMap<MorphChange,MorphChange>();
  public CounterMap<MorphChange,MorphChange> recChangeConfusions = new CounterMap<MorphChange,MorphChange>();
  public CounterMap<MorphChange,String> precErrorEvents = new CounterMap<MorphChange,String>();
  public CounterMap<MorphChange,String> recErrorEvents = new CounterMap<MorphChange,String>();
  
  public EvaluationResults() {
  }
  
  public EvaluationResults(List<ParadigmInstance> predictions, List<ParadigmInstance> gold) {
    for (int i = 0; i < predictions.size(); i++) {
      ParadigmInstance predInstance = predictions.get(i);
      ParadigmInstance goldInstance = gold.get(i);
      boolean allCorrect = true;
      boolean hasStars = false;
      for (Attributes attrs : predInstance.getAttrSetSorted()) {
        Form predForm = predInstance.getInflForm(attrs);
//        Form goldForm = goldInstance.getInflForm(attrs);
        List<Form> goldForms = goldInstance.getAllInflForms(attrs);
        boolean correct = false;
        if (!goldInstance.isStar(attrs)) {
//          correct = predForm.equals(goldForm);
          correct = goldForms.contains(predForm);
          if (correct) {
            numFormsCorrect++;
          }
          numForms++;
        } else {
          hasStars = true;
          correct = true;
        }
        allCorrect = allCorrect && correct;
        if (correct) {
          numFormsCorrectAllowStars++;
        }
        numFormsAllowStars++;
      }
      if (!hasStars) {
        if (allCorrect) {
          numTemplatesCorrect++;
        }
        numTemplates++;
      }
      if (allCorrect) {
        numTemplatesCorrectAllowStars++;
      }
      numTemplatesAllowStars++;
    }
  }

  /**
   * We always evaluate against analyzed instances so that we can compare changes extracted
   * our changes to their changes
   * @param pred
   * @param gold
   */
  public EvaluationResults(ExtractedModel modelUsedForPrediction,
                           List<ParadigmHypothesis> pred,
                           List<AnalyzedParadigmInstance> goldAnalyzed) {
    List<MorphChange> changesInModelCapacity = modelUsedForPrediction.getChangesInModelCapacity();
    assert pred.size() == goldAnalyzed.size();
    GUtil.logsEveryNReset();
    for (int i = 0; i < pred.size(); i++) {
      GUtil.logsEveryN("Evaluating", 500);
      ParadigmHypothesis predResult = pred.get(i);
      ParadigmInstance predInstance = predResult.predictedInstance;
      List<AnchoredMorphChange> predChanges = predResult.appliedChanges;
      AnalyzedParadigmInstance goldInstanceAnalyzed = goldAnalyzed.get(i);
      ParadigmInstance goldInstance = goldInstanceAnalyzed.inst;
      List<AnchoredMorphChange> goldChanges = goldAnalyzed.get(i).extractAndCacheChanges(ExtractedModel.COLLAPSE_ADJACENT_SPANS);
      boolean allCorrect = true;
      boolean hasStars = false;
      for (Attributes attrs : predInstance.getAttrSetSorted()) {
        Form predForm = predInstance.getInflForm(attrs);
//        Form goldForm = goldInstance.getInflForm(attrs);
        List<Form> goldForms = goldInstance.getAllInflForms(attrs);
        boolean correct = false;
        if (!goldInstance.isStar(attrs)) {
//          correct = predForm.equals(goldForm);
          correct = goldForms.contains(predForm);
          if (correct) {
            numFormsCorrect++;
          }
          numForms++;
        } else {
          hasStars = true;
          correct = true;
        }
        allCorrect = allCorrect && correct;
        if (correct) {
          numFormsCorrectAllowStars++;
        }
        numFormsAllowStars++;
      }
      if (!hasStars) {
        if (allCorrect) {
          numTemplatesCorrect++;
        }
        numTemplates++;
      }
      if (allCorrect) {
        numTemplatesCorrectAllowStars++;
      }
      numTemplatesAllowStars++;
      // Precision / recall / F1 over individual changes
      // Only track changes on non-starred onces, since otherwise they're meaningless
      if (!hasStars) {
        boolean achievable = true;
        for (AnchoredMorphChange change : predChanges) {
          predChangesCounts.incrementCount(change.change, 1.0);
          if (goldChanges.contains(change)) {
            correctChangesCounts.incrementCount(change.change, 1.0);
          } else {
            addError(precErrors, change.change, goldInstance.baseForm());
            // Analyze the precision error
            // Why did we predict this? Was it because we predicted at a non-leaf?
            // Was it because the score was too low?
            boolean scoredAtLeaf = change.spanAppliedTo.scoredAtLeaf;
            precErrorEvents.incrementCount(change.change, "scoredAtLeaf? " + scoredAtLeaf, 1.0);
            // Who did we confuse this with?
            List<AnchoredMorphChange> confusions = change.getConflictingChanges(goldInstanceAnalyzed.extractAndCacheChanges(ExtractedModel.COLLAPSE_ADJACENT_SPANS));
            precErrorEvents.incrementCount(change.change, "numConfusions = " + confusions.size(), 1.0);
            if (confusions.size() == 1) {
              precChangeConfusions.incrementCount(change.change, confusions.get(0).change, 1.0);
            }
          }
        }
        for (AnchoredMorphChange change : goldChanges) {
          goldChangesCounts.incrementCount(change.change, 1.0);
          if (!predChanges.contains(change)) {
            addError(recErrors, change.change, goldInstance.baseForm());
            // Analyze the recall error
            // Why did we miss this? Was it predicted at a non-leaf?
            // Did it have a high enough score to pass the threshold?
            boolean scoreTooLow = predResult.scoredProposedChanges.getCount(change) < 1.0;
            recErrorEvents.incrementCount(change.change, "scoreTooLow? " + scoreTooLow, 1.0);
            // Who did we confuse this with?
            List<AnchoredMorphChange> confusions = change.getConflictingChanges(predResult.appliedChanges);
            recErrorEvents.incrementCount(change.change, "numConfusions = " + confusions.size(), 1.0);
            if (confusions.size() == 1) {
              recChangeConfusions.incrementCount(change.change, confusions.get(0).change, 1.0);
            }
          }
          if (!changesInModelCapacity.contains(change.change)) {
            achievable = false;
          }
        }
        if (achievable) {
          numTemplatesAchievable++;
        }
      }
    }
  }
  
  public void accumulate(EvaluationResults other) {
    this.numTemplatesCorrect += other.numTemplatesCorrect;
    this.numTemplates += other.numTemplates;
    this.numTemplatesAchievable += other.numTemplatesAchievable;
    this.numTemplatesCorrectAllowStars += other.numTemplatesCorrectAllowStars;
    this.numTemplatesAllowStars += other.numTemplatesAllowStars;
    this.numFormsCorrect += other.numFormsCorrect;
    this.numForms += other.numForms;
    this.numFormsCorrectAllowStars += other.numFormsCorrectAllowStars;
    this.numFormsAllowStars += other.numFormsAllowStars;
    this.correctChangesCounts.incrementAll(other.correctChangesCounts);
    this.predChangesCounts.incrementAll(other.predChangesCounts);
    this.goldChangesCounts.incrementAll(other.goldChangesCounts);
  }
  
  public String renderLong() {
    return render(true);
  }
  
  public String renderShort() {
    return render(false);
  }
  
  private String render(boolean isLong) {
    String result = "Templates correct: " + renderRatio(numTemplatesCorrect, numTemplates) + "\n";
    result += "Templates achievable: " + numTemplatesAchievable + "\n";
    result += "Templates correct (allowing stars): " + renderRatio(numTemplatesCorrectAllowStars, numTemplatesAllowStars) + "\n";
    result += "Forms correct: " + renderRatio(numFormsCorrect, numForms) + "\n";
    result += "Forms correct (allowing stars): " + renderRatio(numFormsCorrectAllowStars, numFormsAllowStars) + "\n";
    if (isLong) {
      result += "Change P/R/F1:\n";
      int numOneOffErrors = 0;
      for (MorphChange change : Iterators.able(goldChangesCounts.asPriorityQueue())) {
        double correctThisChange = this.correctChangesCounts.getCount(change);
        double predictedThisChange = this.predChangesCounts.getCount(change);
        double goldThisChange = this.goldChangesCounts.getCount(change);
        double prec = (predictedThisChange > 0 ? correctThisChange/predictedThisChange : 0);
        double rec = correctThisChange/goldThisChange;
        double f1 = (prec + rec == 0 ? 0 : 2 * prec * rec/(prec + rec));
        List<Form> precErrorsThisChange = (precErrors.get(change) == null ? new ArrayList<Form>() : precErrors.get(change));
        List<Form> recErrorsThisChange = (recErrors.get(change) == null ? new ArrayList<Form>() : recErrors.get(change));
        if (precErrorsThisChange.size() == 0 && recErrorsThisChange.size() <= 5) {
          numOneOffErrors++;
        }
        else {
          result += "  " + change.toString() + ":\n";
          result += "  " + renderRatio(correctThisChange, predictedThisChange) + ", " +
              renderRatio(correctThisChange, goldThisChange) + ", " + f1 + "\n";
          result += "    Precision errors = " + precErrorsThisChange.size() + ": " +
              precErrorsThisChange.subList(0, Math.min(10, precErrorsThisChange.size())) + "\n";
          result += "    Precision error events: " + precErrorEvents.getCounter(change).toString() + "\n";
          result += "    Precision confusions (should have picked X instead): " + precChangeConfusions.getCounter(change).toString() + "\n";
          result += "    Recall errors = " + recErrorsThisChange.size() + ": " +
              recErrorsThisChange.subList(0, Math.min(10, recErrorsThisChange.size())) + "\n";
          result += "    Recall error events: " + recErrorEvents.getCounter(change).toString() + "\n";
          result += "    Recall confusions (should have picked me instead): " + recChangeConfusions.getCounter(change).toString() + "\n";
        }
      }
      result += numOneOffErrors + " additional one-off errors";
    }
    return result;
  }
  
  public void addError(Map<MorphChange,List<Form>> errors, MorphChange change, Form error) {
    if (!errors.containsKey(change)) {
      errors.put(change, new ArrayList<Form>());
    }
    errors.get(change).add(error);
  }
  
  public String renderRatio(double numer, double denom) {
    return numer + "/" + denom + " = " + (denom > 0 ? ((double)numer)/((double)denom) : 0);
  }
}
