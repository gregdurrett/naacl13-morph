package edu.berkeley.nlp.morph;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import edu.berkeley.nlp.morph.AnalyzedParadigmInstance.AlignmentType;
import edu.berkeley.nlp.morph.fig.Execution;
import edu.berkeley.nlp.morph.fig.IOUtils;
import edu.berkeley.nlp.morph.fig.LogInfo;
import edu.berkeley.nlp.morph.fig.Option;
import edu.berkeley.nlp.morph.util.GUtil;

/**
 * Main class for the morphological prediction system described in
 * 
 * "Supervised Prediction of Complete Morphological Paradigms"
 * Greg Durrett and John DeNero
 * NAACL 2013
 * 
 * Every public static variable in this class with an Option annotation can be set
 * as a command-line argument; see test/de-verb-small.sh. All model parameters are set
 * to good default values for best accuracy (but may be somewhat slow and memory-intensive
 * to train with these settings).
 * 
 * When running in the default mode (Mode.PREDICT), you need to provide
 * --Example inflection tables (predictInflectedDataPath); this must contain all of the
 * data for the train forms. These must be formatted like the .csv files in the wiktionary-morphology
 * dataset accompanying the paper.
 * --Train forms to use (predictTrainFormsPath). One form per line as in the train/dev/test splits
 * of the wiktionary-morphology dataset.
 * --Forms to predict on (predictTestFormsPath). One form per line.
 * --Where to write the predictions to (predictOutputPath)
 * 
 * Additionally, you can evaluate against gold-standard data if the test forms are also
 * included in the inflected forms and predictEvaluate is set to true. Finally, by setting
 * printExtractedChanges to true, you can see some information about the analyses that
 * were used to extract each rule.
 * 
 * Assertions are frequently used in this code so it's a good idea to run with assertions
 * enabled (-ea flag on the command line). You will also need a larger heap than the
 * default 500M unless you're running small experiments. German experiments can run with
 * fewer than 3GB of RAM (-Xmx3G), as can Spanish experiments, but Finnish experiments
 * on large amounts of data may take more memory (due to the large training set sizes
 * and long word forms; cached feature vectors account for most of the memory usage).
 * To reduce the memory usage, either run on fewer forms (the system can perform well even
 * on small amounts of training data) or reduce the max n-gram order or distance.
 * 
 * The WIKTIONARY and DREYER_EISNER mode are useful for reproducing examples from the
 * paper, but they have hard-coded filenames and may print large amounts of extraneous
 * information.
 * 
 * @author gdurrett
 *
 */
public class Driver implements Runnable {
  
  @Option(gloss = "Which experiment to run?")
  public static Mode exper = Mode.PREDICT;
  
  // PREDICT PARAMS
  
  @Option(gloss = "Path to training inflected forms data")
  public static String predictInflectedDataPath = "";
  
  @Option(gloss = "Path to file containing base forms to train on. Each form must be present in the inflexicon.")
  public static String predictTrainFormsPath = "";

  @Option(gloss = "Path to file containing base forms to test on")
  public static String predictTestFormsPath = "";

  @Option(gloss = "Path to file where we will write predictions")
  public static String predictOutputPath = "";
  
  @Option(gloss = "Should we evaluate our predictions? If so, the inflexicon must contain each form in the test set.")
  public static boolean predictEvaluate = false;
  
  @Option(gloss = "Should we print the extracted changes?")
  public static boolean printExtractedChanges = false;
  
  // WIKTIONARY PARAMS
  
  @Option(gloss = "Path to Wiktionary dataset")
  public static String wiktionaryPath = "./data/wiktionary-morphology/";
  
  @Option(gloss = "Languages for Wiktionary data, comma-separated, no spaces")
  public static String wiktionaryLangs = "fi";

  @Option(gloss = "Parts of speech for Wiktionary data, comma-separated, no spaces")
  public static String wiktionaryPoss = "noun";

  @Option(gloss = "Run final test on Wiktionary data")
  public static boolean wiktionaryTest = false;

  @Option(gloss = "Number of Wiktionary training examples to use (-1 for all)")
  public static int wiktionaryTrainSize = -1;
  
  // DREYER EISNER PARAMS
  
  @Option(gloss = "Path to Dreyer and Eisner 2011 dataset")
  public static String dreyerEisnerPath = "./data/CELEX-dreyer-eisner/";
  
  @Option(gloss = "Number of training instances to use for Dreyer and Eisner, comma-separated, no spaces")
  public static String dreyerEisnerTrainSizes = "100";
  
  @Option(gloss = "Number of training instances to use for Dreyer and Eisner, comma-separated, no spaces")
  public static String dreyerEisnerSampleIndices = "0";

  @Option(gloss = "Run a short Dreyer-Eisner experiment (only 500 test examples)")
  public static boolean dreyerEisnerShortExper = false;
  
  // GENERAL MODEL PARAMETERS
  // Defaults are all set to use the JOINT model and give the best numbers from Durrett and DeNero (2013).
  
  @Option(gloss = "Model type to use")
  public static ModelType modelType = ModelType.JOINT;
  
  @Option(gloss = "Type of alignment to use")
  public static AlignmentType alignmentType = AlignmentType.CONSISTENT;
  
  @Option(gloss = "Use the feature-rich reranker rather than the simple one")
  public static String rankingFeats = "FEAT:CHANGE+FEAT:FACTORED"; // options: "FEAT:CHANGE", "FEAT:FACTORED", "FEAT:RICH", "FEAT:LM", rich is mutex with change and factored
  
  @Option(gloss = "What type of featurization on null spans to use (see")
  public static String nullFeats = "FEAT:ALL";
  
  @Option(gloss = "Max n-gram order to use for features on rule context")
  public static int ruleFeaturesNgramOrder = 4;
  
  @Option(gloss = "Max distance to use for features on rule context")
  public static int ruleFeaturesMaxDist = 5;

  @Option(gloss = "Max n-gram order to use for features on null span context")
  public static int nullFeaturesNgramOrder = 4;
  
  @Option(gloss = "Max distance to use for features on null span context")
  public static int nullFeaturesMaxDist = 5;
  
  @Option(gloss = "Apply a simple heuristic to filter potential match sites of rules (this is" +
  		" generally a good idea, speeds things up dramatically and even improves accuracy)")
  public static boolean useMatchFiltering = true;
  
  @Option(gloss = "Perform a token-based evaluation similar to that of Dreyer and Eisner 2011, Appendix G")
  public static boolean evaluateWithLm = false;

  @Option(gloss = "Path to LM directory; only used for token-level evaluation (not discussed in the paper)")
  public static String lmDirectoryPath = "./data/lm/";
  
  public static enum Mode {
    WIKTIONARY, DREYER_EISNER, PREDICT;
  }

  public static enum ModelType {
    BASELINE, ORACLE, FACTORED, JOINT;
  }
  
  public static void main(String[] args) {
    Driver main = new Driver();
    Execution.run(args, main); // add .class here if that class should receive command-line args
  }
  
  public void run() {
    if (exper == Mode.WIKTIONARY) {
      runWiktionary();
    } else if (exper == Mode.DREYER_EISNER) {
      runDreyerEisner();
    } else if (exper == Mode.PREDICT) {
      runPredict();
    } else {
      throw new RuntimeException(exper + " is not a valid experiment name; " +
      		"must be WIKTIONARY or DREYER_EISNER");
    }
  }
  
  
  public void runPredict() {
    // Read in the inflection data and the forms to use
    List<ParadigmInstance> instances = ParadigmInstanceReader.readParadigmInstancesWiktionary(predictInflectedDataPath);
    List<String> trainForms = IOUtils.readLinesHard(predictTrainFormsPath);
    List<String> testForms = IOUtils.readLinesHard(predictTestFormsPath);
    List<ParadigmInstance> trainInstances = new ArrayList<ParadigmInstance>();
    Map<Form,ParadigmInstance> testInstances = new HashMap<Form,ParadigmInstance>();
    for (ParadigmInstance instance : instances) {
      String formString = instance.baseForm().toString();
      if (trainForms.contains(formString)) {
        trainInstances.add(instance);
      } else if (testForms.contains(formString)) {
        Form testForm = new Form(formString);
        testInstances.put(testForm, instance);
      }
    }
    LogInfo.logss(trainForms.size() + " train forms loaded, " + testForms.size() + " test forms loaded");
    LogInfo.logss(trainInstances.size() + " tables allocated to training set, " + testInstances.size() + " tables allocated to test set");
    // Analyze the training examples to extract morphological change rules
    ExtractedModel model = ExtractedModel.extractChanges(trainInstances, alignmentType);
    if (printExtractedChanges) {
      model.printModel();
    }
    // Instantiate and possibly train the inflection predictor
    Predictor finalPredictor;
    if (modelType == ModelType.BASELINE) {
      finalPredictor = new BaselinePredictor(model);
    } else if (modelType == ModelType.ORACLE) {
      finalPredictor = new OraclePredictor(model);
    } else if (modelType == ModelType.JOINT) {
      JointSpanMatchingPredictor jointPredictor = new JointSpanMatchingPredictor(model, new AnchoredSpanFeaturizer(ruleFeaturesNgramOrder, ruleFeaturesMaxDist), new AnchoredSpanFeaturizer(nullFeaturesNgramOrder, nullFeaturesMaxDist));
      jointPredictor.train();
      finalPredictor = jointPredictor;
    } else {
      throw new RuntimeException("Unrecognized model type: " + modelType);
    }
    // Make predictions on the test paradigms and write output
    PrintWriter output = IOUtils.openOutHard(predictOutputPath);
    List<ParadigmHypothesis> predictedTestInstances = new ArrayList<ParadigmHypothesis>();
    List<ParadigmInstance> orderedTestInstances = new ArrayList<ParadigmInstance>();
    for (int i = 0; i < testForms.size(); i++) {
      GUtil.logsEveryN("Decoding", 500);
      Form testForm = new Form(testForms.get(i));
      ParadigmHypothesis prediction = finalPredictor.predict(testForm, trainInstances.get(0).getAttrSetSorted(), testInstances.get(testForm));
      if (predictEvaluate) {
        assert testInstances.get(testForm) != null;
        orderedTestInstances.add(testInstances.get(testForm));
      }
      predictedTestInstances.add(prediction);
      ParadigmInstanceWriter.writeParadigmInstance(prediction.predictedInstance, output);
    }
    output.close();
    LogInfo.logss("Output written to " + predictOutputPath);
    // Print evaluation results
    if (predictEvaluate) {
      ExtractedModel extractedGoldModel = ExtractedModel.extractChanges(orderedTestInstances, alignmentType);
      EvaluationResults results = new EvaluationResults(model, predictedTestInstances, extractedGoldModel.analyzedInstances);
      LogInfo.logss("RESULTS:\n" + results.renderLong());
    }
  }
  
  /////////////////////////////////////////////////////////////////////////////////////
  // The following modes/methods are useful for reproducing results in the paper
  // and feature additional options/printing/etc., but rely on some hard-coded
  // paths and are more suited to system development than prediction (e.g. they assume
  // the gold standard is always there, etc.)
  /////////////////////////////////////////////////////////////////////////////////////
  
  public void runWiktionary() {
    List<String> wiktionaryLangsList = Arrays.asList(wiktionaryLangs.split(","));
    List<String> wiktionaryPossList = Arrays.asList(wiktionaryPoss.split(","));
    if (wiktionaryLangsList.size() != wiktionaryPossList.size()) {
      throw new RuntimeException("Need to be the same length: " + wiktionaryLangs + " " + wiktionaryPoss);
    }
    for (int i = 0; i < wiktionaryLangsList.size(); i++) {
      LogInfo.logss("EXPERIMENT: " + wiktionaryLangsList.get(i) + "-" + wiktionaryPossList.get(i));
      EvaluationResults results = runWiktionary(wiktionaryLangsList.get(i), wiktionaryPossList.get(i));
      LogInfo.logss("RESULTS " + wiktionaryLangsList.get(i) + "-" + wiktionaryPossList.get(i) + ":\n" + results.renderLong());
    }
  }
  
  public EvaluationResults runWiktionary(String wiktionaryLang, String wiktionaryPos) {
    String inflexPath = wiktionaryPath + "/inflections_" + wiktionaryLang + "_" + wiktionaryPos + ".csv";
    List<ParadigmInstance> instances = ParadigmInstanceReader.readParadigmInstancesWiktionary(inflexPath);
    List<String> trainForms = IOUtils.readLinesHard(wiktionaryPath + "/base_forms_" + wiktionaryLang + "_" + wiktionaryPos + "_train.txt");
    List<String> testForms = IOUtils.readLinesHard(wiktionaryPath + "/base_forms_" + wiktionaryLang + "_" + wiktionaryPos + (wiktionaryTest ? "_test.txt" : "_dev.txt"));
    List<ParadigmInstance> trainInstances = new ArrayList<ParadigmInstance>();
    List<ParadigmInstance> testInstances = new ArrayList<ParadigmInstance>();
    for (ParadigmInstance instance : instances) {
      if (trainForms.contains(instance.baseForm().toString())) {
        trainInstances.add(instance);
      } else if (testForms.contains(instance.baseForm().toString())) {
        testInstances.add(instance);
      }
    }
    LmHandler lm = null;
    if (evaluateWithLm) {
      lm = new LmHandler(lmDirectoryPath + "/" + wiktionaryLang + ".lm");
    }
    LogInfo.logss(trainInstances.size() + " train instances read in, " + testInstances.size() + " test instances read in");
    if (wiktionaryTrainSize != -1) {
      trainInstances = trainInstances.subList(0, Math.min(wiktionaryTrainSize, trainInstances.size()));
    }
    LogInfo.logss(trainInstances.size() + " train instances being used");
    if (modelType == ModelType.FACTORED) {
      return learnAndEvaluateModelFactored(trainInstances, testInstances, lm);
    } else {
      return learnAndEvaluateModel(trainInstances, testInstances, lm);
    }
  }
  
  public void runDreyerEisner() {
    List<String> trainSizesToRun = Arrays.asList(dreyerEisnerTrainSizes.split(","));
    List<String> sampleIndicesToRun = Arrays.asList(dreyerEisnerSampleIndices.split(","));
    LogInfo.logss("Running on sizes: " + trainSizesToRun.toString());
    LogInfo.logss("Running on indices: " + sampleIndicesToRun.toString());
    LmHandler lm = null;
    if (evaluateWithLm) {
      lm = new LmHandler(lmDirectoryPath + "/de.lm");
    }
    for (String trainSizeStr : trainSizesToRun) {
      EvaluationResults overallResults = new EvaluationResults();
      int trainSize = Integer.parseInt(trainSizeStr);
      for (String sampleIndexStr : sampleIndicesToRun) {
        int sampleIndex = Integer.parseInt(sampleIndexStr);
        LogInfo.logss("EXPERIMENT: " + trainSize + "-" + sampleIndex);
        EvaluationResults results = runDreyerEisner(sampleIndex, trainSize, lm);
        LogInfo.logss("RESULTS " + trainSize  + "-" + sampleIndex + ":\n" + results.renderLong());
        overallResults.accumulate(results);
      }
      LogInfo.logss("OVERALL RESULTS:\n" + overallResults.renderLong());
    }
  }
  
  public EvaluationResults runDreyerEisner(int experIndex, int size, LmHandler lm) {
    String trainPath = dreyerEisnerPath + "/v2-" + experIndex + "-" + size + "-train.txt";
    String testPath = dreyerEisnerPath + "/v2-" + experIndex + "-test.txt";
    List<ParadigmInstance> trainInstances = ParadigmInstanceReader.readParadigmInstancesCelex(trainPath);
    ParadigmInstance.filterStars(trainInstances);
    List<ParadigmInstance> testInstances = ParadigmInstanceReader.readParadigmInstancesCelex(testPath);
    LogInfo.logss(trainInstances.size() + " train instances left after filtering for STAR");
    if (dreyerEisnerShortExper) {
      testInstances = testInstances.subList(0, Math.min(testInstances.size(), 500));
    }
    LogInfo.logss("Evaluating on " + testInstances.size() + " test instances");
    if (modelType == ModelType.FACTORED) {
      return learnAndEvaluateModelFactored(trainInstances, testInstances, lm);
    } else {
      return learnAndEvaluateModel(trainInstances, testInstances, lm);
    }
  }
  
  public EvaluationResults learnAndEvaluateModel(List<ParadigmInstance> trainInstances, List<ParadigmInstance> testInstances, LmHandler lm) {
    ExtractedModel model = ExtractedModel.extractChanges(trainInstances, alignmentType);
    model.printModel();
    Predictor finalPredictor;
    if (modelType == ModelType.BASELINE) {
      finalPredictor = new BaselinePredictor(model);
    } else if (modelType == ModelType.ORACLE) {
      finalPredictor = new OraclePredictor(model);
    } else if (modelType == ModelType.JOINT) {
      JointSpanMatchingPredictor jointPredictor = new JointSpanMatchingPredictor(model, new AnchoredSpanFeaturizer(ruleFeaturesNgramOrder, ruleFeaturesMaxDist), new AnchoredSpanFeaturizer(nullFeaturesNgramOrder, nullFeaturesMaxDist));
      jointPredictor.train();
      finalPredictor = jointPredictor;
    } else {
      throw new RuntimeException("Unrecognized model type: " + modelType);
    }
    // Compute and print train accuracy
    List<ParadigmInstance> trainPredictions = new ArrayList<ParadigmInstance>();
    GUtil.logsEveryNReset();
    for (int i = 0; i < trainInstances.size(); i++) {
      GUtil.logsEveryN("Decoding", 500);
      ParadigmInstance trainInst = trainInstances.get(i);
      Form trainForm = trainInst.baseForm();
      trainPredictions.add(finalPredictor.predict(trainForm, trainInst.getAttrSetSorted(), trainInst).predictedInstance);
    }
    LogInfo.logss("RESULTS (TRAIN): " + new EvaluationResults(trainPredictions, trainInstances).renderShort());
//    System.exit(0);
    List<ParadigmHypothesis> pred = new ArrayList<ParadigmHypothesis>();
    GUtil.logsEveryNReset();
    for (int i = 0; i < testInstances.size(); i++) {
      GUtil.logsEveryN("Decoding", 500);
      ParadigmInstance testInst = testInstances.get(i);
      Form testForm = testInst.baseForm();
      pred.add(finalPredictor.predict(testForm, testInst.getAttrSetSorted(), testInst));
    }
//    LogInfo.logss(finalPredictor.renderPruningStats());
    // Learn what gold changes fired on the test examples
    ExtractedModel extractedGoldModel = ExtractedModel.extractChanges(testInstances, alignmentType);
    if (evaluateWithLm) {
      lmEvaluateHyps(lm, pred, testInstances, trainInstances);
    }
    return new EvaluationResults(model, pred, extractedGoldModel.analyzedInstances);
  }
  
  public EvaluationResults learnAndEvaluateModelFactored(List<ParadigmInstance> trainInstances, List<ParadigmInstance> testInstances, LmHandler lm) {
    SortedMap<Attributes,List<ParadigmInstance>> trainInstancesSplit = splitParadigmInstances(trainInstances);
    SortedMap<Attributes,List<ParadigmInstance>> testInstancesSplit = splitParadigmInstances(testInstances);
    List<List<ParadigmInstance>> predictionsSplitUp = new ArrayList<List<ParadigmInstance>>();
    LogInfo.logss("Learning factored matchers for " + trainInstancesSplit.keySet().size() + " attributes");
    for (Attributes attrs : trainInstancesSplit.keySet()) {
      List<ParadigmInstance> trainInstancesTheseAttrs = trainInstancesSplit.get(attrs);
      List<ParadigmInstance> testInstancesTheseAttrs = testInstancesSplit.get(attrs);
      ExtractedModel model = ExtractedModel.extractChanges(trainInstancesTheseAttrs, alignmentType);
      JointSpanMatchingPredictor predictor = new JointSpanMatchingPredictor(model, new AnchoredSpanFeaturizer(ruleFeaturesNgramOrder, ruleFeaturesMaxDist), new AnchoredSpanFeaturizer(nullFeaturesNgramOrder, nullFeaturesMaxDist));
      predictor.train();
      LogInfo.logss("Learned factored matcher for attributes: " + attrs);
      List<ParadigmInstance> predictedInstances = new ArrayList<ParadigmInstance>();
      GUtil.logsEveryNReset();
      for (int i = 0; i < testInstancesTheseAttrs.size(); i++) {
        ParadigmInstance testInstanceTheseAttrs = testInstancesTheseAttrs.get(i);
        Form testForm = testInstanceTheseAttrs.baseForm();
        predictedInstances.add(predictor.predict(testForm, testInstanceTheseAttrs.getAttrSetSorted(), null).predictedInstance);
      }
      predictionsSplitUp.add(predictedInstances);
    }
    List<ParadigmInstance> predictedInstances = mergeAllParadigmInstances(predictionsSplitUp);
    assert predictedInstances.size() == testInstances.size();
    if (evaluateWithLm) {
      lmEvaluateInsts(lm, predictedInstances, testInstances, trainInstances);
    }
    return new EvaluationResults(predictedInstances, testInstances);
  }
  
  public static void lmEvaluateHyps(LmHandler lm, List<ParadigmHypothesis> predHypotheses, List<ParadigmInstance> goldInstances, List<ParadigmInstance> freqEstimationInstances) {
    List<ParadigmInstance> predInstances = new ArrayList<ParadigmInstance>();
    for (ParadigmHypothesis predHyp : predHypotheses) {
      predInstances.add(predHyp.predictedInstance);
    }
    lmEvaluateInsts(lm, predInstances, goldInstances, freqEstimationInstances);
  }
  
  public static void lmEvaluateInsts(LmHandler lm, List<ParadigmInstance> predInstances, List<ParadigmInstance> goldInstances, List<ParadigmInstance> freqEstimationInstances) {
    AttributeFrequencyEstimator afe = new AttributeFrequencyEstimator(lm, predInstances.get(0).getAttrSetSorted(), freqEstimationInstances);
    double[] weights = afe.train();
    LogInfo.logss("Weights learned: " + afe.getNaivePosterior(weights));
    afe.evaluateAndPrint(predInstances, goldInstances, weights);
  }

  public static SortedMap<Attributes,List<ParadigmInstance>> splitParadigmInstances(List<ParadigmInstance> baseInstances) {
    SortedMap<Attributes,List<ParadigmInstance>> splitMap = new TreeMap<Attributes,List<ParadigmInstance>>();
    for (Attributes attrs : baseInstances.get(0).getAttrSetSorted()) {
      splitMap.put(attrs, new ArrayList<ParadigmInstance>());
    }
    for (ParadigmInstance instance : baseInstances) {
      assert instance.getAttrSetSorted().equals(splitMap.keySet());
      for (Attributes attrs : instance.getAttrSetSorted()) {
        SortedMap<Attributes,List<Form>> newInstanceMap = new TreeMap<Attributes,List<Form>>();
        newInstanceMap.put(attrs, instance.getAllInflForms(attrs));
        splitMap.get(attrs).add(new ParadigmInstance(instance.baseForm(), newInstanceMap));
      }
    }
    return splitMap;
  }
  
  public static List<ParadigmInstance> mergeAllParadigmInstances(List<List<ParadigmInstance>> instanceLists) {
    List<ParadigmInstance> merged = new ArrayList<ParadigmInstance>();
    for (List<ParadigmInstance> instanceList : instanceLists) {
      if (merged.isEmpty()) {
        merged.addAll(instanceList);
      } else {
        merged = mergeParadigmInstances(merged, instanceList);
      }
    }
    return merged;
  }
  
  public static List<ParadigmInstance> mergeParadigmInstances(List<ParadigmInstance> baseInstances, List<ParadigmInstance> toMergeIn) {
    assert baseInstances.size() == toMergeIn.size();
    List<ParadigmInstance> newInstances = new ArrayList<ParadigmInstance>();
    for (int i = 0; i < baseInstances.size(); i++) {
      Form form = baseInstances.get(i).baseForm();
      assert form.equals(toMergeIn.get(i).baseForm());
      SortedMap<Attributes,List<Form>> mergedInflForms = new TreeMap<Attributes,List<Form>>();
      for (Attributes attrs : baseInstances.get(i).getAttrSetSorted()) {
        mergedInflForms.put(attrs, baseInstances.get(i).getAllInflForms(attrs));
      }
      for (Attributes attrs : toMergeIn.get(i).getAttrSetSorted()) {
        assert !mergedInflForms.containsKey(attrs);
        mergedInflForms.put(attrs, toMergeIn.get(i).getAllInflForms(attrs));
      }
      newInstances.add(new ParadigmInstance(form, mergedInflForms));
    }
    return newInstances;
  }
}
