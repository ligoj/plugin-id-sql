/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.IGroupRepository;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.dao.CacheGroupRepository;
import org.ligoj.app.iam.dao.CacheMembershipRepository;
import org.ligoj.app.iam.model.CacheGroup;
import org.ligoj.app.iam.model.CacheMembership;
import org.ligoj.app.model.ContainerType;
import org.ligoj.app.plugin.id.DnUtils;
import org.ligoj.app.plugin.id.dao.AbstractMemCacheRepository.CacheDataType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Group LDAP repository
 */
@Component
public class GroupSqlRepository extends AbstractContainerSqlRepository<GroupOrg, CacheGroup>
		implements IGroupRepository {

	/**
	 * Default DN member for new group. This is required for some LDAP implementation where "uniqueMember" attribute is
	 * required for "groupOfUniqueNames" class.
	 *
	 * @see <a href="https://msdn.microsoft.com/en-us/library/ms682261(v=vs.85).aspx">MSDN</a>
	 * @see <a href="https://tools.ietf.org/html/rfc4519#page-19">IETF</a>
	 */
	public static final String DEFAULT_MEMBER_DN = "uid=none";

	@Autowired
	private CacheGroupRepository cacheGroupRepository;

	@Autowired
	private CacheMembershipRepository cacheMembershipRepository;

	/**
	 * Default constructor for a container of type {@link ContainerType#GROUP}
	 */
	public GroupSqlRepository() {
		super(ContainerType.GROUP);
	}

	@Override
	public CacheGroupRepository getCacheRepository() {
		return cacheGroupRepository;
	}

	/**
	 * Fetch and return all normalized groups. Note the result use cache, so does not reflect the current state of LDAP.
	 * Cache manager is involved.
	 *
	 * @return the groups. Key is the normalized name, Value is the corresponding LDAP group containing real CN, DN and
	 *         normalized UID members.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Map<String, GroupOrg> findAll() {
		return (Map<String, GroupOrg>) repository.getData().get(CacheDataType.GROUP);
	}

	/**
	 * Fetch and return all normalized groups. Note the result use cache, so does not reflect the current state of LDAP.
	 * LDAP.
	 *
	 * @return the groups. Key is the normalized name, Value is the corresponding LDAP group containing real CN, DN and
	 *         normalized UID members.
	 */
	@Override
	public Map<String, GroupOrg> findAllNoCache() {
		final Map<String, GroupOrg> groups = new HashMap<>();

		// Collect all groups
		for (final CacheGroup groupRaw : cacheGroupRepository.findAll()) {
			final GroupOrg group = newContainer(groupRaw.getDescription(), groupRaw.getName());
			groups.put(group.getId(), group);
		}

		// Complete with memberships
		for (final CacheMembership membership : cacheMembershipRepository.findAll()) {
			final GroupOrg group = groups.get(membership.getGroup().getId());
			if (membership.getUser() == null) {
				// Sub-group membership
				group.getSubGroups().add(membership.getSubGroup().getId());

				// Complete the inverse relationship
				groups.get(membership.getSubGroup().getId()).getGroups().add(group.getId());
			} else {
				// User membership
				group.getMembers().add(membership.getUser().getId());
			}
		}
		return groups;
	}

	private void removeFromJavaCache(final GroupOrg group) {
		// Remove the sub groups from LDAP
		new ArrayList<>(group.getSubGroups()).stream().map(this::findById).filter(Objects::nonNull)
				.forEach(child -> removeGroup(child, group.getId()));

		// Remove from the parent LDAP groups
		new ArrayList<>(group.getGroups()).forEach(parent -> removeGroup(group, parent));

		// Also, update the raw cache
		findAll().remove(group.getId());
	}

	/**
	 * Delete the given group. There is no synchronized block, so error could occur; this is assumed for performance
	 * purpose.
	 *
	 * @param group
	 *            the LDAP group.
	 */
	@Override
	public void delete(final GroupOrg group) {

		/*
		 * Remove from this group, all groups within (sub DN) this group. This operation is needed since we are not
		 * rebuilding the cache from the cache it-self. This save a lot of computations.
		 */
		findAll().values().stream().filter(g -> DnUtils.equalsOrParentOf(group.getDn(), g.getDn()))
				.collect(Collectors.toList()).forEach(this::removeFromJavaCache);

		// Also, update the cache
		repository.delete(group);
	}

	@Override
	public void empty(final GroupOrg group, final Map<String, UserOrg> users) {
		repository.empty(group, users);
	}

	@Override
	public GroupOrg create(final String dn, final String cn) {
		return repository.create(super.create(dn, cn));
	}

	@Override
	public void addUser(final UserOrg user, final String group) {
		// Add to Java cache and to SQL cache
		repository.addUserToGroup(user, findById(group));
	}

	@Override
	public void addGroup(final GroupOrg subGroup, final String toGroup) {
		// Add to Java cache and to SQL cache
		repository.addGroupToGroup(subGroup, findById(toGroup));
	}

	@Override
	public void removeUser(final UserOrg user, final String group) {
		// Remove from Java cache and from SQL cache
		repository.removeUserFromGroup(user, findById(group));
	}

	/**
	 * Remove a group from another group. Cache is updated. There is no deletion.
	 *
	 * @param subGroup
	 *            {@link GroupOrg} to remove.
	 * @param group
	 *            CN of the group to update.
	 */
	public void removeGroup(final GroupOrg subGroup, final String group) {
		// Remove from Java cache and from SQL cache
		repository.removeGroupFromGroup(subGroup, findById(group));
	}

	@Override
	protected GroupOrg newContainer(final String dn, final String cn) {
		return new GroupOrg(dn.toLowerCase(Locale.ENGLISH), cn, new HashSet<>());
	}

	@Override
	public void addAttributes(final String dn, final String attribute, final Collection<String> values) {
		// Nothing to do
	}

	@Override
	public GroupOrg findByDepartment(final String department) {
		return null;
	}
}
