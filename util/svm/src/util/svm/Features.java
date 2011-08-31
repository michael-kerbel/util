package util.svm;

import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;


public class Features implements Externalizable {

   TObjectIntMap<String>     _featureToIndexMap = new TObjectIntHashMap<String>();
   TIntObjectHashMap<String> _indexToFeatureMap = new TIntObjectHashMap<String>();
   int                       _nextIndex         = -1;


   public String getFeature( int index ) {
      return _indexToFeatureMap.get(index);
   }

   public int getIndex( String feature ) {
      if ( !_featureToIndexMap.containsKey(feature) ) {
         _nextIndex++;
         _featureToIndexMap.put(feature, _nextIndex);
         _indexToFeatureMap.put(_nextIndex, feature);
      }
      return _featureToIndexMap.get(feature);
   }

   public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException {
      _nextIndex = in.readInt();
      ((Externalizable)_featureToIndexMap).readExternal(in);
      ((Externalizable)_indexToFeatureMap).readExternal(in);
   }

   public void writeExternal( ObjectOutput out ) throws IOException {
      out.writeInt(_nextIndex);
      ((Externalizable)_featureToIndexMap).writeExternal(out);
      ((Externalizable)_indexToFeatureMap).writeExternal(out);
   }

}
