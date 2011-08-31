package util.swt.editor;

class TextChange {

   /** The starting offset of the change */
   int    start;

   /** The length of the change */
   int    length;

   int    topIndex;

   String replacedText;


   /**
    * Constructs a TextChange
    * 
    * @param start
    *          the starting offset of the change
    * @param length
    *          the length of the change
    * @param replacedText
    *          the text that was replaced
    */
   public TextChange( int start, int length, String replacedText, int topIndex ) {
      this.start = start;
      this.length = length;
      this.replacedText = replacedText;
      this.topIndex = topIndex;
   }

   /**
    * Returns the length
    * 
    * @return int
    */
   public int getLength() {
      return length;
   }

   /**
    * Returns the replacedText
    * 
    * @return String
    */
   public String getReplacedText() {
      return replacedText;
   }

   /**
    * Returns the start
    * 
    * @return int
    */
   public int getStart() {
      return start;
   }

   public int getTopIndex() {
      return topIndex;
   }
}