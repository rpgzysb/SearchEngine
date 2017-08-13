import java.io.IOException;

public class QrySopWAnd extends QrySopWeight {

    
    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelIndri) {
            return getScoreIndri(r);
        } else {
            return 0;
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
     *  getScore for the Indri retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */   
    public double getScoreIndri(RetrievalModel r) throws IOException {
        double geometricMean = 1.0;
        double sumWeight = this.getSumWeight();
        int id = this.docIteratorGetMatch();
        
        for (int i = 0; i < args.size(); ++i) {
            double currWeight = this.getWeightAt(i);
            Qry q = args.get(i);
            if (q.docIteratorHasMatch(r) && (q.docIteratorGetMatch() == id)) {
                geometricMean *= Math.pow(((QrySop)q).getScore(r), currWeight);
            }
            else {
                geometricMean *= Math.pow(((QrySop)q).getDefaultScore(r, id), currWeight);
            }
        }
        return Math.pow(geometricMean, 1.0 / sumWeight);
    }
    
    /**
     * The default score function to deal with non matching query terms.
     * @param r the retrieval model
     * @param docid the document id
     * @return the default score
     * */
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        double geometricMean = 1.0;
        double sumWeight = this.getSumWeight();
        for (int i = 0; i < args.size(); ++i) {
            double currWeight = this.getWeightAt(i);
            Qry q = args.get(i);
            geometricMean *= Math.pow(((QrySop)q).getDefaultScore(r, docid), currWeight);
        }
       
        return Math.pow(geometricMean, 1.0 / sumWeight);
    }
}