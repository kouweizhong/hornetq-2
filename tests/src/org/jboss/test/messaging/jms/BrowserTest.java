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
package org.jboss.test.messaging.jms;

import java.util.Enumeration;

import javax.jms.Connection;
import javax.jms.InvalidDestinationException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TextMessage;
import javax.naming.InitialContext;

import org.jboss.jms.client.JBossConnectionFactory;
import org.jboss.jms.destination.JBossQueue;
import org.jboss.test.messaging.MessagingTestCase;
import org.jboss.test.messaging.tools.ServerManagement;


/**
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 *
 * $Id$
 */
public class BrowserTest extends MessagingTestCase
{
	
	//	 Constants -----------------------------------------------------------------------------------
	
	// Static ---------------------------------------------------------------------------------------
	
	// Attributes -----------------------------------------------------------------------------------
	
	protected InitialContext initialContext;

	protected JBossConnectionFactory cf;
	protected Queue queue;
	protected Topic topic;
   protected Connection connection;
   protected Session session;
   protected MessageProducer producer;

	// Constructors ---------------------------------------------------------------------------------
	
	public BrowserTest(String name)
	{
		super(name);
	}
	
	// Public ---------------------------------------------------------------------------------------

   public void testCreateBrowserOnNullDestination() throws Exception
   {
      try
      {
         session.createBrowser(null);
         fail("should throw exception");
      }
      catch(InvalidDestinationException e)
      {
         // OK
      }
   }

   public void testCreateBrowserOnNonExistentQueue() throws Exception
   {
      Connection pconn = cf.createConnection();

      try
      {
         Session ps = pconn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try
         {
            ps.createBrowser(new JBossQueue("NoSuchQueue"));
            fail("should throw exception");
         }
         catch(InvalidDestinationException e)
         {
            // OK
         }
      }
      finally
      {
         pconn.close();
      }
   }

	public void testBrowse() throws Exception
	{
		log.trace("Starting testBrowse()");

		final int numMessages = 10;
		
		for (int i = 0; i < numMessages; i++)
		{
			Message m = session.createMessage();
         m.setIntProperty("cnt", i);
			producer.send(m);         
		}
		
		log.trace("Sent messages");

		QueueBrowser browser = session.createBrowser(queue);
		
		assertEquals(browser.getQueue(), queue);
		
		assertNull(browser.getMessageSelector());
		
		Enumeration en = browser.getEnumeration();
		
		int count = 0;
		while (en.hasMoreElements())
		{
			en.nextElement();
			count++;
		}
		
		assertEquals(numMessages, count);
		
		MessageConsumer mc = session.createConsumer(queue);
		
		connection.start();
		
		for (int i = 0; i < numMessages; i++)
		{
			Message m = mc.receive();
         assertNotNull(m);
		}
      
		browser = session.createBrowser(queue);
		en = browser.getEnumeration();
				
		count = 0;
		while (en.hasMoreElements())
		{
			Message mess = (Message)en.nextElement();
			log.trace("message:" + mess);
			count++;
		}
		
		log.trace("Received " + count + " messages");
      
      assertEquals(0, count);
	}

	public void testBrowseWithSelector() throws Exception
	{

		final int numMessages = 100;
		
		for (int i = 0; i < numMessages; i++)
		{
			Message m = session.createMessage();
			m.setIntProperty("test_counter", i+1);
			producer.send(m);
		}
		
		log.trace("Sent messages");
		
		QueueBrowser browser = session.createBrowser(queue, "test_counter > 30");
		
		Enumeration en = browser.getEnumeration();
		int count = 0;
		while (en.hasMoreElements())
		{
			Message m = (Message)en.nextElement();
			int testCounter = m.getIntProperty("test_counter");
			assertTrue(testCounter > 30);
			count++;
		}
		assertEquals(70, count);
	}

   public void testGetEnumeration() throws Exception
   {
      // send a message to the queue

      Message m = session.createTextMessage("A");
      producer.send(m);

      // make sure we can browse it

      QueueBrowser browser = session.createBrowser(queue);

      Enumeration en = browser.getEnumeration();

      assertTrue(en.hasMoreElements());

      TextMessage rm = (TextMessage)en.nextElement();

      assertNotNull(rm);
      assertEquals("A", rm.getText());

      assertFalse(en.hasMoreElements());

      // create a *new* enumeration, that should reset it

      en = browser.getEnumeration();

      assertTrue(en.hasMoreElements());

      rm = (TextMessage)en.nextElement();

      assertNotNull(rm);
      assertEquals("A", rm.getText());

      assertFalse(en.hasMoreElements());
   }

   // Package protected ----------------------------------------------------------------------------
	
	// Protected ------------------------------------------------------------------------------------

   protected void setUp() throws Exception
   {

      super.setUp();
      ServerManagement.start("all");

      initialContext = new InitialContext(ServerManagement.getJNDIEnvironment());
      cf = (JBossConnectionFactory)initialContext.lookup("/ConnectionFactory");

      ServerManagement.undeployQueue("Queue");

      ServerManagement.deployQueue("Queue");
      queue = (Queue)initialContext.lookup("/queue/Queue");

      drainDestination(cf, queue);

      connection = cf.createConnection();
      session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      producer = session.createProducer(queue);

   }

   protected void tearDown() throws Exception
   {
      ServerManagement.undeployQueue("Queue");

      connection.close();

      super.tearDown();
   }

   // Private --------------------------------------------------------------------------------------
	
	// Inner classes --------------------------------------------------------------------------------
}

