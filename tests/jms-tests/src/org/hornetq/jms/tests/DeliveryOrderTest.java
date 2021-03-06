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

package org.hornetq.jms.tests;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.CountDownLatch;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.hornetq.jms.tests.util.ProxyAssertSupport;

/**
 * 
 * A DeliveryOrderTest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @version <tt>$Revision: 1.1 $</tt>
 *
 * $Id$
 *
 */
public class DeliveryOrderTest extends JMSTestCase
{

   public void testOutOfOrder() throws Exception
   {
      Connection conn = null;
      try
      {
         conn = JMSTestCase.cf.createConnection();

         Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);

         Session sess2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sess.createProducer(HornetQServerTestCase.queue1);

         MessageConsumer cons = sess2.createConsumer(HornetQServerTestCase.queue1);

         CountDownLatch latch = new CountDownLatch(1);

         final int NUM_MESSAGES = 1000;

         MyListener listener = new MyListener(latch, NUM_MESSAGES);

         cons.setMessageListener(listener);

         conn.start();

         for (int i = 0; i < NUM_MESSAGES; i++)
         {
            TextMessage tm = sess.createTextMessage("message" + i);

            prod.send(tm);

            if (i % 10 == 0)
            {
               sess.commit();
            }
         }

         // need extra commit for cases in which the last message index is not a multiple of 10
         sess.commit();

         latch.await(20000, MILLISECONDS);

         if (listener.failed)
         {
            ProxyAssertSupport.fail("listener failed: " + listener.getError());
         }

      }
      finally
      {
         if (conn != null)
         {
            conn.close();
         }
      }
   }

   class MyListener implements MessageListener
   {
      private int c;

      private final int num;

      private final CountDownLatch latch;

      private volatile boolean failed;

      private String error;

      MyListener(final CountDownLatch latch, final int num)
      {
         this.latch = latch;
         this.num = num;
      }

      public void onMessage(final Message msg)
      {
         // preserve the first error
         if (failed)
         {
            return;
         }

         try
         {
            TextMessage tm = (TextMessage)msg;

            if (!("message" + c).equals(tm.getText()))
            {
               // Failed
               failed = true;
               setError("Listener was supposed to get " + "message" + c + " but got " + tm.getText());
               latch.countDown();
            }

            c++;

            if (c == num)
            {
               latch.countDown();

               try
               {
                  Thread.sleep(2000);
               }
               catch (Exception e)
               {
               }
            }
         }
         catch (JMSException e)
         {
            e.printStackTrace();

            // Failed
            failed = true;
            setError("Listener got exception " + e.toString());
            latch.countDown();
         }
      }

      public synchronized String getError()
      {
         return error;
      }

      private synchronized void setError(final String s)
      {
         error = s;
      }

   }

}
