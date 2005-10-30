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
package org.jboss.test.messaging.jms.perf;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.jboss.logging.Logger;

/**
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 */
public class SenderJob extends BaseThroughputJob
{
   private static final long serialVersionUID = -4031253412475892666L;

   private transient static final Logger log = Logger.getLogger(SenderJob.class);

   protected boolean anon;
   
   protected int msgSize;

   protected int deliveryMode;
   
   protected MessageFactory mf;
   
   protected long initialPause;
   
   public Servitor createServitor(int numMessages)
   {
      return new Sender(numMessages);
   }
   
   protected void logInfo()
   {
      super.logInfo();
      log.trace("Use anonymous producer? " + anon);
      log.trace("Message size: " + msgSize);
      log.trace("Message type: " + mf.getClass().getName());
      log.trace("Delivery Mode:" + (deliveryMode == DeliveryMode.PERSISTENT ? "Persistent" : "Non-persistent"));
      log.trace("Initial pause:" + initialPause);
   }
   
   public SenderJob(String slaveURL, String serverURL, String destinationName, int numConnections,
         int numSessions, boolean transacted, int transactionSize, 
         int numMessages, boolean anon, int messageSize,
         MessageFactory messageFactory, int deliveryMode, long initialPause)
   {
      super (slaveURL, serverURL, destinationName, numConnections,
            numSessions, transacted, transactionSize, numMessages);
      this.anon = anon;
      this.msgSize = messageSize;
      this.mf = messageFactory;
      this.deliveryMode = deliveryMode;
      this.initialPause = initialPause;
   }
   


   protected class Sender extends AbstractServitor
   {
      
      Sender(int numMessages)
      {
         super(numMessages);
      }
      
      MessageProducer prod;
      Session sess;
      
      public void deInit()
      {
         try
         {
            sess.close();
         }      
         catch (Exception e)
         {
            log.error("!!!!!!!!!!!!!!!!!!!!!Close failed", e);
            failed = true;
         }
      }
      
      public void init()
      {
         try
         {
            Connection conn = getNextConnection();
            
            sess = conn.createSession(transacted, Session.AUTO_ACKNOWLEDGE); //Ackmode doesn't matter            
            
            prod = null;
            
            if (anon)
            {
               prod = sess.createProducer(null);
            }
            else
            {
               prod = sess.createProducer(dest);
            }
            
            prod.setDeliveryMode(deliveryMode); 
            
            Thread.sleep(initialPause);
         }
         catch (Exception e)
         {
            log.error("!!!!!!!!!!!!!!!!!!!!!!!!Sender failed", e);
            failed = true;
         }
      }
      
      public void run()
      {
         try
         {
            int count = 0;
            
            while (count < numMessages)
            {               
            
               Message m = mf.getMessage(sess, msgSize);
               
               if (anon)
               {
                  prod.send(dest, m);            
               }
               else
               {
                  prod.send(m);
               }
               
               count++;
           
               if (transacted)
               {                  
                  if (count % transactionSize == 0)
                  {
                     sess.commit();
                  }
               }                   
            }

         }
         catch (Exception e)
         {
            log.error("!!!!!!!!!!!!!!!!!!!!!!!!Sender failed", e);
            failed = true;
         }
      }
      
      public boolean isFailed()
      {
         return failed;
      }
   } 
   

   /**
    * Set the anon.
    * 
    * @param anon The anon to set.
    */
   public void setAnon(boolean anon)
   {
      this.anon = anon;
   }

   /**
    * Set the deliveryMode.
    * 
    * @param deliveryMode The deliveryMode to set.
    */
   public void setDeliveryMode(int deliveryMode)
   {
      this.deliveryMode = deliveryMode;
   }

   /**
    * Set the mf.
    * 
    * @param mf The mf to set.
    */
   public void setMf(MessageFactory mf)
   {
      this.mf = mf;
   }

   /**
    * Set the msgSize.
    * 
    * @param msgSize The msgSize to set.
    */
   public void setMsgSize(int msgSize)
   {
      this.msgSize = msgSize;
   }
   
   public void setInitialPause(long initialPause)
   {
      this.initialPause = initialPause;
   }
}
