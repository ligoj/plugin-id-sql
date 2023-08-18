/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.id.sql.model;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.bootstrap.core.model.AbstractPersistable;

import lombok.Getter;
import lombok.Setter;

/**
 * IAM User cache.
 */
@Entity
@Table(name = "LIGOJ_ID_SQL_CREDENTIAL")
@Getter
@Setter
public class UserSqlCredential extends AbstractPersistable<Integer> {

	/**
	 * User reference.
	 */
	@NotNull
	@ManyToOne
	private CacheUser user;

	/**
	 * User credential salt with hash. When <code>null</code>, provided {@link #value} is not hashed.
	 */
	@Size(min = 1)
	private String salt;

	/**
	 * User secrets with hash and salt.
	 */
	@Size(min = 1)
	private String value;

	/**
	 * When not <code>null</code>, this user is locked and the date corresponds to the moment.
	 */
	@Temporal(TemporalType.TIMESTAMP)
	private Date locked;

	/**
	 * User principal identifier locking this user.
	 */
	@Size(min = 1)
	private String lockedBy;

	/**
	 * When not <code>null</code>, this user has been put in quarantine. Not strong relationship to allow a company
	 * deletion without this constraint.
	 */
	@Size(min = 1)
	private String isolated;
}
