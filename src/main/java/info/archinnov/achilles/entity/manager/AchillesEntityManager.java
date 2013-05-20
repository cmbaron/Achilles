package info.archinnov.achilles.entity.manager;

import info.archinnov.achilles.consistency.AchillesConsistencyLevelPolicy;
import info.archinnov.achilles.entity.context.AchillesConfigurationContext;
import info.archinnov.achilles.entity.context.AchillesPersistenceContext;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.operations.AchillesEntityInitializer;
import info.archinnov.achilles.entity.operations.AchillesEntityLoader;
import info.archinnov.achilles.entity.operations.AchillesEntityMerger;
import info.archinnov.achilles.entity.operations.AchillesEntityPersister;
import info.archinnov.achilles.entity.operations.AchillesEntityProxifier;
import info.archinnov.achilles.entity.operations.AchillesEntityRefresher;
import info.archinnov.achilles.entity.operations.AchillesEntityValidator;
import info.archinnov.achilles.entity.type.ConsistencyLevel;
import info.archinnov.achilles.validation.Validator;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.metamodel.Metamodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AchillesEntityManager
 * 
 * @author DuyHai DOAN
 * 
 */
public abstract class AchillesEntityManager implements EntityManager
{
	private static final Logger log = LoggerFactory.getLogger(AchillesEntityManager.class);

	protected final AchillesEntityManagerFactory entityManagerFactory;
	protected Map<Class<?>, EntityMeta> entityMetaMap;
	protected AchillesConsistencyLevelPolicy consistencyPolicy;
	protected AchillesConfigurationContext configContext;

	protected AchillesEntityPersister persister;
	protected AchillesEntityLoader loader;
	protected AchillesEntityMerger merger;
	protected AchillesEntityRefresher refresher;
	protected AchillesEntityProxifier proxifier;
	protected AchillesEntityValidator entityValidator;
	protected AchillesEntityInitializer initializer = new AchillesEntityInitializer();

	AchillesEntityManager(AchillesEntityManagerFactory entityManagerFactory,
			Map<Class<?>, EntityMeta> entityMetaMap, //
			AchillesConfigurationContext configContext)
	{
		this.entityManagerFactory = entityManagerFactory;
		this.entityMetaMap = entityMetaMap;
		this.configContext = configContext;
		this.consistencyPolicy = configContext.getConsistencyPolicy();
	}

	/**
	 * Persist an entity. All join entities with CascadeType.PERSIST or CascadeType.ALL
	 * 
	 * will be also persisted, overriding their current state in Cassandra
	 * 
	 * @param entity
	 *            Entity to be persisted
	 */
	@Override
	public void persist(Object entity)
	{
		log.debug("Persisting entity '{}'", entity);
		entityValidator.validateEntity(entity, entityMetaMap);
		entityValidator.validateNotWideRow(entity, entityMetaMap);

		if (proxifier.isProxy(entity))
		{
			throw new IllegalStateException(
					"Then entity is already in 'managed' state. Please use the merge() method instead of persist()");
		}
		AchillesPersistenceContext context = initPersistenceContext(entity);
		persister.persist(context);
		context.flush();
	}

	/**
	 * Persist an entity with the given Consistency Level for write. All join entities with CascadeType.PERSIST or CascadeType.ALL
	 * 
	 * will be also persisted, overriding their current state in Cassandra
	 * 
	 * @param entity
	 *            Entity to be persisted
	 * @param writeLevel
	 *            Consistency Level for write
	 */
	public abstract void persist(final Object entity, ConsistencyLevel writeLevel);

	/**
	 * Merge an entity. All join entities with CascadeType.MERGE or CascadeType.ALL
	 * 
	 * will be also merged, updating their current state in Cassandra.
	 * 
	 * Calling merge on a transient entity will persist it and returns a managed
	 * 
	 * instance.
	 * 
	 * <strong>Unlike the JPA specs, Achilles returns the same entity passed
	 * 
	 * in parameter if the latter is in managed state. It was designed on purpose
	 * 
	 * so you do not loose the reference of the passed entity. For transient
	 * 
	 * entity, the return value is a new proxy object
	 * 
	 * </strong>
	 * 
	 * @param entity
	 *            Entity to be merged
	 * @return Merged entity or a new proxified entity
	 */
	@Override
	public <T> T merge(T entity)
	{
		if (log.isDebugEnabled())
		{
			log.debug("Merging entity '{}' ", proxifier.unproxy(entity));
		}
		entityValidator.validateNotWideRow(entity, entityMetaMap);
		entityValidator.validateEntity(entity, entityMetaMap);
		AchillesPersistenceContext context = initPersistenceContext(entity);
		T merged = merger.<T> mergeEntity(context, entity);
		context.flush();
		return merged;
	}

	/**
	 * Merge an entity with the given Consistency Level for write. All join entities with CascadeType.MERGE or CascadeType.ALL
	 * 
	 * will be also merged, updating their current state in Cassandra.
	 * 
	 * Calling merge on a transient entity will persist it and returns a managed
	 * 
	 * instance.
	 * 
	 * <strong>Unlike the JPA specs, Achilles returns the same entity passed
	 * 
	 * in parameter if the latter is in managed state. It was designed on purpose
	 * 
	 * so you do not loose the reference of the passed entity. For transient
	 * 
	 * entity, the return value is a new proxy object
	 * 
	 * </strong>
	 * 
	 * @param entity
	 *            Entity to be merged
	 * @param writeLevel
	 *            Consistency Level for write
	 * @return Merged entity or a new proxified entity
	 */
	public abstract <T> T merge(final T entity, ConsistencyLevel writeLevel);

	/**
	 * Remove an entity. Join entities are <strong>not</strong> removed.
	 * 
	 * CascadeType.REMOVE is not supported as per design to avoid
	 * 
	 * inconsistencies while removing <em>shared</em> join entities.
	 * 
	 * You need to remove the join entities manually
	 * 
	 * @param entity
	 *            Entity to be removed
	 */
	@Override
	public void remove(Object entity)
	{
		if (log.isDebugEnabled())
		{
			log.debug("Removing entity '{}'", proxifier.unproxy(entity));
		}
		entityValidator.validateEntity(entity, entityMetaMap);
		proxifier.ensureProxy(entity);
		AchillesPersistenceContext context = initPersistenceContext(entity);
		persister.remove(context);
		context.flush();
	}

	/**
	 * Remove an entity with the given Consistency Level for write. Join entities are <strong>not</strong> removed.
	 * 
	 * CascadeType.REMOVE is not supported as per design to avoid
	 * 
	 * inconsistencies while removing <em>shared</em> join entities.
	 * 
	 * You need to remove the join entities manually
	 * 
	 * @param entity
	 *            Entity to be removed
	 * @param writeLevel
	 *            Consistency Level for write
	 */
	public abstract void remove(final Object entity, ConsistencyLevel writeLevel);

	/**
	 * Find an entity.
	 * 
	 * @param entityClass
	 *            Entity type
	 * @param primaryKey
	 *            Primary key (Cassandra row key) of the entity to load
	 * @param entity
	 *            Found entity or null if no entity is found
	 */
	@Override
	public <T> T find(Class<T> entityClass, Object primaryKey)
	{
		log.debug("Find entity class '{}' with primary key {}", entityClass, primaryKey);

		Validator.validateNotNull(entityClass, "Entity class should not be null");
		Validator.validateNotNull(primaryKey, "Entity primaryKey should not be null");
		AchillesPersistenceContext context = initPersistenceContext(entityClass, primaryKey);

		T entity = loader.<T> load(context, entityClass);
		if (entity != null)
		{
			entity = proxifier.buildProxy(entity, context);
		}
		return entity;
	}

	/**
	 * Find an entity with the given Consistency Level for read
	 * 
	 * @param entityClass
	 *            Entity type
	 * @param primaryKey
	 *            Primary key (Cassandra row key) of the entity to load
	 * @param readLevel
	 *            Consistency Level for read
	 * @param entity
	 *            Found entity or null if no entity is found
	 */
	public abstract <T> T find(final Class<T> entityClass, final Object primaryKey,
			ConsistencyLevel readLevel);

	/**
	 * Find an entity. Works exactly as find(Class<T> entityClass, Object primaryKey)
	 * 
	 * @param entityClass
	 *            Entity type
	 * @param primaryKey
	 *            Primary key (Cassandra row key) of the entity to load
	 * @param entity
	 *            Found entity or null if no entity is found
	 */
	@Override
	public <T> T getReference(Class<T> entityClass, Object primaryKey)
	{
		log.debug("Get reference for entity class '{}' with primary key {}", entityClass,
				primaryKey);

		Validator.validateNotNull(entityClass, "Entity class should not be null");
		Validator.validateNotNull(primaryKey, "Entity primaryKey should not be null");
		AchillesPersistenceContext context = initPersistenceContext(entityClass, primaryKey);
		context.setLoadEagerFields(false);
		T entity = loader.<T> load(context, entityClass);
		if (entity != null)
		{
			entity = proxifier.buildProxy(entity, context);
		}
		return entity;
	}

	/**
	 * Find an entity with the given Consistency Level for read. Works exactly as find(Class<T> entityClass, Object primaryKey)
	 * 
	 * @param entityClass
	 *            Entity type
	 * @param primaryKey
	 *            Primary key (Cassandra row key) of the entity to load
	 * @param readLevel
	 *            Consistency Level for read
	 * @param entity
	 *            Found entity or null if no entity is found
	 */
	public abstract <T> T getReference(final Class<T> entityClass, final Object primaryKey,
			ConsistencyLevel readLevel);

	/**
	 * Refresh an entity. All join entities with CascadeType.REFRESH or CascadeType.ALL
	 * 
	 * will be also refreshed from Cassandra.
	 * 
	 * @param entity
	 *            Entity to be refreshed
	 */
	@Override
	public void refresh(Object entity)
	{
		if (log.isDebugEnabled())
		{
			log.debug("Refreshing entity '{}'", proxifier.unproxy(entity));
		}
		entityValidator.validateEntity(entity, entityMetaMap);
		entityValidator.validateNotWideRow(entity, entityMetaMap);
		proxifier.ensureProxy(entity);
		AchillesPersistenceContext context = initPersistenceContext(entity);
		refresher.refresh(context);
	}

	/**
	 * Refresh an entity with the given Consistency Level for read. All join entities with CascadeType.REFRESH or CascadeType.ALL
	 * 
	 * will be also refreshed from Cassandra.
	 * 
	 * @param entity
	 *            Entity to be refreshed
	 * @param readLevel
	 *            Consistency Level for read
	 */
	public abstract void refresh(final Object entity, ConsistencyLevel readLevel);

	/**
	 * Initialize all lazy fields of a 'managed' entity, except WideMap fields.
	 * 
	 * Raise an <strong>IllegalStateException</strong> if the entity is not 'managed'
	 * 
	 */
	public <T> void initialize(final T entity)
	{
		log.debug("Force lazy fields initialization for entity {}", entity);
		final EntityMeta entityMeta = prepareEntityForInitialization(entity);
		initializer.initializeEntity(entity, entityMeta);
	}

	/**
	 * Initialize all lazy fields of a collection of 'managed' entities, except WideMap fields.
	 * 
	 * Raise an IllegalStateException if an entity is not 'managed'
	 * 
	 */
	public <T> void initialize(Collection<T> entities)
	{
		log.debug("Force lazy fields initialization for entity collection {}", entities);
		for (T entity : entities)
		{
			EntityMeta entityMeta = prepareEntityForInitialization(entity);
			initializer.initializeEntity(entity, entityMeta);
		}
	}

	/**
	 * Unproxy a 'managed' entity to prepare it for serialization
	 * 
	 * Raise an IllegalStateException if the entity is not managed'
	 * 
	 * @param proxy
	 * @return real object
	 */
	public <T> T unproxy(T proxy)
	{
		log.debug("Unproxying entity {}", proxy);

		T realObject = proxifier.unproxy(proxy);

		return realObject;
	}

	/**
	 * Unproxy a collection of 'managed' entities to prepare them for serialization
	 * 
	 * Raise an IllegalStateException if an entity is not managed' in the collection
	 * 
	 * @param proxy
	 *            collection
	 * @return real object collection
	 */
	public <T> Collection<T> unproxy(Collection<T> proxies)
	{
		log.debug("Unproxying collection of entities {}", proxies);

		return proxifier.unproxy(proxies);
	}

	/**
	 * Unproxy a list of 'managed' entities to prepare them for serialization
	 * 
	 * Raise an IllegalStateException if an entity is not managed' in the list
	 * 
	 * @param proxy
	 *            list
	 * @return real object list
	 */
	public <T> List<T> unproxy(List<T> proxies)
	{
		log.debug("Unproxying list of entities {}", proxies);

		return proxifier.unproxy(proxies);
	}

	/**
	 * Unproxy a set of 'managed' entities to prepare them for serialization
	 * 
	 * Raise an IllegalStateException if an entity is not managed' in the set
	 * 
	 * @param proxy
	 *            set
	 * @return real object set
	 */
	public <T> Set<T> unproxy(Set<T> proxies)
	{
		log.debug("Unproxying set of entities {}", proxies);

		return proxifier.unproxy(proxies);
	}

	/**
	 * Not supported operation. Will throw UnsupportedOperationException
	 */
	@Override
	public <T> T find(Class<T> entityClass, Object primaryKey, Map<String, Object> properties)
	{
		throw new UnsupportedOperationException("This operation is not supported for Cassandra");
	}

	/**
	 * Not supported operation. Will throw UnsupportedOperationException
	 */
	@Override
	public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode)
	{
		throw new UnsupportedOperationException("This operation is not supported for Cassandra");
	}

	/**
	 * Not supported operation. Will throw UnsupportedOperationException
	 */
	@Override
	public <T> T find(Class<T> entityClass, Object primaryKey, LockModeType lockMode,
			Map<String, Object> properties)
	{
		throw new UnsupportedOperationException("This operation is not supported for Cassandra");
	}

	/**
	 * Not supported operation. Will throw UnsupportedOperationException
	 */
	@Override
	public void flush()
	{
		throw new UnsupportedOperationException("This operation is not supported for Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public void setFlushMode(FlushModeType flushMode)
	{
		throw new UnsupportedOperationException("This operation is not supported for Cassandra");

	}

	/**
	 * Always return FlushModeType.AUTO
	 */
	@Override
	public FlushModeType getFlushMode()
	{
		return FlushModeType.AUTO;
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public void lock(Object entity, LockModeType lockMode)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public void lock(Object entity, LockModeType lockMode, Map<String, Object> properties)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public void refresh(Object entity, Map<String, Object> properties)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public void refresh(Object entity, LockModeType lockMode)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public void refresh(Object entity, LockModeType lockMode, Map<String, Object> properties)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public void detach(Object entity)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public LockModeType getLockMode(Object entity)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	@Override
	public void setProperty(String propertyName, Object value)
	{
		// TODO Allow setting config properties at runtime

	}

	@Override
	public Map<String, Object> getProperties()
	{
		// TODO Return config properties at runtime
		return new HashMap<String, Object>();
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public void clear()
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public boolean contains(Object entity)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public <T> TypedQuery<T> createQuery(CriteriaQuery<T> criteriaQuery)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public <T> TypedQuery<T> createQuery(String qlString, Class<T> resultClass)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public Query createQuery(String qlString)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public Query createNamedQuery(String name)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Entity Manager");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public <T> TypedQuery<T> createNamedQuery(String name, Class<T> resultClass)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Entity Manager");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public Query createNativeQuery(String sqlString)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public Query createNativeQuery(String sqlString, Class resultClass)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public Query createNativeQuery(String sqlString, String resultSetMapping)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public void joinTransaction()
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");

	}

	/**
	 * @return the ThriftEntityManager instance itself
	 */
	@Override
	public Object getDelegate()
	{
		return this;
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public void close()
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");

	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public boolean isOpen()
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public EntityTransaction getTransaction()
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public <T> T unwrap(Class<T> cls)
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	@Override
	public EntityManagerFactory getEntityManagerFactory()
	{
		return entityManagerFactory;
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public CriteriaBuilder getCriteriaBuilder()
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	/**
	 * Not supported operation. Will throw <strong>UnsupportedOperationException</strong>
	 */
	@Override
	public Metamodel getMetamodel()
	{
		throw new UnsupportedOperationException(
				"This operation is not supported for this Cassandra");
	}

	protected abstract AchillesPersistenceContext initPersistenceContext(Object entity);

	protected abstract AchillesPersistenceContext initPersistenceContext(Class<?> entityClass,
			Object primaryKey);

	private EntityMeta prepareEntityForInitialization(Object entity)
	{
		proxifier.ensureProxy(entity);
		Object realObject = proxifier.getRealObject(entity);
		EntityMeta entityMeta = entityMetaMap.get(realObject.getClass());
		return entityMeta;
	}

	protected Map<Class<?>, EntityMeta> getEntityMetaMap()
	{
		return entityMetaMap;
	}

	protected AchillesConsistencyLevelPolicy getConsistencyPolicy()
	{
		return consistencyPolicy;
	}

	protected AchillesConfigurationContext getConfigContext()
	{
		return configContext;
	}

	protected void setPersister(AchillesEntityPersister persister)
	{
		this.persister = persister;
	}

	protected void setLoader(AchillesEntityLoader loader)
	{
		this.loader = loader;
	}

	protected void setMerger(AchillesEntityMerger merger)
	{
		this.merger = merger;
	}

	protected void setRefresher(AchillesEntityRefresher refresher)
	{
		this.refresher = refresher;
	}

	protected void setProxifier(AchillesEntityProxifier proxifier)
	{
		this.proxifier = proxifier;
	}

	protected void setEntityValidator(AchillesEntityValidator entityValidator)
	{
		this.entityValidator = entityValidator;
	}

	protected void setInitializer(AchillesEntityInitializer initializer)
	{
		this.initializer = initializer;
	}

	protected void setEntityMetaMap(Map<Class<?>, EntityMeta> entityMetaMap)
	{
		this.entityMetaMap = entityMetaMap;
	}

	protected void setConsistencyPolicy(AchillesConsistencyLevelPolicy consistencyPolicy)
	{
		this.consistencyPolicy = consistencyPolicy;
	}

	protected void setConfigContext(AchillesConfigurationContext configContext)
	{
		this.configContext = configContext;
	}

}
