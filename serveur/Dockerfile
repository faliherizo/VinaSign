# Image du serveur (VS-003) : JRE 21, compte non privilégié, couches Spring Boot extraites, racine en lecture seule
# au déploiement (chart Helm). Le jar est construit par la CI (mvn verify), jamais dans l'image.
FROM eclipse-temurin:21-jre-alpine AS extraction
WORKDIR /extraction
COPY target/vinasign-serveur-*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --launcher --destination couches

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 vinasign && adduser -S -u 10001 -G vinasign vinasign
WORKDIR /application
COPY --from=extraction /extraction/couches/dependencies/ ./
COPY --from=extraction /extraction/couches/spring-boot-loader/ ./
COPY --from=extraction /extraction/couches/snapshot-dependencies/ ./
COPY --from=extraction /extraction/couches/application/ ./
USER 10001:10001
EXPOSE 8300
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.io.tmpdir=/tmp", "org.springframework.boot.loader.launch.JarLauncher"]
