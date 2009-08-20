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

package org.hornetq.tests.integration.cluster.failover;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.hornetq.core.client.ClientMessage;
import org.hornetq.core.client.impl.ClientSessionFactoryInternal;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.TransportConfiguration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.remoting.impl.invm.TransportConstants;
import org.hornetq.core.server.JournalType;
import org.hornetq.core.server.HornetQ;

/**
 * A LargeMessageMultiThreadFailoverTest
 *
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * 
 * Created Jan 18, 2009 4:52:09 PM
 *
 *
 */
public class XALargeMessageMultiThreadFailoverTest extends XAMultiThreadRandomFailoverTest
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------
   private static final byte[] BODY = new byte[500];

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------
   @Override
   protected ClientSessionFactoryInternal createSessionFactory()
   {
      ClientSessionFactoryInternal sf = super.createSessionFactory();
      sf.setMinLargeMessageSize(200);
      return sf;

   }
   
   @Override
   protected void start() throws Exception
   {

      deleteDirectory(new File(getTestDir()));

      Configuration backupConf = new ConfigurationImpl();

      backupConf.setJournalDirectory(getJournalDir(getTestDir() + "/backup"));
      backupConf.setLargeMessagesDirectory(getLargeMessagesDir(getTestDir() + "/backup"));
      backupConf.setBindingsDirectory(getBindingsDir(getTestDir() + "/backup"));
      backupConf.setPagingDirectory(getPageDir(getTestDir() + "/backup"));
      backupConf.setJournalFileSize(100 * 1024);

      backupConf.setJournalType(JournalType.ASYNCIO);

      backupConf.setSecurityEnabled(false);
      backupParams.put(TransportConstants.SERVER_ID_PROP_NAME, 1);

      backupConf.getAcceptorConfigurations()
                .add(new TransportConfiguration(InVMAcceptorFactory.class.getCanonicalName(), backupParams));
      backupConf.setBackup(true);

      backupService = HornetQ.newMessagingServer(backupConf);
      backupService.start();

      Configuration liveConf = new ConfigurationImpl();

      liveConf.setJournalDirectory(getJournalDir(getTestDir() + "/live"));
      liveConf.setLargeMessagesDirectory(getLargeMessagesDir(getTestDir() + "/live"));
      liveConf.setBindingsDirectory(getBindingsDir(getTestDir() + "/live"));
      liveConf.setPagingDirectory(getPageDir(getTestDir() + "/live"));

      liveConf.setJournalFileSize(100 * 1024);

      liveConf.setJournalType(JournalType.ASYNCIO);

      liveConf.setSecurityEnabled(false);
      liveConf.getAcceptorConfigurations()
              .add(new TransportConfiguration(InVMAcceptorFactory.class.getCanonicalName()));

      Map<String, TransportConfiguration> connectors = new HashMap<String, TransportConfiguration>();

      TransportConfiguration backupTC = new TransportConfiguration(INVM_CONNECTOR_FACTORY,
                                                                   backupParams,
                                                                   "backup-connector");
      connectors.put(backupTC.getName(), backupTC);
      liveConf.setConnectorConfigurations(connectors);
      liveConf.setBackupConnectorName(backupTC.getName());
      liveService = HornetQ.newMessagingServer(liveConf);

      liveService.start();

   }

   @Override
   protected void setBody(final ClientMessage message) throws Exception
   {

      message.getBody().writeBytes(BODY);

   }

   /* (non-Javadoc)
    * @see org.hornetq.tests.integration.cluster.failover.MultiThreadRandomFailoverTestBase#checkSize(org.hornetq.core.client.ClientMessage)
    */
   @Override
   protected boolean checkSize(final ClientMessage message)
   {
      return BODY.length == message.getBodySize();
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
