/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.cellar.dosgi;

import org.apache.karaf.cellar.core.ClusterManager;
import org.apache.karaf.cellar.core.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Listener called when a new service is exported.
 */
public class ServiceTracker implements Runnable {

    private static final transient Logger LOGGER = LoggerFactory.getLogger(ServiceTracker.class);

    private ClusterManager clusterManager;

    private Map<String, EndpointDescription> remoteEndpoints;

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public void init() {
        LOGGER.info("CELLAR SERVICE TRACKER: a new Task initialized");
        remoteEndpoints = clusterManager.getMap(Constants.REMOTE_ENDPOINTS);
        scheduler.scheduleWithFixedDelay(this, 10, 10, TimeUnit.SECONDS);
    }

    public void destroy() {
        LOGGER.info("CELLAR SERVICE TRACKER: task is being destroyed");
        scheduler.shutdown();
    }

    public ClusterManager getClusterManager() {
        return clusterManager;
    }

    public void setClusterManager(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
    }


    @Override
    public void run() {
        LOGGER.trace("SERVICE TRACKER: running the service tracker task");
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            if (!remoteEndpoints.isEmpty()) {
                LOGGER.trace("SERVICE TRACKER: Founded {} remote endpoints", remoteEndpoints.size());
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                final Set<Node> activeNodes = clusterManager.listNodes();
                
                // create a clone of remote endpoint to avoid concurrency concerns while iterating it
                final Set<Map.Entry<String, EndpointDescription>> list = new HashSet<Map.Entry<String, EndpointDescription>>(remoteEndpoints.entrySet());
                
                for (Map.Entry<String, EndpointDescription> entry : list) {
                    final EndpointDescription endpointDescription = entry.getValue();
                    final String key = entry.getKey();
                    
                    // create a clone of nodes to avoid concurrency concerns while iterating it
                    final Set<Node> nodes = new HashSet<Node>(endpointDescription.getNodes());
                    
                    boolean endpointChanged = false;
                    for(Node n : nodes) {
                        if(!activeNodes.contains(n)) {
                            LOGGER.debug("SERVICE TRACKER: Removing node with id {} since it is not active", n.getId());
                            endpointDescription.getNodes().remove(n);
                            endpointChanged = true;                        
                        }
                    }

                    if(endpointChanged) {
                        // if the endpoint is used for export from other nodes too, then update it
                        if (endpointDescription.getNodes().size() > 0) {
                            LOGGER.debug("SERVICE TRACKER: Updating remote endpoint {}", key);
                            remoteEndpoints.put(key, endpointDescription);
                        } else { // remove endpoint permanently
                            LOGGER.debug("SERVICE TRACKER: Removing remote endpoint {}", key);
                            remoteEndpoints.remove(key);
                        }
                    } 
                }
            } else {
                LOGGER.debug("SERVICE TRACKER: no remote endpoints found");
            }
        } catch (Exception e) {
            LOGGER.error("SERVICE TRACKER: failed to run service tracker task",e);
        } finally {
           Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
  
}
