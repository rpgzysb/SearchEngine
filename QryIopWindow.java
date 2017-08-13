import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

/**
 * The near operator produces an inverted list. Arguments must be in order.
 * */
public class QryIopWindow extends QryIopProximity {

    /**
     * Constructor for near operator.
     * */
    public QryIopWindow(int d) {
        super(d);
    }
    
    /**
     * The distance function for near operator.
     * It determines absolute distance between adjacent arguments is >= 0 && <= n terms.
     * */
    @Override
    public boolean withinDistance(int loc1, int loc2) {
        int difference = Math.abs(loc1 - loc2);
        return (difference < this.distance); 
    }
    
    /**
     * This function looks for the minimal location position among all lists in a document.
     * @param size the size of the argument
     * @return the minimal location
     * */
    private int getMinLoc(int size) {
        int minloc = this.getArg(0).locIteratorGetMatch();
        for (int i = 1; i < size; ++i) {
            int currloc = this.getArg(i).locIteratorGetMatch();
            if (minloc > currloc) {
                minloc = currloc;
            }
        }
        return minloc;
    }
    
    /**
     * This function looks for the maximal location position among all lists in a document.
     * @param size the size of the argument
     * @return the maximal location
     * */
    private int getMaxLoc(int size) {
        int maxloc = this.getArg(0).locIteratorGetMatch();
        for (int i = 1; i < size; ++i) {
            int currloc = this.getArg(i).locIteratorGetMatch();
            if (maxloc < currloc) {
                maxloc = currloc;
            }
        }
        return maxloc;
    }
    
    /**
     * This is the function to process the position lists to find out
     * matching locations.
     * @return the position lists
     * */
    @Override
    public List<Integer> processPositionList() {
        List<Integer> windowPositions = new LinkedList<Integer>();
        int size = this.args.size();
        // keep iterating the location lists
        boolean notFinish = true;
        while (notFinish) {
            // make sure all location matches
            if (!isAllLocHasMatchFrom(0, size)) {
                notFinish = false;
                break;
            }
            // get the minimal and maximal locations
            int minloc = getMinLoc(size);
            int maxloc = getMaxLoc(size);
            if (notFinish) {  
                if (withinDistance(minloc, maxloc)) {
                    // get a match, append the maximal location
                    windowPositions.add(maxloc);
                    advanceAllLocIteratorFrom(0, size);
                } else {
                    // does not match
                    // advance the smallest
                    int pastMin = maxloc - this.distance;
                    advanceSmallestLocPastFrom(0, size, pastMin);
                }   
            }
        }
        return windowPositions;
    }
    
}