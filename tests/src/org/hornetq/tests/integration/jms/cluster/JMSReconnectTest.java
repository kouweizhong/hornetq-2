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

package org.hornetq.tests.integration.jms.cluster;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import junit.framework.Assert;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.JMSFactoryType;
import org.hornetq.core.client.impl.ClientSessionInternal;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.remoting.impl.invm.InVMRegistry;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServers;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.hornetq.jms.client.HornetQDestination;
import org.hornetq.jms.client.HornetQSession;
import org.hornetq.spi.core.protocol.RemotingConnection;
import org.hornetq.tests.util.RandomUtil;
import org.hornetq.tests.util.UnitTestCase;

/**
 * 
 * A JMSReconnectTest
 *
 * @author Tim Fox
 *
 *
 */
public class JMSReconnectTest extends UnitTestCase
{
   private static final Logger log = Logger.getLogger(JMSReconnectTest.class);

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private HornetQServer liveService;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   //In this test we re-attach to the same node without restarting the server
   public void testReattachSameNode() throws Exception
   {
      testReconnectOrReattachSameNode(true);
   }
   
   //In this test, we reconnect to the same node without restarting the server
   public void testReconnectSameNode() throws Exception
   {
      testReconnectOrReattachSameNode(false);
   }
      
   private void testReconnectOrReattachSameNode(boolean reattach) throws Exception
   {
      HornetQConnectionFactory jbcf = (HornetQConnectionFactory) HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      jbcf.setBlockOnDurableSend(true);
      jbcf.setBlockOnNonDurableSend(true);
      
      jbcf.setReconnectAttempts(-1);
      
      if (reattach)
      {
         jbcf.setConfirmationWindowSize(1024 * 1024);
      }
      
      // Note we set consumer window size to a value so we can verify that consumer credit re-sending
      // works properly on failover
      // The value is small enough that credits will have to be resent several time

      final int numMessages = 10;

      final int bodySize = 1000;

      jbcf.setConsumerWindowSize(numMessages * bodySize / 10);

      Connection conn = jbcf.createConnection();

      MyExceptionListener listener = new MyExceptionListener();

      conn.setExceptionListener(listener);

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      ClientSession coreSession = ((HornetQSession)sess).getCoreSession();

      RemotingConnection coreConn = ((ClientSessionInternal)coreSession).getConnection();

      SimpleString jmsQueueName = new SimpleString(HornetQDestination.JMS_QUEUE_ADDRESS_PREFIX + "myqueue");

      coreSession.createQueue(jmsQueueName, jmsQueueName, null, true);

      Queue queue = sess.createQueue("myqueue");

      MessageProducer producer = sess.createProducer(queue);

      producer.setDeliveryMode(DeliveryMode.PERSISTENT);

      MessageConsumer consumer = sess.createConsumer(queue);

      byte[] body = RandomUtil.randomBytes(bodySize);

      for (int i = 0; i < numMessages; i++)
      {
         BytesMessage bm = sess.createBytesMessage();

         bm.writeBytes(body);

         producer.send(bm);
      }

      conn.start();

      Thread.sleep(2000);

      HornetQException me = new HornetQException(HornetQException.NOT_CONNECTED);

      coreConn.fail(me);
      
      //It should reconnect to the same node

      for (int i = 0; i < numMessages; i++)
      {
         BytesMessage bm = (BytesMessage)consumer.receive(1000);

         Assert.assertNotNull(bm);

         Assert.assertEquals(body.length, bm.getBodyLength());
      }

      TextMessage tm = (TextMessage)consumer.receiveNoWait();

      Assert.assertNull(tm);

      conn.close();

      Assert.assertNotNull(listener.e);

      Assert.assertTrue(me == listener.e.getCause());
   }
   
   public void testReconnectSameNodeServerRestartedWithNonDurableSub() throws Exception
   {
      testReconnectSameNodeServerRestartedWithNonDurableSubOrTempQueue(true);
   }
   
   public void testReconnectSameNodeServerRestartedWithTempQueue() throws Exception
   {
      testReconnectSameNodeServerRestartedWithNonDurableSubOrTempQueue(false);
   }
   
   //Test that non durable JMS sub gets recreated in auto reconnect
   private void testReconnectSameNodeServerRestartedWithNonDurableSubOrTempQueue(final boolean nonDurableSub) throws Exception
   {
      HornetQConnectionFactory jbcf = (HornetQConnectionFactory) HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      jbcf.setReconnectAttempts(-1);
           
      Connection conn = jbcf.createConnection();

      MyExceptionListener listener = new MyExceptionListener();

      conn.setExceptionListener(listener);

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      ClientSession coreSession = ((HornetQSession)sess).getCoreSession();

      Destination dest;
      
      if (nonDurableSub)
      {            
         coreSession.createQueue(HornetQDestination.JMS_TOPIC_ADDRESS_PREFIX + "mytopic", "blahblah", null, false);
   
         dest = HornetQJMSClient.createTopic("mytopic");
      }
      else
      {
         dest = sess.createTemporaryQueue();
      }

      MessageProducer producer = sess.createProducer(dest);

      //Create a non durable subscriber
      MessageConsumer consumer = sess.createConsumer(dest);

      this.liveService.stop();
      
      this.liveService.start();
      
      //Allow client some time to reconnect
      Thread.sleep(3000);
      
      final int numMessages = 100;
      
      byte[] body = RandomUtil.randomBytes(1000);
      
      for (int i = 0; i < numMessages; i++)
      {
         BytesMessage bm = sess.createBytesMessage();

         bm.writeBytes(body);

         producer.send(bm);
      }

      conn.start();
      
      for (int i = 0; i < numMessages; i++)
      {
         BytesMessage bm = (BytesMessage)consumer.receive(1000);

         Assert.assertNotNull(bm);

         Assert.assertEquals(body.length, bm.getBodyLength());
      }

      TextMessage tm = (TextMessage)consumer.receiveNoWait();

      Assert.assertNull(tm);

      conn.close();

      Assert.assertNotNull(listener.e);
   }
   
   //If the server is shutdown after a non durable sub is created, then close on the connection should proceed normally
   public void testNoReconnectCloseAfterFailToReconnectWithTopicConsumer() throws Exception
   {
      HornetQConnectionFactory jbcf = (HornetQConnectionFactory) HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      jbcf.setReconnectAttempts(0);
      
      Connection conn = jbcf.createConnection();

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      ClientSession coreSession = ((HornetQSession)sess).getCoreSession();

      coreSession.createQueue(HornetQDestination.JMS_TOPIC_ADDRESS_PREFIX + "mytopic", "blahblah", null, false);

      Topic topic = HornetQJMSClient.createTopic("mytopic");
      
      //Create a non durable subscriber
      MessageConsumer consumer = sess.createConsumer(topic);      

      Thread.sleep(2000);
 
      this.liveService.stop();
      
      this.liveService.start();
      
      sess.close();
    
      conn.close();
   }
   
   //If server is shutdown, and then connection is closed, after a temp queue has been created, the close should complete normally
   public void testNoReconnectCloseAfterFailToReconnectWithTempQueue() throws Exception
   {
      HornetQConnectionFactory jbcf = (HornetQConnectionFactory) HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMConnectorFactory"));

      jbcf.setReconnectAttempts(0);
      
      Connection conn = jbcf.createConnection();

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      
      sess.createTemporaryQueue();
      
      Thread.sleep(2000);

      this.liveService.stop();
      
      this.liveService.start();
      
      sess.close();
    
      conn.close();
   }


   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();
     
      Configuration liveConf = createBasicConfig();
      liveConf.setSecurityEnabled(false);
      liveConf.setJournalType(getDefaultJournalType());
      liveConf.getAcceptorConfigurations()
              .add(new TransportConfiguration("org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory"));
      liveConf.setBindingsDirectory(getBindingsDir());
      liveConf.setJournalMinFiles(2);
      liveConf.setJournalDirectory(getJournalDir());
      liveConf.setPagingDirectory(getPageDir());
      liveConf.setLargeMessagesDirectory(getLargeMessagesDir());

      liveService = HornetQServers.newHornetQServer(liveConf, true);
      liveService.start();
   }

   @Override
   protected void tearDown() throws Exception
   {
      liveService.stop();

      Assert.assertEquals(0, InVMRegistry.instance.size());

      liveService = null;

      super.tearDown();
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

   private static class MyExceptionListener implements ExceptionListener
   {
      volatile JMSException e;

      public void onException(final JMSException e)
      {
         this.e = e;
      }
   }

}
