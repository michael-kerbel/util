package util.svm;

import java.io.File;
import java.io.IOException;

import util.dump.DumpReader;
import util.dump.DumpWriter;
import util.dump.ExternalizableBean;
import util.dump.stream.SingleTypeObjectStreamProvider;
import util.io.IOUtils;


public class ScaleData extends ExternalizableBean {

   public static ScaleData load( File file ) throws IOException {
      DumpReader<ScaleData> reader = null;
      try {
         reader = new DumpReader<ScaleData>(file, false, new SingleTypeObjectStreamProvider<ScaleData>(ScaleData.class));
         if ( reader.hasNext() ) return reader.next();
      }
      finally {
         IOUtils.close(reader);
      }
      return null;
   }


   @externalize(1)
   private double[] _featureMax;
   @externalize(2)
   private double[] _featureMin;
   @externalize(3)
   private double   _upperLimit;
   @externalize(4)
   private double   _lowerLimit;
   @externalize(5)
   private int      _maxFeatureIndex;


   public ScaleData() {}

   public ScaleData( TrainingData trainingData, double lowerLimit, double upperLimit ) {
      _lowerLimit = lowerLimit;
      _upperLimit = upperLimit;

      int minFeatureIndex = Integer.MAX_VALUE;
      _maxFeatureIndex = Integer.MIN_VALUE;
      Node[][] trainingInstances = trainingData.getTrainingInstances();
      for ( Node[] instanceNodes : trainingInstances ) {
         for ( Node node : instanceNodes ) {
            minFeatureIndex = Math.min(minFeatureIndex, node.index);
            _maxFeatureIndex = Math.max(_maxFeatureIndex, node.index);
         }
      }

      _featureMax = new double[_maxFeatureIndex + 1];
      _featureMin = new double[_maxFeatureIndex + 1];

      for ( int j = 0, length = _featureMax.length; j < length; j++ ) {
         _featureMax[j] = -Double.MAX_VALUE;
         _featureMin[j] = Double.MAX_VALUE;
      }

      for ( Node[] instanceNodes : trainingInstances ) {
         int nextIndex = 1;

         for ( Node node : instanceNodes ) {
            for ( int k = nextIndex; k < node.index; k++ ) {
               // non-existing nodes have value 0
               _featureMax[k] = Math.max(_featureMax[k], 0);
               _featureMin[k] = Math.min(_featureMin[k], 0);
            }

            _featureMax[node.index] = Math.max(_featureMax[node.index], node.value);
            _featureMin[node.index] = Math.min(_featureMin[node.index], node.value);
            nextIndex = node.index + 1;
         }

         for ( int k = nextIndex; k <= _maxFeatureIndex; k++ ) {
            // non-existing nodes have value 0
            _featureMax[k] = Math.max(_featureMax[k], 0);
            _featureMin[k] = Math.min(_featureMin[k], 0);
         }
      }
   }

   public int getMaxFeatureIndex() {
      return _maxFeatureIndex;
   }

   public void save( File file ) throws IOException {
      if ( file.exists() ) if ( !file.delete() ) {
         throw new RuntimeException("Failed to delete old file " + file);
      }
      DumpWriter<ScaleData> writer = new DumpWriter<ScaleData>(file, new SingleTypeObjectStreamProvider<ScaleData>(ScaleData.class));
      writer.write(this);
      writer.close();
   }

   public Node scale( Node node ) {
      int index = node.index;
      double value = node.value;
      if ( _featureMax.length <= index || _featureMin.length <= index ) return null; // test data might have unknown features, which we can safely ignore
      if ( _featureMax[index] == _featureMin[index] ) return null;

      if ( value == _featureMin[index] )
         value = _lowerLimit;
      else if ( value == _featureMax[index] )
         value = _upperLimit;
      else
         value = _lowerLimit + (_upperLimit - _lowerLimit) * (value - _featureMin[index]) / (_featureMax[index] - _featureMin[index]);

      return new Node(index, value);
   }
}
