/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.resource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityNotFoundException;
import javax.ws.rs.NotAuthorizedException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.model.CacheCompany;
import org.ligoj.app.iam.model.CacheGroup;
import org.ligoj.app.iam.model.CacheMembership;
import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.id.resource.IdentityResource;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Test class of {@link SqlPluginResource}
 */
class SqlPluginResourceTest extends AbstractSqlPluginResourceTest {

	@Test
	void deleteNoMoreGroup() {
		final Subscription subscription = new Subscription();
		subscription.setProject(projectRepository.findByName("gStack"));
		subscription.setNode(nodeRepository.findOneExpected("service:id:sql:local"));
		em.persist(subscription);

		// Attach the wrong group
		setGroup(subscription, "any");

		initSpringSecurityContext("fdaugan");
		final Map<String, String> parameters = subscriptionResource.getParametersNoCheck(subscription.getId());
		Assertions.assertFalse(resource.checkSubscriptionStatus(parameters).getStatus().isUp());

		resource.delete(subscription.getId(), true);
		em.flush();
		em.clear();
		Assertions.assertFalse(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
		subscriptionResource.getParametersNoCheck(subscription.getId()).isEmpty();
	}

	/**
	 * The unsubscription without deletion has no effect
	 */
	@Test
	void delete() {
		initSpringSecurityContext("fdaugan");
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);
		Assertions.assertTrue(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
		resource.delete(subscription, false);
		em.flush();
		em.clear();
		Assertions.assertTrue(resource.checkSubscriptionStatus(parameters).getStatus().isUp());
	}

	@Test
	void getVersion() {
		final String version = resource.getVersion(null);
		Assertions.assertEquals("1", version);
	}

	@Test
	void getLastVersion() {
		final String lastVersion = resource.getLastVersion();
		Assertions.assertEquals("1", lastVersion);
	}

	@Test
	void validateGroupNotExists() {
		final Map<String, String> parameters = pvResource.getNodeParameters("service:id:sql:local");
		parameters.put(IdentityResource.PARAMETER_GROUP, "broken");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.validateGroup(parameters);
		}), IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void validateGroupNotProject() {
		final Map<String, String> parameters = pvResource.getNodeParameters("service:id:sql:local");
		parameters.put(IdentityResource.PARAMETER_GROUP, "vigireport");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.validateGroup(parameters);
		}), IdentityResource.PARAMETER_GROUP, "group-type");
	}

	@Test
	void validateGroup() {
		final Map<String, String> parameters = pvResource.getNodeParameters("service:id:sql:local");
		parameters.put(IdentityResource.PARAMETER_GROUP, "ligoj-gstack");

		final INamableBean<String> group = resource.validateGroup(parameters);
		Assertions.assertNotNull(group);
		Assertions.assertEquals("ligoj-gstack", group.getId());
		Assertions.assertEquals("ligoj-gStack", group.getName());
	}

	/**
	 * Create a group in a existing OU. Most Simple case. Group matches exactly to the pkey of the project.
	 */
	@Test
	void create() {
		resource.delete(create("sea-new-project").getId(), true);
	}

	/**
	 * Create a group with the same name.
	 */
	@Test
	void createAlreadyExist() {
		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-octopus"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-octopus");
		setOu(subscription2, "sea");

		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			basicCreate(subscription2);
		}), IdentityResource.PARAMETER_GROUP, "already-exist");
	}

	/**
	 * Create a group inside an existing group. Parent group matches exactly to the pkey of the project.
	 */
	@Test
	void createSubGroup() {
		// Create the parent group
		final Project newProject = create("sea-parent").getProject();
		createSubGroup(newProject, "sea-parent", "sea-parent-client");
	}

	/**
	 * Create a group inside an existing group without reusing the name of the parent group.
	 */
	@Test
	void createNotCompliantGroupForParent() {
		// Create the parent group
		final Project newProject = create("sea-parent2").getProject();
		createSubGroup(newProject, "sea-parent2", "sea-parent2-client");

		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			createSubGroup(newProject, "sea-parent2-client", "sea-parent2-dev");
		}), IdentityResource.PARAMETER_GROUP, "pattern");
	}

	/**
	 * Create a group for an existing project, but without reusing the pkey of this project.
	 */
	@Test
	void createNotCompliantGroupForProject() {
		// Preconditions
		Assertions.assertNotNull(getGroup().findById("sea-octopus"));
		Assertions.assertNull(getGroup().findById("sea-octopusZZ"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-octopus"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-octopusZZ");
		setOu(subscription2, "sea");

		// Invoke link for an already linked entity, since for now
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			basicCreate(subscription2);
		}), IdentityResource.PARAMETER_GROUP, "pattern");
	}

	/**
	 * Create a group for an existing project, reusing the pkey of this project and without suffix.
	 */
	@Test
	void createNotCompliantGroupForProject2() {

		// Preconditions
		Assertions.assertNotNull(getGroup().findById("sea-octopus"));
		Assertions.assertNull(getGroup().findById("sea-octopus-"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-octopus"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-octopus-");
		setOu(subscription2, "sea");

		// Invoke link for an already linked entity, since for now
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			basicCreate(subscription2);
		}), IdentityResource.PARAMETER_GROUP, "pattern");
	}

	/**
	 * Create a group for an existing project, perfect match with the pkey, but without reusing the OU of this project.
	 */
	@Test
	void createNotCompliantGroupForOu() {
		// Preconditions
		Assertions.assertNull(getGroup().findById("sea-invalid-ou"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-invalid-ou"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-invalid-ou");
		setOu(subscription2, "ligoj");

		// Invoke link for an already linked entity, since for now
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			basicCreate(subscription2);
		}), IdentityResource.PARAMETER_GROUP, "pattern");
	}

	/**
	 * Create a group inside an existing group without reusing the name of the parent.
	 */
	@Test
	void createNotExistingParentGroup() {
		// Preconditions
		Assertions.assertNotNull(getGroup().findById("sea-octopus"));
		Assertions.assertNull(getGroup().findById("sea-octopus-client"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("sea-orpahn"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "sea-orpahn-any");
		setParentGroup(subscription2, "sea-orpahn");
		setOu(subscription2, "sea");

		// Invoke link for an already linked entity, since for now
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			basicCreate(subscription2);
		}), IdentityResource.PARAMETER_PARENT_GROUP, "unknown-id");
	}

	/**
	 * Create a group inside a new organizational unit. Not an error, lazy creation. Exact match for group and pkey.
	 */
	@Test
	void createOuNotExists() {

		// Preconditions
		Assertions.assertNull(getGroup().findById("some-new-project"));

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(newProject("some-new-project"));
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);

		// Add parameters
		setGroup(subscription2, "some-new-project");
		setOu(subscription2, "some");

		basicCreate(subscription2);

		// Checks
		final GroupOrg groupSql = getGroup().findById("some-new-project");
		Assertions.assertNotNull(groupSql);
		Assertions.assertEquals("some-new-project", groupSql.getName());
		Assertions.assertEquals("cn=some-new-project,ou=some,ou=project,dc=sample,dc=com", groupSql.getDn());
		Assertions.assertEquals("some-new-project", groupSql.getId());

		resource.delete(subscription2.getId(), true);
	}

	@Test
	void link() {

		// Attach the new group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final Subscription subscription2 = new Subscription();
		subscription2.setProject(subscription.getProject());
		subscription2.setNode(subscription.getNode());
		em.persist(subscription2);
		em.flush();
		em.clear();

		// Add parameters
		setGroup(subscription2, "sea-octopus");

		final CacheCompany company = new CacheCompany();
		company.setDescription("ou=c,dc=sample,dc=com");
		company.setId("c");
		company.setName("C");
		em.persist(company);

		final CacheUser user = new CacheUser();
		user.setId(DEFAULT_USER);
		user.setCompany(company);
		em.persist(user);

		final CacheGroup group = new CacheGroup();
		group.setDescription("cn=g,dc=sample,dc=com");
		group.setId("ligoj-gstack");
		group.setName("ligoj-gstack");
		// em.persist(group);

		final CacheMembership membership = new CacheMembership();
		membership.setUser(user);
		membership.setGroup(group);
		em.persist(membership);

		// Invoke link for an already linkd entity, since for now
		basicLink(subscription2);
		// Nothing to validate for now...
		resource.delete(subscription2.getId(), false);
	}

	@Test
	void linkNotVisibleProject() {

		// Invoke link for an already created entity, since for now
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			resource.link(this.subscription);
		});
	}

	/**
	 * Visible project, but not visible target group
	 */
	@Test
	void linkNotVisibleGroup() {
		// Attach the wrong group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		setGroup(subscription, "sea-octopus");

		// Invoke link for an already created entity, since for now
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.link(this.subscription);
		}), IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOWN_ID);
	}

	/**
	 * Visible project, but target group does not exist
	 */
	@Test
	void linkNotExistingGroup() {
		// Attach the wrong group
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		setGroup(subscription, "any-g");

		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.link(this.subscription);
		}), IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void checkStatus() {
		Assertions.assertTrue(
				resource.checkStatus("service:id:sql:local", subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	void checkSubscriptionStatus() {
		Assertions.assertTrue(resource.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription))
				.getStatus().isUp());
	}

	@Test
	void findGroupsByNameNoRight() {
		initSpringSecurityContext("any");
		final List<INamableBean<String>> jobs = resource.findGroupsByName("StAck");
		Assertions.assertEquals(0, jobs.size());
	}

	@Test
	void findGroupsByName() {
		final List<INamableBean<String>> jobs = resource.findGroupsByName("StAck");
		Assertions.assertTrue(jobs.size() >= 1);
		Assertions.assertEquals("ligoj-gStack", jobs.get(0).getName());
		Assertions.assertEquals("ligoj-gstack", jobs.get(0).getId());
	}

	@Test
	void findGroupsByNameNoScope() {
		final List<INamableBean<String>> jobs = resource.findGroupsByName("StAck");
		Assertions.assertTrue(jobs.size() >= 1);
		Assertions.assertEquals("ligoj-gStack", jobs.get(0).getName());
		Assertions.assertEquals("ligoj-gstack", jobs.get(0).getId());
	}

	@Test
	void acceptNoParameters() {
		Assertions.assertFalse(resource.accept(null, "service:any"));
	}

	@Test
	void acceptNotMatch() {
		final Node sql = new Node();
		sql.setId("service:id:sql:test");
		sql.setRefined(nodeRepository.findOneExpected("service:id:sql"));
		sql.setName("ID SQL Test");
		nodeRepository.saveAndFlush(sql);
		final ParameterValue parameterValue = new ParameterValue();
		parameterValue.setNode(sql);
		parameterValue.setParameter(parameterRepository.findOneExpected("service:id:uid-pattern"));
		parameterValue.setData("-nomatch-");
		em.persist(parameterValue);
		Assertions.assertFalse(
				resource.accept(new UsernamePasswordAuthenticationToken("some", ""), "service:id:sql:test"));
	}

	@Test
	void accept() {
		final Node sql = new Node();
		sql.setId("service:id:sql:test");
		sql.setRefined(nodeRepository.findOneExpected("service:id:sql"));
		sql.setName("ID SQL Test");
		nodeRepository.saveAndFlush(sql);
		persistParameter(sql, IdentityResource.PARAMETER_UID_PATTERN, "some-.*-text");
		Assertions.assertTrue(resource.accept(new UsernamePasswordAuthenticationToken("some-awesome-text", ""),
				"service:id:sql:test"));
	}

	@Test
	void authenticatePrimary() {
		final Authentication authentication = new UsernamePasswordAuthenticationToken("jdoe4", "Azerty01");
		Assertions.assertSame(authentication, resource.authenticate(authentication, "service:id:sql:local", true));
	}

	@Test
	void authenticateFail() {
		final Authentication authentication = new UsernamePasswordAuthenticationToken("jdoe4", "any");
		Assertions.assertThrows(BadCredentialsException.class, () -> {
			resource.authenticate(authentication, "service:id:sql:local", true);
		});
	}

	@Test
	void authenticateSecondaryMock() {
		// Create a new SQL node plugged to the primary node
		final Authentication authentication = new UsernamePasswordAuthenticationToken("mmartin", "complexOne");
		final Authentication localAuthentication = resource.authenticate(authentication, "service:id:sql:secondary",
				false);
		Assertions.assertEquals("mmartin", localAuthentication.getName());
	}

	@Test
	void toApplicationUserExists() {
		// Create a new LDAP node plugged to the primary node
		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("marc.martin@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondarylogin");
		user.setCompany("ligoj");
		user.setDepartment("3890");
		user.setLocalId("8234");
		Assertions.assertEquals("mmartin", toApplicationUser(resource, user));

		final UserOrg userLdap = userResource.findByIdNoCache("mmartin");
		Assertions.assertEquals("mmartin", userLdap.getName());
		Assertions.assertEquals("Marc", userLdap.getFirstName());
		Assertions.assertEquals("Martin", userLdap.getLastName());
		Assertions.assertEquals("marc.martin@sample.com", userLdap.getMails().get(0));
	}

	@Test
	void toApplicationUserNew() {
		// Create a new LDAP node plugged to the primary node
		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("some@where.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setCompany("ligoj");
		user.setName("secondarylogin");
		Assertions.assertEquals("flast123", toApplicationUser(resource, user));

		final UserOrg userLdap = userResource.findByIdNoCache("flast123");
		Assertions.assertEquals("flast123", userLdap.getName());
		Assertions.assertEquals("First", userLdap.getFirstName());
		Assertions.assertEquals("Last123", userLdap.getLastName());
		Assertions.assertEquals("ligoj", userLdap.getCompany());
		Assertions.assertEquals("some@where.com", userLdap.getMails().get(0));
		userResource.delete("flast123");
	}

	@Test
	void toApplicationUserNewWithCollision() {
		// Create a new LDAP node plugged to the primary node
		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("some@where.com"));
		user.setFirstName("Marc");
		user.setLastName("Martin");
		user.setCompany("ligoj");
		user.setName("secondarylogin");
		Assertions.assertEquals("mmartin1", toApplicationUser(resource, user));

		final UserOrg userLdap = userResource.findByIdNoCache("mmartin1");
		Assertions.assertEquals("mmartin1", userLdap.getName());
		Assertions.assertEquals("Marc", userLdap.getFirstName());
		Assertions.assertEquals("Martin", userLdap.getLastName());
		Assertions.assertEquals("ligoj", userLdap.getCompany());
		Assertions.assertEquals("some@where.com", userLdap.getMails().get(0));
		userResource.delete("mmartin1");
	}

	@Test
	void toApplicationUserTooManyMail() {
		// Create a new LDAP node pluged to the primary node
		final UserOrg user = new UserOrg();
		user.setMails(Collections.singletonList("fabrice.daugan@sample.com"));
		user.setFirstName("First");
		user.setLastName("Last123");
		user.setName("secondarylogin");
		Assertions.assertThrows(NotAuthorizedException.class, () -> {
			toApplicationUser(resource, user);
		});
	}

	@Test
	void toLogin() {
		final UserOrg user = new UserOrg();
		user.setFirstName("First");
		user.setLastName("Last123");
		Assertions.assertEquals("flast123", toLogin(resource, user));
	}

	@Test
	void toLoginNoFirstName() {
		final UserOrg user = new UserOrg();
		user.setLastName("Last123");
		Assertions.assertThrows(NotAuthorizedException.class, () -> {
			toLogin(resource, user);
		});
	}

	@Test
	void toLoginNoLastName() {
		final UserOrg user = new UserOrg();
		user.setFirstName("First");
		Assertions.assertThrows(NotAuthorizedException.class, () -> {
			toLogin(resource, user);
		});
	}

	@Test
	void authenticateSecondaryNoMail() {
		final Authentication authentication = new UsernamePasswordAuthenticationToken("jdupont", "Azerty01");
		Assertions.assertThrows(NotAuthorizedException.class, () -> {
			resource.authenticate(authentication, "service:id:sql:secondary", false);
		});
	}

	@Test
	void authenticateSecondaryFail() {
		final Authentication authentication = new UsernamePasswordAuthenticationToken("jdoe4", "any");
		Assertions.assertThrows(BadCredentialsException.class, () -> {
			resource.authenticate(authentication, "service:id:sql:secondary", false);
		});
	}
}
