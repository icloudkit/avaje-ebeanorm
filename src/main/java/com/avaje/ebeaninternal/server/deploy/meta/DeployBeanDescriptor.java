package com.avaje.ebeaninternal.server.deploy.meta;

import com.avaje.ebean.RawSql;
import com.avaje.ebean.annotation.Cache;
import com.avaje.ebean.annotation.DocStore;
import com.avaje.ebean.annotation.DocStoreMode;
import com.avaje.ebean.config.ServerConfig;
import com.avaje.ebean.config.TableName;
import com.avaje.ebean.config.dbplatform.IdType;
import com.avaje.ebean.config.dbplatform.PlatformIdGenerator;
import com.avaje.ebean.event.BeanFindController;
import com.avaje.ebean.event.BeanPersistController;
import com.avaje.ebean.event.BeanPersistListener;
import com.avaje.ebean.event.BeanPostLoad;
import com.avaje.ebean.event.BeanQueryAdapter;
import com.avaje.ebean.event.changelog.ChangeLogFilter;
import com.avaje.ebean.text.PathProperties;
import com.avaje.ebean.util.CamelCaseHelper;
import com.avaje.ebeaninternal.api.ConcurrencyMode;
import com.avaje.ebeaninternal.server.core.CacheOptions;
import com.avaje.ebeaninternal.server.deploy.BeanDescriptor.EntityType;
import com.avaje.ebeaninternal.server.deploy.BeanDescriptorManager;
import com.avaje.ebeaninternal.server.deploy.ChainedBeanPersistController;
import com.avaje.ebeaninternal.server.deploy.ChainedBeanPersistListener;
import com.avaje.ebeaninternal.server.deploy.ChainedBeanPostLoad;
import com.avaje.ebeaninternal.server.deploy.ChainedBeanQueryAdapter;
import com.avaje.ebeaninternal.server.deploy.IndexDefinition;
import com.avaje.ebeaninternal.server.deploy.InheritInfo;
import com.avaje.ebeaninternal.server.deploy.parse.DeployBeanInfo;
import com.avaje.ebeaninternal.server.idgen.UuidIdGenerator;

import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes Beans including their deployment information.
 */
public class DeployBeanDescriptor<T> {

  private static final Map<String, String> EMPTY_NAMED_QUERY = new HashMap<String, String>();

  private static final Map<String, RawSql> EMPTY_RAW_MAP = new HashMap<String, RawSql>();

  private static class PropOrder implements Comparator<DeployBeanProperty> {

    public int compare(DeployBeanProperty o1, DeployBeanProperty o2) {

      int v2 = o1.getSortOrder();
      int v1 = o2.getSortOrder();
      return (v1 < v2 ? -1 : (v1 == v2 ? 0 : 1));
    }
  }

  private static final PropOrder PROP_ORDER = new PropOrder();

  private static final String I_SCALAOBJECT = "scala.ScalaObject";

  private final ServerConfig serverConfig;

  private final BeanDescriptorManager manager;

  /**
   * Map of BeanProperty Linked so as to preserve order.
   */
  private LinkedHashMap<String, DeployBeanProperty> propMap = new LinkedHashMap<String, DeployBeanProperty>();

  private Map<String, RawSql> namedRawSql;

  private Map<String, String> namedQuery;

  private EntityType entityType;

  private DeployBeanPropertyAssocOne<?> unidirectional;

  /**
   * Type of Identity generation strategy used.
   */
  private IdType idType;

  /**
   * Set to true if the identity is default for the platform.
   */
  private boolean idTypePlatformDefault;

  /**
   * The name of an IdGenerator (optional).
   */
  private String idGeneratorName;

  private PlatformIdGenerator idGenerator;

  /**
   * The database sequence name (optional).
   */
  private String sequenceName;

  private int sequenceInitialValue;

  private int sequenceAllocationSize;

  /**
   * Used with Identity columns but no getGeneratedKeys support.
   */
  private String selectLastInsertedId;

  /**
   * The concurrency mode for beans of this type.
   */
  private ConcurrencyMode concurrencyMode;

  private boolean updateChangesOnly;

  private List<IndexDefinition> indexDefinitions;

  /**
   * The base database table.
   */
  private String baseTable;

  private String baseTableAsOf;

  private String baseTableVersionsBetween;

  private String draftTable;

  private String[] dependentTables;

  private boolean historySupport;

  private boolean readAuditing;

  private boolean draftable;

  private boolean draftableElement;

  private TableName baseTableFull;

  private String[] properties;

  /**
   * The EntityBean type used to create new EntityBeans.
   */
  private final Class<T> beanType;

  private final List<BeanPersistController> persistControllers = new ArrayList<BeanPersistController>();
  private final List<BeanPersistListener> persistListeners = new ArrayList<BeanPersistListener>();
  private final List<BeanQueryAdapter> queryAdapters = new ArrayList<BeanQueryAdapter>();
  private final List<BeanPostLoad> postLoaders = new ArrayList<BeanPostLoad>();

  private CacheOptions cacheOptions = CacheOptions.NO_CACHING;

  /**
   * If set overrides the find implementation. Server side only.
   */
  private BeanFindController beanFinder;

  /**
   * The table joins for this bean. Server side only.
   */
  private final ArrayList<DeployTableJoin> tableJoinList = new ArrayList<DeployTableJoin>(2);

  /**
   * Inheritance information. Server side only.
   */
  private InheritInfo inheritInfo;

  private String name;

  private boolean processedRawSqlExtend;

  private ChangeLogFilter changeLogFilter;

  private String dbComment;

  /**
   * One of NONE, INDEX or EMBEDDED.
   */
  private boolean docStoreMapped;

  private DocStore docStore;

  private PathProperties docStorePathProperties;

  private String docStoreQueueId;

  private String docStoreIndexName;

  private String docStoreIndexType;

  private DocStoreMode docStorePersist;
  private DocStoreMode docStoreInsert;
  private DocStoreMode docStoreUpdate;
  private DocStoreMode docStoreDelete;

  private List<DeployBeanProperty> idProperties;

  /**
   * Construct the BeanDescriptor.
   */
  public DeployBeanDescriptor(BeanDescriptorManager manager, Class<T> beanType, ServerConfig serverConfig) {
    this.manager = manager;
    this.serverConfig = serverConfig;
    this.beanType = beanType;
  }

  /**
   * Return the DeployBeanInfo for the given bean class.
   */
  DeployBeanInfo<?> getDeploy(Class<?> cls) {
    return manager.getDeploy(cls);
  }

  /**
   * Return true if this beanType is an abstract class.
   */
  public boolean isAbstract() {
    return Modifier.isAbstract(beanType.getModifiers());
  }

  /**
   * Set to true for @History entity beans that have history.
   */
  public void setHistorySupport() {
    this.historySupport = true;
  }

  /**
   * Return true if this is an @History entity bean.
   */
  public boolean isHistorySupport() {
    return historySupport;
  }

  /**
   * Set read auditing on for this entity bean.
   */
  public void setReadAuditing() {
    readAuditing = true;
  }

  /**
   * Return true if read auditing is on for this entity bean.
   */
  public boolean isReadAuditing() {
    return readAuditing;
  }

  public void setDbComment(String dbComment) {
    this.dbComment = dbComment;
  }

  public String getDbComment() {
    return dbComment;
  }

  public void setDraftable() {
    draftable = true;
  }

  public boolean isDraftable() {
    return draftable;
  }

  public void setDraftableElement() {
    draftable = true;
    draftableElement = true;
  }

  public boolean isDraftableElement() {
    return draftableElement;
  }

  /**
   * Read the top level doc store deployment information.
   */
  public void readDocStore(DocStore docStore) {

    this.docStore = docStore;
    docStoreMapped = true;
    docStoreQueueId = docStore.queueId();
    docStoreIndexName = docStore.indexName();
    docStoreIndexType = docStore.indexType();
    docStorePersist = docStore.persist();
    docStoreInsert = docStore.insert();
    docStoreUpdate = docStore.update();
    docStoreDelete = docStore.delete();
    String doc = docStore.doc();
    if (!doc.isEmpty()) {
      docStorePathProperties = PathProperties.parse(doc);
    }
  }

  public boolean isScalaObject() {
    Class<?>[] interfaces = beanType.getInterfaces();
    for (int i = 0; i < interfaces.length; i++) {
      String iname = interfaces[i].getName();
      if (I_SCALAOBJECT.equals(iname)) {
        return true;
      }
    }
    return false;
  }

  public DeployBeanTable createDeployBeanTable() {

    DeployBeanTable beanTable = new DeployBeanTable(getBeanType());
    beanTable.setBaseTable(baseTable);
    beanTable.setIdProperties(propertiesId());

    return beanTable;
  }

  public void setEntityType(EntityType entityType) {
    this.entityType = entityType;
  }

  public boolean isEmbedded() {
    return EntityType.EMBEDDED.equals(entityType);
  }

  public boolean isBaseTableType() {
    EntityType et = getEntityType();
    return EntityType.ORM.equals(et);
  }

  public EntityType getEntityType() {
    if (entityType == null) {
      entityType = EntityType.ORM;
    }
    return entityType;
  }

  public void setSequenceInitialValue(int sequenceInitialValue) {
    this.sequenceInitialValue = sequenceInitialValue;
  }

  public void setSequenceAllocationSize(int sequenceAllocationSize) {
    this.sequenceAllocationSize = sequenceAllocationSize;
  }

  public int getSequenceInitialValue() {
    return sequenceInitialValue;
  }

  public int getSequenceAllocationSize() {
    return sequenceAllocationSize;
  }

  public String[] getProperties() {
    return properties;
  }

  public void setProperties(String[] props) {
    this.properties = props;
  }

  /**
   * Return the class type this BeanDescriptor describes.
   */
  public Class<T> getBeanType() {
    return beanType;
  }

  public void setChangeLogFilter(ChangeLogFilter changeLogFilter) {
    this.changeLogFilter = changeLogFilter;
  }

  public ChangeLogFilter getChangeLogFilter() {
    return changeLogFilter;
  }

  /**
   * Returns the Inheritance mapping information. This will be null if this type
   * of bean is not involved in any ORM inheritance mapping.
   */
  public InheritInfo getInheritInfo() {
    return inheritInfo;
  }

  /**
   * Set the ORM inheritance mapping information.
   */
  public void setInheritInfo(InheritInfo inheritInfo) {
    this.inheritInfo = inheritInfo;
  }

  /**
   * Enable L2 bean and query caching based on Cache annotation.
   */
  public void setCache(Cache cache) {

    String naturalKey = null;
    if (!cache.naturalKey().isEmpty()) {
      // find the property and mark as natural key property
      String propName = cache.naturalKey().trim();
      DeployBeanProperty beanProperty = getBeanProperty(propName);
      if (beanProperty != null) {
        beanProperty.setNaturalKey();
        naturalKey = propName;
      }
    }
    this.cacheOptions = new CacheOptions(cache, naturalKey);
  }

  /**
   * Return the cache options.
   */
  public CacheOptions getCacheOptions() {
    return cacheOptions;
  }

  public DeployBeanPropertyAssocOne<?> getUnidirectional() {
    return unidirectional;
  }

  public void setUnidirectional(DeployBeanPropertyAssocOne<?> unidirectional) {
    this.unidirectional = unidirectional;
  }

  /**
   * Return the concurrency mode used for beans of this type.
   */
  public ConcurrencyMode getConcurrencyMode() {
    return concurrencyMode;
  }

  /**
   * Set the concurrency mode used for beans of this type.
   */
  public void setConcurrencyMode(ConcurrencyMode concurrencyMode) {
    this.concurrencyMode = concurrencyMode;
  }

  public boolean isUpdateChangesOnly() {
    return updateChangesOnly;
  }

  public void setUpdateChangesOnly(boolean updateChangesOnly) {
    this.updateChangesOnly = updateChangesOnly;
  }

  /**
   * Add a compound unique constraint.
   */
  public void addIndex(IndexDefinition c) {
    if (indexDefinitions == null) {
      indexDefinitions = new ArrayList<IndexDefinition>();
    }
    indexDefinitions.add(c);
  }

  /**
   * Return the compound unique constraints (can be null).
   */
  public IndexDefinition[] getIndexDefinitions() {
    if (indexDefinitions == null) {
      return null;
    } else {
      return indexDefinitions.toArray(new IndexDefinition[indexDefinitions.size()]);
    }
  }

  /**
   * Return the beanFinder. Usually null unless overriding the finder.
   */
  public BeanFindController getBeanFinder() {
    return beanFinder;
  }

  /**
   * Set the BeanFinder to use for beans of this type. This is set to override
   * the finding from the default.
   */
  public void setBeanFinder(BeanFindController beanFinder) {
    this.beanFinder = beanFinder;
  }

  /**
   * Return the BeanPersistController (could be a chain of them, 1 or null).
   */
  public BeanPersistController getPersistController() {
    if (persistControllers.isEmpty()) {
      return null;
    } else if (persistControllers.size() == 1) {
      return persistControllers.get(0);
    } else {
      return new ChainedBeanPersistController(persistControllers);
    }
  }

  /**
   * Return the BeanPersistListener (could be a chain of them, 1 or null).
   */
  public BeanPersistListener getPersistListener() {
    if (persistListeners.isEmpty()) {
      return null;
    } else if (persistListeners.size() == 1) {
      return persistListeners.get(0);
    } else {
      return new ChainedBeanPersistListener(persistListeners);
    }
  }

  public BeanQueryAdapter getQueryAdapter() {
    if (queryAdapters.isEmpty()) {
      return null;
    } else if (queryAdapters.size() == 1) {
      return queryAdapters.get(0);
    } else {
      return new ChainedBeanQueryAdapter(queryAdapters);
    }
  }

  /**
   * Return the BeanPostLoad (could be a chain of them, 1 or null).
   */
  public BeanPostLoad getPostLoad() {
    if (postLoaders.isEmpty()) {
      return null;
    } else if (postLoaders.size() == 1) {
      return postLoaders.get(0);
    } else {
      return new ChainedBeanPostLoad(postLoaders);
    }
  }

  public void addPersistController(BeanPersistController controller) {
    persistControllers.add(controller);
  }

  public void addPersistListener(BeanPersistListener listener) {
    persistListeners.add(listener);
  }

  public void addQueryAdapter(BeanQueryAdapter queryAdapter) {
    queryAdapters.add(queryAdapter);
  }

  public void addPostLoad(BeanPostLoad postLoad) {
    postLoaders.add(postLoad);
  }

  public String getDraftTable() {
    return draftTable;
  }

  /**
   * For view based entity return the dependant tables.
   */
  public String[] getDependentTables() {
    return dependentTables;
  }

  /**
   * Return the base table. Only properties mapped to the base table are by
   * default persisted.
   */
  public String getBaseTable() {
    return baseTable;
  }

  /**
   * Return the base table with as of suffix.
   */
  public String getBaseTableAsOf() {
    return baseTableAsOf;
  }

  /**
   * Return the base table with versions between suffix.
   */
  public String getBaseTableVersionsBetween() {
    return baseTableVersionsBetween;
  }

  /**
   * Return the base table with full structure.
   */
  public TableName getBaseTableFull() {
    return baseTableFull;
  }

  /**
   * Set when entity is based on a view.
   */
  public void setView(String viewName, String[] dependentTables) {
    this.entityType = EntityType.VIEW;
    this.dependentTables = dependentTables;
    setBaseTable(new TableName(viewName), "", "");
  }

  /**
   * Set the base table. Only properties mapped to the base table are by default persisted.
   */
  public void setBaseTable(TableName baseTableFull, String asOfSuffix, String versionsBetweenSuffix) {
    this.baseTableFull = baseTableFull;
    this.baseTable = baseTableFull == null ? null : baseTableFull.getQualifiedName();
    this.baseTableAsOf = baseTable + asOfSuffix;
    this.baseTableVersionsBetween = baseTable + versionsBetweenSuffix;
    this.draftTable = (draftable) ? baseTable+"_draft" : baseTable;
  }

  public void sortProperties() {

    ArrayList<DeployBeanProperty> list = new ArrayList<DeployBeanProperty>();
    list.addAll(propMap.values());

    Collections.sort(list, PROP_ORDER);

    propMap = new LinkedHashMap<String, DeployBeanProperty>(list.size());
    for (int i = 0; i < list.size(); i++) {
      addBeanProperty(list.get(i));
    }
  }

  /**
   * Add a bean property.
   */
  public DeployBeanProperty addBeanProperty(DeployBeanProperty prop) {
    return propMap.put(prop.getName(), prop);
  }

  /**
   * Find the matching property for a given property name or dbColumn.
   * <p>
   * This is primarily to find imported primary key columns (ManyToOne that also match the PK).
   * </p>
   */
  DeployBeanProperty findMatch(String propertyName, String dbColumn) {

    DeployBeanProperty prop = propMap.get(propertyName);
    if (prop != null) {
      return prop;
    }
    if (dbColumn != null) {
      String asCamel = CamelCaseHelper.toCamelFromUnderscore(dbColumn);
      prop = propMap.get(asCamel);
      if (prop != null) {
        return prop;
      }
      // scan looking for dbColumn match
      for (DeployBeanProperty property : propMap.values()) {
        if (dbColumn.equals(property.getDbColumn())) {
          return property;
        }
      }
    }
    return null;
  }

  /**
   * Get a BeanProperty by its name.
   */
  public DeployBeanProperty getBeanProperty(String propName) {
    return propMap.get(propName);
  }

  /**
   * Return the bean class name this descriptor is used for.
   * <p>
   * If this BeanDescriptor is for a table then this returns the table name
   * instead.
   * </p>
   */
  public String getFullName() {
    return beanType.getName();
  }

  /**
   * Return the bean short name.
   */
  public String getName() {
    return name;
  }

  /**
   * Set the bean shortName.
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Return the identity generation type.
   */
  public IdType getIdType() {
    return idType;
  }

  /**
   * Set the identity generation type.
   */
  public void setIdType(IdType idType) {
    this.idType = idType;
  }

  /**
   * Set when the identity type is the platform default.
   */
  public void setIdTypePlatformDefault() {
    this.idTypePlatformDefault = true;
  }

  /**
   * Return true when the identity is the platform default.
   */
  public boolean isIdTypePlatformDefault() {
    return idTypePlatformDefault;
  }

  /**
   * Return the DB sequence name (can be null).
   */
  public String getSequenceName() {
    return sequenceName;
  }

  /**
   * Set the DB sequence name.
   */
  private void setSequenceName(String sequenceName) {
    this.sequenceName = sequenceName;
  }

  /**
   * Return the SQL used to return the last inserted Id.
   * <p>
   * Used with Identity columns where getGeneratedKeys is not supported.
   * </p>
   */
  public String getSelectLastInsertedId() {
    return selectLastInsertedId;
  }

  /**
   * Set the SQL used to return the last inserted Id.
   */
  public void setSelectLastInsertedId(String selectLastInsertedId) {
    this.selectLastInsertedId = selectLastInsertedId;
  }

  /**
   * Return the name of the IdGenerator that should be used with this type of
   * bean. A null value could be used to specify the 'default' IdGenerator.
   */
  public String getIdGeneratorName() {
    return idGeneratorName;
  }

  /**
   * Set the name of the IdGenerator that should be used with this type of bean.
   */
  public void setIdGeneratorName(String idGeneratorName) {
    this.idGeneratorName = idGeneratorName;
  }

  /**
   * Return the actual IdGenerator for this bean type (can be null).
   */
  public PlatformIdGenerator getIdGenerator() {
    return idGenerator;
  }

  /**
   * Set the actual IdGenerator for this bean type.
   */
  public void setIdGenerator(PlatformIdGenerator idGenerator) {
    this.idGenerator = idGenerator;
    if (idGenerator != null && idGenerator.isDbSequence()) {
      setSequenceName(idGenerator.getName());
    }
  }

  /**
   * Assign the standard UUID generator.
   */
  public void setUuidGenerator() {
    this.idType = IdType.EXTERNAL;
    this.idGeneratorName = UuidIdGenerator.AUTO_UUID;
    this.idGenerator = UuidIdGenerator.INSTANCE;
  }

  /**
   * Assign a custom external IdGenerator.
   */
  public void setCustomIdGenerator(PlatformIdGenerator idGenerator) {
    this.idType = IdType.EXTERNAL;
    this.idGeneratorName = idGenerator.getName();
    this.idGenerator = idGenerator;
  }

  /**
   * Summary description.
   */
  public String toString() {
    return getFullName();
  }

  /**
   * Add a TableJoin to this type of bean. For Secondary table properties.
   */
  public void addTableJoin(DeployTableJoin join) {
    tableJoinList.add(join);
  }

  List<DeployTableJoin> getTableJoins() {
    return tableJoinList;
  }

  /**
   * Return a collection of all BeanProperty deployment information.
   */
  public Collection<DeployBeanProperty> propertiesAll() {
    return propMap.values();
  }

  /**
   * Return the defaultSelectClause using FetchType.LAZY and FetchType.EAGER.
   */
  public String getDefaultSelectClause() {

    StringBuilder sb = new StringBuilder();

    boolean hasLazyFetch = false;

    for (DeployBeanProperty prop : propMap.values()) {
      if (!prop.isTransient() && !(prop instanceof DeployBeanPropertyAssocMany<?>)) {
        if (prop.isFetchEager()) {
          sb.append(prop.getName()).append(",");
        } else {
          hasLazyFetch = true;
        }
      }
    }

    if (!hasLazyFetch) {
      return null;
    }
    String selectClause = sb.toString();
    if (selectClause.isEmpty()) {
      throw new IllegalStateException("Bean " + getFullName() + " has no properties?");
    }
    return selectClause.substring(0, selectClause.length() - 1);
  }

  /**
   * Return true if the primary key is a compound key or if it's database type
   * is non-numeric (and hence not suitable for db identity or sequence.
   */
  public boolean isPrimaryKeyCompoundOrNonNumeric() {

    List<DeployBeanProperty> ids = propertiesId();
    if (ids.size() != 1) {
      // compound key
      return true;
    }
    DeployBeanProperty p = ids.get(0);
    if (p instanceof DeployBeanPropertyAssocOne<?>) {
      return ((DeployBeanPropertyAssocOne<?>) p).isCompound();
    } else {
      return !p.isDbNumberType();
    }
  }

  /**
   * Return the Primary Key column assuming it is a single column (not
   * compound). This is for the purpose of defining a sequence name.
   */
  public String getSinglePrimaryKeyColumn() {
    List<DeployBeanProperty> ids = propertiesId();
    if (ids.size() == 1) {
      DeployBeanProperty p = ids.get(0);
      if (p instanceof DeployBeanPropertyAssoc<?>) {
        // its a compound primary key
        return null;
      } else {
        return p.getDbColumn();
      }
    }
    return null;
  }

  /**
   * Return the BeanProperty that make up the unique id.
   * <p>
   * The order of these properties can be relied on to be consistent if the bean
   * itself doesn't change or the xml deployment order does not change.
   * </p>
   */
  public List<DeployBeanProperty> propertiesId() {

    if (idProperties == null) {
      idProperties = new ArrayList<DeployBeanProperty>(2);
      for (DeployBeanProperty prop : propMap.values()) {
        if (prop.isId()) {
          idProperties.add(prop);
        }
      }
    }
    return idProperties;
  }

  public DeployBeanPropertyAssocOne<?> findJoinToTable(String tableName) {

    List<DeployBeanPropertyAssocOne<?>> assocOne = propertiesAssocOne();
    for (DeployBeanPropertyAssocOne<?> prop : assocOne) {
      DeployTableJoin tableJoin = prop.getTableJoin();
      if (tableJoin != null && tableJoin.getTable().equalsIgnoreCase(tableName)) {
        return prop;
      }
    }
    return null;
  }

  /**
   * Return an Iterator of BeanPropertyAssocOne that are not embedded. These are
   * effectively joined beans. For ManyToOne and OneToOne associations.
   */
  public List<DeployBeanPropertyAssocOne<?>> propertiesAssocOne() {

    ArrayList<DeployBeanPropertyAssocOne<?>> list = new ArrayList<DeployBeanPropertyAssocOne<?>>();

    for (DeployBeanProperty prop : propMap.values()) {
      if (prop instanceof DeployBeanPropertyAssocOne<?>) {
        if (!prop.isEmbedded()) {
          list.add((DeployBeanPropertyAssocOne<?>) prop);
        }
      }
    }

    return list;

  }

  /**
   * Return BeanPropertyAssocMany for this descriptor.
   */
  public List<DeployBeanPropertyAssocMany<?>> propertiesAssocMany() {

    ArrayList<DeployBeanPropertyAssocMany<?>> list = new ArrayList<DeployBeanPropertyAssocMany<?>>();

    for (DeployBeanProperty prop : propMap.values()) {
      if (prop instanceof DeployBeanPropertyAssocMany<?>) {
        list.add((DeployBeanPropertyAssocMany<?>) prop);
      }
    }

    return list;
  }

  /**
   * base properties without the unique id properties.
   */
  public List<DeployBeanProperty> propertiesBase() {

    ArrayList<DeployBeanProperty> list = new ArrayList<DeployBeanProperty>();

    for (DeployBeanProperty prop : propMap.values()) {
      if (!(prop instanceof DeployBeanPropertyAssoc<?>) && !prop.isId()) {
        list.add(prop);
      }
    }

    return list;
  }

  /**
   * Check the mapping for class inheritance
   */
  public void checkInheritanceMapping() {
    if (inheritInfo == null) {
      checkInheritance(getBeanType());
    }
  }

  /**
   * Check valid mapping annotations on the class hierarchy.
   */
  private void checkInheritance(Class<?> beanType) {

    Class<?> parent = beanType.getSuperclass();
    if (parent == null || Object.class.equals(parent)) {
      // all good
      return;
    }
    if (parent.isAnnotationPresent(Entity.class)) {
      String msg = "Checking " + getBeanType() + " and found " + parent + " that has @Entity annotation rather than MappedSuperclass?";
      throw new IllegalStateException(msg);
    }
    if (parent.isAnnotationPresent(MappedSuperclass.class)) {
      // continue checking
      checkInheritance(parent);
    }
  }

  public PathProperties getDocStorePathProperties() {
    return docStorePathProperties;
  }

  /**
   * Return true if this type is mapped for a doc store.
   */
  public boolean isDocStoreMapped() {
    return docStoreMapped;
  }

  public String getDocStoreQueueId() {
    return docStoreQueueId;
  }

  public String getDocStoreIndexName() {
    return docStoreIndexName;
  }

  public String getDocStoreIndexType() {
    return docStoreIndexType;
  }

  public DocStore getDocStore() {
    return docStore;
  }

  /**
   * Return the DocStore index behavior for bean inserts.
   */
  public DocStoreMode getDocStoreInsertEvent() {
    return getDocStoreIndexEvent(docStoreInsert);
  }

  /**
   * Return the DocStore index behavior for bean updates.
   */
  public DocStoreMode getDocStoreUpdateEvent() {
    return getDocStoreIndexEvent(docStoreUpdate);
  }

  /**
   * Return the DocStore index behavior for bean deletes.
   */
  public DocStoreMode getDocStoreDeleteEvent() {
    return getDocStoreIndexEvent(docStoreDelete);
  }

  private DocStoreMode getDocStoreIndexEvent(DocStoreMode mostSpecific) {
    if (!docStoreMapped) {
      return DocStoreMode.IGNORE;
    }
    if (mostSpecific != DocStoreMode.DEFAULT) return mostSpecific;
    if (docStorePersist != DocStoreMode.DEFAULT) return docStorePersist;
    return serverConfig.getDocStoreConfig().getPersist();
  }

  /**
   * Return the named ORM queries.
   */
  public Map<String, String> getNamedQuery() {
    return (namedQuery != null) ? namedQuery : EMPTY_NAMED_QUERY;
  }

  /**
   * Add a named query.
   */
  public void addNamedQuery(String name, String query) {
    if (namedQuery == null) {
      namedQuery = new LinkedHashMap<String, String>();
    }
    namedQuery.put(name, query);
  }

  /**
   * Return the named RawSql queries.
   */
  public Map<String, RawSql> getNamedRawSql() {
    return (namedRawSql != null) ? namedRawSql : EMPTY_RAW_MAP;
  }

  /**
   * Add a named RawSql from ebean.xml file.
   */
  public void addRawSql(String name, RawSql rawSql) {
    if (namedRawSql == null) {
      namedRawSql = new HashMap<String, RawSql>();
    }
    namedRawSql.put(name, rawSql);
  }
}
