package edu.berkeley.nlp.morph.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import edu.berkeley.nlp.morph.fig.LogInfo;


public class GUtil {

  // LOGGING UTILS
  
  public static int loggingCounter = 0;
  
  public static void logsEveryNReset() {
    loggingCounter = 0;
  }
  
  public static void logsEveryN(String str, int n) {
    if (loggingCounter % n == 0) {
      LogInfo.logss(loggingCounter + ": " + str);
    }
    loggingCounter++;
  }
  
  public static void logVerbose(String str) {
    String fullClassName = Thread.currentThread().getStackTrace()[2].getClassName();            
    String className = fullClassName.substring(fullClassName.lastIndexOf(".") + 1);
    String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
    int lineNumber = Thread.currentThread().getStackTrace()[2].getLineNumber();
    LogInfo.logss(className + "." + methodName + "():" + lineNumber + ": "+ str);
  }
  
  // TOSTRING UTILS
  
  public static String toStringOnePerLine(Iterable<?> objs) {
    String ret = "";
    for (Object obj : objs) {
      ret += obj.toString() + "\n";
    }
    return ret;
  }
  
  public static String toStringSorted(Iterable<?> objs) {
    return toStringSorted(objs, false);
  }

  public static String toStringSortedOnePerLine(Iterable<?> objs) {
    return toStringSorted(objs, true);
  }
  
  public static final int CUTOFF = 1000;
  
  private static String toStringSorted(Iterable<?> objs, boolean onePerLine) {
    List<String> list = new ArrayList<String>();
    for (Object obj : objs) {
      if (list.size() > CUTOFF) {
        LogInfo.logss("Too many to return sorted (> " + CUTOFF + ")");
        return objs.toString();
      }
      list.add(obj.toString());
    }
    Collections.sort(list);
    if (onePerLine) {
      return toStringOnePerLine(list);
    } else {
      return list.toString();
    }
  }
  
  // VARIOUS UTILS
  
  public static <T extends Comparable<T>> int compareCollections(Iterable<T> col1, Iterable<T> col2) {
    Iterator<T> first = col1.iterator();
    Iterator<T> second = col2.iterator();
    while (first.hasNext() && second.hasNext()) {
      int result = first.next().compareTo(second.next());
      if (result != 0) {
        return result;
      }
    }
    if (!first.hasNext() && !second.hasNext()) {
      return 0;
    }
    // Longer one comes second
    return (first.hasNext() ? 1 : -1);
  }
}
