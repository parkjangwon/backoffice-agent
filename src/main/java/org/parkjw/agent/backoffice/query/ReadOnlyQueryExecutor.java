package org.parkjw.agent.backoffice.query;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import javax.sql.DataSource;

import org.parkjw.agent.backoffice.config.AiQueryProperties;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReadOnlyQueryExecutor {

	private final DataSource dataSource;
	private final AiQueryProperties properties;

	public ReadOnlyQueryExecutor(DataSource dataSource, AiQueryProperties properties) {
		this.dataSource = dataSource;
		this.properties = properties;
	}

	public QueryResult execute(String sql, int limit) {
		// Double check read-only safety before execution on the database level
		SqlSafetyValidatorTestHelper.validate(sql);

		var connection = DataSourceUtils.getConnection(dataSource);
		try {
			connection.setReadOnly(true);
			try (var statement = connection.prepareStatement(sql)) {
				statement.setQueryTimeout(properties.policy().queryTimeoutSeconds());
				statement.setMaxRows(limit);
				try (var resultSet = statement.executeQuery()) {
					return readResult(sql, resultSet);
				}
			}
		}
		catch (SQLException exception) {
			throw new SqlExecutionException("Read-only query execution failed.", exception);
		}
		finally {
			DataSourceUtils.releaseConnection(connection, dataSource);
		}
	}

	private QueryResult readResult(String sql, ResultSet resultSet) throws SQLException {
		var metadata = resultSet.getMetaData();
		var columns = new ArrayList<String>();
		for (var index = 1; index <= metadata.getColumnCount(); index++) {
			columns.add(metadata.getColumnLabel(index));
		}
		var rows = new ArrayList<LinkedHashMap<String, Object>>();
		while (resultSet.next()) {
			var row = new LinkedHashMap<String, Object>();
			for (var index = 1; index <= columns.size(); index++) {
				row.put(columns.get(index - 1), resultSet.getObject(index));
			}
			rows.add(row);
		}
		return new QueryResult(sql, List.copyOf(columns), List.copyOf(rows), rows.size());
	}
}
