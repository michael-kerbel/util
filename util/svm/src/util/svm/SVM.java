package util.svm;

import gnu.trove.procedure.TObjectIntProcedure;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import libsvm.svm;
import libsvm.svm_model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.dump.Dump;
import util.dump.DumpUtils;
import util.dump.stream.ExternalizableObjectStreamProvider;
import util.string.StringTool;


public class SVM {

   private static Logger _log = LoggerFactory.getLogger(SVM.class);


   public static TrainingData createTrainingData( Collection<? extends Instance>... classInstances ) {
      int n = 0;
      for ( Collection<? extends Instance> e : classInstances ) {
         n += e.size();
      }

      Node[][] trainingInstances = new Node[n][];
      double[] instanceClasses = new double[n];
      Instance[] instances = new Instance[n];

      int i = 0;
      for ( int j = 0, length = classInstances.length; j < length; j++ ) {
         for ( Instance instance : classInstances[j] ) {
            trainingInstances[i] = instance.getNodes();
            instanceClasses[i] = j;
            instances[i] = instance;
            instance.setIndex(i);
            i++;
         }
      }

      TrainingData prob = new TrainingData(trainingInstances, instanceClasses);
      prob.setInstances(instances);
      return prob;
   }

   /** Performs an n-fold cross validation for each instance in problem. 
    *  @return an array containing the predicted class label for each instance   
    */
   public static double[] crossValidate( TrainingData problem, Parameters parameters, int n ) {
      double[] classLabels = new double[problem.l];
      svm.svm_cross_validation(problem, parameters, 10, classLabels);
      return classLabels;
   }

   public static Features loadFeatures( File dumpFile ) throws IOException {
      Dump<Features> featuresDump = null;
      try {
         featuresDump = new Dump<Features>(Features.class, new ExternalizableObjectStreamProvider(), dumpFile, 0, Dump.DEFAULT_MODE);
         Features features = featuresDump.get(0);
         return features;
      }
      finally {
         DumpUtils.closeSilently(featuresDump);
      }
   }

   public static Model loadModel( String fileName ) throws IOException {
      return new Model(svm.svm_load_model(fileName));
   }

   public static ScaleData loadScaleData( File dumpFile ) throws IOException {
      Dump<ScaleData> scaleDataDump = null;
      try {
         scaleDataDump = new Dump<ScaleData>(ScaleData.class, dumpFile);
         ScaleData scaleData = scaleDataDump.get(0);
         return scaleData;
      }
      finally {
         DumpUtils.closeSilently(scaleDataDump);
      }
   }

   /**
    * @param name name used as filename and modelname
    * @param globalFeatures Map of String to feature indexes 
    */
   public static void persistToARFF( String name, Features globalFeatures, Collection<? extends Instance>... classInstances ) {

      BufferedWriter o = null;
      try {
         o = new BufferedWriter(new FileWriter(name + ".arff"));
         o.write("@relation " + name + "\n");

         final int[] minmax = { Integer.MAX_VALUE, Integer.MIN_VALUE };
         final Map<Integer, String> indexFeatures = new HashMap<Integer, String>(globalFeatures._featureToIndexMap.size());
         globalFeatures._featureToIndexMap.forEachEntry(new TObjectIntProcedure<String>() {

            public boolean execute( String key, int value ) {
               indexFeatures.put(value, key);
               if ( minmax[0] > value ) {
                  minmax[0] = value;
               }
               if ( minmax[1] < value ) {
                  minmax[1] = value;
               }
               return true;
            }
         });

         o.write("\n");

         String classAttributes = "";
         for ( int i = 0, length = classInstances.length; i < length; i++ ) {
            classAttributes += (i > 0 ? "," : "") + "class" + i;
         }
         for ( int i = minmax[0]; i <= minmax[1]; i++ ) {
            String featureName = indexFeatures.get(i);
            featureName = StringTool.replace(featureName, new String[] { "'", ",", "%", "\"" }, new String[] { "QUOTE", "KOMMA", "PERCENT", "DOUBLEQUOTE" });
            o.write("@attribute " + featureName + " numeric\n"); // TODO this is probably wrong!
         }
         o.write("@attribute class {" + classAttributes + "}\n");

         o.write("\n");

         NumberFormat valueFormat = NumberFormat.getInstance(Locale.US);
         valueFormat.setMinimumFractionDigits(0);
         valueFormat.setMaximumFractionDigits(5);
         valueFormat.setGroupingUsed(false);

         o.write("@data\n");
         for ( int j = 0, length = classInstances.length; j < length; j++ ) {
            for ( Instance instance : classInstances[j] ) {
               StringBuilder sb = new StringBuilder();
               sb.append("{");

               for ( Node node : instance.getNodes() ) {
                  if ( node.value > 0 ) {
                     sb.append(sb.length() > 1 ? "," : "").append(node.index).append(" ").append(valueFormat.format(node.value));
                  }
               }
               sb.append("," + (globalFeatures._nextIndex + 1) + " class" + j);
               o.write(sb.toString() + "}\n");
            }
         }
      }
      catch ( Exception e ) {
         _log.error("Failed to persist ARFF '" + name + "'", e);
      }
      finally {
         try {
            if ( o != null ) {
               o.close();
            }
         }
         catch ( Exception argh ) {
            _log.warn("failed to close output stream", argh);
         }
      }
   }

   /**
      This function does classification or regression on a test vector x
       given a model.

       For a classification model, the predicted class for x is returned.
       For a regression model, the function value of x calculated using
       the model is returned. For an one-class model, +1 or -1 is
       returned.
    */
   public static double predictClass( Model model, Node[] nodes ) {
      return svm.svm_predict(model, nodes);
   }

   /**
    This function does classification or regression on a test vector x
    given a model with probability information.

    For a classification model with probability information, this
    function gives nr_class probability estimates in the array
    prob_estimates. nr_class can be obtained from the function
    svm_get_nr_class. The class with the highest probability is
    returned. For regression/one-class SVM, the array prob_estimates
    is unchanged and the returned value is the same as that of
    svm_predict.
    */
   public static double[] predictClassProbabilities( Model model, Node[] nodes ) {
      boolean modelHasPropabilities = svm.svm_check_probability_model(model) > 0;
      if ( !modelHasPropabilities ) {
         throw new IllegalArgumentException("given model has no probability information!");
      }

      double[] probs = new double[model.getClassNumber()];
      svm.svm_predict_probability(model, nodes, probs);
      return probs;
   }

   public static void saveFeatures( Features features, File dumpFile ) throws IOException {
      Dump<Features> featuresDump = null;
      try {
         dumpFile.delete();
         featuresDump = new Dump<Features>(Features.class, new ExternalizableObjectStreamProvider(), dumpFile, 0, Dump.DEFAULT_MODE);
         featuresDump.add(features);
      }
      finally {
         DumpUtils.closeSilently(featuresDump);
      }
   }

   public static void saveModel( Model model, String filename ) throws IOException {
      svm.svm_save_model(filename, model);
   }

   public static void saveScaleData( ScaleData scaleData, File dumpFile ) throws IOException {
      Dump<ScaleData> scaleDataDump = null;
      try {
         dumpFile.delete();
         scaleDataDump = new Dump<ScaleData>(ScaleData.class, dumpFile);
         scaleDataDump.add(scaleData);
      }
      finally {
         DumpUtils.closeSilently(scaleDataDump);
      }
   }

   public static Node[] scale( ScaleData scaleData, Node[] instance ) {
      List<Node> scaledNodes = new ArrayList<Node>();
      int nextIndex = 1;
      for ( Node node : instance ) {
         for ( int i = nextIndex; i < node.index; i++ ) {
            // non-existing nodes have value 0
            Node scaledNode = scaleData.scale(new Node(i, 0.0));
            if ( scaledNode != null && scaledNode.value != 0 ) scaledNodes.add(scaledNode);
         }

         Node scaledNode = scaleData.scale(node);
         if ( scaledNode != null && scaledNode.value != 0 ) scaledNodes.add(scaledNode);

         nextIndex = node.index + 1;
      }
      for ( int i = nextIndex; i <= scaleData.getMaxFeatureIndex(); i++ ) {
         // non-existing nodes have value 0
         Node scaledNode = scaleData.scale(new Node(i, 0.0));
         if ( scaledNode != null && scaledNode.value != 0 ) scaledNodes.add(scaledNode);
      }
      return scaledNodes.toArray(new Node[scaledNodes.size()]);
   }

   /**
    * Scales nodes in trainingData and returns the ScaleData for later reuse during classifying
    */
   public static ScaleData scale( TrainingData trainingData, double lowerLimit, double upperLimit ) {

      ScaleData scaleData = new ScaleData(trainingData, lowerLimit, upperLimit);
      Node[][] trainingInstances = trainingData.getTrainingInstances();

      for ( int i = 0, length = trainingInstances.length; i < length; i++ ) {
         Node[] instanceNodes = trainingInstances[i];
         trainingInstances[i] = scale(scaleData, instanceNodes);
      }

      return scaleData;
   }

   /**
    * Performs a 10-fold cross validation. All instances are classified.
    * @return the number of correct classifications
    */
   public static int tenFoldCrossValidation( TrainingData problem, Parameters parameters ) {
      double[] labels = crossValidate(problem, parameters, 10);
      int correct = 0;
      for ( int i = 0; i < problem.l; i++ ) {
         if ( labels[i] == problem.y[i] ) {
            ++correct;
         }
      }
      return correct;
   }

   public static Model train( TrainingData problem, Parameters params ) {
      String error = svm.svm_check_parameter(problem, params);
      if ( error != null ) throw new IllegalArgumentException(error);

      svm_model model = svm.svm_train(problem, params);
      return new Model(model);
   }

}
