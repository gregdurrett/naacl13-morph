package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import edu.berkeley.nlp.morph.MarkovEditDistanceComputer.EditDistanceParams;
import edu.berkeley.nlp.morph.fig.LogInfo;

/**
 * Wrapper around a ParadigmInstance; when analyze() is called, stores edit
 * operations for each inflected form (the output of the Alignment step in
 * Section 3 of Durrett and DeNero (2013)), as well as the determined anchored
 * morph change set (the output of the Span Merging and Rule Extraction steps).
 * 
 * @author gdurrett
 *
 */
public class AnalyzedParadigmInstance {

  public static enum AlignmentType {
    BASIC, MAX_ALIGN, CONSISTENT;
  }
  
  public final double SWITCH_COST = 0.00001;
  
  public final ParadigmInstance inst;
  private final SortedMap<Attributes,List<Operation>> formAlignments;
  private final List<AnchoredMorphChange> extractedChanges;
  
  public AnalyzedParadigmInstance(ParadigmInstance inst) {
    this.inst = inst;
    this.formAlignments = new TreeMap<Attributes,List<Operation>>();
    this.extractedChanges = new ArrayList<AnchoredMorphChange>();
  }

  public List<AnchoredMorphChange> getCachedChanges() {
    return extractedChanges;
  }
  
  /**
   * Aligns each inflected form to the base form using the specified alignment
   * algorithm
   * @param alignmentType
   */
  public void analyze(AlignmentType alignmentType) {
    this.formAlignments.clear();
    // For BASIC and MAX_ALIGN, we don't need to do anything fancy, just run edit distance
    // for each pair with the right parameters
    if (alignmentType == AlignmentType.BASIC || alignmentType == AlignmentType.MAX_ALIGN) {
      analyzeSimple(alignmentType);
    } else {
      analyzeConsistent();
    }
  }
  
  public void analyzeSimple(AlignmentType alignmentType) {
    for (Entry<Attributes,Form> attrsFormPair : inst.getAttrsFormMap().entrySet()) {
      Attributes attrs = attrsFormPair.getKey();
      Form inflForm = attrsFormPair.getValue();
      EditDistanceParams params;
      if (alignmentType == AlignmentType.BASIC) {
        params = EditDistanceParams.getStandardParams(inst.baseForm(), inflForm, SWITCH_COST);
      } else {
        params = EditDistanceParams.getMaxAlignmentParams(inst.baseForm(), inflForm, SWITCH_COST);
      }
      MarkovEditDistanceComputer computer = new MarkovEditDistanceComputer(params);
      formAlignments.put(attrs, computer.runEditDistance().ops);
    }
  }
  
  public void analyzeConsistent() {
    Form baseForm = inst.baseForm();
    boolean someChange = true;
    double[] oldCosts = new double[baseForm.length()];
    Arrays.fill(oldCosts, -1);
    double[] newCosts = new double[baseForm.length()];
    Arrays.fill(newCosts, 0);
    int numItrs = 0;
    while (someChange) {
      if (numItrs > 10) {
        LogInfo.logss("Aborting after 10 iterations");
      }
      someChange = false;
      for (Entry<Attributes,Form> attrsFormPair : inst.getAttrsFormMap().entrySet()) {
        Attributes attrs = attrsFormPair.getKey();
        Form inflForm = attrsFormPair.getValue();
        EditDistanceParams params = EditDistanceParams.getWeightedMaxAlignmentParams(baseForm, inflForm, oldCosts, SWITCH_COST);
        MarkovEditDistanceComputer computer = new MarkovEditDistanceComputer(params);
        AlignedFormPair alignedPair = computer.runEditDistance();
        if (!formAlignments.containsKey(attrs) || !alignedPair.ops.equals(formAlignments.get(attrs))) {
          someChange = true;
        }
        updateAlignmentCosts(newCosts, alignedPair.ops);
        formAlignments.put(attrs, alignedPair.ops);
      }
      oldCosts = newCosts;
      newCosts = new double[baseForm.length()];
      Arrays.fill(newCosts, 0);
      numItrs++;
    }
  }
  
  /**
   * Updates the alignment cost vector given the alignment. Note that this vector
   * should be more highly negative if something is aligned more times.
   * @param currCosts
   * @param ops
   */
  private void updateAlignmentCosts(double[] currCosts, List<Operation> ops) {
    int srcIndex = 0;
    for (Operation op : ops) {
      if (op == Operation.EQUAL) {
        currCosts[srcIndex]--;
      }
      // The source pointer advances with DELETE, SUBST, and EQUAL
      if (op != Operation.INSERT) {
        srcIndex++;
      }
    }
  }
  
  /**
   * After we've aligned each inflected form to the base form, extracts the grouped
   * changes that characterize this paradigm
   * @param collapseAdjacentSpans
   * @return
   */
  public List<AnchoredMorphChange> extractAndCacheChanges(boolean collapseAdjacentSpans) {
    assert !formAlignments.isEmpty() : "Must call analyze() before extractAndCacheChanges()";
    List<AnchoredSpan> changedSpans = getOverallChangedSpans(collapseAdjacentSpans);
    this.extractedChanges.clear();
    for (AnchoredSpan changedSpan : changedSpans) {
      this.extractedChanges.add(extractChangeOverSpan(changedSpan));
    }
    return this.extractedChanges;
  }
  
  private List<AnchoredSpan> getOverallChangedSpans(boolean collapseAdjacentSpans) {
    // Find the spans 
    List<AnchoredSpan> spans = new ArrayList<AnchoredSpan>();
    for (List<Operation> opSequence : this.formAlignments.values()) {
      spans.addAll(AnchoredSpan.getChangedSpans(this.inst.baseForm(), opSequence));
      spans = AnchoredSpan.collapseAll(spans, collapseAdjacentSpans);
    }
    return spans;
  }
  
  private AnchoredMorphChange extractChangeOverSpan(AnchoredSpan span) {
    SortedMap<Attributes,Form> changeMap = new TreeMap<Attributes,Form>();
    for (Attributes attrs : this.formAlignments.keySet()) {
      Form inflForm = this.inst.getInflForm(attrs);
      List<Operation> ops = this.formAlignments.get(attrs);
      changeMap.put(attrs, extractSpanTargetSide(inflForm, ops, span));
    }
    MorphChange change = new MorphChange(inst.baseForm().substring(span.start, span.end), changeMap);
    return new AnchoredMorphChange(change, span, this);
  }
  
  private Form extractSpanTargetSide(Form inflForm, List<Operation> ops, AnchoredSpan srcSpan) {
    assert !ops.isEmpty();
    int opIndex = 0, srcIndex = 0, trgIndex = 0;
    int trgStart = -1, trgEnd = -1;
    for (; opIndex < ops.size(); opIndex++) {
      Operation op = ops.get(opIndex);
      // The first time we hit the target source span, recording the target side
      // index as the start of the span we want to extract.
      // (We might sit on srcSpan.start for a while if we're inserting, but we
      // want these insertions.)
      if (srcIndex == srcSpan.start && trgStart == -1) {
        trgStart = trgIndex;
      }
      // Need all trailing insertions, so make sure we don't stop until we find
      // an operation that would really cause us to exceed our stopping point.
      if (srcIndex == srcSpan.end && op != Operation.INSERT) {
        trgEnd = trgIndex;
        break;
      }
      // The source pointer advances with DELETE, SUBST, and EQUAL
      if (op != Operation.INSERT) {
        srcIndex++;
      }
      // The target pointer advances with INSERT, SUBST, and EQUAL
      if (op != Operation.DELETE) {
        trgIndex++;
      }
    }
    // Only true if we're extracting from a zero-width span at the very end
    if (trgStart == -1) {
      assert trgEnd == -1;
      trgStart = trgIndex;
    }
    // Happens whenever we end with insertions
    if (trgEnd == -1) {
      trgEnd = trgIndex;
    }
    assert trgStart != -1: ops + " " + srcSpan + " " + inflForm;
    return inflForm.substring(trgStart, trgEnd);
  }
}
