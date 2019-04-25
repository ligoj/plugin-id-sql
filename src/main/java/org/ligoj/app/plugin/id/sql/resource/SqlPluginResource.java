/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.api.Normalizer;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.model.CacheProjectGroup;
import org.ligoj.app.model.ContainerType;
import org.ligoj.app.model.Project;
import org.ligoj.app.plugin.id.dao.CacheProjectGroupRepository;
import org.ligoj.app.plugin.id.model.ContainerScope;
import org.ligoj.app.plugin.id.resource.AbstractPluginIdResource;
import org.ligoj.app.plugin.id.resource.ContainerScopeResource;
import org.ligoj.app.plugin.id.resource.ContainerWithScopeVo;
import org.ligoj.app.plugin.id.resource.GroupResource;
import org.ligoj.app.plugin.id.resource.IdentityResource;
import org.ligoj.app.plugin.id.sql.dao.GroupSqlRepository;
import org.ligoj.app.plugin.id.sql.dao.UserSqlRepository;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * SQL resource.
 */
@Path(SqlPluginResource.URL)
@Service
@Transactional
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class SqlPluginResource extends AbstractPluginIdResource<UserSqlRepository> {

	private static final String PATTERN_PROPERTY = "pattern";

	/**
	 * Plug-in key.
	 */
	public static final String URL = IdentityResource.SERVICE_URL + "/sql";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = URL.replace('/', ':').substring(1);

	/**
	 * Salt string length used to build the credential hash.
	 */
	public static final String PARAMETER_SALT_LENGTH = KEY + ":salt-length";

	/**
	 * Hash iteration count.
	 */
	public static final String PARAMETER_HASH_ITERATION = KEY + ":hash-iteration";

	/**
	 * Hash key length.
	 */
	public static final String PARAMETER_KEY_LENGTH = KEY + ":key-length";

	/**
	 * Key algorithm. such as <code>PBKDF2WithHmacSHA512</code>.
	 */
	public static final String PARAMETER_KEY_ALG = KEY + ":key-alg";

	/**
	 * Base DN where people, groups and companies are located
	 */
	public static final String PARAMETER_BASE_DN = KEY + ":base-dn";

	@Autowired
	protected GroupResource groupResource;

	@Autowired
	private ContainerScopeResource containerScopeResource;

	@Autowired
	private CacheProjectGroupRepository cacheProjectGroupRepository;

	@Autowired
	private IamProvider[] iamProvider;

	@Autowired
	protected ServicePluginLocator servicePluginLocator;

	@Autowired
	@Getter
	protected SqlPluginResource self;

	@Override
	protected UserSqlRepository getUserRepository(final String node) {
		log.info("Build SQL template for node {}", node);
		final Map<String, String> parameters = pvResource.getNodeParameters(node);

		// A new repository instance
		final UserSqlRepository repository = new UserSqlRepository();
		repository.setSaltLength(Integer.parseInt(parameters.getOrDefault(PARAMETER_SALT_LENGTH, "64"), 10));
		repository.setHashIteration(Integer.parseInt(parameters.getOrDefault(PARAMETER_HASH_ITERATION, "10"), 10));
		repository.setKeyLength(Integer.parseInt(parameters.getOrDefault(PARAMETER_KEY_LENGTH, "256"), 10));
		repository.setSecretKeyFactory(parameters.getOrDefault(PARAMETER_KEY_ALG, "PBKDF2WithHmacSHA512"));

		// Complete the bean
		context.getAutowireCapableBeanFactory().autowireBean(repository);

		return repository;
	}

	@Override
	public boolean accept(final Authentication authentication, final String node) {
		final Map<String, String> parameters = pvResource.getNodeParameters(node);
		return !parameters.isEmpty() && authentication.getName()
				.matches(StringUtils.defaultString(parameters.get(IdentityResource.PARAMETER_UID_PATTERN), ".*"));
	}

	@Override
	public void create(final int subscription) {
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);
		final String group = parameters.get(IdentityResource.PARAMETER_GROUP);
		final String parentGroup = parameters.get(IdentityResource.PARAMETER_PARENT_GROUP);
		final String ou = parameters.get(IdentityResource.PARAMETER_OU);
		final Project project = subscriptionRepository.findOne(subscription).getProject();
		final String pkey = project.getPkey();

		// Check the relationship between group, OU and project
		validateGroup(group, ou, pkey);

		// Check the relationship between group, and parent
		final String parentDn = validateAndCreateParent(group, parentGroup, ou, pkey);

		// Create the group inside the parent (OU or parent CN)
		final String groupDn = "cn=" + group + "," + parentDn;
		log.info("New Group CN would be created {} project {} and subscription {}", group, pkey);
		final GroupSqlRepository repository = getGroup();
		final GroupOrg groupSql = repository.create(groupDn, group);

		// Complete as needed the relationship between parent and this new group
		if (StringUtils.isNotBlank(parentGroup)) {
			// This group will be added as "uniqueMember" of its parent
			repository.addGroup(groupSql, parentGroup);
		}

		// Associate the project to this group in the cache
		final CacheProjectGroup projectGroup = new CacheProjectGroup();
		projectGroup.setProject(project);
		projectGroup.setGroup(repository.getCacheRepository().findOneExpected(groupSql.getId()));
		cacheProjectGroupRepository.saveAndFlush(projectGroup);
	}

	/**
	 * Validate the group against the OU and the linked project.
	 */
	private void validateGroup(final String group, final String ou, final String pkey) {
		// Check the group does not exists
		if (groupResource.findById(group) != null) {
			// This group already exists
			throw new ValidationJsonException(IdentityResource.PARAMETER_GROUP, "already-exist", "0",
					GroupResource.GROUP_ATTRIBUTE, "1", group);
		}

		// Compare the project's key with the OU, and the name of the group

		// The group must start with the target OU
		if (!startsWithAndDifferent(group, ou + "-")) {
			// This group has not a correct form
			throw new ValidationJsonException(IdentityResource.PARAMETER_GROUP, PATTERN_PROPERTY, ou + "-.+");
		}

		// The name of the group must start with the PKEY of project
		if (!group.equals(pkey) && !startsWithAndDifferent(group, pkey + "-")) {
			// This group has not a correct form
			throw new ValidationJsonException(IdentityResource.PARAMETER_GROUP, PATTERN_PROPERTY, pkey + "(-.+)?");
		}
	}

	private boolean startsWithAndDifferent(final String provided, final String expected) {
		return provided.startsWith(expected) && !provided.equals(expected);
	}

	/**
	 * Validate the parent and return its DN. OU must be normalized.
	 */
	private String validateAndCreateParent(final String group, final String parentGroup, final String ou,
			final String pkey) {
		// Check the creation mode
		if (StringUtils.isBlank(parentGroup)) {
			// Parent as not been defined, so will be the specified OU. that
			// would be created if it does not exist
			return validateAndCreateParentOu(group, ou, pkey);
		}

		// Parent has been specified, so will be another group we need to check
		return validateParentGroup(group, parentGroup);
	}

	/**
	 * Validate the group against its direct parent (a normalized OU) and return its DN.
	 */
	private String validateAndCreateParentOu(final String group, final String ou, final String pkey) {
		final ContainerScope groupType = containerScopeResource.findByName(ContainerScope.TYPE_PROJECT);

		// Build the complete normalized DN from the OU and new Group
		return "ou=" + ou + "," + groupType.getDn();
	}

	/**
	 * Validate the group against its parent and return the corresponding DN.
	 */
	private String validateParentGroup(final String group, final String parentGroup) {
		final GroupOrg parentGroupSql = groupResource.findById(parentGroup);
		if (parentGroupSql == null) {
			// The parent group does not exists
			throw new ValidationJsonException(IdentityResource.PARAMETER_PARENT_GROUP, BusinessException.KEY_UNKNOW_ID,
					parentGroup);
		}

		// Compare the group and its parent
		if (!group.startsWith(parentGroup + "-")) {
			// This sub-group has not a correct form
			throw new ValidationJsonException(IdentityResource.PARAMETER_GROUP, PATTERN_PROPERTY, parentGroup + "-.*");
		}

		// Parent will be another group, return its DN
		return parentGroupSql.getDn();
	}

	@Override
	public void link(final int subscription) {
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);

		// Validate the job settings
		validateGroup(parameters);
	}

	@Override
	public String getVersion(final Map<String, String> parameters) {
		// SQL version is fixed
		return "1";
	}

	/**
	 * Validate the group settings.
	 *
	 * @param parameters the administration parameters.
	 * @return real group name.
	 */
	protected INamableBean<String> validateGroup(final Map<String, String> parameters) {
		// Get group configuration
		final String group = parameters.get(IdentityResource.PARAMETER_GROUP);
		final ContainerWithScopeVo groupSql = groupResource.findByName(group);

		// Check the group exists
		if (groupSql == null) {
			throw new ValidationJsonException(IdentityResource.PARAMETER_GROUP, BusinessException.KEY_UNKNOW_ID, group);
		}

		// Check the group has type TYPE_PROJECT
		if (!ContainerScope.TYPE_PROJECT.equals(groupSql.getScope())) {
			// Invalid type
			throw new ValidationJsonException(IdentityResource.PARAMETER_GROUP, "group-type", group);
		}

		// Return the nice name
		final INamableBean<String> result = new NamedBean<>();
		result.setName(groupSql.getName());
		result.setId(group);
		return result;
	}

	/**
	 * Search the SQL Groups matching to the given criteria and for type "Project". Node identifier is ignored for now.
	 *
	 * @param criteria the search criteria.
	 * @return Groups matching the criteria.
	 * @see ContainerScope#TYPE_PROJECT
	 */
	@GET
	@Path("group/{node}/{criteria}")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<INamableBean<String>> findGroupsByName(@PathParam("criteria") final String criteria) {
		final List<INamableBean<String>> result = new ArrayList<>();
		final String criteriaClean = Normalizer.normalize(criteria);
		final Set<GroupOrg> visibleGroups = groupResource.getContainers();
		final List<ContainerScope> types = containerScopeResource.findAllDescOrder(ContainerType.GROUP);
		for (final GroupOrg group : visibleGroups) {
			final ContainerScope scope = groupResource.toScope(types, group);

			// Check type and criteria
			if (scope != null && ContainerScope.TYPE_PROJECT.equals(scope.getName())
					&& group.getId().contains(criteriaClean)) {
				// Return the nice name
				final INamableBean<String> bean = new NamedBean<>();
				NamedBean.copy(group, bean);
				result.add(bean);
			}
		}

		return result;
	}

	@Override
	public void delete(final int subscription, final boolean deleteRemoteData) {
		if (deleteRemoteData) {
			// Data are removed from the SQL
			final Map<String, String> parameters = subscriptionResource.getParameters(subscription);
			final String group = parameters.get(IdentityResource.PARAMETER_GROUP);

			// Check the group exists, but is not required to continue the process
			final GroupSqlRepository repository = getGroup();
			final GroupOrg groupSql = repository.findById(group);
			if (groupSql != null) {
				// Perform the deletion
				repository.delete(groupSql);
			}
		}
	}

	@Override
	@Transactional(value = TxType.NOT_SUPPORTED)
	public String getKey() {
		return KEY;
	}

	@Override
	@Transactional(value = TxType.NOT_SUPPORTED)
	public String getLastVersion() {
		return "1";
	}

	@Override
	public boolean checkStatus(final String node, final Map<String, String> parameters) {
		// Query the SQL, the user is not important, we expect no error, that's all
		self.getConfiguration(node).getUserRepository().findByIdNoCache("-any-");
		return true;
	}

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final Map<String, String> parameters) {
		final GroupOrg group = getGroup().findById(parameters.get(IdentityResource.PARAMETER_GROUP));
		if (group == null) {
			return new SubscriptionStatusWithData(false);
		}

		// Non empty group, return amount of members
		final SubscriptionStatusWithData result = new SubscriptionStatusWithData(true);
		result.put("members", group.getMembers().size());
		return result;
	}

	@CacheResult(cacheName = "id-sql-configuration")
	public boolean ensureCachedConfiguration(@CacheKey final String node) {
		return super.ensureCachedConfiguration(node);
	}

	/**
	 * Group repository provider.
	 *
	 * @return Group repository provider.
	 */
	private GroupSqlRepository getGroup() {
		return (GroupSqlRepository) iamProvider[0].getConfiguration().getGroupRepository();
	}
}
