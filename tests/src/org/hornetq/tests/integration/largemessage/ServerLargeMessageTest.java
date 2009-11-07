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

package org.hornetq.tests.integration.largemessage;

import org.hornetq.core.client.ClientConsumer;
import org.hornetq.core.client.ClientMessage;
import org.hornetq.core.client.ClientProducer;
import org.hornetq.core.client.ClientSession;
import org.hornetq.core.client.ClientSessionFactory;
import org.hornetq.core.client.impl.ClientSessionFactoryImpl;
import org.hornetq.core.persistence.impl.journal.FileLargeServerMessage;
import org.hornetq.core.persistence.impl.journal.JournalStorageManager;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.tests.util.ServiceTestBase;

/**
 * A ServerLargeMessageTest
 *
 * @author <mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 *
 *
 */
public class ServerLargeMessageTest extends ServiceTestBase
{

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------
   
   // The ClientConsumer should be able to also send ServerLargeMessages as that's done by the CoreBridge
   public void testSendServerMessage() throws Exception
   {
      HornetQServer server = createServer(true);
      
      server.start();
      
      ClientSessionFactory sf = createFactory(false);
      
      ClientSession session = sf.createSession(false, false);
      
      try
      {
         FileLargeServerMessage fileMessage = new FileLargeServerMessage((JournalStorageManager)server.getStorageManager());
         
         fileMessage.setMessageID(1005);
         
         for (int i = 0 ; i < 2 * ClientSessionFactoryImpl.DEFAULT_MIN_LARGE_MESSAGE_SIZE; i++)
         {
            fileMessage.addBytes(new byte[]{getSamplebyte(i)});
         }
         
         fileMessage.releaseResources();
         
         session.createQueue("A", "A");
         
         ClientProducer prod = session.createProducer("A");
         
         prod.send(fileMessage);
         
         fileMessage.deleteFile();
         
         session.commit();
                  
         session.start();
         
         ClientConsumer cons = session.createConsumer("A");
         
         ClientMessage msg = cons.receive(5000);
         
         assertNotNull(msg);
         
         assertEquals(msg.getBodySize(), 2 * ClientSessionFactoryImpl.DEFAULT_MIN_LARGE_MESSAGE_SIZE);
         
         for (int i = 0 ; i < 2 * ClientSessionFactoryImpl.DEFAULT_MIN_LARGE_MESSAGE_SIZE; i++)
         {
            assertEquals(getSamplebyte(i), msg.getBody().readByte());
         }
         
         msg.acknowledge();
         
         session.commit();
         
      }
      finally
      {
         sf.close();
         server.stop();
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
