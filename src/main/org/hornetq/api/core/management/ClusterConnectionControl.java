/*
 * Copyright 2009 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.hornetq.api.core.management;

import java.util.Map;

/**
 * A ClusterConnectionControl is used to manage a cluster connection.
 *
 * @author <a href="jmesnil@redhat.com">Jeff Mesnil</a>
 *
 */
public interface ClusterConnectionControl extends HornetQComponentControl
{
   /**
    * Returns the configuration name of this cluster connection.
    */
   String getName();

   /**
    * Returns the address used by this cluster connection.
    */
   String getAddress();

   /**
    * Returns the node ID used by this cluster connection.
    */
   String getNodeID();

   /**
    * Return whether this cluster connection use duplicate detection.
    */
   boolean isDuplicateDetection();

   /**
    * Return whether this cluster connection forward messages when it has no local consumers.
    */
   boolean isForwardWhenNoConsumers();

   /**
    * Returns the maximum number of hops used by this cluster connection.
    */
   int getMaxHops();

   /**
    * Returns the list of static connectors
    */
   Object[] getStaticConnectors();

   /**
    * Returns the list of static connectors as JSON
    */
   String getStaticConnectorsAsJSON() throws Exception;

   /**
    * Returns the name of the discovery group used by this cluster connection.
    */
   String getDiscoveryGroupName();

   /**
    * Returns the connection retry interval used by this cluster connection.
    */
   long getRetryInterval();

   /**
    * Returns a map of the nodes connected to this cluster connection.
    * <br>
    * keys are node IDs, values are the addresses used to connect to the nodes.
    */
   Map<String, String> getNodes() throws Exception;
}
