package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of edit distance that supports two nontrivial operations:
 * 
 * a) Switching costs for going from EQUAL to something else or something else to EQUAL.
 * We do not charge for beginning or ending the edit sequence with insertions.
 * 
 * b) Position-specific edit costs for the equality and substitution operations (see
 * Section 3 of Durrett and DeNero (2013))
 * 
 * We need to expand the DP state to do this. Our DP state is actually quite general
 * and could store arbitrary other information; this means that it's somewhat slower
 * than it needs to be because it's object-heavy, but this isn't the bottleneck of our
 * algorithm and this generality allows possible other extensions to edit distance.
 * 
 * @author gdurrett
 * 
 */
public class MarkovEditDistanceComputer implements EditDistanceComputer {

  /**
   * Stores parameters for the edit distance operation.
   * Also stores the things being aligned because some parameters are anchored
   * to these, such as the fancy equality and substitute costs.
   * 
   * @author gdurrett
   *
   */
  public static class EditDistanceParams {

    public final Form src;
    public final Form trg;
    public final double[] equalCosts;
    public final double[] substCosts;
    public final double insertCost;
    public final double deleteCost;
    public final double switchMultiplier;

    public EditDistanceParams(Form src, Form trg, double[] equalCosts, double[] substCosts, double insertCost,
                              double deleteCost, double switchMultiplier) {
      this.src = src;
      this.trg = trg;
      this.equalCosts = equalCosts;
      this.substCosts = substCosts;
      this.insertCost = insertCost;
      this.deleteCost = deleteCost;
      this.switchMultiplier = switchMultiplier;
    }

    public static EditDistanceParams getStandardParams(Form src, Form trg, double switchCost) {
      return new EditDistanceParams(src, trg, populateArr(0, src.length()), populateArr(1, src.length()), 1, 1, switchCost);
    }

    public static EditDistanceParams getMaxAlignmentParams(Form src, Form trg, double switchCost) {
      return new EditDistanceParams(src, trg, populateArr(-1, src.length()), populateArr(0, src.length()), 0, 0, switchCost);
    }

    public static EditDistanceParams
        getWeightedMaxAlignmentParams(Form src, Form trg, double[] equalCosts, double switchCost) {
      return new EditDistanceParams(src, trg, equalCosts, populateArr(0, src.length()), 0, 0, switchCost);
    }

    public static double[] populateArr(double val, int len) {
      double[] arr = new double[len];
      for (int i = 0; i < len; i++) {
        arr[i] = val;
      }
      return arr;
    }
  }

  /**
   * State for the Viterbi forward pass through the edit distance lattice to compute backward costs.
   * 
   * @author gdurrett
   *
   */
  public static class ForwardSearchState {

    public final int srcIndex;
    public final int trgIndex;
    public final Operation lastOp;
    public final double viterbiBackwardCost;
    public final ForwardSearchState viterbiBackptr;

    public ForwardSearchState(int srcIndex, int trgIndex, Operation lastOp, double viterbiBackwardCost,
                              ForwardSearchState viterbiBackptr) {
      this.srcIndex = srcIndex;
      this.trgIndex = trgIndex;
      this.lastOp = lastOp;
      this.viterbiBackwardCost = viterbiBackwardCost;
      this.viterbiBackptr = viterbiBackptr;
    }
  }

  private final EditDistanceParams params;
  // Indices are src index, trg index, and previous operations.
  private ForwardSearchState[][][] chart;

  public MarkovEditDistanceComputer(EditDistanceParams params) {
    this.params = params;
    this.chart = new ForwardSearchState[params.src.length() + 1][params.trg.length() + 1][Operation.values().length];
  }

  /**
   * @param op
   * @param state
   * @return The cost to apply the given operator to the given state.
   */
  private double costToApply(Operation op, ForwardSearchState state) {
    if (!isLegalToApply(op, state)) {
      throw new RuntimeException("Illegal operation; applying " + op + " to " + state.srcIndex + ", " + state.trgIndex + " of "
                                 + params.src + "-" + params.trg);
    }
    double cost = 0;
    if (op == Operation.INSERT) {
      cost += params.insertCost;
    } else if (op == Operation.DELETE) {
      cost += params.deleteCost;
    } else if (op == Operation.SUBST) {
      cost += params.substCosts[state.srcIndex];
    } else if (op == Operation.EQUAL) {
      cost += params.equalCosts[state.srcIndex];
    }
    if ((state.lastOp == Operation.EQUAL && op != Operation.EQUAL) || (state.lastOp != Operation.EQUAL && op == Operation.EQUAL)) {
      cost += params.switchMultiplier;
    }
    return cost;
  }

  /**
   * @param op
   * @param state
   * @return True if it is legal to apply the given operation to the given state.
   * Checks bounds and conditions for equal vs. substitute
   */
  private boolean isLegalToApply(Operation op, ForwardSearchState state) {
    boolean roomOnSrc = state.srcIndex < params.src.length();
    boolean roomOnTrg = state.trgIndex < params.trg.length();
    if (op == Operation.INSERT) {
      return roomOnTrg;
    } else if (op == Operation.DELETE) {
      return roomOnSrc;
    } else {
      // EQUAL or SUBST must have room on both sides
      if (!roomOnSrc || !roomOnTrg) {
        return false;
      }
      // Now check that EQUAL applies only to equal characters and SUBST only to
      // unequal characters
      boolean charsEq = params.src.charAt(state.srcIndex).equals(params.trg.charAt(state.trgIndex));
      return (op == Operation.EQUAL && charsEq) || (op == Operation.SUBST && !charsEq);
    }
  }

  /**
   * @param op
   * @param state
   * @return A new state produced by applying op to the given state, or null
   * if op cannot be legally applied here
   */
  private ForwardSearchState apply(Operation op, ForwardSearchState state) {
    if (!isLegalToApply(op, state)) {
      return null;
    }
    int newSrcIndex = state.srcIndex;
    int newTrgIndex = state.trgIndex;
    if (op == Operation.EQUAL || op == Operation.SUBST) {
      newSrcIndex++;
      newTrgIndex++;
    } else if (op == Operation.INSERT) {
      newTrgIndex++;
    } else if (op == Operation.DELETE) {
      newSrcIndex++;
    }
    double costDelta = costToApply(op, state);
    return new ForwardSearchState(newSrcIndex, newTrgIndex, op, state.viterbiBackwardCost + costDelta, state);
  }

  /**
   * Does the forward pass, computing Viterbi backwards scores for each state.
   */
  private void forwardPass() {
    // Put in the initial states; we can either start in EQUAL or in one of the
    // other states, so initial switching is not penalized
    for (int prevOpIndex = 0; prevOpIndex < Operation.values().length; prevOpIndex++) {
      chart[0][0][prevOpIndex] = new ForwardSearchState(0, 0, Operation.values()[prevOpIndex], 0, null);
    }
    // Loop over chart cells
    for (int srcIndex = 0; srcIndex < params.src.length() + 1; srcIndex++) {
      for (int trgIndex = 0; trgIndex < params.trg.length() + 1; trgIndex++) {
        for (int prevOpIndex = 0; prevOpIndex < Operation.values().length; prevOpIndex++) {
          // Loop over operations that could be applied to the given cell
          for (int opIndex = 0; opIndex < Operation.values().length; opIndex++) {
            Operation currOp = Operation.values()[opIndex];
            // Produce the result of applying the operation and insert it into the chart as appropriate
            ForwardSearchState prevState = chart[srcIndex][trgIndex][prevOpIndex];
            if (prevState == null) {
              continue;
            }
            ForwardSearchState result = apply(currOp, prevState);
            if (result != null) {
              ForwardSearchState currEntry = chart[result.srcIndex][result.trgIndex][result.lastOp.ordinal()];
              if (currEntry == null || result.viterbiBackwardCost < currEntry.viterbiBackwardCost) {
                chart[result.srcIndex][result.trgIndex][result.lastOp.ordinal()] = result;
              }
            }
          }
        }
      }
    }
  }

  /**
   * Moves back through the chart and extracts the one-best solution.
   * @return The forms being aligned here and their one-best alignment.
   */
  private AlignedFormPair backwardPass() {
    ForwardSearchState currState = null;
    for (int i = 0; i < Operation.values().length; i++) {
      ForwardSearchState currFinalState = chart[params.src.length()][params.trg.length()][i];
      if (currFinalState == null) {
        continue;
      }
      if (currState == null || currFinalState.viterbiBackwardCost < currState.viterbiBackwardCost) {
        currState = currFinalState;
      }
    }
    if (currState == null) {
      throw new RuntimeException("Edit distance returned nothing for " + params.src + "-" + params.trg);
    }
    double cost = currState.viterbiBackwardCost;
    List<Operation> editOps = new ArrayList<Operation>();
    // Until we hit the first state, accrue the edit ops (whcih come in reverse order)
    while (currState.viterbiBackptr != null) {
      editOps.add(0, currState.lastOp);
      currState = currState.viterbiBackptr;
    }
    return new AlignedFormPair(params.src, params.trg, editOps, cost);
  }

  public AlignedFormPair runEditDistance() {
    forwardPass();
    return backwardPass();
  }
}
