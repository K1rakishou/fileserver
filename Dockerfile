FROM openjdk:8
ADD target/cocona.jar cocona.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "cocona.jar"]