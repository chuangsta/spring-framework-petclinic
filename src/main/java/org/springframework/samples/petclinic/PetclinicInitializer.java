/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionManagerCustomizationAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.jta.JtaAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.HttpEncodingAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.DispatcherServlet;

import jakarta.servlet.DispatcherType;


/**
 * Single entry point of the application, for both the executable WAR ({@code java -jar petclinic.war}) and a
 * WAR deployed to an external Servlet container (for example {@code ./mvnw jetty:run-war}).
 * <p/>
 * The Spring "<strong>root</strong>" application context is the Spring Boot context. It imports the existing
 * business and tools XML configuration and nothing else: there is no component scan here.<br/>
 * A single {@link DispatcherServlet}, mapped to {@code /}, owns a child {@link XmlWebApplicationContext} loaded
 * from the existing MVC XML configuration.<br/>
 * A {@link CharacterEncodingFilter} forcing UTF-8 is mapped to that servlet.
 * <p/>
 * Spring Boot auto-configurations that would add a second definition of something the XML configuration
 * already owns (data source and SQL initialization, JPA and repositories, transactions, AOP proxying, cache,
 * JMX, validation, message source, placeholder resolution, task executors, Jackson, Spring MVC, dispatcher,
 * encoding filter, multipart and error handling) are excluded. The embedded web server auto-configuration is kept.
 *
 * @author Antoine Rey
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
    AopAutoConfiguration.class,
    CacheAutoConfiguration.class,
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    DispatcherServletAutoConfiguration.class,
    ErrorMvcAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    HttpEncodingAutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class,
    JacksonAutoConfiguration.class,
    JdbcClientAutoConfiguration.class,
    JdbcTemplateAutoConfiguration.class,
    JmxAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class,
    JtaAutoConfiguration.class,
    MessageSourceAutoConfiguration.class,
    MultipartAutoConfiguration.class,
    PersistenceExceptionTranslationAutoConfiguration.class,
    PropertyPlaceholderAutoConfiguration.class,
    SpringDataWebAutoConfiguration.class,
    SqlInitializationAutoConfiguration.class,
    TaskExecutionAutoConfiguration.class,
    TaskSchedulingAutoConfiguration.class,
    TransactionAutoConfiguration.class,
    TransactionManagerCustomizationAutoConfiguration.class,
    ValidationAutoConfiguration.class,
    WebMvcAutoConfiguration.class
})
@ImportResource({"classpath:spring/business-config.xml", "classpath:spring/tools-config.xml"})
public class PetclinicInitializer extends SpringBootServletInitializer {

    /**
     * Spring profile used to choose the persistence layer implementation.
     * <p>
     * When using Spring jpa, use: jpa
     * When using Spring JDBC, use: jdbc
     * When using Spring Data JPA, use: spring-data-jpa
     * <p/>
     * <p>
     * You also may use the -Dspring.profiles.active=jdbc VM options (or the --spring.profiles.active=jdbc
     * argument of the executable WAR) to change default jpa Spring profile.
     */
    private static final String SPRING_PROFILE = "jpa";

    private static final String DISPATCHER_SERVLET_NAME = "dispatcher";

    public PetclinicInitializer() {
        // Keep the container's own error handling in an external Servlet container, as before Spring Boot.
        setRegisterErrorPageFilter(false);
    }

    public static void main(String[] args) {
        configureApplication(new SpringApplicationBuilder()).run(args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return configureApplication(builder);
    }

    private static SpringApplicationBuilder configureApplication(SpringApplicationBuilder builder) {
        return builder
            .sources(PetclinicInitializer.class)
            .properties(
                "spring.profiles.default=" + SPRING_PROFILE,
                // <mvc:default-servlet-handler/> forwards to the container's "default" servlet
                "server.servlet.register-default-servlet=true");
    }

    @Bean
    public ServletRegistrationBean<DispatcherServlet> dispatcherServletRegistration() {
        XmlWebApplicationContext webAppContext = new XmlWebApplicationContext();
        webAppContext.setConfigLocation("classpath:spring/mvc-core-config.xml");
        ServletRegistrationBean<DispatcherServlet> registration =
            new ServletRegistrationBean<>(new DispatcherServlet(webAppContext), "/");
        registration.setName(DISPATCHER_SERVLET_NAME);
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CharacterEncodingFilter> characterEncodingFilterRegistration() {
        // Used to provide the ability to enter Chinese characters inside the Owner Form
        CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter("UTF-8", true);
        FilterRegistrationBean<CharacterEncodingFilter> registration =
            new FilterRegistrationBean<>(characterEncodingFilter);
        registration.setName("characterEncodingFilter");
        registration.addServletNames(DISPATCHER_SERVLET_NAME);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.INCLUDE,
            DispatcherType.ASYNC);
        registration.setAsyncSupported(true);
        return registration;
    }

}
