package util.swt.editor;

public class Keyword {

   public String _handle;
   public String _description;


   public Keyword( String handle, String description ) {
      _handle = handle;
      _description = description;
   }

   @Override
   public String toString() {
      return _handle;
   }
}
