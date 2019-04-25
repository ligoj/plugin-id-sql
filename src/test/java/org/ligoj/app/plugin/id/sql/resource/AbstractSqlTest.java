/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.id.sql.dao.CompanySqlRepository;
import org.ligoj.app.plugin.id.sql.dao.GroupSqlRepository;
import org.ligoj.app.plugin.id.sql.dao.UserSqlRepository;

/**
 * Test for SQL resources.
 */
public abstract class AbstractSqlTest extends AbstractAppTest {

	/**
	 * Prepare the Spring Security in the context, not the REST one.
	 */
	@BeforeEach
	public void setUp2() throws IOException {
		persistEntities("csv", new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class },
				StandardCharsets.UTF_8.name());
	}

	/**
	 * User repository provider.
	 *
	 * @return User repository provider.
	 */
	@Override
	protected UserSqlRepository getUser() {
		return (UserSqlRepository) super.getUser();
	}

	/**
	 * Company repository provider.
	 *
	 * @return Company repository provider.
	 */
	@Override
	protected CompanySqlRepository getCompany() {
		return (CompanySqlRepository) super.getCompany();
	}

	/**
	 * Group repository provider.
	 *
	 * @return Group repository provider.
	 */
	@Override
	protected GroupSqlRepository getGroup() {
		return (GroupSqlRepository) super.getGroup();
	}

}
