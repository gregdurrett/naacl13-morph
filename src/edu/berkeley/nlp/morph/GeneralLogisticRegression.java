package edu.berkeley.nlp.morph;

import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.morph.fig.LogInfo;
import edu.berkeley.nlp.morph.fig.Pair;
import edu.berkeley.nlp.morph.util.CachingDifferentiableFunction;
import edu.berkeley.nlp.morph.util.LBFGSMinimizer;

/**
 * Abstract logistic regression class that supports Adagrad (see Duchi et al 2011)
 * with L1 regulariziation and LBFGS with L2 regularization.
 * 
 * @author gdurrett
 *
 */
public class GeneralLogisticRegression {
  
  public static interface Example {
    
    public void addUnregularizedStochasticGradient(double[] weights, double[] gradient);
    
    public double computeLogLikelihood(double[] weights);
    
    public boolean predictsCorrectly(double[] weights);
  }
  
  public void trainWeightsAdagradL1R(List<? extends Example> exs,
                                     double reg,
                                     double eta,
                                     int numItrs,
                                     double[] weights) {
    double[] bestWeights = null;
    double bestObjective = Double.NEGATIVE_INFINITY;
    int bestIteration = -1;
    double[] reusableGradientArray = new double[weights.length];
    Arrays.fill(reusableGradientArray, 0.0);
    double[] diagGt = new double[weights.length];
    Arrays.fill(diagGt, 0.0);
    for (int i = 0; i < numItrs; i++) {
      LogInfo.logss("ITERATION " + i);
      for (int j = 0; j < exs.size(); j++) {
        takeAdagradStepL1R(exs.get(j), reusableGradientArray, diagGt, reg, eta, weights);
      }
      // Print some diagnostics
      double norm = 0, nonzeroWeights = 0;
      for (int j = 0; j < weights.length; j++) {
        if (weights[j] != 0) {
          nonzeroWeights++;
        }
        norm += weights[j] * weights[j];
      }
      LogInfo.logss("NONZERO WEIGHTS: " + nonzeroWeights);
      LogInfo.logss("NORM OF WEIGHTS: " + norm);
      double objective = computeObjectiveL1R(exs, weights, reg);
      LogInfo.logss("TRAIN OBJECTIVE: " + objective);
      if (objective > bestObjective) {
        bestWeights = weights;
        bestObjective = objective;
        bestIteration = i;
      }
      LogInfo.logss("TRAIN ACCURACY: " + computeAccuracy(exs, weights));
    }
    LogInfo.logss("Best weights were from iteration " + bestIteration + " with objective value " + bestObjective);
    for (int i = 0; i < weights.length; i++) {
      weights[i] = bestWeights[i];
    }
  }
  
  private double computeObjectiveL1R(List<? extends Example> exs, double[] weights, double reg) {
    double objective = 0;
    for (int i = 0; i < exs.size(); i++) {
      objective += computeLogLikelihood(exs.get(i), weights);
    }
    for (int i = 0; i < weights.length; i++) {
      objective -= Math.abs(weights[i]) * reg;
    }
    return objective;
  }
  
  private void takeAdagradStepL1R(Example ex,
                                  double[] reusableGradientArray,
                                  double[] diagGt,
                                  double reg,
                                  double eta,
                                  double[] weights) {
    for (int i = 0; i < reusableGradientArray.length; i++) {
      reusableGradientArray[i] = 0;
    }
    ex.addUnregularizedStochasticGradient(weights, reusableGradientArray);
//    checkGradient(Collections.singletonList(ex), weights, reusableGradientArray);
    for (int i = 0; i < reusableGradientArray.length; i++) {
      double xti = weights[i];
      // N.B. We negate the gradient here because the Adagrad formulas are all for minimizing
      // and we're trying to maximize, so think of it as minimizing the negative of the objective
      // which has the opposite gradient
      double gti = -reusableGradientArray[i];
      // Update diagGt
      diagGt[i] += gti * gti;
      double Htii = 1 + Math.sqrt(diagGt[i]);
      double etaOverHtii = eta / Htii;
      double newXti = xti - etaOverHtii * gti;
      weights[i] = Math.signum(newXti) * Math.max(0, Math.abs(newXti) - reg * etaOverHtii);
    }
  }

  public void trainWeightsLbfgsL2R(final List<? extends Example> exs,
                                   final double reg,
                                   final double epsilon,
                                   final int numItrs,
                                   final double[] weights) {
    CachingDifferentiableFunction diffFunc = new CachingDifferentiableFunction() {
      
      private final double[] reusableGradientArr = new double[weights.length];
      
      public int dimension() {
        return weights.length;
      }
      
      public Pair<Double, double[]> calculate(double[] currWeights) {
        long nanoTime = System.nanoTime();
        double objective = 0.0;
//        double[] gradient = new double[currWeights.length];
        double[] gradient = reusableGradientArr;
        Arrays.fill(gradient, 0.0);
        for (Example ex : exs) {
          objective += computeLogLikelihood(ex, currWeights);
          ex.addUnregularizedStochasticGradient(currWeights, gradient);
        }
//        checkGradient(exs, weights, gradient);
        for (int i = 0; i < gradient.length; i++) {
          objective -= reg * currWeights[i] * currWeights[i];
          gradient[i] -= 2 * reg * currWeights[i];
        }

        double negObjective = -objective;
        double[] negGradient = new double[currWeights.length];
        for (int i = 0; i < gradient.length; i++) {
          negGradient[i] = -gradient[i];
        }
        // Print some diagnostics
        double norm = 0, nonzeroWeights = 0;
        for (int j = 0; j < currWeights.length; j++) {
          if (currWeights[j] > 0) {
            nonzeroWeights++;
          }
          norm += currWeights[j] * currWeights[j];
        }
        LogInfo.logss("NONZERO WEIGHTS: " + nonzeroWeights);
        LogInfo.logss("NORM OF WEIGHTS: " + norm);
        LogInfo.logss("TRAIN OBJECTIVE: " + objective);
        LogInfo.logss("TRAIN ACCURACY: " + computeAccuracy(exs, currWeights));
        LogInfo.logss("TRAIN MILLIS: "+  (System.nanoTime() - nanoTime)/1000000);
        return new Pair<Double, double[]>(negObjective, negGradient);
      }
    };
    double[] finalWeights = new LBFGSMinimizer(numItrs).minimize(diffFunc, weights, epsilon, true);
    for (int i = 0; i < finalWeights.length; i++) {
      weights[i] = finalWeights[i];
    }
  }
  
  public void checkGradient(List<? extends Example> exs, double[] weights, double[] gradient) {
    double[] tempWeights = new double[weights.length];
    System.arraycopy(weights, 0, tempWeights, 0, weights.length);
    double currLL = 0;
    for (Example ex: exs) {
      currLL += computeLogLikelihood(ex, tempWeights);
    }
    double stepSize = 1e-7;
    for (int i = 0; i < gradient.length; i++) {
      if (i % 1000 == 0) {
        LogInfo.logss("EMP GRADIENT " + i);
      }
      tempWeights[i] += stepSize;
      double newLL = 0;
      for (Example ex: exs) {
        newLL += computeLogLikelihood(ex, tempWeights);
      }
      double empGradient = (newLL - currLL)/stepSize;
//      if (Math.abs(empGradient) > 1e-10) {
        LogInfo.logss("Gradient: " + gradient[i] + ", emp gradient: " + empGradient);
//      }
      tempWeights[i] -= stepSize;
    }
    LogInfo.logss("Empirical gradient over");
  }

  private double computeAccuracy(List<? extends Example> exs, double[] weights) {
    double numCorrect = 0;
    for (int i = 0; i < exs.size(); i++) {
      numCorrect += (exs.get(i).predictsCorrectly(weights) ? 1 : 0);
    }
    return numCorrect/exs.size();
  }
  
  private double computeLogLikelihood(Example ex, double[] weights) {
    return ex.computeLogLikelihood(weights);
  }
}
