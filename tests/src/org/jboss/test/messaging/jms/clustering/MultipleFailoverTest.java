/*
* JBoss, Home of Professional Open Source
* Copyright 2005, JBoss Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.test.messaging.jms.clustering;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.jboss.test.messaging.jms.clustering.base.ClusteringTestBase;
import org.jboss.test.messaging.tools.ServerManagement;

import EDU.oswego.cs.dl.util.concurrent.Latch;

/**
 * A test where we kill multiple nodes and make sure the failover works correctly in these condtions
 * too.
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class MultipleFailoverTest extends ClusteringTestBase
{
   // Constants ------------------------------------------------------------------------------------

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   // Constructors ---------------------------------------------------------------------------------

   public MultipleFailoverTest(String name)
   {
      super(name);
   }

   // Public ---------------------------------------------------------------------------------------

   public void testAllKindsOfServerFailures() throws Exception
   {
      Connection conn = null;
      TextMessage m = null;
      MessageProducer prod = null;
      MessageConsumer cons = null;

      try
      {
         // we start with a cluster of two (server 0 and server 1)

         conn = cf.createConnection();
         conn.start();

         // send/receive message
         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         prod = s.createProducer(queue[0]);
         cons = s.createConsumer(queue[0]);
         prod.send(s.createTextMessage("step1"));
         m = (TextMessage)cons.receive();
         assertNotNull(m);
         assertEquals("step1", m.getText());

         log.info("killing node 0 ....");

         ServerManagement.kill(0);

         log.info("########");
         log.info("######## KILLED NODE 0");
         log.info("########");

         // send/receive message
         prod.send(s.createTextMessage("step2"));
         m = (TextMessage)cons.receive();
         assertNotNull(m);
         assertEquals("step2", m.getText());

         log.info("########");
         log.info("######## STARTING NODE 2");
         log.info("########");

         ServerManagement.start(2, "all");
         ServerManagement.deployQueue("testDistributedQueue", 2);

         // send/receive message
         prod.send(s.createTextMessage("step3"));
         m = (TextMessage)cons.receive();
         assertNotNull(m);
         assertEquals("step3", m.getText());

         log.info("killing node 1 ....");

         ServerManagement.kill(1);

         log.info("########");
         log.info("######## KILLED NODE 1");
         log.info("########");

         // send/receive message
         prod.send(s.createTextMessage("step4"));
         m = (TextMessage)cons.receive();
         assertNotNull(m);
         assertEquals("step4", m.getText());

         log.info("########");
         log.info("######## STARTING NODE 3");
         log.info("########");

         ServerManagement.start(3, "all");
         ServerManagement.deployQueue("testDistributedQueue", 3);

         // send/receive message
         prod.send(s.createTextMessage("step5"));
         m = (TextMessage)cons.receive();
         assertNotNull(m);
         assertEquals("step5", m.getText());

         log.info("killing node 2 ....");

         ServerManagement.kill(2);

         log.info("########");
         log.info("######## KILLED NODE 2");
         log.info("########");

         // send/receive message
         prod.send(s.createTextMessage("step6"));
         m = (TextMessage)cons.receive();
         assertNotNull(m);
         assertEquals("step6", m.getText());

         log.info("########");
         log.info("######## STARTING NODE 0");
         log.info("########");

         ServerManagement.start(0, "all");
         ServerManagement.deployQueue("testDistributedQueue", 0);

         // send/receive message
         prod.send(s.createTextMessage("step7"));
         m = (TextMessage)cons.receive();
         assertNotNull(m);
         assertEquals("step7", m.getText());

         log.info("killing node 3 ....");

         ServerManagement.kill(3);

         log.info("########");
         log.info("######## KILLED NODE 3");
         log.info("########");

         // send/receive message
         prod.send(s.createTextMessage("step8"));
         m = (TextMessage)cons.receive();
         assertNotNull(m);
         assertEquals("step8", m.getText());

      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }
   
   class Killer implements Runnable
   { 
      boolean failed;
      
      public void run()
      {
         try
         {                                     
            Thread.sleep(10000);
               
            log.info("Killing server 0");
            ServerManagement.kill(0);
            
            Thread.sleep(10000);
            
            log.info("starting server 0");
            ServerManagement.start(0, "all");
            
            Thread.sleep(10000);
            
            log.info("Killing server 1");
            ServerManagement.kill(1);
            
            Thread.sleep(10000);
            
            log.info("Starting server 1");
            ServerManagement.start(1, "all");
            
            Thread.sleep(10000);
            
            log.info("Killing server 0");
            ServerManagement.kill(0);
            
            Thread.sleep(10000);
            
            log.info("Starting server 0");
            ServerManagement.start(0, "all");
            
            Thread.sleep(10000);
            
            log.info("Killing server 1");
            ServerManagement.kill(1);
            
            Thread.sleep(10000);
            
            log.info("Starting server 1");
            ServerManagement.start(1, "all");
            
            Thread.sleep(10000);
            
            log.info("Killing server 0");
            ServerManagement.kill(0);
            
            Thread.sleep(10000);
            
            log.info("Starting server 0");
            ServerManagement.start(0, "all");

         }
         catch (Exception e)
         {               
            failed = true;
         }
      }
      
   }
   
   public void testFailoverFloodTwoServers() throws Exception
   {
      Connection conn = null;

      try
      {
         conn = cf.createConnection();

         Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session sessCons = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons = sessCons.createConsumer(queue[0]);

         Latch latch = new Latch();
         
         final int NUM_MESSAGES = 10000;         
         
         MessageListener list = new MyListener(latch, NUM_MESSAGES);

         cons.setMessageListener(list);

         conn.start();

         MessageProducer prod = sessSend.createProducer(queue[0]);

         prod.setDeliveryMode(DeliveryMode.PERSISTENT);

         int count = 0;
         
         Thread t = new Thread(new Killer());
         
         t.start();

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sessSend.createTextMessage("message " + count);

            prod.send(tm);
            
            Thread.sleep(250);

            log.info("sent " + count);

            count++;
         }
         
         t.join();
         
         latch.acquire();
      }
      catch (Exception e)
      {
         log.error("Failed", e);
         throw e;
      }
      finally
      {
         if (conn != null)
         {
            log.info("closing connetion");
            try
            {
               conn.close();
            }
            catch (Exception ignore)
            {
            }
            log.info("closed connection");
         }
      }
   }

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   protected void setUp() throws Exception
   {
      nodeCount = 2;

      super.setUp();

      log.debug("setup done");
   }

   protected void tearDown() throws Exception
   {
      super.tearDown();
   }

   // Private --------------------------------------------------------------------------------------

   // Inner classes --------------------------------------------------------------------------------

   class MyListener implements MessageListener
   {
      int count = 0;
      
      Latch latch;
      
      boolean failed;
      
      int num;
      
      MyListener(Latch latch, int num)
      {
         this.latch = latch;
         
         this.num = num;
      }
           
      public void onMessage(Message msg)
      {
         try
         {
            TextMessage tm = (TextMessage)msg;
            
            log.info("Received message " + tm.getText());
            
            if (!tm.getText().equals("message " + count))
            {
               failed = true;
               
               latch.release();
            }
            
            count++;
            
            if (count == num)
            {
               latch.release();
            }
         }
         catch (Exception e)
         {
            log.error("Failed to receive", e);
         }
      }
      
   }
}
