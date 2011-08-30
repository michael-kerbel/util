package util.xml;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.pool.PoolableObjectFactory;


public class PoolDocumentBuilderFactory implements PoolableObjectFactory {

   private DocumentBuilderFactory _factory;


   public PoolDocumentBuilderFactory( DocumentBuilderFactory factory ) {
      _factory = factory;
   }

   public void activateObject( Object arg0 ) throws Exception {}

   public void destroyObject( Object arg0 ) throws Exception {}

   public Object makeObject() throws Exception {
      return _factory.newDocumentBuilder();
   }

   public void passivateObject( Object arg0 ) throws Exception {}

   public boolean validateObject( Object arg0 ) {
      return false;
   }

}
