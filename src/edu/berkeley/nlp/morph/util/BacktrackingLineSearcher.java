package edu.berkeley.nlp.morph.util;

import edu.berkeley.nlp.morph.fig.LogInfo;


/**
 */
public class BacktrackingLineSearcher implements GradientLineSearcher
{
	private double EPS = 1e-10;

	public double stepSizeMultiplier = 0.1;//was 0.9;

	public double sufficientDecreaseConstant = 1e-4;//0.9;

	public double sufficientSlopeDecreaseConstant = 0.0;//see http://en.wikipedia.org/wiki/Wolfe_conditions, 0.9 is a good value (0.0 means no test)

	public double initialStepSize = 1.0;

	double stepSize;

	public boolean verbose = false;

	boolean stepSizeUnderflow = false;

	int maxIterations = Integer.MAX_VALUE;

	public double[] minimize(DifferentiableFunction function, double[] initial, double[] direction) {
		return minimize(function, initial, direction, false);
	}

  public static double norm_inf(double[] a) {
    double max = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < a.length; i++) {
      if (Math.abs(a[i]) > max) {
        max = Math.abs(a[i]);
      }
    }
    return max;
  }

	public double[] minimize(DifferentiableFunction function, double[] initial, double[] direction, boolean project) {
		stepSizeUnderflow = false;
		stepSize = initialStepSize;
		double initialFunctionValue = function.valueAt(initial);
		final double[] derivative = function.derivativeAt(initial);
		double initialDirectionalDerivative = DoubleArrays.innerProduct(derivative, direction);
		double derivMax = norm_inf(derivative);
		double[] guess = null;
		double guessValue = 0.0;
		boolean sufficientDecreaseObtained = false;
		//    if (false) {
		//      guess = DoubleArrays.addMultiples(initial, 1.0, direction, EPS);
		//      double guessValue = function.valueAt(guess);
		//      double sufficientDecreaseValue = initialFunctionValue + sufficientDecreaseConstant * initialDirectionalDerivative * EPS;
		//      System.out.println("NUDGE TEST:");
		//      System.out.println("  Trying step size:  "+EPS);
		//      System.out.println("  Required value is: "+sufficientDecreaseValue);
		//      System.out.println("  Value is:          "+guessValue);
		//      System.out.println("  Initial was:       "+initialFunctionValue);
		//      if (guessValue > initialFunctionValue) {
		//        System.err.println("NUDGE TEST FAILED");
		//        return initial;
		//      }
		//    }
		int iter = 0;
		while (!sufficientDecreaseObtained) {
			if (verbose) LogInfo.logss("Trying step size " + stepSize);
			guess = DoubleArrays.addMultiples(initial, 1.0, direction, stepSize);
			if (project) DoubleArrays.project2(guess, initial); //keep the guess within the same orthant
			guessValue = function.valueAt(guess);
			double sufficientDecreaseValue = initialFunctionValue + sufficientDecreaseConstant * initialDirectionalDerivative * stepSize;
			//      System.out.println("Trying step size:  "+stepSize);
			//      System.out.println("Required value is: "+sufficientDecreaseValue);
			//      System.out.println("Value is:          "+guessValue);
			//      System.out.println("Initial was:       "+initialFunctionValue);
			sufficientDecreaseObtained = (guessValue <= sufficientDecreaseValue);
			if (sufficientDecreaseObtained) {
				if (sufficientSlopeDecreaseConstant > 0.0) {
					double[] guessDeriv = function.derivativeAt(guess);
					double guessInnerProduct = DoubleArrays.innerProduct(guessDeriv, direction);
					sufficientDecreaseObtained &= Math.abs(guessInnerProduct) <= sufficientSlopeDecreaseConstant * Math.abs(initialDirectionalDerivative);
				}
			}
			if (!sufficientDecreaseObtained) {

				stepSize *= stepSizeMultiplier;
				if (stepSize * derivMax < EPS) {

					LogInfo.errors("BacktrackingSearcher.minimize: stepSize underflow.");
					EmpiricalGradientTester.test(function, initial);
					stepSizeUnderflow = true;
					return initial;

				}
			}
			if (iter++ > maxIterations) return initial;
		}
		//    double lastGuessValue = guessValue;
		//    double[] lastGuess = guess;
		//    while (lastGuessValue >= guessValue) {
		//      lastGuessValue = guessValue;
		//      lastGuess = guess;
		//      stepSize *= stepSizeMultiplier;
		//      guess = DoubleArrays.addMultiples(initial, 1.0, direction, stepSize);
		//      guessValue = function.valueAt(guess);
		//    }
		//    return lastGuess;
		return guess;
	}

	/**
	 * Returns the final step size after a sufficient decrease is found.
	 * 
	 * @return
	 */
	public double getFinalStepSize() {
		return stepSize;
	}

	public static void main(String[] args) {
		DifferentiableFunction function = new DifferentiableFunction()
		{
			public int dimension() {
				return 1;
			}

			public double valueAt(double[] x) {
				return x[0] * (x[0] - 0.01);
			}

			public double[] derivativeAt(double[] x) {
				return new double[] { 2 * x[0] - 0.01 };
			}

			public double[] unregularizedDerivativeAt(double[] x) {
				return new double[] { 2 * x[0] - 0.01 };
			}
		};
		BacktrackingLineSearcher lineSearcher = new BacktrackingLineSearcher();
		lineSearcher.minimize(function, new double[] { 0 }, new double[] { 1 }, false);
	}

	public void setMaxIterations(int i) {
		this.maxIterations = i;
	}

	public void configureForIteration(int iteration) {

	}

	public boolean stepSizeUnderflowed() {
		return stepSizeUnderflow;
	}
}
