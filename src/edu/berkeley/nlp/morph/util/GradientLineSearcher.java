package edu.berkeley.nlp.morph.util;

/**
 * @author Dan Klein
 */
public interface GradientLineSearcher {

	public void configureForIteration(int iteration);

	public double[] minimize(DifferentiableFunction function, double[] initial, double[] direction);

	public boolean stepSizeUnderflowed();
}
