/*
 * JBoss, Home of Professional Open Source Copyright 2005-2008, Red Hat
 * Middleware LLC, and individual contributors by the @authors tag. See the
 * copyright.txt in the distribution for a full listing of individual
 * contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */

package org.jboss.messaging.tests.integration.cluster.distribution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.messaging.core.client.ClientConsumer;
import org.jboss.messaging.core.client.ClientMessage;
import org.jboss.messaging.core.client.ClientProducer;
import org.jboss.messaging.core.client.ClientSession;
import org.jboss.messaging.core.client.ClientSessionFactory;
import org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl;
import org.jboss.messaging.core.config.TransportConfiguration;
import org.jboss.messaging.core.config.cluster.MessageFlowConfiguration;
import org.jboss.messaging.core.logging.Logger;
import org.jboss.messaging.core.remoting.impl.invm.InVMRegistry;
import org.jboss.messaging.core.server.MessagingService;
import org.jboss.messaging.util.SimpleString;

/**
 * 
 * A MessageFlowWildcardTest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * 
 * Created 15 Nov 2008 08:19:07
 *
 *
 */
public class MessageFlowTransformerTest extends MessageFlowTestBase
{
   private static final Logger log = Logger.getLogger(MessageFlowTransformerTest.class);

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testWithTransformer() throws Exception
   {
      Map<String, Object> service0Params = new HashMap<String, Object>();
      MessagingService service0 = createMessagingService(0, service0Params);

      Map<String, Object> service1Params = new HashMap<String, Object>();
      MessagingService service1 = createMessagingService(1, service1Params);
      service1.start();

      Map<String, TransportConfiguration> connectors = new HashMap<String, TransportConfiguration>();
      TransportConfiguration server1tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service1Params,
                                                                    "connector1");
      connectors.put(server1tc.getName(), server1tc);
      service0.getServer().getConfiguration().setConnectorConfigurations(connectors);

      final SimpleString address1 = new SimpleString("address1");

      List<String> connectorNames = new ArrayList<String>();
      connectorNames.add(server1tc.getName());

      MessageFlowConfiguration ofconfig = new MessageFlowConfiguration("outflow1",
                                                                       "address1",
                                                                       null,
                                                                       true,
                                                                       1,
                                                                       -1,
                                                                       "org.jboss.messaging.tests.integration.cluster.distribution.SimpleTransformer",
                                                                       connectorNames);
      Set<MessageFlowConfiguration> ofconfigs = new HashSet<MessageFlowConfiguration>();
      ofconfigs.add(ofconfig);
      service0.getServer().getConfiguration().setMessageFlowConfigurations(ofconfigs);

      service0.start();

      TransportConfiguration server0tc = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                    service0Params);

      ClientSessionFactory csf0 = new ClientSessionFactoryImpl(server0tc);

      ClientSession session0 = csf0.createSession(false, true, true);

      ClientSessionFactory csf1 = new ClientSessionFactoryImpl(server1tc);

      ClientSession session1 = csf1.createSession(false, true, true);

      session0.createQueue(address1, address1, null, false, false, true);

      session1.createQueue(address1, address1, null, false, false, true);

      ClientProducer prod0 = session0.createProducer(address1);

      ClientConsumer cons1 = session1.createConsumer(address1);

      session1.start();

      final int numMessages = 100;

      final SimpleString propKey = new SimpleString("wibble");

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session0.createClientMessage(false);
         message.putStringProperty(propKey, new SimpleString("bing"));
         message.getBody().putString("doo be doo be doo be doo");
         message.getBody().flip();

         prod0.send(message);
      }

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage rmessage = cons1.receive(1000);

         assertNotNull(rmessage);

         SimpleString val = (SimpleString)rmessage.getProperty(propKey);
         assertEquals(new SimpleString("bong"), val);

         String sval = rmessage.getBody().getString();
         assertEquals("dee be dee be dee be dee", sval);
      }

      service0.stop();
      service1.stop();

      assertEquals(0, service0.getServer().getRemotingService().getConnections().size());
      assertEquals(0, service1.getServer().getRemotingService().getConnections().size());
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
   }

   @Override
   protected void tearDown() throws Exception
   {
      assertEquals(0, InVMRegistry.instance.size());
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
