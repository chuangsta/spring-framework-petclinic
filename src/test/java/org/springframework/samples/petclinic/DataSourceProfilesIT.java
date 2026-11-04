/*
 * Copyright 2002-2026 the original author or authors.
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

import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.naming.NameNotFoundException;
import javax.sql.DataSource;

import org.eclipse.jetty.ee10.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.ee10.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.ee10.webapp.Configuration;
import org.eclipse.jetty.ee10.webapp.FragmentConfiguration;
import org.eclipse.jetty.ee10.webapp.JettyWebXmlConfiguration;
import org.eclipse.jetty.ee10.webapp.JndiConfiguration;
import org.eclipse.jetty.ee10.webapp.JspConfiguration;
import org.eclipse.jetty.ee10.webapp.MetaInfConfiguration;
import org.eclipse.jetty.ee10.webapp.WebAppConfiguration;
import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.ee10.webapp.WebInfConfiguration;
import org.eclipse.jetty.ee10.webapp.WebXmlConfiguration;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.plus.jndi.Resource;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.parsing.BeanDefinitionParsingException;
import org.springframework.beans.factory.support.BeanDefinitionOverrideException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jndi.JndiObjectFactoryBean;
import org.springframework.samples.petclinic.repository.OwnerRepository;
import org.springframework.samples.petclinic.repository.jdbc.JdbcOwnerRepositoryImpl;
import org.springframework.samples.petclinic.repository.jpa.JpaOwnerRepositoryImpl;
import org.springframework.samples.petclinic.repository.springdatajpa.SpringDataOwnerRepository;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.samples.petclinic.web.OwnerController;
import org.springframework.web.SpringServletContainerInitializer;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.servlet.FrameworkServlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Starts the application through its external-container path ({@link SpringServletContainerInitializer} calling
 * {@link PetclinicInitializer}, which runs Spring Boot) inside a real Jetty 12 EE10 web application with Jetty JNDI
 * naming, and checks which data source each Spring profile combination selects.
 * <p>
 * Every {@code javaee} case binds its own new in-memory H2 database at {@code java:comp/env/jdbc/petclinic}. The
 * application's schema script drops tables, so no pre-existing database is ever used. The server has only an
 * in-memory {@link LocalConnector} and opens no network port.
 * <p>
 * Not part of the 102-test characterization run (Surefire includes {@code *Tests.java} only). Run it with
 * {@code ./mvnw -Dtest=DataSourceProfilesIT test}.
 */
class DataSourceProfilesIT {

    private static final String DISPATCHER_CONTEXT_ATTRIBUTE = FrameworkServlet.SERVLET_CONTEXT_PREFIX + "dispatcher";

    private static final String LOCAL_URL = "jdbc:h2:mem:petclinic";

    @Test
    void jpaWithJavaeeUsesTheBoundDataSource() throws Exception {
        assertBoundDataSourceSelected("jpa,javaee", JpaOwnerRepositoryImpl.class, true);
    }

    @Test
    void jdbcWithJavaeeUsesTheBoundDataSource() throws Exception {
        assertBoundDataSourceSelected("jdbc,javaee", JdbcOwnerRepositoryImpl.class, false);
    }

    @Test
    void springDataJpaWithJavaeeUsesTheBoundDataSource() throws Exception {
        assertBoundDataSourceSelected("spring-data-jpa,javaee", SpringDataOwnerRepository.class, true);
    }

    @Test
    void javaeeWithoutBindingFailsInsteadOfUsingTheLocalPool() throws Exception {
        try (JettyWebApp webApp = new JettyWebApp("jpa,javaee", null)) {
            Throwable failure = catchThrowable(webApp::start);

            assertThat(failure).as("startup without java:comp/env/jdbc/petclinic").isNotNull();
            assertThat(causes(failure)).anySatisfy(cause -> assertThat(cause)
                .isInstanceOf(NameNotFoundException.class));
            assertThat(causes(failure)).anySatisfy(cause -> {
                assertThat(cause).isInstanceOf(BeanCreationException.class);
                assertThat(((BeanCreationException) cause).getBeanName()).isEqualTo("dataSource");
            });
            assertNoDuplicateDefinition(failure);
            assertThat(webApp.rootContextOrNull())
                .as("root context after the failed startup")
                .satisfiesAnyOf(
                    context -> assertThat(context).isNull(),
                    context -> assertThat(((ConfigurableApplicationContext) context).isActive()).isFalse());
            report("jpa,javaee (nothing bound)", "startup failed: " + rootCause(failure));
        }
        assertThat(beanDefinitionClassNames("jpa", "javaee"))
            .as("no local pool definition to fall back to")
            .doesNotContain(org.apache.tomcat.jdbc.pool.DataSource.class.getName());
    }

    @Test
    void javaeeAloneDoesNotActivateJpa() throws Exception {
        Throwable failure;
        try (SyntheticH2 database = new SyntheticH2("javaee");
             JettyWebApp webApp = new JettyWebApp("javaee", database.dataSource)) {
            failure = catchThrowable(webApp::start);

            assertThat(failure).as("startup with javaee and no persistence profile").isNotNull();
            assertNoDuplicateDefinition(failure);
            assertThat(causes(failure)).anySatisfy(cause -> {
                assertThat(cause).isInstanceOf(NoSuchBeanDefinitionException.class);
                assertThat(((NoSuchBeanDefinitionException) cause).getBeanType())
                    .isNotNull()
                    .satisfies(type -> assertThat(type.getPackageName())
                        .isEqualTo(OwnerRepository.class.getPackageName()));
            });
        }
        DefaultListableBeanFactory definitions = loadBeanDefinitions("javaee");
        assertThat(definitions.containsBeanDefinition("entityManagerFactory")).isFalse();
        assertThat(definitions.containsBeanDefinition("transactionManager")).isFalse();
        assertThat(definitions.getBeanDefinition("dataSource").getBeanClassName())
            .isEqualTo(JndiObjectFactoryBean.class.getName());
        report("javaee", "no entityManagerFactory/transactionManager defined; startup failed: " + rootCause(failure));
    }

    @Test
    void noProfileUsesTheLocalPoolWithJpa() throws Exception {
        assertLocalDataSourceSelected(null, JpaOwnerRepositoryImpl.class, true);
    }

    @Test
    void jpaUsesTheLocalPool() throws Exception {
        assertLocalDataSourceSelected("jpa", JpaOwnerRepositoryImpl.class, true);
    }

    @Test
    void jdbcUsesTheLocalPool() throws Exception {
        assertLocalDataSourceSelected("jdbc", JdbcOwnerRepositoryImpl.class, false);
    }

    @Test
    void springDataJpaUsesTheLocalPool() throws Exception {
        assertLocalDataSourceSelected("spring-data-jpa", SpringDataOwnerRepository.class, true);
    }

    private void assertBoundDataSourceSelected(String profiles, Class<?> ownerRepositoryType, boolean jpa)
        throws Exception {
        try (SyntheticH2 database = new SyntheticH2(profiles)) {
            assertThat(database.tableCount()).as("fresh synthetic database before startup").isZero();
            try (JettyWebApp webApp = new JettyWebApp(profiles, database.dataSource)) {
                webApp.start();
                WebApplicationContext root = webApp.rootContext();

                assertThat(root.getEnvironment().getActiveProfiles())
                    .containsExactlyInAnyOrder(profiles.split(","));
                assertSingleDataSource(root);
                DataSource dataSource = root.getBean("dataSource", DataSource.class);
                assertThat(dataSource).as("bean 'dataSource' is the object bound in JNDI")
                    .isSameAs(database.dataSource);
                assertThat(root.getBeanNamesForType(org.apache.tomcat.jdbc.pool.DataSource.class)).isEmpty();
                try (Connection connection = dataSource.getConnection()) {
                    assertThat(connection.getMetaData().getURL()).isEqualTo(database.connectionUrl);
                }
                assertThat(database.ownerCount()).as("owners seeded into the bound database").isEqualTo(10);
                assertApplicationReadsSeedData(webApp, root, ownerRepositoryType, jpa);
                report(profiles, "dataSource is the JNDI-bound " + dataSource.getClass().getSimpleName() + "@"
                    + Integer.toHexString(System.identityHashCode(dataSource)) + ", url " + database.connectionUrl
                    + ", 10 owners seeded, owner 2 Betty, " + ownerRepositoryType.getSimpleName() + ", /vets.json 6 vets");
            }
        }
    }

    private void assertLocalDataSourceSelected(String profiles, Class<?> ownerRepositoryType, boolean jpa)
        throws Exception {
        org.apache.tomcat.jdbc.pool.DataSource pool = null;
        try (JettyWebApp webApp = new JettyWebApp(profiles, null)) {
            webApp.start();
            WebApplicationContext root = webApp.rootContext();

            if (profiles == null) {
                assertThat(root.getEnvironment().getActiveProfiles()).isEmpty();
                assertThat(root.getEnvironment().getDefaultProfiles()).containsExactly("jpa");
            } else {
                assertThat(root.getEnvironment().getActiveProfiles()).containsExactly(profiles);
            }
            assertSingleDataSource(root);
            assertThat(root.getBean("dataSource")).isInstanceOf(org.apache.tomcat.jdbc.pool.DataSource.class);
            pool = root.getBean("dataSource", org.apache.tomcat.jdbc.pool.DataSource.class);
            assertThat(pool.getUrl()).isEqualTo(LOCAL_URL);
            assertThat(new JdbcTemplate(pool).queryForObject("select count(*) from owners", Integer.class))
                .isEqualTo(10);
            assertApplicationReadsSeedData(webApp, root, ownerRepositoryType, jpa);
            report(profiles == null ? "(none, default jpa)" : profiles, "dataSource is the local tomcat-jdbc pool, url "
                + pool.getUrl() + ", 10 owners seeded, owner 2 Betty, " + ownerRepositoryType.getSimpleName()
                + ", /vets.json 6 vets");
        } finally {
            if (pool != null) {
                // The XML bean has no destroy method; closing the pool lets H2 drop this test's in-memory database.
                pool.close(true);
            }
        }
    }

    private static void assertSingleDataSource(WebApplicationContext root) {
        assertThat(((ConfigurableApplicationContext) root).getBeanFactory())
            .isInstanceOfSatisfying(DefaultListableBeanFactory.class, beanFactory ->
                assertThat(beanFactory.isAllowBeanDefinitionOverriding()).as("bean definition overriding").isFalse());
        assertThat(root.getBeanNamesForType(DataSource.class)).containsExactly("dataSource");
    }

    private static void assertApplicationReadsSeedData(JettyWebApp webApp, WebApplicationContext root,
                                                       Class<?> ownerRepositoryType, boolean jpa) throws Exception {
        String[] ownerRepositories = root.getBeanNamesForType(OwnerRepository.class);
        assertThat(ownerRepositories).hasSize(1);
        Object ownerRepository = root.getBean(ownerRepositories[0]);
        assertThat(ownerRepositoryType.isInstance(ownerRepository)
            || ownerRepositoryType.isAssignableFrom(AopUtils.getTargetClass(ownerRepository)))
            .as("OwnerRepository implementation %s is a %s", AopUtils.getTargetClass(ownerRepository),
                ownerRepositoryType.getName())
            .isTrue();
        assertThat(root.containsBean("entityManagerFactory")).as("JPA entity manager factory").isEqualTo(jpa);
        assertThat(root.getBean(ClinicService.class).findOwnerById(2).getFirstName()).isEqualTo("Betty");

        WebApplicationContext dispatcher = webApp.dispatcherContext();
        assertThat(dispatcher.getParent()).as("dispatcher context parent").isSameAs(root);
        assertThat(dispatcher.getBeanNamesForType(OwnerController.class)).hasSize(1);
        assertThat(root.getBeanNamesForType(OwnerController.class)).isEmpty();
        assertThat(dispatcher.getBeanNamesForType(DataSource.class)).isEmpty();

        HttpTester.Response vets = webApp.get("/vets.json");
        assertThat(vets.getStatus()).isEqualTo(200);
        assertThat(vets.getContent().split("\"firstName\"", -1)).hasSize(7);
    }

    private static void report(String profiles, String result) {
        System.out.println("[DataSourceProfilesIT] profiles " + profiles + ": " + result);
    }

    private static String rootCause(Throwable failure) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(failure);
        return cause.getClass().getName() + ": " + cause.getMessage();
    }

    private static void assertNoDuplicateDefinition(Throwable failure) {
        // The XML reader reports a refused duplicate as a BeanDefinitionParsingException.
        assertThat(causes(failure))
            .noneMatch(cause -> cause instanceof BeanDefinitionOverrideException
                || cause instanceof BeanDefinitionParsingException);
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> causes = new ArrayList<>();
        collectCauses(failure, causes);
        return causes;
    }

    private static void collectCauses(Throwable throwable, List<Throwable> causes) {
        if (throwable == null || causes.contains(throwable)) {
            return;
        }
        causes.add(throwable);
        collectCauses(throwable.getCause(), causes);
        for (Throwable suppressed : throwable.getSuppressed()) {
            collectCauses(suppressed, causes);
        }
    }

    private static DefaultListableBeanFactory loadBeanDefinitions(String... activeProfiles) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.setAllowBeanDefinitionOverriding(false);
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(activeProfiles);
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(beanFactory);
        reader.setEnvironment(environment);
        reader.loadBeanDefinitions("classpath:spring/business-config.xml");
        return beanFactory;
    }

    private static List<String> beanDefinitionClassNames(String... activeProfiles) {
        DefaultListableBeanFactory beanFactory = loadBeanDefinitions(activeProfiles);
        List<String> classNames = new ArrayList<>();
        for (String name : beanFactory.getBeanDefinitionNames()) {
            classNames.add(beanFactory.getBeanDefinition(name).getBeanClassName());
        }
        return classNames;
    }

    /**
     * A new, empty in-memory H2 database. {@link SimpleDriverDataSource} is not {@code Referenceable}, so Jetty binds
     * and returns this exact object, which lets the test compare identities.
     */
    private static final class SyntheticH2 implements AutoCloseable {

        /** The URL H2 reports for an open connection: the database name without connection settings. */
        final String connectionUrl;

        final SimpleDriverDataSource dataSource;

        SyntheticH2(String label) {
            this.connectionUrl = "jdbc:h2:mem:datasource-profiles-it-" + label.replace(',', '-') + "-" + UUID.randomUUID();
            this.dataSource = new SimpleDriverDataSource(new org.h2.Driver(), connectionUrl + ";DB_CLOSE_DELAY=-1",
                "sa", "");
        }

        int tableCount() {
            return new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'PUBLIC'", Integer.class);
        }

        int ownerCount() {
            return new JdbcTemplate(dataSource).queryForObject("select count(*) from owners", Integer.class);
        }

        @Override
        public void close() {
            new JdbcTemplate(dataSource).execute("SHUTDOWN");
        }
    }

    /**
     * {@code src/main/webapp} deployed as a Jetty 12 EE10 web application with JNDI support, initialized the way an
     * external container initializes the WAR.
     */
    private static final class JettyWebApp implements AutoCloseable {

        private final Server server = new Server();

        private final LocalConnector connector = new LocalConnector(server);

        private final WebAppContext webApp = new WebAppContext();

        private final Resource binding;

        JettyWebApp(String activeProfiles, DataSource jndiDataSource) throws Exception {
            server.addConnector(connector);
            webApp.setContextPath("/");
            webApp.setWar(Path.of("src/main/webapp").toAbsolutePath().toString());
            webApp.setParentLoaderPriority(true);
            webApp.setThrowUnavailableOnStartupException(true);
            // No AnnotationConfiguration: the Spring initializer is registered explicitly below instead of scanned.
            webApp.setConfigurations(new Configuration[]{
                new WebInfConfiguration(), new WebXmlConfiguration(), new MetaInfConfiguration(),
                new FragmentConfiguration(), new JndiConfiguration(), new EnvConfiguration(), new PlusConfiguration(),
                new WebAppConfiguration(), new JspConfiguration(), new JettyWebXmlConfiguration()});
            webApp.addServletContainerInitializer(new SpringServletContainerInitializer(), PetclinicInitializer.class);
            if (activeProfiles != null) {
                webApp.setInitParameter("spring.profiles.active", activeProfiles);
            }
            if (jndiDataSource != null) {
                // Jetty itself refuses to deploy a resource-ref that has nothing bound, so the descriptor is only
                // used together with a binding; without one, java:comp/env/jdbc/petclinic simply does not exist.
                webApp.setDescriptor(
                    Path.of("src/test/resources/datasource-profiles/web.xml").toAbsolutePath().toString());
                this.binding = new Resource(webApp, "jdbc/petclinic", jndiDataSource);
            } else {
                this.binding = null;
            }
            server.setHandler(webApp);
        }

        void start() throws Exception {
            server.start();
        }

        WebApplicationContext rootContextOrNull() {
            return WebApplicationContextUtils.getWebApplicationContext(webApp.getServletContext());
        }

        WebApplicationContext rootContext() {
            return WebApplicationContextUtils.getRequiredWebApplicationContext(webApp.getServletContext());
        }

        WebApplicationContext dispatcherContext() {
            Object context = webApp.getServletContext().getAttribute(DISPATCHER_CONTEXT_ATTRIBUTE);
            assertThat(context).as("dispatcher context").isInstanceOf(WebApplicationContext.class);
            return (WebApplicationContext) context;
        }

        HttpTester.Response get(String path) throws Exception {
            return HttpTester.parseResponse(connector.getResponse(
                "GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"));
        }

        @Override
        public void close() throws Exception {
            try {
                server.stop();
                server.destroy();
            } finally {
                if (binding != null) {
                    binding.release();
                }
            }
        }
    }

}
