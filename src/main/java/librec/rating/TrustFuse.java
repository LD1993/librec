package librec.rating;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import librec.data.DenseMatrix;
import librec.data.MatrixEntry;
import librec.data.SparseMatrix;
import librec.data.SparseVector;
import librec.intf.SocialRecommender;

/**
 * Created by AndyXue on 8/28/2015.
 */
public class TrustFuse extends SocialRecommender {

    private Table<Integer, Integer, Double> userCorrs;
    private float beta;
    private int iternum;
    private float damping_facor;
    private double min_delta;
    private SparseVector userWeights;

    public TrustFuse(SparseMatrix trainMatrix, SparseMatrix testMatrix, int fold) {
        super(trainMatrix, testMatrix, fold);

        initByNorm = false;
    }

    @Override
    protected void initModel() throws Exception {
        super.initModel();

        userCorrs = HashBasedTable.create();
        beta = algoOptions.getFloat("-beta");
        iternum = algoOptions.getInt("-iternum", 1000);
        damping_facor = algoOptions.getFloat("-damping_facor", 0.85f);
        min_delta = algoOptions.getDouble("-min_delta", 0.0001);
    }

    /**
     * compute similarity between users u and v
     */
    protected double similarity(Integer u, Integer v) {
        if (userCorrs.contains(u, v))
            return userCorrs.get(u, v);

        if (userCorrs.contains(v, u))
            return userCorrs.get(v, u);

        double sim = Double.NaN;

        if (u < trainMatrix.numRows() && v < trainMatrix.numRows()) {
            SparseVector uv = trainMatrix.row(u);
            if (uv.getCount() > 0) {
                SparseVector vv = trainMatrix.row(v);
                sim = correlation(uv, vv, "pcc"); // could change to other measures

                if (!Double.isNaN(sim))
                    sim = (1.0 + sim) / 2;
            }
        }

        userCorrs.put(u, v, sim);

        return sim;
    }

    /**
     * compute trustWeight index by users u
     */
    protected double trustWeight(Integer u) {
        if (userWeights.contains(u))
            return userWeights.get(u);

        // init transfer vector
        double[] tranferVector = new double[numUsers];
        for (int i = 0; i < numUsers; i++) {
            tranferVector[i] = 1.0;
        }
        userWeights = new SparseVector(numUsers, tranferVector);

        if (u < trainMatrix.numRows()) {
            double min_value = (1.0 - damping_facor) / numUsers;
            for (int i = 0; i < iternum; i++) {
                double diff = 0.0;
                for (int j = 0; j < numUsers; j++) {
                    double rank = min_value;
                    SparseVector uv = trainMatrix.row(i);
                    for (int k : uv.getIndex()) {
                        rank += damping_facor * tranferVector[k] / trainMatrix.row(k).size();
                    }
                    diff += Math.abs(tranferVector[j] - rank);
                    tranferVector[j] = rank;
                }
                if (diff < min_delta)
                    break;
            }
        }
        return userWeights.get(u);
    }


    @Override
    protected void buildModel() throws Exception {
        for (int iter = 1; iter <= numIters; iter++) {

            loss = 0;

            // temp data
            DenseMatrix PS = new DenseMatrix(numUsers, numFactors);
            DenseMatrix QS = new DenseMatrix(numItems, numFactors);

            // ratings
            for (MatrixEntry me : trainMatrix) {
                int u = me.row();
                int j = me.column();
                double ruj = me.get();

                double pred = predict(u, j);
                double euj = pred - ruj;

                loss += euj * euj;

                for (int f = 0; f < numFactors; f++) {
                    double puf = P.get(u, f);
                    double qjf = Q.get(j, f);

                    PS.add(u, f, euj * qjf + regU * puf);
                    QS.add(j, f, euj * puf + regI * qjf);

                    loss += regU * puf * puf + regI * qjf * qjf;
                }
            }

            // friends
            for (int u = 0; u < numUsers; u++) {
                // out links: F+
                SparseVector uos = socialMatrix.row(u);

                double sumweight = 0.0;
                for (int k : uos.getIndex()) {
                    sumweight += trustWeight(k);
                }

                for (int k : uos.getIndex()) {
                    double suk = similarity(u, k);
                    double weight = trustWeight(k)/sumweight;
                    if (!Double.isNaN(suk)) {
                        for (int f = 0; f < numFactors; f++) {
                            double euk = P.get(u, f) - weight * P.get(k, f);
                            PS.add(u, f, beta * suk * euk);
                            loss += beta * suk * euk * euk;
                        }
                    }
                }
            } // end of for loop

            P = P.add(PS.scale(-lRate));
            Q = Q.add(QS.scale(-lRate));

            loss *= 0.5;

            if (isConverged(iter))
                break;
        }
    }

}

