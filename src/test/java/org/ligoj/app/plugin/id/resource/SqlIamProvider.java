/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.resource;

import java.util.Optional;

import javax.cache.annotation.CacheResult;

import org.ligoj.app.iam.IamConfiguration;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.plugin.id.sql.resource.SqlPluginResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

/**
 * SQL IAM provider.
 */
public class SqlIamProvider implements IamProvider {

	@Autowired
	protected SqlPluginResource resource;

	private IamConfiguration iamConfiguration;

	@Autowired
	private SqlIamProvider self;

	@Override
	public Authentication authenticate(final Authentication authentication) {
		// Primary authentication
		return resource.authenticate(authentication, "service:id:sql:local", true);
	}

	@Override
	public IamConfiguration getConfiguration() {
		self.ensureCachedConfiguration();
		return Optional.ofNullable(iamConfiguration).orElseGet(this::refreshConfiguration);
	}

	@CacheResult(cacheName = "iam-sql-configuration")
	public boolean ensureCachedConfiguration() {
		refreshConfiguration();
		return true;
	}

	private IamConfiguration refreshConfiguration() {
		this.iamConfiguration = resource.getConfiguration("service:id:sql:local");
		return this.iamConfiguration;
	}

}
