
public class RetrievalModelBM25 extends RetrievalModel {

    /**
     * The parameters.
     * */
    private double k1;
    private double b;
    private double k3;
    
    /**
     * Constructor for BM25.
     * @param _k1 the k1 parameter
     * @param _b the b parameter
     * @param _k3 the k3 parameter
     * */
    public RetrievalModelBM25(double _k1, double _b, double _k3) {
        k1 = _k1;
        b = _b;
        k3 = _k3;
    }
    
    /**
     * To get the k1 parameter.
     * @return the k1 parameter
     * */
    public double getK1() {
        return this.k1;
    }
    
    /**
     * To get the B parameter.
     * @return the B parameter
     * */
    public double getB() {
        return this.b;
    }
    
    /**
     * To get the k3 parameter.
     * @return the k3 parameter
     * */
    public double getK3() {
        return this.k3;
    }
    
    
    @Override
    public String defaultQrySopName() {
        return new String("#sum");
    }
    
}