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

package org.jboss.messaging.core.paging.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.messaging.core.logging.Logger;
import org.jboss.messaging.core.paging.LastPageRecord;
import org.jboss.messaging.core.paging.PagedMessage;
import org.jboss.messaging.core.paging.PageTransactionInfo;
import org.jboss.messaging.core.paging.PagingManager;
import org.jboss.messaging.core.paging.PagingStore;
import org.jboss.messaging.core.paging.PagingStoreFactory;
import org.jboss.messaging.core.persistence.StorageManager;
import org.jboss.messaging.core.postoffice.PostOffice;
import org.jboss.messaging.core.server.MessageReference;
import org.jboss.messaging.core.server.ServerMessage;
import org.jboss.messaging.core.settings.HierarchicalRepository;
import org.jboss.messaging.core.settings.impl.QueueSettings;
import org.jboss.messaging.util.SimpleString;

/**
 *  <p>Look at the <a href="http://wiki.jboss.org/wiki/JBossMessaging2Paging">WIKI</a> for more information.</p>
 * 
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:andy.taylor@jboss.org>Andy Taylor</a>
 *
 */
public class PagingManagerImpl implements PagingManager
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private volatile boolean started = false;

   private final long maxGlobalSize;

   private final AtomicLong globalSize = new AtomicLong(0);

   private final AtomicBoolean globalMode = new AtomicBoolean(false);

   private final AtomicBoolean globalDepageRunning = new AtomicBoolean(false);

   private final ConcurrentMap<SimpleString, PagingStore> stores = new ConcurrentHashMap<SimpleString, PagingStore>();

   private final HierarchicalRepository<QueueSettings> queueSettingsRepository;

   private final PagingStoreFactory pagingSPI;

   private final StorageManager storageManager;

   private final long defaultPageSize;

   private PostOffice postOffice;

   private final ConcurrentMap</*TransactionID*/Long, PageTransactionInfo> transactions = new ConcurrentHashMap<Long, PageTransactionInfo>();

   // Static
   // --------------------------------------------------------------------------------------------------------------------------

   private static final Logger log = Logger.getLogger(PagingManagerImpl.class);

   // private static final boolean isTrace = log.isTraceEnabled();
   private static final boolean isTrace = true;

   // This is just a debug tool method.
   // During debugs you could make log.trace as log.info, and change the
   // variable isTrace above
   private static void trace(final String message)
   {
      // log.trace(message);
      log.info(message);
   }

   // Constructors
   // --------------------------------------------------------------------------------------------------------------------

   public PagingManagerImpl(final PagingStoreFactory pagingSPI,
                            final StorageManager storageManager,
                            final HierarchicalRepository<QueueSettings> queueSettingsRepository,
                            final long maxGlobalSize,
                            final long defaultPageSize)
   {
      this.pagingSPI = pagingSPI;
      this.queueSettingsRepository = queueSettingsRepository;
      this.storageManager = storageManager;
      this.defaultPageSize = defaultPageSize;
      this.maxGlobalSize = maxGlobalSize;
   }

   // Public
   // ---------------------------------------------------------------------------------------------------------------------------

   // PagingManager implementation
   // -----------------------------------------------------------------------------------------------------

   public boolean isGlobalPageMode()
   {
      return globalMode.get();
   }

   public void setGlobalPageMode(boolean globalMode)
   {
      this.globalMode.set(globalMode);
   }

   /**
    * @param destination
    * @return
    */
   public synchronized PagingStore createPageStore(final SimpleString storeName) throws Exception
   {
      PagingStore store = stores.get(storeName);

      if (store == null)
      {
         store = newStore(storeName);

         PagingStore oldStore = stores.putIfAbsent(storeName, store);

         if (oldStore != null)
         {
            store = oldStore;
         }
         else
         {
            store.start();
         }
      }

      return store;
   }

   public PagingStore getPageStore(final SimpleString storeName) throws Exception
   {
      PagingStore store = stores.get(storeName);

      if (store == null)
      {
         throw new IllegalStateException("Store " + storeName + " not found on paging");
      }

      return store;
   }

   /** this will be set by the postOffice itself.
    *  There is no way to set this on the constructor as the PagingManager is constructed before the postOffice.
    *  (There is a one-to-one relationship here) */
   public void setPostOffice(final PostOffice postOffice)
   {
      this.postOffice = postOffice;
   }

   public void clearLastPageRecord(final LastPageRecord lastRecord) throws Exception
   {
      trace("Clearing lastRecord information " + lastRecord.getLastId());
      
      storageManager.storeDelete(lastRecord.getRecordId());
   }

   /**
    * This method will remove files from the page system and and route them, doing it transactionally
    * 
    * A Transaction will be opened only if persistent messages are used.
    * 
    * If persistent messages are also used, it will update eventual PageTransactions
    */
   public boolean onDepage(final int pageId,
                           final SimpleString destination,
                           final PagingStore pagingStore,
                           final PagedMessage[] data) throws Exception
   {
      trace("Depaging....");

      // Depage has to be done atomically, in case of failure it should be
      // back to where it was
      final long depageTransactionID = storageManager.generateUniqueID();

      LastPageRecord lastPage = pagingStore.getLastRecord();

      if (lastPage == null)
      {
         lastPage = new LastPageRecordImpl(pageId, destination);
         pagingStore.setLastRecord(lastPage);
      }
      else
      {
         if (pageId <= lastPage.getLastId())
         {
            log.warn("Page " + pageId + " was already processed, ignoring the page");
            return true;
         }
      }

      lastPage.setLastId(pageId);
      
      storageManager.storeLastPage(depageTransactionID, lastPage);

      HashSet<PageTransactionInfo> pageTransactionsToUpdate = new HashSet<PageTransactionInfo>();

      final List<MessageReference> refsToAdd = new ArrayList<MessageReference>();

      for (PagedMessage msg : data)
      {
         ServerMessage pagedMessage = null;

         pagedMessage = (ServerMessage)msg.getMessage(storageManager);

         final long transactionIdDuringPaging = msg.getTransactionID();
         
         if (transactionIdDuringPaging >= 0)
         {
            final PageTransactionInfo pageTransactionInfo = transactions.get(transactionIdDuringPaging);

            // http://wiki.jboss.org/wiki/JBossMessaging2Paging
            // This is the Step D described on the "Transactions on Paging"
            // section
            if (pageTransactionInfo == null)
            {
               if (isTrace)
               {
                  trace("Transaction " + msg.getTransactionID() + " not found, ignoring message " + pagedMessage);
               }
               continue;
            }

            // This is to avoid a race condition where messages are depaged
            // before the commit arrived
            if (!pageTransactionInfo.waitCompletion())
            {
               trace("Rollback was called after prepare, ignoring message " + pagedMessage);
               continue;
            }

            // Update information about transactions
            if (pagedMessage.isDurable())
            {
               pageTransactionInfo.decrement();
               pageTransactionsToUpdate.add(pageTransactionInfo);
            }
         }

         refsToAdd.addAll(postOffice.route(pagedMessage));

         if (pagedMessage.getDurableRefCount() != 0)
         {
            storageManager.storeMessageTransactional(depageTransactionID, pagedMessage);
         }
      }

      for (PageTransactionInfo pageWithTransaction : pageTransactionsToUpdate)
      {
         if (pageWithTransaction.getNumberOfMessages() == 0)
         {
            // http://wiki.jboss.org/wiki/JBossMessaging2Paging
            // numberOfReads==numberOfWrites -> We delete the record
            storageManager.storeDeletePageTransaction(depageTransactionID, pageWithTransaction.getRecordID());
            transactions.remove(pageWithTransaction.getTransactionID());
         }
         else
         {
            storageManager.storePageTransaction(depageTransactionID, pageWithTransaction);
         }
      }

      storageManager.commit(depageTransactionID);

      trace("Depage committed");

      for (MessageReference ref : refsToAdd)
      {
         ref.getQueue().addLast(ref);
      }

      if (globalMode.get())
      {
         // We use the Default Page Size when in global mode for the calculation of the Watermark
         return globalSize.get() < maxGlobalSize - defaultPageSize && pagingStore.getMaxSizeBytes() <= 0 ||
                pagingStore.getAddressSize() < pagingStore.getMaxSizeBytes();
      }
      else
      {
         // If Max-size is not configured (-1) it will aways return true, as
         // this method was probably called by global-depage
         return pagingStore.getMaxSizeBytes() <= 0 || pagingStore.getAddressSize() < pagingStore.getMaxSizeBytes();
      }

   }

   public long getDefaultPageSize()
   {
      return defaultPageSize;
   }

   public void setLastPage(final LastPageRecord lastPage) throws Exception
   {
      trace("LastPage loaded was " + lastPage.getLastId() + " recordID = " + lastPage.getRecordId());
      
      getPageStore(lastPage.getDestination()).setLastRecord(lastPage);
   }

   public boolean isPaging(final SimpleString destination) throws Exception
   {
      return getPageStore(destination).isPaging();
   }

   public void messageDone(final ServerMessage message) throws Exception
   {
      addSize(message.getDestination(), message.getMemoryEstimate() * -1);
   }

   public long addSize(final ServerMessage message) throws Exception
   {
      return addSize(message.getDestination(), message.getMemoryEstimate());
   }

   public boolean page(final ServerMessage message, final long transactionId) throws Exception
   {
      return getPageStore(message.getDestination()).page(new PagedMessageImpl(message, transactionId));
   }

   public boolean page(final ServerMessage message) throws Exception
   {
      return getPageStore(message.getDestination()).page(new PagedMessageImpl(message));
   }

   public void addTransaction(final PageTransactionInfo pageTransaction)
   {
      transactions.put(pageTransaction.getTransactionID(), pageTransaction);
   }

   public void sync(final Collection<SimpleString> destinationsToSync) throws Exception
   {
      for (SimpleString destination : destinationsToSync)
      {
         getPageStore(destination).sync();
      }
   }

   // MessagingComponent implementation
   // ------------------------------------------------------------------------------------------------

   public boolean isStarted()
   {
      return started;
   }

   public synchronized void start() throws Exception
   {
      if (started)
      {
         return;
      }
      
      started = true;
   }

   public synchronized void stop() throws Exception
   {
      if (!started)
      {
         return;
      }
      
      started = false;

      pagingSPI.stop();

      for (PagingStore store : stores.values())
      {
         store.stop();
      }
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   private PagingStore newStore(final SimpleString destinationName)
   {
      return pagingSPI.newStore(destinationName, queueSettingsRepository.getMatch(destinationName.toString()));
   }

   private long addSize(final SimpleString destination, final long size) throws Exception
   {
      final PagingStore store = getPageStore(destination);

      final long maxSize = store.getMaxSizeBytes();

      final long pageSize = store.getPageSizeBytes();

      if (store.isDropWhenMaxSize() && size > 0)
      {
         // if destination configured to drop messages && size is over the
         // limit, we return -1 which means drop the message
         if (store.getAddressSize() + size > maxSize || maxGlobalSize > 0 && globalSize.get() + size > maxGlobalSize)
         {
            if (!store.isDroppedMessage())
            {
               store.setDroppedMessage(true);
               
               log.warn("Messages are being dropped on adress " + store.getStoreName());
            }

            return -1l;
         }
         else
         {
            return store.addAddressSize(size);
         }
      }
      else
      {
         long currentGlobalSize = globalSize.addAndGet(size);

         final long addressSize = store.addAddressSize(size);

         if (size > 0)
         {
            if (maxGlobalSize > 0 && currentGlobalSize > maxGlobalSize)
            {
               globalMode.set(true);
               if (store.startPaging())
               {
                  if (isTrace)
                  {
                     trace("Starting paging on " + destination + ", size = " + addressSize + ", maxSize=" + maxSize);
                  }
               }
            }
            else if (maxSize > 0 && addressSize > maxSize)
            {
               if (store.startPaging())
               {
                  if (isTrace)
                  {
                     trace("Starting paging on " + destination + ", size = " + addressSize + ", maxSize=" + maxSize);
                  }
               }
            }
         }
         else
         {
            // When in Global mode, we use the default page size as the minimal
            // watermark to start depage

            if (isTrace)
            {
               log.trace("globalMode.get = " + globalMode.get() +
                         " currentGlobalSize = " +
                         currentGlobalSize +
                         " defaultPageSize = " +
                         defaultPageSize +
                         " maxGlobalSize = " +
                         maxGlobalSize +
                         "maxGlobalSize - defaultPageSize = " +
                         (maxGlobalSize - defaultPageSize));
            }

            if (globalMode.get() && currentGlobalSize < maxGlobalSize - defaultPageSize)
            {
               startGlobalDepage();
            }
            else if (maxSize > 0 && addressSize < maxSize - pageSize)
            {
               if (store.startDepaging())
               {
                  log.info("Starting depaging Thread, size = " + addressSize);
               }
            }
         }

         return addressSize;
      }
   }

   private void startGlobalDepage()
   {
      if (globalDepageRunning.compareAndSet(false, true))
      {
         Runnable globalDepageRunnable = new GlobalDepager();
         pagingSPI.getPagingExecutor().execute(globalDepageRunnable);
      }
   }

   // Inner classes -------------------------------------------------

   private class GlobalDepager implements Runnable
   {
      public void run()
      {
         try
         {
            while (globalSize.get() < maxGlobalSize && started)
            {
               boolean depaged = false;
               // Round robin depaging one page at the time from each
               // destination
               for (PagingStore store : stores.values())
               {
                  if (globalSize.get() < maxGlobalSize)
                  {
                     if (store.isPaging())
                     {
                        depaged = true;
                        try
                        {
                           store.readPage();
                        }
                        catch (Exception e)
                        {
                           log.error(e.getMessage(), e);
                        }
                     }
                  }
               }
               if (!depaged)
               {
                  break;
               }
            }

            if (globalSize.get() < maxGlobalSize && started)
            {
               globalMode.set(false);
               // Clearing possible messages still in page-mode
               for (PagingStore store : stores.values())
               {
                  store.startDepaging();
               }
            }
         }
         finally
         {
            globalDepageRunning.set(false);
         }
      }
   }
}
