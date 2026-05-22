FROM eclipse-temurin:21-alpine
WORKDIR /
ADD *.jar server.jar
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "server.jar"]
