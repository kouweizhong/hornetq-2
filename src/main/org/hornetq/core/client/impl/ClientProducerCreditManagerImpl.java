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

package org.hornetq.core.client.impl;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hornetq.api.core.SimpleString;
import org.hornetq.core.logging.Logger;

/**
 * A ProducerCreditManager
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 *
 */
public class ClientProducerCreditManagerImpl implements ClientProducerCreditManager
{
   private static final Logger log = Logger.getLogger(ClientProducerCreditManagerImpl.class);

   public static final int MAX_UNREFERENCED_CREDITS_CACHE_SIZE = 1000;

   private final Map<SimpleString, ClientProducerCredits> producerCredits = new LinkedHashMap<SimpleString, ClientProducerCredits>();

   private final Map<SimpleString, ClientProducerCredits> unReferencedCredits = new LinkedHashMap<SimpleString, ClientProducerCredits>();

   private final ClientSessionInternal session;

   private final int windowSize;

   public ClientProducerCreditManagerImpl(final ClientSessionInternal session, final int windowSize)
   {
      this.session = session;

      this.windowSize = windowSize;
   }

   public synchronized ClientProducerCredits getCredits(final SimpleString address, final boolean anon)
   {
      ClientProducerCredits credits = producerCredits.get(address);

      if (credits == null)
      {
         // Doesn't need to be fair since session is single threaded
         credits = new ClientProducerCreditsImpl(session, address, windowSize);

         producerCredits.put(address, credits);
      }

      if (!anon)
      {
         credits.incrementRefCount();

         // Remove from anon credits (if there)
         unReferencedCredits.remove(address);
      }
      else
      {
         addToUnReferencedCache(address, credits);
      }

      return credits;
   }

   public synchronized void returnCredits(final SimpleString address)
   {
      ClientProducerCredits credits = producerCredits.get(address);

      if (credits != null && credits.decrementRefCount() == 0)
      {
         addToUnReferencedCache(address, credits);
      }
   }

   public synchronized void receiveCredits(final SimpleString address, final int credits)
   {
      ClientProducerCredits cr = producerCredits.get(address);

      if (cr != null)
      {
         cr.receiveCredits(credits);
      }
   }

   public synchronized void reset()
   {
      for (ClientProducerCredits credits : producerCredits.values())
      {
         credits.reset();
      }
   }

   public synchronized void close()
   {
      for (ClientProducerCredits credits : producerCredits.values())
      {
         credits.close();
      }

      producerCredits.clear();
      
      unReferencedCredits.clear();
   }
   
   public synchronized int creditsMapSize()
   {
      return producerCredits.size();
   }
   
   public synchronized int unReferencedCreditsSize()
   {
      return unReferencedCredits.size();
   }

   private void addToUnReferencedCache(final SimpleString address, final ClientProducerCredits credits)
   {
      unReferencedCredits.put(address, credits);

      if (unReferencedCredits.size() > MAX_UNREFERENCED_CREDITS_CACHE_SIZE)
      {
         // Remove the oldest entry

         Iterator<Map.Entry<SimpleString, ClientProducerCredits>> iter = unReferencedCredits.entrySet().iterator();

         Map.Entry<SimpleString, ClientProducerCredits> oldest = iter.next();

         iter.remove();

         removeEntry(oldest.getKey(), oldest.getValue());                 
      }
   }

   private void removeEntry(final SimpleString address, final ClientProducerCredits credits)
   {
      producerCredits.remove(address);

      credits.releaseOutstanding();

      credits.close();
   }

}
