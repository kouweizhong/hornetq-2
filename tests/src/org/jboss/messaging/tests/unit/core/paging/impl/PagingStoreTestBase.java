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

package org.jboss.messaging.tests.unit.core.paging.impl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.easymock.classextension.EasyMock;
import org.jboss.messaging.core.config.impl.ConfigurationImpl;
import org.jboss.messaging.core.journal.SequentialFile;
import org.jboss.messaging.core.journal.SequentialFileFactory;
import org.jboss.messaging.core.paging.Page;
import org.jboss.messaging.core.paging.PagedMessage;
import org.jboss.messaging.core.paging.PagingManager;
import org.jboss.messaging.core.paging.impl.PagedMessageImpl;
import org.jboss.messaging.core.paging.impl.PagingStoreImpl;
import org.jboss.messaging.core.paging.impl.TestSupportPageStore;
import org.jboss.messaging.core.persistence.StorageManager;
import org.jboss.messaging.core.postoffice.PostOffice;
import org.jboss.messaging.core.remoting.impl.ByteBufferWrapper;
import org.jboss.messaging.core.server.ServerMessage;
import org.jboss.messaging.core.server.impl.ServerMessageImpl;
import org.jboss.messaging.core.settings.impl.QueueSettings;
import org.jboss.messaging.tests.util.RandomUtil;
import org.jboss.messaging.tests.util.UnitTestCase;
import org.jboss.messaging.util.SimpleString;

/**
 * 
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 *
 */
public abstract class PagingStoreTestBase extends UnitTestCase
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------
   protected ExecutorService executor;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();
      executor = Executors.newSingleThreadExecutor();
   }

   @Override
   protected void tearDown() throws Exception
   {
      super.tearDown();
      executor.shutdown();
   }

   protected void testConcurrentPaging(final SequentialFileFactory factory, final int numberOfThreads) throws Exception,
                                                                                                      InterruptedException
   {

      final int MAX_SIZE = 1024 * 10;

      final AtomicLong messageIdGenerator = new AtomicLong(0);

      final AtomicInteger aliveProducers = new AtomicInteger(numberOfThreads);

      final CountDownLatch latchStart = new CountDownLatch(numberOfThreads);

      final ConcurrentHashMap<Long, PagedMessageImpl> buffers = new ConcurrentHashMap<Long, PagedMessageImpl>();

      final ArrayList<Page> readPages = new ArrayList<Page>();

      QueueSettings settings = new QueueSettings();
      settings.setPageSizeBytes(MAX_SIZE);

      final TestSupportPageStore storeImpl = new PagingStoreImpl(createMockManager(),
                                                                 createStorageManagerMock(),
                                                                 createPostOfficeMock(),
                                                                 factory,
                                                                 new SimpleString("test"),
                                                                 settings,
                                                                 executor, true);

      storeImpl.start();

      assertEquals(0, storeImpl.getNumberOfPages());

      storeImpl.startPaging();

      assertEquals(1, storeImpl.getNumberOfPages());

      final SimpleString destination = new SimpleString("test");

      class ProducerThread extends Thread
      {

         Exception e;

         @Override
         public void run()
         {

            try
            {
               boolean firstTime = true;
               while (true)
               {
                  long id = messageIdGenerator.incrementAndGet();
                  PagedMessageImpl msg = createMessage(destination, createRandomBuffer(id, 5));
                  if (storeImpl.page(msg, false, true))
                  {
                     buffers.put(id, msg);
                  }
                  else
                  {
                     break;
                  }

                  if (firstTime)
                  {
                     latchStart.countDown();
                     firstTime = false;
                  }
               }
            }
            catch (Exception e)
            {
               e.printStackTrace();
               this.e = e;
            }
            finally
            {
               aliveProducers.decrementAndGet();
            }
         }
      }

      class ConsumerThread extends Thread
      {
         Exception e;

         @Override
         public void run()
         {
            try
            {
               // Wait every producer to produce at least one message
               latchStart.await();
               while (aliveProducers.get() > 0)
               {
                  Page page = storeImpl.depage();
                  if (page != null)
                  {
                     readPages.add(page);
                  }
               }
            }
            catch (Exception e)
            {
               e.printStackTrace();
               this.e = e;
            }
         }
      }

      ProducerThread producerThread[] = new ProducerThread[numberOfThreads];

      for (int i = 0; i < numberOfThreads; i++)
      {
         producerThread[i] = new ProducerThread();
         producerThread[i].start();
      }

      ConsumerThread consumer = new ConsumerThread();
      consumer.start();

      for (int i = 0; i < numberOfThreads; i++)
      {
         producerThread[i].join();
         if (producerThread[i].e != null)
         {
            throw producerThread[i].e;
         }
      }

      consumer.join();

      if (consumer.e != null)
      {
         throw consumer.e;
      }

      final ConcurrentHashMap<Long, PagedMessage> buffers2 = new ConcurrentHashMap<Long, PagedMessage>();

      for (Page page : readPages)
      {
         page.open();
         List<PagedMessage> msgs = page.read();
         page.close();

         for (PagedMessage msg : msgs)
         {
            msg.getMessage(null).getBody().rewind();
            long id = msg.getMessage(null).getBody().getLong();
            msg.getMessage(null).getBody().rewind();

            PagedMessageImpl msgWritten = buffers.remove(id);
            buffers2.put(id, msg);
            assertNotNull(msgWritten);
            assertEquals(msg.getMessage(null).getDestination(), msgWritten.getMessage(null).getDestination());
            assertEqualsByteArrays(msgWritten.getMessage(null).getBody().array(), msg.getMessage(null)
                                                                                     .getBody()
                                                                                     .array());
         }
      }

      assertEquals(0, buffers.size());

      List<String> files = factory.listFiles("page");

      assertTrue(files.size() != 0);

      for (String file : files)
      {
         SequentialFile fileTmp = factory.createSequentialFile(file, 1);
         fileTmp.open();
         assertTrue(fileTmp.size() + " <= " + MAX_SIZE, fileTmp.size() <= MAX_SIZE);
         fileTmp.close();
      }

      TestSupportPageStore storeImpl2 = new PagingStoreImpl(createMockManager(),
                                                            createStorageManagerMock(),
                                                            createPostOfficeMock(),
                                                            factory,
                                                            new SimpleString("test"),
                                                            settings,
                                                            executor, true);
      storeImpl2.start();

      int numberOfPages = storeImpl2.getNumberOfPages();
      assertTrue(numberOfPages != 0);

      storeImpl2.startPaging();

      storeImpl2.startPaging();

      assertEquals(numberOfPages, storeImpl2.getNumberOfPages());

      long lastMessageId = messageIdGenerator.incrementAndGet();
      PagedMessage lastMsg = createMessage(destination, createRandomBuffer(lastMessageId, 5));

      storeImpl2.page(lastMsg, false, true);
      buffers2.put(lastMessageId, lastMsg);

      Page lastPage = null;
      while (true)
      {
         Page page = storeImpl2.depage();
         if (page == null)
         {
            break;
         }

         lastPage = page;

         page.open();

         List<PagedMessage> msgs = page.read();

         page.close();

         for (PagedMessage msg : msgs)
         {

            msg.getMessage(null).getBody().rewind();
            long id = msg.getMessage(null).getBody().getLong();
            PagedMessage msgWritten = buffers2.remove(id);
            assertNotNull(msgWritten);
            assertEquals(msg.getMessage(null).getDestination(), msgWritten.getMessage(null).getDestination());
            assertEqualsByteArrays(msgWritten.getMessage(null).getBody().array(), msg.getMessage(null)
                                                                                     .getBody()
                                                                                     .array());
         }
      }

      lastPage.open();
      List<PagedMessage> lastMessages = lastPage.read();
      lastPage.close();
      assertEquals(1, lastMessages.size());

      lastMessages.get(0).getMessage(null).getBody().rewind();
      assertEquals(lastMessages.get(0).getMessage(null).getBody().getLong(), lastMessageId);
      assertEqualsByteArrays(lastMessages.get(0).getMessage(null).getBody().array(), lastMsg.getMessage(null)
                                                                                            .getBody()
                                                                                            .array());

      assertEquals(0, buffers2.size());

      assertEquals(0, storeImpl.getAddressSize());

   }

   protected PagedMessageImpl createMessage(final SimpleString destination, final ByteBuffer buffer)
   {
      ServerMessage msg = new ServerMessageImpl((byte)1,
                                                true,
                                                0,
                                                System.currentTimeMillis(),
                                                (byte)0,
                                                new ByteBufferWrapper(buffer));

      msg.setDestination(destination);
      return new PagedMessageImpl(msg);
   }

   protected ByteBuffer createRandomBuffer(final long id, final int size)
   {
      ByteBuffer buffer = ByteBuffer.allocate(size + 8);

      buffer.putLong(id);

      for (int j = 8; j < buffer.limit(); j++)
      {
         buffer.put(RandomUtil.randomByte());
      }
      return buffer;
   }

   /**
    * @return
    */
   protected PagingManager createMockManager()
   {
      PagingManager mockManager = EasyMock.createNiceMock(PagingManager.class);
      org.easymock.EasyMock.expect(mockManager.getDefaultPageSize()).andStubReturn(ConfigurationImpl.DEFAULT_PAGE_SIZE);
      EasyMock.replay(mockManager);
      return mockManager;
   }

   protected StorageManager createStorageManagerMock()
   {
      StorageManager storageManager = EasyMock.createNiceMock(StorageManager.class);
      EasyMock.replay(storageManager);
      return storageManager;
   }

   protected PostOffice createPostOfficeMock()
   {
      PostOffice postOffice = EasyMock.createNiceMock(PostOffice.class);
      EasyMock.replay(postOffice);
      return postOffice;
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

}
