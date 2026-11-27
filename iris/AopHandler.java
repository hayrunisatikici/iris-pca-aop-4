package iris;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

/**
 * AOP katmanı. Arayüzdeki her metod çağrısı önce buradan geçer:
 *  1) Loglama  2) Doğrulama  3) Zaman ölçümü  4) Hata yönetimi  5) Performans metrikleri
 * Böylece PcaPipelineImpl içinde bu işlerin hiçbiri yazılmaz.
 */
public class AopHandler implements InvocationHandler {

    private static final Logger LOG = Logger.getLogger("iris");
    private static final Map<String, Stat> STATS = new LinkedHashMap<>();

    /** Bir metodun toplanan performans bilgileri. */
    private static class Stat {
        int calls, errors;
        long totalNs, maxNs, bytes;
    }

    private final Object target;

    private AopHandler(Object target) {
        this.target = target;
    }

    /** Nesneyi proxy ile sarar: artık her çağrı invoke() içinden geçer. */
    public static <T> T wrap(Class<T> iface, T target) {
        return iface.cast(Proxy.newProxyInstance(
                iface.getClassLoader(), new Class<?>[]{iface}, new AopHandler(target)));
    }

    /** Log ayarı: hem konsola hem logs/ klasöründeki dosyaya yazar (dosya 100 KB'ı geçince döner). */
    public static void setupLogging() {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT | %4$-7s | %5$s%n");
        try {
            Files.createDirectories(Path.of("logs"));
            SimpleFormatter formatter = new SimpleFormatter();

            FileHandler file = new FileHandler("logs/app_%g.log", 100_000, 3, true);
            file.setEncoding("UTF-8");
            file.setFormatter(formatter);

            StreamHandler console = new StreamHandler(System.out, formatter) {
                @Override
                public synchronized void publish(LogRecord record) {
                    super.publish(record);
                    flush();
                }
            };
            console.setEncoding("UTF-8");

            LOG.setUseParentHandlers(false);
            LOG.addHandler(file);
            LOG.addHandler(console);
        } catch (IOException e) {
            throw new RuntimeException("Log dosyası açılamadı", e);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        long start = System.nanoTime();
        long memStart = allocatedBytes();
        boolean ok = false;

        LOG.info(name + " başladı");                                   // 1) LOGLAMA
        try {
            validate(args);                                            // 2) DOĞRULAMA
            Object result = method.invoke(target, args);
            ok = true;
            return result;
        } catch (InvocationTargetException e) {                        // 4) HATA YÖNETİMİ
            LOG.severe(name + " HATA: " + e.getCause());
            throw e.getCause();
        } catch (RuntimeException e) {                                 // doğrulama hatası
            LOG.severe(name + " HATA: " + e);
            throw e;
        } finally {
            long ns = System.nanoTime() - start;                       // 3) ZAMAN ÖLÇÜMÜ
            long bytes = Math.max(allocatedBytes() - memStart, 0);
            record(name, ok, ns, bytes);                               // 5) METRİK TOPLAMA
            LOG.info(String.format("%s bitti | süre: %.3f ms | bellek: %d KB | durum: %s",
                    name, ns / 1e6, bytes / 1024, ok ? "BAŞARILI" : "HATALI"));
        }
    }

    // ---------- Doğrulama ----------
    private static void validate(Object[] args) {
        if (args == null) {
            return;
        }
        for (Object a : args) {
            if (a == null) {
                throw new IllegalArgumentException("Parametre null olamaz");
            }
            if (a instanceof String s && s.isBlank()) {
                throw new IllegalArgumentException("Metin parametresi boş olamaz");
            }
            if (a instanceof double[][] m) {
                checkMatrix(m);
            }
            if (a instanceof PcaPipeline.Dataset d) {
                checkMatrix(d.x());
            }
        }
    }

    private static void checkMatrix(double[][] m) {
        if (m.length == 0) {
            throw new IllegalArgumentException("Veri matrisi boş");
        }
        for (int i = 0; i < m.length; i++) {
            if (m[i].length != m[0].length) {
                throw new IllegalArgumentException("Satır " + i + " diğer satırlarla aynı uzunlukta değil");
            }
            for (double v : m[i]) {
                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    throw new IllegalArgumentException("Satır " + i + " eksik (NaN) veya sonsuz değer içeriyor");
                }
            }
        }
    }

    // ---------- Performans metrikleri ----------
    private static long allocatedBytes() {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        return bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    private static synchronized void record(String name, boolean ok, long ns, long bytes) {
        Stat s = STATS.computeIfAbsent(name, k -> new Stat());
        s.calls++;
        if (!ok) {
            s.errors++;
        }
        s.totalNs += ns;
        s.maxNs = Math.max(s.maxNs, ns);
        s.bytes += bytes;
    }

    public static synchronized void printReport() {
        System.out.printf("%-12s %6s %6s %12s %12s %12s %12s%n",
                "metod", "çağrı", "hata", "toplam(ms)", "ortalama(ms)", "en uzun(ms)", "bellek(KB)");
        for (Map.Entry<String, Stat> e : STATS.entrySet()) {
            Stat s = e.getValue();
            System.out.printf("%-12s %6d %6d %12.3f %12.3f %12.3f %12d%n", e.getKey(), s.calls, s.errors,
                    s.totalNs / 1e6, s.totalNs / 1e6 / s.calls, s.maxNs / 1e6, s.bytes / 1024);
        }
    }
}