package edu.berkeley.nlp.morph.util;

/**
 * @author Dan Klein
 */
public interface GradientMinimizer {
  double[] minimize(DifferentiableFunction function, double[] initial, double tolerance);
  double[] minimize(DifferentiableFunction function, double[] initial, double tolerance, boolean project);


	/**
	 * User callback function to test or examine weights at the end of each
	 * iteration
	 * 
	 * @param callbackFunction
	 *            Will get called with the following args (double[]
	 *            currentGuess, int iterDone, double value, double[] derivative)
	 *            You don't have to read any or all of these.
	 */
	public void setIterationCallbackFunction(CallbackFunction callbackFunction);



}
