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

package org.hornetq.tests.integration.management;

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.api.core.management.BroadcastGroupControl;
import org.hornetq.api.core.management.ResourceNames;
import org.hornetq.core.client.impl.ClientSessionFactoryImpl;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;

/**
 * A BroadcastGroupControlUsingCoreTest
 *
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 *
 *
 */
public class BroadcastGroupControlUsingCoreTest extends BroadcastGroupControlTest
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private ClientSession session;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // BroadcastGroupControlTest overrides --------------------------------

   @Override
   protected BroadcastGroupControl createManagementControl(final String name) throws Exception
   {
      ServerLocator locator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(InVMConnectorFactory.class.getName()));
      ClientSessionFactory sf = locator.createSessionFactory();
      session = sf.createSession(false, true, true);
      session.start();

      return new BroadcastGroupControl()
      {
         private final CoreMessagingProxy proxy = new CoreMessagingProxy(session,
                                                                         ResourceNames.CORE_BROADCAST_GROUP + name);

         public long getBroadcastPeriod()
         {
            return ((Integer)proxy.retrieveAttributeValue("broadcastPeriod")).longValue();
         }

         public Object[] getConnectorPairs()
         {
            return (Object[])proxy.retrieveAttributeValue("connectorPairs");
         }

         public String getConnectorPairsAsJSON()
         {
            return (String)proxy.retrieveAttributeValue("connectorPairsAsJSON");
         }

         public String getGroupAddress()
         {
            return (String)proxy.retrieveAttributeValue("groupAddress");
         }

         public int getGroupPort()
         {
            return (Integer)proxy.retrieveAttributeValue("groupPort");
         }

         public int getLocalBindPort()
         {
            return (Integer)proxy.retrieveAttributeValue("localBindPort");
         }

         public String getName()
         {
            return (String)proxy.retrieveAttributeValue("name");
         }

         public boolean isStarted()
         {
            return (Boolean)proxy.retrieveAttributeValue("started");
         }

         public void start() throws Exception
         {
            proxy.invokeOperation("start");
         }

         public void stop() throws Exception
         {
            proxy.invokeOperation("stop");
         }
      };
   }

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected void tearDown() throws Exception
   {
      if (session != null)
      {
         session.close();
      }

      session = null;

      super.tearDown();
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
