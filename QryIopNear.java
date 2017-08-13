import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

/**
 * The near operator produces an inverted list. Arguments must be in order.
 * */
public class QryIopNear extends QryIopProximity {

    /**
     * Constructor for near operator.
     * */
    public QryIopNear(int d) {
        super(d);
    }
    
    /**
     * The distance function for near operator.
     * It determines distance between adjacent arguments is >= 0 && <= n terms.
     * */
    @Override
    public boolean withinDistance(int loc1, int loc2) {
        return (loc2 <= loc1 + this.distance); 
    }

    /**
     * This function tests whether all location pair match.
     * @param prevloc the initial location
     * @param size the size of the arguments
     * @return true if all location pair match, false otherwise
     * */
    private boolean matchNearPairs(int prevloc, int size) {
        for (int i = 1; i < size; ++i) {
            QryIop qi = this.getArg(i);
            qi.locIteratorAdvancePast(prevloc);
            if (!qi.locIteratorHasMatch()) {
                // at the end
                // finish
                return false;
            } else {
                int currloc = qi.locIteratorGetMatch();
                if (!withinDistance(prevloc, currloc)) {
                    // does not match this pair
                    // finish
                    return false;
                }
                prevloc = currloc;
            }
        }
        // match all pairs
        return true;
    }
    
    /**
     * This is the function to process the position lists to find out
     * matching locations.
     * @return the position lists
     * */
    @Override
    public List<Integer> processPositionList() {
        List<Integer> nearPositions = new LinkedList<Integer>();
        int size = this.args.size();
        // keep iterating the location lists
        boolean notFinish = true;
        while (notFinish) {
            // select the first document
            QryIop q0 = this.getArg(0);
            if (!q0.locIteratorHasMatch()) {
                break;
            } else {
                // see if all match
                boolean locationMatch = matchNearPairs(q0.locIteratorGetMatch(), size);
                if (locationMatch) {
                    // if all match, then append the last position into the list
                    nearPositions.add(this.getArg(size - 1).locIteratorGetMatch());
                    // advance all pointers, and the q0 in all cases
                    advanceAllLocIteratorFrom(1, size);
                }
                q0.locIteratorAdvance();
            }
        }
        return nearPositions;
    }
    
}