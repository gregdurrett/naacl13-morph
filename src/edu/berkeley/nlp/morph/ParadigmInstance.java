package edu.berkeley.nlp.morph;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import edu.berkeley.nlp.morph.MarkovEditDistanceComputer.EditDistanceParams;
import edu.berkeley.nlp.morph.fig.Fmt;
import edu.berkeley.nlp.morph.fig.LogInfo;
import edu.berkeley.nlp.morph.util.Counter;
import edu.berkeley.nlp.morph.util.GUtil;
import edu.berkeley.nlp.morph.util.Iterators;

/**
 * Instance of a morphological paradigm.
 * 
 * @author gdurrett
 *
 */
public class ParadigmInstance {
  
  private final Form baseForm;
  private final SortedSet<Attributes> attrsSet;
  private final SortedMap<Attributes,List<Form>> inflForms;
  
  // Shows up in the Dreyer and Eisner CELEX data to indicate a form that is
  // never attested. Useful to look for so that we don't evaluate on these and
  // don't try to extract rules over nonexistent forms.
  public static final Form STAR_TOKEN = new Form("STAR");
    
  private static SortedMap<Attributes,List<Form>> convertToSingletonLists(SortedMap<Attributes,Form> origMap) {
    SortedMap<Attributes,List<Form>> newMap = new TreeMap<Attributes,List<Form>>();
    for (Attributes attrs : origMap.keySet()) {
      newMap.put(attrs, Collections.singletonList(origMap.get(attrs)));
    }
    return newMap;
  }
  
  public ParadigmInstance(Form baseForm, SortedMap<Attributes,List<Form>> inflForms) {
    this.baseForm = baseForm;
    this.attrsSet = (SortedSet<Attributes>)inflForms.keySet();
    this.inflForms = inflForms;
  }
  
  public ParadigmInstance(Form baseForm, SortedSet<Attributes> attrsSet, List<AnchoredMorphChange> morphChanges) {
    this(baseForm, convertToSingletonLists(inflect(baseForm, attrsSet, morphChanges)));
  }
  
  public boolean isStar(Attributes attrs) {
    List<Form> forms = this.inflForms.get(attrs);
    return forms.contains(STAR_TOKEN);
  }
  
  public boolean containsStar() {
    boolean containsStar = false;
    for (Attributes attrs : this.attrsSet) {
      containsStar = containsStar || isStar(attrs);
    }
    return containsStar;
  }
  
  private static SortedMap<Attributes,Form> inflect(Form baseForm, SortedSet<Attributes> attrsSet, List<AnchoredMorphChange> morphChanges) {
    // Sort by starting position so we can construct the forms with a left-to-right pass
    Collections.sort(morphChanges, new Comparator<AnchoredMorphChange>() {
      @Override
      public int compare(AnchoredMorphChange first, AnchoredMorphChange second) {
        return first.spanAppliedTo.compareTo(second.spanAppliedTo);
      }
    });
    // Check that the morph changes are all defined over the correct set of attributes
    for (AnchoredMorphChange morphChange : morphChanges) {
      assert attrsSet.equals(morphChange.change.rewrite.keySet());
    }
    SortedMap<Attributes,Form> inflForms = new TreeMap<Attributes,Form>();
    for (Attributes attrs : attrsSet) {
      Form newForm = new Form("");
      int currBaseFormIndex = 0;
      for (AnchoredMorphChange change : morphChanges) {
        int changeStart = change.spanAppliedTo.start;
        int changeEnd = change.spanAppliedTo.end;
        Form changeResult = change.change.rewrite.get(attrs);
        if (changeStart < currBaseFormIndex) {
          throw new IllegalArgumentException("Bad sequence of changes; last change ended at " +
                                             currBaseFormIndex + " but current on starts at " + changeStart);
        }
        newForm = newForm.append(baseForm.substring(currBaseFormIndex, changeStart)).append(changeResult);
        currBaseFormIndex = changeEnd;
      }
      if (currBaseFormIndex < baseForm.length()) {
        newForm = newForm.append(baseForm.substring(currBaseFormIndex));
      }
      inflForms.put(attrs, newForm);
    }
    return inflForms;
  }
  
  public Form baseForm() {
    return baseForm;
  }
  
//  public SortedMap<Attributes,Form> inflForms() {
//    return inflForms;
//  }
  
  public SortedSet<Attributes> getAttrSetSorted() {
    return attrsSet;
  }
  
  public Form getInflForm(Attributes attrs) {
    return inflForms.get(attrs).get(0);
  }
  
  public List<Form> getAllInflForms(Attributes attrs) {
    return inflForms.get(attrs);
  }
  
  public SortedSet<Attributes> getAttrsForForm(Form form) {
    SortedSet<Attributes> thisFormAttrsSet = new TreeSet<Attributes>();
    for (Attributes attrs : attrsSet) {
      if (getInflForm(attrs).equals(form)) {
        thisFormAttrsSet.add(attrs);
      }
    }
    return thisFormAttrsSet;
  }
  
  public Map<Attributes,Form> getAttrsFormMap() {
    SortedMap<Attributes,Form> map = new TreeMap<Attributes,Form>();
    for (Attributes attrs : attrsSet) {
      map.put(attrs, getInflForm(attrs));
    }
    return map;
  }
  
  public boolean doesExactlyMatchGold(ParadigmInstance goldInstance) {
    if (!this.attrsSet.equals(goldInstance.attrsSet)) {
      return false;
    }
    for (Attributes attrs : this.attrsSet) {
      if (!goldInstance.getAllInflForms(attrs).contains(this.getInflForm(attrs))) {
        return false;
      }
    }
    return true;
  }
  
  public int countMatchesGold(ParadigmInstance goldInstance, boolean allowStars) {
    int correctAllowStars = 0;
    int correctDisallowStars = 0;
    for (Attributes attrs : getAttrSetSorted()) {
      Form predForm = getInflForm(attrs);
      List<Form> goldForms = goldInstance.getAllInflForms(attrs);
      if (!goldInstance.isStar(attrs)) {
        if (goldForms.contains(predForm)) {
          correctDisallowStars++;
          correctAllowStars++;
        }
      } else {
        correctAllowStars++;
      }
    }
    return allowStars ? correctAllowStars : correctDisallowStars;
  }
  
  public int editDistanceToGold(ParadigmInstance goldInstance, boolean allowStars) {
    int totalEditDistance = 0;
    for (Attributes attrs : getAttrSetSorted()) {
      Form predForm = getInflForm(attrs);
      List<Form> goldForms = goldInstance.getAllInflForms(attrs);
      int minEd = Integer.MAX_VALUE;
      for (Form goldForm : goldForms) {
        int ed = (int)new MarkovEditDistanceComputer(EditDistanceParams.getStandardParams(predForm, goldForm, 0.0)).runEditDistance().cost;
        minEd = Math.min(ed, minEd);
      }
      if (!allowStars || !goldInstance.isStar(attrs)) {
        totalEditDistance += minEd;
      }
    }
    return totalEditDistance;
  }
  
  @Override
  public String toString() {
    String ret = "";
    for (Attributes key : inflForms.keySet()) {
      ret += baseForm.toString() + " => " + inflForms.get(key).toString() + " (" + key.toShortString() + ")\n";
    }
    return ret;
  }
  
  /**
   * Filters out paradigm instances that aren't defined over the most common
   * set of morphological attributes; this serves to reject things that either
   * have too few entries due to filtering or too many entries due to duplicated
   * forms or something like that. Usually, only a few entries are discarded.
   * @param insts
   */
  public static void filterNoncanonicalParadigmInstances(List<ParadigmInstance> insts) {
    Counter<Set<Attributes>> attrSetCounts = new Counter<Set<Attributes>>();
    for (ParadigmInstance inst : insts) {
      attrSetCounts.incrementCount(inst.getAttrSetSorted(), 1.0);
    }
    LogInfo.logss("Attribute set counts");
    for (Set<Attributes> attrSet : Iterators.able(attrSetCounts.asPriorityQueue())) {
      LogInfo.logss(attrSetCounts.getCount(attrSet) + ": " + GUtil.toStringSorted(attrSet));
    }
    Set<Attributes> mostCommonAttrsSet = attrSetCounts.argMax();
    double numKept = attrSetCounts.getCount(mostCommonAttrsSet);
    double numTossed = attrSetCounts.totalCount();
    LogInfo.logss("Keeping " + numKept + " / " + numTossed + " (" + Fmt.D(numKept/numTossed) + ")");
    Iterator<ParadigmInstance> itr = insts.iterator();
    while (itr.hasNext()) {
      ParadigmInstance next = itr.next();
      if (!next.getAttrSetSorted().equals(mostCommonAttrsSet)) {
        itr.remove();
      }
    }
  }

  
  /**
   * Filters out paradigm instances with undefined forms, represented by the token
   * STAR
   */
  public static void filterStars(List<ParadigmInstance> insts) {
    Iterator<ParadigmInstance> itr = insts.iterator();
    while (itr.hasNext()) {
      if (itr.next().containsStar()) {
        itr.remove();
      }
    }
  }
}
