package edu.berkeley.nlp.morph;

import java.io.PrintWriter;


public class ParadigmInstanceWriter {

  public static void writeParadigmInstance(ParadigmInstance instance, PrintWriter output) {
    for (Attributes attrs : instance.getAttrSetSorted()) {
      output.println(instance.getInflForm(attrs).toString() + "," + instance.baseForm().toString() + "," + attrs.toString());
    }
  }
}
