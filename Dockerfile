# Estágio 1: Build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Copia todos os arquivos do projeto para o container
COPY . .
# Garante permissão de execução e faz o build ignorando os testes
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

# Estágio 2: Execução
FROM eclipse-temurin:21-jre
WORKDIR /app
# Copia apenas o .jar gerado no estágio anterior
COPY --from=build /app/target/*.jar app.jar
# Comando para iniciar o Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]