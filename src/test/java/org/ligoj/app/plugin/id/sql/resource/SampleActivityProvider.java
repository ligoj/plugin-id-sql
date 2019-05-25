/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.resource;

import java.util.Collection;
import java.util.Map;

import org.ligoj.app.api.ServicePlugin;
import org.ligoj.app.iam.Activity;
import org.ligoj.app.resource.ActivitiesProvider;

/**
 * Test provider
 */
public class SampleActivityProvider implements ActivitiesProvider, ServicePlugin {

	@Override
	public void delete(final int subscription, final boolean deleteRemoteData) {
		// Mock
	}

	@Override
	public void create(final int subscription) {
		// Mock
	}

	@Override
	public void link(final int subscription) {
		// Mock
	}

	@Override
	public String getKey() {
		// Mock
		return "service:bt:jira:6";
	}

	@Override
	public Map<String, Activity> getActivities(final int subscription, final Collection<String> users) {
		// Mock
		return null;
	}

}
