package iris;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Asıl iş mantığı. Loglama, süre ölçümü gibi işler burada YOK, onları AopHandler yapar. */
public class PcaPipelineImpl implements PcaPipeline {

    // ---------- 1) Veri yükleme ----------
    @Override
    public Dataset load(String file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Dosya okunamadı: " + file, e);
        }
        String[] header = lines.get(0).split(",");
        String[] featureNames = Arrays.copyOf(header, header.length - 1);

        int n = lines.size() - 1;
        double[][] x = new double[n][featureNames.length];
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) {
            String[] parts = lines.get(i + 1).split(",");
            if (parts.length != header.length) {
                throw new IllegalArgumentException("Satır " + (i + 2) + " hatalı: sütun sayısı uyuşmuyor");
            }
            for (int j = 0; j < featureNames.length; j++) {
                x[i][j] = Double.parseDouble(parts[j]);
            }
            labels[i] = parts[parts.length - 1];
        }
        return new Dataset(x, labels, featureNames);
    }

    // ---------- 2) Veri setinin kapsamı ----------
    @Override
    public void describe(Dataset data) {
        System.out.println("\n=== VERİ SETİ ÖZETİ ===");
        System.out.println("Örnek sayısı   : " + data.x().length);
        System.out.println("Özellik sayısı : " + data.featureNames().length);

        Map<String, Integer> classCounts = new LinkedHashMap<>();
        for (String l : data.labels()) {
            classCounts.merge(l, 1, Integer::sum);
        }
        System.out.println("Sınıflar       : " + classCounts);

        System.out.printf("%-14s %8s %8s %8s%n", "Özellik (cm)", "ortalama", "min", "maks");
        for (int j = 0; j < data.featureNames().length; j++) {
            double sum = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (double[] row : data.x()) {
                sum += row[j];
                min = Math.min(min, row[j]);
                max = Math.max(max, row[j]);
            }
            System.out.printf("%-14s %8.2f %8.2f %8.2f%n", data.featureNames()[j], sum / data.x().length, min, max);
        }
    }

    // ---------- 3) Ön işleme: standardizasyon (z-score) ----------
    @Override
    public double[][] standardize(double[][] x) {
        int n = x.length, p = x[0].length;
        double[][] z = new double[n][p];
        for (int j = 0; j < p; j++) {
            double mean = 0;
            for (double[] row : x) {
                mean += row[j];
            }
            mean /= n;
            double var = 0;
            for (double[] row : x) {
                var += (row[j] - mean) * (row[j] - mean);
            }
            double std = Math.sqrt(var / n);
            if (std == 0) {
                throw new IllegalArgumentException((j + 1) + ". özelliğin varyansı 0, standardize edilemez");
            }
            for (int i = 0; i < n; i++) {
                z[i][j] = (x[i][j] - mean) / std;
            }
        }
        return z;
    }

    // ---------- 4) PCA ----------
    @Override
    public PcaResult pca(double[][] x, int k) {
        int n = x.length, p = x[0].length;
        if (n < 2) {
            throw new IllegalArgumentException("PCA için en az 2 örnek gerekir");
        }
        if (k < 1 || k > p) {
            throw new IllegalArgumentException("Bileşen sayısı 1 ile " + p + " arasında olmalı, gelen: " + k);
        }

        // Adım 1: Her sütunu ortala
        double[] mean = new double[p];
        for (double[] row : x) {
            for (int j = 0; j < p; j++) {
                mean[j] += row[j] / n;
            }
        }

        // Adım 2: Kovaryans matrisi
        double[][] cov = new double[p][p];
        for (double[] row : x) {
            for (int a = 0; a < p; a++) {
                for (int b = 0; b < p; b++) {
                    cov[a][b] += (row[a] - mean[a]) * (row[b] - mean[b]) / (n - 1);
                }
            }
        }

        // Adım 3: Özdeğer ve özvektorler (Jacobi yöntemi). Sonunda cov'un köşegeni özdeğerlerdir.
        double[][] vectors = new double[p][p];
        for (int i = 0; i < p; i++) {
            vectors[i][i] = 1;
        }
        jacobi(cov, vectors);
        double[] values = new double[p];
        double total = 0;
        for (int i = 0; i < p; i++) {
            values[i] = cov[i][i];
            total += values[i];
        }

        // Adım 4: Özdeğerleri büyükten küçüğe sırala
        Integer[] order = new Integer[p];
        for (int i = 0; i < p; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(values[b], values[a]));

        double[] ratio = new double[p];
        for (int i = 0; i < p; i++) {
            ratio[i] = values[order[i]] / total;
        }

        // Adım 5: Veriyi ilk k özvektör üzerine izdüşür
        double[][] projected = new double[n][k];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < k; c++) {
                for (int j = 0; j < p; j++) {
                    projected[r][c] += (x[r][j] - mean[j]) * vectors[j][order[c]];
                }
            }
        }
        return new PcaResult(projected, ratio);
    }

    /** Simetrik matris için Jacobi özdeğer algoritması. a köşegenleşir, v özvektörleri tutar. */
    private static void jacobi(double[][] a, double[][] v) {
        int n = a.length;
        for (int sweep = 0; sweep < 50; sweep++) {
            double off = 0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    off += a[i][j] * a[i][j];
                }
            }
            if (off < 1e-20) {
                return;
            }
            for (int p = 0; p < n - 1; p++) {
                for (int q = p + 1; q < n; q++) {
                    if (Math.abs(a[p][q]) < 1e-15) {
                        continue;
                    }
                    double theta = (a[q][q] - a[p][p]) / (2 * a[p][q]);
                    double t = (theta >= 0 ? 1 : -1) / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
                    double c = 1 / Math.sqrt(t * t + 1);
                    double s = t * c;
                    for (int k = 0; k < n; k++) {
                        double akp = a[k][p], akq = a[k][q];
                        a[k][p] = c * akp - s * akq;
                        a[k][q] = s * akp + c * akq;
                    }
                    for (int k = 0; k < n; k++) {
                        double apk = a[p][k], aqk = a[q][k];
                        a[p][k] = c * apk - s * aqk;
                        a[q][k] = s * apk + c * aqk;
                    }
                    for (int k = 0; k < n; k++) {
                        double vkp = v[k][p], vkq = v[k][q];
                        v[k][p] = c * vkp - s * vkq;
                        v[k][q] = s * vkp + c * vkq;
                    }
                }
            }
        }
    }

    // ---------- 5) Görselleştirme: PC1 - PC2 dağılım grafiği ----------
    @Override
    public void plot(PcaResult result, String[] labels, String file) {
        double[][] z = result.projected();
        int w = 800, h = 600, m = 70;

        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (double[] pt : z) {
            minX = Math.min(minX, pt[0]);
            maxX = Math.max(maxX, pt[0]);
            minY = Math.min(minY, pt[1]);
            maxY = Math.max(maxY, pt[1]);
        }
        minX -= 0.5;
        maxX += 0.5;
        minY -= 0.5;
        maxY += 0.5;
        double sx = (w - 2.0 * m) / (maxX - minX);
        double sy = (h - 2.0 * m) / (maxY - minY);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.drawRect(m, m, w - 2 * m, h - 2 * m);

        // eksen değerleri (tam sayılar)
        for (int t = (int) Math.ceil(minX); t <= maxX; t++) {
            int px = (int) (m + (t - minX) * sx);
            g.drawLine(px, h - m, px, h - m + 5);
            g.drawString(String.valueOf(t), px - 4, h - m + 20);
        }
        for (int t = (int) Math.ceil(minY); t <= maxY; t++) {
            int py = (int) (h - m - (t - minY) * sy);
            g.drawLine(m - 5, py, m, py);
            g.drawString(String.valueOf(t), m - 22, py + 4);
        }

        // her sınıfa bir renk
        Color[] palette = {new Color(31, 119, 180), new Color(255, 127, 14), new Color(44, 160, 44)};
        Map<String, Color> colors = new LinkedHashMap<>();
        for (String l : labels) {
            colors.computeIfAbsent(l, k -> palette[colors.size() % palette.length]);
        }
        for (int i = 0; i < z.length; i++) {
            int px = (int) (m + (z[i][0] - minX) * sx);
            int py = (int) (h - m - (z[i][1] - minY) * sy);
            g.setColor(colors.get(labels[i]));
            g.fillOval(px - 5, py - 5, 10, 10);
        }

        // başlık, eksen adları, açıklama (legend)
        g.setColor(Color.BLACK);
        g.drawString("Iris veri seti - PCA (PC1 / PC2)", w / 2 - 90, 35);
        g.drawString(String.format("PC1 (%%%.2f varyans)", result.explainedRatio()[0] * 100), w / 2 - 60, h - 20);
        g.drawString(String.format("PC2 (%%%.2f varyans)", result.explainedRatio()[1] * 100), 10, m - 15);
        int ly = m + 20;
        for (Map.Entry<String, Color> e : colors.entrySet()) {
            g.setColor(e.getValue());
            g.fillOval(w - m - 110, ly - 9, 10, 10);
            g.setColor(Color.BLACK);
            g.drawString(e.getKey(), w - m - 92, ly);
            ly += 20;
        }
        g.dispose();

        try {
            new File(file).getAbsoluteFile().getParentFile().mkdirs();
            ImageIO.write(img, "png", new File(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Grafik kaydedilemedi: " + file, e);
        }
    }
}