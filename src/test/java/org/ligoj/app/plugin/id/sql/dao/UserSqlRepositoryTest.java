/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.model.CacheCompany;
import org.ligoj.app.iam.model.CacheGroup;
import org.ligoj.app.iam.model.CacheMembership;
import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.model.CacheProjectGroup;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.app.plugin.id.sql.model.UserSqlCredential;
import org.ligoj.bootstrap.AbstractJpaTest;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.resource.TechnicalException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link UserSqlRepository}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class UserSqlRepositoryTest extends AbstractJpaTest {

	private UserSqlRepository repository;

	@Autowired
	private UserSqlCredentialRepository credentialRepository;

	@BeforeEach
	public void init2() throws IOException {
		persistEntities("csv",
				new Class[] { DelegateOrg.class, ContainerScope.class, CacheCompany.class, CacheUser.class,
						CacheGroup.class, CacheMembership.class, Project.class, Node.class, Parameter.class,
						Subscription.class, ParameterValue.class, CacheProjectGroup.class, UserSqlCredential.class },
				StandardCharsets.UTF_8.name());
		repository = new UserSqlRepository();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(repository);
		cacheManager.getCache("id-sql-data").clear();
	}

	@Test
	public void toUser() {
		final UserSqlRepository repository = new UserSqlRepository() {
			@Override
			public UserOrg findById(final String login) {
				final UserOrg userLdap = new UserOrg();
				userLdap.setId(login);
				userLdap.setFirstName("First");
				return userLdap;
			}

		};
		Assertions.assertEquals("user1", repository.toUser("user1").getId());
		Assertions.assertEquals("First", repository.toUser("user1").getFirstName());
	}

	@Test
	public void toUserNotExist() {
		final UserSqlRepository repository = new UserSqlRepository() {
			@Override
			public UserOrg findById(final String login) {
				return null;
			}

		};
		Assertions.assertEquals("user1", repository.toUser("user1").getId());
		Assertions.assertNull(repository.toUser("user1").getFirstName());
	}

	@Test
	public void toUserNull() {
		Assertions.assertNull(repository.toUser(null));
	}

	@Test
	public void getToken() {
		Assertions.assertEquals("Azerty01", repository.getToken("jdoe4"));
		Assertions.assertEquals("credential", repository.getToken("jdoe5"));
		Assertions.assertNull(repository.getToken("any"));
	}

	@Test
	public void getTokenNotExists() {
		Assertions.assertNull(repository.getToken("any"));
	}

	@Test
	public void findByIdExpectedNotVisibleCompany() {
		final UserSqlRepository repository = new UserSqlRepository() {
			@Override
			public UserOrg findById(final String login) {
				UserOrg user = new UserOrg();
				user.setId(login);
				return user;
			}
		};
		repository.setCompanyRepository(Mockito.mock(CompanySqlRepository.class));
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			repository.findByIdExpected("user1", "user2");
		}), "id", "unknown-id");
	}

	@Test
	public void findByIdExpected() {
		final UserSqlRepository repository = new UserSqlRepository() {
			@Override
			public UserOrg findById(final String login) {
				UserOrg user = new UserOrg();
				user.setId(login);
				user.setCompany("company");
				return user;
			}
		};
		CompanySqlRepository mock = Mockito.mock(CompanySqlRepository.class);
		Mockito.when(mock.findById("user1", "company")).thenReturn(new CompanyOrg("", ""));
		repository.setCompanyRepository(mock);
		repository.findByIdExpected("user1", "user2");
	}

	@Test
	public void setPassword() {
		Assertions.assertTrue(repository.authenticate("jdoe4", "Azerty01"));
		setPassword("Azerty01", "new-password");
		Assertions.assertTrue(credentialRepository.findByExpected("user.id", "jdoe4").getSalt().length() >= 64);
		Assertions.assertTrue(credentialRepository.findByExpected("user.id", "jdoe4").getValue().length() >= 32);
		Assertions.assertTrue(repository.authenticate("jdoe4", "new-password"));
		Assertions.assertFalse(repository.authenticate("jdoe4", "Azerty01"));

		// This user has the same credential, but hashed with a different salt
		Assertions.assertTrue(repository.authenticate("fdoe2", "new-password"));
	}

	@Test
	public void setPasswordNullOldPassword() {
		setPassword(null, "new-password");
	}

	@Test
	public void setPasswordFirst() {
		final UserOrg user = newUser();
		user.setId("flast0");
		repository.setPassword(user, null, "new-password");
	}

	@Test
	public void authenticateNoCredential() {
		Assertions.assertFalse(repository.authenticate("flast0", "any"));
	}

	@Test
	public void setPasswordInvalidAlgo() {
		repository.setSecretKeyFactory("invalid");
		Assertions.assertThrows(TechnicalException.class,
				() -> repository.setPassword(newUser(), null, "new-password"));
	}

	@Test
	public void setPasswordInvalidSpec() {
		repository.setSecretKeyFactory("RSA");
		Assertions.assertThrows(TechnicalException.class,
				() -> repository.setPassword(newUser(), null, "new-password"));
	}

	@Test
	public void isolateRestore() {
		final UserOrg cacheUser = newUser();
		cacheUser.setCompany("socygan");

		// Restore not isolated
		repository.restore(cacheUser);
		final UserSqlCredential user = credentialRepository.findBy("user.id", "jdoe4");
		Assertions.assertNotNull(user.getValue());
		Assertions.assertNull(user.getLocked());
		Assertions.assertNull(user.getLockedBy());
		Assertions.assertNull(user.getIsolated());

		// Isolate
		isolate(cacheUser);
		isolate(cacheUser); // no effect

		// Restore
		repository.restore(cacheUser);
		final UserSqlCredential user2 = credentialRepository.findBy("user.id", "jdoe4");
		Assertions.assertNull(user2.getValue());
		Assertions.assertNull(user2.getLocked());
		Assertions.assertNull(user2.getLockedBy());
		Assertions.assertNull(user2.getIsolated());

		// Check the cache
		Assertions.assertNull(cacheUser.getLocked());
		Assertions.assertNull(cacheUser.getLockedBy());
		Assertions.assertNull(cacheUser.getIsolated());
		Assertions.assertEquals("socygan", cacheUser.getCompany());
	}

	private void isolate(final UserOrg cacheUser) {
		repository.isolate("fdaugan", cacheUser);
		final UserSqlCredential user = credentialRepository.findBy("user.id", "jdoe4");
		Assertions.assertNull(user.getValue());
		Assertions.assertNotNull(user.getLocked());
		Assertions.assertEquals("fdaugan", user.getLockedBy());
		Assertions.assertEquals("socygan", user.getIsolated());

		// Check the cache
		Assertions.assertNotNull(cacheUser.getLocked());
		Assertions.assertEquals("fdaugan", cacheUser.getLockedBy());
		Assertions.assertEquals("socygan", cacheUser.getIsolated());
	}

	@Test
	public void lock() {
		final UserOrg cacheUser = newUser();
		repository.lock("fdaugan", cacheUser);
		final UserSqlCredential user = credentialRepository.findBy("user.id", "jdoe4");
		Assertions.assertNull(user.getValue());
		Assertions.assertNotNull(user.getLocked());
		Assertions.assertEquals("fdaugan", user.getLockedBy());

		// Check the cache
		Assertions.assertNotNull(cacheUser.getLocked());
		Assertions.assertEquals("fdaugan", cacheUser.getLockedBy());
	}

	@Test
	public void lockAlreadyLocked() {
		final UserOrg cacheUser = newUser();
		cacheUser.setLocked(new Date());
		cacheUser.setLockedBy("someone");
		repository.lock("fdaugan", cacheUser);
		final UserSqlCredential user = credentialRepository.findBy("user.id", "jdoe4");
		Assertions.assertEquals("jdoe4", user.getUser().getId()); // Coverage only

		// Unchanged
		Assertions.assertEquals("Azerty01", user.getValue());

		// Check the cache
		Assertions.assertNotNull(cacheUser.getLocked());
		Assertions.assertEquals("someone", cacheUser.getLockedBy());
	}

	@Test
	public void setPasswordBadPassword() {
		final UserOrg user = newUser();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class,
				() -> repository.setPassword(user, "wrong-old-password", "new-password")), "password", "login");
	}

	private void setPassword(final String password, final String newPassword) {
		final UserOrg user = newUser();
		repository.setPassword(user, password, newPassword);
	}

	private UserOrg newUser() {
		final UserOrg user = new UserOrg();
		user.setDn("cn=Any");
		user.setId("jdoe4");
		return user;
	}

	@Test
	public void checkUserStatus() {
		final UserOrg user = newUser();
		repository.checkLockStatus(user);

		Assertions.assertNull(user.getLocked());

		// Only for coverage, design constraint
		Assertions.assertEquals("ou=internal,ou=people", repository.getPeopleInternalBaseDn());
	}

	@Test
	public void unlockNoLocked() {
		final UserOrg user = newUser();

		// Dirty flag, should never occurs, but this flag is used to check the untouched user
		user.setLockedBy("some");

		repository.unlock(user);

		// The user was not locked, expect the user data to be untouched
		Assertions.assertEquals("some", user.getLockedBy());
	}

	@Test
	public void unlockIsolated() {
		final UserOrg user = newUser();
		user.setIsolated("old-company");

		repository.unlock(user);

		Assertions.assertEquals("old-company", user.getIsolated());
	}

	@Test
	public void unlock() {
		final UserOrg user = newUser();
		user.setLocked(new Date());
		user.setLockedBy("some");

		repository.unlock(user);

		Assertions.assertNull(user.getLocked());
		Assertions.assertNull(user.getLockedBy());
	}
}
