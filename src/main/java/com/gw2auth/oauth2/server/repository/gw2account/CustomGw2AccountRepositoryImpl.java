package com.gw2auth.oauth2.server.repository.gw2account;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class CustomGw2AccountRepositoryImpl implements CustomGw2AccountRepository {

    private static final String QUERY = """
    UPDATE gw2_accounts
    SET gw2_account_name = :gw2_account_name
    WHERE account_id = :account_id
    AND gw2_account_id = :gw2_account_id
    """;

    private final NamedParameterJdbcOperations namedParameterJdbcOperations;

    @Autowired
    public CustomGw2AccountRepositoryImpl(NamedParameterJdbcOperations namedParameterJdbcOperations) {
        this.namedParameterJdbcOperations = namedParameterJdbcOperations;
    }

    @Transactional
    @Override
    public void updateGw2AccountNames(Collection<Gw2AccountNameUpdateEntity> updates) {
        final List<SqlParameterSource> sqlParameterSources = new ArrayList<>(updates.size());

        for (Gw2AccountNameUpdateEntity entity : updates) {
            final SqlParameterSource sqlParameterSource = new MapSqlParameterSource(Map.of(
                    "account_id", entity.accountId(),
                    "gw2_account_id", entity.gw2AccountId(),
                    "gw2_account_name", entity.gw2AccountName()
            ));

            sqlParameterSources.add(sqlParameterSource);
        }

        this.namedParameterJdbcOperations.batchUpdate(QUERY, sqlParameterSources.toArray(SqlParameterSource[]::new));
    }
}
