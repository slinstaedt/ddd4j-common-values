package org.ddd4j.infrastructure.jdbc;

import javax.sql.DataSource;

public interface DataSourceContext {

	DataSource lookupDataSource();
}
