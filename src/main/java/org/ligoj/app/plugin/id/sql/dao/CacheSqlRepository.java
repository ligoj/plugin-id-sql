/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.dao;

import java.util.Map;
import java.util.Optional;

import javax.cache.annotation.CacheResult;

import org.ligoj.app.iam.ResourceOrg;
import org.ligoj.app.plugin.id.dao.AbstractMemCacheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SQL in memory cache with JPA back-end cache.
 */
@Component
public class CacheSqlRepository extends AbstractMemCacheRepository {

	@Autowired
	protected CacheSqlRepository self = this;

	/**
	 * Reset the database cache with the SQL data. Note there is no synchronization for this method. Initial first
	 * concurrent calls may note involve the cache.
	 *
	 * @return The cached SQL data..
	 */
	@Override
	public Map<CacheDataType, Map<String, ? extends ResourceOrg>> getData() {
		self.ensureCachedData();
		return Optional.ofNullable(data).orElseGet(this::refreshData);
	}

	/**
	 * Ensure the fresh data computed when there is no cached SQL data.
	 *
	 * @return <code>true</code>, required by JSR-107.
	 */
	@CacheResult(cacheName = "id-sql-data")
	public boolean ensureCachedData() {
		refreshData();
		return true;
	}
}
