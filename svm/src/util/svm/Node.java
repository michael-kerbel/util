package util.svm;

import libsvm.svm_node;


public class Node extends svm_node implements Comparable<Node> {

   private static final long serialVersionUID = 5401998803437475L;


   public Node( int index, double value ) {
      this.index = index;
      this.value = value;
   }

   public int compareTo( Node o ) {
      return (index < o.index ? -1 : (index == o.index ? 0 : 1));
   }

   @Override
   public boolean equals( Object obj ) {
      if ( this == obj ) return true;
      if ( obj == null ) return false;
      if ( getClass() != obj.getClass() ) return false;
      Node other = (Node)obj;
      if ( index != other.index ) return false;
      return true;
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + index;
      return result;
   }

}
