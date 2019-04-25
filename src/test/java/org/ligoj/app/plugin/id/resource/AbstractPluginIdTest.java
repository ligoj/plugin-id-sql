/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.resource;

import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.plugin.id.sql.resource.SqlPluginResource;

/**
 * Test placed in a package level visible for plugin-id.
 */
public abstract class AbstractPluginIdTest extends AbstractAppTest {

	protected String toApplicationUser(SqlPluginResource resource, final UserOrg user) {
		return resource.toApplicationUser(user);
	}
	
	protected String toLogin(SqlPluginResource resource, final UserOrg user) {
		return resource.toLogin(user);
	}
}
