package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.berkeley.nlp.morph.AnalyzedParadigmInstance.AlignmentType;
import edu.berkeley.nlp.morph.fig.LogInfo;
import edu.berkeley.nlp.morph.fig.StrUtils;
import edu.berkeley.nlp.morph.util.Counter;
import edu.berkeley.nlp.morph.util.GUtil;
import edu.berkeley.nlp.morph.util.Iterators;

/**
 * Name is slightly misleading, basically just contains the analyzed training set
 * and a mapping from each morphological change to forms where it occurs. This
 * is the output of the procedure described in Section 3 of Durrett and DeNero (2013).
 * 
 * @author gdurrett
 *
 */
public class ExtractedModel {
  
  // Union spans that are adjacent to one another.
  // Setting this to false might work but is untested. It will generally be bad
  // because adjacent changes will not be predicted at test time, so these are
  // no longer in the model capacity.
  public static final boolean COLLAPSE_ADJACENT_SPANS = true;

  public final List<AnalyzedParadigmInstance> analyzedInstances;
  public final Map<MorphChange,Set<AnchoredMorphChange>> extractedMorphChanges;
  
  private ExtractedModel(List<AnalyzedParadigmInstance> analyzedInstances,
                        Map<MorphChange,Set<AnchoredMorphChange>> extractedMorphChanges) {
    this.analyzedInstances = analyzedInstances;
    this.extractedMorphChanges = extractedMorphChanges;
  }

  /**
   * Takes a list of paradigm instances, which should have been prefiltered using
   * ParadigmInstance.filterNoncanonicalParadigmInstances
   * @param paradigmInstances
   * @return
   */
  public static ExtractedModel extractChanges(List<ParadigmInstance> paradigmInstances, AlignmentType alignmentType) {
    // First, analyze the instances
    List<AnalyzedParadigmInstance> analyzedInstances = new ArrayList<AnalyzedParadigmInstance>();
    long nanoTime = System.nanoTime();
    GUtil.logsEveryNReset();
    for (ParadigmInstance instance : paradigmInstances) {
      GUtil.logsEveryN("Analyzing", 500);
      AnalyzedParadigmInstance analyzedInstance = new AnalyzedParadigmInstance(instance);
      analyzedInstance.analyze(alignmentType);
      analyzedInstances.add(analyzedInstance);
    }
    LogInfo.logss("Analysis in " + (System.nanoTime() - nanoTime)/1000000 + " millis");
    // Now extract the changes.
    Map<MorphChange,Set<AnchoredMorphChange>> morphChanges = new HashMap<MorphChange,Set<AnchoredMorphChange>>();
    GUtil.logsEveryNReset();
    for (AnalyzedParadigmInstance instance : analyzedInstances) {
      GUtil.logsEveryN("Extracting", 500);
      List<AnchoredMorphChange> extractedChanges = instance.extractAndCacheChanges(COLLAPSE_ADJACENT_SPANS);
      for (AnchoredMorphChange extractedChange : extractedChanges) {
        MorphChange change = extractedChange.change;
        if (!morphChanges.containsKey(change)) {
          morphChanges.put(change, new HashSet<AnchoredMorphChange>());
        }
        morphChanges.get(change).add(extractedChange);
      }
    }
    LogInfo.logss("Extraction in " + (System.nanoTime() - nanoTime)/1000000 + " millis");
    return new ExtractedModel(analyzedInstances, morphChanges);
  }
  
  public List<MorphChange> getChangesInModelCapacity() {
    return new ArrayList<MorphChange>(extractedMorphChanges.keySet());
  }
  
  public void printModel() {
    Counter<MorphChange> occurrenceCounts = new Counter<MorphChange>();
    int numAboveTwo = 0;
    int numAboveThree = 0;
    for (MorphChange key : extractedMorphChanges.keySet()) {
      int size = extractedMorphChanges.get(key).size();
      occurrenceCounts.setCount(key, size);
      if (size >= 2) {
        numAboveTwo++;
      }
      if (size >= 3) {
        numAboveThree++;
      }
    }
    String ret = "==============\n";
    ret += "Found " + extractedMorphChanges.size() + " changes, " + numAboveTwo
        + " occurred in >=2 forms, " + numAboveThree + " in >=3\n";
    for (MorphChange key : Iterators.able(occurrenceCounts.asPriorityQueue())) {
      ret += key.toString() + "\n";
      String formsList = "";
      int counter = 0;
      AnalyzedParadigmInstance firstInst = null;
      for (AnchoredMorphChange change : extractedMorphChanges.get(key)) {
        if (counter >= 100) {
          break;
        }
        formsList += change.spanAppliedTo.form.toString() + ", ";
        firstInst = change.instanceAppliedTo;
        counter++;
      }
      if (firstInst != null) {
        formsList = formsList.substring(0, formsList.length() - 2);
      }
      ret += "   found in " + extractedMorphChanges.get(key).size() + " forms, such as: " + formsList + "\n";
      ret += firstInst.inst.toString();
      ret += "--------------\n";
      // Log everything up until here and clear the string or this will get really slow
      LogInfo.logss(ret);
      ret = "";
    }
    LogInfo.logss("==============");
  }
  
  /**
   * Prints the changes in a LaTeX table.
   */
  public void printChangesAsLatex(List<Form> formsToPrintChangesFor) {
    Set<Attributes> attrsSet = this.analyzedInstances.get(0).inst.getAttrSetSorted();
    int numAttrs = attrsSet.size();
    Set<MorphChange> morphChangesToPrint = new HashSet<MorphChange>();
    for (MorphChange morphChange : extractedMorphChanges.keySet()) {
      for (AnchoredMorphChange extractedChange : extractedMorphChanges.get(morphChange)) {
        if (formsToPrintChangesFor.contains(extractedChange.instanceAppliedTo.inst.baseForm())) {
          morphChangesToPrint.add(morphChange);
        }
      }
    }
    assert numAttrs > 0 && this.extractedMorphChanges.size() > 0;
    String[][] table = new String[numAttrs + 1][morphChangesToPrint.size() + 1];
    // First row is all blank, just the heading
    Arrays.fill(table[0], "");
    table[0][0] = "Attributes";
    int row = 1;
    for (Attributes attrs : attrsSet) {
       table[row][0] = attrs.toShortString();
       row++;
    }
    int col = 1;
    for (MorphChange morphChange : morphChangesToPrint) {
      table[0][col] = " (" + extractedMorphChanges.get(morphChange).size() + ")";
      row = 1;
      for (Attributes attrs : morphChange.rewrite.keySet()) {
        table[row][col] = morphChange.rewrite.get(attrs).toString();
        row++;
      }
      col++;
    }
    int[] columnMaxes = new int[table[0].length];
    Arrays.fill(columnMaxes, 0);
    for (int j = 0; j < columnMaxes.length; j++) {
      for (int i = 0; i < table.length; i++) {
        columnMaxes[j] = Math.max(columnMaxes[j], table[i][j].length());
      }
    }
    String tableStr = "";
    for (int i = 0; i < table.length; i++) {
      for (int j = 0; j < table[i].length; j++) {
        tableStr += table[i][j] + StrUtils.repeat(" ", columnMaxes[j] - table[i][j].length()) + " & ";
      }
      tableStr = tableStr.substring(0, tableStr.length() - 3) + " \\\\ \n";
    }
    LogInfo.logss(tableStr);
  }
}
