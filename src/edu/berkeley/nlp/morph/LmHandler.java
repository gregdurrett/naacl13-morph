package edu.berkeley.nlp.morph;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;

import edu.berkeley.nlp.morph.fig.IOUtils;
import edu.berkeley.nlp.morph.fig.LogInfo;
import edu.berkeley.nlp.morph.util.Counter;
import edu.berkeley.nlp.morph.util.Iterators;

/**
 * Wrapper around vocabulary count information.
 * 
 * @author gdurrett
 *
 */
public class LmHandler {
  
  private final Counter<Form> counts;

  public LmHandler(String fileName) {
    this(fileName, 0);
  }
  
  public LmHandler(String fileName, int minCountThreshold) {
    this.counts = new Counter<Form>();
    LogInfo.logss("Loading LM");
    try {
      Iterator<String> lineItr = IOUtils.lineIterator(fileName);
      for (String line : Iterators.able(lineItr)) {
        String[] fields = line.split("\t");
        assert fields.length == 2;
        String word = fields[0];
        int count = Integer.parseInt(fields[1]);
        if (count < minCountThreshold) {
          break;
        }
        Form form = new Form(word);
        counts.setCount(form, count);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    LogInfo.logss("LM loaded: " + counts.totalCount() + " tokens read for " + counts.size() + " types, cut off at count " + minCountThreshold);
  }
  
  public Collection<Form> getAllForms() {
    return counts.keySet();
  }
  
  public double getCount(Form form) {
    return counts.getCount(form);
  }
  
  /**
   * Estimate from a document
   * @param args
   */
  public static void main(String[] args) {
    String textPath = args[0];
    String lmPath = args[1];
    Counter<String> wordCounts = new Counter<String>();
    PrintWriter outWriter = IOUtils.openOutHard(lmPath);
    try {
      Iterator<String> lineItr = IOUtils.lineIterator(textPath);
      int lineNumber = 0;
      for (String line : Iterators.able(lineItr)) {
        if (lineNumber % 10000 == 0) {
          System.out.println("Line number: " + lineNumber);
        }
        String[] words= line.split("\\s+");
        for (String word : words) {
          wordCounts.incrementCount(word, 1.0);
        }
        lineNumber++;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    for (String word : Iterators.able(wordCounts.asPriorityQueue())) {
      outWriter.println(word + "\t" + ((int)wordCounts.getCount(word)));
    }
    outWriter.close();
  }
}
