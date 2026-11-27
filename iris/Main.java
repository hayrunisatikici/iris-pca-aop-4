package iris;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class Main {

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);                                   // ondalık ayracı nokta olsun
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        AopHandler.setupLogging();

        // Nesne AOP proxy'si ile sarılır; bundan sonra her çağrı aspect'lerden geçer.
        PcaPipeline pipeline = AopHandler.wrap(PcaPipeline.class, new PcaPipelineImpl());

        // 1) Veri yükleme, ön işleme, veri özeti
        PcaPipeline.Dataset data = pipeline.load("iris.csv");
        pipeline.describe(data);
        double[][] z = pipeline.standardize(data.x());

        // 2) PCA (4 boyut -> 2 boyut)
        PcaPipeline.PcaResult result = pipeline.pca(z, 2);
        System.out.println("\n=== PCA SONUCU ===");
        for (int i = 0; i < result.explainedRatio().length; i++) {
            System.out.printf("PC%d açıkladığı varyans: %%%.2f%n", i + 1, result.explainedRatio()[i] * 100);
        }
        System.out.printf("PC1 + PC2 toplam: %%%.2f%n",
                (result.explainedRatio()[0] + result.explainedRatio()[1]) * 100);

        // 3) Görselleştirme
        pipeline.plot(result, data.labels(), "output/pca_iris.png");

        // 4) Hata yakalama örnekleri (hata olunca program çökmez, loglanır)
        System.out.println("\n=== HATA YÖNETİMİ ÖRNEKLERİ ===");
        tryCall("Eksik (NaN) veri", () -> pipeline.pca(new double[][]{{1, 2}, {Double.NaN, 3}}, 1));
        tryCall("Hatalı bileşen sayısı", () -> pipeline.pca(z, 10));
        tryCall("Olmayan dosya", () -> pipeline.load("yok.csv"));

        // 5) Performans metrikleri
        System.out.println("\n=== PERFORMANS METRİKLERİ ===");
        AopHandler.printReport();
    }

    private static void tryCall(String title, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            System.out.println("[Yakalandı] " + title + " -> " + e.getMessage());
        }
    }
}