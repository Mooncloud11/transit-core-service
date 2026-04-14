# 1. Aşama: Derleme (Build) Aşaması
# Projeyi derlemek için içinde Maven olan bir Java 17 imajı kullanıyoruz.
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Sadece gerekli dosyaları kopyala
COPY pom.xml .
COPY src ./src

# Projeyi paketle (.jar dosyası oluştur) ve testleri atla (hız kazanmak için)
RUN mvn clean package -DskipTests

# 2. Aşama: Çalıştırma (Run) Aşaması
# Sadece çalıştırmak için çok daha hafif olan JRE imajını kullanıyoruz.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# İlk aşamada üretilen .jar dosyasını bu hafif imajın içine kopyala
COPY --from=build /app/target/*.jar app.jar

# Uygulamanın dış dünya ile konuşacağı portu aç
EXPOSE 8080

# Konteyner ayağa kalktığında çalıştırılacak komut
ENTRYPOINT ["java", "-jar", "app.jar"]