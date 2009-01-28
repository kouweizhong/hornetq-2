/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2009, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.messaging.core.server.cluster.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.messaging.core.filter.Filter;
import org.jboss.messaging.core.filter.impl.FilterImpl;
import org.jboss.messaging.core.logging.Logger;
import org.jboss.messaging.core.server.Bindable;
import org.jboss.messaging.core.server.Queue;
import org.jboss.messaging.core.server.ServerMessage;
import org.jboss.messaging.core.server.cluster.RemoteQueueBinding;
import org.jboss.messaging.util.SimpleString;

/**
 * A RemoteQueueBindingImpl
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * 
 * Created 21 Jan 2009 18:55:22
 *
 *
 */
public class RemoteQueueBindingImpl implements RemoteQueueBinding
{
   private static final Logger log = Logger.getLogger(RemoteQueueBindingImpl.class);

   private final SimpleString address;

   private final Queue storeAndForwardQueue;

   private final SimpleString uniqueName;

   private final SimpleString routingName;

   private final Filter queueFilter;

   private final Set<Filter> filters = new HashSet<Filter>();

   private final Map<SimpleString, Integer> filterCounts = new HashMap<SimpleString, Integer>();

   private int consumerCount;

   private final boolean duplicateDetection;
   
   private final boolean forwardWhenNoMatchingConsumers;

   public RemoteQueueBindingImpl(final SimpleString address,
                                 final SimpleString uniqueName,
                                 final SimpleString routingName,
                                 final SimpleString filterString,
                                 final Queue storeAndForwardQueue,
                                 final boolean duplicateDetection,
                                 final boolean forwardWhenNoMatchingConsumers) throws Exception
   {
      this.address = address;

      this.storeAndForwardQueue = storeAndForwardQueue;

      this.uniqueName = uniqueName;

      this.routingName = routingName;

      this.duplicateDetection = duplicateDetection;

      if (filterString != null)
      {
         queueFilter = new FilterImpl(filterString);
      }
      else
      {
         queueFilter = null;
      }
      
      this.forwardWhenNoMatchingConsumers = forwardWhenNoMatchingConsumers;
   }

   public SimpleString getAddress()
   {
      return address;
   }

   public Bindable getBindable()
   {
      return storeAndForwardQueue;
   }

   public SimpleString getRoutingName()
   {
      return routingName;
   }

   public SimpleString getUniqueName()
   {
      return uniqueName;
   }

   public boolean isExclusive()
   {
      return false;
   }

   public boolean isQueueBinding()
   {
      return false;
   }

   public boolean filterMatches(final ServerMessage message) throws Exception
   {
      if (queueFilter != null && !queueFilter.match(message))
      {
         return false;
      }
      else
      {
         return true;
      }
   }

   public boolean isHighAcceptPriority(final ServerMessage message)
   {
      if (forwardWhenNoMatchingConsumers)
      {
         return true;
      }
      
      if (consumerCount == 0)
      {
         return false;
      }

      if (filters.isEmpty())
      {
         return true;
      }
      else
      {
         for (Filter filter : filters)
         {
            if (filter.match(message))
            {
               return true;
            }
         }
      }

      return false;
   }

   public synchronized void addConsumer(final SimpleString filterString) throws Exception
   {
      if (filterString != null)
      {
         // There can actually be many consumers on the same queue with the same filter, so we need to maintain a ref
         // count

         Integer i = filterCounts.get(filterString);

         if (i == null)
         {
            filterCounts.put(filterString, 0);

            filters.add(new FilterImpl(filterString));
         }
         else
         {
            filterCounts.put(filterString, i + 1);
         }
      }

      consumerCount++;
   }

   public synchronized void removeConsumer(final SimpleString filterString) throws Exception
   {
      if (filterString != null)
      {
         Integer i = filterCounts.get(filterString);

         if (i != null)
         {
            int ii = i - 1;

            if (ii == 0)
            {
               filterCounts.remove(filterString);

               filters.remove(filterString);
            }
            else
            {
               filterCounts.put(filterString, ii);
            }
         }
      }

      consumerCount--;
   }

}