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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import junit.framework.Assert;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.Interceptor;
import org.hornetq.api.core.Message;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.MessageHandler;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.api.core.client.SessionFailureListener;
import org.hornetq.core.client.impl.ClientSessionFactoryInternal;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.transaction.impl.XidImpl;
import org.hornetq.jms.client.HornetQTextMessage;
import org.hornetq.tests.util.RandomUtil;

/**
 * 
 * A FailoverTest
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 */
public class FailoverTest extends FailoverTestBase
{
   private static final Logger log = Logger.getLogger(FailoverTest.class);
   private ServerLocator locator;

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   /**
    * @param name
    */
   public FailoverTest(final String name)
   {
      super(name);
   }

   public FailoverTest()
   {
   }

   abstract class BaseListener implements SessionFailureListener
   {
      public void beforeReconnect(final HornetQException me)
      {
      }
   }

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();
      locator = getServerLocator();
   }

   @Override
   protected void tearDown() throws Exception
   {
      locator.close();
      super.tearDown();
   }

   //https://jira.jboss.org/browse/HORNETQ-522
   public void testNonTransactedWithZeroConsumerWindowSize() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setAckBatchSize(0);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = (ClientSessionFactoryInternal)locator.createSessionFactory();

      ClientSession session = sf.createSession(true, true);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);


      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(true);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      int winSize = 0;
      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS, null, winSize, 100, false);
      
      final List<ClientMessage> received = new ArrayList<ClientMessage>();
      
      consumer.setMessageHandler(new MessageHandler() {

         public void onMessage(ClientMessage message)
         {
            received.add(message);
            try
            {
               Thread.sleep(20);
            }
            catch (InterruptedException e)
            {
               // TODO Auto-generated catch block
               e.printStackTrace();
            }
         }
         
      });

      session.start();
      
      crash(session);
      
      int retry = 0;
      while (received.size() >= numMessages)
      {
         Thread.sleep(1000);
         retry++;
         if (retry > 5)
         {
            break;
         }
      }
      System.out.println("received.size() = " + received.size());
      session.close();

      sf.close();
      
      Assert.assertTrue(retry <= 5);

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testNonTransacted() throws Exception
   {
      ClientSessionFactoryInternal sf;

      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(true, true);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      crash(session);

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      for (int i = 0; i < numMessages; i++)
      {
         // Only the persistent messages will survive

         if (i % 2 == 0)
         {
            ClientMessage message = consumer.receive(1000);

            Assert.assertNotNull(message);

            assertMessageBody(i, message);

            Assert.assertEquals(i, message.getIntProperty("counter").intValue());

            message.acknowledge();
         }
      }

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testConsumeTransacted() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(false, false);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(true);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session.commit();

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);

         message.acknowledge();

         // TODO: The test won't pass if you uncomment this line
         // assertEquals(i, (int)message.getIntProperty("counter"));

         if (i == 5)
         {
            crash(session);
         }
      }

      try
      {
         session.commit();
         fail("session must have rolled back on failover");
      }
      catch (HornetQException e)
      {
         assertTrue(e.getCode() == HornetQException.TRANSACTION_ROLLED_BACK);
      }

      consumer.close();

      consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);
         
         assertNotNull(message);

         message.acknowledge();
      }

      session.commit();

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

 

   // https://jira.jboss.org/jira/browse/HORNETQ-285
   public void testFailoverOnInitialConnection() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setFailoverOnInitialConnection(true);
      locator.setReconnectAttempts(-1);


      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      // Crash live server
      crash();

      ClientSession session = sf.createSession();

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(true);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());

         message.acknowledge();
      }

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testTransactedMessagesSentSoRollback() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(false, false);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      crash(session);

      Assert.assertTrue(session.isRollbackOnly());

      try
      {
         session.commit();

         Assert.fail("Should throw exception");
      }
      catch (HornetQException e)
      {
         Assert.assertEquals(HornetQException.TRANSACTION_ROLLED_BACK, e.getCode());
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientMessage message = consumer.receiveImmediate();

      Assert.assertNull(message);

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   /**
    * Test that once the transacted session has throw a TRANSACTION_ROLLED_BACK exception,
    * it can be reused again
    */
   public void testTransactedMessagesSentSoRollbackAndContinueWork() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(false, false);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      crash(session);

      Assert.assertTrue(session.isRollbackOnly());

      try
      {
         session.commit();

         Assert.fail("Should throw exception");
      }
      catch (HornetQException e)
      {
         Assert.assertEquals(HornetQException.TRANSACTION_ROLLED_BACK, e.getCode());
      }

      ClientMessage message = session.createMessage(false);
      int counter = RandomUtil.randomInt();
      message.putIntProperty("counter", counter);

      producer.send(message);

      // session is working again
      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      message = consumer.receiveImmediate();

      Assert.assertNotNull(message);
      Assert.assertEquals(counter, message.getIntProperty("counter").intValue());

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testTransactedMessagesNotSentSoNoRollback() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(false, false);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session.commit();

      crash(session);

      // committing again should work since didn't send anything since last commit

      Assert.assertFalse(session.isRollbackOnly());

      session.commit();

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      for (int i = 0; i < numMessages; i++)
      {
         // Only the persistent messages will survive

         if (i % 2 == 0)
         {
            ClientMessage message = consumer.receive(1000);

            Assert.assertNotNull(message);

            assertMessageBody(i, message);

            Assert.assertEquals(i, message.getIntProperty("counter").intValue());

            message.acknowledge();
         }
      }

      Assert.assertNull(consumer.receiveImmediate());

      session.commit();

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testTransactedMessagesWithConsumerStartedBeforeFailover() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(false, false);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      // create a consumer and start the session before failover
      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      // messages will be delivered to the consumer when the session is committed
      session.commit();

      Assert.assertFalse(session.isRollbackOnly());

      crash(session);

      session.commit();

      session.close();

      session = sf.createSession(false, false);

      consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      for (int i = 0; i < numMessages; i++)
      {
         // Only the persistent messages will survive

         if (i % 2 == 0)
         {
            ClientMessage message = consumer.receive(1000);

            Assert.assertNotNull(message);

            assertMessageBody(i, message);

            Assert.assertEquals(i, message.getIntProperty("counter").intValue());

            message.acknowledge();
         }
      }

      Assert.assertNull(consumer.receiveImmediate());

      session.commit();

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testTransactedMessagesConsumedSoRollback() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session1 = sf.createSession(false, false);

      session1.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session1.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session1.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session1.commit();

      ClientSession session2 = sf.createSession(false, false);

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());

         message.acknowledge();
      }

      crash(session2);

      Assert.assertTrue(session2.isRollbackOnly());

      try
      {
         session2.commit();

         Assert.fail("Should throw exception");
      }
      catch (HornetQException e)
      {
         Assert.assertEquals(HornetQException.TRANSACTION_ROLLED_BACK, e.getCode());
      }

      session1.close();

      session2.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testTransactedMessagesNotConsumedSoNoRollback() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session1 = sf.createSession(false, false);

      session1.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session1.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session1.createMessage(true);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session1.commit();

      ClientSession session2 = sf.createSession(false, false);

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      for (int i = 0; i < numMessages / 2; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());

         message.acknowledge();
      }

      session2.commit();

      consumer.close();

      crash(session2);

      Assert.assertFalse(session2.isRollbackOnly());

      consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      for (int i = numMessages / 2; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());

         message.acknowledge();
      }

      session2.commit();

      Assert.assertNull(consumer.receiveImmediate());

      session1.close();

      session2.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testXAMessagesSentSoRollbackOnEnd() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(true, false, false);

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      session.start(xid, XAResource.TMNOFLAGS);

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      crash(session);

      try
      {
         session.end(xid, XAResource.TMSUCCESS);

         Assert.fail("Should throw exception");
      }
      catch (XAException e)
      {
         Assert.assertEquals(XAException.XA_RBOTHER, e.errorCode);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientMessage message = consumer.receiveImmediate();

      Assert.assertNull(message);

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testXAMessagesSentSoRollbackOnPrepare() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(true, false, false);

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      session.start(xid, XAResource.TMNOFLAGS);

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session.end(xid, XAResource.TMSUCCESS);

      crash(session);

      try
      {
         session.prepare(xid);

         Assert.fail("Should throw exception");
      }
      catch (XAException e)
      {
         Assert.assertEquals(XAException.XA_RBOTHER, e.errorCode);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientMessage message = consumer.receiveImmediate();

      Assert.assertNull(message);

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   // This might happen if 1PC optimisation kicks in
   public void testXAMessagesSentSoRollbackOnCommit() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(true, false, false);

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      session.start(xid, XAResource.TMNOFLAGS);

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session.end(xid, XAResource.TMSUCCESS);

      session.prepare(xid);

      crash(session);

      try
      {
         session.commit(xid, true);

         Assert.fail("Should throw exception");
      }
      catch (XAException e)
      {
         Assert.assertEquals(XAException.XA_RBOTHER, e.errorCode);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientMessage message = consumer.receiveImmediate();

      Assert.assertNull(message);

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testXAMessagesNotSentSoNoRollbackOnCommit() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(true, false, false);

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      session.start(xid, XAResource.TMNOFLAGS);

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session.end(xid, XAResource.TMSUCCESS);

      session.prepare(xid);

      session.commit(xid, false);

      crash(session);

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      Xid xid2 = new XidImpl("tfytftyf".getBytes(), 54654, "iohiuohiuhgiu".getBytes());

      session.start(xid2, XAResource.TMNOFLAGS);

      for (int i = 0; i < numMessages; i++)
      {
         // Only the persistent messages will survive

         if (i % 2 == 0)
         {
            ClientMessage message = consumer.receive(1000);

            Assert.assertNotNull(message);

            assertMessageBody(i, message);

            Assert.assertEquals(i, message.getIntProperty("counter").intValue());

            message.acknowledge();
         }
      }

      session.end(xid2, XAResource.TMSUCCESS);

      session.prepare(xid2);

      session.commit(xid2, false);

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testXAMessagesConsumedSoRollbackOnEnd() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session1 = sf.createSession(false, false);

      session1.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session1.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session1.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session1.commit();

      ClientSession session2 = sf.createSession(true, false, false);

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session2.start(xid, XAResource.TMNOFLAGS);

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());

         message.acknowledge();
      }

      crash(session2);

      try
      {
         session2.end(xid, XAResource.TMSUCCESS);

         Assert.fail("Should throw exception");
      }
      catch (XAException e)
      {
         Assert.assertEquals(XAException.XA_RBOTHER, e.errorCode);
      }

      session1.close();

      session2.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testXAMessagesConsumedSoRollbackOnPrepare() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session1 = sf.createSession(false, false);

      session1.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session1.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session1.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session1.commit();

      ClientSession session2 = sf.createSession(true, false, false);

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session2.start(xid, XAResource.TMNOFLAGS);

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());

         message.acknowledge();
      }

      session2.end(xid, XAResource.TMSUCCESS);

      crash(session2);

      try
      {
         session2.prepare(xid);

         Assert.fail("Should throw exception");
      }
      catch (XAException e)
      {
         Assert.assertEquals(XAException.XA_RBOTHER, e.errorCode);
      }

      session1.close();

      session2.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   // 1PC optimisation
   public void testXAMessagesConsumedSoRollbackOnCommit() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);
      ClientSession session1 = sf.createSession(false, false);

      session1.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session1.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session1.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session1.commit();

      ClientSession session2 = sf.createSession(true, false, false);

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      Xid xid = new XidImpl("uhuhuhu".getBytes(), 126512, "auhsduashd".getBytes());

      session2.start(xid, XAResource.TMNOFLAGS);

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());

         message.acknowledge();
      }

      session2.end(xid, XAResource.TMSUCCESS);

      session2.prepare(xid);

      crash(session2);

      try
      {
         session2.commit(xid, true);

         Assert.fail("Should throw exception");
      }
      catch (XAException e)
      {
         Assert.assertEquals(XAException.XA_RBOTHER, e.errorCode);
      }

      session1.close();

      session2.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testCreateNewFactoryAfterFailover() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setFailoverOnInitialConnection(true);
      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sendAndConsume(sf, true);

      crash(session);

      session.close();

      Thread.sleep(5000);

      sf = (ClientSessionFactoryInternal) locator.createSessionFactory();

      session = sendAndConsume(sf, true);

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testFailoverMultipleSessionsWithConsumers() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      final int numSessions = 5;

      final int numConsumersPerSession = 5;

      Map<ClientSession, List<ClientConsumer>> sessionConsumerMap = new HashMap<ClientSession, List<ClientConsumer>>();

      for (int i = 0; i < numSessions; i++)
      {
         ClientSession session = sf.createSession(true, true);

         List<ClientConsumer> consumers = new ArrayList<ClientConsumer>();

         for (int j = 0; j < numConsumersPerSession; j++)
         {
            SimpleString queueName = new SimpleString("queue" + i + "-" + j);

            session.createQueue(FailoverTestBase.ADDRESS, queueName, null, true);

            ClientConsumer consumer = session.createConsumer(queueName);

            consumers.add(consumer);
         }

         sessionConsumerMap.put(session, consumers);
      }

      ClientSession sendSession = sf.createSession(true, true);

      ClientProducer producer = sendSession.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = sendSession.createMessage(true);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      Set<ClientSession> sessionSet = sessionConsumerMap.keySet();
      ClientSession[] sessions = new ClientSession[sessionSet.size()];
      sessionSet.toArray(sessions);
      crash(sessions);


      for (ClientSession session : sessionConsumerMap.keySet())
      {
         session.start();
      }

      for (List<ClientConsumer> consumerList : sessionConsumerMap.values())
      {
         for (ClientConsumer consumer : consumerList)
         {
            for (int i = 0; i < numMessages; i++)
            {
               ClientMessage message = consumer.receive(1000);

               Assert.assertNotNull(message);

               assertMessageBody(i, message);

               Assert.assertEquals(i, message.getIntProperty("counter").intValue());

               message.acknowledge();
            }
         }
      }

      for (ClientSession session : sessionConsumerMap.keySet())
      {
         session.close();
      }

      sendSession.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   /*
    * Browser will get reset to beginning after failover
    */
   public void testFailWithBrowser() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);
      ClientSession session = sf.createSession(true, true);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS, true);

      session.start();

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());
      }

      crash(session);

      for (int i = 0; i < numMessages; i++)
      {
         // Only the persistent messages will survive

         if (i % 2 == 0)
         {
            ClientMessage message = consumer.receive(1000);

            Assert.assertNotNull(message);

            assertMessageBody(i, message);

            Assert.assertEquals(i, message.getIntProperty("counter").intValue());

            message.acknowledge();
         }
      }

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testFailThenReceiveMoreMessagesAfterFailover() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(true, true);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());
      }

      crash(session);

      // Should get the same ones after failover since we didn't ack

      for (int i = 0; i < numMessages; i++)
      {
         // Only the persistent messages will survive

         if (i % 2 == 0)
         {
            ClientMessage message = consumer.receive(1000);

            Assert.assertNotNull(message);

            assertMessageBody(i, message);

            Assert.assertEquals(i, message.getIntProperty("counter").intValue());

            message.acknowledge();
         }
      }

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testFailThenReceiveMoreMessagesAfterFailover2() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setBlockOnAcknowledge(true);
      locator.setReconnectAttempts(-1);
      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(true, true, 0);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());

         message.acknowledge();
      }

      crash(session);

      // Send some more

      for (int i = numMessages; i < numMessages * 2; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      // Should get the same ones after failover since we didn't ack

      for (int i = numMessages; i < numMessages * 2; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());

         message.acknowledge();
      }

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testSimpleSendAfterFailoverDurableTemporary() throws Exception
   {
      testSimpleSendAfterFailover(true, true);
   }

   public void testSimpleSendAfterFailoverNonDurableTemporary() throws Exception
   {
      testSimpleSendAfterFailover(false, true);
   }

   public void testSimpleSendAfterFailoverDurableNonTemporary() throws Exception
   {
      testSimpleSendAfterFailover(true, false);
   }

   public void testSimpleSendAfterFailoverNonDurableNonTemporary() throws Exception
   {
      testSimpleSendAfterFailover(false, false);
   }

   private void testSimpleSendAfterFailover(final boolean durable, final boolean temporary) throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setBlockOnAcknowledge(true);
      locator.setReconnectAttempts(-1);
      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      ClientSession session = sf.createSession(true, true, 0);

      if (temporary)
      {
         session.createTemporaryQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null);
      }
      else
      {
         session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, durable);
      }


      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 100;

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      crash(session);

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(i % 2 == 0);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());

         message.acknowledge();
      }

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void _testForceBlockingReturn() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setBlockOnAcknowledge(true);
      locator.setReconnectAttempts(-1);
      ClientSessionFactoryInternal sf = (ClientSessionFactoryInternal) locator.createSessionFactory();

      // Add an interceptor to delay the send method so we can get time to cause failover before it returns

      //liveServer.getRemotingService().addInterceptor(new DelayInterceptor());



      final ClientSession session = sf.createSession(true, true, 0);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      final ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      class Sender extends Thread
      {
         @Override
         public void run()
         {
            ClientMessage message = session.createMessage(true);

            message.getBodyBuffer().writeString("message");

            try
            {
               producer.send(message);
            }
            catch (HornetQException e)
            {
               this.e = e;
            }
         }

         volatile HornetQException e;
      }

      Sender sender = new Sender();

      sender.start();

      Thread.sleep(500);

      crash(session);

      sender.join();

      Assert.assertNotNull(sender.e);

      Assert.assertEquals(sender.e.getCode(), HornetQException.UNBLOCKED);

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testCommitOccurredUnblockedAndResendNoDuplicates() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      locator.setBlockOnAcknowledge(true);
      final ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      final ClientSession session = sf.createSession(false, false);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      final int numMessages = 100;

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      String txID = "my-tx-id";

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(true);

         if (i == 0)
         {
            // Only need to add it on one message per tx
            message.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, new SimpleString(txID));
         }

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      class Committer extends Thread
      {
         DelayInterceptor2 interceptor = new DelayInterceptor2();

         @Override
         public void run()
         {
            try
            {
               sf.getServerLocator().addInterceptor(interceptor);

               session.commit();
            }
            catch (HornetQException e)
            {
               if (e.getCode() == HornetQException.TRANSACTION_ROLLED_BACK)
               {
                  // Ok - now we retry the commit after removing the interceptor

                  sf.getServerLocator().removeInterceptor(interceptor);

                  try
                  {
                     session.commit();

                     failed = false;
                  }
                  catch (HornetQException e2)
                  {

                  }

               }
            }
         }

         volatile boolean failed = true;
      }

      Committer committer = new Committer();

      // Commit will occur, but response will never get back, connetion is failed, and commit should be unblocked
      // with transaction rolled back

      committer.start();

      // Wait for the commit to occur and the response to be discarded
      assertTrue(committer.interceptor.await());

      Thread.sleep(500);

      crash(session);

      committer.join();

      Assert.assertFalse(committer.failed);

      session.close();

      ClientSession session2 = sf.createSession(false, false);

      producer = session2.createProducer(FailoverTestBase.ADDRESS);

      // We now try and resend the messages since we get a transaction rolled back exception
      // but the commit actually succeeded, duplicate detection should kick in and prevent dups

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session2.createMessage(true);

         if (i == 0)
         {
            // Only need to add it on one message per tx
            message.putStringProperty(Message.HDR_DUPLICATE_DETECTION_ID, new SimpleString(txID));
         }

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session2.commit();

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());

         message.acknowledge();
      }

      ClientMessage message = consumer.receiveImmediate();

      Assert.assertNull(message);

      session2.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testCommitDidNotOccurUnblockedAndResend() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setBlockOnAcknowledge(true);
      locator.setReconnectAttempts(-1);
      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);

      final ClientSession session = sf.createSession(false, false);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      final int numMessages = 100;

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(true);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      class Committer extends Thread
      {
         @Override
         public void run()
         {
            Interceptor interceptor = new DelayInterceptor3();

            try
            {
               liveServer.addInterceptor(interceptor);

               session.commit();
            }
            catch (HornetQException e)
            {
               if (e.getCode() == HornetQException.TRANSACTION_ROLLED_BACK)
               {
                  // Ok - now we retry the commit after removing the interceptor

                  liveServer.removeInterceptor(interceptor);

                  try
                  {
                     session.commit();

                     failed = false;
                  }
                  catch (HornetQException e2)
                  {
                  }
               }
            }
         }

         volatile boolean failed = true;
      }

      Committer committer = new Committer();

      committer.start();

      Thread.sleep(500);

      crash(session);

      committer.join();

      Assert.assertFalse(committer.failed);

      session.close();

      ClientSession session2 = sf.createSession(false, false);

      producer = session2.createProducer(FailoverTestBase.ADDRESS);

      // We now try and resend the messages since we get a transaction rolled back exception

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session2.createMessage(true);

         setBody(i, message);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session2.commit();

      ClientConsumer consumer = session2.createConsumer(FailoverTestBase.ADDRESS);

      session2.start();

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());

         message.acknowledge();
      }

      ClientMessage message = consumer.receiveImmediate();

      Assert.assertNull(message);

      session2.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testBackupServerNotRemoved() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setFailoverOnInitialConnection(true);
      locator.setReconnectAttempts(-1);
      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);
      final CountDownLatch latch = new CountDownLatch(1);

      class MyListener implements SessionFailureListener
      {
         public void connectionFailed(final HornetQException me, boolean failedOver)
         {
            latch.countDown();
         }

         public void beforeReconnect(HornetQException exception)
         {
            System.out.println("MyListener.beforeReconnect");
         }
      }

      ClientSession session = sendAndConsume(sf, true);

      session.addFailureListener(new MyListener());

      backupServer.stop();

      liveServer.crash();
      
      backupServer.start();

      assertTrue(latch.await(5, TimeUnit.SECONDS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      ClientMessage message = session.createMessage(true);

      setBody(0, message);

      producer.send(message);

      session.close();

      sf.close();
      
      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testLiveAndBackupLiveComesBack() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setFailoverOnInitialConnection(true);
      locator.setReconnectAttempts(-1);
      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);
      final CountDownLatch latch = new CountDownLatch(1);

      class MyListener implements SessionFailureListener
      {
         public void connectionFailed(final HornetQException me, boolean failedOver)
         {
            latch.countDown();
         }

         public void beforeReconnect(HornetQException exception)
         {
            System.out.println("MyListener.beforeReconnect");
         }
      }

      ClientSession session = sendAndConsume(sf, true);

      session.addFailureListener(new MyListener());

      backupServer.stop();

      liveServer.crash();

      liveServer.start();

      assertTrue(latch.await(5, TimeUnit.SECONDS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      ClientMessage message = session.createMessage(true);

      setBody(0, message);

      producer.send(message);

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testLiveAndBackupLiveComesBackNewFactory() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setFailoverOnInitialConnection(true);
      locator.setReconnectAttempts(-1);
      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);
      final CountDownLatch latch = new CountDownLatch(1);

      class MyListener implements SessionFailureListener
      {
         public void connectionFailed(final HornetQException me, boolean failedOver)
         {
            latch.countDown();
         }

         public void beforeReconnect(HornetQException exception)
         {
            System.out.println("MyListener.beforeReconnect");
         }
      }

      ClientSession session = sendAndConsume(sf, true);

      session.addFailureListener(new MyListener());

      backupServer.stop();

      liveServer.crash();

      liveServer.start();

      assertTrue(latch.await(5, TimeUnit.SECONDS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      ClientMessage message = session.createMessage(true);

      setBody(0, message);

      producer.send(message);

      session.close();

      sf.close();

      sf = (ClientSessionFactoryInternal) locator.createSessionFactory();

      session = sf.createSession();

      ClientConsumer cc = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();
      
      ClientMessage cm = cc.receive(5000);

      assertNotNull(cm);

      Assert.assertEquals("message0", cm.getBodyBuffer().readString());

      session.close();
      
      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }

   public void testLiveAndBackupBackupComesBackNewFactory() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setFailoverOnInitialConnection(true);
      locator.setReconnectAttempts(-1);
      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);
      final CountDownLatch latch = new CountDownLatch(1);

      class MyListener implements SessionFailureListener
      {
         public void connectionFailed(final HornetQException me, boolean failedOver)
         {
            latch.countDown();
         }

         public void beforeReconnect(HornetQException exception)
         {
            System.out.println("MyListener.beforeReconnect");
         }
      }

      ClientSession session = sendAndConsume(sf, true);

      session.addFailureListener(new MyListener());

      backupServer.stop();

      liveServer.crash();

      backupServer.start();

      assertTrue(latch.await(5, TimeUnit.SECONDS));

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      ClientMessage message = session.createMessage(true);

      setBody(0, message);

      producer.send(message);

      session.close();

      sf.close();

      sf = (ClientSessionFactoryInternal) locator.createSessionFactory();

      session = sf.createSession();

      ClientConsumer cc = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      ClientMessage cm = cc.receive(5000);

      assertNotNull(cm);

      Assert.assertEquals("message0", cm.getBodyBuffer().readString());

      session.close();

      sf.close();

      Assert.assertEquals(0, sf.numSessions());

      Assert.assertEquals(0, sf.numConnections());
   }
   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live)
   {
      return getInVMTransportAcceptorConfiguration(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live)
   {
      return getInVMConnectorTransportConfiguration(live);
   }

   /**
    * @param i
    * @param message
    */
   protected void assertMessageBody(final int i, final ClientMessage message)
   {
      Assert.assertEquals("message" + i, message.getBodyBuffer().readString());
   }

   /**
    * @param i
    * @param message
    * @throws Exception 
    */
   protected void setBody(final int i, final ClientMessage message) throws Exception
   {
      message.getBodyBuffer().writeString("message" + i);
   }

   // Private -------------------------------------------------------

   private ClientSession sendAndConsume(final ClientSessionFactory sf, final boolean createQueue) throws Exception
   {
      ClientSession session = sf.createSession(false, true, true);

      if (createQueue)
      {
         session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, false);
      }

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      final int numMessages = 1000;

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message = session.createMessage(HornetQTextMessage.TYPE,
                                                       false,
                                                       0,
                                                       System.currentTimeMillis(),
                                                       (byte)1);
         message.putIntProperty(new SimpleString("count"), i);
         message.getBodyBuffer().writeString("aardvarks");
         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      session.start();

      for (int i = 0; i < numMessages; i++)
      {
         ClientMessage message2 = consumer.receive();

         Assert.assertEquals("aardvarks", message2.getBodyBuffer().readString());

         Assert.assertEquals(i, message2.getObjectProperty(new SimpleString("count")));

         message2.acknowledge();
      }

      ClientMessage message3 = consumer.receiveImmediate();

      Assert.assertNull(message3);

      return session;
   }

   // Inner classes -------------------------------------------------


}
