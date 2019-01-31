package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import edu.berkeley.nlp.morph.Attributes.Attribute;
import edu.berkeley.nlp.morph.GeneralLogisticRegression.Example;
import edu.berkeley.nlp.morph.fig.Indexer;
import edu.berkeley.nlp.morph.fig.LogInfo;
import edu.berkeley.nlp.morph.util.Counter;
import edu.berkeley.nlp.morph.util.SloppyMath;

/**
 * Fits a log-linear model to predict the frequency of each inflected variant
 * so that we can approximate a token-level evaluation with only type-level data
 * and corpus counts.
 * 
 * @author gdurrett
 *
 */
public class AttributeFrequencyEstimator {

  public final LmHandler lm;
  public final SortedSet<Attributes> attrSetSorted;
  public final Map<Form,List<ParadigmInstance>> inflectedFormsToInstances;
  public final Indexer<String> featureIndexer;
  public final Map<Attributes,int[]> featsCache;
  
  public AttributeFrequencyEstimator(LmHandler lm, SortedSet<Attributes> attrSetSorted, List<ParadigmInstance> instances) {
    this.lm = lm;
    this.attrSetSorted = attrSetSorted;
    this.inflectedFormsToInstances = new HashMap<Form,List<ParadigmInstance>>();
    LogInfo.logss("Populating forms map");
    for (ParadigmInstance instance : instances) {
      for (Attributes attrs : instance.getAttrSetSorted()) {
        Form form = instance.getInflForm(attrs);
        if (!inflectedFormsToInstances.containsKey(form)) {
          inflectedFormsToInstances.put(form, new ArrayList<ParadigmInstance>());
        }
        List<ParadigmInstance> observedInstances = inflectedFormsToInstances.get(form);
        boolean matches = false;
        for (int i = 0; i < observedInstances.size(); i++) {
          if (observedInstances.get(i).baseForm().equals(instance.baseForm())) {
            matches = true;
          }
        }
        if (!matches) {
          observedInstances.add(instance);
        }
      }
    }
    LogInfo.logss("Indexing features");
    this.featureIndexer = new Indexer<String>();
    this.featsCache = new HashMap<Attributes,int[]>();
    for (Attributes attrs : attrSetSorted) {
      List<String> feats = featurize(attrs);
      int[] featsIndexed = new int[feats.size()];
      for (int i = 0; i < feats.size(); i++) {
        featsIndexed[i] = featureIndexer.getIndex(feats.get(i));
      }
      featsCache.put(attrs, featsIndexed);
    }
  }
  
  public double[] train() {
    double[] weights = new double[featureIndexer.size()];
    final double reg = 0.00001;
//    final double reg = 0.1;
    final double eps = 0.01;
    final int numItrs = 30;
    List<FormExample> formExamples = new ArrayList<FormExample>();
    Collection<Form> allLmStrings = lm.getAllForms();
    LogInfo.logss("Creating training examples from an LM with " + allLmStrings.size() + " entries");
    for (Form lmForm : allLmStrings) {
      if (inflectedFormsToInstances.containsKey(lmForm)) {
        formExamples.add(new FormExample(lmForm, (int)lm.getCount(lmForm)));
      }
      
    }
    LogInfo.logss("Created " + formExamples.size() + " examples");
    new GeneralLogisticRegression().trainWeightsLbfgsL2R(formExamples, reg, eps, numItrs, weights);
    return weights;
  }
  
  public void evaluateAndPrint(List<ParadigmInstance> predInstances, List<ParadigmInstance> goldInstances, double[] weights) {
    assert predInstances.size() == goldInstances.size();
    double correctCount = 0;
    double totalCount = 0;
    for (int i = 0; i < predInstances.size(); i++) {
      ParadigmInstance predInst = predInstances.get(i);
      ParadigmInstance goldInst = goldInstances.get(i);
      boolean display = lm.getCount(goldInst.baseForm()) > 100;
      for (Attributes attrs : attrSetSorted) {
        Form predForm = predInst.getInflForm(attrs);
        Form goldForm = goldInst.getInflForm(attrs);
        // How many examples of the gold do we think there are? Figure out
        // the allocation of counts among the different Attributes in this
        // instance.
        double count = getPosterior(weights, goldInst, goldForm).getCount(attrs) * lm.getCount(goldForm);
        if (display) {
          LogInfo.logss((predForm.equals(goldForm) ? "***" : "---") + predForm + " " + goldForm + " " + count + " (total = " + lm.getCount(goldForm) + ")");
        }
        // Did we get them right?
        if (!goldForm.equals(ParadigmInstance.STAR_TOKEN)) {
          if (predForm.equals(goldForm)) {
            correctCount += count;
          }
          totalCount += count;
        }
      }
    }
    LogInfo.logss("OVERALL TOKEN-LEVEL ACCURACY: " + correctCount + " / " + totalCount + " = " + (correctCount/totalCount));
  }
  
  public Counter<Attributes> getNaivePosterior(double[] weights) {
    Counter<Attributes> counter = new Counter<Attributes>();
    for (Attributes attrs : attrSetSorted) {
      counter.incrementCount(attrs, Math.exp(score(weights, attrs)));
    }
    counter.normalize();
    return counter;
  }
  
  public double getNaivePosterior(double[] weights, Attributes targetAttrs) {
    Counter<Attributes> counter = new Counter<Attributes>();
    for (Attributes attrs : attrSetSorted) {
      counter.incrementCount(attrs, Math.exp(score(weights, attrs)));
    }
    counter.normalize();
    return counter.getCount(targetAttrs);
  }
  
  /**
   * Computes the posterior over a given form, assuming that all instances of the given form
   * arise from the given ParadigmInstance.
   * @param weights
   * @param instance
   * @param form
   * @return
   */
  public Counter<Attributes> getPosterior(double[] weights, ParadigmInstance instance, Form form) {
    Counter<Attributes> counter = new Counter<Attributes>();
    for (Attributes attrs : attrSetSorted) {
      if (instance.getInflForm(attrs).equals(form)) {
        counter.incrementCount(attrs, Math.exp(score(weights, attrs)));
      }
    }
    counter.normalize();
    return counter;
  }
  
  private static List<String> featurize(Attributes attrs) {
    // Note that keySet() is always a SortedSet so this always gives the same order
    List<Attribute> attrsList = new ArrayList<Attribute>();
    for (String attrName : attrs.attrs.keySet()) {
      attrsList.add(attrs.attrs.get(attrName));
    }
    List<List<Attribute>> allSubsets = PredictionUtils.getNonEmptySubsets(attrsList);
    List<String> features = new ArrayList<String>();
    for (List<Attribute> attrsToFeaturize : allSubsets) {
      String featName = "";
      for (Attribute attr : attrsToFeaturize) {
        featName += attr.together + "&";
      }
      features.add(featName);
    }
    return features;
  }
  
  public int[] featurizeAndIndexUseCache(Attributes attrs) {
    return featsCache.get(attrs);
  }
  
  public double score(double[] weights, Attributes attrs) {
    double score = 0.0;
    int[] feats = featurizeAndIndexUseCache(attrs);
    for (int i = 0; i < feats.length; i++) {
      score += weights[feats[i]];
    }
    return score;
  }
  
  
  private class FormExample implements Example {
    
    public final Form form;
    public final int count;
    
    public FormExample(Form form, int count) {
      this.form = form;
      this.count = count;
    }
    
    @Override
    public void addUnregularizedStochasticGradient(double[] weights, double[] gradient) {
      List<ParadigmInstance> potentialInstances = inflectedFormsToInstances.get(form);
      double correctWeight = 0.0;
      double totalWeight = 0.0;
      for (ParadigmInstance instance : potentialInstances) {
        for (Attributes attrs : attrSetSorted) {
          double score = Math.exp(score(weights, attrs));
          if (instance.getInflForm(attrs).equals(form)) {
            correctWeight += score;
          }
          totalWeight += score;
        }
      }
      for (ParadigmInstance instance : potentialInstances) {
        for (Attributes attrs : attrSetSorted) {
          int[] feats = featurizeAndIndexUseCache(attrs);
          double score = Math.exp(score(weights, attrs));
          boolean correct = instance.getInflForm(attrs).equals(form);
          for (int i = 0; i < feats.length; i++) {
            if (correct) {
              gradient[feats[i]] += count * score/correctWeight;
            }
            gradient[feats[i]] -= count * score/totalWeight;
          }
        }
      }
    }
    
    @Override
    public double computeLogLikelihood(double[] weights) {
      List<ParadigmInstance> potentialInstances = inflectedFormsToInstances.get(form);
      double correctLogWeight = Double.NEGATIVE_INFINITY;
      double totalLogWeight = Double.NEGATIVE_INFINITY;
      for (ParadigmInstance instance : potentialInstances) {
        for (Attributes attrs : attrSetSorted) {
          double score = score(weights, attrs);
          if (instance.getInflForm(attrs).equals(form)) {
            correctLogWeight = SloppyMath.logAdd(correctLogWeight, score);
          }
          totalLogWeight = SloppyMath.logAdd(totalLogWeight, score);
        }
      }
      double ll = correctLogWeight - totalLogWeight;
      return count * ll;
    }
    
    @Override
    public boolean predictsCorrectly(double[] weights) {
      // No real notion of "correct prediction" here
      return false;
    }
  }
  
  public static void main(String[] args) {
    
  }
}
