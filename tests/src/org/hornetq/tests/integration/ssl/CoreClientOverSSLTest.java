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

package org.hornetq.tests.integration.ssl;

import java.util.HashMap;
import java.util.Map;

import org.hornetq.core.client.ClientConsumer;
import org.hornetq.core.client.ClientSession;
import org.hornetq.core.client.ClientSessionFactory;
import org.hornetq.core.client.impl.ClientSessionFactoryImpl;
import org.hornetq.core.config.TransportConfiguration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.message.Message;
import org.hornetq.core.server.HornetQ;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.integration.transports.netty.NettyAcceptorFactory;
import org.hornetq.integration.transports.netty.NettyConnectorFactory;
import org.hornetq.integration.transports.netty.TransportConstants;
import org.hornetq.tests.util.SpawnedVMSupport;
import org.hornetq.tests.util.UnitTestCase;
import org.hornetq.utils.SimpleString;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * 
 * @version <tt>$Revision: 3716 $</tt>
 * 
 */
public class CoreClientOverSSLTest extends UnitTestCase
{
   // Constants -----------------------------------------------------

   public static final String MESSAGE_TEXT_FROM_CLIENT = "CoreClientOverSSLTest from client";

   public static final SimpleString QUEUE = new SimpleString("QueueOverSSL");

   public static final int SSL_PORT = 5402;

   // Static --------------------------------------------------------

   private static final Logger log = Logger.getLogger(CoreClientOverSSLTest.class);

   // Attributes ----------------------------------------------------

   private HornetQServer server;

   private ClientSession session;

   private ClientConsumer consumer;

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testSSL() throws Exception
   {
      final Process p = SpawnedVMSupport.spawnVM(CoreClientOverSSL.class.getName(), Boolean.TRUE.toString(), TransportConstants.DEFAULT_KEYSTORE_PATH, TransportConstants.DEFAULT_KEYSTORE_PASSWORD);

      Message m = consumer.receive(10000);
      assertNotNull(m);
      assertEquals(MESSAGE_TEXT_FROM_CLIENT, m.getBodyBuffer().readString());

      log.debug("waiting for the client VM to exit ...");
      SpawnedVMSupport.assertProcessExits(true, 0, p);
   }

   public void testSSLWithIncorrectKeyStorePassword() throws Exception
   {
      Process p = SpawnedVMSupport.spawnVM(CoreClientOverSSL.class.getName(), Boolean.TRUE.toString(), TransportConstants.DEFAULT_KEYSTORE_PATH, "invalid pasword");

      Message m = consumer.receive(5000);
      assertNull(m);

      log.debug("waiting for the client VM to exit ...");
      SpawnedVMSupport.assertProcessExits(false, 0, p);
   }

   // see https://jira.jboss.org/jira/browse/HORNETQ-234
   public void _testPlainConnectionToSSLEndpoint() throws Exception
   {
      Process p = SpawnedVMSupport.spawnVM(CoreClientOverSSL.class.getName(), Boolean.FALSE.toString(), TransportConstants.DEFAULT_KEYSTORE_PATH, TransportConstants.DEFAULT_KEYSTORE_PASSWORD);

      Message m = consumer.receive(5000);
      assertNull(m);

      log.debug("waiting for the client VM to exit ...");
      SpawnedVMSupport.assertProcessExits(false, 0, p);
   }

   // Package protected ---------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
      ConfigurationImpl config = new ConfigurationImpl();
      config.setSecurityEnabled(false);
      Map<String, Object> params = new HashMap<String, Object>();
      params.put(TransportConstants.SSL_ENABLED_PROP_NAME, true);
      config.getAcceptorConfigurations().add(new TransportConfiguration(NettyAcceptorFactory.class.getName(), params));
      server = HornetQ.newHornetQServer(config, false);
      server.start();
      TransportConfiguration tc = new TransportConfiguration(NettyConnectorFactory.class.getName(), params);
      ClientSessionFactory sf = new ClientSessionFactoryImpl(tc);
      session = sf.createSession(false, true, true);
      session.createQueue(QUEUE, QUEUE, false);
      consumer = session.createConsumer(QUEUE);
      session.start();
   }

   @Override
   protected void tearDown() throws Exception
   {
      consumer.close();
      session.close();

      server.stop();

      super.tearDown();
   }

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
