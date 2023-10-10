/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.dao;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.api.Normalizer;
import org.ligoj.app.iam.ContainerOrg;
import org.ligoj.app.iam.IContainerRepository;
import org.ligoj.app.iam.dao.CacheContainerRepository;
import org.ligoj.app.iam.model.CacheContainer;
import org.ligoj.app.model.ContainerType;
import org.ligoj.bootstrap.core.json.InMemoryPagination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

import java.util.*;

/**
 * A SQL container repository.
 *
 * @param <T> The container type.
 * @param <C> The container cache type.
 */
public abstract class AbstractContainerSqlRepository<T extends ContainerOrg, C extends CacheContainer>
		implements IContainerRepository<T> {

	protected static final Sort.Order DEFAULT_ORDER = new Sort.Order(Direction.ASC, "name");

	@Autowired
	protected InMemoryPagination inMemoryPagination;

	@Autowired
	@Setter
	protected CacheSqlRepository repository;

	/**
	 * Human readable type name.
	 */
	@Getter
	protected final String typeName;

	/**
	 * Container type.
	 */
	private final ContainerType type;

	protected AbstractContainerSqlRepository(final ContainerType type) {
		this.type = type;
		this.typeName = this.type.name().toLowerCase(Locale.ENGLISH);
	}

	/**
	 * Return the repository managing the container as cache.
	 *
	 * @return the repository managing the container as cache.
	 */
	protected abstract CacheContainerRepository<C> getCacheRepository();

	/**
	 * Create a new container bean. Not in SQL repository.
	 *
	 * @param dn The unique DN of the container.
	 * @param cn The human readable name (CN) that will be used to build the identifier.
	 * @return A new transient container bean. Never <code>null</code>.
	 */
	protected abstract T newContainer(String dn, String cn);

	@Override
	public T create(final String dn, final String cn) {
		return newContainer(dn, cn);
	}

	@Override
	public Page<T> findAll(final Set<T> containers, final String criteria, final Pageable pageable,
			final Map<String, Comparator<T>> customComparators) {
		// Create the set with the right comparator
		final List<Sort.Order> orders = IteratorUtils
				.toList(ObjectUtils.defaultIfNull(pageable.getSort(), new ArrayList<Sort.Order>()).iterator());
		orders.add(DEFAULT_ORDER);
		final Sort.Order order = orders.get(0);
		Comparator<T> comparator = customComparators.get(order.getProperty());
		if (order.getDirection() == Direction.DESC) {
			comparator = Collections.reverseOrder(comparator);
		}
		final Set<T> result = new TreeSet<>(comparator);

		// Filter the containers, filtering by the criteria
		containers.stream()
				.filter(c -> StringUtils.isEmpty(criteria) || StringUtils.containsIgnoreCase(c.getName(), criteria))
				.forEach(result::add);

		// Apply in-memory pagination
		return inMemoryPagination.newPage(result, pageable);
	}

	/**
	 * Find a container from its identifier. Security is applied regarding the given user.
	 *
	 * @param user The user requesting this container.
	 * @param id   The container's identifier. Will be normalized.
	 * @return The container from its identifier. <code>null</code> if the container is not found or cannot be seen by
	 * the given user.
	 */
	@Override
	public T findById(final String user, final String id) {
		// Check the container exists and return the in memory object.
		return Optional.ofNullable(getCacheRepository().findById(user, Normalizer.normalize(id)))
				.map(CacheContainer::getId).map(this::findById).orElse(null);
	}
}
