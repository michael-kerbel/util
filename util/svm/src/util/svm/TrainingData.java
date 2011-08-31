package util.svm;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import libsvm.svm_node;
import libsvm.svm_problem;


/** This is a wrapper for {@link svm_problem} providing a nicer name. 
 * This data structure contains the training data for */
public class TrainingData extends svm_problem {

   private Instance[] _instances;


   public TrainingData( Node[][] trainingInstances, double[] targetClassLabels ) {
      x = trainingInstances;
      y = targetClassLabels;
      l = targetClassLabels.length;
      if ( l != x.length ) throw new IllegalArgumentException("both arguments must be arrays of the same length! " + x.length + "!=" + y.length);
   }

   public int getFeatureNumber() {
      TIntSet featureIndexes = new TIntHashSet();
      for ( svm_node[] instance : x ) {
         for ( svm_node node : instance ) {
            featureIndexes.add(node.index);
         }
      }
      return featureIndexes.size();
   }

   public int getInstanceNumber() {
      return l;
   }

   public Instance[] getInstances() {
      return _instances;
   }

   /** @return An array containing the target values. (integers in classification, real numbers in regression) */
   public double[] getTargetClassLabels() {
      return y;
   }

   /**
    * @return An array containing a sparse representation (array of Node) of one training vector
    */
   public Node[][] getTrainingInstances() {
      return (Node[][])x;
   }

   public void setInstances( Instance[] instances ) {
      _instances = instances;
   }

}
