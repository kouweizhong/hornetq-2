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
package org.jboss.jms.server.endpoint;

import java.util.ArrayList;
import java.util.Iterator;

import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;

import org.jboss.jms.delegate.BrowserDelegate;
import org.jboss.jms.selector.Selector;
import org.jboss.logging.Logger;
import org.jboss.messaging.core.Filter;
import org.jboss.messaging.core.Channel;
import org.jboss.messaging.core.Routable;


/**
 * @author <a href="mailto:tim.l.fox@gmail.com">Tim Fox</a>
 * @version $Revision$
 *
 * $Id$
 */
public class ServerBrowserDelegate implements BrowserDelegate
{
   private static final Logger log = Logger.getLogger(ServerBrowserDelegate.class);

   protected Iterator iterator;
   
   protected ServerSessionDelegate session;
   
   protected String browserID;
   
   protected boolean closed;


   ServerBrowserDelegate(ServerSessionDelegate session, String browserID, Channel destination, String messageSelector)
      throws JMSException
   {     
      this.session = session;
      this.browserID = browserID;
		Filter filter = null;
		if (messageSelector != null)
		{	
			filter = new Selector(messageSelector);		
		}
		iterator = destination.browse(filter).iterator();
   }
      
   
   public boolean hasNextMessage() throws JMSException
   {
      if (closed)
      {
         throw new IllegalStateException("Browser is closed");
      }
      return iterator.hasNext();
   }
   
   public Message nextMessage() throws JMSException
   {
      if (closed)
      {
         throw new IllegalStateException("Browser is closed");
      }
      Routable r = (Routable)iterator.next();

      if (log.isTraceEnabled()) { log.trace("returning the message corresponding to " + r); }
      return (Message)r.getMessage();
   }
   
   
	//Is this the most efficient way to pass it back?
	//why not just pass back the arraylist??
   public Message[] nextMessageBlock(int maxMessages) throws JMSException
   {
      if (closed)
      {
         throw new IllegalStateException("Browser is closed");
      }
      
      if (maxMessages < 2) throw new IllegalArgumentException("maxMessages must be >=2 otherwise use nextMessage");
      
      ArrayList messages = new ArrayList(maxMessages);
      int i = 0;
      while (i < maxMessages)
      {
         if (iterator.hasNext())
         {
            Message m = (Message)((Routable)iterator.next()).getMessage();
            messages.add(m);
            i++;
         }
         else break;
      }		
		return (Message[])messages.toArray(new Message[messages.size()]);		        
   }
   
   
   public void close() throws JMSException
   {
      if (closed)
      {
         throw new IllegalStateException("Browser is already closed");
      }
      iterator = null;
      this.session.producers.remove(this.browserID);
      closed = true;
   }
   
   public void closing() throws JMSException
   {
      //Do nothing
   }
}
