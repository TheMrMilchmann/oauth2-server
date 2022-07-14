package com.gw2auth.oauth2.server.service.client.consent;

import com.gw2auth.oauth2.server.repository.client.authorization.ClientAuthorizationEntity;
import com.gw2auth.oauth2.server.repository.client.authorization.ClientAuthorizationRepository;
import com.gw2auth.oauth2.server.repository.client.consent.ClientConsentEntity;
import com.gw2auth.oauth2.server.repository.client.consent.ClientConsentLogEntity;
import com.gw2auth.oauth2.server.repository.client.consent.ClientConsentLogRepository;
import com.gw2auth.oauth2.server.repository.client.consent.ClientConsentRepository;
import com.gw2auth.oauth2.server.service.client.AuthorizationCodeParamAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class ClientConsentServiceImpl implements ClientConsentService, OAuth2AuthorizationConsentService {

    private static final Logger LOG = LoggerFactory.getLogger(ClientConsentServiceImpl.class);
    private static final int MAX_LOG_COUNT = 50;

    private final ClientConsentRepository clientConsentRepository;
    private final ClientConsentLogRepository clientConsentLogRepository;
    private final ClientAuthorizationRepository clientAuthorizationRepository;
    private final AuthorizationCodeParamAccessor authorizationCodeParamAccessor;

    public ClientConsentServiceImpl(ClientConsentRepository clientConsentRepository,
                                    ClientConsentLogRepository clientConsentLogRepository,
                                    ClientAuthorizationRepository clientAuthorizationRepository,
                                    AuthorizationCodeParamAccessor authorizationCodeParamAccessor) {

        this.clientConsentRepository = clientConsentRepository;
        this.clientConsentLogRepository = clientConsentLogRepository;
        this.clientAuthorizationRepository = clientAuthorizationRepository;
        this.authorizationCodeParamAccessor = authorizationCodeParamAccessor;
    }

    @Autowired
    public ClientConsentServiceImpl(ClientConsentRepository clientConsentRepository,
                                    ClientConsentLogRepository clientConsentLogRepository,
                                    ClientAuthorizationRepository clientAuthorizationRepository) {

        this(clientConsentRepository, clientConsentLogRepository, clientAuthorizationRepository, AuthorizationCodeParamAccessor.DEFAULT);
    }

    @Override
    public List<ClientConsent> getClientConsents(UUID accountId) {
        return this.clientConsentRepository.findAllByAccountId(accountId).stream()
                .filter(ClientConsentServiceImpl::isAuthorized)
                .map(ClientConsent::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ClientConsent> getClientConsent(UUID accountId, UUID clientRegistrationId) {
        return this.clientConsentRepository.findByAccountIdAndClientRegistrationId(accountId, clientRegistrationId)
                .filter(ClientConsentServiceImpl::isAuthorized)
                .map(ClientConsent::fromEntity);
    }

    @Override
    @Transactional
    public void createEmptyClientConsentIfNotExists(UUID accountId, UUID clientRegistrationId) {
        final Optional<ClientConsentEntity> optional = this.clientConsentRepository.findByAccountIdAndClientRegistrationId(accountId, clientRegistrationId);
        if (optional.isEmpty()) {
            this.clientConsentRepository.save(createAuthorizedClientEntity(accountId, clientRegistrationId));
        }
    }

    @Override
    @Transactional
    public void deleteClientConsent(UUID accountId, UUID clientRegistrationId) {
        this.clientConsentRepository.findByAccountIdAndClientRegistrationId(accountId, clientRegistrationId).ifPresent(this::deleteInternal);
    }

    @Transactional
    protected void deleteInternal(ClientConsentEntity entity) {
        // not actually deleting, since we want to keep the client specific account sub
        this.clientConsentRepository.deleteByAccountIdAndClientRegistrationId(entity.accountId(), entity.clientRegistrationId());
        this.clientConsentRepository.save(new ClientConsentEntity(entity.accountId(), entity.clientRegistrationId(), entity.accountSub(), Set.of()));
    }

    @Override
    public LoggingContext log(UUID accountId, UUID clientRegistrationId, LogType logType) {
        return new LoggingContextImpl(accountId, clientRegistrationId, logType);
    }

    // region OAuth2AuthorizationConsentService
    @Override
    @Transactional
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        if (!authorizationConsent.getScopes().containsAll(this.authorizationCodeParamAccessor.getRequestedScopes())) {
            throw this.authorizationCodeParamAccessor.error(new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED));
        }

        final UUID accountId = UUID.fromString(authorizationConsent.getPrincipalName());
        final UUID clientRegistrationId = UUID.fromString(authorizationConsent.getRegisteredClientId());

        try (LoggingContext log = log(accountId, clientRegistrationId, LogType.CONSENT)) {
            ClientConsentEntity clientConsentEntity = this.clientConsentRepository.findByAccountIdAndClientRegistrationId(accountId, clientRegistrationId)
                    .orElseGet(() -> createAuthorizedClientEntity(accountId, clientRegistrationId))
                    .withAdditionalScopes(authorizationConsent.getScopes());

            clientConsentEntity = this.clientConsentRepository.save(clientConsentEntity);
            log.log("Updated consented oauth2-scopes to [%s]", String.join(", ", clientConsentEntity.authorizedScopes()));
        }
    }

    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        final UUID accountId = UUID.fromString(authorizationConsent.getPrincipalName());
        final UUID registeredClientId = UUID.fromString(authorizationConsent.getRegisteredClientId());

        deleteClientConsent(accountId, registeredClientId);
    }

    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        final UUID accountId = UUID.fromString(principalName);
        final UUID clientRegistrationId = UUID.fromString(registeredClientId);

        if (this.authorizationCodeParamAccessor.isInCodeRequest() && !this.authorizationCodeParamAccessor.isInConsentContext()) {
            if (this.authorizationCodeParamAccessor.getAdditionalParameters().filter((e) -> e.getKey().equals("prompt")).map(Map.Entry::getValue).anyMatch(Predicate.isEqual("consent"))) {
                return null;
            }

            final String copyGw2AccountIdsFromClientAuthorizationId = this.clientAuthorizationRepository.findLatestByAccountIdAndClientRegistrationIdAndHavingScopes(accountId, clientRegistrationId, this.authorizationCodeParamAccessor.getRequestedScopes())
                    .map(ClientAuthorizationEntity::id)
                    .orElse(null);

            if (copyGw2AccountIdsFromClientAuthorizationId == null) {
                return null;
            }

            this.authorizationCodeParamAccessor.putValue("COPY_FROM_CLIENT_AUTHORIZATION_ID", copyGw2AccountIdsFromClientAuthorizationId);
        }

        return this.clientConsentRepository.findByAccountIdAndClientRegistrationId(accountId, clientRegistrationId)
                .filter(ClientConsentServiceImpl::isAuthorized)
                .map((entity) -> {
                    final OAuth2AuthorizationConsent.Builder builder = OAuth2AuthorizationConsent.withId(entity.clientRegistrationId().toString(), entity.accountId().toString());
                    entity.authorizedScopes().forEach(builder::scope);

                    return builder.build();
                })
                .orElse(null);
    }
    // endregion

    private ClientConsentEntity createAuthorizedClientEntity(UUID accountId, UUID registeredClientId) {
        return new ClientConsentEntity(accountId, registeredClientId, UUID.randomUUID(), Set.of());
    }

    private static boolean isAuthorized(ClientConsentEntity clientConsentEntity) {
        return clientConsentEntity.authorizedScopes() != null && !clientConsentEntity.authorizedScopes().isEmpty();
    }

    private final class LoggingContextImpl implements LoggingContext {

        private final UUID accountId;
        private final UUID clientRegistrationId;
        private final LogType logType;
        private final Instant timestamp;
        private final List<String> messages;
        private final AtomicBoolean isValid;

        private LoggingContextImpl(UUID accountId, UUID clientRegistrationId, LogType logType) {
            this.accountId = accountId;
            this.clientRegistrationId = clientRegistrationId;
            this.logType = logType;
            this.timestamp = Instant.now();
            this.messages = new LinkedList<>();
            this.isValid = new AtomicBoolean(true);
        }

        @Override
        public void log(String message) {
            // not truly thread-safe
            // this is an optimistic variant. I'm in control of all users of these methods
            if (this.isValid.get()) {
                this.messages.add(message);
            }
        }

        @Override
        public void close() {
            if (this.isValid.compareAndSet(true, false)) {
                try {
                    /*
                    Preparation for CockroachDB
                    Caused by: org.postgresql.util.PSQLException: ERROR: restart transaction: TransactionRetryWithProtoRefreshError: ReadWithinUncertaintyIntervalError: read at time 1657844930.854217895,0 encountered previous write with future timestamp 1657844930.854217895,1 within uncertainty interval [...]
                    Hinweis: See: https://www.cockroachlabs.com/docs/v22.1/transaction-retry-error-reference.html#readwithinuncertaintyinterval
                     */
                    ClientConsentServiceImpl.this.clientConsentLogRepository.deleteAllByAccountIdAndClientRegistrationIdExceptLatestN(this.accountId, this.clientRegistrationId, MAX_LOG_COUNT - 1);
                } catch (DataAccessException e) {
                    LOG.info("failed to delete old logs", e);
                }

                ClientConsentServiceImpl.this.clientConsentLogRepository.save(new ClientConsentLogEntity(UUID.randomUUID(), this.accountId, this.clientRegistrationId, this.timestamp, this.logType.name(), this.messages));
                this.messages.clear();
            }
        }
    }
}
