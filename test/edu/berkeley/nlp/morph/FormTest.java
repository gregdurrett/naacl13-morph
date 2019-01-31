package edu.berkeley.nlp.morph;

import static org.junit.Assert.*;

import org.junit.Test;


public class FormTest {

  @Test
  public void testEncoding() {
    Form form1 = new Form("hießen");
    Form form2 = new Form("русский");
    assertEquals("Encoding problems with: " + form1, 6, form1.length());
    assertEquals("Encoding problems with: " + form2, 7, form2.length());
    assertEquals("Encoding problems with: " + form1, new Glyph('ß'), form1.charAt(3));
    assertEquals("Encoding problems with: " + form2, new Glyph('с'), form2.charAt(3));
    assertEquals("Encoding problems with: " + form2, new Glyph('й'), form2.charAt(6));
  }
}
