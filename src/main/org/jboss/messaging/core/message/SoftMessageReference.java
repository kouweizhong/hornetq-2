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
package org.jboss.messaging.core.message;



import org.jboss.messaging.core.MessageReference;
import org.jboss.messaging.core.Message;

import java.io.Serializable;
import java.util.Iterator;
import java.lang.ref.SoftReference;

/**
 * A MessageReference implementation that contains a soft reference to the message.
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 */
public class SoftMessageReference extends RoutableSupport implements MessageReference
{
   // Attributes ----------------------------------------------------

   // only used for testing
   
   
   /**
    * THIS CLASS IS NO LONGER USED
    */
   
   /* FIXME
    * Note I have set this to false for the following reason:
    * 
    * When we are using soft references, and the server is handling a lot of messages
    * the memory eventually reaches max. available memory, then the server spends all it's time
    * gc'ing the soft references, causing it to slow to a crawl.
    * This was noticed in perf. testing and made the server unusable.
    * Perhaps a LRU cache would be a better choice in this situation?
    * -Tim
    */
   public static boolean keepSoftReference = false;

   protected transient UnreliableMessageStore ms;
   private SoftReference softReference;

   // Constructors --------------------------------------------------

   /**
    * Required by externalization.
    */
   public SoftMessageReference()
   {
   }

   SoftMessageReference(Serializable messageID, boolean reliable,
                        long expirationTime, UnreliableMessageStore ms)
   {
      super(messageID, reliable, expirationTime);

      // TODO how about headers here?

      this.ms = ms;
   }

   /**
    * Creates a reference based on a given message.
    */
   public SoftMessageReference(Message m, UnreliableMessageStore ms)
   {
      this(m.getMessageID(), m.isReliable(), m.getExpiration(), ms);

      for(Iterator i = m.getHeaderNames().iterator(); i.hasNext(); )
      {
         String name = (String)i.next();
         putHeader(name, m.getHeader(name));
      }

      refreshReference(m);
   }

   // Message implementation ----------------------------------------

   public boolean isReference()
   {
      return true;
   }

   // MessageReference implementation -------------------------------

   public Serializable getStoreID()
   {
      return ms.getStoreID();
   }

   public Message getMessage()
   {
      Message m = (Message)softReference.get();
      if (m == null)
      {
         m = ms.retrieve(this);
         
         //FIXME
         //Hmmmm... if the same message is being delivered to, say, 2 receivers
         //on a topic, and only one of them recovers then the redelivered flag
         //should be set for only one of them.
         //Doing this sets it for the whole message!!
         
         if (this.isRedelivered())
         {
            m.setRedelivered(true);
         }
         
         refreshReference(m);
      }
      return m;
   }


   // Public --------------------------------------------------------

   public boolean equals(Object o)
   {
      if (this == o)
      {
         return true;
      }
      if (!(o instanceof SoftMessageReference))
      {
         return false;
      }
      SoftMessageReference that = (SoftMessageReference)o;
      if (messageID == null)
      {
         return that.messageID == null;
      }
      return messageID.equals(that.messageID);
   }

   public int hashCode()
   {
      if (messageID == null)
      {
         return 0;
      }
      return messageID.hashCode();
   }

   public String toString()
   {
      return "Reference["+messageID+"]";
   }

   // Package protected ---------------------------------------------

   void refreshReference(Message m)
   {
      if (keepSoftReference)
      {
         softReference = new SoftReference(m);
      }
      else if (softReference == null)
      {
         softReference = new SoftReference(null);
      }
   }

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
