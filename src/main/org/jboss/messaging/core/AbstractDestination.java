/**
 * JBoss, the OpenSource J2EE WebOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.messaging.core;

import org.jboss.messaging.interfaces.Channel;
import org.jboss.messaging.interfaces.Distributor;
import org.jboss.messaging.interfaces.Message;
import org.jboss.messaging.interfaces.Receiver;

import java.util.Iterator;

/**
 * A Channel with a routing policy in place. It delegates the routing policy to a Router instance.
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 */
public abstract class AbstractDestination implements Channel, Distributor
{
   // Attributes ----------------------------------------------------

   protected Pipe inputPipe;
   protected AbstractRouter router;

   // Constructors --------------------------------------------------

   protected AbstractDestination()
   {
      router = createRouter();
      inputPipe = new Pipe(router);
   }


   // Public --------------------------------------------------------

   // Channel implementation ----------------------------------------

   public boolean handle(Message m)
   {
      return inputPipe.handle(m);
   }

   public boolean hasMessages()
   {
      return inputPipe.hasMessages();
   }

   public boolean setSynchronous(boolean b)
   {
      return inputPipe.setSynchronous(b);
   }

   public boolean isSynchronous()
   {
      return inputPipe.isSynchronous();
   }

   /**
    * Override if you want a more sophisticated delivery mechanism.
    */
   public boolean deliver()
   {
      return inputPipe.deliver();
   }

   // Distributor interface -----------------------------------------

   public boolean add(Receiver r)
   {
      if (!router.add(r))
      {
         return false;
      }

      // adding a Receiver triggers an asynchronous delivery attempt
      if (inputPipe.hasMessages())
      {
         inputPipe.deliver();
      }
      return true;
   }

   public boolean remove(Receiver r)
   {
      return router.remove(r);
   }

   public boolean contains(Receiver r)
   {
      return router.contains(r);
   }

   public Iterator iterator()
   {
      return router.iterator();
   }

   public void clear()
   {
      router.clear();
   }

   public boolean acknowledged(Receiver r)
   {
      return router.acknowledged(r);
   }


   // Protected -----------------------------------------------------

   protected abstract AbstractRouter createRouter();

   // DEBUG ---------------------------------------------------------

   public String dump()
   {
      StringBuffer sb = new StringBuffer();
      sb.append(inputPipe.dump());
      sb.append(" ");
      sb.append(router.dump());
      return sb.toString();
   }
}




