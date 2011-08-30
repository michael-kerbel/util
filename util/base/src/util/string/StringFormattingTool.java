package util.string;

/**
 * This class contains methods for formatting Strings, mostly useful only when using proportional fonts. 
 */
public class StringFormattingTool {

   /**
    * 
    * Crops a String from the center if its length exceds the specified 
    * max length. That is, the length of the returned may be less or 
    * equal to the length of the incoming string
    *   
    * @param toCrop String to be eventually crop 
    * @param maxSize the size to use in order to crop
    * @return the croped string
    */
   public static String centerCrop( String toCrop, int maxSize ) {
      if ( toCrop.length() > maxSize ) {
         if ( maxSize > 0 ) {
            int middle = (int)Math.floor(toCrop.length() / 2) - (int)Math.floor(maxSize / 2);
            return toCrop.substring(middle, middle + maxSize);
         }
         return "";
      }
      return toCrop;
   }

   /**
    * 
    * Tabulates the incoming string by aligning it to the center and
    * appending spaces at the sides, please notice that the data will 
    * be croped at need
    *  
    * @param toTabulate string to be tabulated
    * @param tabSize tabulation size
    * @return centered, tabulated and eventually croped string 
    */
   public static String centerTab( String toTabulate, int tabSize ) {
      int fillAmount = (int)Math.ceil(((float)tabSize - (float)toTabulate.length()) / 2);
      return centerCrop(replicate(' ', fillAmount) + toTabulate + replicate(' ', fillAmount), tabSize);
   }

   /**
    * Tabulates the incoming string by aligning it to the center and 
    * appending the specified fill character at the sides, please notice 
    * that the data will be croped at need
    *  
    * @param toTabulate string to be tabulated
    * @param tabSize tabulation size
    * @param fillCharacter character to use for filling tasks
    * @return centered, tabulated and eventually croped string 
    */
   public static String centerTab( String toTabulate, int tabSize, char fillCharacter ) {
      int fillAmount = (int)Math.ceil(((float)tabSize - (float)toTabulate.length()) / 2);
      return centerCrop(replicate(fillCharacter, fillAmount) + toTabulate + replicate(fillCharacter, fillAmount), tabSize);
   }

   /**
    * Tabulates the incoming string by aligning it to the center and
    * appending the specified replicated fill string at the sides, 
    * please notice that the data will be croped at need
    * 
    * @param toTabulate string to be tabulated
    * @param tabSize tabulation size
    * @param fillString string to use for filling tasks
    * @return centered, tabulated and eventually croped string 
    */
   public static String centerTab( String toTabulate, int tabSize, String fillString ) {
      int fillAmount = (int)Math.ceil(((float)tabSize - (float)toTabulate.length()) / 2);
      return centerCrop(replicate(fillString, fillAmount) + toTabulate + replicate(fillString, fillAmount), tabSize);
   }

   /**
    * 
    * Joins an String array and returns a string representation of it
    * 
    * @param toJoin array of string to be joined
    * @param token token to place between joined elements
    * @return the result of the join operation
    */
   public static String join( String[] toJoin, String token ) {

      StringBuffer joined = new StringBuffer();
      final int maxLenMin1 = toJoin.length - 1;

      for ( int i = 0; i < maxLenMin1; i++ ) {
         joined.append(toJoin[i]);
         joined.append(token);
      }
      joined.append(toJoin[maxLenMin1]);

      return joined.toString();

   }

   /**
    * 
    * Joins an String array and returns a string representation of it
    * 
    * @param toJoin array of string to be joined
    * @param token token to place between joined elements
    * @param itemPrefix prefix to place before every element
    * @param itemSufix sufix to place after every element
    * @return the result of the join operation
    */
   public static String join( String[] toJoin, String token, String itemPrefix, String itemSufix ) {
      return join(toJoin, token, itemPrefix, itemSufix, "", "");
   }

   /**
    * 
    * Joins an String array and returns a string representation of it
    * 
    * @param toJoin array of string to be joined
    * @param token token to place between joined elements
    * @param itemPrefix prefix to place before every element
    * @param itemSufix sufix to place after every element
    * @param linePrefix prefix to place at the begining of the whole joined line
    * @param lineSufix sufix to place at the end of the whole joined line
    * @return the result of the join operation
    */
   public static String join( String[] toJoin, String token, String itemPrefix, String itemSufix, String linePrefix, String lineSufix ) {

      StringBuffer joined = new StringBuffer();
      final int maxLenMin1 = toJoin.length - 1;

      joined.append(linePrefix);

      for ( int i = 0; i < maxLenMin1; i++ ) {
         joined.append(itemPrefix);
         joined.append(toJoin[i]);
         joined.append(itemSufix);
         joined.append(token);
      }
      joined.append(itemPrefix);
      joined.append(toJoin[maxLenMin1]);
      joined.append(itemSufix);

      joined.append(lineSufix);

      return joined.toString();

   }

   /**
    * 
   * Joins an String array and returns a string representation of it,
   * no tokens are added between the items
   * 
    * @param toJoin array of string to be joined
    * @return
    */
   public static String joinCompact( String[] toJoin ) {
      return join(toJoin, "");
   }

   /**
    *
   * Joins an String array by separating the items with spaces 
   * and returns a string representation of it
   * 
    * @param toJoin array of string to be joined
    * @return
    */
   public static String joinSpaced( String[] toJoin ) {
      return join(toJoin, " ");
   }

   /**
    * 
    * Crops a String from the left if its length exceds the specified 
    * max length. That is, the length of the returned may be less or 
    * equal to the length of the incoming string
    *   
    * @param toCrop String to be eventually crop 
    * @param maxSize the size to use in order to crop
    * @return the croped string
    */
   public static String leftCrop( String toCrop, int maxSize ) {
      if ( toCrop.length() > maxSize ) {
         if ( maxSize > 0 ) {
            return toCrop.substring(0, maxSize);
         }
         return "";
      }
      return toCrop;

   }

   /**
    * 
    * Tabulates the incoming character by aligning it to the left and 
    * appending spaces at the right, please notice that the data will be 
    * croped at need
    *  
    * @param toTabulate string to be tabulated
    * @param tabSize tabulation size
    * @return tabulated and eventually croped string aligned to the left
    */
   public static String leftTab( String toTabulate, int tabSize ) {
      return leftCrop(toTabulate + replicate(' ', tabSize - toTabulate.length()), tabSize);
   }

   /**
    * 
    * Tabulates the incoming string by aligning it to the left and 
    * appending the specified fill character at the right, please notice 
    * that the data will be croped at need 
    * 
    * @param toTabulate string to be tabulated
    * @param tabSize tabulation size
    * @param fillCharacter character to use for filling tasks
    * @return tabulated and eventually croped string aligned to the left
    */
   public static String leftTab( String toTabulate, int tabSize, char fillCharacter ) {
      return leftCrop(toTabulate + replicate(fillCharacter, tabSize - toTabulate.length()), tabSize);
   }

   /**
    * 
    * Tabulates the incoming string by aligning it to the left and 
    * appending the specified replicated fill string at the right, 
    * please notice that the data will be croped at need
    *
    * @param toTabulate string to be tabulated
    * @param tabSize tabulation size
    * @param fillString string to use for filling tasks
    * @return tabulated and eventually croped string aligned to the left
    */
   public static String leftTab( String toTabulate, int tabSize, String fillString ) {
      return leftCrop(toTabulate + replicate(fillString, tabSize - toTabulate.length()), tabSize);
   }

   /**
    * Replicates a character
    *    
    * @param toReplicate character to be replicated
    * @param times how many times should be the character replicated
    * @return the cumulated string resulted from the replication
    */
   public static String replicate( char toReplicate, int times ) {
      StringBuffer replicated = new StringBuffer();
      for ( int i = 0; i < times; i++ ) {
         replicated.append(toReplicate);
      }
      return replicated.toString();
   }

   /**
    * 
    * Replicates a String
    *    
    * @param toReplicate string to be replicated
    * @param times how many times should be the string replicated
    * @return the cumulated string resulted from the replication
    */
   public static String replicate( String toReplicate, int times ) {
      StringBuffer replicated = new StringBuffer();
      for ( int i = 0; i < times; i++ ) {
         replicated.append(toReplicate);
      }
      return replicated.toString();
   }

   /**
    * 
    * Crops a String from the right if its length exceds the specified 
    * max length. That is, the length of the returned may be less or 
    * equal to the length of the incoming string
    *   
    * @param toCrop String to be eventually crop 
    * @param maxSize the size to use in order to crop
    * @return the croped string
    */
   public static String rightCrop( String toCrop, int maxSize ) {
      if ( toCrop.length() > maxSize ) {
         if ( maxSize > 0 ) {
            return toCrop.substring(toCrop.length() - maxSize);
         }
         return "";
      }
      return toCrop;
   }

   /**
    * 
    * Tabulates the incoming character by aligning it to the right and 
    * appending spaces at the left, please notice that the data will be 
    * croped at need
    *  
    * @param toTabulate string to be tabulated
    * @param tabSize tabulation size
    * @return tabulated and eventually croped string aligned to the left
    */
   public static String rightTab( String toTabulate, int tabSize ) {
      return rightCrop(replicate(' ', tabSize - toTabulate.length()) + toTabulate, tabSize);
   }

   /**
    * Tabulates the incoming string by aligning it to the right and 
    * appending the specified fill character at the left, please notice 
    * that the data will be croped at need 
    * 
    * @param toTabulate string to be tabulated
    * @param tabSize tabulation size
    * @param fillCharacter character to use for filling tasks
    * @return tabulated and eventually croped string aligned to the left
    */
   public static String rightTab( String toTabulate, int tabSize, char fillCharacter ) {
      return rightCrop(replicate(fillCharacter, tabSize - toTabulate.length()) + toTabulate, tabSize);
   }

   /**
    * Tabulates the incoming string by aligning it to the right and 
    * appending the specified replicated fill string at the left, 
    * please notice that the data will be croped at need
    * 
    * @param toTabulate string to be tabulated
    * @param tabSize tabulation size
    * @param fillString string to use for filling tasks
    * @return tabulated and eventually croped string aligned to the left
    */
   public static String rightTab( String toTabulate, int tabSize, String fillString ) {
      return rightCrop(replicate(fillString, tabSize - toTabulate.length()) + toTabulate, tabSize);
   }

}
