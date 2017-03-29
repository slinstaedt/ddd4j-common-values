package org.ddd4j.infrastructure.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.XADataSource;

import org.ddd4j.Require;

public interface ConnectionFactory {

	class ConnectionPoolDataSourceConnectionFactory implements ConnectionFactory {

		private final ConnectionPoolDataSource source;

		public ConnectionPoolDataSourceConnectionFactory(ConnectionPoolDataSource source) {
			this.source = Require.nonNull(source);
		}

		@Override
		public Connection createConnection() throws SQLException {
			return source.getPooledConnection().getConnection();
		}
	}

	class DataSourceConnectionFactory implements ConnectionFactory {

		private final DataSource source;

		public DataSourceConnectionFactory(DataSource source) {
			this.source = Require.nonNull(source);
		}

		@Override
		public Connection createConnection() throws SQLException {
			return source.getConnection();
		}
	}

	class XADataSourceConnectionFactory implements ConnectionFactory {

		private final XADataSource source;

		public XADataSourceConnectionFactory(XADataSource source) {
			this.source = Require.nonNull(source);
		}

		@Override
		public Connection createConnection() throws SQLException {
			return source.getXAConnection().getConnection();
		}
	}

	Connection createConnection() throws SQLException;
}
