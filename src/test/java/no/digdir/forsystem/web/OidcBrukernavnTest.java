package no.digdir.forsystem.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Users signed in through Entra ID are recorded by a username colleagues recognise, not the opaque "sub"
 * (specs/010 FR-011). The prod profile needs OIDC endpoints, so the setting is checked in the file itself.
 */
class OidcBrukernavnTest {

    @Test
    void prodRecordsThePreferredUsername() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yaml"));
        Properties prod = yaml.getObject();
        assertThat(prod).containsEntry(
                "spring.security.oauth2.client.provider.entra.user-name-attribute", "preferred_username");
    }
}
