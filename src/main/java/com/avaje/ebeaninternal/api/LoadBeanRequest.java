package com.avaje.ebeaninternal.api;

import com.avaje.ebean.bean.EntityBean;
import com.avaje.ebean.bean.EntityBeanIntercept;
import com.avaje.ebeaninternal.server.core.OrmQueryRequest;
import com.avaje.ebeaninternal.server.deploy.BeanDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Request for loading ManyToOne and OneToOne relationships.
 */
public class LoadBeanRequest extends LoadRequest {

  private static final Logger logger = LoggerFactory.getLogger(LoadBeanRequest.class);

  private final List<EntityBeanIntercept> batch;

  private final LoadBeanBuffer loadBuffer;

  private final String lazyLoadProperty;

  private final boolean loadCache;

  /**
   * Construct for lazy load request.
   */
  public LoadBeanRequest(LoadBeanBuffer LoadBuffer, String lazyLoadProperty, boolean loadCache) {
    this(LoadBuffer, null, true, lazyLoadProperty, loadCache);
  }

  /**
   * Construct for secondary query.
   */
  public LoadBeanRequest(LoadBeanBuffer LoadBuffer, OrmQueryRequest<?> parentRequest) {
    this(LoadBuffer, parentRequest, false, null, false);
  }

  private LoadBeanRequest(LoadBeanBuffer loadBuffer, OrmQueryRequest<?> parentRequest, boolean lazy,
                          String lazyLoadProperty, boolean loadCache) {

    super(parentRequest, lazy);
    this.loadBuffer = loadBuffer;
    this.batch = loadBuffer.getBatch();
    this.lazyLoadProperty = lazyLoadProperty;
    this.loadCache = loadCache;
  }

  @Override
  public Class<?> getBeanType() {
    return loadBuffer.getBeanDescriptor().getBeanType();
  }

  public boolean isLoadCache() {
    return loadCache;
  }


  public String getDescription() {
    return "path:" + loadBuffer.getFullPath() + " batch:" + batch.size();
  }

  /**
   * Return the batch of beans to actually load.
   */
  public List<EntityBeanIntercept> getBatch() {
    return batch;
  }

  /**
   * Return the load context.
   */
  public LoadBeanBuffer getLoadContext() {
    return loadBuffer;
  }

  /**
   * Return the property that invoked the lazy loading.
   */
  public String getLazyLoadProperty() {
    return lazyLoadProperty;
  }

  public int getBatchSize() {
    return getLoadContext().getBatchSize();
  }

  /**
   * Return the list of Id values for the beans in the lazy load buffer.
   */
  public List<Object> getIdList(int batchSize) {

    List<Object> idList = new ArrayList<Object>(batchSize);

    BeanDescriptor<?> desc = loadBuffer.getBeanDescriptor();
    for (int i = 0; i < batch.size(); i++) {
      EntityBeanIntercept ebi = batch.get(i);
      EntityBean bean = ebi.getOwner();
      idList.add(desc.getId(bean));
    }

    if (!idList.isEmpty()) {
      int extraIds = batchSize - batch.size();
      if (extraIds > 0) {
        // for performance make up the Id's to the batch size
        // so we get the same query (for Ebean and the db)
        Object firstId = idList.get(0);
        for (int i = 0; i < extraIds; i++) {
          // just add the first Id again
          idList.add(firstId);
        }
      }
    }

    return idList;
  }

  /**
   * Configure the query for lazy loading execution.
   */
  public void configureQuery(SpiQuery<?> query, List<Object> idList) {

    query.setMode(SpiQuery.Mode.LAZYLOAD_BEAN);
    query.setPersistenceContext(loadBuffer.getPersistenceContext());

    String mode = isLazy() ? "+lazy" : "+query";
    query.setLoadDescription(mode, getDescription());

    if (isLazy()) {
      // cascade the batch size (if set) for further lazy loading
      query.setLazyLoadBatchSize(getBatchSize());
    }

    loadBuffer.configureQuery(query, lazyLoadProperty);

    if (idList.size() == 1) {
      query.where().idEq(idList.get(0));
    } else {
      query.where().idIn(idList);
    }
  }

  /**
   * Load the beans into the L2 cache if that is requested and check for load failures due to deletes.
   */
  public void postLoad(List<?> list) {

    Set<Object> loadedIds = new HashSet<Object>();

    BeanDescriptor<?> desc = loadBuffer.getBeanDescriptor();
    // collect Ids and maybe load bean cache
    for (int i = 0; i < list.size(); i++) {
      EntityBean loadedBean = (EntityBean) list.get(i);
      loadedIds.add(desc.getId(loadedBean));
      if (isLoadCache()) {
        desc.cacheBeanPut(loadedBean);
      }
    }

    if (lazyLoadProperty != null) {
      for (int i = 0; i < batch.size(); i++) {
        // check if the underlying row in DB was deleted. Mark the bean as 'failed' if
        // necessary but allow processing to continue until it is accessed by client code
        EntityBeanIntercept ebi = batch.get(i);
        Object id = desc.getId(ebi.getOwner());
        if (!loadedIds.contains(id)) {
          logger.info("Lazy loading unsuccessful for type:" + desc.getName() + " id:" + id + " - expecting when bean has been deleted");
          ebi.setLazyLoadFailure(id);
        }
      }
    }
  }
}
