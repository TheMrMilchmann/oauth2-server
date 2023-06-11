package com.gw2auth.oauth2.server.configuration;

import com.gw2auth.oauth2.server.configuration.properties.Gw2AuthClientProperties;
import com.gw2auth.oauth2.server.service.OAuth2ClientApiVersion;
import com.gw2auth.oauth2.server.service.account.Account;
import com.gw2auth.oauth2.server.service.account.AccountService;
import com.gw2auth.oauth2.server.service.application.Application;
import com.gw2auth.oauth2.server.service.application.ApplicationService;
import com.gw2auth.oauth2.server.service.application.client.ApplicationClientCreation;
import com.gw2auth.oauth2.server.service.application.client.ApplicationClientService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Configuration
@EnableConfigurationProperties(Gw2AuthClientProperties.class)
public class Gw2AuthClientConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(Gw2AuthClientConfiguration.class);

    private final AccountService accountService;
    private final ApplicationService applicationService;
    private final ApplicationClientService applicationClientService;
    private final JdbcOperations jdbcOperations;
    private final PasswordEncoder passwordEncoder;
    private final Gw2AuthClientProperties properties;

    @Autowired
    public Gw2AuthClientConfiguration(AccountService accountService,
                                      ApplicationService applicationService,
                                      ApplicationClientService applicationClientService,
                                      JdbcOperations jdbcOperations,
                                      PasswordEncoder passwordEncoder,
                                      Gw2AuthClientProperties properties) {

        this.accountService = accountService;
        this.applicationService = applicationService;
        this.applicationClientService = applicationClientService;
        this.jdbcOperations = jdbcOperations;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        for (Gw2AuthClientProperties.Registration registrationConfig : this.properties.getRegistration()) {
            final UUID clientId = UUID.fromString(registrationConfig.getClientId());

            if (this.applicationClientService.getApplicationClients(Set.of(clientId)).isEmpty()) {
                final List<Gw2AuthClientProperties.Account> accountsConfig = this.properties.getAccount().get(registrationConfig.getAccount());
                Account account = null;

                for (Gw2AuthClientProperties.Account accountConfig : accountsConfig) {
                    if (account == null) {
                        account = this.accountService.getOrCreateAccount(accountConfig.getIssuer(), accountConfig.getIdAtIssuer());
                    } else {
                        account = this.accountService.addAccountFederationOrReturnExisting(account.id(), accountConfig.getIssuer(), accountConfig.getIdAtIssuer());
                    }
                }

                final UUID accountId = Objects.requireNonNull(account).id();
                final Application application = this.applicationService.createApplication(accountId, registrationConfig.getDisplayName());
                final ApplicationClientCreation applicationClientCreation = this.applicationClientService.createApplicationClient(
                        accountId,
                        application.id(),
                        registrationConfig.getDisplayName(),
                        registrationConfig.getAuthorizationGrantTypes(),
                        registrationConfig.getRedirectUris(),
                        OAuth2ClientApiVersion.fromValueRequired(registrationConfig.getClientApiVersion())
                );

                this.jdbcOperations.update(
                        "UPDATE application_clients SET id = ?, client_secret = ? WHERE id = ?",
                        clientId,
                        this.passwordEncoder.encode(registrationConfig.getClientSecret()),
                        applicationClientCreation.client().id()
                );

                LOG.debug("created gw2auth client with client-id={} from configuration", registrationConfig.getClientId());
            } else {
                LOG.debug("gw2auth client with client-id={} already exists", registrationConfig.getClientId());
            }
        }
    }
}
