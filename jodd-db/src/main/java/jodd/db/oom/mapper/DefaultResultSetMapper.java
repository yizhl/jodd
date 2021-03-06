// Copyright (c) 2003-2014, Jodd Team (jodd.org). All Rights Reserved.

package jodd.db.oom.mapper;

import jodd.bean.BeanUtil;
import jodd.db.oom.ColumnData;
import jodd.db.oom.DbEntityDescriptor;
import jodd.db.oom.DbOomManager;
import jodd.db.oom.DbOomException;
import jodd.db.oom.DbEntityColumnDescriptor;
import jodd.db.type.SqlTypeManager;
import jodd.db.type.SqlType;
import jodd.typeconverter.TypeConverterManager;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * Maps all columns of database result set (RS) row to objects.
 * It does it in two steps: preparation (reading table and column names)
 * and parsing (parsing one result set row to resulting objects).
 * <p>
 * <b>Preparation</b><br>
 * Default mapper reads RS column and table names from RS meta-data and external maps, if provided.
 * Since column name is always available in RS meta-data, it may be used to hold table name information.
 * Column names may contain table code separator ({@link jodd.db.oom.DbOomManager#getColumnAliasSeparator()} that
 * divides column name to table reference and column name. Here, table reference may be either table name or
 * table alias. When it is table alias, external alias-to-name map must be provided.
 * Hence, this defines the table name, and there is no need to read it from RS meta-data.
 * <p>
 * When column name doesn't contain a separator, it may be either an actual column name, or a column code.
 * For column codes, both table and column name is lookup-ed from external map. If column name is an actual column name,
 * table information is read from the RS meta data. Unfortunately, some DBs (such Oracle) doesn't implements
 * this simple JDBC feature. Therefore, it must be expected that column table name is not available.
 * <p>
 * Table name is also not available for columns which are not directly table columns:
 * e.g. some calculations, counts etc.
 * <p>
 * <b>Parsing</b><br>
 * Parser takes types array and tries to populate their instances in best possible way. It assumes that provided
 * types list matches selected columns. That is very important, and yet very easy and natural to follow.
 * So, parser will try to inject columns value into the one result instance. Now, there are two types of instances:
 * simple types (numbers and strings) and entities (pojo objects). Simple types are always mapped to
 * one and only one column. Entities will be mapped to all possible columns that can be matched starting from
 * current column. So, simple types are not column-hungry, entity types are column-hungry:)
 *
 * <p>
 * A column can be injected in one entities property only once. If one column is already mapped to current result,
 * RS mapper will assume that current result is finished with mapping and will proceed to the next one.
 * Similarly, if property name is not found for a column, RS mapper will proceed to the next result.
 * Therefore, entity types are column precise and hungry;) - all listed columns must be mapped somewhere.
 *
 * <p>
 * Results that are not used during parsing will be set to <code>null</code>.
 * <p>
 */
public class DefaultResultSetMapper extends BaseResultSetMapper {

	protected final DbOomManager dbOomManager;
	protected final boolean cacheEntities;
	protected final boolean strictCompare;
	protected final int totalColumns;			// total number of columns
	protected final String[] columnNames;		// list of all column names
	protected final int[] columnDbSqlTypes;		// list of all column db types
	protected final String[] tableNames;		// list of table names for each column, table name may be null

	private final Set<String> resultColumns;	// internal columns per entity cache

	// ---------------------------------------------------------------- ctor

	/**
	 * Reads <code>ResultSet</code> meta-data for column and table names.
	 * @param resultSet JDBC result set
	 * @param columnAliases alias names for columns, if exist
	 * @param cacheEntities flag if entities should be cached
	 * @param oomManager DbOom manager instance
	 */
	public DefaultResultSetMapper(ResultSet resultSet, Map<String, ColumnData> columnAliases, boolean cacheEntities, DbOomManager oomManager) {
		super(resultSet);
		this.dbOomManager = oomManager;
		this.cacheEntities = cacheEntities;
		this.strictCompare = dbOomManager.isStrictCompare();
		//this.resultColumns = new HashSet<String>();
		try {
			ResultSetMetaData rsMetaData = resultSet.getMetaData();
			if (rsMetaData == null) {
				throw new DbOomException("JDBC driver does not provide meta-data.");
			}

			totalColumns = rsMetaData.getColumnCount();

			this.resultColumns = new HashSet<String>(totalColumns);
			columnNames = new String[totalColumns];
			columnDbSqlTypes = new int[totalColumns];
			tableNames = new String[totalColumns];

			for (int i = 0; i < totalColumns; i++) {
				String columnName = rsMetaData.getColumnName(i + 1);
				String tableName = null;

				// resolve column and table name
				int sepNdx = columnName.indexOf(dbOomManager.getColumnAliasSeparator());
				if (sepNdx != -1) {
					// column alias exist, result set is ignored and columnAliases contains table data.
					tableName = columnName.substring(0, sepNdx);
					if (columnAliases != null) {
						ColumnData columnData = columnAliases.get(tableName);
						if (columnData != null) {
							tableName = columnData.getTableName();
						}
					}
					columnName = columnName.substring(sepNdx + 1);
				} else {
					// column alias does not exist, table name is read from columnAliases and result set (if available).
					if (columnAliases != null) {
						ColumnData columnData = columnAliases.get(columnName.toLowerCase());
						if (columnData != null) {
							tableName = columnData.getTableName();
							columnName = columnData.getColumnName();
						}
					}
					if (tableName == null) {
						try {
							tableName = rsMetaData.getTableName(i + 1);
						} catch (SQLException sex) {
							// ignore
					}
						if ((tableName != null) && (tableName.length() == 0)) {
							tableName = null;
						}
					}
				}

				columnName = columnName.trim();
				if (columnName.length() == 0) {
					columnName = null;
				}

				if (columnName != null) {
					columnName = columnName.trim();

					if (!strictCompare) {
						columnName = columnName.toUpperCase();
					}
				}
				columnNames[i] = columnName;

				if (tableName != null) {
					tableName = tableName.trim();

					if (!strictCompare) {
						tableName = tableName.toUpperCase();
					}
				}
				tableNames[i] = tableName;
				columnDbSqlTypes[i] = rsMetaData.getColumnType(i + 1);
			}
		} catch (SQLException sex) {
			throw new DbOomException("Unable to read ResultSet meta-data.", sex);
		}
	}

	// ---------------------------------------------------------------- delegates

	/**
	 * {@inheritDoc}
	 */
	public Class[] resolveTables() {
		List<Class> classes = new ArrayList<Class>(tableNames.length);
		String lastTableName = null;
		resultColumns.clear();

		for (int i = 0; i < tableNames.length; i++) {
			String tableName = tableNames[i];
			String columnName = columnNames[i];

			if (tableName == null) {
				// maybe JDBC driver does not support it
				throw new DbOomException("Table name not available in driver meta-data.");
			}

			if ((tableName.equals(lastTableName) == false) || (resultColumns.contains(columnName) == true)) {
				resultColumns.clear();
				lastTableName = tableName;

				DbEntityDescriptor ded = dbOomManager.lookupTableName(tableName);
				if (ded == null) {
					throw new DbOomException("Table name not registered: " + tableName);
				}

				classes.add(ded.getType());
			}
			resultColumns.add(columnName);
		}
		return classes.toArray(new Class[classes.size()]);
	}

	// ---------------------------------------------------------------- parse objects

	protected Class[] cachedUsedTypes;
	protected String[] cachedTypesTableNames;

	/**
	 * Creates table names for all specified types.
	 * Since this is usually done once per result set, these names are cached.
	 * Type name will be <code>null</code> for simple names, i.e. for all those
	 * types that returns <code>null</code> when used by {@link jodd.db.oom.DbOomManager#lookupType(Class)}.
	 */
	protected String[] createTypesTableNames(Class[] types) {
		if (types != cachedUsedTypes) {
			cachedTypesTableNames = new String[types.length];
			for (int i = 0; i < types.length; i++) {
				if (types[i] == null) {
					cachedTypesTableNames[i] = null;
					continue;
				}
				DbEntityDescriptor ded = dbOomManager.lookupType(types[i]);
				if (ded != null) {
					String tableName = ded.getTableName();
					if (!strictCompare) {
						tableName = tableName.toUpperCase();
					}
					cachedTypesTableNames[i] = tableName;
				}
			}
			cachedUsedTypes = types;			
		}
		return cachedTypesTableNames;
	}


	protected int cachedColumnNdx;
	protected Object cachedColumnValue;

	/**
	 * Reads column value from result set. Since this method may be called more then once for
	 * the same column, it caches column values.
	 */
	@SuppressWarnings({"unchecked"})
	protected Object readColumnValue(int colNdx, Class destinationType, Class<? extends SqlType> sqlTypeClass, int columnDbSqlType) {
		if (colNdx != cachedColumnNdx) {
			try {
				SqlType sqlType;
				if (sqlTypeClass != null) {
					sqlType = SqlTypeManager.lookupSqlType(sqlTypeClass);
				} else {
					sqlType = SqlTypeManager.lookup(destinationType);
				}
				if (sqlType != null) {
					cachedColumnValue = sqlType.readValue(resultSet, colNdx + 1, destinationType, columnDbSqlType);
				} else {
					cachedColumnValue = resultSet.getObject(colNdx + 1);
					cachedColumnValue = TypeConverterManager.convertType(cachedColumnValue, destinationType);
				}
			} catch (SQLException sex) {
				throw new DbOomException("Unable to read value for column #" + (colNdx + 1), sex);
			}
			cachedColumnNdx = colNdx;
		}
		return cachedColumnValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public Object[] parseObjects(Class... types) {
		resultColumns.clear();

		int totalTypes = types.length;
		Object[] result = new Object[totalTypes];
		boolean[] resultUsage = new boolean[totalTypes];
		String[] typesTableNames = createTypesTableNames(types);

		int currentResult = 0;
		cachedColumnNdx = -1;
		int colNdx = 0;
		while (colNdx < totalColumns) {

			// no more types for mapping?
			if (currentResult >= totalTypes) {
				break;
			}

			// skip columns that doesn't map
			Class currentType = types[currentResult];
			if (currentType == null) {
				colNdx++;
				currentResult++; resultColumns.clear();
				continue;
			}

			String columnName = columnNames[colNdx];
			int columnDbSqlType = columnDbSqlTypes[colNdx];
			String tableName = tableNames[colNdx];
			String resultTableName = typesTableNames[currentResult];			

			if (resultTableName == null) {
				// match: simple type
				result[currentResult] = readColumnValue(colNdx, currentType, null, columnDbSqlType);
				resultUsage[currentResult] = true;
				colNdx++;
				currentResult++; resultColumns.clear();
				continue;
			}
			if ((tableName == null) || (resultTableName.equals(tableName) == true)) {
				if (resultColumns.contains(columnName) == false) {
					DbEntityDescriptor ded = dbOomManager.lookupType(currentType);
					DbEntityColumnDescriptor dec = ded.findByColumnName(columnName);
					String propertyName = (dec == null ? null : dec.getPropertyName());
					if (propertyName != null) {

						// if current entity instance does not exist (i.e. we are at the first column
						// of some entity), create the instance and store it
						if (result[currentResult] == null) {
							result[currentResult] = dbOomManager.createEntityInstance(currentType);
						}
/*
						boolean success = value != null ?
										BeanUtil.setDeclaredPropertySilent(result[currentResult], propertyName, value) :
										BeanUtil.hasDeclaredProperty(result[currentResult], propertyName);
*/
						Class type = BeanUtil.getDeclaredPropertyType(result[currentResult], propertyName);
						if (type != null) {
							// match: entity
							dec.updateDbSqlType(columnDbSqlType);	// updates column db sql type information for the entity!!!
							Class<? extends SqlType> sqlTypeClass = dec.getSqlTypeClass();
							Object value = readColumnValue(colNdx, type, sqlTypeClass, columnDbSqlType);

							if (value != null) {
								// inject column value into existing entity
								BeanUtil.setDeclaredProperty(result[currentResult], propertyName, value);
								resultUsage[currentResult] = true;
							}
							colNdx++;
							resultColumns.add(columnName);
							continue;
						}
					}
				}
			}
			// go to next type, i.e. result
			currentResult++;
			resultColumns.clear();
		}

		resultColumns.clear();
		for (int i = 0; i < resultUsage.length; i++) {
			if (resultUsage[i] == false) {
				result[i] = null;
			}
		}

		if (cacheEntities) {
			cacheResultSetEntities(result);
		}

		return result;
	}


	// ---------------------------------------------------------------- cache

	protected HashMap<Object, Object> entitiesCache;

	/**
	 * Caches returned entities. Replaces new instances with existing ones.
	 */
	protected void cacheResultSetEntities(Object[] result) {
		if (entitiesCache == null) {
			entitiesCache = new HashMap<Object, Object>();
		}

		for (int i = 0; i < result.length; i++) {
			Object object = result[i];

			Object cachedObject = entitiesCache.get(object);

			if (cachedObject == null) {
				// object is not in the cache, add it
				entitiesCache.put(object, object);
			} else {
				// object is in the cache, replace it
				result[i] = cachedObject;
			}
		}
	}

}