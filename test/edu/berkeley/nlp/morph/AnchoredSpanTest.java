package edu.berkeley.nlp.morph;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class AnchoredSpanTest {

  @Test
  public void testCollapseAll() {
    {
      Form f = new Form("whatever");
      List<AnchoredSpan> initialSpans = Arrays.asList(new AnchoredSpan[]
          { new AnchoredSpan(f, 0, 1), new AnchoredSpan(f, 0, 2), new AnchoredSpan(f, 0, 3) } );
      List<AnchoredSpan> finalSpans = Arrays.asList(new AnchoredSpan[] { new AnchoredSpan(f, 0, 3) } );
      List<AnchoredSpan> predFinalSpans = AnchoredSpan.collapseAllOverlapping(initialSpans);
      Collections.sort(predFinalSpans);
      assertEquals("Not the right list of spans", finalSpans, predFinalSpans);
    }
    {
      Form f = new Form("whatever");
      List<AnchoredSpan> initialSpans = Arrays.asList(new AnchoredSpan[]
          { new AnchoredSpan(f, 0, 1), new AnchoredSpan(f, 1, 3) } );
      List<AnchoredSpan> finalSpans = Arrays.asList(new AnchoredSpan[]
          { new AnchoredSpan(f, 0, 1), new AnchoredSpan(f, 1, 3) } );
      List<AnchoredSpan> predFinalSpans = AnchoredSpan.collapseAllOverlapping(initialSpans);
      Collections.sort(predFinalSpans);
      assertEquals("Not the right list of spans", finalSpans, predFinalSpans);
    }
    {
      Form f = new Form("whatever");
      List<AnchoredSpan> initialSpans = Arrays.asList(new AnchoredSpan[]
          { new AnchoredSpan(f, 0, 1), new AnchoredSpan(f, 1, 3), new AnchoredSpan(f, 2, 4) } );
      List<AnchoredSpan> finalSpans = Arrays.asList(new AnchoredSpan[]
          { new AnchoredSpan(f, 0, 1), new AnchoredSpan(f, 1, 4) } );
      List<AnchoredSpan> predFinalSpans = AnchoredSpan.collapseAllOverlapping(initialSpans);
      Collections.sort(predFinalSpans);
      assertEquals("Not the right list of spans", finalSpans, predFinalSpans);
    }
  }

  @Test
  public void testCollapseAllTouching() {
    {
      Form f = new Form("whatever");
      List<AnchoredSpan> initialSpans = Arrays.asList(new AnchoredSpan[]
          { new AnchoredSpan(f, 0, 1), new AnchoredSpan(f, 1, 3) } );
      List<AnchoredSpan> finalSpans = Arrays.asList(new AnchoredSpan[]
          { new AnchoredSpan(f, 0, 3) } );
      List<AnchoredSpan> predFinalSpans = AnchoredSpan.collapseAllTouching(initialSpans);
      Collections.sort(predFinalSpans);
      assertEquals("Not the right list of spans", finalSpans, predFinalSpans);
    }
  }
}
