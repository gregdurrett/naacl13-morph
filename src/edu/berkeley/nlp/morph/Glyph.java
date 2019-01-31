package edu.berkeley.nlp.morph;

/**
 * Abstraction layer between characters and the rest of the code in case there
 * are encoding problems
 * 
 * @author gdurrett
 *
 */
public class Glyph implements Comparable<Glyph> {
  
  public static Glyph BEGIN = new Glyph('[');
  public static Glyph END = new Glyph(']');
  
  public final char glyph;
  
  public Glyph(char glyph) {
    this.glyph = glyph;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof Glyph)) {
      return false;
    }
    return this.glyph == ((Glyph)other).glyph;
  }
  
  @Override
  public int hashCode() {
    return glyph * 101159;
  }

  @Override
  public String toString() {
    return "" + glyph;
  }

  @Override
  public int compareTo(Glyph o) {
    return (int)this.glyph - (int)o.glyph;
  }
}
