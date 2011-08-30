package util.svm;

import java.util.Arrays;


public abstract class Instance {

   private int _index;


   public int getIndex() {
      return _index;
   }

   public Node[] getNodes() {
      Node[] nodes = doGetNodes();
      Arrays.sort(nodes);
      return nodes;
   }

   public void setIndex( int index ) {
      _index = index;
   }

   protected abstract Node[] doGetNodes();
}
