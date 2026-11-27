# Iris Veri Seti Üzerinde PCA Uygulaması ve AOP Tabanlı İzleme Sistemi

Bu proje, **Makine Öğrenmesi** ve **Yazılım Mimarisi / Nesne Yönelimli Programlama** prensiplerini bir araya getirmek amacıyla geliştirilmiş bir dönem ödevidir. Projede, klasik **Iris veri seti** üzerinde **Temel Bileşenler Analizi (PCA - Principal Component Analysis)** uygulanmış ve bu veri işleme hattındaki (pipeline) tüm adımlar **Aspect-Oriented Programming (AOP - Cephe Yönelimli Programlama)** yaklaşımı ile ana iş mantığından bağımsız olarak izlenmiş, loglanmış ve performans açısından analiz edilmiştir.

---

## 👥 Proje Ekibi

Bu proje 2 kişilik bir çalışma grubu olarak geliştirilmiştir:

1. **Hayrunisa Tikici** — `1250505061` (Kırklareli Üniversitesi - Yazılım Mühendisliği)
2. **[Ekip Arkadaşının Adı Soyadı]** — `[Öğrenci Numarası]` (Kırklareli Üniversitesi - Yazılım Mühendisliği)

---

## 🎯 Projenin Amacı ve Kapsamı

Makine öğrenmesi ve veri bilimi süreçlerinde veri yükleme, ön işleme, boyut indirgeme ve görselleştirme gibi adımların çalışma sürelerini ölçmek, hataları yakalamak ve log tutmak kritik öneme sahiptir. Ancak bu izleme kodlarının doğrudan algoritma sınıflarının içine yazılması kod tekrarını artırır ve okunabilirliği düşürür.

Bu projede **Sorumlulukların Ayrılması (Separation of Concerns)** ilkesi benimsenerek:
* **Çekirdek İş Mantığı (Core Business Logic):** Iris veri setinin okunması, Z-score standardizasyonu, kovaryans matrisi/özdeğer hesaplamalarıyla PCA uygulanması ve 2B scatter plot (saçılım grafiği) üretilmesi işlemlerini yapar.
* **Kesişen İlgiler (Cross-Cutting Concerns - AOP):** Harici bir framework karmaşasına girilmeden, doğrudan Java'nın standart `java.lang.reflect.Proxy` ve `InvocationHandler` altyapısı kullanılarak dinamik bir proxy katmanı oluşturulmuş; tüm metot çağrıları bu katman üzerinden geçirilmiştir.

---

## 🏗️ Proje Mimarisi ve Dosya Yapısı

```text
iris-pca-aop/
├── iris/
│   ├── Main.java               # Uygulama giriş noktası, proxy sarmalama ve hata senaryosu testleri
│   ├── PcaPipeline.java        # İşlem adımlarını ve veri taşıyıcı record yapılarını tanımlayan arayüz
│   ├── PcaPipelineImpl.java    # Veri yükleme, standardizasyon, PCA ve görselleştirme algoritmaları
│   └── AopHandler.java         # Loglama, doğrulama, süre/bellek ölçümü ve hata yönetimini yapan AOP katmanı
├── output/
│   └── pca_iris.png            # PCA sonucu elde edilen 2 boyutlu sınıf dağılım grafiği
├── iris.csv                    # 150 örnek ve 4 özellikten oluşan Iris veri seti
└── README.md                   # Proje dokümantasyonu


🚀 Kurulum ve Çalıştırma

Bu proje, harici kütüphanelere veya karmaşık yapılandırma araçlarına ihtiyaç duymadan doğrudan **Java (JDK 17 ve üzeri)** ortamında çalışacak şekilde tasarlanmıştır.
Projeyi yerel bilgisayarınızda çalıştırmak için şu adımları izleyebilirsiniz:

1. **Projeyi İndirin:** Aşağıdaki komutla depoyu bilgisayarınıza kopyalayın veya doğrudan ZIP olarak indirin:
   ```bash
   git clone [https://github.com/hayrunisatikici/iris-pca-aop.git](https://github.com/hayrunisatikici/iris-pca-aop.git)
   cd iris-pca-aop
Klasör Yapısını Doğrulayın: Proje ana dizininde iris.csv veri setinin ve grafik çıktısının kaydedileceği output klasörünün bulunduğundan emin olun.

Uygulamayı Başlatın: Projeyi IntelliJ IDEA veya tercih ettiğiniz bir Java IDE'sinde açarak iris/Main.java dosyasını çalıştırın. Komut satırından çalıştırmak isterseniz şu komutları kullanabilirsiniz:

Bash
javac iris/*.java
java iris.Main

📊 Uygulama Çıktıları ve Değerlendirme
Uygulama çalıştırıldığında hem matematiksel PCA analizini hem de AOP izleme katmanının topladığı metrikleri konsolda sunar:

Veri Seti İstatistikleri: 150 örneğe ait sınıf dağılımları (Setosa, Versicolor, Virginica) ile her özelliğin ortalama, minimum ve maksimum değerleri özetlenir.

PCA Varyans Oranları: 4 boyutlu veri 2 boyuta indirgendiğinde PC1 (%72.96) ve PC2 (%22.85) bileşenlerinin toplam varyansın %95.81'ini koruduğu görülür.

Görsel Çıktı: Elde edilen 2 boyutlu sınıf dağılımı grafiği output/pca_iris.png konumuna kaydedilir.

AOP Log ve Hata Yönetimi: Bilerek verilen hatalı senaryolarda (NaN veri, geçersiz bileşen sayısı ve olmayan dosya) sistem çökmeden hataları yakalar, zaman damgasıyla loglar ve her metodun çalışma süresi ile bellek kullanımını tablo halinde raporlar.
