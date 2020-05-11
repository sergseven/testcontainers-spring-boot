package com.playtika.test.localstack;

import java.util.LinkedHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;

import static com.playtika.test.localstack.LocalStackProperties.BEAN_NAME_EMBEDDED_LOCALSTACK;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

@Slf4j
@Configuration
@Order(HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "embedded.localstack.enabled", matchIfMissing = true)
@EnableConfigurationProperties(LocalStackProperties.class)
public class EmbeddedLocalStackBootstrapConfiguration {
    @ConditionalOnMissingBean(name = BEAN_NAME_EMBEDDED_LOCALSTACK)
    @Bean(name = BEAN_NAME_EMBEDDED_LOCALSTACK, destroyMethod = "stop")
    public EmbeddedLocalStackContainer localStack(ConfigurableEnvironment environment,
                                                  LocalStackProperties properties) {
        log.info("Starting Localstack server. Docker image: {}", properties.dockerImage);

        EmbeddedLocalStackContainer localStackContainer = new EmbeddedLocalStackContainer(properties.dockerImage);
        localStackContainer.withEnv("EDGE_PORT", String.valueOf(properties.getEdgePort()))
                           .withEnv("DEFAULT_REGION", properties.getDefaultRegion())
                           .withEnv("HOSTNAME", properties.getHostname())
                           .withEnv("HOSTNAME_EXTERNAL", properties.getHostnameExternal())
                           .withEnv("USE_SSL", String.valueOf(properties.isUseSsl()));

        for (LocalStackContainer.Service service : properties.services) {
            localStackContainer.withServices(service);
        }
        localStackContainer.start();
        registerLocalStackEnvironment(localStackContainer, environment, properties);
        return localStackContainer;
    }

    private void registerLocalStackEnvironment(EmbeddedLocalStackContainer localStack,
                                               ConfigurableEnvironment environment,
                                               LocalStackProperties properties) {
        String host = localStack.getContainerIpAddress();

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("embedded.localstack.host", host);
        map.put("embedded.localstack.accessKey", localStack.getAccessKey());
        map.put("embedded.localstack.secretKey", localStack.getSecretKey());
        map.put("embedded.localstack.region", localStack.getRegion());
        String prefix = "embedded.localstack.";
        for (LocalStackContainer.Service service : properties.services) {
            map.put(prefix + service, localStack.getEndpointConfiguration(service)
                                                .getServiceEndpoint());
            map.put(prefix + service + ".port", localStack.getMappedPort(service.getPort()));
        }
        log.info("Started Localstack. Connection details: {}", map);

        MapPropertySource propertySource = new MapPropertySource("embeddedLocalstackInfo", map);
        environment.getPropertySources().addFirst(propertySource);
    }

    private static class EmbeddedLocalStackContainer extends LocalStackContainer {
        EmbeddedLocalStackContainer(final String dockerImageName) {
            setDockerImageName(dockerImageName);
        }
    }
}