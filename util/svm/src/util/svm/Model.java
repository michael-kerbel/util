package util.svm;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import libsvm.svm_model;
import libsvm.svm_node;


/** just a wrapper for {@link svm_model} providing a nicer name */
public class Model extends svm_model {

   Map<String, Field> _fields = new HashMap<String, Field>();


   public Model( svm_model parent ) {
      try {
         Field[] fields = svm_model.class.getDeclaredFields();
         for ( Field field : fields ) {
            field.setAccessible(true);
            Object value = field.get(parent);
            field.set(this, value);
            _fields.put(field.getName(), field);
         }
      }
      catch ( Exception argh ) {
         throw new RuntimeException("Failed to clone svm_model", argh);
      }
   }

   /** number of classes, = 2 in regression/one class svm */
   public int getClassNumber() {
      try {
         return (Integer)_fields.get("nr_class").get(this);
      }
      catch ( Exception argh ) {
         throw new RuntimeException("Failed to get data from underlying svm_model", argh);
      }
   }

   /** label of each class (label[k]) - for classification only */
   public int[] getLabels() {
      try {
         return (int[])_fields.get("label").get(this);
      }
      catch ( Exception argh ) {
         throw new RuntimeException("Failed to get data from underlying svm_model", argh);
      }
   }

   public Parameters getParameters() {
      try {
         return (Parameters)_fields.get("param").get(this);
      }
      catch ( Exception argh ) {
         throw new RuntimeException("Failed to get data from underlying svm_model", argh);
      }
   }

   /** pairwise probability information */
   public double[] getProbA() {
      try {
         return (double[])_fields.get("probA").get(this);
      }
      catch ( Exception argh ) {
         throw new RuntimeException("Failed to get data from underlying svm_model", argh);
      }
   }

   /** pairwise probability information */
   public double[] getProbB() {
      try {
         return (double[])_fields.get("probB").get(this);
      }
      catch ( Exception argh ) {
         throw new RuntimeException("Failed to get data from underlying svm_model", argh);
      }
   }

   /** constants in decision functions (rho[k*(k-1)/2]) */
   public double[] getRho() {
      try {
         return (double[])_fields.get("rho").get(this);
      }
      catch ( Exception argh ) {
         throw new RuntimeException("Failed to get data from underlying svm_model", argh);
      }
   }

   /** coefficients for SVs in decision functions (sv_coef[k-1][l]) */
   public double[][] getSupportVectorCoefficients() {
      try {
         return (double[][])_fields.get("SV").get(this);
      }
      catch ( Exception argh ) {
         throw new RuntimeException("Failed to get data from underlying svm_model", argh);
      }
   }

   public int getSupportVectorNumber() {
      try {
         return (Integer)_fields.get("l").get(this);
      }
      catch ( Exception argh ) {
         throw new RuntimeException("Failed to get data from underlying svm_model", argh);
      }
   }

   // for classification only

   /** number of SVs for each class (nSV[k]) - for classification only */
   public int[] getSupportVectorNumbers() {
      try {
         return (int[])_fields.get("nSV").get(this);
      }
      catch ( Exception argh ) {
         throw new RuntimeException("Failed to get data from underlying svm_model", argh);
      }
   }

   public svm_node[][] getSupportVectors() {
      try {
         return (svm_node[][])_fields.get("SV").get(this);
      }
      catch ( Exception argh ) {
         throw new RuntimeException("Failed to get data from underlying svm_model", argh);
      }
   }

}
