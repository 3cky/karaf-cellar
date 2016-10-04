/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.karaf.cellar.hazelcast.factory;

import com.hazelcast.config.Config;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.config.XmlConfigBuilder;
import org.apache.karaf.cellar.core.discovery.Discovery;
import org.apache.karaf.cellar.core.utils.CellarUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.karaf.cellar.core.discovery.DiscoveryService;

/**
 * Hazelcast configuration manager.
 * It loads hazelcast.xml configuration file.
 */
public class HazelcastConfigurationManager {

    private static final transient Logger LOGGER = LoggerFactory.getLogger(HazelcastServiceFactory.class);

    private String xmlConfigLocation = System.getProperty("karaf.etc") + File.separator + "hazelcast.xml";

    private Set<String> discoveredMembers = new LinkedHashSet<String>();
    private List<DiscoveryService> discoveryServices;

    private Config config = null;
    private List<String> configMembers;

    /**
     * Build a Hazelcast {@link com.hazelcast.config.Config}.
     *
     * @return the Hazelcast configuration.
     */
    public synchronized Config getHazelcastConfig() {
        if (config == null) {
            System.setProperty("hazelcast.config", xmlConfigLocation);
            config = new XmlConfigBuilder().build();
            if (System.getProperty("hazelcast.instanceName") != null) {
                config.setInstanceName(System.getProperty("hazelcast.instanceName"));
            } else {
                config.setInstanceName("cellar");
            }
            TcpIpConfig tcpIpConfig = config.getNetworkConfig().getJoin().getTcpIpConfig();
            if (tcpIpConfig.isEnabled()) {
                configMembers = Collections.unmodifiableList(tcpIpConfig.getMembers());
                if (discoveryServices != null && !discoveryServices.isEmpty()) {
                    for (DiscoveryService service : discoveryServices) {
                        service.refresh();
                        Set<String> discovered = service.discoverMembers();
                        discoveredMembers.addAll(discovered);
                        LOGGER.trace("HAZELCAST STARTUP DISCOVERY: service {} found members {}",
                                service, discovered);
                    }
                }
                tcpIpConfig.getMembers().addAll(discoveredMembers);
            }
        }
        return config;
    }

    /**
     * Update configuration of a Hazelcast instance.
     *
     * @param properties the updated configuration properties.
     * @return <code>true</code> if configuration was changed, <code>false</code> otherwise
     */
    public boolean isUpdated(Map properties) {
        boolean updated = false;
        if (properties != null && properties.containsKey(Discovery.DISCOVERED_MEMBERS_PROPERTY_NAME)) {
            Set<String> newDiscoveredMemberSet = CellarUtils.createSetFromString(
                    (String) properties.get(Discovery.DISCOVERED_MEMBERS_PROPERTY_NAME));
            synchronized (this) {
                if (!CellarUtils.collectionEquals(discoveredMembers, newDiscoveredMemberSet)) {
                    LOGGER.debug("Hazelcast discoveredMemberSet has been changed from {} to {}",
                            discoveredMembers, newDiscoveredMemberSet);
                    discoveredMembers = newDiscoveredMemberSet;
                    updateHazelcastTcpIpMembers();
                    updated = true;
                }
            }
        }
        return updated;
    }

    /**
     * Update Hazelcast TCP/IP members list with discovered members, if applicable.
     */
    private void updateHazelcastTcpIpMembers() {
        if (config != null) {
            TcpIpConfig tcpIpConfig = config.getNetworkConfig().getJoin().getTcpIpConfig();
            if (tcpIpConfig.isEnabled()) {
                List<String> members = tcpIpConfig.getMembers();
                members.clear();
                members.addAll(configMembers);
                members.addAll(discoveredMembers);
            }
        }
    }

    public void setDiscoveryServices(List<DiscoveryService> discoveryServices) {
        this.discoveryServices = discoveryServices;
    }

}
