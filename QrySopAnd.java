import java.io.IOException;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    @Override
    public double getScore(RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelUnrankedBoolean) {
            return getScoreUnrankedBoolean(r);
        } else if (r instanceof RetrievalModelRankedBoolean) {
            return getScoreRankedBoolean(r);
        } else if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25(r);
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException(r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        if (r instanceof RetrievalModelIndri) {
            return this.docIteratorHasMatchMin(r);
        } else {
            return this.docIteratorHasMatchAll(r);
        }
    }
    
    /**
     *  getScore for the UnrankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatch(r)) {
            return 0.0;
        } else {
            return 1.0;
        }
    }
    
    /**
     *  getScore for the RankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScoreRankedBoolean(RetrievalModel r) throws IOException {
        // using the minimum operator
        double min = ((QrySop)(args.get(0))).getScore(r);
        for (Qry q : args) {
            double curr = ((QrySop)q).getScore(r);
            if (min > curr) { min = curr; }
        }
        
        return min;
    }
    
    /**
     *  getScore for the BM25 retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScoreBM25(RetrievalModel r) throws IOException {
        return 0.0;
    }
    
    
    /**
     *  getScore for the Indri retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScoreIndri(RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        }
        else {
            double geometricMean = 1.0;
            double qSize = (double)args.size();
            int id = this.docIteratorGetMatch();
            for (Qry q : args) {
                if (q.docIteratorHasMatch(r) && (q.docIteratorGetMatch() == id)) {
                    geometricMean *= ((QrySop)q).getScore(r);
                }
                else {
                    geometricMean *= ((QrySop)q).getDefaultScore(r, id);
                }
            }
            return Math.pow(geometricMean, 1.0 / qSize);
        }
    }
    
    /**
     * The default score function to deal with non matching query terms.
     * @param r the retrieval model
     * @param docid the document id
     * @return the default score
     * */
    public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
        double geometricMean = 1.0;
        double qSize = (double)args.size();
        for (Qry q : args) {
            geometricMean *= ((QrySop)q).getDefaultScore(r, docid);
        }
        return Math.pow(geometricMean, 1.0 / qSize);
    }
    
}