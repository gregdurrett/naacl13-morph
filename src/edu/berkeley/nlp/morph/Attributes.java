package edu.berkeley.nlp.morph;

import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

import edu.berkeley.nlp.morph.util.GUtil;

/**
 * Vector of morphological properties and their bound values, e.g. "Person=1st,Tense=Past"
 * 
 * @author gdurrett
 *
 */
public class Attributes implements Comparable<Attributes> {
  
  public static class Attribute implements Comparable<Attribute> {
    public final String attr;
    public final String value;
    public final String together;
    
    public Attribute(String attr, String value) {
      this.attr = attr;
      this.value = value;
      this.together = attr + "=" + value;
    }

    @Override
    public boolean equals(Object other) {
      if (other == null || !(other instanceof Attribute)) {
        return false;
      }
      return this.together.equals(((Attribute)other).together);
    }
    
    @Override
    public int hashCode() {
      return together.hashCode();
    }
    
    @Override
    public String toString() {
      return together;
    }

    @Override
    public int compareTo(Attribute other) {
      return this.together.compareTo(other.together);
    }
  }

  // SortedMap because it's really handy to have these guys sorted for printing
  // all sorts of things
  public final SortedMap<String,Attribute> attrs;
  
  public Attributes(SortedSet<Attribute> attrs) {
    this.attrs = new TreeMap<String,Attribute>();
    for (Attribute attr : attrs) {
      this.attrs.put(attr.attr, attr);
    }
  }

  public Attributes(SortedMap<String,Attribute> attrs) {
    this.attrs = attrs;
  }
  
  public String getValueOrEmpty(String attr) {
    if (!attrs.containsKey(attr)) {
      return "";
    }
    return attrs.get(attr).value;
  }
  
  public void setValue(String attr, String value) {
    this.attrs.put(attr, new Attribute(attr, value));
  }
  
  public String toShortString() {
    String ret = "";
    for (Attribute attr : attrs.values()) {
      ret += attr.value.substring(0, Math.min(6, attr.value.length()));
    }
    return ret;
  }
  
  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof Attributes)) {
      return false;
    }
    return this.attrs.equals(((Attributes)other).attrs);
  }
  
  @Override
  public int hashCode() {
    return this.attrs.hashCode();
  }
  
  @Override
  public String toString() {
    String ret = "";
    for (Attribute attr : attrs.values()) {
      ret += attr.toString() + ":";
    }
    // Remove trailing colon
    return (ret.length() > 0 ? ret.substring(0, ret.length() - 1) : ret);
  }

  @Override
  public int compareTo(Attributes other) {
    return GUtil.compareCollections(this.attrs.values(), other.attrs.values());
  }
}
