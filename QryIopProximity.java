import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;


/**
 * The proximity operator with one distance parameter.
 * */
public abstract class QryIopProximity extends QryIop {

    /**
     * The distance parameter of the near operator.
     * */
    protected int distance;

    /**
     * The constructor for near operator.
     * @param d the distance parameter
     * */
    public QryIopProximity(int d) {
        distance = d;
    }

    /**
     * Distance function defined for proximity operator for loc1 and loc2.
     * Overload by near and windows operator.
     * @param loc1 the first location
     * @param loc2 the second location
     * @return true if they are within distance, false otherwise
     * */
    public abstract boolean withinDistance(int loc1, int loc2);
    
    /**
     * This function will be implemented by NEAR or WINDOW operator.
     * It is used to process the location matching and positions in the inverted
     * list after matching all documents.
     * @return the positions of matching locations.
     * */
    public abstract List<Integer> processPositionList();

    /**
     * This function tests whether all locations beginning from match or not.
     * @param from the beginning location
     * @param size the size of the arguments
     * @return true if all location match, false otherwise
     * */
    protected boolean isAllLocHasMatchFrom(int from, int size) {
        for (int i = from; i < size; ++i) {
            if (!this.getArg(i).locIteratorHasMatch()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * This function advances all location iterators beginning from the
     * from argument.
     * @param from the beginning location
     * @param size the size of the arguments
     * */
    protected void advanceAllLocIteratorFrom(int from, int size) {
        for (int i = from; i < size; ++i) {
            this.getArg(i).locIteratorAdvance();
        }
    }
    
    /**
     * This function advances all locations past after the location beginning from
     * the from argument.
     * @param from the beginning location
     * @param size the size of the arguments
     * @parma loc the smallest location
     * */
    protected void advanceSmallestLocPastFrom(int from, int size, int loc) {
        for (int i = from; i < size; ++i) {
            this.getArg(i).locIteratorAdvancePast(loc);
        }
    }
    
    /**
     * Evaluate the near operator using the withinDistance function provided.
     * */
    @Override
    protected void evaluate() throws IOException {
        this.invertedList = new InvList(this.getField());
        // If there are no arguments: #near(), then this is the result.
        if (args.size() == 0) {
            return;
        } else {
            evaluateGeneral();
        }
    }

    /**
     * This function evaluates the proximity operator in general.
     * It uses the matchAll function from Qry and modifies the code accordingly.
     * When all documents are the same, then it uses processPosition() to argument
     * the inverted list.
     * */
    protected void evaluateGeneral() throws IOException {
        this.invertedList = new InvList(this.getField());
        // If there are no arguments: #near(), then this is the result.
        if (args.size() == 0) {
            return;
        } else {
            boolean matchFound = false;
            // Keep trying until a match is found or no match is possible.
            while (true) {
                // Get the docid of the first query argument.
                Qry q_0 = this.args.get(0);
                if (!q_0.docIteratorHasMatch(null)) {
                    return;
                }
                int docid_0 = q_0.docIteratorGetMatch();
                // Other query arguments must match the docid of the first query
                // argument.
                matchFound = true;
                for (int i = 1; i < this.args.size(); i++) {
                    Qry q_i = this.args.get(i);
                    q_i.docIteratorAdvanceTo (docid_0);
                    if (!q_i.docIteratorHasMatch(null)) {    // If any argument is exhausted
                        return;             // there are no more matches.
                    }
                    int docid_i = q_i.docIteratorGetMatch();
                    if (docid_0 != docid_i) {   // docid_0 can't match.  Try again.
                        q_0.docIteratorAdvanceTo(docid_i);
                        matchFound = false;
                        break;
                    }
                }
                if (matchFound) {
                    // same document, and then match the location use
                    // processPositionList function
                    List<Integer> proximityPositions = processPositionList();
                    if (proximityPositions.size() > 0) {
                        // if there are matches
                        this.invertedList.appendPosting(docid_0, proximityPositions);
                    }
                    // advance the pointer to next
                    q_0.docIteratorAdvancePast(docid_0);
                }
            }
        }
    }



  
}
