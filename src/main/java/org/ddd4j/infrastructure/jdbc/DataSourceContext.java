package org.ddd4j.infrastructure.jdbc;

import javax.sql.DataSource;

import org.ddd4j.spi.Service;

public interface DataSourceContext extends Service<DataSourceContext> {

	DataSource lookupDataSource();
}
