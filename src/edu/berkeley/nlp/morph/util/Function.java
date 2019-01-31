package edu.berkeley.nlp.morph.util;

/**
 */
public interface Function {
  int dimension();
  double valueAt(double[] x);
}
