package edu.berkeley.nlp.morph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import edu.berkeley.nlp.morph.Attributes.Attribute;
import edu.berkeley.nlp.morph.fig.IOUtils;
import edu.berkeley.nlp.morph.fig.LogInfo;
import edu.berkeley.nlp.morph.util.GUtil;
import edu.berkeley.nlp.morph.util.Iterators;

/**
 * Reader for our Wiktionary data and also the CELEX data of Dreyer and Eisner (2011).
 * 
 * @author gdurrett
 *
 */
public class ParadigmInstanceReader {
  
  public static final String WIKTIONARY_FIELD_DELIMITER = ",";
  public static final String WIKTIONARY_ALTERNATIVE_DELIMITER = "====="; // should never match, there are no alternatives given
  public static final String CELEX_FIELD_DELIMITER = "\t";
  public static final String CELEX_ALTERNATIVE_DELIMITER = ",";

  public static List<ParadigmInstance> readParadigmInstancesWiktionary(String fileName) {
    return readParadigmInstances(fileName, WIKTIONARY_FIELD_DELIMITER, WIKTIONARY_ALTERNATIVE_DELIMITER);
  }
 
  public static List<ParadigmInstance> readParadigmInstancesCelex(String fileName) {
    return readParadigmInstances(fileName, CELEX_FIELD_DELIMITER, CELEX_ALTERNATIVE_DELIMITER);
  }
  
  private static List<ParadigmInstance> readParadigmInstances(String fileName, String fieldDelimiter, String alternativeDelimiter) {
    LogInfo.logss("Loading from " + fileName);
    Map<Form,SortedMap<Attributes,List<Form>>> protoInstances = new HashMap<Form,SortedMap<Attributes,List<Form>>>();
    Iterator<String> lines = IOUtils.lineIterator(IOUtils.openInHard(fileName));
    GUtil.logsEveryNReset();
    int numDuplicatesDiscarded = 0;
    for (String line : Iterators.able(lines)) {
      if (line.trim().equals("")) {
        continue;
      }
//      GUtil.logsEveryN("Another 50000 lines read", 50000);
      String[] fields = line.split(fieldDelimiter);
      assert fields.length == 3 : fields.length + " is not 3 fields for line: " + line;
      List<Form> inflForms = new ArrayList<Form>();
      for (String alternative : fields[0].split(alternativeDelimiter)) {
        inflForms.add(new Form(alternative));
      }
      Form base = new Form(fields[1]);
      Attributes attrs = parseAttrs(fields[2]);
      if (!protoInstances.containsKey(base)) {
        protoInstances.put(base, new TreeMap<Attributes,List<Form>>());
      }
      if (protoInstances.get(base).containsKey(attrs)) {
//        LogInfo.logss("Warning: " + base + " already contains entry for " + attrs.toString());
        numDuplicatesDiscarded++;
      }
      protoInstances.get(base).put(attrs, inflForms);
    }
    List<ParadigmInstance> instances = new ArrayList<ParadigmInstance>();
    for (Form key : protoInstances.keySet()) {
      SortedMap<Attributes,List<Form>> protoInstance = protoInstances.get(key);
//      if (collapse1stAnd3rd) {
//        protoInstance = collapseFirstAndThird(protoInstance);
//      }
      instances.add(new ParadigmInstance(key, protoInstance));
    }
    LogInfo.logss(instances.size() + " templates read in, " + numDuplicatesDiscarded + " duplicate entries discarded");
    ParadigmInstance.filterNoncanonicalParadigmInstances(instances);
    return instances;
  }

  static Attributes parseAttrs(String attrsLine) {
    SortedSet<Attribute> protoAttrs = new TreeSet<Attribute>();
    String[] attrsLineArr = attrsLine.split(":");
    assert attrsLineArr.length > 0 : "Bad attrs line: " + attrsLine;
    for (String entry : attrsLine.split(":")) {
      String[] entryArr = entry.split("=");
      assert entryArr.length == 2 : "Bad attrs line: " + attrsLine;
      protoAttrs.add(new Attribute(entryArr[0], entryArr[1]));
    }
    return new Attributes(protoAttrs);
  }
  
  /**
   * Dreyer and Eisner 2011 don't separate 1st and 3rd except for singular indicative
   * present, because CELEX does not do this.
   * @param protoInstance
   */
  private static SortedMap<Attributes,Form> collapseFirstAndThird(SortedMap<Attributes,Form> protoInstance) {
    Set<Attributes> attrsToSave = new HashSet<Attributes>();
    attrsToSave.add(parseAttrs("Person=1st:Number=Singular:Tense=Present:Mood=Indicative:POS=VERB"));
    attrsToSave.add(parseAttrs("Person=3rd:Number=Singular:Tense=Present:Mood=Indicative:POS=VERB"));
    SortedMap<Attributes,Form> newInstance = new TreeMap<Attributes,Form>();
    for (Entry<Attributes,Form> entry : protoInstance.entrySet()) {
      Attributes currAttrs = entry.getKey();
      // If we're not 1st or 3rd person; this includes 2nd as well as infinitive forms that don't \
      // have person defined
      boolean firstPerson = currAttrs.getValueOrEmpty("Person").equals("1st");
      boolean thirdPerson = currAttrs.getValueOrEmpty("Person").equals("3rd");
      if ((firstPerson || thirdPerson) && !attrsToSave.contains(currAttrs)) {
        if (firstPerson) {
          Attributes copy = new Attributes(new TreeMap<String,Attribute>(currAttrs.attrs));
          copy.setValue("Person", "1st3rd");
          newInstance.put(copy, entry.getValue());
        }
        // For third person, do nothing; it's deleted
      } else {
        newInstance.put(currAttrs, entry.getValue());
      }
    }
    return newInstance;
  }
}
