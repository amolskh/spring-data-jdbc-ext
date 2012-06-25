/*
 * Copyright 2008-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.jdbc.core;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * An abstract results extractor for row mapping operations that map multiple rows to a single root object.
 * This is useful when joining a one-to-many relationship where there can be multiple child rows returned per
 * parent root.
 *
 * It's assumed the the root type R table has a primary key (id) of type K and that the child type C table has a
 * foreign key of type K referencing the root table's primary key.
 *
 * For example, consider the relationship: "a Customer has one-to-many Addresses".
 * When joining the Customer table with the Address table to build a Customer object, multiple rows would be returned
 * for a Customer if it has more than one Address. This extractor is useful in that case.
 *
 * @author Thomas Risberg
 * @author Keith Donald
 * @since 1.0
 */
public abstract class OneToManyResultSetExtractor<R, C, K> implements ResultSetExtractor<List<R>> {

	public enum ExpectedResults {
		ANY,
		ONE_AND_ONLY_ONE,
		ONE_OR_NONE,
		AT_LEAST_ONE
	}

	protected List<R> results;

	protected ExpectedResults expectedResults = ExpectedResults.ANY;

	protected RowMapper<R> rootMapper;

	protected RowMapper<C> childMapper;

	public OneToManyResultSetExtractor(RowMapper<R> rootMapper, RowMapper<C> childMapper) {
		this.rootMapper = rootMapper;
		this.childMapper = childMapper;
	}

	public OneToManyResultSetExtractor(RowMapper<R> rootMapper, RowMapper<C> childMapper, ExpectedResults expectedResults) {
		this(rootMapper, childMapper);
		this.expectedResults = expectedResults;
	}

	public List<R> extractData(ResultSet rs) throws SQLException, DataAccessException {
		if (results == null) {
			results = new ArrayList<R>();
		}
		int row = 0;
		boolean more = rs.next();
		if (more) {
			row++;
		}
		while (more) {
			R root = rootMapper.mapRow(rs, row);
			K primaryKey = mapPrimaryKey(rs);
			if (mapForeignKey(rs) != null) {
				while (more && primaryKey.equals(mapForeignKey(rs))) {
					addChild(root, childMapper.mapRow(rs, row));
					more = rs.next();
					if (more) {
						row++;
					}
				}
			}
			else {
				more = rs.next();
				if (more) {
					row++;
				}
			}
			results.add(root);
		}
		if ((expectedResults == ExpectedResults.ONE_AND_ONLY_ONE || expectedResults == ExpectedResults.ONE_OR_NONE) &&
				results.size() > 1) {
			throw new IncorrectResultSizeDataAccessException(1, results.size());
		}
		if ((expectedResults == ExpectedResults.ONE_AND_ONLY_ONE || expectedResults == ExpectedResults.AT_LEAST_ONE) &&
				results.size() < 1) {
			throw new IncorrectResultSizeDataAccessException(1, 0);
		}
		return results;
	}

	/**
	 * Map the primary key value to the required type.
	 * This method must be implemented by subclasses.
	 * This method should not call <code>next()</code> on
	 * the ResultSet; it is only supposed to map values of the current row.
	 *
	 * @param rs the ResultSet
	 * @return the primary key value
	 * @throws SQLException
	 */
	protected abstract K mapPrimaryKey(ResultSet rs) throws SQLException;

	/**
	 * Map the foreign key value to the required type.
	 * This method must be implemented by subclasses.
	 * This method should not call <code>next()</code> on
	 * the ResultSet; it is only supposed to map values of the current row.
	 *
	 * @param rs the ResultSet
	 * @return the foreign key value
	 * @throws SQLException
	 */
	protected abstract K mapForeignKey(ResultSet rs) throws SQLException;

	/**
	 * Add the child object to the root object
	 * This method must be implemented by subclasses.
	 *
	 * @param root the Root object
	 * @param child the Child object
	 */
	protected abstract void addChild(R root, C child);

}
