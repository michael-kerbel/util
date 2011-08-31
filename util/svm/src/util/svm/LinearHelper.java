package util.svm;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import liblinear.FeatureNode;
import liblinear.Linear;
import liblinear.Parameter;
import liblinear.Problem;
import liblinear.SolverType;
import libsvm.svm_node;


public class LinearHelper {

   /**
    * Very similar to Linear.crossValidation(.), but allows access to the submodels used during cross validation.
    * This allows calls to explain(.).
    * @param target an array which will be filled with the class predictions
    * @return the submodels for each instance - the indexes are the same as in target or prob.x
    */
   public static liblinear.Model[] crossValidation( Random random, Problem prob, Parameter param, int nr_fold, int[] target ) {
      int[] fold_start = new int[nr_fold + 1];
      int l = prob.l;
      int[] perm = new int[l];
      liblinear.Model[] models = new liblinear.Model[l];

      for ( int i = 0; i < l; ++i )
         perm[i] = i;
      for ( int i = 0; i < l; ++i ) {
         int j = i + random.nextInt(l - i);
         swap(perm, i, j);
      }
      for ( int i = 0; i <= nr_fold; ++i ) {
         fold_start[i] = (i * l / nr_fold);
      }
      for ( int i = 0; i < nr_fold; ++i ) {
         int begin = fold_start[i];
         int end = fold_start[(i + 1)];

         Problem subprob = new Problem();

         subprob.bias = prob.bias;
         subprob.n = prob.n;
         subprob.l = (l - (end - begin));
         subprob.x = new FeatureNode[subprob.l][];
         subprob.y = new int[subprob.l];

         int k = 0;
         for ( int j = 0; j < begin; ++j ) {
            subprob.x[k] = prob.x[perm[j]];
            subprob.y[k] = prob.y[perm[j]];
            ++k;
         }
         for ( int j = end; j < l; ++j ) {
            subprob.x[k] = prob.x[perm[j]];
            subprob.y[k] = prob.y[perm[j]];
            ++k;
         }
         liblinear.Model submodel = Linear.train(subprob, param);
         for ( int j = begin; j < end; ++j ) {
            target[perm[j]] = Linear.predict(submodel, prob.x[perm[j]]);
            models[perm[j]] = submodel;
         }
      }
      return models;
   }

   public static String explain( Parameter params, liblinear.Model model, Features features, FeatureNode[] instance, int classLabel ) {
      StringBuffer s = new StringBuffer();

      int[] labels = model.getLabels();
      int classIndex = 0;
      for ( int i = 0, length = labels.length; i < length; i++ ) {
         if ( classLabel == labels[i] ) classIndex = i;
      }

      int nr_w;
      float factor = 1f;
      if ( (model.getNrClass() == 2) && (params.getSolverType() != SolverType.MCSVM_CS) ) {
         if ( classIndex == 1 ) factor = -1f;
         classIndex = 0;
         nr_w = 1;
      } else
         nr_w = model.getNrClass();

      double[] featureWeights = model.getFeatureWeights();
      List<FeatureWeight> weights = new ArrayList<FeatureWeight>();
      double absSum = 0;
      double sum = 0;
      for ( FeatureNode n : instance ) {
         int i = n.index;
         double w = featureWeights[nr_w * (i - 1) + classIndex];
         weights.add(new FeatureWeight(features.getFeature(i), w * factor * n.value));
         absSum += Math.abs(w * factor * n.value);
         sum += w * factor * n.value;
      }

      Collections.sort(weights);

      NumberFormat percentFormat = NumberFormat.getPercentInstance(Locale.GERMAN);
      for ( FeatureWeight w : weights ) {
         s.append((w._feature + " " + percentFormat.format(w._weight / absSum))).append("\n");
      }
      s.append("\nsum of weights " + sum);
      s.append("\nsum of absolute weights " + absSum);
      s.append("\nconfidence " + (50 + Math.min((int)(sum / absSum * 100), 50)) + "%");

      return s.toString();
   }

   public static FeatureNode[] toLinearNode( svm_node[] n ) {
      FeatureNode[] linearNodes = new FeatureNode[n.length];
      for ( int j = 0, llength = n.length; j < llength; j++ ) {
         linearNodes[j] = new FeatureNode(n[j].index, n[j].value);
      }
      return linearNodes;
   }

   private static void swap( int[] array, int idxA, int idxB ) {
      int temp = array[idxA];
      array[idxA] = array[idxB];
      array[idxB] = temp;
   }


   public static class FeatureWeight implements Comparable<FeatureWeight> {

      String _feature;
      double _weight;


      public FeatureWeight( String feature, double weight ) {
         _feature = feature;
         _weight = weight;
      }

      public int compareTo( FeatureWeight o ) {
         return -Double.compare(_weight, o._weight);
      }

      @Override
      public String toString() {
         return _feature + " " + _weight;
      }
   }
}
