package edu.berkeley.nlp.morph;

/**
 * Maintains pointers into a Form around an AnchoredSpan, and lets us step
 * forward and backward from the span as well as backwards from the end of the
 * form for pattern-matching purposes.
 * 
 * @author gdurrett
 *
 */
public class FormReader {

  private final Form form;
  private int beforeIndex;
  private int afterIndex;
  private int suffixIndex;

  public FormReader(AnchoredSpan span) {
    this(span.form, span.start, span.end);
  }
  
  public FormReader(Form form, int spanStart, int spanEnd) {
    this.form = form;
    this.beforeIndex = spanStart - 1;
    this.afterIndex = spanEnd;
    this.suffixIndex = form.length() - 1;
  }
  
  public Glyph readAndAdvance(PatternType patternType) {
    int indexToRead = -1;
    if (patternType == PatternType.BEFORE) {
      indexToRead = beforeIndex;
      beforeIndex--;
    } else if (patternType == PatternType.AFTER) {
      indexToRead = afterIndex;
      afterIndex++;
    } else if (patternType == PatternType.SUFFIX) {
      indexToRead = suffixIndex;
      suffixIndex--;
    } else {
      throw new RuntimeException("Bad pattern type for advancing FormReader: " + patternType);
    }
    return this.form.charAtOrBoundary(indexToRead);
  }
}
