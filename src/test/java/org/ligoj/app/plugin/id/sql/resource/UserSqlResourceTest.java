/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.resource;

import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.SimpleUserOrg;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.dao.CacheUserRepository;
import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.app.iam.model.DelegateOrg;
import org.ligoj.app.plugin.id.resource.UserOrgEditionVo;
import org.ligoj.app.plugin.id.resource.UserOrgResource;
import org.ligoj.app.plugin.id.sql.dao.GroupSqlRepository;
import org.ligoj.app.plugin.id.sql.dao.UserSqlRepository;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Test of {@link UserOrgResource} Delegate
 */
class UserSqlResourceTest extends AbstractSqlPluginResourceTest {

	@Autowired
	protected CacheUserRepository cacheUserRepository;

	@Test
	void findById() {
		final var user = resource.findById("fdaugan");
		findById(user);
	}

	@Test
	void findByIdNoCache() {
		final var user = resource.findByIdNoCache("fdaugan");
		Assertions.assertNotNull(user);
		Assertions.assertEquals("fdaugan", user.getId());
		Assertions.assertEquals("Fabrice", user.getFirstName());
		Assertions.assertEquals("Daugan", user.getLastName());
		Assertions.assertEquals("ligoj", user.getCompany());
		Assertions.assertEquals("fabrice.daugan@sample.com", user.getMails().getFirst());
	}

	@Test
	void findByIdCaseInsensitive() {
		final var user = resource.findById("fdaugan");
		findById(user);
	}

	@Test
	void findBy() {
		final var users = resource.findAllBy("mails", "marc.martin@sample.com");
		Assertions.assertEquals(1, users.size());
		final var user = users.getFirst();
		Assertions.assertEquals("mmartin", user.getName());
	}

	@Test
	void findByIdNotExists() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.findById("any")), "id", "unknown-id");
	}

	@Test
	void findByIdNotManagedUser() {
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.findById("fdaugan")), "id", "unknown-id");
	}

	/**
	 * Show users inside the company "ing" (or sub company), and members of group "dig rha", and matching to criteria
	 * "iRsT"
	 */
	@Test
	void findAllAllFiltersAllRights() {

		final var tableItem = resource.findAll("ing", "dig rha", "iRsT", newUriInfoAsc("id"));
		Assertions.assertEquals(2, tableItem.getRecordsTotal());
		Assertions.assertEquals(2, tableItem.getRecordsFiltered());

		// Check the users
		final var user = tableItem.getData().getFirst();
		Assertions.assertEquals("fdoe2", user.getId());
		Assertions.assertEquals("jdoe5", tableItem.getData().get(1).getId());

		// Check the other attributes
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("First2", user.getFirstName());
		Assertions.assertEquals("Doe2", user.getLastName());
		Assertions.assertEquals("first2.doe2@ing.fr", user.getMails().getFirst());
		Assertions.assertTrue(user.isCanWrite());
		final var groups = new ArrayList<>(user.getGroups());
		Assertions.assertEquals(2, groups.size());
		Assertions.assertEquals("Biz Agency", groups.getFirst().getName());
		Assertions.assertEquals("DIG RHA", groups.get(1).getName());
	}

	@Test
	void findAllAllFiltersReducesGroupsAscLogin() {
		initSpringSecurityContext("fdaugan");
		final var tableItem = resource.findAll("ing", "dig rha", "iRsT", newUriInfoAsc("id"));
		Assertions.assertEquals(2, tableItem.getRecordsTotal());
		Assertions.assertEquals(2, tableItem.getRecordsFiltered());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
		Assertions.assertEquals("jdoe5", tableItem.getData().get(1).getId());

		// Check the other attributes
		Assertions.assertEquals("ing", tableItem.getData().getFirst().getCompany());
		Assertions.assertEquals("First2", tableItem.getData().getFirst().getFirstName());
		Assertions.assertEquals("Doe2", tableItem.getData().getFirst().getLastName());
		Assertions.assertEquals("first2.doe2@ing.fr", tableItem.getData().getFirst().getMails().getFirst());
		final var groups = new ArrayList<>(tableItem.getData().getFirst().getGroups());
		Assertions.assertEquals(2, groups.size());
		Assertions.assertEquals("Biz Agency", groups.getFirst().getName());
		Assertions.assertEquals("DIG RHA", groups.get(1).getName());
	}

	@Test
	void findAllNotSecure() {
		initSpringSecurityContext("fdaugan");
		final var tableItem = resource.findAllNotSecure("ing", "dig rha");
		Assertions.assertEquals(4, tableItem.size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getFirst().getId());
		Assertions.assertEquals("jdoe4", tableItem.get(1).getId());
		Assertions.assertEquals("jdoe5", tableItem.get(2).getId());

		// Check the other attributes
		Assertions.assertEquals("ing", tableItem.getFirst().getCompany());
		Assertions.assertEquals("First2", tableItem.getFirst().getFirstName());
		Assertions.assertEquals("Doe2", tableItem.getFirst().getLastName());
		Assertions.assertEquals("first2.doe2@ing.fr", tableItem.getFirst().getMails().getFirst());
		Assertions.assertEquals(2, tableItem.getFirst().getGroups().size());
		Assertions.assertTrue(tableItem.getFirst().getGroups().contains("biz agency"));
		Assertions.assertTrue(tableItem.getFirst().getGroups().contains("dig rha"));
	}

	@Test
	void findAllDefaultDescFirstName() {
		final var uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "5");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add(DataTableAttributes.START, "6");
		uriInfo.getQueryParameters().add("columns[2][data]", "firstName");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "desc");

		initSpringSecurityContext("fdaugan");
		final var tableItem = resource.findAll(null, null, "e", uriInfo);
		Assertions.assertEquals(13, tableItem.getRecordsTotal());
		Assertions.assertEquals(13, tableItem.getRecordsFiltered());
		Assertions.assertEquals(5, tableItem.getData().size());

		// Check the users

		// My company
		// [SimpleUser(id=jdoe4), SimpleUser(id=hdurant), SimpleUser(id=fdoe2),
		// SimpleUser(id=fdauganb)]
		Assertions.assertEquals("jdoe4", tableItem.getData().getFirst().getId());
		Assertions.assertEquals("hdurant", tableItem.getData().get(1).getId());
		Assertions.assertEquals("fdoe2", tableItem.getData().get(3).getId());

		// Not my company, brought by delegation
		Assertions.assertEquals("jdoe5", tableItem.getData().get(2).getId()); //
	}

	@Test
	void findAllDefaultDescMail() {
		final var uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "5");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add(DataTableAttributes.START, "2");
		uriInfo.getQueryParameters().add("columns[2][data]", "mail");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "desc");

		initSpringSecurityContext("fdaugan");
		final var tableItem = resource.findAll(null, null, "@sample.com", uriInfo);
		Assertions.assertEquals(6, tableItem.getRecordsTotal());
		Assertions.assertEquals(6, tableItem.getRecordsFiltered());
		Assertions.assertEquals(5, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdaugan", tableItem.getData().get(1).getId());
	}

	/**
	 * One delegation to members of group "ligoj-jupiter" to see the company "ing"
	 */
	@Test
	void findAllUsingDelegateReceiverGroup() {
		initSpringSecurityContext("admin-test");
		final var tableItem = resource.findAll(null, null, null, newUriInfoAsc("id"));

		// Counts : 8 from ing, + 7 from the same company
		Assertions.assertEquals(15, tableItem.getRecordsTotal());
		Assertions.assertEquals(15, tableItem.getRecordsFiltered());
		Assertions.assertEquals(15, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("admin-test", tableItem.getData().getFirst().getId());
		Assertions.assertFalse(tableItem.getData().getFirst().isCanWrite());

		// Check the groups
		Assertions.assertEquals(1, tableItem.getData().getFirst().getGroups().size());
		Assertions.assertEquals("ligoj-Jupiter", tableItem.getData().getFirst().getGroups().getFirst().getName());
	}

	/**
	 * No delegation for any group, but only for a company. So see only users within these company : ing(5) + socygan(1)
	 */
	@Test
	void findAllForMyCompany() {
		initSpringSecurityContext("assist");
		final var tableItem = resource.findAll(null, null, null, newUriInfoAsc("id"));
		Assertions.assertEquals(9, tableItem.getRecordsTotal());
		Assertions.assertEquals(9, tableItem.getRecordsFiltered());
		Assertions.assertEquals(9, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
		Assertions.assertTrue(tableItem.getData().getFirst().isCanWrite());

		// Check the groups
		Assertions.assertEquals(0, tableItem.getData().getFirst().getGroups().size());
	}

	/**
	 * No delegation for any group, but only for a company. So see only users within this company : ing(5)
	 */
	@Test
	void findAllForMyCompanyFilter() {
		initSpringSecurityContext("assist");

		final var tableItem = resource.findAll("ing", null, null, newUriInfoAsc("id"));
		Assertions.assertEquals(8, tableItem.getRecordsTotal());
		Assertions.assertEquals(8, tableItem.getRecordsFiltered());
		Assertions.assertEquals(8, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
		Assertions.assertTrue(tableItem.getData().getFirst().isCanWrite());

		// Check the groups
		Assertions.assertEquals(0, tableItem.getData().getFirst().getGroups().size());
	}

	/**
	 * Whatever the managed company, if the current user sees a group, can search any user even in a different company
	 * this user can manage. <br>
	 */
	@Test
	void findAllForMyGroup() {
		initSpringSecurityContext("mmartin");
		final var tableItem = resource.findAll(null, "dig as", null, newUriInfoAsc("id"));

		// 4 users from delegate and 1 from my company
		Assertions.assertEquals(5, tableItem.getRecordsTotal());
		Assertions.assertEquals(5, tableItem.getRecordsFiltered());
		Assertions.assertEquals(5, tableItem.getData().size());

		// Check the users (from delegate)
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
		Assertions.assertFalse(tableItem.getData().getFirst().isCanWrite());
		Assertions.assertTrue(tableItem.getData().getFirst().isCanWriteGroups());
	}

	/**
	 * Whatever the managed company, if the current user sees a group, then he can search any user even in a different
	 * company this user can manage. <br>
	 */
	@Test
	void findAllForMySubGroup() {
		initSpringSecurityContext("mmartin");
		final var tableItem = resource.findAll(null, "biz agency", "fdoe2", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
		Assertions.assertFalse(tableItem.getData().getFirst().isCanWrite());
		Assertions.assertTrue(tableItem.getData().getFirst().isCanWriteGroups());

		// Check the groups
		// "Biz Agency" is visible since "mmartin" is in the parent group "
		Assertions.assertEquals(2, tableItem.getData().getFirst().getGroups().size());
		Assertions.assertEquals("Biz Agency", tableItem.getData().getFirst().getGroups().getFirst().getName());
		Assertions.assertTrue(tableItem.getData().getFirst().getGroups().getFirst().isCanWrite());
		Assertions.assertEquals("DIG RHA", tableItem.getData().getFirst().getGroups().get(1).getName());
		Assertions.assertFalse(tableItem.getData().getFirst().getGroups().get(1).isCanWrite());
	}

	@Test
	void findAllFullAscCompany() {
		initSpringSecurityContext("fdaugan");
		final var tableItem = resource.findAll(null, null, null, newUriInfoAsc("company"));

		// 8 from delegate, 7 from my company
		Assertions.assertEquals(15, tableItem.getRecordsTotal());
		Assertions.assertEquals(15, tableItem.getRecordsFiltered());
		Assertions.assertEquals(15, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
	}

	@Test
	void findAllFullDescCompany() {
		final var tableItem = resource.findAll(null, null, null, newUriInfoDesc("company"));
		Assertions.assertEquals(16, tableItem.getRecordsTotal());
		Assertions.assertEquals(16, tableItem.getRecordsFiltered());
		Assertions.assertEquals(16, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("flast0", tableItem.getData().getFirst().getId());
		Assertions.assertEquals("socygan", tableItem.getData().getFirst().getCompany());
		Assertions.assertEquals("fdaugan", tableItem.getData().get(6).getId());
		Assertions.assertEquals("ligoj", tableItem.getData().get(6).getCompany());
	}

	@Test
	void findAllFullAscLastName() {
		initSpringSecurityContext("fdaugan");
		final var tableItem = resource.findAll(null, null, null, newUriInfoAsc("lastName"));

		// 8 from delegate, 7 from my company
		Assertions.assertEquals(15, tableItem.getRecordsTotal());
		Assertions.assertEquals(15, tableItem.getRecordsFiltered());
		Assertions.assertEquals(15, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().get(3).getId());
	}

	@Test
	void findAllMemberDifferentCase() {
		final var tableItem = resource.findAll("LigoJ", "ProductioN", "mmarTIN",
				newUriInfoAsc("lastName"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("mmartin", tableItem.getData().getFirst().getId());
	}

	/**
	 * No available delegate for the current user -> 0
	 */
	@Test
	void findAllNoRight() {
		initSpringSecurityContext("any");

		final var tableItem = resource.findAll(null, null, null, newUriInfoAsc("id"));
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
		final var tableItem = resource.findAll(null, null, "fdoe2", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		// Check the users
		Assertions.assertEquals("fdoe2", tableItem.getData().getFirst().getId());
		Assertions.assertFalse(tableItem.getData().getFirst().isCanWrite());

		// Check the groups
		Assertions.assertEquals(1, tableItem.getData().getFirst().getGroups().size());
		Assertions.assertEquals("Biz Agency", tableItem.getData().getFirst().getGroups().getFirst().getName());
		Assertions.assertFalse(tableItem.getData().getFirst().getGroups().getFirst().isCanWrite());
	}

	/**
	 * Add filter by group, but this group does not exist/not visible. No match.
	 */
	@Test
	void findAllFilteredNonExistingGroup() {
		initSpringSecurityContext("fdaugan");
		final var tableItem = resource.findAll(null, "any", null, newUriInfoAsc("id"));
		Assertions.assertEquals(0, tableItem.getRecordsTotal());
	}

	@Test
	void createUserAlreadyExists() {
		final var user = new UserOrgEditionVo();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flast12@ing.com");
		final var groups = new ArrayList<String>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.create(user)), "id", "already-exist");
	}

	@Test
	void deleteUserNoDelegateCompany() {
		initSpringSecurityContext("mmartin");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete("flast1")), "id", "read-only");
	}

	@Test
	void deleteLastMember() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete("mmartin")), "id", "last-member-of-group");
	}

	@Test
	void deleteUserNoDelegateWriteCompany() {
		initSpringSecurityContext("mtuyer");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete("flast1")), "id", "read-only");
	}

	@Test
	void mergeUserNoChange() {
		final var user2 = getUser().findById("flast1");
		Assertions.assertNull(user2.getDepartment());
		Assertions.assertNull(user2.getLocalId());

		resource.mergeUser(user2, new UserOrg());
		Assertions.assertNull(user2.getDepartment());
		Assertions.assertNull(user2.getLocalId());
	}

	@Test
	void mergeUser() {
		final var user2 = getUser().findById("flast1");
		Assertions.assertNull(user2.getDepartment());
		Assertions.assertNull(user2.getLocalId());

		final var newUser = new UserOrg();
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
		final var userEdit = new UserOrgEditionVo();
		userEdit.setId("flast1");
		userEdit.setFirstName("FirstA");
		userEdit.setLastName("LastA");
		userEdit.setCompany("ing");
		userEdit.setMail("flasta@ing.com");
		final var groups = new ArrayList<String>();
		groups.add("dig rha");
		userEdit.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(userEdit);
		final var tableItem = resource.findAll(null, null, "flast1", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final var user = tableItem.getData().getFirst();
		Assertions.assertEquals("flast1", user.getId());
		Assertions.assertEquals("Firsta", user.getFirstName());
		Assertions.assertEquals("Lasta", user.getLastName());
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("flasta@ing.com", user.getMails().getFirst());
		Assertions.assertEquals(1, user.getGroups().size());
		Assertions.assertEquals("DIG RHA", user.getGroups().getFirst().getName());

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
		final var userEdit = new UserOrgEditionVo();
		userEdit.setId("jlast3");
		userEdit.setFirstName("John31");
		userEdit.setLastName("Last3");
		userEdit.setCompany("ing");
		userEdit.setMail("john3.last3@ing.com");
		userEdit.setGroups(null);
		initSpringSecurityContext("assist");
		resource.update(userEdit);
		var tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		var user = tableItem.getData().getFirst();
		Assertions.assertEquals("jlast3", user.getId());
		Assertions.assertEquals("John31", user.getFirstName());
		Assertions.assertEquals("Last3", user.getLastName());
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("john3.last3@ing.com", user.getMails().getFirst());
		Assertions.assertEquals(0, user.getGroups().size());
		rollbackUser();
	}

	@Test
	void updateLastName() {
		// Last name change only
		final var userEdit = new UserOrgEditionVo();
		userEdit.setId("jlast3");
		userEdit.setFirstName("John31");
		userEdit.setLastName("Last31");
		userEdit.setCompany("ing");
		userEdit.setMail("john3.last3@ing.com");
		userEdit.setGroups(Collections.singleton("DIG RHA"));
		resource.update(userEdit);
		var tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		var user = tableItem.getData().getFirst();
		Assertions.assertEquals("jlast3", user.getId());
		Assertions.assertEquals("John31", user.getFirstName());
		Assertions.assertEquals("Last31", user.getLastName());
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("john3.last3@ing.com", user.getMails().getFirst());
		Assertions.assertEquals(1, user.getGroups().size());
		rollbackUser();
	}

	@Test
	void updateMail() {
		// Mail change only
		final var userEdit = new UserOrgEditionVo();
		userEdit.setId("jlast3");
		userEdit.setFirstName("John31");
		userEdit.setLastName("Last31");
		userEdit.setCompany("ing");
		userEdit.setMail("john31.last31@ing.com");
		userEdit.setGroups(Collections.singleton("DIG RHA"));
		resource.update(userEdit);
		var tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final var user = tableItem.getData().getFirst();
		Assertions.assertEquals("jlast3", user.getId());
		Assertions.assertEquals("John31", user.getFirstName());
		Assertions.assertEquals("Last31", user.getLastName());
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("john31.last31@ing.com", user.getMails().getFirst());
		Assertions.assertEquals(1, user.getGroups().size());
		rollbackUser();
	}

	@Test
	void updateUserChangeCompanyAndBackAgain() {
		Assertions.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com",
				toDn(getContext("flast0")));

		final var user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0"); // Unchanged
		user.setLastName("Last0"); // Unchanged
		user.setCompany("ing"); // Previous is "socygan"
		user.setMail("first0.last0@socygan.fr"); // Unchanged
		final var groups = new ArrayList<String>();
		user.setGroups(groups);
		initSpringSecurityContext("assist");
		resource.update(user);

		// Check the new DN and company everywhere
		Assertions.assertEquals("uid=flast0,ou=ing,ou=external,ou=people,dc=sample,dc=com", toDn(getContext("flast0")));
		Assertions.assertEquals("ing",
				resource.findAll(null, null, "flast0", newUriInfo()).getData().getFirst().getCompany());
		Assertions.assertEquals("ing", getUser().findByIdNoCache("flast0").getCompany());
		Assertions.assertEquals("ing", getUser().findById("flast0").getCompany());

		user.setCompany("socygan"); // Previous is "socygan"
		resource.update(user);

		// Check the old DN and company everywhere
		Assertions.assertEquals("uid=flast0,ou=socygan,ou=external,ou=people,dc=sample,dc=com",
				toDn(getContext("flast0")));
		Assertions.assertEquals("socygan",
				resource.findAll(null, null, "flast0", newUriInfo()).getData().getFirst().getCompany());
		Assertions.assertEquals("socygan", getUser().findByIdNoCache("flast0").getCompany());
		Assertions.assertEquals("socygan", getUser().findById("flast0").getCompany());
	}

	@Test
	void updateUserCompanyNotExists() {
		final var user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("any");
		user.setMail("flasta@ing.com");
		final var groups = new ArrayList<String>();
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserGroupNotExists() {
		final var user = new UserOrgEditionVo();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final var groups = new ArrayList<String>();
		groups.add("any");
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "group", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoChange() {
		final var userEdit = new UserOrgEditionVo();
		userEdit.setId("jlast3");
		userEdit.setFirstName("John3");
		userEdit.setLastName("Last3");
		userEdit.setCompany("ing");
		userEdit.setMail("jlast3@ing.com");
		final var groups = new ArrayList<String>();
		groups.add("dig rha");
		userEdit.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		resource.update(userEdit);
		final var tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		final var user = tableItem.getData().getFirst();
		Assertions.assertEquals("jlast3", user.getId());
		Assertions.assertEquals("John3", user.getFirstName());
		Assertions.assertEquals("Last3", user.getLastName());
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("jlast3@ing.com", user.getMails().getFirst());
		Assertions.assertEquals(1, user.getGroups().size());
		Assertions.assertEquals("DIG RHA", user.getGroups().getFirst().getName());
	}

	@Test
	void updateUserNoDelegate() {
		final var user = new UserOrgEditionVo();
		user.setId("flast1");
		user.setFirstName("FirstW");
		user.setLastName("LastW");
		user.setCompany("ing");
		user.setMail("flastw@ing.com");
		final var groups = new ArrayList<String>();
		groups.add("dig rha");
		user.setGroups(groups);
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateNotVisibleTargetCompany() {
		final var user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.last0@socygan.fr");
		final var groups = new ArrayList<String>();
		groups.add("Biz Agency");
		user.setGroups(groups);
		initSpringSecurityContext("mlavoine");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoDelegateCompany() {
		final var user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("socygan");
		user.setMail("flasta@ing.com");
		final var groups = new ArrayList<String>();
		user.setGroups(groups);
		initSpringSecurityContext("any");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoDelegateCompanyChangeFirstName() {
		final var user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("FirstA");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.last0@socygan.fr");
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoDelegateCompanyChangeMail() {
		final var user = new UserOrgEditionVo();
		user.setId("flast0");
		user.setFirstName("First0");
		user.setLastName("Last0");
		user.setCompany("socygan");
		user.setMail("first0.lastA@socygan.fr");
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "company", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNoDelegateCompanyNoChange() {
		final var user = new UserOrgEditionVo();
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
		final var user = new UserOrgEditionVo();
		user.setId("flast1");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final var groups = new ArrayList<String>();
		groups.add("dig sud ouest"); // no right on this group
		user.setGroups(groups);
		initSpringSecurityContext("fdaugan");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "group", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateUserNotExists() {
		final var user = new UserOrgEditionVo();
		user.setId("flast11");
		user.setFirstName("FirstA");
		user.setLastName("LastA");
		user.setCompany("ing");
		user.setMail("flasta@ing.com");
		final var groups = new ArrayList<String>();
		user.setGroups(groups);
		initSpringSecurityContext("assist");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(user)), "id", BusinessException.KEY_UNKNOWN_ID);
	}

	/**
	 * Add a group to user having already some groups but not visible from the current user.
	 */
	@Test
	void updateUserAddGroup() {
		// Pre-condition, check the user "wuser", has not yet the group "DIG
		// RHA" we want to be added by "fdaugan"
		initSpringSecurityContext("fdaugan");
		final var initialResultsFromUpdater = resource.findAll(null, null, "wuser",
				newUriInfoAsc("id"));
		Assertions.assertEquals(1, initialResultsFromUpdater.getRecordsTotal());
		Assertions.assertEquals(1, initialResultsFromUpdater.getData().getFirst().getGroups().size());
		Assertions.assertEquals("Biz Agency Manager",
				initialResultsFromUpdater.getData().getFirst().getGroups().getFirst().getName());

		// Pre-condition, check the user "wuser", has no group visible by
		// "assist"
		initSpringSecurityContext("assist");
		final var assisteResult = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assertions.assertEquals(1, assisteResult.getRecordsTotal());
		Assertions.assertEquals(0, assisteResult.getData().getFirst().getGroups().size());

		// Pre-condition, check the user "wuser", "Biz Agency Manager" is not
		// visible by "mtuyer"
		initSpringSecurityContext("mtuyer");
		final var usersFromOtherGroupManager = resource.findAll(null, null, "wuser",
				newUriInfoAsc("id"));
		Assertions.assertEquals(1, usersFromOtherGroupManager.getRecordsTotal());
		Assertions.assertEquals(0, usersFromOtherGroupManager.getData().getFirst().getGroups().size());

		// Add a new valid group "DIG RHA" to "wuser" by "fdaugan"
		initSpringSecurityContext("fdaugan");
		final var user = new UserOrgEditionVo();
		user.setId("wuser");
		user.setFirstName("William");
		user.setLastName("User");
		user.setCompany("ing");
		user.setMail("wuser.wuser@ing.fr");
		final var groups = new ArrayList<String>();
		groups.add("DIG RHA");
		groups.add("Biz Agency Manager");
		user.setGroups(groups);
		resource.update(user);

		// Check the group "DIG RHA" is added and
		final var tableItem = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());
		Assertions.assertEquals(2, tableItem.getData().getFirst().getGroups().size());
		Assertions.assertEquals("Biz Agency Manager", tableItem.getData().getFirst().getGroups().getFirst().getName());
		Assertions.assertEquals("DIG RHA", tableItem.getData().getFirst().getGroups().get(1).getName());

		// Check the user "wuser", still has no group visible by "assist"
		initSpringSecurityContext("assist");
		final var assisteResult2 = resource.findAll(null, null, "wuser", newUriInfoAsc("id"));
		Assertions.assertEquals(1, assisteResult2.getRecordsTotal());
		Assertions.assertEquals(0, assisteResult2.getData().getFirst().getGroups().size());

		// Check the user "wuser", still has the group "DIG RHA" visible by
		// "mtuyer"
		initSpringSecurityContext("mtuyer");
		final var usersFromOtherGroupManager2 = resource.findAll(null, null, "wuser",
				newUriInfoAsc("id"));
		Assertions.assertEquals(1, usersFromOtherGroupManager2.getRecordsTotal());
		Assertions.assertEquals("DIG RHA", usersFromOtherGroupManager2.getData().getFirst().getGroups().getFirst().getName());

		// Restore the old state
		initSpringSecurityContext("fdaugan");
		final var user2 = new UserOrgEditionVo();
		user2.setId("wuser");
		user2.setFirstName("William");
		user2.setLastName("User");
		user2.setCompany("ing");
		user2.setMail("wuser.wuser@ing.fr");
		final var groups2 = new ArrayList<String>();
		groups2.add("Biz Agency Manager");
		user.setGroups(groups2);
		resource.update(user);
		final var initialResultsFromUpdater2 = resource.findAll(null, null, "wuser",
				newUriInfoAsc("id"));
		Assertions.assertEquals(1, initialResultsFromUpdater2.getRecordsTotal());
		Assertions.assertEquals(1, initialResultsFromUpdater2.getData().getFirst().getGroups().size());
		Assertions.assertEquals("Biz Agency Manager",
				initialResultsFromUpdater2.getData().getFirst().getGroups().getFirst().getName());
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
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete("wuser")), "id", "read-only");
	}

	@Test
	void deleteUserNotExists() {
		initSpringSecurityContext("assist");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete("any")), "id", BusinessException.KEY_UNKNOWN_ID);
	}

	@Test
	void updateMembership() {
		final var repository = new UserSqlRepository();
		repository.setGroupRepository(Mockito.mock(GroupSqlRepository.class));
		final var groups = new ArrayList<String>();
		groups.add("dig rha");
		final var user = new UserOrg();
		final var oldGroups = new ArrayList<String>();
		user.setGroups(oldGroups);
		user.setId("flast1");
		user.setCompany("ing");
		repository.updateMembership(groups, user);
	}

	@Test
	void convertUserRaw() {
		final var user = getUser().toUser("jdoe5");
		checkRawUser(user);
		Assertions.assertNotNull(user.getGroups());
		Assertions.assertEquals(1, user.getGroups().size());
	}

	@Test
	void convertUserNotExist() {
		final var user = getUser().toUser("any");
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

		final var tableItem = resource.findAll("ligoj", null, null, newUriInfoAsc("id"));

		// 7 users from company 'ligoj', 0 from delegate
		Assertions.assertEquals(7, tableItem.getRecordsTotal());
		Assertions.assertEquals(7, tableItem.getRecordsFiltered());

		// Check the users
		Assertions.assertEquals("admin-test", tableItem.getData().getFirst().getId());
	}

	/**
	 * When the requested company does not exist, return an empty set.
	 */
	@Test
	void findAllUnknowFilteredCompany() {
		final var tableItem = resource.findAll("any", null, null, newUriInfoAsc("id"));
		Assertions.assertEquals(0, tableItem.getRecordsTotal());
		Assertions.assertEquals(0, tableItem.getRecordsFiltered());
	}

	@Test
	void setIamProviderForTest() {
		// There, for test by other plugin/application
		new UserOrgResource().setIamProvider(new IamProvider[] { Mockito.mock(IamProvider.class) });
	}

	@Autowired
	protected UserOrgResource resource;

	@Override
	@BeforeEach
	protected void prepareData() throws IOException {
		super.prepareData();
		persistEntities("csv", new Class<?>[] { DelegateOrg.class }, StandardCharsets.UTF_8);

		// Force the cache to be created
		getUser().findAll();
	}

	@Override
	protected UriInfo newUriInfoAsc(final String ascProperty) {
		final var uriInfo = newUriInfo();
		uriInfo.getQueryParameters().add(DataTableAttributes.PAGE_LENGTH, "100");
		uriInfo.getQueryParameters().add(DataTableAttributes.SORTED_COLUMN, "2");
		uriInfo.getQueryParameters().add("columns[2][data]", ascProperty);
		uriInfo.getQueryParameters().add(DataTableAttributes.SORT_DIRECTION, "asc");
		return uriInfo;
	}

	@Override
	protected UriInfo newUriInfoDesc(final String property) {
		final var uriInfo = newUriInfo();
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
		Assertions.assertEquals("fabrice.daugan@sample.com", user.getMails().getFirst());
		Assertions.assertEquals(1, user.getGroups().size());
		Assertions.assertEquals("Hub Paris", user.getGroups().iterator().next());
	}

	protected void rollbackUser() {
		final var userEdit = new UserOrgEditionVo();
		userEdit.setId("jlast3");
		userEdit.setFirstName("John3");
		userEdit.setLastName("Last3");
		userEdit.setCompany("ing");
		userEdit.setMail("john3.last3@ing.com");
		userEdit.setGroups(null);
		initSpringSecurityContext("assist");
		resource.update(userEdit);
		var tableItem = resource.findAll(null, null, "jlast3", newUriInfoAsc("id"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals(1, tableItem.getRecordsFiltered());
		Assertions.assertEquals(1, tableItem.getData().size());

		var user = tableItem.getData().getFirst();
		Assertions.assertEquals("jlast3", user.getId());
		Assertions.assertEquals("John3", user.getFirstName());
		Assertions.assertEquals("Last3", user.getLastName());
		Assertions.assertEquals("ing", user.getCompany());
		Assertions.assertEquals("john3.last3@ing.com", user.getMails().getFirst());
		Assertions.assertEquals(0, user.getGroups().size());
	}

	protected CacheUser getContext(final String uid) {
		return cacheUserRepository.findOneExpected(uid);
	}

	private String toDn(final CacheUser context) {
		return "uid=" + context.getId() + "," + context.getCompany().getDescription();
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
