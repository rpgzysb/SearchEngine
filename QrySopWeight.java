import java.util.ArrayList;

/**
 * This is the weight operator class. 
 * WAND and WSUM operator will implement this class.
 * */
public abstract class QrySopWeight extends QrySop {

    /**
     * The weights for each query.
     * */
    private ArrayList<Double> weights;
    /**
     * The cached total weight.
     * */
    private double sumWeight;
    
    /**
     * The constructor for weight operator.
     * */
    public QrySopWeight() {
        weights = new ArrayList<Double>();
        sumWeight = 0.0;
    }

    /**
     * Get the weight of a query at an index.
     * @param idx the index
     * @return the corresponding weight.
     * */
    public double getWeightAt(int idx) {
        return weights.get(idx);
    }
    
    /**
     * Get the sum of weight.
     * @return the total weight.
     * */
    public double getSumWeight() {
        return sumWeight;
    }
    
    /**
     * Get the weight array.
     * @return the weight array
     * */
    public ArrayList<Double> getWeights() {
        return weights;
    }

    /**
     * To append weight for this operator.
     * @param w the weight to be appended
     * */
    public void appendWeights(Double w) {
        this.weights.add(w);
        sumWeight += w;
    }
    
    /**
     * Helper function to print out weight.
     * */
    public void printWeight() {
        for (Double w : weights) {
            System.out.print(w);
        }
        System.out.println();
    }
    
    
}