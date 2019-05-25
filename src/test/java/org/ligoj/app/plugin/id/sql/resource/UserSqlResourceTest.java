/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.SimpleUserOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.dao.CacheGroupRepository;
import org.ligoj.app.iam.dao.CacheMembershipRepository;
import org.ligoj.app.iam.dao.CacheUserRepository;
import org.ligoj.app.iam.model.CacheGroup;
import org.ligoj.app.iam.model.CacheMembership;
import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.plugin.id.resource.GroupVo;
import org.ligoj.app.plugin.id.resource.UserOrgEditionVo;
import org.ligoj.app.plugin.id.resource.UserOrgResource;
import org.ligoj.app.plugin.id.resource.UserOrgVo;
import org.ligoj.app.plugin.id.sql.dao.GroupSqlRepository;
import org.ligoj.app.plugin.id.sql.dao.UserSqlCredentialRepository;
import org.ligoj.app.plugin.id.sql.dao.UserSqlRepository;
import org.ligoj.app.plugin.id.sql.model.UserSqlCredential;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test of {@link UserOrgResource} Delegate
 */
class UserSqlResourceTest extends AbstractSqlPluginResourceTest {

	@Autowired
	protected UserSqlCredentialRepository credentialRepository;

	@Autowired
	protected CacheGroupRepository cacheGroupRepository;
	@Autowired
	protected CacheUserRepository cacheUserRepository;

	@Autowired
	protected CacheMembershipRepository cacheMembershipRepository;

	@Test
	void findById() {
		final UserOrg user = resource.findById("fdaugan");
		findById(user);
	}

	@Test
	void findByIdNoCache() {
		final UserOrg user = resource.findByIdNoCache("fdaugan");
		Assertions.assertNotNull(user);
		Assertions.assertEquals("fdaugan", user.getId());
		Assertions.assertEquals("Fabrice", user.getFirstName());
		Assertions.assertEquals("Daugan", user.getLastName());
		Assertions.assertEquals("ligoj", user.getCompany());
		Assertions.assertEquals("fabrice.daugan@sample.com", user.getMails().get(0));
	}

	@Test
	void findByIdCaseInsensitive() {
		final UserOrg user = resource.findById("fdaugan");
		findById(user);
	}

	@Test
	void findBy() {
		final List<UserOrg> users = resource.findAllBy("mails", "marc.martin@sample.com");
		Assertions.assertEquals(1, users.size());
		final UserOrg user = users.get(0);
		Assertions.assertEquals("mmartin", user.getName());
	}

	@Test
	void findByIdNotExists() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.findById("any");
		}), "id", "unknown-id");
	}

	@Test
	void findByIdNotManagedUser() {
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.findById("fdaugan");
		}), "id", "unknown-id");
	}

	/**
	 * Show users inside the company "ing" (or sub company), and members of group "dig rha", and matching to criteria
	 * "iRsT"
	 */
	@Test
	void findAllAllFiltersAllRights() {

		final TableItem<UserOrgVo> tableItem = resource.findAll("ing", "dig rha", "iRsT", newUriInfoAsc("id"));
		Assertions.assertEquals(2, tableItem.getRecordsTotal());
		Assertions.assertEquals(2, tableItem.getRecordsFiltered());

		// Check the users
		final UserOrgVo user = tableItem.getData().get(0);
		Assertions.assertEquals("fdoe2", user.getId());
		Assertions.assertEquals("jdoe5", tableItem.getData().get(1).getId());

		// Check the other attributes
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("First2", user.getFirstName());
		Assertions.assertEquals("Doe2", user.getLastName());
		Assertions.assertEquals("first2.doe2@ing.fr", user.getMails().get(0));
		Assertions.assertTrue(user.isCanWrite());
		final List<GroupVo> groups = new ArrayList<>(user.getGroups());
		Assertions.assertEquals(2, groups.size());
		Assertions.assertEquals("Biz Agency", groups.get(0).getName());
		Assertions.assertEquals("DIG RHA", groups.get(1).getName());
	}

	@Test
	void findAllAllFiltersReducesGroupsAscLogin() {
		initSpringSecurityContext("fdaugan");
		final TableItem<UserOrgVo> tableItem = resource.findAll("ing", "dig rha", "iRsT", newUriInfoAsc("id"));
		Assertions.assertEquals(2, tableItem.getRecordsTotal());
		Assertions.assertEquals(2, tableItem.getRecordsFiltered());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().get(0).getId());
		Assertions.assertEquals("jdoe5", tableItem.getData().get(1).getId());

		// Check the other attributes
		Assertions.assertEquals("ing", tableItem.getData().get(0).getCompany());
		Assertions.assertEquals("First2", tableItem.getData().get(0).getFirstName());
		Assertions.assertEquals("Doe2", tableItem.getData().get(0).getLastName());
		Assertions.assertEquals("first2.doe2@ing.fr", tableItem.getData().get(0).getMails().get(0));
		final List<GroupVo> groups = new ArrayList<>(tableItem.getData().get(0).getGroups());
		Assertions.assertEquals(2, groups.size());
		Assertions.assertEquals("Biz Agency", groups.get(0).getName());
		Assertions.assertEquals("DIG RHA", groups.get(1).getName());
	}

	@Test
	void findAllNotSecure() {
		initSpringSecurityContext("fdaugan");
		final List<UserOrg> tableItem = resource.findAllNotSecure("ing", "dig rha");
		Assertions.assertEquals(4, tableItem.size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.get(0).getId());
		Assertions.assertEquals("jdoe4", tableItem.get(1).getId());
		Assertions.assertEquals("jdoe5", tableItem.get(2).getId());

		// Check the other attributes
		Assertions.assertEquals("ing", tableItem.get(0).getCompany());
		Assertions.assertEquals("First2", tableItem.get(0).getFirstName());
		Assertions.assertEquals("Doe2", tableItem.get(0).getLastName());
		Assertions.assertEquals("first2.doe2@ing.fr", tableItem.get(0).getMails().get(0));
		Assertions.assertEquals(2, tableItem.get(0).getGroups().size());
		Assertions.assertTrue(tableItem.get(0).getGroups().contains("biz agency"));
		Assertions.assertTrue(tableItem.get(0).getGroups().contains("dig rha"));
	}

	@Test
	void findAllDefaultDescFirstName() {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "5");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add(DataTableAttributes.START, "6");
		uriInfo.getQueryParameters().add("columns[2][data]", "firstName");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "desc");

		initSpringSecurityContext("fdaugan");
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "e", uriInfo);
		Assertions.assertEquals(13, tableItem.getRecordsTotal());
		Assertions.assertEquals(13, tableItem.getRecordsFiltered());
		Assertions.assertEquals(5, tableItem.getData().size());

		// Check the users

		// My company
		// [SimpleUser(id=jdoe4), SimpleUser(id=hdurant), SimpleUser(id=fdoe2),
		// SimpleUser(id=fdauganb)]
		Assertions.assertEquals("jdoe4", tableItem.getData().get(0).getId());
		Assertions.assertEquals("hdurant", tableItem.getData().get(1).getId());
		Assertions.assertEquals("fdoe2", tableItem.getData().get(3).getId());

		// Not my company, brought by delegation
		Assertions.assertEquals("jdoe5", tableItem.getData().get(2).getId()); //
	}

	@Test
	void findAllDefaultDescMail() {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "5");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add(DataTableAttributes.START, "2");
		uriInfo.getQueryParameters().add("columns[2][data]", "mail");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "desc");

		initSpringSecurityContext("fdaugan");
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "@sample.com", uriInfo);
		Assertions.assertEquals(6, tableItem.getRecordsTotal());
		Assertions.assertEquals(6, tableItem.getRecordsFiltered());
		Assertions.assertEquals(5, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdaugan", tableItem.getData().get(1).getId());
	}

	/**
	 * One delegation to members of group "ligoj-gstack" to see the company "ing"
	 */
	@Test
	void findAllUsingDelegateReceiverGroup() {
		initSpringSecurityContext("alongchu");
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, null, newUriInfoAsc("id"));

		// Counts : 8 from ing, + 7 from the same company
		Assertions.assertEquals(15, tableItem.getRecordsTotal());
		Assertions.assertEquals(15, tableItem.getRecordsFiltered());
		Assertions.assertEquals(15, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("alongchu", tableItem.getData().get(0).getId());
		Assertions.assertFalse(tableItem.getData().get(0).isCanWrite());

		// Check the groups
		Assertions.assertEquals(1, tableItem.getData().get(0).getGroups().size());
		Assertions.assertEquals("ligoj-gStack", tableItem.getData().get(0).getGroups().get(0).getName());
	}

	/**
	 * No delegation for any group, but only for a company. So see only users within these company : ing(5) + socygan(1)
	 */
	@Test
	void findAllForMyCompany() {
		initSpringSecurityContext("assist");
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, null, newUriInfoAsc("id"));
		Assertions.assertEquals(9, tableItem.getRecordsTotal());
		Assertions.assertEquals(9, tableItem.getRecordsFiltered());
		Assertions.assertEquals(9, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().get(0).getId());
		Assertions.assertTrue(tableItem.getData().get(0).isCanWrite());

		// Check the groups
		Assertions.assertEquals(0, tableItem.getData().get(0).getGroups().size());
	}

	/**
	 * No delegation for any group, but only for a company. So see only users within this company : ing(5)
	 */
	@Test
	void findAllForMyCompanyFilter() {
		initSpringSecurityContext("assist");

		final TableItem<UserOrgVo> tableItem = resource.findAll("ing", null, null, newUriInfoAsc("id"));
		Assertions.assertEquals(8, tableItem.getRecordsTotal());
		Assertions.assertEquals(8, tableItem.getRecordsFiltered());
		Assertions.assertEquals(8, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().get(0).getId());
		Assertions.assertTrue(tableItem.getData().get(0).isCanWrite());

		// Check the groups
		Assertions.assertEquals(0, tableItem.getData().get(0).getGroups().size());
	}

	/**
	 * Whatever the managed company, if the current user sees a group, can search any user even in a different company
	 * this user can manage. <br>
	 */
	@Test
	void findAllForMyGroup() {
		initSpringSecurityContext("mmartin");
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, "dig as", null, newUriInfoAsc("id"));

		// 4 users from delegate and 1 from my company
		Assertions.assertEquals(5, tableItem.getRecordsTotal());
		Assertions.assertEquals(5, tableItem.getRecordsFiltered());
		Assertions.assertEquals(5, tableItem.getData().size());

		// Check the users (from delegate)
		Assertions.assertEquals("fdoe2", tableItem.getData().get(0).getId());
		Assertions.assertFalse(tableItem.getData().get(0).isCanWrite());
		Assertions.assertTrue(tableItem.getData().get(0).isCanWriteGroups());
	}

	/**
	 * Whatever the managed company, if the current user sees a group, then he can search any user even in a different
	 * company this user can manage. <br>
	 */
	@Test
	void findAllForMySubGroup() {
		initSpringSecurityContext("mmartin");
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, "biz agency", "fdoe2", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().get(0).getId());
		Assertions.assertFalse(tableItem.getData().get(0).isCanWrite());
		Assertions.assertTrue(tableItem.getData().get(0).isCanWriteGroups());

		// Check the groups
		// "Biz Agency" is visible since "mmartin" is in the parent group "
		Assertions.assertEquals(2, tableItem.getData().get(0).getGroups().size());
		Assertions.assertEquals("Biz Agency", tableItem.getData().get(0).getGroups().get(0).getName());
		Assertions.assertTrue(tableItem.getData().get(0).getGroups().get(0).isCanWrite());
		Assertions.assertEquals("DIG RHA", tableItem.getData().get(0).getGroups().get(1).getName());
		Assertions.assertFalse(tableItem.getData().get(0).getGroups().get(1).isCanWrite());
	}

	@Test
	void findAllFullAscCompany() {
		initSpringSecurityContext("fdaugan");
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, null, newUriInfoAsc("company"));

		// 8 from delegate, 7 from my company
		Assertions.assertEquals(15, tableItem.getRecordsTotal());
		Assertions.assertEquals(15, tableItem.getRecordsFiltered());
		Assertions.assertEquals(15, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().get(0).getId());
	}

	@Test
	void findAllFullDescCompany() {
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, null, newUriInfoDesc("company"));
		Assertions.assertEquals(16, tableItem.getRecordsTotal());
		Assertions.assertEquals(16, tableItem.getRecordsFiltered());
		Assertions.assertEquals(16, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("flast0", tableItem.getData().get(0).getId());
		Assertions.assertEquals("socygan", tableItem.getData().get(0).getCompany());
		Assertions.assertEquals("fdaugan", tableItem.getData().get(6).getId());
		Assertions.assertEquals("ligoj", tableItem.getData().get(6).getCompany());
	}

	@Test
	void findAllFullAscLastName() {
		initSpringSecurityContext("fdaugan");
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, null, newUriInfoAsc("lastName"));

		// 8 from delegate, 7 from my company
		Assertions.assertEquals(15, tableItem.getRecordsTotal());
		Assertions.assertEquals(15, tableItem.getRecordsFiltered());
		Assertions.assertEquals(15, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().get(3).getId());
	}

	@Test
	void findAllMemberDifferentCase() {
		final TableItem<UserOrgVo> tableItem = resource.findAll("LigoJ", "ProductioN", "mmarTIN",
				newUriInfoAsc("lastName"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("mmartin", tableItem.getData().get(0).getId());
	}

	/**
	 * No available delegate for the current user -> 0
	 */
	@Test
	void findAllNoRight() {
		initSpringSecurityContext("any");

		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, null, newUriInfoAsc("id"));
		Assertions.assertEquals(0, tableItem.getRecordsTotal());
		Assertions.assertEquals(0, tableItem.getRecordsFiltered());
		Assertions.assertEquals(0, tableItem.getData().size());
	}

	/**
	 * Whatever the managed company, if the current user sees a group, can search any user even in a different company
	 * this user can manage. <br>
	 */
	@Test
	void findAllNoWrite() {
		initSpringSecurityContext("mlavoine");
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "fdoe2", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().get(0).getId());
		Assertions.assertFalse(tableItem.getData().get(0).isCanWrite());

		// Check the groups
		Assertions.assertEquals(1, tableItem.getData().get(0).getGroups().size());
		Assertions.assertEquals("Biz Agency", tableItem.getData().get(0).getGroups().get(0).getName());
		Assertions.assertFalse(tableItem.getData().get(0).getGroups().get(0).isCanWrite());
	}

	/**
	 * Add filter by group, but this group does not exist/not visible. No match.
	 */
	@Test
	void findAllFilteredNonExistingGroup() {
		initSpringSecurityContext("fdaugan");
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, "any", null, newUriInfoAsc("id"));
		Assertions.assertEquals(0, tableItem.getRecordsTotal());
	}

	@Test
	void createUserAlreadyExists() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flast12@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.create(user);
		}), "id", "already-exist");
	}

	@Test
	void deleteUserNoDelegateCompany() {
		initSpringSecurityContext("mmartin");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete("flast1");
		}), "id", "read-only");
	}

	@Test
	void deleteLastMember() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete("mmartin");
		}), "id", "last-member-of-group");
	}

	@Test
	void deleteUserNoDelegateWriteCompany() {
		initSpringSecurityContext("mtuyer");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete("flast1");
		}), "id", "read-only");
	}

	@Test
	void mergeUserNoChange() {
		final UserOrg user2 = getUser().findById("flast1");
		Assertions.assertNull(user2.getDepartment());
		Assertions.assertNull(user2.getLocalId());

		resource.mergeUser(user2, new UserOrg());
		Assertions.assertNull(user2.getDepartment());
		Assertions.assertNull(user2.getLocalId());
	}

	@Test
	void mergeUser() {
		final UserOrg user2 = getUser().findById("flast1");
		Assertions.assertNull(user2.getDepartment());
		Assertions.assertNull(user2.getLocalId());

		final UserOrg newUser = new UserOrg();
		newUser.setDepartment("any");
		newUser.setLocalId("some");
		resource.mergeUser(user2, newUser);
		Assertions.assertEquals("any", user2.getDepartment());
		Assertions.assertEquals("some", user2.getLocalId());

		// Revert to previous state (null)
		resource.mergeUser(user2, new UserOrg());
		Assertions.assertNull(user2.getDepartment());
		Assertions.assertNull(user2.getLocalId());
	}

	/**
	 * Update everything : attributes and mails
	 */
	@Test
	void update() {
		final UserOrgEditionVo userEdit = new UserOrgEditionVo();
		userEdit.setId("flast1");
		userEdit.setFirstName("FirstA");
		userEdit.setLastName("LastA");
		userEdit.setCompany("ing");
		userEdit.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		userEdit.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(userEdit);
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "flast1", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final UserOrgVo user = tableItem.getData().get(0);
		Assertions.assertEquals("flast1", user.getId());
		Assertions.assertEquals("Firsta", user.getFirstName());
		Assertions.assertEquals("Lasta", user.getLastName());
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("flasta@ing.com", user.getMails().get(0));
		Assertions.assertEquals(1, user.getGroups().size());
		Assertions.assertEquals("DIG RHA", user.getGroups().get(0).getName());

		// Rollback attributes
		userEdit.setId("flast1");
		userEdit.setFirstName("First1");
		userEdit.setLastName("Last1");
		userEdit.setCompany("ing");
		userEdit.setMail("first1.last1@ing.fr");
		userEdit.setGroups(null);
		resource.update(userEdit);
	}

	@Test
	void updateFirstName() {
		// First name change only
		final UserOrgEditionVo userEdit = new UserOrgEditionVo();
		userEdit.setId("jlast3");
		userEdit.setFirstName("John31");
		userEdit.setLastName("Last3");
		userEdit.setCompany("ing");
		userEdit.setMail("john3.last3@ing.com");
		userEdit.setGroups(null);
		initSpringSecurityContext("assist");
		resource.update(userEdit);
		TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		UserOrgVo user = tableItem.getData().get(0);
		Assertions.assertEquals("jlast3", user.getId());
		Assertions.assertEquals("John31", user.getFirstName());
		Assertions.assertEquals("Last3", user.getLastName());
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("john3.last3@ing.com", user.getMails().get(0));
		Assertions.assertEquals(0, user.getGroups().size());
		rollbackUser();
	}

	@Test
	void updateLastName() {
		// Last name change only
		final UserOrgEditionVo userEdit = new UserOrgEditionVo();
		userEdit.setId("jlast3");
		userEdit.setFirstName("John31");
		userEdit.setLastName("Last31");
		userEdit.setCompany("ing");
		userEdit.setMail("john3.last3@ing.com");
		userEdit.setGroups(Collections.singleton("DIG RHA"));
		resource.update(userEdit);
		TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		UserOrgVo user = tableItem.getData().get(0);
		Assertions.assertEquals("jlast3", user.getId());
		Assertions.assertEquals("John31", user.getFirstName());
		Assertions.assertEquals("Last31", user.getLastName());
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("john3.last3@ing.com", user.getMails().get(0));
		Assertions.assertEquals(1, user.getGroups().size());
		rollbackUser();
	}

	@Test
	void updateMail() {
		// Mail change only
		final UserOrgEditionVo userEdit = new UserOrgEditionVo();
		userEdit.setId("jlast3");
		userEdit.setFirstName("John31");
		userEdit.setLastName("Last31");
		userEdit.setCompany("ing");
		userEdit.setMail("john31.last31@ing.com");
		userEdit.setGroups(Collections.singleton("DIG RHA"));
		resource.update(userEdit);
		TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final UserOrgVo user = tableItem.getData().get(0);
		Assertions.assertEquals("jlast3", user.getId());
		Assertions.assertEquals("John31", user.getFirstName());
		Assertions.assertEquals("Last31", user.getLastName());
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("john31.last31@ing.com", user.getMails().get(0));
		Assertions.assertEquals(1, user.getGroups().size());
		rollbackUser();
	}

	@Test
	void updateUserChangeCompanyAndBackAgain() {
		Assertions.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com",
				toDn(getContext("flast0")));

		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0"); // Unchanged
		user.setLastName("Last0"); // Unchanged
		user.setCompany("ing"); // Previous is "socygan"
		user.setMail("first0.last0@socygan.fr"); // Unchanged
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("assist");
		resource.update(user);

		// Check the new DN and company everywhere
		Assertions.assertEquals("uid=flast0,ou=ing,ou=external,ou=people,dc=sample,dc=com", toDn(getContext("flast0")));
		Assertions.assertEquals("ing",
				resource.findAll(null, null, "flast0", newUriInfo()).getData().get(0).getCompany());
		Assertions.assertEquals("ing", getUser().findByIdNoCache("flast0").getCompany());
		Assertions.assertEquals("ing", getUser().findById("flast0").getCompany());

		user.setCompany("socygan"); // Previous is "socygan"
		resource.update(user);

		// Check the old DN and company everywhere
		Assertions.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com",
				toDn(getContext("flast0")));
		Assertions.assertEquals("socygan",
				resource.findAll(null, null, "flast0", newUriInfo()).getData().get(0).getCompany());
		Assertions.assertEquals("socygan", getUser().findByIdNoCache("flast0").getCompany());
		Assertions.assertEquals("socygan", getUser().findById("flast0").getCompany());
	}

	@Test
	void updateUserCompanyNotExists() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("any");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(user);
		}), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserGroupNotExists() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("any");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(user);
		}), "group", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoChange() {
		final UserOrgEditionVo userEdit = new UserOrgEditionVo();
		userEdit.setId("jlast3");
		userEdit.setFirstName("John3");
		userEdit.setLastName("Last3");
		userEdit.setCompany("ing");
		userEdit.setMail("jlast3@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		userEdit.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(userEdit);
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final UserOrgVo user = tableItem.getData().get(0);
		Assertions.assertEquals("jlast3", user.getId());
		Assertions.assertEquals("John3", user.getFirstName());
		Assertions.assertEquals("Last3", user.getLastName());
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("jlast3@ing.com", user.getMails().get(0));
		Assertions.assertEquals(1, user.getGroups().size());
		Assertions.assertEquals("DIG RHA", user.getGroups().get(0).getName());
	}

	@Test
	void updateUserNoDelegate() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast1");
		user.setFirstName("FirstW");
		user.setLastName("LastW");
		user.setCompany("ing");
		user.setMail("flastw@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(user);
		}), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateNotVisibleTargetCompany() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.last0@socygan.fr");
		final List<String> groups = new ArrayList<>();
		groups.add("Biz Agency");
		user.setGroups(groups);
		initSpringSecurityContext("mlavoine");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(user);
		}), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoDelegateCompany() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("socygan");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(user);
		}), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoDelegateCompanyChangeFirstName() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("FirstA");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.last0@socygan.fr");
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(user);
		}), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoDelegateCompanyChangeMail() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.lastA@socygan.fr");
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(user);
		}), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoDelegateCompanyNoChange() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.last0@socygan.fr");
		initSpringSecurityContext("assist");
		resource.update(user);
	}

	@Test
	void updateUserNoDelegateGroupForTarget() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		groups.add("dig sud ouest"); // no right on this group
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(user);
		}), "group", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNotExists() {
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("flast11");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final List<String> groups = new ArrayList<>();
		user.setGroups(groups);
		initSpringSecurityContext("assist");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(user);
		}), "id", BusinessException.KEY_UNKNOWN_ID);
	}

	/**
	 * Add a group to user having already some groups but not visible from the current user.
	 */
	@Test
	void updateUserAddGroup() {
		// Pre condition, check the user "wuser", has not yet the group "DIG
		// RHA" we want to be added by "fdaugan"
		initSpringSecurityContext("fdaugan");
		final TableItem<UserOrgVo> initialResultsFromUpdater = resource.findAll(null, null, "wuser",
				newUriInfoAsc("id"));
		Assertions.assertEquals(1, initialResultsFromUpdater.getRecordsTotal());
		Assertions.assertEquals(1, initialResultsFromUpdater.getData().get(0).getGroups().size());
		Assertions.assertEquals("Biz Agency Manager",
				initialResultsFromUpdater.getData().get(0).getGroups().get(0).getName());

		// Pre condition, check the user "wuser", has no group visible by
		// "assist"
		initSpringSecurityContext("assist");
		final TableItem<UserOrgVo> assisteResult = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assertions.assertEquals(1, assisteResult.getRecordsTotal());
		Assertions.assertEquals(0, assisteResult.getData().get(0).getGroups().size());

		// Pre condition, check the user "wuser", "Biz Agency Manager" is not
		// visible by "mtuyer"
		initSpringSecurityContext("mtuyer");
		final TableItem<UserOrgVo> usersFromOtherGroupManager = resource.findAll(null, null, "wuser",
				newUriInfoAsc("id"));
		Assertions.assertEquals(1, usersFromOtherGroupManager.getRecordsTotal());
		Assertions.assertEquals(0, usersFromOtherGroupManager.getData().get(0).getGroups().size());

		// Add a new valid group "DIG RHA" to "wuser" by "fdaugan"
		initSpringSecurityContext("fdaugan");
		final UserOrgEditionVo user = new UserOrgEditionVo();
		user.setId("wuser");
		user.setFirstName("William");
		user.setLastName("User");
		user.setCompany("ing");
		user.setMail("wuser.wuser@ing.fr");
		final List<String> groups = new ArrayList<>();
		groups.add("DIG RHA");
		groups.add("Biz Agency Manager");
		user.setGroups(groups);
		resource.update(user);

		// Check the group "DIG RHA" is added and
		final TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());
		Assertions.assertEquals(2, tableItem.getData().get(0).getGroups().size());
		Assertions.assertEquals("Biz Agency Manager", tableItem.getData().get(0).getGroups().get(0).getName());
		Assertions.assertEquals("DIG RHA", tableItem.getData().get(0).getGroups().get(1).getName());

		// Check the user "wuser", still has no group visible by "assist"
		initSpringSecurityContext("assist");
		final TableItem<UserOrgVo> assisteResult2 = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assertions.assertEquals(1, assisteResult2.getRecordsTotal());
		Assertions.assertEquals(0, assisteResult2.getData().get(0).getGroups().size());

		// Check the user "wuser", still has the group "DIG RHA" visible by
		// "mtuyer"
		initSpringSecurityContext("mtuyer");
		final TableItem<UserOrgVo> usersFromOtherGroupManager2 = resource.findAll(null, null, "wuser",
				newUriInfoAsc("id"));
		Assertions.assertEquals(1, usersFromOtherGroupManager2.getRecordsTotal());
		Assertions.assertEquals("DIG RHA", usersFromOtherGroupManager2.getData().get(0).getGroups().get(0).getName());

		// Restore the old state
		initSpringSecurityContext("fdaugan");
		final UserOrgEditionVo user2 = new UserOrgEditionVo();
		user2.setId("wuser");
		user2.setFirstName("William");
		user2.setLastName("User");
		user2.setCompany("ing");
		user2.setMail("wuser.wuser@ing.fr");
		final List<String> groups2 = new ArrayList<>();
		groups2.add("Biz Agency Manager");
		user.setGroups(groups2);
		resource.update(user);
		final TableItem<UserOrgVo> initialResultsFromUpdater2 = resource.findAll(null, null, "wuser",
				newUriInfoAsc("id"));
		Assertions.assertEquals(1, initialResultsFromUpdater2.getRecordsTotal());
		Assertions.assertEquals(1, initialResultsFromUpdater2.getData().get(0).getGroups().size());
		Assertions.assertEquals("Biz Agency Manager",
				initialResultsFromUpdater2.getData().get(0).getGroups().get(0).getName());
	}

	/**
	 * Test user addition to a group this user is already member.
	 */
	@Test
	void addUserToGroup() {
		// Pre condition
		Assertions.assertTrue(resource.findById("wuser").getGroups().contains("Biz Agency Manager"));

		resource.addUserToGroup("wuser", "biz agency manager");

		// Post condition -> no change
		Assertions.assertTrue(resource.findById("wuser").getGroups().contains("Biz Agency Manager"));
	}

	@Test
	void deleteUserNoWriteRight() {
		initSpringSecurityContext("mmartin");
		Assertions.assertEquals(1, resource.findAll(null, null, "wuser", newUriInfo()).getData().size());
		Assertions.assertNotNull(getUser().findByIdNoCache("wuser"));
		Assertions.assertTrue(getGroup().findAll().get("biz agency manager").getMembers().contains("wuser"));
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete("wuser");
		}), "id", "read-only");
	}

	@Test
	void deleteUserNotExists() {
		initSpringSecurityContext("assist");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.delete("any");
		}), "id", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateMembership() {
		final UserSqlRepository repository = new UserSqlRepository();
		repository.setGroupRepository(Mockito.mock(GroupSqlRepository.class));
		final List<String> groups = new ArrayList<>();
		groups.add("dig rha");
		final UserOrg user = new UserOrg();
		final Collection<String> oldGroups = new ArrayList<>();
		user.setGroups(oldGroups);
		user.setId("flast1");
		user.setCompany("ing");
		repository.updateMembership(groups, user);
	}

	@Test
	void convertUserRaw() {
		final UserOrg user = getUser().toUser("jdoe5");
		checkRawUser(user);
		Assertions.assertNotNull(user.getGroups());
		Assertions.assertEquals(1, user.getGroups().size());
	}

	@Test
	void convertUserNotExist() {
		final UserOrg user = getUser().toUser("any");
		Assertions.assertNotNull(user);
		Assertions.assertEquals("any", user.getId());
		Assertions.assertNull(user.getCompany());
		Assertions.assertNull(user.getGroups());
		Assertions.assertNull(user.getFirstName());
		Assertions.assertNull(user.getLastName());
		Assertions.assertNull(user.getMails());
	}

	/**
	 * Check a user can see all users from the same company
	 */
	@Test
	void findAllMyCompany() {
		initSpringSecurityContext("mmartin");

		final TableItem<UserOrgVo> tableItem = resource.findAll("ligoj", null, null, newUriInfoAsc("id"));

		// 7 users from company 'ligoj', 0 from delegate
		Assertions.assertEquals(7, tableItem.getRecordsTotal());
		Assertions.assertEquals(7, tableItem.getRecordsFiltered());

		// Check the users
		Assertions.assertEquals("alongchu", tableItem.getData().get(0).getId());
	}

	/**
	 * When the requested company does not exists, return an empty set.
	 */
	@Test
	void findAllUnknowFilteredCompany() {
		final TableItem<UserOrgVo> tableItem = resource.findAll("any", null, null, newUriInfoAsc("id"));
		Assertions.assertEquals(0, tableItem.getRecordsTotal());
		Assertions.assertEquals(0, tableItem.getRecordsFiltered());
	}

	@Test
	void setIamProviderForTest() {
		// There for test by other plugin/application
		new UserOrgResource().setIamProvider(new IamProvider[] { Mockito.mock(IamProvider.class) });
	}

	@Autowired
	protected UserOrgResource resource;

	@Override
	@BeforeEach
	protected void prepareData() throws IOException {
		super.prepareData();
		persistEntities("csv", new Class[] { DelegateOrg.class }, StandardCharsets.UTF_8.name());

		// Force the cache to be created
		getUser().findAll();
	}

	/**
	 * Check the result : expects one entry
	 */
	protected void checkResult(final TableItem<UserOrgVo> tableItem) {
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final UserOrgVo user = tableItem.getData().get(0);
		Assertions.assertEquals("flasta", user.getId());
		Assertions.assertEquals("Firsta", user.getFirstName());
		Assertions.assertEquals("Lasta", user.getLastName());
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("flasta@ing.com", user.getMails().get(0));
		Assertions.assertEquals(1, user.getGroups().size());
		Assertions.assertEquals("DIG RHA", user.getGroups().get(0).getName());
	}

	@Override
	protected UriInfo newUriInfoAsc(final String ascProperty) {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "100");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add("columns[2][data]", ascProperty);
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "asc");
		return uriInfo;
	}

	@Override
	protected UriInfo newUriInfoDesc(final String property) {
		final UriInfo uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "100");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add("columns[2][data]", property);
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "desc");
		return uriInfo;
	}

	protected void findById(final UserOrg user) {
		Assertions.assertNotNull(user);
		Assertions.assertEquals("fdaugan", user.getId());
		Assertions.assertEquals("Fabrice", user.getFirstName());
		Assertions.assertEquals("Daugan", user.getLastName());
		Assertions.assertEquals("ligoj", user.getCompany());
		Assertions.assertEquals("fabrice.daugan@sample.com", user.getMails().get(0));
		Assertions.assertEquals(1, user.getGroups().size());
		Assertions.assertEquals("Hub Paris", user.getGroups().iterator().next());
	}

	protected void rollbackUser() {
		final UserOrgEditionVo userEdit = new UserOrgEditionVo();
		userEdit.setId("jlast3");
		userEdit.setFirstName("John3");
		userEdit.setLastName("Last3");
		userEdit.setCompany("ing");
		userEdit.setMail("john3.last3@ing.com");
		userEdit.setGroups(null);
		initSpringSecurityContext("assist");
		resource.update(userEdit);
		TableItem<UserOrgVo> tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		UserOrgVo user = tableItem.getData().get(0);
		Assertions.assertEquals("jlast3", user.getId());
		Assertions.assertEquals("John3", user.getFirstName());
		Assertions.assertEquals("Last3", user.getLastName());
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("john3.last3@ing.com", user.getMails().get(0));
		Assertions.assertEquals(0, user.getGroups().size());
	}

	protected CacheUser getContext(final String base, final String uid) {
		return cacheUserRepository.findOneExpected(uid);
	}

	protected CacheUser getContext(final String uid) {
		return getContext("dc=sample,dc=com", uid);
	}

	/**
	 * Check the DN and uniqueMember is updated for the related groups
	 */
	protected void checkDnAndMember(final CacheUser context, final String dn) {
		// Check the DN is restored
		Assertions.assertEquals(dn, toDn(context));

		// Check the uniqueMember is restored for the related groups
		checkMember(dn);
	}

	private String toDn(final CacheUser context) {
		return "uid=" + context.getId() + "," + context.getCompany().getDescription();
	}

	/**
	 * Check the uniqueMember is updated for the related groups
	 */
	protected void checkMember(final String dn) {
		CacheGroup group = cacheGroupRepository.findByNameExpected("ligoj-gStack");
		List<CacheMembership> members = cacheMembershipRepository.findAllBy("group", group);
		Assertions.assertEquals(1, members.size());
		Assertions.assertEquals(dn, members.get(0).getGroup().getDescription());
	}

	protected CacheUser checkUnlockedBefore() {
		initSpringSecurityContext(DEFAULT_USER);

		// Restore lock status from SQL
		final UserSqlCredential credential = credentialRepository.findBy("user.id", "alongchu");
		credential.setValue("secret");
		credential.setLocked(null);
		credential.setLockedBy(null);
		credential.setIsolated(null);
		credential.setSalt(null);
		credentialRepository.save(credential);

		// Asserts
		final CacheUser result = checkUnlocked();
		Assertions.assertNull(credentialRepository.findBy("user", result).getValue());
		return result;
	}

	protected CacheUser checkUnlockedAfter() {
		final CacheUser result = checkUnlocked();
		Assertions.assertNull(credentialRepository.findBy("user", result).getValue());
		return result;
	}

	protected CacheUser checkUnlocked() {
		assertUnlocked(resource.findAll("ligoj", null, "alongchu", newUriInfo()).getData().get(0));
		assertUnlocked(getUser().findByIdNoCache("alongchu"));
		assertUnlocked(getUser().findById("alongchu"));
		Assertions.assertTrue(getGroup().findAll().get("ligoj-gstack").getMembers().contains("alongchu"));

		final CacheUser result = getContext("alongchu");
		Assertions.assertNull(credentialRepository.findBy("user", result).getLocked());
		return result;
	}

	protected CacheUser check(final String company, final String base, final String patternLocked,
			final Consumer<SimpleUserOrg> checker) {
		// Check the status at business layer
		checker.accept(resource.findAll(company, null, "alongchu", newUriInfo()).getData().get(0));
		checker.accept(resource.findById("alongchu"));

		// Check the status at cache layer
		Assertions.assertTrue(getGroup().findAll().get("ligoj-gstack").getMembers().contains("alongchu"));
		checker.accept(getUser().findByIdNoCache("alongchu"));

		// Check in the status in the SQL
		final CacheUser result = getContext(base, "alongchu");

		Assertions.assertNull(credentialRepository.findBy("user", result).getValue());
		Assertions.assertNotNull(credentialRepository.findBy("user", result).getLocked());
		Assertions.assertEquals("junit", credentialRepository.findBy("user", result).getLockedBy());
		return result;
	}

	protected void assertLocked(final SimpleUserOrg user) {
		Assertions.assertNotNull(user.getLocked());
		Assertions.assertEquals("junit", user.getLockedBy());
	}

	protected void assertUnlocked(final SimpleUserOrg user) {
		Assertions.assertNull(user.getLocked());
		Assertions.assertNull(user.getLockedBy());
		Assertions.assertNull(user.getIsolated());
	}

	protected void checkRawUser(final SimpleUserOrg user) {
		Assertions.assertNotNull(user);
		Assertions.assertEquals("jdoe5", user.getId());
		Assertions.assertEquals("ing-internal", user.getCompany());
		Assertions.assertEquals("First5", user.getFirstName());
		Assertions.assertEquals("Last5", user.getLastName());
		Assertions.assertNotNull(user.getMails());
	}
}
