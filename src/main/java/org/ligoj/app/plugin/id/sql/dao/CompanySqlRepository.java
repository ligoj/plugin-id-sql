/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.dao;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.ligoj.app.iam.CompanyOrg;
import org.ligoj.app.iam.ICompanyRepository;
import org.ligoj.app.iam.dao.CacheCompanyRepository;
import org.ligoj.app.iam.model.CacheCompany;
import org.ligoj.app.model.ContainerType;
import org.ligoj.app.plugin.id.DnUtils;
import org.ligoj.app.plugin.id.dao.AbstractMemCacheRepository.CacheDataType;
import org.ligoj.bootstrap.core.resource.TechnicalException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Company SQL repository
 */
@Component
public class CompanySqlRepository extends AbstractContainerSqlRepository<CompanyOrg, CacheCompany>
		implements ICompanyRepository {

	/**
	 * Special company that will contains the isolated accounts.
	 */
	private static final String QUARANTINE_DN = "ou=quarantine";

	@Autowired
	private CacheCompanyRepository cacheCompanyRepository;

	/**
	 * Default constructor for a container of type {@link ContainerType#COMPANY}
	 */
	public CompanySqlRepository() {
		super(ContainerType.COMPANY);
	}

	@Override
	public CacheCompanyRepository getCacheRepository() {
		return cacheCompanyRepository;
	}

	/**
	 * Fetch and return all normalized companies. Note the result uses cache, so does not reflect the current state of
	 * SQL. Cache manager is involved.
	 *
	 * @return the companies. Key is the normalized name.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Map<String, CompanyOrg> findAll() {
		return (Map<String, CompanyOrg>) repository.getData().get(CacheDataType.COMPANY);
	}

	/**
	 * Fetch and return all normalized companies. Note the result use cache, so does not reflect the current state of
	 * SQL.
	 *
	 * @return the companies. Key is the normalized name.
	 */
	@Override
	public Map<String, CompanyOrg> findAllNoCache() {
		final Map<String, CompanyOrg> result = new HashMap<>();
		for (final CacheCompany companyRaw : cacheCompanyRepository.findAll()) {
			final CompanyOrg company = new CompanyOrg(companyRaw.getDescription(), companyRaw.getName());
			result.put(company.getId(), company);
		}

		// Also add/replace the quarantine zone
		final CompanyOrg quarantine = new CompanyOrg(QUARANTINE_DN, getQuarantineCompany());
		quarantine.setLocked(true);
		result.put(quarantine.getId(), quarantine);

		// The complete the hierarchy of companies
		result.values().forEach(this::buildLdapName);
		result.values().forEach(c -> this.buildHierarchy(result, c));
		return result;
	}

	/**
	 * Build the {@link LdapName} instance from the DN. This also requires a valid DN for the given {@link CompanyOrg}
	 */
	private void buildLdapName(final CompanyOrg company) {
		company.setLdapName(newLdapName(company.getDn()));
	}

	/**
	 * Build the {@link LdapName} instance from the DN. This also requires a valid DN.
	 *
	 * @param dn The DN to parse.
	 * @return The {@link LdapName} instance.
	 */
	protected LdapName newLdapName(final String dn) {
		try {
			return new LdapName(dn);
		} catch (InvalidNameException e) {
			throw new TechnicalException("Invalid SQL to SQL pattern");
		}
	}

	/**
	 * Build the company hierarchy from the given {@link CompanyOrg}
	 */
	private void buildHierarchy(final Map<String, CompanyOrg> companies, final CompanyOrg company) {
		// Collect all parents and sorted from parent to the leaf
		company.setCompanyTree(
				companies.values().stream().filter(c -> DnUtils.equalsOrParentOf(c.getDn(), company.getDn()))
						.sorted(Comparator.comparing(CompanyOrg::getLdapName)).toList());
	}

	/**
	 * Return the quarantine/isolated company identifier.
	 *
	 * @return The quarantine/isolated company identifier.
	 */
	public String getQuarantineCompany() {
		return DnUtils.toRdn(QUARANTINE_DN);
	}

	@Override
	public CompanyOrg create(final String dn, final String cn) {
		return repository.create(super.create(dn, cn));
	}

	@Override
	protected CompanyOrg newContainer(final String dn, final String name) {
		return new CompanyOrg(dn.toLowerCase(Locale.ENGLISH), name);
	}

	@Override
	public void delete(final CompanyOrg container) {

		/*
		 * Remove from this company, all companies within (sub SQL DN) this company. This operation is needed since we
		 * are not rebuilding the cache from the SQL. This save a lot of computations.
		 */
		findAll().values().stream().filter(g -> DnUtils.equalsOrParentOf(container.getDn(), g.getDn())).toList()
				.forEach(repository::delete);
	}

}
