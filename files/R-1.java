java
public final class Dimension {
    public static final int activity_horizontal_margin=0x7f060000;
    public static final int activity_vertical_margin=0x7f060001;
    
    public static int modifyDimension(int dimensionID, int newValue) {
        if (dimensionID == 0x7f060000 || dimensionID == 0x7f060001) {
            // Only allow modification of the activity margins
            // Other dimensions are not modifiable to prevent abuse
            return newValue;
        } else {
            throw new IllegalArgumentException("Cannot modify dimension: " + dimensionID);
        }
    }
}