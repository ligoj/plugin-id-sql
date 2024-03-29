/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.resource;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.dao.ParameterRepository;
import org.ligoj.app.dao.ParameterValueRepository;
import org.ligoj.app.dao.ProjectRepository;
import org.ligoj.app.iam.model.*;
import org.ligoj.app.model.*;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.app.plugin.id.resource.AbstractPluginIdTest;
import org.ligoj.app.plugin.id.resource.IdentityResource;
import org.ligoj.app.plugin.id.resource.UserOrgResource;
import org.ligoj.app.plugin.id.sql.dao.CacheSqlRepository;
import org.ligoj.app.plugin.id.sql.model.UserSqlCredential;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.node.ParameterValueResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Test class of {@link SqlPluginResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public abstract class AbstractSqlPluginResourceTest extends AbstractPluginIdTest {
	@Autowired
	protected SqlPluginResource resource;

	@Autowired
	protected ParameterValueResource pvResource;

	@Autowired
	protected ParameterRepository parameterRepository;

	@Autowired
	protected ParameterValueRepository parameterValueRepository;

	@Autowired
	protected NodeRepository nodeRepository;

	@Autowired
	protected UserOrgResource userResource;

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	protected ProjectRepository projectRepository;

	@Autowired
	protected ServicePluginLocator servicePluginLocator;

	@Autowired
	protected CacheSqlRepository cache;

	protected int subscription;

	@BeforeEach
	protected void prepareData() throws IOException {
		persistEntities("csv",
				new Class<?>[]{DelegateOrg.class, ContainerScope.class, CacheCompany.class, CacheUser.class,
						CacheGroup.class, CacheMembership.class, Project.class, Node.class, Parameter.class,
						Subscription.class, ParameterValue.class, CacheProjectGroup.class, UserSqlCredential.class},
				StandardCharsets.UTF_8);
		cacheManager.getCache("container-scopes").clear();
		cacheManager.getCache("id-configuration").clear();
		cacheManager.getCache("id-sql-data").clear();

		// Force the cache to be created
		getUser().findAll();

		// Only with Spring context
		this.subscription = getSubscription("Jupiter", IdentityResource.SERVICE_KEY);

		// Coverage only
		Assertions.assertEquals("service:id:sql", resource.getKey());
	}

	/**
	 * Create a group in an existing OU "sea". Most Simple case. Group matches exactly to the pkey of the project.
	 *
	 * @param groupAndProject The group identifier.
	 * @return the created subscription.
	 */
	protected Subscription create(final String groupAndProject) {
		// Preconditions
		Assertions.assertNull(getGroup().findById(groupAndProject));

		// Attach the new group
		final var subscription = em.find(Subscription.class, this.subscription);
		final var subscription2 = new Subscription();
		final var newProject = newProject(groupAndProject);
		subscription2.setProject(newProject);
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, groupAndProject);
		setOu(subscription2, "sea");

		basicCreate(subscription2);

		// Checks
		final var groupSql = getGroup().findById(groupAndProject);
		Assertions.assertNotNull(groupSql);
		Assertions.assertEquals(groupAndProject, groupSql.getName());
		Assertions.assertEquals(groupAndProject, groupSql.getId());
		Assertions.assertEquals("cn=" + groupAndProject + ",ou=sea,ou=project,dc=sample,dc=com", groupSql.getDn());

		return subscription2;
	}

	/**
	 * Create a new project
	 *
	 * @param pkey The project key.
	 * @return The resolved project.
	 */
	protected Project newProject(final String pkey) {
		final var project = new Project();
		project.setPkey(pkey);
		project.setName("ANY - " + pkey);
		project.setTeamLeader(DEFAULT_USER);
		em.persist(project);
		return project;
	}

	protected void setGroup(final Subscription subscription, final String group) {
		setData(subscription, IdentityResource.PARAMETER_GROUP, group);
	}

	protected void setData(final Subscription subscription, final String parameter, String data) {
		final var groupParameter = parameterRepository.findOneExpected(parameter);
		var value = parameterValueRepository
				.findAllBy("subscription.id", subscription.isNew() ? 0 : subscription.getId()).stream()
				.filter(v -> v.getParameter().getId().equals(parameter)).findFirst().orElseGet(() -> {
					final ParameterValue pv = new ParameterValue();
					pv.setParameter(groupParameter);
					pv.setSubscription(subscription);
					pv.setData(data);
					return pv;
				});
		value.setData(data);
		if (value.isNew()) {
			em.persist(value);
		}
		em.flush();
	}

	protected void setOu(final Subscription subscription, final String ou) {
		setData(subscription, IdentityResource.PARAMETER_OU, ou);
	}

	protected void setParentGroup(final Subscription subscription, final String parentGroup) {
		setData(subscription, IdentityResource.PARAMETER_PARENT_GROUP, parentGroup);
	}

	protected void basicCreate(final Subscription subscription2) {
		initSpringSecurityContext(DEFAULT_USER);
		resource.create(subscription2.getId());
		em.flush();
		em.clear();
	}

	protected void basicLink(final Subscription subscription2) {
		initSpringSecurityContext(DEFAULT_USER);
		resource.link(subscription2.getId());
		em.flush();
		em.clear();
	}

	/**
	 * Create a group inside another group/ Both are created inside "sea" OU.
	 *
	 * @param newProject  The source project.
	 * @param parentGroup The related parent group.
	 * @param subGroup    The subgroup.
	 * @return the created {@link Subscription}.
	 */
	protected Subscription createSubGroup(final Project newProject, final String parentGroup, final String subGroup) {

		// Preconditions
		Assertions.assertNotNull(getGroup().findById(parentGroup));
		Assertions.assertNull(getGroup().findById(subGroup));

		// Attach the new group
		final var subscription = em.find(Subscription.class, this.subscription);
		final var subscription2 = new Subscription();
		subscription2.setProject(newProject);
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, subGroup);
		setParentGroup(subscription2, parentGroup);
		setOu(subscription2, "sea");

		basicCreate(subscription2);

		// Checks
		final var groupSql = getGroup().findById(subGroup);
		Assertions.assertNotNull(groupSql);
		Assertions.assertEquals(subGroup, groupSql.getName());
		Assertions.assertEquals("cn=" + subGroup + ",cn=" + parentGroup + ",ou=sea,ou=project,dc=sample,dc=com",
				groupSql.getDn());
		Assertions.assertEquals(subGroup, groupSql.getId());
		Assertions.assertEquals(parentGroup, groupSql.getParent());
		final var groupSqlParent = getGroup().findById(parentGroup);
		Assertions.assertIterableEquals(List.of(subGroup), groupSqlParent.getSubGroups());
		return subscription2;
	}

	protected void persistParameter(final Node node, final String id, final String value) {
		final var parameterValue = new ParameterValue();
		parameterValue.setNode(node);
		parameterValue.setParameter(parameterRepository.findOneExpected(id));
		parameterValue.setData(value);
		em.persist(parameterValue);
	}
}
