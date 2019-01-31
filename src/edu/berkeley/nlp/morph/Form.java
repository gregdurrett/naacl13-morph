package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.morph.fig.ListUtils;
import edu.berkeley.nlp.morph.util.GUtil;

/**
 * Abstraction for a word form; we use this rather than Strings in case complex
 * character encoding nonsense has to be done with individual characters.
 * 
 * @author gdurrett
 *
 */
public class Form implements Comparable<Form> {

  private final List<Glyph> glyphs;
  
  public Form(String str) {
    this.glyphs = new ArrayList<Glyph>();
    for (int i = 0; i < str.length(); i++) {
      this.glyphs.add(new Glyph(str.charAt(i)));
    }
  }
  
  public Form(List<Glyph> glyphs) {
    this.glyphs = glyphs;
  }
  
  public Form substring(int start) {
    return substring(start, length());
  }
  
  public Form substring(int start, int end) {
    return new Form(glyphs.subList(start, end));
  }
  
  public Form reverse() {
    return new Form(ListUtils.reverseCopy(glyphs));
  }
  
  public int length() {
    return glyphs.size();
  }

  public Glyph charAt(int index) {
    return glyphs.get(index);
  }
  
  public Glyph charAtOrBoundary(int indexToRead) {
    Glyph ret =  null;
    if (indexToRead >= 0 && indexToRead < length()) {
      ret = charAt(indexToRead);
    } else if (indexToRead < 0) {
      ret = Glyph.BEGIN;
    } else {
      ret = Glyph.END;
    }
    return ret;
  }
  
  public Form append(Form other) {
    List<Glyph> newGlyphs = new ArrayList<Glyph>();
    newGlyphs.addAll(this.glyphs);
    newGlyphs.addAll(other.glyphs);
    return new Form(newGlyphs);
  }
  
  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof Form)) {
      return false;
    }
    return this.glyphs.equals(((Form)other).glyphs);
  }
  
  @Override
  public int hashCode() {
    return this.glyphs.hashCode();
  }

  @Override
  public String toString() {
    String ret = "";
    for (Glyph glyph : glyphs) {
      ret += glyph.toString();
    }
    return ret;
  }

  @Override
  public int compareTo(Form o) {
    return GUtil.compareCollections(this.glyphs, o.glyphs);
  }
}
