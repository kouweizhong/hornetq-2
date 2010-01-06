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
package org.hornetq.jms.example;

import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.common.example.HornetQExample;
import org.hornetq.integration.transports.netty.NettyConnectorFactory;
import org.hornetq.integration.transports.netty.TransportConstants;

/**
 * 
 * This example demonstrates how a JMS client can directly instantiate it's JMS Objects like
 * Queue, ConnectionFactory, etc. without having to use JNDI at all.
 * 
 * For more information please see the readme.html file.
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 */
public class InstantiateConnectionFactoryExample extends HornetQExample
{
   public static void main(final String[] args)
   {
      new InstantiateConnectionFactoryExample().run(args);
   }

   @Override
   public boolean runExample() throws Exception
   {
      Connection connection = null;
      try
      {
         // Step 1. Directly instantiate the JMS Queue object.
         Queue queue = HornetQJMSClient.createHornetQQueue("exampleQueue");

         // Step 2. Instantiate the TransportConfiguration object which contains the knowledge of what transport to use,
         // The server port etc.

         Map<String, Object> connectionParams = new HashMap<String, Object>();
         connectionParams.put(TransportConstants.PORT_PROP_NAME, 5446);

         TransportConfiguration transportConfiguration = new TransportConfiguration(NettyConnectorFactory.class.getName(),
                                                                                    connectionParams);

         // Step 3 Directly instantiate the JMS ConnectionFactory object using that TransportConfiguration
         ConnectionFactory cf = HornetQJMSClient.createConnectionFactory(transportConfiguration);

         // Step 4.Create a JMS Connection
         connection = cf.createConnection();

         // Step 5. Create a JMS Session
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         // Step 6. Create a JMS Message Producer
         MessageProducer producer = session.createProducer(queue);

         // Step 7. Create a Text Message
         TextMessage message = session.createTextMessage("This is a text message");

         System.out.println("Sent message: " + message.getText());

         // Step 8. Send the Message
         producer.send(message);

         // Step 9. Create a JMS Message Consumer
         MessageConsumer messageConsumer = session.createConsumer(queue);

         // Step 10. Start the Connection
         connection.start();

         // Step 11. Receive the message
         TextMessage messageReceived = (TextMessage)messageConsumer.receive(5000);

         System.out.println("Received message: " + messageReceived.getText());

         return true;
      }
      finally
      {
         if (connection != null)
         {
            connection.close();
         }
      }
   }

}
