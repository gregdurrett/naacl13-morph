package edu.berkeley.nlp.morph.util;

/**
 */
public interface DifferentiableFunction extends Function {
  double[] derivativeAt(double[] x);
}
