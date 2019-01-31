package edu.berkeley.nlp.morph.util;

public interface LineSearchingMinimizer
{
	public void setThrowExceptionOnStepSizeUnderflow(boolean exceptionOnStepSizeUnderflow);

	public void setLineSearcherFactory(GradientLineSearcherFactory factory);

}
