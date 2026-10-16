# Spring PetClinic Sample Application

[![Java CI with Maven](https://github.com/spring-petclinic/spring-framework-petclinic/actions/workflows/maven-build.yml/badge.svg)](https://github.com/spring-petclinic/spring-framework-petclinic/actions/workflows/maven-build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=spring-petclinic_spring-framework-petclinic&metric=alert_status)](https://sonarcloud.io/dashboard?id=spring-petclinic_spring-framework-petclinic)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=spring-petclinic_spring-framework-petclinic&metric=coverage)](https://sonarcloud.io/dashboard?id=spring-petclinic_spring-framework-petclinic)

Approved by the Spring team, this repo is a fork of the [spring-projects/spring-petclinic](https://github.com/spring-projects/spring-petclinic).
It allows the Spring community to maintain a Petclinic version with a plain old **Spring Framework configuration**
and with a **3-layer architecture** (i.e. presentation --> service --> repository).
The "canonical" implementation is now based on Spring Boot, Thymeleaf and [aggregate-oriented domain]([https://github.com/spring-projects/spring-petclinic/pull/200). 

This version runs on Java 21 with Spring Framework 6.2, Spring Data JPA 3.5, Hibernate ORM 6.6 and Jakarta EE 10
(Servlet 6.0, JSP/JSTL 3.0). It is packaged as a WAR and run locally with the Jetty 12 EE10 Maven plugin.


## Understanding the Spring Petclinic application with a few diagrams

[See the presentation here](http://fr.slideshare.net/AntoineRey/spring-framework-petclinic-sample-application) (2017 update)

## Running petclinic locally

### Build toolchain: JDK 21 and Maven 3.9

The project builds and tests with JDK 21 and Maven 3.9.16. The Maven wrapper (`./mvnw`) downloads
Maven 3.9.16 on first use.

The compiler targets Java 21 (`maven.compiler.release` = `21`) on Spring Framework 6.2 and Jakarta EE 10,
so JDK 21 or newer is required; JDK 8 can no longer build the project.

A prebuilt local toolchain can live under `~/.m2/tools` without changing the machine default JDK:

* JDK 21 (Temurin 21.0.12.1+1): `~/.m2/tools/jdk-21.0.12.1+1/Contents/Home`
* Maven 3.9.16: `~/.m2/tools/apache-maven-3.9.16`

Select them for the current shell only through `JAVA_HOME` and `PATH`:
```
export JAVA_HOME=~/.m2/tools/jdk-21.0.12.1+1/Contents/Home
export PATH=~/.m2/tools/apache-maven-3.9.16/bin:$PATH
java -version   # 21.0.12.1
mvn --version   # Apache Maven 3.9.16, Java version: 21.0.12.1
```

Run the tests and build the WAR:
```
mvn test
mvn -DskipTests package
```

### With Maven command line
```
git clone https://github.com/spring-petclinic/spring-framework-petclinic.git
cd spring-framework-petclinic
./mvnw jetty:run-war
# For Windows : ./mvnw.cmd jetty:run-war
```
Run these commands with `JAVA_HOME` pointing at JDK 21, as shown above.

### With Docker
```
docker run -p 8080:8080 springcommunity/spring-framework-petclinic
```

You can then access petclinic here: [http://localhost:8080/](http://localhost:8080/)

<img width="1042" alt="petclinic-screenshot" src="https://cloud.githubusercontent.com/assets/838318/19727082/2aee6d6c-9b8e-11e6-81fe-e889a5ddfded.png">

## In case you find a bug/suggested improvement for Spring Petclinic

Our issue tracker is available here: https://github.com/spring-petclinic/spring-framework-petclinic/issues


## Database configuration

In its default configuration, Petclinic uses an in-memory database (H2) which gets populated at startup with data.

A similar setups is provided for MySQL and PostgreSQL in case a persistent database configuration is needed.
To run petclinic locally using persistent database, it is needed to run with profile defined in main pom.xml file.

For MySQL database, it is needed to run with 'MySQL' profile defined in main pom.xml file.

```
./mvnw jetty:run-war -P MySQL
```

Before do this, would be good to check properties defined in MySQL profile inside pom.xml file.

```
<properties>
    <jpa.database>MYSQL</jpa.database>
    <jdbc.driverClassName>com.mysql.cj.jdbc.Driver</jdbc.driverClassName>
    <jdbc.url>jdbc:mysql://localhost:3306/petclinic?useUnicode=true</jdbc.url>
    <jdbc.username>petclinic</jdbc.username>
    <jdbc.password>petclinic</jdbc.password>
</properties>
```      

You could start MySQL locally with whatever installer works for your OS, or with docker:

```
docker run -e MYSQL_USER=petclinic -e MYSQL_PASSWORD=petclinic -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=petclinic -p 3306:3306 mysql:5.7.8
```

For PostgreSQL database, it is needed to run with 'PostgreSQL' profile defined in main pom.xml file.

```
./mvnw jetty:run-war -P PostgreSQL
```

Before do this, would be good to check properties defined in PostgreSQL profile inside pom.xml file.

```
<properties>
    <jpa.database>POSTGRESQL</jpa.database>
    <jdbc.driverClassName>org.postgresql.Driver</jdbc.driverClassName>
    <jdbc.url>jdbc:postgresql://localhost:5432/petclinic</jdbc.url>
    <jdbc.username>postgres</jdbc.username>
    <jdbc.password>petclinic</jdbc.password>
</properties>
```
You could also start PostgreSQL locally with whatever installer works for your OS, or with docker:

```
docker run --name postgres-petclinic -e POSTGRES_PASSWORD=petclinic -e POSTGRES_DB=petclinic -p 5432:5432 -d postgres:9.6.0
```

## Persistence layer choice

The persistence layer have 3 available implementations: JPA (default), JDBC and Spring Data JPA.
The default JPA implementation could be changed by using a Spring profile: `jdbc`, `spring-data-jpa` and `jpa`.  
As an example, you may use the `-Dspring.profiles.active=jdbc` VM options to start the application with the JDBC implementation.

```
./mvnw jetty:run-war -Dspring.profiles.active=jdbc
```


## Working with Petclinic in your IDE

### Prerequisites
The following items should be installed in your system:
* JDK 21 (full JDK not a JRE), selected through `JAVA_HOME`
* Maven 3.9+ (http://maven.apache.org/install.html), or the bundled `./mvnw` wrapper (Maven 3.9.16)
* git command line tool (https://help.github.com/articles/set-up-git)
* Optional: a Jakarta EE 10 (Servlet 6.0) web container such as Jetty 12 (EE10) or Tomcat 10.1, if you prefer
  deploying the WAR from your IDE instead of running `./mvnw jetty:run-war`
* Your prefered IDE 
  * Eclipse with the m2e plugin. Note: when m2e is available, there is an m2 icon in Help -> About dialog. If m2e is not there, just follow the install process here: http://www.eclipse.org/m2e/
  * [Spring Tools Suite](https://spring.io/tools) (STS)
  * IntelliJ IDEA


### Steps:

1) On the command line
```
git clone https://github.com/spring-petclinic/spring-framework-petclinic.git
```

2) Inside Eclipse or STS
```
File -> Import -> Maven -> Existing Maven project
```
Then either build on the command line `./mvnw generate-resources` or using the Eclipse launcher (right click on project and `Run As -> Maven install`) to generate the CSS.
Run `./mvnw jetty:run-war` from the command line, or configure a Jakarta EE 10 web container (Jetty 12 EE10 or
Tomcat 10.1) and deploy the `petclinic.war` file.

3) Inside IntelliJ IDEA

In the main menu, select `File > Open` and select the Petclinic [pom.xml](pom.xml). Click on the `Open` button.

CSS files are generated from the Maven build. You can either build them on the command line `./mvnw generate-resources` 
or right click on the `spring-petclinic` project then `Maven -> Generates sources and Update Folders`.

Set the project SDK to JDK 21. Then either run the `jetty:run-war` Maven goal, or go to `Run -> Edit Configuration`,
configure a Jakarta EE 10 web container (Jetty 12 EE10 or Tomcat 10.1), and deploy the `petclinic.war` file.
Run the application by clicking on the `Run` icon.


4) Navigate to Petclinic

Visit [http://localhost:8080](http://localhost:8080) in your browser.


## Looking for something in particular?

| Java Config |   |
|-------------|---|
| Java config branch | Petclinic uses XML configuration by default. In case you'd like to use Java Config instead, there is a Java Config branch available [here](https://github.com/spring-petclinic/spring-framework-petclinic/tree/javaconfig) |

| Inside the 'Web' layer | Files |
|------------------------|-------|
| Spring MVC - XML integration | [mvc-view-config.xml](src/main/resources/spring/mvc-view-config.xml)  |
| Spring MVC - ContentNegotiatingViewResolver| [mvc-view-config.xml](src/main/resources/spring/mvc-view-config.xml) |
| JSP custom tags | [WEB-INF/tags](src/main/webapp/WEB-INF/tags), [createOrUpdateOwnerForm.jsp](src/main/webapp/WEB-INF/jsp/owners/createOrUpdateOwnerForm.jsp)|
| JavaScript dependencies | [JavaScript libraries are declared as webjars in the pom.xml](pom.xml) |
| Static resources config | [Resource mapping in Spring configuration](/src/main/resources/spring/mvc-core-config.xml#L30) |
| Static resources usage | [htmlHeader.tag](src/main/webapp/WEB-INF/tags/htmlHeader.tag), [footer.tag](src/main/webapp/WEB-INF/tags/footer.tag) |
| Thymeleaf | In the late 2016, the original [Spring Petclinic](https://github.com/spring-projects/spring-petclinic) has moved from JSP to Thymeleaf. |

| 'Service' and 'Repository' layers | Files |
|-----------------------------------|-------|
| Transactions | [business-config.xml](src/main/resources/spring/business-config.xml), [ClinicServiceImpl.java](src/main/java/org/springframework/samples/petclinic/service/ClinicServiceImpl.java) |
| Cache | [tools-config.xml](src/main/resources/spring/tools-config.xml), [ClinicServiceImpl.java](src/main/java/org/springframework/samples/petclinic/service/ClinicServiceImpl.java) |
| Bean Profiles | [business-config.xml](src/main/resources/spring/business-config.xml), [ClinicServiceJdbcTests.java](src/test/java/org/springframework/samples/petclinic/service/ClinicServiceJdbcTests.java), [PetclinicInitializer.java](src/main/java/org/springframework/samples/petclinic/PetclinicInitializer.java) |
| JDBC | [business-config.xml](src/main/resources/spring/business-config.xml), [jdbc folder](src/main/java/org/springframework/samples/petclinic/repository/jdbc) |
| JPA | [business-config.xml](src/main/resources/spring/business-config.xml), [jpa folder](src/main/java/org/springframework/samples/petclinic/repository/jpa) |
| Spring Data JPA | [business-config.xml](src/main/resources/spring/business-config.xml), [springdatajpa folder](src/main/java/org/springframework/samples/petclinic/repository/springdatajpa) |


## Publishing a Docker image

This application uses [Google Jib]([https://github.com/GoogleContainerTools/jib) to build an optimized Docker image
into the [Docker Hub](https://cloud.docker.com/u/springcommunity/repository/docker/springcommunity/spring-framework-petclinic/)
repository.
The [pom.xml](pom.xml) has been configured to publish the image with a the `springcommunity/spring-framework-petclinic` image name.

Jib containerizes this WAR project by using the [distroless Jetty](https://github.com/GoogleContainerTools/distroless/tree/master/java/jetty) as a base image.

Note: the Jib configuration has not been updated for this migration. Its default base image predates Jakarta EE 10,
so it may not run this WAR. This is listed for review in the dependency risk review; verify the image before publishing.

Build and push the container image of Petclinic to the Docker Hub registry:
```
mvn jib:build
```


## Interesting Spring Petclinic forks

The Spring Petclinic master branch in the main [spring-projects](https://github.com/spring-projects/spring-petclinic)
GitHub org is the "canonical" implementation, currently based on Spring Boot and Thymeleaf.

This [spring-framework-petclinic](https://github.com/spring-petclinic/spring-framework-petclinic) project is one of the [several forks](https://spring-petclinic.github.io/docs/forks.html) 
hosted in a special GitHub org: [spring-petclinic](https://github.com/spring-petclinic).
If you have a special interest in a different technology stack
that could be used to implement the Pet Clinic then please join the community there.


## Interaction with other open source projects

One of the best parts about working on the Spring Petclinic application is that we have the opportunity to work in direct contact with many Open Source projects. We found some bugs/suggested improvements on various topics such as Spring, Spring Data, Bean Validation and even Eclipse! In many cases, they've been fixed/implemented in just a few days.
Here is a list of them:

| Name | Issue |
|------|-------|
| Spring JDBC: simplify usage of NamedParameterJdbcTemplate | [SPR-10256](https://jira.springsource.org/browse/SPR-10256) and [SPR-10257](https://jira.springsource.org/browse/SPR-10257) |
| Bean Validation / Hibernate Validator: simplify Maven dependencies and backward compatibility |[HV-790](https://hibernate.atlassian.net/browse/HV-790) and [HV-792](https://hibernate.atlassian.net/browse/HV-792) |
| Spring Data: provide more flexibility when working with JPQL queries | [DATAJPA-292](https://jira.springsource.org/browse/DATAJPA-292) |
| Dandelion: improves the DandelionFilter for Jetty support | [113](https://github.com/dandelion/dandelion/issues/113) |


# Contributing

The [issue tracker](/issues) is the preferred channel for bug reports, features requests and submitting pull requests.

For pull requests, editor preferences are available in the [editor config](.editorconfig) for easy use in common text editors. Read more and download plugins at <http://editorconfig.org>.




