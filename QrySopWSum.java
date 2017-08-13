import java.io.IOException;

public class QrySopWSum extends QrySopWeight {

      
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
     * Calculate the user weight.
     * @param r the retrieval model
     * @param idx the query idx
     * @return the user weight
     * */
    private double calcUserWeight(RetrievalModel r, int idx) {
        double k3 = ((RetrievalModelBM25)r).getK3();
        double qtf = this.getWeights().get(idx);
        return (k3 + 1.0) * qtf / (k3 + qtf);
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
        double sumWeight = this.getSumWeight();
        for (int i = 0; i < args.size(); ++i) {
            Qry q = args.get(i);
            // match the right document at a time
            if (q.docIteratorHasMatch(r) && (q.docIteratorGetMatch() == id)) {
                double curr = ((QrySop)q).getScore(r) * calcUserWeight(r, i);
                total += curr;
            }
        }
        
        return total / sumWeight;
    }
    
    /**
     *  getScore for the Indri retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */   
    public double getScoreIndri(RetrievalModel r) throws IOException {
        double weightSum = 0.0;
        double sumWeight = this.getSumWeight();
        int id = this.docIteratorGetMatch();
        for (int i = 0; i < args.size(); ++i) {
            Qry q = args.get(i);
            double currWeight = this.getWeightAt(i);
            if (q.docIteratorHasMatch(r) && (q.docIteratorGetMatch() == id)) {
                weightSum += currWeight * ((QrySop)q).getScore(r);
            }
            else {
                weightSum += currWeight * ((QrySop)q).getDefaultScore(r, id);
            }
        }
        return weightSum / sumWeight;
    }
    
    /**
     * The default score function to deal with non matching query terms.
     * @param r the retrieval model
     * @param docid the document id
     * @return the default score
     * */
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        double weightSum = 0.0;
        for (int i = 0; i < args.size(); ++i) {
            Qry q = args.get(i);
            double currWeight = this.getWeightAt(i);
            weightSum += currWeight * ((QrySop)q).getDefaultScore(r, docid);
        }
        return weightSum;
    }
}