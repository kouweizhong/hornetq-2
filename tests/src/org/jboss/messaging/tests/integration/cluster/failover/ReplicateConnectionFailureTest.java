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

package org.jboss.messaging.tests.integration.cluster.failover;

import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_ACK_BATCH_SIZE;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_AUTO_GROUP;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_BLOCK_ON_ACKNOWLEDGE;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_BLOCK_ON_NON_PERSISTENT_SEND;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_BLOCK_ON_PERSISTENT_SEND;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_CALL_TIMEOUT;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_CONSUMER_MAX_RATE;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_CONSUMER_WINDOW_SIZE;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_MAX_CONNECTIONS;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_MAX_RETRIES_AFTER_FAILOVER;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_MAX_RETRIES_BEFORE_FAILOVER;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_MIN_LARGE_MESSAGE_SIZE;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_PRE_ACKNOWLEDGE;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_PRODUCER_MAX_RATE;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_RETRY_INTERVAL;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_RETRY_INTERVAL_MULTIPLIER;
import static org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl.DEFAULT_SEND_WINDOW_SIZE;

import java.util.HashMap;
import java.util.Map;

import org.jboss.messaging.core.client.ClientSession;
import org.jboss.messaging.core.client.impl.ClientSessionFactoryImpl;
import org.jboss.messaging.core.client.impl.ClientSessionFactoryInternal;
import org.jboss.messaging.core.client.impl.ClientSessionImpl;
import org.jboss.messaging.core.config.Configuration;
import org.jboss.messaging.core.config.TransportConfiguration;
import org.jboss.messaging.core.config.impl.ConfigurationImpl;
import org.jboss.messaging.core.logging.Logger;
import org.jboss.messaging.core.remoting.impl.RemotingConnectionImpl;
import org.jboss.messaging.core.remoting.impl.invm.InVMRegistry;
import org.jboss.messaging.core.remoting.impl.invm.TransportConstants;
import org.jboss.messaging.core.server.Messaging;
import org.jboss.messaging.core.server.MessagingService;
import org.jboss.messaging.tests.util.UnitTestCase;

/**
 * 
 * A ReplicateConnectionFailureTest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * 
 * Created 6 Nov 2008 08:42:36
 *
 * Test whether when a connection is failed on the server since server receives no ping, that close
 * is replicated to backup.
 *
 */
public class ReplicateConnectionFailureTest extends UnitTestCase
{
   private static final Logger log = Logger.getLogger(ReplicateConnectionFailureTest.class);

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private MessagingService liveService;

   private MessagingService backupService;

   private final Map<String, Object> backupParams = new HashMap<String, Object>();

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testFailConnection() throws Exception
   {
      final long pingPeriod = 500;

      ClientSessionFactoryInternal sf1 = new ClientSessionFactoryImpl(new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory"),
                                                                      null,
                                                                      DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME,
                                                                      pingPeriod,
                                                                      (long)(pingPeriod * 1.5),
                                                                      DEFAULT_CALL_TIMEOUT,
                                                                      DEFAULT_CONSUMER_WINDOW_SIZE,
                                                                      DEFAULT_CONSUMER_MAX_RATE,
                                                                      DEFAULT_SEND_WINDOW_SIZE,
                                                                      DEFAULT_PRODUCER_MAX_RATE,
                                                                      DEFAULT_MIN_LARGE_MESSAGE_SIZE,
                                                                      DEFAULT_BLOCK_ON_ACKNOWLEDGE,
                                                                      DEFAULT_BLOCK_ON_PERSISTENT_SEND,
                                                                      DEFAULT_BLOCK_ON_NON_PERSISTENT_SEND,
                                                                      DEFAULT_AUTO_GROUP,
                                                                      DEFAULT_MAX_CONNECTIONS,
                                                                      DEFAULT_PRE_ACKNOWLEDGE,
                                                                      DEFAULT_ACK_BATCH_SIZE,
                                                                      DEFAULT_RETRY_INTERVAL,
                                                                      DEFAULT_RETRY_INTERVAL_MULTIPLIER,
                                                                      DEFAULT_MAX_RETRIES_BEFORE_FAILOVER,
                                                                      DEFAULT_MAX_RETRIES_AFTER_FAILOVER);

      sf1.setSendWindowSize(32 * 1024);

      assertEquals(0, liveService.getServer().getRemotingService().getConnections().size());

      assertEquals(0, backupService.getServer().getRemotingService().getConnections().size());

      ClientSession session1 = sf1.createSession(false, true, true);

      // One connection
      assertEquals(1, liveService.getServer().getRemotingService().getConnections().size());

      // One replicating connection per session
      assertEquals(1, backupService.getServer().getRemotingService().getConnections().size());

      session1.close();

      log.info("recreating");

      session1 = sf1.createSession(false, true, true);

      final RemotingConnectionImpl conn1 = (RemotingConnectionImpl)((ClientSessionImpl)session1).getConnection();

      conn1.stopPingingAfterOne();

      log.info("waiting");
      
      Thread.sleep(3 * pingPeriod);

      assertEquals(0, liveService.getServer().getRemotingService().getConnections().size());

      assertEquals(1, backupService.getServer().getRemotingService().getConnections().size());

      session1.close();

      assertEquals(0, liveService.getServer().getRemotingService().getConnections().size());

      assertEquals(1, backupService.getServer().getRemotingService().getConnections().size());
   }
   
   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();
      
      Configuration backupConf = new ConfigurationImpl();
      backupConf.setConnectionScanPeriod(100);
      backupConf.setSecurityEnabled(false);
      backupParams.put(TransportConstants.SERVER_ID_PROP_NAME, 1);
      backupConf.getAcceptorConfigurations()
                .add(new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMAcceptorFactory",
                                                backupParams));
      backupConf.setBackup(true);
      backupService = Messaging.newNullStorageMessagingService(backupConf);
      backupService.start();

      Configuration liveConf = new ConfigurationImpl();
      liveConf.setConnectionScanPeriod(100);
      liveConf.setSecurityEnabled(false);
      liveConf.getAcceptorConfigurations()
              .add(new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMAcceptorFactory"));
      Map<String, TransportConfiguration> connectors = new HashMap<String, TransportConfiguration>();
      TransportConfiguration backupTC = new TransportConfiguration("org.jboss.messaging.core.remoting.impl.invm.InVMConnectorFactory",
                                                                   backupParams,
                                                                   "backup-connector");
      connectors.put(backupTC.getName(), backupTC);
      liveConf.setConnectorConfigurations(connectors);
      liveConf.setBackupConnectorName(backupTC.getName());
      liveService = Messaging.newNullStorageMessagingService(liveConf);
      liveService.start();
   }

   @Override
   protected void tearDown() throws Exception
   {
      backupService.stop();

      liveService.stop();

      assertEquals(0, InVMRegistry.instance.size());
      
      super.tearDown();
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
