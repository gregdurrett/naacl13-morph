package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;

import edu.berkeley.nlp.morph.fig.Indexer;
import edu.berkeley.nlp.morph.fig.LogInfo;
import edu.berkeley.nlp.morph.fig.SysInfoUtils;
import edu.berkeley.nlp.morph.util.Counter;
import edu.berkeley.nlp.morph.util.SloppyMath;

/**
 * Full, JOINT predictor as described in Durrett and DeNero (2013). Feature set and
 * source of extracted rules must be specified before training, which follows the
 * logistic regression scheme presented in Section 4 of the paper.
 * 
 * @author gdurrett
 *
 */
public class JointSpanMatchingPredictor implements Predictor {
  
  /**
   * Sequence of anchored morphological changes over a base form. This is defined
   * relative to changes previously extracted; it stores both the gold changes
   * and the set of possible changes. Manages all of the sequence modeling code.
   * 
   * @author gdurrett
   *
   */
  public static class Sequence implements GeneralLogisticRegression.Example {
    
    public final Form baseForm;
    public final List<AnchoredMorphChange> possibleMorphChanges;
    public final List<int[]> featuresEachMorphChange;
    public final List<AnchoredMorphChange> goldMorphChanges;
    
    // Says which of the possible changes are also gold changes; makes us
    // not have to go through the whole gold change set to check every time.
    public final List<Boolean> goldChangesOn;
    // Stores allowed changes over each span so that the dynamic program
    // can quickly check these.
    public final List<AnchoredMorphChange>[][] possibleChangesBySpan;

    // Gold preservations, *excluding glyphs that immediately follow morphological
    // changes* (these are always preserved and are handled differently)
    public final List<Boolean> goldNonAdjacentPreservationsOn;
    // Cached features over preserved spans
    public final List<int[]> featuresEachPreservation;
    
    public Sequence(Form baseForm,
                    List<AnchoredMorphChange> possibleMorphChanges,
                    List<AnchoredMorphChange> goldMorphChanges,
                    AnchoredSpanFeaturizer ruleFeaturizer,
                    AnchoredSpanFeaturizer nullFeaturizer,
                    Indexer<MorphChange> changeIndexer,
                    Indexer<String> featureIndexer,
                    boolean addToIndexer) {
      this.baseForm = baseForm;
      this.possibleMorphChanges = possibleMorphChanges;
      this.featuresEachMorphChange = new ArrayList<int[]>();
      this.goldMorphChanges = goldMorphChanges;
      this.goldChangesOn = new ArrayList<Boolean>();
      this.possibleChangesBySpan = new List[baseForm.length() + 1][baseForm.length() + 1];
      for (int i = 0; i < possibleChangesBySpan.length; i++) {
        for (int j = 0; j < possibleChangesBySpan[i].length; j++) {
          possibleChangesBySpan[i][j] = new ArrayList<AnchoredMorphChange>();
        }
      }
      for (AnchoredMorphChange change : possibleMorphChanges) {
        List<String> featurePrefixes = new ArrayList<String>();
        if (Driver.rankingFeats.contains("FEAT:CHANGE")) {
          featurePrefixes.add("CHANGE-" + changeIndexer.indexOf(change.change) + ":");
        }
        if (Driver.rankingFeats.contains("FEAT:FACTORED")) {
          for (Attributes attrs : change.change.rewrite.keySet()) {
            featurePrefixes.add(attrs.toString() + ":" + change.change.base.toString() + "=>" + change.change.rewrite.get(attrs));
          }
        }
        List<String> spanFeatures = ruleFeaturizer.getFeatures(change.spanAppliedTo);
        int[] features = new int[featurePrefixes.size() * spanFeatures.size()];
        int idx = 0;
        for (String featurePrefix : featurePrefixes) {
          for (String spanFeature : spanFeatures) {
            String completeFeature = featurePrefix + spanFeature;
            if (!addToIndexer && !featureIndexer.contains(completeFeature)) {
              features[idx] = featureIndexer.getIndex("UNK_FEAT");
            } else {
              features[idx] = featureIndexer.getIndex(completeFeature);
            }
            idx++;
          }
        }
        this.featuresEachMorphChange.add(features);
        if (goldMorphChanges != null && goldMorphChanges.contains(change)) {
          this.goldChangesOn.add(true);
        } else {
          this.goldChangesOn.add(false);
        }
        this.possibleChangesBySpan[change.spanAppliedTo.start][change.spanAppliedTo.end].add(change);
      }
      this.goldNonAdjacentPreservationsOn = new ArrayList<Boolean>();
      this.featuresEachPreservation = new ArrayList<int[]>();
      for (int i = 0; i < baseForm.length(); i++) {
        // N.B. These two assume that you don't have unseen 
        if (Driver.nullFeats.contains("FEAT:INDICATOR")) {
          String feature = "PRESERVE";
          if (!addToIndexer && !featureIndexer.contains(feature)) { 
            this.featuresEachPreservation.add(new int[] { featureIndexer.getIndex("UNK_FEAT") });
          } else {
            this.featuresEachPreservation.add(new int[] { featureIndexer.getIndex(feature) });
          }
        } else if (Driver.nullFeats.contains("FEAT:SIMPLE")) {
          String feature = "PRESERVE:" + baseForm.charAt(i).toString();
          if (!addToIndexer && !featureIndexer.contains(feature)) { 
            this.featuresEachPreservation.add(new int[] { featureIndexer.getIndex("UNK_FEAT") });
          } else {
            this.featuresEachPreservation.add(new int[] { featureIndexer.getIndex(feature) });
          }
        } else if (Driver.nullFeats.contains("FEAT:ALL")) {
//          String featurePrefix = "PRESERVE:";
          String featurePrefix = "PRESERVE:" + baseForm.charAt(i).toString();
          List<String> spanFeatures = nullFeaturizer.getFeatures(new AnchoredSpan(baseForm, i, i+1));
          int[] features = new int[spanFeatures.size()];
          int idx = 0;
          for (String spanFeature : spanFeatures) {
            String completeFeature = featurePrefix + spanFeature;
            if (!addToIndexer && !featureIndexer.contains(completeFeature)) {
              features[idx] = featureIndexer.getIndex("UNK_FEAT");
            } else {
              features[idx] = featureIndexer.getIndex(completeFeature);
            }
            idx++;
          }
          this.featuresEachPreservation.add(features);
        } else {
          this.featuresEachPreservation.add(new int[0]);
        }
        boolean preservedInGold = true;
        if (goldMorphChanges != null) {
          for (AnchoredMorphChange goldMorphChange : goldMorphChanges) {
            // N.B. we also rule out guys who immediately follow gold changes; these are considered
            // to be part of the change so we don't want to include them
            if (goldMorphChange.spanAppliedTo.start <= i && i <= goldMorphChange.spanAppliedTo.end) {
              preservedInGold = false;
            }
          }
        }
        this.goldNonAdjacentPreservationsOn.add(preservedInGold);
      }
      // Last index has no features
      this.featuresEachPreservation.add(new int[0]);
      this.goldNonAdjacentPreservationsOn.add(true);
    }
    
    private double[] computeChangeScores(double[] weights) {
      double[] changeScores = new double[possibleMorphChanges.size()];
      for (int i = 0; i < featuresEachMorphChange.size(); i++) {
        int[] feats = featuresEachMorphChange.get(i);
        double score = 0;
        for (int j = 0; j < feats.length; j++) {
          score += weights[feats[j]];
        }
        changeScores[i] = score;
      }
      return changeScores;
    }
    
    private double[] computePreserveScores(double[] weights) {
      assert baseForm.length()+1 == featuresEachPreservation.size();
      double[] preserveScores = new double[baseForm.length()+1];
//      Arrays.fill(preserveScores, 0.0);
      for (int i = 0; i < featuresEachPreservation.size(); i++) {
        int[] feats = featuresEachPreservation.get(i);
        double score = 0;
        for (int j = 0; j < feats.length; j++) {
          score += weights[feats[j]];
        }
        preserveScores[i] = score;
      }
      return preserveScores;
    }
    
    public void addUnregularizedStochasticGradient(double[] weights, double[] gradient) {
      double[] changeScores = computeChangeScores(weights);
      double[] preserveScores = computePreserveScores(weights);
      double[] alphas = computeAlphas(changeScores, preserveScores, false);
      double[] betas = computeBetas(changeScores, preserveScores, false);
//      for (int i = 0; i < alphas.length - 1; i++) {
//        LogInfo.logss((alphas[i] + betas[i+1]));
//      }
      double normalizer = alphas[alphas.length-1];
      for (int i = 0; i < possibleMorphChanges.size(); i++) {
        int[] changeFeats = featuresEachMorphChange.get(i);
        int[] preservationFeats = featuresEachPreservation.get(possibleMorphChanges.get(i).spanAppliedTo.end);
        if (goldChangesOn.get(i).booleanValue()) {
          addFeaturesToGradient(gradient, changeFeats, 1.0);
          addFeaturesToGradient(gradient, preservationFeats, 1.0);
        }
        int startIdx = possibleMorphChanges.get(i).spanAppliedTo.start;
        int endIdx = possibleMorphChanges.get(i).spanAppliedTo.end;
        double expectedCount = Math.exp(alphas[startIdx] + changeScores[i] + preserveScores[endIdx] + betas[endIdx+1] - normalizer);
//        if (expectedCount > 0.001) {
//          LogInfo.logss("Expected count " + expectedCount + " for " + possibleMorphChanges.get(i).toString());
//        }
        addFeaturesToGradient(gradient, changeFeats, -expectedCount);
        addFeaturesToGradient(gradient, preservationFeats, -expectedCount);
      }
      // Add gradients from preserved guys
      for (int i = 0; i < baseForm.length(); i++) {
        int[] preservationFeats = featuresEachPreservation.get(i);
        if (goldNonAdjacentPreservationsOn.get(i).booleanValue()) {
          addFeaturesToGradient(gradient, preservationFeats, 1.0);
        }
        double expectedCount = Math.exp(alphas[i] + preserveScores[i] + betas[i+1] - normalizer);
//        if (expectedCount > 0.001) {
//          LogInfo.logss("Expected count " + expectedCount + " for preserving " + i);
//        }
        addFeaturesToGradient(gradient, preservationFeats, -expectedCount);
      }
      
    }
    
    private void addFeaturesToGradient(double[] gradient, int[] feats, double scale) {
      for (int i = 0; i < feats.length; i++) {
        gradient[feats[i]] += scale;
      }
    }
    
    public double computeLogLikelihood(double[] weights) {
      double[] changeScores = computeChangeScores(weights);
      double[] preserveScores = computePreserveScores(weights);
      double[] alphas = computeAlphas(changeScores, preserveScores, false);
      double normalizer = alphas[alphas.length-1];
      assert goldChangesOn.size() == possibleMorphChanges.size();
      double goldChangesScore = 0;
      for (int i = 0; i < goldChangesOn.size(); i++) {
        if (goldChangesOn.get(i)) {
          goldChangesScore += changeScores[i] + preserveScores[possibleMorphChanges.get(i).spanAppliedTo.end];
        }
      }
      for (int i = 0; i < baseForm.length(); i++) {
        if (goldNonAdjacentPreservationsOn.get(i)) {
          goldChangesScore += preserveScores[i];
        }
      }
      return goldChangesScore - normalizer;
    }
    
    public List<AnchoredMorphChange> predict(double[] weights) {
      List<AnchoredMorphChange> prediction = new ArrayList<AnchoredMorphChange>();
      double[] changeScores = computeChangeScores(weights);
      double[] preserveScores = computePreserveScores(weights);
      double[] alphas = computeAlphas(changeScores, preserveScores, true);
      int i = alphas.length - 1;
      while (i > 0) {
        AnchoredMorphChange bestChange = null;
        double bestChangeScore = Double.NEGATIVE_INFINITY;
        for (int j = 0; j <= i-1; j++) {
          if (!possibleChangesBySpan[j][i-1].isEmpty()) {
            for (AnchoredMorphChange changeIJ : possibleChangesBySpan[j][i-1]) {
              double score = alphas[j] + changeScores[possibleMorphChanges.indexOf(changeIJ)] + preserveScores[i-1];
              if (score > bestChangeScore) {
                bestChange = changeIJ;
                bestChangeScore = score;
              }
            }
          }
        }
        if (bestChangeScore < alphas[i-1] + preserveScores[i-1]) {
          bestChange = null;
          bestChangeScore = alphas[i-1];
        }
        if (bestChange != null) {
          prediction.add(0, bestChange);
          i = bestChange.spanAppliedTo.start;
        } else {
          i--;
        }
      }
      return prediction;
    }
    
    private double[] computeAlphas(double[] changeScores, double[] preserveScores, boolean max) {
      // alphas live on fenceposts, need one extra fencepost so we can
      // store the last column of alphas
      double[] alphas = new double[baseForm.length()+2];
      Arrays.fill(alphas, Double.NEGATIVE_INFINITY);
      alphas[0] = 0;
      for (int i = 0; i < baseForm.length() + 1; i++) {
        // Apply changes ending at i
        for (int j = 0; j <= i; j++) {
          if (!possibleChangesBySpan[j][i].isEmpty()) {
            for (AnchoredMorphChange changeJI : possibleChangesBySpan[j][i]) {
              double increment = alphas[j] + changeScores[possibleMorphChanges.indexOf(changeJI)] + preserveScores[i];
              alphas[i+1] = sum(alphas[i+1], increment, max);
            }
          }
        }
        // Incorporate the featureless null transition
//        alphas[i+1] = sum(alphas[i+1], alphas[i], max);
        alphas[i+1] = sum(alphas[i+1], alphas[i] + preserveScores[i], max);
      }
      return alphas;
    }

    private double[] computeBetas(double[] changeScores, double[] preserveScores, boolean max) {
      double[] betas = new double[baseForm.length()+2];
      Arrays.fill(betas, Double.NEGATIVE_INFINITY);
      betas[betas.length-1] = 0;
      for (int i = baseForm.length()+1; i > 0; i--) {
        // Apply changes beginning at i
        for (int j = i; j < baseForm.length()+1; j++) {
          if (!possibleChangesBySpan[i][j].isEmpty()) {
            for (AnchoredMorphChange change : possibleChangesBySpan[i][j]) {
              double increment = betas[j+1] + changeScores[possibleMorphChanges.indexOf(change)] + preserveScores[j];
              betas[i] = sum(betas[i], increment, max);
            }
          }
        }
        // Incorporate the featureless null transition
//        betas[i-1] = sum(betas[i-1], betas[i], max);
        betas[i-1] = sum(betas[i-1], betas[i] + preserveScores[i-1], max);
      }
      return betas;
    }
    
    private double sum(double a, double b, boolean max) {
      if (max) {
        return Math.max(a, b);
      } else {
        return SloppyMath.logAdd(a, b);
      }
    }
    
    public boolean predictsCorrectly(double[] weights) {
      return predict(weights).equals(this.goldMorphChanges);
    }
  }

  private final ExtractedModel extractedModel;
  private final AnchoredSpanFeaturizer ruleFeaturizer;
  private final AnchoredSpanFeaturizer nullFeaturizer;
  private final Indexer<MorphChange> morphChangeIndexer;
  private final Indexer<String> featureIndexer;

  private final ChangeFilterer changeFilterer;
  
  private double[] weights;
  
  public JointSpanMatchingPredictor(ExtractedModel extractedModel, AnchoredSpanFeaturizer ruleFeaturizer, AnchoredSpanFeaturizer nullFeaturizer) {
    this.extractedModel = extractedModel;
    this.ruleFeaturizer = ruleFeaturizer;
    this.nullFeaturizer = nullFeaturizer;
    this.morphChangeIndexer = new Indexer<MorphChange>();
    for (AnalyzedParadigmInstance analyzedInstance: this.extractedModel.analyzedInstances) {
      for (AnchoredMorphChange change : analyzedInstance.getCachedChanges()) {
        this.morphChangeIndexer.getIndex(change.change);
      }
    }
    this.featureIndexer = new Indexer<String>();
    this.featureIndexer.getIndex("UNK_FEAT");
    this.changeFilterer = new ChangeFilterer(extractedModel, Driver.useMatchFiltering);
    this.weights = new double[0];
  }
  
  public void train() {
    // Index morph changes consistently every time
    List<Sequence> sequences = new ArrayList<Sequence>();
    List<AnalyzedParadigmInstance> analyzedInstances = this.extractedModel.analyzedInstances;
    
    long nanoTime = System.nanoTime();
    for (int i = 0; i < analyzedInstances.size(); i++) {
      if (i % 200 == 0) {
        LogInfo.logss("Featurized " + i + ", memory = " + SysInfoUtils.getUsedMemoryStr());
      }
      AnalyzedParadigmInstance analyzedInstance = analyzedInstances.get(i);
      Form baseForm = analyzedInstance.inst.baseForm();
      List<AnchoredMorphChange> goldMorphChanges = analyzedInstance.getCachedChanges();
      sequences.add(makeSequence(baseForm, goldMorphChanges, true));
    }
    LogInfo.logss(sequences.size() + " train sequences created, " + featureIndexer.size() +
                  " features, " + morphChangeIndexer.size() + " morph changes in " + (System.nanoTime() - nanoTime)/1000000 + " millis");
    int avgNumMorphChanges = 0;
    int maxNumMorphChanges = 0;
    for (Sequence sequence : sequences) {
      avgNumMorphChanges += sequence.possibleMorphChanges.size();
      maxNumMorphChanges = Math.max(maxNumMorphChanges, sequence.possibleMorphChanges.size());
    }
    LogInfo.logss("Average num morph changes: " + avgNumMorphChanges/((double)sequences.size()) + ", max = " + maxNumMorphChanges);

    this.weights = new double[featureIndexer.size()];
    // Only run training if there are a non-zero number of morph changes or LBFGS will break
    if (morphChangeIndexer.size() > 0) {
//      final double reg = 0.001;
//      final double eta = 1.0;
//      final int numItrs = 30;
//      new GeneralLogisticRegression<List<AnchoredMorphChange>>().trainWeightsAdagradL1R(sequences, reg, eta, numItrs, this.weights);
      final double reg = 0.00001;
      final double eps = 0.01;
      final int numItrs = 30;
      new GeneralLogisticRegression().trainWeightsLbfgsL2R(sequences, reg, eps, numItrs, this.weights);
    }
    if (Driver.nullFeats.contains("FEAT:INDICATOR")) {
      LogInfo.logss("Weight: " + weights[featureIndexer.indexOf("PRESERVE")]);
    }
  }
  
  private Sequence makeSequence(Form baseForm, List<AnchoredMorphChange> goldChanges, boolean addToIndexer) {
    List<AnchoredMorphChange> possibleMorphChanges = new ArrayList<AnchoredMorphChange>();
    for (MorphChange morphChange : morphChangeIndexer) {
      List<AnchoredSpan> matchingSpans = changeFilterer.findMatchingSpans(baseForm, morphChange);
      for (AnchoredSpan matchingSpan : matchingSpans) {
        possibleMorphChanges.add(new AnchoredMorphChange(morphChange, matchingSpan));
      }
    }
    return new Sequence(baseForm, possibleMorphChanges, goldChanges, ruleFeaturizer, nullFeaturizer, morphChangeIndexer, featureIndexer, addToIndexer);
  }
  
  @Override
  public ParadigmHypothesis predict(Form baseForm, SortedSet<Attributes> attrs, ParadigmInstance goldInstance) {
    // Don't use oracle info
    return predict(baseForm, attrs);
  }

  public ParadigmHypothesis predict(Form baseForm, SortedSet<Attributes> attrs) {
    Sequence seq = makeSequence(baseForm, null, false);
    List<AnchoredMorphChange> predChanges = seq.predict(this.weights);
    Counter<AnchoredMorphChange> scores = new Counter<AnchoredMorphChange>();
    for (AnchoredMorphChange change : predChanges) {
      scores.setCount(change, 0.0);
    }
    ParadigmInstance predInstance = new ParadigmInstance( baseForm, attrs, predChanges);
    return new ParadigmHypothesis(predInstance, predChanges, scores, 0);
  }
}
