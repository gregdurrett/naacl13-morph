package edu.berkeley.nlp.morph.util;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedList;

import edu.berkeley.nlp.morph.fig.LogInfo;


/**
 * @author Dan Klein
 */
public class LBFGSMinimizer implements GradientMinimizer, LineSearchingMinimizer, Serializable
{
	public void setCheckEmpiricalGradient(boolean checkEmpiricalGradient) {
		this.checkEmpiricalGradient = checkEmpiricalGradient;
	}

	private class MyLineSearcher extends BacktrackingLineSearcher implements Serializable
	{

		/**
		 * 
		 */
		private static final long serialVersionUID = 6771462316184493751L;

		@Override
		public void configureForIteration(int iteration) {
			if (iteration == 0)
				stepSizeMultiplier = initialStepSizeMultiplier;
			else
				stepSizeMultiplier = LBFGSMinimizer.this.stepSizeMultiplier;
			stepSize = 1.0;
		}

	}

	private class MyLineSearcherFactory implements GradientLineSearcherFactory, Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 5030157457080977207L;

		public GradientLineSearcher newLineSearcher() {
			return new MyLineSearcher();
		}
	}

	private static final long serialVersionUID = 36473897808840226L;

	double EPS = 1e-10;

	int maxIterations = 20;

	int maxHistorySize = 5;

	LinkedList<double[]> inputDifferenceVectorList = new LinkedList<double[]>();

	LinkedList<double[]> derivativeDifferenceVectorList = new LinkedList<double[]>();

	transient CallbackFunction iterCallbackFunction = null;

	int minIterations = -1;

	double initialStepSizeMultiplier = 0.01;

	double stepSizeMultiplier = 0.5;

	boolean convergedAndClearedHistories = false;

	int maxHistoryResets = Integer.MAX_VALUE;

	int numHistoryResets;

	boolean verbose = false;

	boolean finishOnFirstConverge = false;

	boolean checkEmpiricalGradient = false;

	boolean throwExceptionOnStepSizeUnderflow;

	File checkpointFile = null;

	private GradientLineSearcherFactory lineSearcherFactory = new MyLineSearcherFactory();

	public void setMinIterations(int minIterations) {
		this.minIterations = minIterations;
	}

	public void setFinishOnFirstConverge(boolean finishOnFirstConverge) {
		this.finishOnFirstConverge = finishOnFirstConverge;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public boolean getVerbose() {
		return verbose;
	}

	public int getMaxHistoryResets() {
		return maxHistoryResets;
	}

	public void setMaxHistoryResets(int maxHistoryResets) {
		this.maxHistoryResets = maxHistoryResets;
	}

	public void setMaxIterations(int maxIterations) {
		this.maxIterations = maxIterations;
	}

	public void setInitialStepSizeMultiplier(double initialStepSizeMultiplier) {
		this.initialStepSizeMultiplier = initialStepSizeMultiplier;
	}

	public void setStepSizeMultiplier(double stepSizeMultiplier) {
		this.stepSizeMultiplier = stepSizeMultiplier;
	}

	public void setCheckpointFile(File file) {
		this.checkpointFile = file;
	}

	public double[] getSearchDirection(int dimension, double[] derivative) {
		double[] initialInverseHessianDiagonal = getInitialInverseHessianDiagonal(dimension);
		double[] direction = implicitMultiply(initialInverseHessianDiagonal, derivative);
		return direction;
	}

	protected double[] getInitialInverseHessianDiagonal(int dimension) {
		double scale = 1.0;
		if (derivativeDifferenceVectorList.size() >= 1) {
			double[] lastDerivativeDifference = getLastDerivativeDifference();
			double[] lastInputDifference = getLastInputDifference();
			double num = DoubleArrays.innerProduct(lastDerivativeDifference, lastInputDifference);
			double den = DoubleArrays.innerProduct(lastDerivativeDifference, lastDerivativeDifference);
			scale = num / den;
		}
		return DoubleArrays.constantArray(scale, dimension);
	}

	public double[] minimize(DifferentiableFunction function, double[] initial, double tolerance) {
		return minimize(function, initial, tolerance, false);
	}

	public double[] minimize(DifferentiableFunction function, double[] initial, double tolerance, boolean printProgress) {
		double[] guess = DoubleArrays.clone(initial);
		return minimize(function, guess, function.valueAt(guess), function.derivativeAt(guess), 0, tolerance, printProgress);
	}

//	public static double[] minimizeFromCheckpoint(DifferentiableFunction function, File savedCheckpointFile, File newCheckpointFile, int maxIterations,
//		double tolerance, boolean printProgress) {
//		Checkpoint checkpoint = (Checkpoint) IOUtils.readObjFileHard(savedCheckpointFile);
//		LBFGSMinimizer minimizer = checkpoint.minimizer;
//		minimizer.setMaxIterations(maxIterations);
//		minimizer.setCheckpointFile(newCheckpointFile);
//		return minimizer.minimize(function, checkpoint.currentGuess, checkpoint.currentValue, checkpoint.currentDerivative, checkpoint.currentIteration + 1,
//			tolerance, printProgress);
//	}

	private double[] minimize(DifferentiableFunction function, double[] initial, double initialValue, double[] initialDerivative, int startIteration,
		double tolerance, boolean printProgress) {
		numHistoryResets = 0;
		GradientLineSearcher lineSearcher = lineSearcherFactory.newLineSearcher();

		double[] guess = DoubleArrays.clone(initial);
		double value = initialValue;
		double[] derivative = initialDerivative;
		for (int iteration = startIteration; iteration < maxIterations; iteration++) {
			if (checkEmpiricalGradient) EmpiricalGradientTester.test(function, guess);
			assert derivative.length == function.dimension();
			double[] initialInverseHessianDiagonal = getInitialInverseHessianDiagonal(function);
			double[] direction = implicitMultiply(initialInverseHessianDiagonal, derivative);
			//      System.out.println(" Derivative is: "+DoubleArrays.toString(derivative, 100));
			//      DoubleArrays.assign(direction, derivative);
			DoubleArrays.scale(direction, -1.0);
			//      System.out.println(" Looking in direction: "+DoubleArrays.toString(direction, 100));

			lineSearcher.configureForIteration(iteration);
			double[] nextGuess = lineSearcher.minimize(function, guess, direction);
			if (lineSearcher.stepSizeUnderflowed()) {

				// if step size underflow, clear histories and repeat this iteration
				if (clearHistories()) {
					--iteration;
					continue;
				} else {
					if (throwExceptionOnStepSizeUnderflow) {
						throw new StepSizeUnderflowException("Step size underflow", guess, derivative);
					} else
						break;
				}

			}
			double nextValue = function.valueAt(nextGuess);
			double[] nextDerivative = function.derivativeAt(nextGuess);

			if (printProgress) {
				printProgress(iteration, nextValue);
			}

			if (iteration >= minIterations && converged(value, nextValue, tolerance)) {
				if (!finishOnFirstConverge && !convergedAndClearedHistories) {
					clearHistories();
					convergedAndClearedHistories = true;
				} else {
					return nextGuess;
				}
			} else {
				convergedAndClearedHistories = false;
			}

			updateHistories(guess, nextGuess, derivative, nextDerivative);
			guess = nextGuess;
			value = nextValue;
			derivative = nextDerivative;
			if (iterCallbackFunction != null) {
				iterCallbackFunction.callback(guess, iteration, value, derivative);
			}
//			if (checkpointFile != null) {
//				IOUtils.writeObjFileHard(checkpointFile, new Checkpoint(this, iteration, guess, value, derivative));
//			}

		}
		if (verbose) LogInfo.logs("LBFGSMinimizer.minimize: Exceeded maxIterations without converging.");
		//System.err.println("LBFGSMinimizer.minimize: Exceeded maxIterations without converging.");
		return guess;
	}

	private void printProgress(int iteration, double nextValue) {
	  LogInfo.logs(String.format("[LBFGSMinimizer.minimize] Iteration %d ended with value %.6f\n", iteration, nextValue));

	}

	protected boolean converged(double value, double nextValue, double tolerance) {
		if (value == nextValue) return true;
		double valueChange = Math.abs(nextValue - value);
		double valueAverage = Math.abs(nextValue + value + EPS) / 2.0;
		if (valueChange / valueAverage < tolerance) return true;
		return false;
	}

	protected void updateHistories(double[] guess, double[] nextGuess, double[] derivative, double[] nextDerivative) {
		double[] guessChange = DoubleArrays.addMultiples(nextGuess, 1.0, guess, -1.0);
		double[] derivativeChange = DoubleArrays.addMultiples(nextDerivative, 1.0, derivative, -1.0);
		pushOntoList(guessChange, inputDifferenceVectorList);
		pushOntoList(derivativeChange, derivativeDifferenceVectorList);
	}

	private void pushOntoList(double[] vector, LinkedList<double[]> vectorList) {
		vectorList.addFirst(vector);
		if (vectorList.size() > maxHistorySize) vectorList.removeLast();
	}

	public boolean clearHistories() {
		if (numHistoryResets < maxHistoryResets) {
			if (verbose) LogInfo.logss("LBFGS cleared history.");
			inputDifferenceVectorList.clear();
			derivativeDifferenceVectorList.clear();
			numHistoryResets++;
			return true;
		}
		return false;
	}

	private int historySize() {
		return inputDifferenceVectorList.size();
	}

	public void setMaxHistorySize(int maxHistorySize) {
		this.maxHistorySize = maxHistorySize;
	}

	private double[] getInputDifference(int num) {
		// 0 is previous, 1 is the one before that
		return inputDifferenceVectorList.get(num);
	}

	private double[] getDerivativeDifference(int num) {
		return derivativeDifferenceVectorList.get(num);
	}

	private double[] getLastDerivativeDifference() {
		return derivativeDifferenceVectorList.getFirst();
	}

	private double[] getLastInputDifference() {
		return inputDifferenceVectorList.getFirst();
	}

	private double[] implicitMultiply(double[] initialInverseHessianDiagonal, double[] derivative) {
		double[] rho = new double[historySize()];
		double[] alpha = new double[historySize()];
		double[] right = DoubleArrays.clone(derivative);
		// loop last backward
		for (int i = historySize() - 1; i >= 0; i--) {
			double[] inputDifference = getInputDifference(i);
			double[] derivativeDifference = getDerivativeDifference(i);
			rho[i] = DoubleArrays.innerProduct(inputDifference, derivativeDifference);
			if (rho[i] == 0.0) throw new RuntimeException("LBFGSMinimizer.implicitMultiply: Curvature problem.");
			alpha[i] = DoubleArrays.innerProduct(inputDifference, right) / rho[i];
			right = DoubleArrays.addMultiples(right, 1.0, derivativeDifference, -1.0 * alpha[i]);
		}
		double[] left = DoubleArrays.pointwiseMultiply(initialInverseHessianDiagonal, right);
		for (int i = 0; i < historySize(); i++) {
			double[] inputDifference = getInputDifference(i);
			double[] derivativeDifference = getDerivativeDifference(i);
			double beta = DoubleArrays.innerProduct(derivativeDifference, left) / rho[i];
			left = DoubleArrays.addMultiples(left, 1.0, inputDifference, alpha[i] - beta);
		}
		return left;
	}

	private double[] getInitialInverseHessianDiagonal(DifferentiableFunction function) {
		double scale = 1.0;
		if (derivativeDifferenceVectorList.size() >= 1) {
			double[] lastDerivativeDifference = getLastDerivativeDifference();
			double[] lastInputDifference = getLastInputDifference();
			double num = DoubleArrays.innerProduct(lastDerivativeDifference, lastInputDifference);
			double den = DoubleArrays.innerProduct(lastDerivativeDifference, lastDerivativeDifference);
			scale = num / den;
		}
		return DoubleArrays.constantArray(scale, function.dimension());
	}

	/**
	 * User callback function to test or examine weights at the end of each
	 * iteration
	 * 
	 * @param callbackFunction
	 *            Will get called with the following args (double[]
	 *            currentGuess, int iterDone, double value, double[] derivative)
	 *            You don't have to read any or all of these.
	 */
	public void setIterationCallbackFunction(CallbackFunction callbackFunction) {
		this.iterCallbackFunction = callbackFunction;
	}

	public LBFGSMinimizer() {
	}

	public LBFGSMinimizer(int maxIterations) {
		this.maxIterations = maxIterations;
	}

	private static class Checkpoint implements Serializable
	{
		public Checkpoint(LBFGSMinimizer minimizer, int iteration, double[] guess, double value, double[] derivative) {
			this.minimizer = minimizer;
			this.currentIteration = iteration;
			this.currentGuess = guess;
			this.currentValue = value;
			this.currentDerivative = derivative;
		}

		private static final long serialVersionUID = 5712120416088704745L;

		LBFGSMinimizer minimizer;

		int currentIteration;

		double[] currentGuess;

		double currentValue;

		double[] currentDerivative;
	}

	public void setThrowExceptionOnStepSizeUnderflow(boolean exceptionOnStepSizeUnderflow) {
		this.throwExceptionOnStepSizeUnderflow = exceptionOnStepSizeUnderflow;
	}

	public void setLineSearcherFactory(GradientLineSearcherFactory factory) {
		this.lineSearcherFactory = factory;
	}

}
