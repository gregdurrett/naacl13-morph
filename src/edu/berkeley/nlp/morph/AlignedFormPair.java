package edu.berkeley.nlp.morph;

import java.util.List;

/**
 * Pair of Forms, the edit Operations needed to produce trg from src, and
 * cost to do so.
 * 
 * @author gdurrett
 *
 */
public class AlignedFormPair {
  
  public final Form src;
  public final Form trg;
  public final List<Operation> ops;
  public final double cost;
  
  public AlignedFormPair(Form src, Form trg, List<Operation> ops, double cost) {
    this.src = src;
    this.trg = trg;
    this.ops = ops;
    this.cost = cost;
  }
}
