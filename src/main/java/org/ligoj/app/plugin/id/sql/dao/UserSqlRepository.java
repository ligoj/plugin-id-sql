/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.dao;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.naming.Name;
import javax.naming.ldap.LdapName;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.RandomStringGenerator;
import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.GroupOrg;
import org.ligoj.app.iam.IUserRepository;
import org.ligoj.app.iam.SimpleUser;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.iam.dao.CacheUserRepository;
import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.app.plugin.id.dao.AbstractMemCacheRepository.CacheDataType;
import org.ligoj.app.plugin.id.model.CompanyComparator;
import org.ligoj.app.plugin.id.model.FirstNameComparator;
import org.ligoj.app.plugin.id.model.LastNameComparator;
import org.ligoj.app.plugin.id.model.LoginComparator;
import org.ligoj.app.plugin.id.model.MailComparator;
import org.ligoj.app.plugin.id.sql.model.UserSqlCredential;
import org.ligoj.bootstrap.core.DateUtils;
import org.ligoj.bootstrap.core.json.InMemoryPagination;
import org.ligoj.bootstrap.core.resource.TechnicalException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * User SQL repository
 */
@Slf4j
public class UserSqlRepository implements IUserRepository {

	private static final String USER_ID = "user.id";

	private static final Map<String, Comparator<UserOrg>> COMPARATORS = new HashMap<>();

	/**
	 * User comparator for ordering
	 */
	public static final Comparator<UserOrg> DEFAULT_COMPARATOR = new LoginComparator();
	private static final Sort.Order DEFAULT_ORDER = new Sort.Order(Direction.ASC, "id");

	/**
	 * Shared random string generator used for temporary passwords.
	 */
	public static final RandomStringGenerator GENERATOR = new RandomStringGenerator.Builder()
			.filteredBy(c -> CharUtils.isAsciiAlphanumeric(Character.toChars(c)[0])).build();

	/**
	 * Base DN for internal people. Should be a subset of people, so including {@link #peopleBaseDn}
	 */
	@Getter
	private String peopleInternalBaseDn = "ou=internal,ou=people";

	/**
	 * Salt string length used to build the credential hash.
	 *
	 * @see #hashPassword(char[], byte[], int, int)
	 */
	@Setter
	private int saltLength = 64;

	/**
	 * Hash iteration count.
	 *
	 * @see #hashPassword(char[], byte[], int, int)
	 */
	@Setter
	private int hashIteration = 10;

	/**
	 * Hash key length.
	 *
	 * @see #hashPassword(char[], byte[], int, int)
	 */
	@Setter
	private int keyLength = 256;

	/**
	 * If the code generates a NoSuchAlgorithmException, replace PBKDF2WithHmacSHA512 with PBKDF2WithHmacSHA1. Both are
	 * adequate to the task but you may be criticized when people see "SHA1" in the specification (SHA1 can be unsafe
	 * outside of the context of PBKDF2).
	 */
	@Setter
	private String secretKeyFactory = "PBKDF2WithHmacSHA512";

	@Autowired
	private InMemoryPagination inMemoryPagination;

	@Getter
	@Setter
	@Autowired
	private GroupSqlRepository groupRepository;

	@Getter
	@Setter
	@Autowired
	private CompanySqlRepository companyRepository;

	@Autowired
	private UserSqlCredentialRepository credentialRepository;

	@Autowired
	private CacheUserRepository cacheUserRepository;

	@Autowired
	private CacheSqlRepository cacheRepository;

	static {
		COMPARATORS.put("company", new CompanyComparator());
		COMPARATORS.put("id", new LoginComparator());
		COMPARATORS.put("firstName", new FirstNameComparator());
		COMPARATORS.put("lastName", new LastNameComparator());
		COMPARATORS.put("mail", new MailComparator());
	}

	@Override
	public UserOrg create(final UserOrg user) {
		user.setDn(buildDn(user).toString());
		// Return the original entry with updated DN
		return cacheRepository.create(user);
	}

	@Override
	public UserOrg findByIdNoCache(final String login) {
		return Optional.ofNullable(cacheUserRepository.findOne(login)).map(this::toUser).orElse(null);
	}

	@Override
	public List<UserOrg> findAllBy(final String attribute, final String value) {
		return cacheUserRepository.findAllBy(attribute, value).stream().map(this::toUser).collect(Collectors.toList());
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, UserOrg> findAll() {
		return (Map<String, UserOrg>) cacheRepository.getData().get(CacheDataType.USER);
	}

	/**
	 * Return all user entries.
	 *
	 * @param groups The existing groups. They will be be used to complete the membership of each returned user.
	 * @return all user entries. Key is the user login.
	 */
	@Override
	public Map<String, UserOrg> findAllNoCache(final Map<String, GroupOrg> groups) {

		// Fetch users and their direct attributes
		final List<UserOrg> users = cacheUserRepository.findAll().stream().map(this::toUser)
				.collect(Collectors.toList());

		// Index the users by the identifier and update the memberships of this user
		final Map<String, UserOrg> result = new HashMap<>();
		for (final UserOrg user : users) {
			user.setGroups(new ArrayList<>());
			result.put(user.getId(), user);
			groups.values().stream().filter(g -> g.getMembers().contains(user.getId()))
					.forEach(g -> user.getGroups().add(g.getId()));
		}
		return result;
	}

	@Override
	public String toDn(UserOrg newUser) {
		return buildDn(newUser).toString();
	}

	/**
	 * Return DN from entry.
	 *
	 * @param entry SQL entry to convert to DN.
	 * @return DN from entry.
	 */
	public Name buildDn(final UserOrg entry) {
		return companyRepository
				.newLdapName(buildDn(entry.getId(), companyRepository.findById(entry.getCompany()).getDn()));
	}

	/**
	 * Return DN from entry.
	 *
	 * @param login     The user login to create.
	 * @param companyDn The target company DN.
	 * @return DN from entry.
	 */
	private String buildDn(final String login, final String companyDn) {
		return "uid=" + login + "," + companyDn;
	}

	private UserOrg toUser(final CacheUser entity) {
		final UserOrg user = new UserOrg();
		user.setDn(buildDn(entity.getId(), entity.getCompany().getDescription()));
		user.setLastName(entity.getLastName());
		user.setFirstName(entity.getFirstName());
		user.setId(entity.getId());
		user.setCompany(entity.getCompany().getId());

		// Copy the credential data
		final UserSqlCredential credential = credentialRepository.findBy(USER_ID, entity.getId());
		user.setSecured(Optional.ofNullable(credential).map(UserSqlCredential::getValue).orElse(null) != null);
		user.setMails(Arrays.asList(StringUtils.split(StringUtils.defaultIfBlank(entity.getMails(), ""), ",;")));
		user.setLocked(Optional.ofNullable(credential).map(UserSqlCredential::getLocked).orElse(null));
		user.setLockedBy(Optional.ofNullable(credential).map(UserSqlCredential::getLockedBy).orElse(null));
		return user;
	}

	@Override
	public Page<UserOrg> findAll(final Collection<GroupOrg> requiredGroups, final Set<String> companies,
			final String criteria, final Pageable pageable) {
		// Create the set with the right comparator
		final List<Sort.Order> orders = IteratorUtils
				.toList(ObjectUtils.defaultIfNull(pageable.getSort(), new ArrayList<Sort.Order>()).iterator());
		orders.add(DEFAULT_ORDER);
		final Sort.Order order = orders.get(0);
		Comparator<UserOrg> comparator = ObjectUtils.defaultIfNull(COMPARATORS.get(order.getProperty()),
				DEFAULT_COMPARATOR);
		if (order.getDirection() == Direction.DESC) {
			comparator = Collections.reverseOrder(comparator);
		}
		final Set<UserOrg> result = new TreeSet<>(comparator);

		// Filter the users traversing firstly the required groups and their members,
		// the companies, then the criteria
		final Map<String, UserOrg> users = findAll();
		if (requiredGroups == null) {
			// No constraint on group
			addFilteredByCompaniesAndPattern(users.keySet(), companies, criteria, result, users);
		} else {
			// User must be within one the given groups
			for (final GroupOrg requiredGroup : requiredGroups) {
				addFilteredByCompaniesAndPattern(requiredGroup.getMembers(), companies, criteria, result, users);
			}
		}

		// Apply in-memory pagination
		return inMemoryPagination.newPage(result, pageable);
	}

	/**
	 * Add the members to the result if they match to the required company and the pattern.
	 */
	private void addFilteredByCompaniesAndPattern(final Set<String> members, final Set<String> companies,
			final String criteria, final Set<UserOrg> result, final Map<String, UserOrg> users) {
		// Filter by company for each members
		for (final String member : members) {
			final UserOrg userSql = users.get(member);

			// User is always found since #findAll() ensure the members of the groups exist
			addFilteredByCompaniesAndPattern(companies, criteria, result, userSql);
		}

	}

	private void addFilteredByCompaniesAndPattern(final Set<String> companies, final String criteria,
			final Set<UserOrg> result, final UserOrg userSql) {
		final List<CompanyOrg> userCompanies = companyRepository.findAll().get(userSql.getCompany()).getCompanyTree();
		if (userCompanies.stream().map(CompanyOrg::getId).anyMatch(companies::contains)) {
			addFilteredByPattern(criteria, result, userSql);
		}
	}

	private void addFilteredByPattern(final String criteria, final Set<UserOrg> result, final UserOrg userSql) {
		if (criteria == null || matchPattern(userSql, criteria)) {
			// Company and pattern match
			result.add(userSql);
		}
	}

	/**
	 * Indicates the given user match to the given pattern.
	 */
	private boolean matchPattern(final UserOrg userSql, final String criteria) {
		return StringUtils.containsIgnoreCase(userSql.getFirstName(), criteria)
				|| StringUtils.containsIgnoreCase(userSql.getLastName(), criteria)
				|| StringUtils.containsIgnoreCase(userSql.getId(), criteria)
				|| !userSql.getMails().isEmpty() && StringUtils.containsIgnoreCase(userSql.getMails().get(0), criteria);
	}

	@Override
	public void updateMembership(final Collection<String> groups, final UserOrg user) {
		// Add new groups
		addUserToGroups(user, CollectionUtils.subtract(groups, user.getGroups()));

		// Remove old groups
		removeUserFromGroups(user, CollectionUtils.subtract(user.getGroups(), groups));
	}

	/**
	 * Add the user from the given groups. Cache is also updated.
	 *
	 * @param user   The user to add to the given groups.
	 * @param groups the groups to add, normalized.
	 */
	protected void addUserToGroups(final UserOrg user, final Collection<String> groups) {
		groups.forEach(g -> groupRepository.addUser(user, g));
	}

	/**
	 * Remove the user from the given groups.Cache is also updated.
	 *
	 * @param user   The user to remove from the given groups.
	 * @param groups the groups to remove, normalized.
	 */
	protected void removeUserFromGroups(final UserOrg user, final Collection<String> groups) {
		groups.forEach(g -> groupRepository.removeUser(user, g));
	}

	@Override
	public void updateUser(final UserOrg user) {
		final UserOrg userInternal = findById(user.getId());
		user.copy((SimpleUser) userInternal);
		userInternal.setMails(user.getMails());
		cacheRepository.update(user);
	}

	@Override
	public void delete(final UserOrg user) {
		// Remove attached credentials
		credentialRepository.deleteAllBy(USER_ID, user.getId());

		// Remove user from all groups
		removeUserFromGroups(user, user.getGroups());

		// Remove the user from the cache
		cacheRepository.delete(user);

	}

	@Override
	public void lock(final String principal, final UserOrg user) {
		lock(principal, user, false);
	}

	@Override
	public void isolate(final String principal, final UserOrg user) {
		if (user.getIsolated() == null) {
			// Not yet isolated
			lock(principal, user, true);
			final String previousCompany = user.getCompany();
			move(user, companyRepository.findById(companyRepository.getQuarantineCompany()));
			user.setIsolated(previousCompany);
		}
	}

	@Override
	public void restore(final UserOrg user) {
		if (user.getIsolated() != null) {
			move(user, companyRepository.findById(user.getIsolated()));
			user.setIsolated(null);
			unlock(user, true);
		}
	}

	@Override
	public void move(final UserOrg user, final CompanyOrg company) {
		final LdapName newDn = companyRepository.newLdapName(buildDn(user.getId(), company.getDn()));
		user.setDn(newDn.toString());
		user.setCompany(company.getId());
		cacheRepository.update(user);
	}

	/**
	 * Lock an user :
	 * <ul>
	 * <li>Clear the password to prevent new authentication</li>
	 * <li>Set the disabled flag.</li>
	 * </ul>
	 *
	 * @param principal Principal user requesting the lock.
	 * @param user      The SQL user to disable.
	 * @param isolate   When <code>true</code>, the user will be isolated in addition.
	 */
	private void lock(final String principal, final UserOrg user, final boolean isolate) {
		if (user.getLockedBy() == null) {
			// Not yet locked
			final UserSqlCredential credential = createAsNeeded(user);
			credential.setLocked(DateUtils.newCalendar().getTime());
			credential.setLockedBy(principal);
			credential.setValue(null);
			credential.setSalt(null);

			if (isolate) {
				credential.setIsolated(user.getCompany());
			}

			// Also update the locked date
			user.setLocked(credential.getLocked());
			user.setLockedBy(principal);
		}
	}

	private UserSqlCredential createAsNeeded(final UserOrg user) {
		return Optional.ofNullable(credentialRepository.findBy(USER_ID, user.getId())).orElseGet(() -> {
			final UserSqlCredential result = new UserSqlCredential();
			result.setUser(cacheUserRepository.findOne(user.getId()));
			credentialRepository.save(result);
			return result;
		});
	}

	@Override
	public void unlock(final UserOrg user) {
		unlock(user, false);
	}

	private void unlock(final UserOrg user, final boolean isolate) {
		if (user.getIsolated() == null && user.getLocked() != null) {
			final UserSqlCredential credential = createAsNeeded(user);
			credential.setLocked(null);
			credential.setLockedBy(null);

			if (isolate) {
				credential.setIsolated(null);
			}

			// Also clear the disabled state from cache
			user.setLocked(null);
			user.setLockedBy(null);
		}
	}

	@Override
	public boolean authenticate(final String name, final String password) {
		log.info("Authenticating {} ...", name);
		final UserSqlCredential credential = credentialRepository.findBy(USER_ID, name);

		final String salt;
		final String value;
		if (credential == null) {
			// Time resisting attack
			salt = "-".repeat(saltLength);
			value = "0".repeat(16);
		} else {
			salt = credential.getSalt();
			value = StringUtils.defaultString(credential.getValue(), "");
		}

		// Compare
		final boolean result;
		if (salt == null) {
			// Not encrypted password
			result = password.equals(value);
		} else {
			result = hashPassword(password.toCharArray(), salt.getBytes(StandardCharsets.UTF_8), hashIteration,
					keyLength).equals(value);
		}
		log.info("Authenticate {} : {}", name, result);
		return result;
	}

	@Override
	public String getToken(final String login) {
		return Optional.ofNullable(credentialRepository.findBy(USER_ID, login)).map(UserSqlCredential::getValue)
				.orElse(null);
	}

	@Override
	public void setPassword(final UserOrg user, final String password) {
		final UserSqlCredential credential = createAsNeeded(user);
		credential.setSalt(GENERATOR.generate(saltLength));
		credential.setValue(hashPassword(password.toCharArray(), credential.getSalt().getBytes(StandardCharsets.UTF_8),
				hashIteration, keyLength));
	}

	/**
	 * The password and salt arguments are arrays, as is the result of the hashPassword function. Sensitive data should
	 * be cleared after you have used it (set the array elements to zero).
	 *
	 * The example uses a Password Based Key Derivation Function 2 (PBKDF2), as discussed in the Password Storage Cheat
	 * Sheet.
	 *
	 * @param password   Password to hash.
	 * @param salt       Should be random data and vary for each user. It should be at least 32 bytes long. Remember to
	 *                   save the salt with the hashed password!
	 * @param iterations Specifies how many times the PBKDF2 executes its underlying algorithm. A higher value is safer.
	 *                   You need to experiment on hardware equivalent to your production systems. As a starting point,
	 *                   find a value that requires one half second to execute. Scaling to huge number of users is
	 *                   beyond the scope of this document. Remember to save the value of iterations with the hashed
	 *                   password!
	 * @param keyLength  Key length. 256 is safe.
	 * @return
	 * @see <a href="https://www.owasp.org/index.php/Hashing_Java">www.owasp.org<a>
	 */
	private String hashPassword(final char[] password, final byte[] salt, final int iterations, final int keyLength) {

		try {
			final SecretKeyFactory skf = SecretKeyFactory.getInstance(secretKeyFactory);
			return Base64.getEncoder().encodeToString(
					skf.generateSecret(new PBEKeySpec(password, salt, iterations, keyLength)).getEncoded());
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new TechnicalException("password-security", e);
		}
	}

	@Override
	public void setPassword(final UserOrg user, final String password, final String newPassword) {
		log.info("Changing password for {} ...", user.getId());
		if (password == null || authenticate(user.getId(), password)) {
			setPassword(user, newPassword);
			// Also unlock account
			unlock(user);
		} else {
			throw new ValidationJsonException("password", "login");
		}
	}

}
