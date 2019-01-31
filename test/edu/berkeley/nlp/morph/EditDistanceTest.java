package edu.berkeley.nlp.morph;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.berkeley.nlp.morph.MarkovEditDistanceComputer.EditDistanceParams;


public class EditDistanceTest {

  @Test
  public void testSimpleEditDistance() {
    {
      EditDistanceParams params = EditDistanceParams.getStandardParams(new Form("aaaaaa"), new Form("aaaaaa"), 0);
      MarkovEditDistanceComputer computer = new MarkovEditDistanceComputer(params);
      AlignedFormPair alignedPair = computer.runEditDistance();
      assertEquals("Bad edit ops", Operation.stringToOps("======"), alignedPair.ops);
      assertEquals("Bad cost", 0, alignedPair.cost, 0.0000001);
    }
    {
      EditDistanceParams params = EditDistanceParams.getStandardParams(new Form("aaaaaa"), new Form("baaacaaad"), 0);
      MarkovEditDistanceComputer computer = new MarkovEditDistanceComputer(params);
      AlignedFormPair alignedPair = computer.runEditDistance();
      assertEquals("Bad edit ops", Operation.stringToOps("I===I===I"), alignedPair.ops);
      assertEquals("Bad cost", 3, alignedPair.cost, 0.0000001);
    }
    {
      EditDistanceParams params = EditDistanceParams.getStandardParams(new Form("staffed"), new Form("stuff"), 0);
      MarkovEditDistanceComputer computer = new MarkovEditDistanceComputer(params);
      AlignedFormPair alignedPair = computer.runEditDistance();
      assertEquals("Bad edit ops", Operation.stringToOps("==S==DD"), alignedPair.ops);
      assertEquals("Bad cost", 3, alignedPair.cost, 0.0000001);
    }
  }
  
  public void testEditDistanceSwitchCost() {
    // heissen -> hiesst: choices are
    // =SS=={SD,DS}
    // =D=I=={SD,DS}
    // =I=D=={SD,DS}
    // =D=S=SS
    {
      // Penalty for switches: should get =SS=...
      // There are three switches in the best ones 
      EditDistanceParams params = EditDistanceParams.getStandardParams(new Form("heissen"), new Form("hiesst"), 0.1);
      MarkovEditDistanceComputer computer = new MarkovEditDistanceComputer(params);
      AlignedFormPair alignedPair = computer.runEditDistance();
      assertEquals("Bad edit ops", Operation.charToOp('='), alignedPair.ops.get(0));
      assertEquals("Bad edit ops", Operation.charToOp('S'), alignedPair.ops.get(1));
      assertEquals("Bad edit ops", Operation.charToOp('S'), alignedPair.ops.get(2));
      assertEquals("Bad edit ops", Operation.charToOp('='), alignedPair.ops.get(3));
      assertEquals("Bad cost", 4.3, alignedPair.cost, 0.0000001);
    }
    {
      // Bonus for switches: should get =D=... or =I=...
      // There are five switches in the best ones
      EditDistanceParams params = EditDistanceParams.getStandardParams(new Form("heissen"), new Form("hiesst"), -0.1);
      MarkovEditDistanceComputer computer = new MarkovEditDistanceComputer(params);
      AlignedFormPair alignedPair = computer.runEditDistance();
      assertEquals("Bad edit ops", Operation.charToOp('='), alignedPair.ops.get(0));
      assertTrue("Bad edit ops", Operation.charToOp('I') == alignedPair.ops.get(1) || Operation.charToOp('D') == alignedPair.ops.get(2));
      assertEquals("Bad edit ops", Operation.charToOp('='), alignedPair.ops.get(2));
      assertEquals("Bad cost", 3.5, alignedPair.cost, 0.0000001);
    }
  }

  @Test
  public void testMaxAlignEditDistance() {
    {
      // We would use six substitutes under normal edit distance, but here we can align two things
      // if we instead use four deletions and four insertions, so max-align gives a different result
      EditDistanceParams params = EditDistanceParams.getMaxAlignmentParams(new Form("aaaagg"), new Form("ggbbbb"), 0);
      MarkovEditDistanceComputer computer = new MarkovEditDistanceComputer(params);
      AlignedFormPair alignedPair = computer.runEditDistance();
      assertEquals("Bad edit ops", Operation.stringToOps("DDDD==IIII"), alignedPair.ops);
      assertEquals("Bad cost", -2, alignedPair.cost, 0.0000001);
    }
  }

}
