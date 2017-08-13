/**
 *  An object that stores parameters for the ranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {

    /**
     * The smoothing parameter.
     * */
    private int mu;
    private double lambda;
    
    /**
     * The constructor for Indri.
     * @param _mu the mu parameter
     * @param _lambda the lambda paramter
     * */
    RetrievalModelIndri(int _mu, double _lambda) {
        mu = _mu;
        lambda = _lambda;
    }
    
    /**
     * To get the mu parameter.
     * @return the mu parameter
     * */
    public int getMu() {
        return mu;
    }
    
    /**
     * To get the lambda parameter.
     * @return the lambda parameter
     * */
    public double getLambda() {
        return lambda;
    }
    
    
    @Override
    public String defaultQrySopName() {
        return new String("#and");
    }
    
}