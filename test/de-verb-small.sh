#!/bin/bash

java -Xmx1g -jar ../morph.jar \
-predictInflectedDataPath ./test-data/inflections_de_verb.csv \
-predictTrainFormsPath ./test-data/base_forms_de_verb_train_short.txt \
-predictTestFormsPath ./test-data/base_forms_de_verb_dev.txt \
-predictOutputPath output.txt \
-predictEvaluate true
echo "==================================================================="
output=$(diff de-verb-small-output.txt output.txt)
if [[ -z $output ]]; then
  echo "output.txt matched de-verb-small-output.txt (expected output) exactly!"
else
  echo "WARNING: output does not match expected output; diff:"
  echo $output
fi
