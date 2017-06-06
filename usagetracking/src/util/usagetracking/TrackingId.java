package util.usagetracking;

public interface TrackingId {

   public void collectCyclicValues( int[] values );

   public TrackingId getForId( int id );

   public TrackingId getForName( String name );

   public Aggregation getAggregation();

   public int getId();

   public TrackingId getSlave();

   public TrackingId getAggregationWeight();

   public int getMaxId();

}
