import java.io.IOException;

public class QrySopSum extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25(r);
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
            (r.getClass().getName() + " doesn't support the OR operator.");
        }
    }

    /**
     * The matching scheme for BM25.
     * Its matching behavior is like OR operator.
     * @return boolean if matches
     * */
    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }
 
    /**
     *  getScore for the BM25 retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */   
    public double getScoreBM25(RetrievalModel r) throws IOException {
        double total = 0.0;
        if (!this.docIteratorHasMatchCache()) {
            return total;
        }
        int id = this.docIteratorGetMatch();
        for (Qry q : args) {
            // match the right document at a time
            if (q.docIteratorHasMatch(r) && (q.docIteratorGetMatch() == id)) {
                double curr = ((QrySop)q).getScore(r);
                total += curr;
            }
        }
        
        return total;
    }
    
    /**
     *  getScore for the Indri retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScoreIndri(RetrievalModel r) throws IOException {
        return 0.0;
    }
    
    /**
     * Calculate the MLE weight.
     * @param r the retrieval model
     * @return the MLE weight score 
     * */
    private double getMLE() throws IOException {
        QryIop q = (QryIop)(this.getArg(0));
        double ctf = (double)q.invertedList.ctf;
        double termC = (double)Idx.getSumOfFieldLengths(q.getField());
        return ctf / termC;
    }
    
    /**
     * The default score function to deal with non matching query terms.
     * @param r the retrieval model
     * @param docid the document id
     * @return the default score
     * */
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        double mu = (double)((RetrievalModelIndri)r).getMu();
        double lambda = ((RetrievalModelIndri)r).getLambda();
        QryIop q = (QryIop)(this.getArg(0));
        double docLength = Idx.getFieldLength(q.getField(), (int)docid);
        double pMLE = getMLE();
        double leftSmooth = (1.0 - lambda) * (mu * pMLE) / (mu + docLength);
        double rightSmooth = lambda * pMLE;
        
        return leftSmooth + rightSmooth;
    }
    
}