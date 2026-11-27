package iris;

/** İşlem adımlarının arayüzü. AOP (dinamik proxy) bu arayüz üzerinden çalışır. */
public interface PcaPipeline {

    /** Veri seti: özellik matrisi, sınıf etiketleri, özellik adları. */
    record Dataset(double[][] x, String[] labels, String[] featureNames) {
    }

    /** PCA sonucu: izdüşüm, tüm bileşenlerin açıkladığı varyans oranı. */
    record PcaResult(double[][] projected, double[] explainedRatio) {
    }

    Dataset load(String file);                         // veri yükleme

    void describe(Dataset data);                       // veri setinin kapsamı

    double[][] standardize(double[][] x);              // ön işleme

    PcaResult pca(double[][] x, int k);                // PCA analizi

    void plot(PcaResult result, String[] labels, String file);  // görselleştirme
}