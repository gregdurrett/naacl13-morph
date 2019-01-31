package edu.berkeley.nlp.morph.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import edu.berkeley.nlp.morph.fig.LogInfo;


public class EmpiricalGradientTester
{
	static final double EPS = 1e-3;

	static final double REL_EPS = 1e-2;

	static final double DEL_INITIAL = 1e-5;

	static final double DEL_MIN = 1e-8;

	public static void test(DifferentiableFunction func, double[] x) {
		double[] nextX = DoubleArrays.clone(x);
		double baseVal = func.valueAt(x);
		double[] grad = func.derivativeAt(x);
		for (int i : shuffle(0, x.length, new Random(1))) { //Functional.range(x.length)) {//
			double delta = DEL_INITIAL;
			boolean ok = false;
			double empDeriv = 0.0;
			while (delta > DEL_MIN && !ok) {
				nextX[i] += delta;
				double nextVal = func.valueAt(nextX);
				empDeriv = (nextVal - baseVal) / delta;
				if (close(empDeriv, grad[i])) {
					//					LogInfo.logss("Gradient ok for dim %d, delta %f, calculated %f, empirical: %f", i, delta, grad[i], empDeriv);
					ok = true;
				}
				nextX[i] -= delta;
				if (!ok) delta /= 10;
			}
			if (!ok) LogInfo.errors("Empirical gradient step-size underflow dim %d, delta %f, calculated %f, empirical: %f", i, delta, grad[i], empDeriv);
		}
	}

  public static List<Integer> range(int start, int end) {
    List<Integer> result = new ArrayList<Integer>(end - start);
    for (int i = start; i < end; i++) {
      result.add(i);
    }
    return result;
  }

  public static List<Integer> shuffle(int start, int end, final Random rand) {
    List<Integer> order = range(start, end);
    Collections.shuffle(order, rand);
    return order;
  }

	public static boolean close(double x, double y) {
		if (Math.abs(x - y) < EPS) return true;
		double avgMag = (Math.abs(x) + Math.abs(y)) / 2.0;
		return Math.abs(x - y) / avgMag < REL_EPS;
	}
}
