/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.dao;

import java.util.HashSet;
import java.util.Set;

import javax.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.bootstrap.AbstractDataGeneratorTest;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link GroupSqlRepository}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class GroupSqlRepositoryTest extends AbstractDataGeneratorTest {

	@Test
	void addUser() {
		final Set<String> users = new HashSet<>();
		final GroupSqlRepository groupRepository = new GroupSqlRepository() {
			@Override
			public GroupOrg findById(final String name) {
				return new GroupOrg("dc=" + name, name, users);
			}

		};
		final CacheSqlRepository cacheRepository = Mockito.mock(CacheSqlRepository.class);
		groupRepository.setRepository(cacheRepository);
		addUser(groupRepository);

		Mockito.verify(cacheRepository, VerificationModeFactory.times(1))
				.addUserToGroup(ArgumentMatchers.any(UserOrg.class), ArgumentMatchers.any(GroupOrg.class));
	}

	@Test
	void addUserAlreadyMember() {
		final Set<String> users = new HashSet<>();
		users.add("flast1");
		final GroupSqlRepository groupRepository = new GroupSqlRepository() {
			@Override
			public GroupOrg findById(final String name) {
				return new GroupOrg("dc=" + name, name, users);
			}

		};
		final CacheSqlRepository cacheRepository = Mockito.mock(CacheSqlRepository.class);
		groupRepository.setRepository(cacheRepository);
		addUser(groupRepository);

		Assertions.assertEquals(1, users.size());
		Assertions.assertTrue(users.contains("flast1"));
	}

	@Test
	void removeUser() {
		final GroupSqlRepository groupRepository = newGroupSqlRepository();
		removeUser(groupRepository);
	}

	@Test
	void removeGroup() {
		final GroupSqlRepository groupRepository = newGroupSqlRepository();
		groupRepository.removeGroup(new GroupOrg("any", "any", null), "DIG RHA");
	}

	@Test
	void addGroup() {
		final GroupSqlRepository groupRepository = newGroupSqlRepository();
		groupRepository.addGroup(new GroupOrg("dc=any", "any", null), "DIG RHA");
	}

	private GroupSqlRepository newGroupSqlRepository() {
		final GroupSqlRepository groupRepository = new GroupSqlRepository() {
			@Override
			public GroupOrg findById(final String name) {
				return new GroupOrg("dc=" + name, name, new HashSet<>());
			}

		};
		groupRepository.setRepository(Mockito.mock(CacheSqlRepository.class));
		return groupRepository;
	}

	private void removeUser(final GroupSqlRepository groupRepository) {
		final UserOrg user = new UserOrg();
		user.setId("flast1");
		user.setDn("dc=com");
		user.setCompany("ing");
		groupRepository.removeUser(user, "DIG RHA");
	}

	private void addUser(final GroupSqlRepository groupRepository) {
		final UserOrg user = new UserOrg();
		user.setId("flast1");
		user.setDn("dc=com");
		user.setCompany("ing");
		groupRepository.addUser(user, "DIG RHA");
	}

	@Test
	void addAttributes() {
		new GroupSqlRepository().addAttributes(null, null, null);
	}

	@Test
	void empty() {
		GroupSqlRepository repository = new GroupSqlRepository();
		repository.setRepository(Mockito.mock(CacheSqlRepository.class));
		repository.empty(null, null);
	}

}
