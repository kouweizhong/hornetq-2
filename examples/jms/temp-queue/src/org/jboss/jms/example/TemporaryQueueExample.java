/*
   * JBoss, Home of Professional Open Source
   * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.jms.example;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.InvalidDestinationException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import org.hornetq.common.example.JBMExample;

/**
 * A simple JMS example that shows how to use temporary queues.
 *
 * @author <a href="hgao@redhat.com">Howard Gao</a>
 */
public class TemporaryQueueExample extends JBMExample
{
   public static void main(String[] args)
   {
      new TemporaryQueueExample().run(args);
   }

   public boolean runExample() throws Exception
   {
      Connection connection = null;
      InitialContext initialContext = null;
      try
      {
         // Step 1. Create an initial context to perform the JNDI lookup.
         initialContext = getContext(0);

         // Step 2. Look-up the JMS connection factory
         ConnectionFactory cf = (ConnectionFactory)initialContext.lookup("/ConnectionFactory");
         
         // Step 3. Create a JMS Connection
         connection = cf.createConnection();

         // Step 4. Start the connection
         connection.start();

         // Step 5. Create a JMS session with AUTO_ACKNOWLEDGE mode
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         // Step 6. Create a Temporary Queue
         TemporaryQueue tempQueue = session.createTemporaryQueue();
         
         System.out.println("Temporary queue is created: " + tempQueue);
         
         // Step 7. Create a JMS message producer
         MessageProducer messageProducer = session.createProducer(tempQueue);

         // Step 8. Create a text message
         TextMessage message = session.createTextMessage("This is a text message");
         
         // Step 9. Send the text message to the queue
         messageProducer.send(message);

         System.out.println("Sent message: " + message.getText());
         
         // Step 11. Create a message consumer
         MessageConsumer messageConsumer = session.createConsumer(tempQueue);

         // Step 12. Receive the message from the queue
         message = (TextMessage) messageConsumer.receive(5000);

         System.out.println("Received message: " + message.getText());
         
         // Step 13. Close the consumer and producer
         messageConsumer.close();
         messageProducer.close();
         
         // Step 14. Delete the temporary queue
         tempQueue.delete();
         
         // Step 15. Create another temporary queue.
         TemporaryQueue tempQueue2 = session.createTemporaryQueue();
         
         System.out.println("Another temporary queue is created: " + tempQueue2);
         
         // Step 16. Close the connection.
         connection.close();
         
         // Step 17. Create a new connection.
         connection = cf.createConnection();
         
         // Step 18. Create a new session.
         session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         
         // Step 19. Try to access the tempQueue2 outside its lifetime
         try
         {
            messageConsumer = session.createConsumer(tempQueue2);
            throw new Exception("Temporary queue cannot be accessed outside its lifecycle!");
         }
         catch (InvalidDestinationException e)
         {
            System.out.println("Exception got when trying to access a temp queue outside its scope: " + e);
         }
         
         return true;
      }
      finally
      {
         if(connection != null)
         {
            // Step 20. Be sure to close our JMS resources!
            connection.close();
         }
         if (initialContext != null)
         {
            //Step 21. Also close the initialContext!
            initialContext.close();
         }
      }
   }
}
