/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.slider.server.appmaster.web.rest.application;

import com.google.common.collect.Lists;
import org.apache.hadoop.yarn.exceptions.YarnRuntimeException;
import org.apache.hadoop.yarn.webapp.NotFoundException;
import org.apache.slider.api.types.SerializedComponentInformation;
import org.apache.slider.api.types.SerializedContainerInformation;
import org.apache.slider.core.conf.AggregateConf;
import org.apache.slider.core.conf.ConfTree;
import org.apache.slider.core.exceptions.NoSuchNodeException;
import org.apache.slider.server.appmaster.state.RoleInstance;
import org.apache.slider.server.appmaster.state.RoleStatus;
import org.apache.slider.server.appmaster.state.StateAccessForProviders;
import org.apache.slider.server.appmaster.web.WebAppApi;
import org.apache.slider.server.appmaster.web.rest.AbstractSliderResource;
import static org.apache.slider.server.appmaster.web.rest.RestPaths.*;

import org.apache.slider.server.appmaster.web.rest.application.actions.RestActionStop;
import org.apache.slider.server.appmaster.web.rest.application.actions.StopResponse;
import org.apache.slider.server.appmaster.web.rest.application.resources.AggregateModelRefresher;
import org.apache.slider.server.appmaster.web.rest.application.resources.AppconfRefresher;
import org.apache.slider.server.appmaster.web.rest.application.resources.CachedContent;
import org.apache.slider.server.appmaster.web.rest.application.resources.LiveContainersRefresher;
import org.apache.slider.server.appmaster.web.rest.application.resources.ContentCache;
import org.apache.slider.server.appmaster.web.rest.application.resources.LiveComponentsRefresher;
import org.apache.slider.server.appmaster.web.rest.application.resources.LiveResourcesRefresher;
import org.apache.slider.server.appmaster.web.rest.application.actions.RestActionPing;
import org.apache.slider.server.appmaster.web.rest.application.resources.PingResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import static javax.ws.rs.core.MediaType.*;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
@SuppressWarnings("unchecked")
public class ApplicationResource extends AbstractSliderResource {
  private static final Logger log =
      LoggerFactory.getLogger(ApplicationResource.class);

  public static final int LIFESPAN = 1000;
  public static final List<String> LIVE_ENTRIES = toJsonList("resources",
      "containers",
      "components",
      "nodes",
      "statistics",
      "internal");

  public static final List<String> ROOT_ENTRIES =
      toJsonList("model", "live", "actions");

  public static final List<String> MODEL_ENTRIES =
      toJsonList("desired", "resolved");

  /**
   * This is the cache of all content ... each entry is
   * designed to be self-refreshing on get operations, 
   * so is never very out of date, yet many GETs don't
   * overload the rest of the system.
   */
  private final ContentCache cache = new ContentCache();
  private final StateAccessForProviders state;

  public ApplicationResource(WebAppApi slider) {
    super(slider);
    state = slider.getAppState();
    cache.put(LIVE_RESOURCES,
        new CachedContent<ConfTree>(LIFESPAN,
            new LiveResourcesRefresher(state)));
    cache.put(LIVE_CONTAINERS,
        new CachedContent<Map<String, SerializedContainerInformation>>(LIFESPAN,
            new LiveContainersRefresher(state)));
    cache.put(LIVE_COMPONENTS,
        new CachedContent<Map<String, SerializedComponentInformation>> (LIFESPAN,
            new LiveComponentsRefresher(state)));
    cache.put(MODEL_DESIRED,
        new CachedContent<AggregateConf>(LIFESPAN,
            new AggregateModelRefresher(state, false)));
    cache.put(MODEL_RESOLVED,
        new CachedContent<AggregateConf>(LIFESPAN,
            new AggregateModelRefresher(state, true)));
    cache.put(MODEL_RESOLVED_APPCONF,
        new CachedContent<ConfTree>(LIFESPAN,
            new AppconfRefresher(state, false, false)));
    cache.put(MODEL_RESOLVED_RESOURCES,
        new CachedContent<ConfTree>(LIFESPAN,
            new AppconfRefresher(state, false, true)));
    cache.put(MODEL_DESIRED_APPCONF,
        new CachedContent<ConfTree>(LIFESPAN,
            new AppconfRefresher(state, true, false)));
    cache.put(MODEL_DESIRED_RESOURCES,
        new CachedContent<ConfTree>(LIFESPAN,
            new AppconfRefresher(state, true, true)));
  }

  /**
   * Build a new JSON-marshallable list of string elements
   * @param elements elements
   * @return something that can be returned
   */
  private static List<String> toJsonList(String... elements) {
    return Lists.newArrayList(elements);
  }

  @GET
  @Path("/")
  @Produces({APPLICATION_JSON})
  public List<String> getRoot() {
    markGet(SLIDER_SUBPATH_APPLICATION);
    return ROOT_ENTRIES;
  }

  /**
   * Enum model values: desired and resolved
   * @return the desired and resolved model
   */
  @GET
  @Path(MODEL)
  @Produces({APPLICATION_JSON})
  public List<String> getModel() {
    markGet(SLIDER_SUBPATH_APPLICATION, MODEL);
    return MODEL_ENTRIES;
  }

  @GET
  @Path(MODEL_DESIRED)
  @Produces({APPLICATION_JSON})
  public AggregateConf getModelDesired() {
    markGet(SLIDER_SUBPATH_APPLICATION, MODEL_DESIRED);
    return lookupAggregateConf(MODEL_DESIRED);
  }
  
  @GET
  @Path(MODEL_DESIRED_APPCONF)
  @Produces({APPLICATION_JSON})
  public ConfTree getModelDesiredAppconf() {
    markGet(SLIDER_SUBPATH_APPLICATION, MODEL_DESIRED_APPCONF);
    return lookupConfTree(MODEL_DESIRED_APPCONF);
  }

  @GET
  @Path(MODEL_DESIRED_RESOURCES)
  @Produces({APPLICATION_JSON})
  public ConfTree getModelDesiredResources() {
    markGet(SLIDER_SUBPATH_APPLICATION, MODEL_DESIRED_RESOURCES);
    return lookupConfTree(MODEL_DESIRED_RESOURCES);
  }
  
  @GET
  @Path(MODEL_RESOLVED)
  @Produces({APPLICATION_JSON})
  public AggregateConf getModelResolved() {
    markGet(SLIDER_SUBPATH_APPLICATION, MODEL_RESOLVED);
    return lookupAggregateConf(MODEL_RESOLVED);
  }

  @GET
  @Path(MODEL_RESOLVED_APPCONF)
  @Produces({APPLICATION_JSON})
  public ConfTree getModelResolvedAppconf() {
    markGet(SLIDER_SUBPATH_APPLICATION, MODEL_RESOLVED_APPCONF);
    return lookupConfTree(MODEL_RESOLVED_APPCONF);
  }

  @GET
  @Path(MODEL_RESOLVED_RESOURCES)
  @Produces({APPLICATION_JSON})
  public ConfTree getModelResolvedResources() {
    markGet(SLIDER_SUBPATH_APPLICATION, MODEL_RESOLVED_RESOURCES);
    return lookupConfTree(MODEL_RESOLVED_RESOURCES);
  }
  
  @GET
  @Path(LIVE)
  @Produces({APPLICATION_JSON})
  public List<String> getLive() {
    markGet(SLIDER_SUBPATH_APPLICATION, LIVE);
    return LIVE_ENTRIES;
  }

  @GET
  @Path(LIVE_RESOURCES)
  @Produces({APPLICATION_JSON})
  public Object getLiveResources() {
    markGet(SLIDER_SUBPATH_APPLICATION, LIVE_RESOURCES);
    return lookupConfTree(LIVE_RESOURCES);
  }
  
  @GET
  @Path(LIVE_CONTAINERS)
  @Produces({APPLICATION_JSON})
  public Map<String, SerializedContainerInformation> getLiveContainers() {
    markGet(SLIDER_SUBPATH_APPLICATION, LIVE_CONTAINERS);
    try {
      return (Map<String, SerializedContainerInformation>)cache.lookup(
          LIVE_CONTAINERS);
    } catch (Exception e) {
      throw buildException(LIVE_CONTAINERS, e);
    }
  }

  @GET
  @Path(LIVE_CONTAINERS + "/{containerId}")
  @Produces({APPLICATION_JSON})
  public SerializedContainerInformation getLiveContainer(
      @PathParam("containerId") String containerId) {
    markGet(SLIDER_SUBPATH_APPLICATION, LIVE_CONTAINERS);
    try {
      RoleInstance id = state.getLiveInstanceByContainerID(containerId);
      return id.serialize();
    } catch (NoSuchNodeException e) {
      throw new NotFoundException("Unknown container: " + containerId);
    } catch (Exception e) {
      throw buildException(LIVE_CONTAINERS + "/"+ containerId, e);
    }
  }

  @GET
  @Path(LIVE_COMPONENTS)
  @Produces({APPLICATION_JSON})
  public Map<String, SerializedComponentInformation> getLiveComponents() {
    markGet(SLIDER_SUBPATH_APPLICATION, LIVE_COMPONENTS);
    try {
      return (Map<String, SerializedComponentInformation>) cache.lookup(
          LIVE_COMPONENTS);
    } catch (Exception e) {
      throw buildException(LIVE_COMPONENTS, e);
    }
  }
  
  @GET
  @Path(LIVE_COMPONENTS + "/{component}")
  @Produces({APPLICATION_JSON})
  public SerializedComponentInformation getLiveComponent(
      @PathParam("component") String component) {
    markGet(SLIDER_SUBPATH_APPLICATION, LIVE_COMPONENTS);
    try {
      RoleStatus roleStatus = state.lookupRoleStatus(component);
      SerializedComponentInformation info = roleStatus.serialize();
      List<RoleInstance> containers = lookupRoleContainers(component);
      info.containers = new ArrayList<String>(containers.size());
      for (RoleInstance container : containers) {
        info.containers.add(container.id);
      }
      return info;
    } catch (YarnRuntimeException e) {
      throw new NotFoundException("Unknown component: " + component);
    } catch (Exception e) {
      throw buildException(LIVE_CONTAINERS, e);
    }
  }

  /**
   * Look up all containers of a specific component name 
   * @param component component/role name
   * @return list of instances. This is a snapshot
   */
  private List<RoleInstance> lookupRoleContainers(String component) {
    RoleStatus roleStatus = state.lookupRoleStatus(component);
    List<RoleInstance> ownedContainerList = state.cloneOwnedContainerList();
    List<RoleInstance> matching = new ArrayList<RoleInstance>(ownedContainerList.size());
    int roleId = roleStatus.getPriority();
    for (RoleInstance instance : ownedContainerList) {
      if (instance.roleId == roleId) {
        matching.add(instance);
      }
    }
    return matching;
  }

  protected AggregateConf lookupAggregateConf(String key) {
    try {
      return (AggregateConf) cache.lookup(key);
    } catch (Exception e) {
      throw buildException(key, e);
    }
  }

  protected ConfTree lookupConfTree(String key) {
    try {
      return (ConfTree) cache.lookup(key);
    } catch (Exception e) {
      throw buildException(key, e);
    }
  }

  /* ************************************************************************
  
  ACTION PING
  
  **************************************************************************/
  
  @GET
  @Path(ACTION_PING)
  @Produces({APPLICATION_JSON})
  public PingResource actionPingGet(@Context HttpServletRequest request,
      @Context UriInfo uriInfo) {
    markGet(SLIDER_SUBPATH_APPLICATION, ACTION_PING);
    return new RestActionPing().ping(request, uriInfo, "");
  }
  
  @POST
  @Path(ACTION_PING)
  @Produces({APPLICATION_JSON})
  public PingResource actionPingPost(@Context HttpServletRequest request,
      @Context UriInfo uriInfo,
      String body) {
    markPost(SLIDER_SUBPATH_APPLICATION, ACTION_PING);
    return new RestActionPing().ping(request, uriInfo, body);
  }
  
  @PUT
  @Path(ACTION_PING)
  @Consumes({TEXT_PLAIN})
  @Produces({APPLICATION_JSON})
  public PingResource actionPingPut(@Context HttpServletRequest request,
      @Context UriInfo uriInfo,
      String body) {
    markPut(SLIDER_SUBPATH_APPLICATION, ACTION_PING);
    return new RestActionPing().ping(request, uriInfo, body);
  }
  
  @DELETE
  @Path(ACTION_PING)
  @Consumes({APPLICATION_JSON})
  @Produces({APPLICATION_JSON})
  public PingResource actionPingDelete(@Context HttpServletRequest request,
      @Context UriInfo uriInfo) {
    markDelete(SLIDER_SUBPATH_APPLICATION, ACTION_PING);
    return new RestActionPing().ping(request, uriInfo, "");
  }
  
  @HEAD
  @Path(ACTION_PING)
  public Object actionPingHead(@Context HttpServletRequest request,
      @Context UriInfo uriInfo) {
    mark("HEAD", SLIDER_SUBPATH_APPLICATION, ACTION_PING);
    return new RestActionPing().ping(request, uriInfo, "");
  }
  
  /* ************************************************************************
  
  ACTION STOP
  
  **************************************************************************/


  @POST
  @Path(ACTION_STOP)
  @Produces({APPLICATION_JSON})
  public StopResponse actionStop(@Context HttpServletRequest request,
      @Context UriInfo uriInfo,
      String body) {
    markPost(SLIDER_SUBPATH_APPLICATION, ACTION_STOP);
    return new RestActionStop(slider).stop(request, uriInfo, body);
  }

}
