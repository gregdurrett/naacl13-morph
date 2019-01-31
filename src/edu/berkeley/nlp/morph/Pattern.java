package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents an ordered pattern: first matches a span of the given Form, then
 * tries to match each PatternElement in order. PatternElements have one of three
 * types (BEFORE, AFTER, SUFFIX) which indicates where they match in the word, and
 * each one can be thought of as advancing a corresponding pointer into the form.
 * For example, if you have a base form "form" and PatternElements (a, BEFORE) (b, BEFORE),
 * they will match the span baform. See the main() below for examples.
 * 
 * @author gdurrett
 *
 */
public class Pattern {

  public static class PatternElement {
    public final PatternType type;
    public final Glyph glyph;
    
    public PatternElement(PatternType type, Glyph glyph) {
      this.type = type;
      this.glyph = glyph;
    }

    @Override
    public boolean equals(Object other) {
      if (other == null || !(other instanceof PatternElement)) {
        return false;
      }
      PatternElement rhs = (PatternElement)other;
      return this.type == rhs.type && this.glyph.equals(rhs.glyph);
    }
    
    @Override
    public int hashCode() {
      return type.hashCode() * 136347231 + glyph.hashCode();
    }
  }
  
  public final Form span;
  public final List<PatternElement> patternElts;

  public Pattern(Form span) {
    this.span = span;
    this.patternElts = new ArrayList<PatternElement>();
  }
  
  public Pattern(Form span, List<PatternElement> pattern) {
    this.span = span;
    this.patternElts = pattern;
  }
  
  public Pattern(Pattern currPattern, PatternElement extension) {
    this.span = currPattern.span;
    this.patternElts = new ArrayList<PatternElement>(currPattern.patternElts);
    this.patternElts.add(extension);
  }

  public List<AnchoredSpan> findMatchingSpans(Form form) {
    List<Integer> posns = findMatchingStartPosns(form);
    List<AnchoredSpan> spans = new ArrayList<AnchoredSpan>();
    for (Integer posn : posns) {
      spans.add(new AnchoredSpan(form, posn.intValue(), posn.intValue() + span.length()));
    }
    return spans;
  }
  
  public List<Integer> findMatchingStartPosns(Form form) {
    List<Integer> posns = new ArrayList<Integer>();
    for (int i = 0; i < form.length() + 1 - span.length(); i++) {
      if (isStartPosn(form, i)) {
        posns.add(i);
      }
    }
    return posns;
  }
  
  public boolean isStartPattern() {
    for (PatternElement patternElt : patternElts) {
      if (patternElt.type == PatternType.BEFORE) {
        return patternElt.glyph.equals(Glyph.BEGIN);
      }
    }
    return false;
  }
  
  public boolean isEndPattern() {
    for (PatternElement patternElt : patternElts) {
      if (patternElt.type == PatternType.AFTER) {
        return patternElt.glyph.equals(Glyph.END);
      }
    }
    return false;
  }
  
  public boolean isLessRestrictiveThan(Pattern other) {
    return other.getSpanAndNeighborhood().contains(this.getSpanAndNeighborhood()) &&
        other.getSuffixAsStr().contains(this.getSuffixAsStr());
  }
  
  public boolean isStrictlyLessRestrictiveThan(Pattern other) {
    return this.isLessRestrictiveThan(other) && !this.matchesTheSameSpansAs(other);
  }
  
  public boolean matchesTheSameSpansAs(Pattern other) {
    return this.getSpanAndNeighborhood().equals(other.getSpanAndNeighborhood()) &&
        this.getSuffixAsStr().equals(other.getSuffixAsStr());
  }
  
  private String getSpanAndNeighborhood() {
    String beforeStr = "", afterStr = "";
    for (int i = 0; i < patternElts.size(); i++) {
      PatternElement patternElt = patternElts.get(i);
      switch (patternElt.type) {
        case BEFORE:
          beforeStr = patternElt.glyph + beforeStr;
          break;
        case AFTER:
          afterStr += patternElt.glyph;
          break;
        default:
          break;
      }
    }
    return beforeStr + span.toString() + afterStr;
  }
  
  private String getSuffixAsStr() {
    String suffixStr = "";
    for (int i = 0; i < patternElts.size(); i++) {
      PatternElement patternElt = patternElts.get(i);
      switch (patternElt.type) {
        case SUFFIX:
          suffixStr = patternElt.glyph + "(" + i + ")" + suffixStr;
          break;
        default:
          break;
      }
    }
    return suffixStr;
  }
  
  public String toString() {
    String beforeStr = "", afterStr = "", suffixStr = "";
    for (int i = 0; i < patternElts.size(); i++) {
      PatternElement patternElt = patternElts.get(i);
      switch (patternElt.type) {
        case BEFORE:
          beforeStr = patternElt.glyph + "(" + i + ")" + beforeStr;
          break;
        case AFTER:
          afterStr = afterStr + patternElt.glyph + "(" + i + ")";
          break;
        case SUFFIX:
          suffixStr = patternElt.glyph + "(" + i + ")" + suffixStr;
          break;
      }
    }
    return beforeStr + "{" + span.toString() + "}" + afterStr + ",end=" + suffixStr;
  }
  
  private boolean isStartPosn(Form form, int startPosn) {
    // If the span doesn't match, it's over
    if (!form.substring(startPosn, startPosn + this.span.length()).equals(this.span)) {
      return false;
    }
    FormReader formReader = new FormReader(form, startPosn, startPosn + span.length());
    for (PatternElement element : patternElts) {
      Glyph glyph = formReader.readAndAdvance(element.type);
      if (!glyph.equals(element.glyph)) {
        return false;
      }
    }
    return true;
  }
  
  public static void main(String[] args) {
    Pattern p1 = new Pattern(new Form("ab"), Arrays.asList(new PatternElement[] { new PatternElement(PatternType.BEFORE, new Glyph('g')) }));
    Pattern p2 = new Pattern(new Form("abc"));
    Pattern p3 = new Pattern(new Form("ab"), Arrays.asList(new PatternElement[] {}));
//    Pattern p4 = new Pattern(new Form("ab"), Arrays.asList(new PatternElement[] { new PatternElement(PatternType.BEFORE, new Glyph('g')) }));
    System.out.println("Should be false, false, false, true");
    System.out.println(p1.isLessRestrictiveThan(p2) + " " + p2.isLessRestrictiveThan(p1) + " " + p1.isLessRestrictiveThan(p3) + " " + p3.isLessRestrictiveThan(p1));
    Pattern nonstart = new Pattern(new Form("abc"));
    Pattern start = new Pattern(new Form("ab"), Arrays.asList(new PatternElement[] { new PatternElement(PatternType.BEFORE, Glyph.BEGIN) }));
    Pattern end = new Pattern(new Form("ab"), Arrays.asList(new PatternElement[] { new PatternElement(PatternType.AFTER, Glyph.END) }));
    System.out.println("Should be false, false, true, true");
    System.out.println(p1.isStartPattern() + " " + nonstart.isStartPattern() + " " + start.isStartPattern() + " " + end.isEndPattern());
    Pattern falseStart = new Pattern(new Form("ab"), Arrays.asList(new PatternElement[] { new PatternElement(PatternType.BEFORE, new Glyph('a')), new PatternElement(PatternType.BEFORE, Glyph.BEGIN) }));
    System.out.println("Should be false");
    System.out.println(falseStart.isStartPattern());
  }
}
