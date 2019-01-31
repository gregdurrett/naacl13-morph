package edu.berkeley.nlp.morph;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Spot checks to make sure everything is loaded correctly.
 * @author gdurrett
 *
 */
public class CelexLoadingTest {

  @Test
  public void testWithoutCollapsing() {
    List<ParadigmInstance> instances = ParadigmInstanceReader.readParadigmInstancesCelex("data/celex-test.txt");
    assertEquals("Wrong number of instances loaded", 2, instances.size());
    Map<Attributes,Form> inflForms;
    inflForms = instances.get(0).getAttrsFormMap();
    assertEquals("Bad form", 29, inflForms.size());
    assertEquals("Bad form", new Form("waehren"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=Infinitive:POS=VERB")));
    // Different from below (they are collapsed)
    assertEquals("Bad form", new Form("waehrten"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=1st:Number=Plural:Tense=Past:Mood=Indicative:POS=VERB")));
    assertEquals("Bad form", new Form("waehrten"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=3rd:Number=Plural:Tense=Past:Mood=Indicative:POS=VERB")));
    // These next two are preserved since they differ
    assertEquals("Bad form", new Form("waehre"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=1st:Number=Singular:Tense=Present:Mood=Indicative:POS=VERB")));
    assertEquals("Bad form", new Form("waehrt"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=3rd:Number=Singular:Tense=Present:Mood=Indicative:POS=VERB")));
    // Smattering of other forms
    assertEquals("Bad form", new Form("waehrtet"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=2nd:Number=Plural:Tense=Past:Mood=Indicative:POS=VERB")));
    assertEquals("Bad form", new Form("gewaehrt"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=PastPart:POS=VERB")));
    assertEquals("Bad form", new Form("waehre"), inflForms.get(ParadigmInstanceReader.parseAttrs("Number=Singular:Mood=Imperative:POS=VERB")));
    inflForms = instances.get(1).getAttrsFormMap();
    assertEquals("Bad form", 29, inflForms.size());
    assertEquals("Bad form", new Form("werden"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=Infinitive:POS=VERB")));
    // Different from below (they are collapsed)
    assertEquals("Bad form", new Form("wurden"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=1st:Number=Plural:Tense=Past:Mood=Indicative:POS=VERB")));
    assertEquals("Bad form", new Form("wurden"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=3rd:Number=Plural:Tense=Past:Mood=Indicative:POS=VERB")));
    // These next two are preserved since they differ
    assertEquals("Bad form", new Form("werde"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=1st:Number=Singular:Tense=Present:Mood=Indicative:POS=VERB")));
    assertEquals("Bad form", new Form("wird"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=3rd:Number=Singular:Tense=Present:Mood=Indicative:POS=VERB")));
    // Smattering of other forms
    assertEquals("Bad form", new Form("wurdet"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=2nd:Number=Plural:Tense=Past:Mood=Indicative:POS=VERB")));
    assertEquals("Bad form", new Form("geworden"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=PastPart:POS=VERB")));
    assertEquals("Bad form", new Form("werde"), inflForms.get(ParadigmInstanceReader.parseAttrs("Number=Singular:Mood=Imperative:POS=VERB")));
  }
  
//  @Test
//  public void testWithCollapsing() {
//    ParadigmInstanceReader.readParadigmInstances("data/celex-test.txt", true);
//    List<ParadigmInstance> instances = ParadigmInstanceReader.readParadigmInstances("data/celex-test.txt", true);
//    assertEquals("Wrong number of instances loaded", 2, instances.size());
//    Map<Attributes,Form> inflForms;
//    inflForms = instances.get(0).inflForms();
//    assertEquals("Bad form", 22, inflForms.size());
//    assertEquals("Bad form", new Form("waehren"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=Infinitive:POS=VERB")));
//    // Different from above!
//    assertEquals("Bad form", new Form("waehrten"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=1st3rd:Number=Plural:Tense=Past:Mood=Indicative:POS=VERB")));
//    // These next two are preserved since they differ
//    assertEquals("Bad form", new Form("waehre"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=1st:Number=Singular:Tense=Present:Mood=Indicative:POS=VERB")));
//    assertEquals("Bad form", new Form("waehrt"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=3rd:Number=Singular:Tense=Present:Mood=Indicative:POS=VERB")));
//    // Smattering of other forms
//    assertEquals("Bad form", new Form("waehrtet"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=2nd:Number=Plural:Tense=Past:Mood=Indicative:POS=VERB")));
//    assertEquals("Bad form", new Form("gewaehrt"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=PastPart:POS=VERB")));
//    assertEquals("Bad form", new Form("waehre"), inflForms.get(ParadigmInstanceReader.parseAttrs("Number=Singular:Mood=Imperative:POS=VERB")));
//    inflForms = instances.get(1).inflForms();
//    assertEquals("Bad form", 22, inflForms.size());
//    assertEquals("Bad form", new Form("werden"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=Infinitive:POS=VERB")));
//    // Different from above!
//    assertEquals("Bad form", new Form("wurden"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=1st3rd:Number=Plural:Tense=Past:Mood=Indicative:POS=VERB")));
//    // These next two are preserved since they differ
//    assertEquals("Bad form", new Form("werde"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=1st:Number=Singular:Tense=Present:Mood=Indicative:POS=VERB")));
//    assertEquals("Bad form", new Form("wird"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=3rd:Number=Singular:Tense=Present:Mood=Indicative:POS=VERB")));
//    // Smattering of other forms
//    assertEquals("Bad form", new Form("wurdet"), inflForms.get(ParadigmInstanceReader.parseAttrs("Person=2nd:Number=Plural:Tense=Past:Mood=Indicative:POS=VERB")));
//    assertEquals("Bad form", new Form("geworden"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=PastPart:POS=VERB")));
//    assertEquals("Bad form", new Form("werde"), inflForms.get(ParadigmInstanceReader.parseAttrs("Number=Singular:Mood=Imperative:POS=VERB")));
//  }

  @Test
  public void testNewSet() {
    List<ParadigmInstance> instances = ParadigmInstanceReader.readParadigmInstancesCelex("data/celex-test-updated.txt");
    assertEquals("Wrong number of instances loaded", 2, instances.size());
    Map<Attributes,Form> inflForms;
    inflForms = instances.get(0).getAttrsFormMap();
    assertEquals("Bad form", 22, inflForms.size());
    assertEquals("Bad form", new Form("waehren"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=0i:POS=VERB")));
    // Different from above!
    assertEquals("Bad form", new Form("waehrten"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=13PIA:POS=VERB")));
    // These next two are preserved since they differ
    assertEquals("Bad form", new Form("waehre"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=1SIE:POS=VERB")));
    assertEquals("Bad form", new Form("waehrt"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=3SIE:POS=VERB")));
    // Smattering of other forms
    assertEquals("Bad form", new Form("waehrtet"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=2PIA:POS=VERB")));
    assertEquals("Bad form", new Form("gewaehrt"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=pA:POS=VERB")));
    assertEquals("Bad form", new Form("waehre"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=rS:POS=VERB")));
    inflForms = instances.get(1).getAttrsFormMap();
    assertEquals("Bad form", 22, inflForms.size());
    assertEquals("Bad form", new Form("werden"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=0i:POS=VERB")));
    // Different from above!
    assertEquals("Bad form", new Form("wurden"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=13PIA:POS=VERB")));
    // These next two are preserved since they differ
    assertEquals("Bad form", new Form("werde"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=1SIE:POS=VERB")));
    assertEquals("Bad form", new Form("wird"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=3SIE:POS=VERB")));
    // Smattering of other forms
    assertEquals("Bad form", new Form("wurdet"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=2PIA:POS=VERB")));
    assertEquals("Bad form", new Form("geworden"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=pA:POS=VERB")));
    assertEquals("Bad form", new Form("werde"), inflForms.get(ParadigmInstanceReader.parseAttrs("Type=rS:POS=VERB")));
  }

}
