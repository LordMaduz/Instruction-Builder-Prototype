package com.ruchira.murex.repository;

import com.ruchira.murex.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
@Slf4j
public class GenericJdbcDataRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * Execute dynamic SQL for batch of DTOs.
     *
     * @param sql  string contains the batch script
     * @param dtos list of DTO objects
     */
    public <T> void executeBatch(String sql, List<T> dtos) {
        try {

            // 4. Map DTO list to parameters
            SqlParameterSource[] batchParams = SqlParameterSourceUtils.createBatch(dtos.toArray());

            // 5. Execute batch
            int[] result = jdbcTemplate.batchUpdate(sql, batchParams);
            log.info("Successfully executed batch SQL from sql {} for {} records; update counts={}",
                    sql, dtos.size(), result.length);

        } catch (Exception e) {
            final String message = String.format("Batch execution failed for %d records from sql template %s", dtos.size(), sql);
            log.error(message, e);
            throw new BusinessException(message, e);
        }
    }

    /**
     * Executes an INSERT statement using a SQL template and named parameters, and returns the auto-generated ID.
     *
     * <p>This method uses the provided FreeMarker template file to construct the SQL statement, binds the
     * parameters from the given {@code params} map to the named placeholders in the SQL, executes the insert,
     * and retrieves the generated key from the specified ID column.</p>
     *
     * @param sql          string containing the INSERT SQL statement
     * @param params       a map of parameter names to values, used to bind values to the named parameters in the SQL
     * @param idColumnName the name of the auto-generated ID column to be returned
     * @return the generated ID as a {@link Long}, or {@code null} if no ID was generated
     */
    public Long insertAndReturnId(String sql, Map<String, Object> params, final String idColumnName) {
        try {

            KeyHolder keyHolder = new GeneratedKeyHolder();

            jdbcTemplate.update(sql,
                    new MapSqlParameterSource(params),
                    keyHolder,
                    new String[]{idColumnName});

            // "id" is the auto-generated column name
            return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;

        } catch (Exception e) {
            final String message = String.format("insert failed for record; Error executing dynamic SQL %s with params %s", sql, params);
            log.error(message, e);
            throw new BusinessException(message, e);
        }
    }

    /**
     * Executes a database query defined in a FreeMarker (FTL) template file and maps the results
     * into a list of objects using the provided {@link RowMapper}.
     * <p>
     *
     * @param <T>       the type of objects that the result rows will be mapped to
     * @param sql       query definition
     * @param rowMapper the mapper used to convert each row of the result set into an object of type {@code T}
     * @return a list of mapped objects resulting from the executed query
     */
    public <T> List<T> fetchData(final String sql, final RowMapper<T> rowMapper) {
        try {

            // Execute query with row mapper
            return jdbcTemplate.query(sql, rowMapper);
        } catch (Exception e) {
            final String message = String.format("fetch data failed for dynamic SQL %s with rowMapper %s", sql, rowMapper);
            log.error(message, e);
            throw new BusinessException(message, e);
        }
    }
}
