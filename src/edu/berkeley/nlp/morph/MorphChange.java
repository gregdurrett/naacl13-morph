package edu.berkeley.nlp.morph;

import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Maintains an unanchored base form span (represented as a Form, though it isn't
 * a complete lexical item generally) and how it changes for each possible inflection.
 * 
 * @author gdurrett
 *
 */
public class MorphChange {

  public final Form base;
  public final SortedMap<Attributes,Form> rewrite;
  
  public MorphChange(Form base, SortedMap<Attributes,Form> rewrite) {
    this.base = base;
    this.rewrite = rewrite;
  }
  
  public MorphChange getMinimizedSubMorphChange(Attributes attrs) {
    Form newBase = this.base;
    assert rewrite.containsKey(attrs);
    Form newInfl = rewrite.get(attrs);
    while (newBase.length() > 0 && newInfl.length() > 0 && newBase.charAt(0).equals(newInfl.charAt(0))) {
      newBase = newBase.substring(1);
      newInfl = newInfl.substring(1);
    }
    while (newBase.length() > 0 && newInfl.length() > 0 && newBase.charAt(newBase.length() - 1).equals(newInfl.charAt(newInfl.length() - 1))) {
      newBase = newBase.substring(0, newBase.length() - 1);
      newInfl = newInfl.substring(0, newInfl.length() - 1);
    }
    SortedMap<Attributes,Form> newRewrite = new TreeMap<Attributes,Form>();
    newRewrite.put(attrs, newInfl);
    return new MorphChange(newBase, newRewrite);
  }
  
  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof MorphChange)) {
      return false;
    }
    MorphChange rhs = (MorphChange)other;
    return this.base.equals(rhs.base) && this.rewrite.equals(rhs.rewrite);
  }
  
  @Override
  public int hashCode() {
    return base.hashCode() * 81071 + rewrite.hashCode();
  }
  
  @Override
  public String toString() {
    String ret = base.toString() + "=>";
    for (Attributes attrs : rewrite.keySet()) {
      ret += rewrite.get(attrs).toString() + ",";
    }
    // Remove trailing , (or > if no inflected forms)
    return ret.substring(0, ret.length() - 1);
  }
}
