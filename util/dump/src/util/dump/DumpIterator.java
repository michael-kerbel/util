package util.dump;

import java.util.Iterator;


public interface DumpIterator<E> extends Iterator<E> {

   public long getPosition();
}
