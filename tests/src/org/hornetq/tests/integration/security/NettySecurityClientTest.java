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

package org.hornetq.tests.integration.security;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

import junit.framework.Assert;

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.remoting.impl.netty.NettyAcceptorFactory;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServers;
import org.hornetq.tests.util.SpawnedVMSupport;
import org.hornetq.tests.util.UnitTestCase;

/**
 * A NettySecurityClientTest
 *
 * @author <a href="jmesnil@redhat.com">Jeff Mesnil</a>
 */
public class NettySecurityClientTest extends UnitTestCase
{

   // Constants -----------------------------------------------------

   private static final Logger log = Logger.getLogger(NettySecurityClientTest.class);

   // Attributes ----------------------------------------------------

   private HornetQServer messagingService;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testProducerConsumerClientWithoutSecurityManager() throws Exception
   {
      doTestProducerConsumerClient(false);
   }

   public void testProducerConsumerClientWithSecurityManager() throws Exception
   {
      doTestProducerConsumerClient(true);
   }

   // SecurityManagerClientTestBase overrides -----------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();

      ConfigurationImpl config = createBasicConfig();
      config.setSecurityEnabled(false);
      config.getAcceptorConfigurations().add(new TransportConfiguration(NettyAcceptorFactory.class.getName()));
      messagingService = HornetQServers.newHornetQServer(config, false);
      messagingService.start();
   }

   @Override
   protected void tearDown() throws Exception
   {
      messagingService.stop();

      messagingService = null;

      super.tearDown();
   }

   // Private -------------------------------------------------------

   private void doTestProducerConsumerClient(final boolean withSecurityManager) throws Exception
   {
      String[] vmargs = new String[0];
      if (withSecurityManager)
      {
         URL securityPolicyURL = Thread.currentThread()
                                       .getContextClassLoader()
                                       .getResource("restricted-security-client.policy");
         vmargs = new String[] { "-Djava.security.manager", "-Djava.security.policy=" + securityPolicyURL.getPath() };
      }

      // spawn a JVM that creates a client withor without a security manager which sends and receives a test message
      Process p = SpawnedVMSupport.spawnVM(SimpleClient.class.getName(),
                                           vmargs,
                                           false,
                                           new String[] { NettyConnectorFactory.class.getName() });

      InputStreamReader isr = new InputStreamReader(p.getInputStream());
      BufferedReader br = new BufferedReader(isr);
      String line = null;
      while ((line = br.readLine()) != null)
      {
         line = line.replace('|', '\n');
         if (line.startsWith("Listening"))
         {
            continue;
         }
         else if ("OK".equals(line.trim()))
         {
            break;
         }
         else
         {
            //Assert.fail("Exception when starting the client: " + line);
            System.out.println(line);
         }
      }

      SpawnedVMSupport.startLogger(SimpleClient.class.getName(), p);

      // the client VM should exit by itself. If it doesn't, that means we have a problem
      // and the test will timeout
      NettySecurityClientTest.log.debug("waiting for the client VM to exit ...");
      p.waitFor();

      Assert.assertEquals("client VM did not exit cleanly", 0, p.exitValue());
   }

   // Inner classes -------------------------------------------------

}
