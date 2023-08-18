/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.dao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.iam.CompanyOrg;
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
import org.ligoj.bootstrap.core.resource.TechnicalException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link CompanySqlRepository}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class CompanySqlRepositoryTest extends AbstractJpaTest {

	@Autowired
	private CompanySqlRepository repository;

	@BeforeEach
	void init2() throws IOException {
		persistEntities("csv",
				new Class[] { DelegateOrg.class, ContainerScope.class, CacheCompany.class, CacheUser.class,
						CacheGroup.class, CacheMembership.class, Project.class, Node.class, Parameter.class,
						Subscription.class, ParameterValue.class, CacheProjectGroup.class, UserSqlCredential.class },
				StandardCharsets.UTF_8);
		cacheManager.getCache("id-sql-data").clear();
	}

	@Test
	void create() {
		final CompanyOrg org = createInternal();
		Assertions.assertEquals("other", org.getId());
		Assertions.assertEquals("ou=other,ou=external,ou=people,dc=sample,dc=com", org.getDescription());
	}

	private CompanyOrg createInternal() {
		return repository.create("OU=Other,ou=external,ou=people,dc=sample,dc=com", "other");
	}

	@Test
	void delete() {
		Assertions.assertEquals(9, repository.findAll().size());
		Assertions.assertEquals(9, repository.findAllNoCache().size());
		final CompanyOrg createInternal = createInternal();
		final CompanyOrg sub = repository.create("OU=Sub-Other,ou=other,ou=external,ou=people,dc=sample,dc=com",
				"sub-other");
		Assertions.assertEquals(11, repository.findAll().size());
		Assertions.assertEquals(11, repository.findAllNoCache().size());
		final Set<CompanyOrg> containers = new HashSet<>();
		containers.add(createInternal);
		containers.add(sub);
		final Map<String, Comparator<CompanyOrg>> customComparators = new HashMap<>();
		final Page<CompanyOrg> all = repository.findAll(containers, "o", PageRequest.of(0, 10), customComparators);
		Assertions.assertEquals(2, all.getContent().size());
		Assertions.assertEquals("other", all.getContent().get(0).getId());
		Assertions.assertEquals("sub-other", all.getContent().get(1).getId());
		repository.delete(createInternal);
		Assertions.assertEquals(9, repository.findAll().size());
		Assertions.assertEquals(9, repository.findAllNoCache().size());
	}

	@Test
	void newSqlName() {
		Assertions.assertThrows(TechnicalException.class, () -> repository.newLdapName("-invalid-"));
	}

	@Test
	void findAll() {
		final CompanyOrg createInternal = createInternal();
		final Set<CompanyOrg> containers = new HashSet<>();
		containers.add(createInternal);
		final Page<CompanyOrg> all = repository.findAll(containers, "", PageRequest.of(0, 10, Direction.DESC, "id"),
				Collections.emptyMap());
		Assertions.assertEquals(1, all.getContent().size());
		Assertions.assertEquals("other", all.getContent().get(0).getId());
	}

	@Test
	void findAllNoMatch() {
		final CompanyOrg createInternal = createInternal();
		final Set<CompanyOrg> containers = new HashSet<>();
		containers.add(createInternal);
		final Page<CompanyOrg> all = repository.findAll(containers, "-no-match-", PageRequest.of(0, 10, Direction.DESC, "id"),
				Collections.emptyMap());
		Assertions.assertEquals(0, all.getContent().size());
	}
}
