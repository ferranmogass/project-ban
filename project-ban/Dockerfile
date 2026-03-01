FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -q -DskipTests clean package


FROM tomcat:9.0-jdk21-temurin
COPY --from=build /app/target/project-ban-0.1.war /usr/local/tomcat/webapps/project-ban-0.1.war
EXPOSE 8081