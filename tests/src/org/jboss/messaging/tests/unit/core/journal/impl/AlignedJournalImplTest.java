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


package org.jboss.messaging.tests.unit.core.journal.impl;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.jboss.messaging.core.journal.EncodingSupport;
import org.jboss.messaging.core.journal.PreparedTransactionInfo;
import org.jboss.messaging.core.journal.RecordInfo;
import org.jboss.messaging.core.journal.SequentialFile;
import org.jboss.messaging.core.journal.SequentialFileFactory;
import org.jboss.messaging.core.journal.impl.JournalImpl;
import org.jboss.messaging.core.logging.Logger;
import org.jboss.messaging.tests.unit.core.journal.impl.fakes.FakeSequentialFileFactory;
import org.jboss.messaging.tests.unit.core.journal.impl.fakes.SimpleEncoding;
import org.jboss.messaging.tests.util.UnitTestCase;
import org.jboss.messaging.util.MessagingBuffer;

/**
 * 
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 *
 */
public class AlignedJournalImplTest extends UnitTestCase
{

   // Constants -----------------------------------------------------
   
   // Attributes ----------------------------------------------------
   
   private int alignment = 0;
   
   private SequentialFileFactory factory;

   JournalImpl journalImpl = null;
   
   private ArrayList<RecordInfo> records = null;

   private ArrayList<PreparedTransactionInfo> transactions = null;
   
   
   // Static --------------------------------------------------------
   
   private static final Logger log = Logger.getLogger(AlignedJournalImplTest.class);
   
   // Constructors --------------------------------------------------
   
   // Public --------------------------------------------------------
   
   // This test just validates basic alignment on the FakeSequentialFile itself
   public void testBasicAlignment() throws Exception
   {
      
      FakeSequentialFileFactory factory = new FakeSequentialFileFactory(200,
            true, false);
      
      SequentialFile file = factory.createSequentialFile("test1", 100, 10000);

      file.open();

      
      
      try
      {
         ByteBuffer buffer = ByteBuffer.allocateDirect(57);
         file.write(buffer, true);
         fail("Exception expected");
      }
      catch (Exception ignored)
      {
      }
      
      try
      {
         ByteBuffer buffer = ByteBuffer.allocateDirect(200);
         for (int i = 0; i < 200; i++)
         {
            buffer.put(i, (byte) 1);
         }
         
         file.write(buffer, true);
         
         buffer = ByteBuffer.allocate(400);
         for (int i = 0; i < 400; i++)
         {
            buffer.put(i, (byte) 2);
         }
         
         file.write(buffer, true);
         
         buffer = ByteBuffer.allocate(600);

         file.position(0);
         
         file.read(buffer);
         
         for (int i = 0; i < 200; i++)
         {
            assertEquals((byte)1, buffer.get(i));
         }
         
         for (int i = 201; i < 600; i++)
         {
            assertEquals("Position " + i, (byte)2, buffer.get(i));
         }
         
      }
      catch (Exception ignored)
      {
      }
   }
   
   public void testAppendAndUpdateRecords() throws Exception
   {
      
      final int JOURNAL_SIZE = 10000;
      
      setupJournal(JOURNAL_SIZE, 100);
      
      assertEquals(0, records.size());
      assertEquals(0, transactions.size());

      for (int i = 0; i < 25; i++)
      {
         byte[] bytes = new byte[5];
         for (int j=0; j<bytes.length; j++)
         {
            bytes[j] = (byte)i;
         }
         journalImpl.appendAddRecord(i * 100l, (byte)i, bytes);
      }
      
      for (int i = 25; i < 50; i++)
      {
         EncodingSupport support = new SimpleEncoding(5, (byte) i);
         journalImpl.appendAddRecord(i * 100l, (byte)i, support);
      }
      
      setupJournal(JOURNAL_SIZE, 1024);
      
      assertEquals(50, records.size());
      
      int i=0;
      for (RecordInfo recordItem: records)
      {
         assertEquals(i * 100l, recordItem.id);
         assertEquals(i, recordItem.getUserRecordType());
         assertEquals(5, recordItem.data.length);
         for (int j=0;j<5;j++)
         {
            assertEquals((byte)i, recordItem.data[j]);
         }
         
         i++;
      }
      
      for (i = 40; i < 50; i++)
      {
         byte[] bytes = new byte[10];
         for (int j = 0; j < 10; j++)
         {
            bytes[j] = (byte)'x';
         }
         
         journalImpl.appendUpdateRecord(i * 100l, (byte)i, bytes);
      }
      
      setupJournal(JOURNAL_SIZE, 1024);
      
      i=0;
      for (RecordInfo recordItem: records)
      {
         
         if (i < 50)
         {
            assertEquals(i * 100l, recordItem.id);
            assertEquals(i, recordItem.getUserRecordType());
            assertEquals(5, recordItem.data.length);
            for (int j=0;j<5;j++)
            {
               assertEquals((byte)i, recordItem.data[j]);
            }
         }
         else
         {
            assertEquals((i - 10) * 100l, recordItem.id);
            assertEquals(i - 10, recordItem.getUserRecordType());
            assertTrue(recordItem.isUpdate);
            assertEquals(10, recordItem.data.length);
            for (int j=0;j<10;j++)
            {
               assertEquals((byte)'x', recordItem.data[j]);
            }
         }
         
         i++;
      }
      
      journalImpl.stop();
      
   }
   
   public void testPartialDelete() throws Exception
   {
      final int JOURNAL_SIZE = 10000;
      
      setupJournal(JOURNAL_SIZE, 100);
      
      journalImpl.disableAutoReclaiming();
      
      journalImpl.checkAndReclaimFiles();
      
      journalImpl.debugWait();
      
      assertEquals(2, factory.listFiles("tt").size());
      
      log.debug("Initial:--> " + journalImpl.debug());
      
      log.debug("_______________________________");
      
      for (int i = 0; i < 50; i++)
      {
         journalImpl.appendAddRecord((long)i, (byte)1, new SimpleEncoding(1, (byte) 'x'));
      }
      
      journalImpl.forceMoveNextFile();
   
      // as the request to a new file is asynchronous, we need to make sure the async requests are done
      journalImpl.debugWait();
      
      assertEquals(3, factory.listFiles("tt").size());
      
      for (int i = 10; i < 50; i++)
      {
         journalImpl.appendDeleteRecord((long)i);
      }
      
      journalImpl.debugWait();
      
      setupJournal(JOURNAL_SIZE, 100);
      
      assertEquals(10, this.records.size());
      
      assertEquals(3, factory.listFiles("tt").size());

   }

   public void testAddAndDeleteReclaimWithoutTransactions() throws Exception
   {
      final int JOURNAL_SIZE = 10000;
      
      setupJournal(JOURNAL_SIZE, 100);
      
      journalImpl.disableAutoReclaiming();
      
      journalImpl.checkAndReclaimFiles();
      
      journalImpl.debugWait();
      
      assertEquals(2, factory.listFiles("tt").size());
      
      log.debug("Initial:--> " + journalImpl.debug());
      
      log.debug("_______________________________");
      
      for (int i = 0; i < 50; i++)
      {
         journalImpl.appendAddRecord((long)i, (byte)1, new SimpleEncoding(1, (byte) 'x'));
      }
   
      // as the request to a new file is asynchronous, we need to make sure the async requests are done
      journalImpl.debugWait();
      
      assertEquals(2, factory.listFiles("tt").size());
      
      for (int i = 0; i < 50; i++)
      {
         journalImpl.appendDeleteRecord((long)i);
      }
      
      journalImpl.forceMoveNextFile();
      
      journalImpl.appendAddRecord((long)1000, (byte)1, new SimpleEncoding(1, (byte) 'x'));
      
      journalImpl.debugWait();
      
      assertEquals(4, factory.listFiles("tt").size());


      setupJournal(JOURNAL_SIZE, 100);
      
      assertEquals(1, records.size());
      
      assertEquals(1000, records.get(0).id);
      
      journalImpl.checkAndReclaimFiles();
      
      log.debug(journalImpl.debug());
      
      journalImpl.debugWait();
      
      log.debug("Final:--> " + journalImpl.debug());
      
      log.debug("_______________________________");

      log.debug("Files size:" + factory.listFiles("tt").size());
      
      assertEquals(2, factory.listFiles("tt").size());

   }

   public void testReloadWithTransaction() throws Exception
   {
      final int JOURNAL_SIZE = 2000;
      
      setupJournal(JOURNAL_SIZE, 100);
      
      assertEquals(0, records.size());
      assertEquals(0, transactions.size());
      
      journalImpl.appendAddRecordTransactional(1, 1, (byte) 1, new SimpleEncoding(1,(byte) 1));
      
      setupJournal(JOURNAL_SIZE, 100);
      
      assertEquals(0, records.size());
      assertEquals(0, transactions.size());

      try
      {
         journalImpl.appendCommitRecord(1l);
         // This was supposed to throw an exception, as the transaction was forgotten (interrupted by a reload).
         fail("Supposed to throw exception");
      }
      catch (Exception e)
      {
         log.warn(e);
      }

      setupJournal(JOURNAL_SIZE, 100);
      
      assertEquals(0, records.size());
      assertEquals(0, transactions.size());
      
   }
   
   public void testReloadWithInterruptedTransaction() throws Exception
   {
      final int JOURNAL_SIZE = 1100;
      
      setupJournal(JOURNAL_SIZE, 100);
      
      journalImpl.disableAutoReclaiming();
      
      assertEquals(0, records.size());
      assertEquals(0, transactions.size());
      
      for (int i = 0; i < 10; i++)
      {
         journalImpl.appendAddRecordTransactional(77l, 1, (byte) 1, new SimpleEncoding(1,(byte) 1));
         journalImpl.forceMoveNextFile();
      }
      
      journalImpl.debugWait();
      
      assertEquals(12, factory.listFiles("tt").size());
      
      journalImpl.appendAddRecordTransactional(78l, 1, (byte) 1, new SimpleEncoding(1,(byte) 1));

      assertEquals(12, factory.listFiles("tt").size());
      
      setupJournal(JOURNAL_SIZE, 100);
      
      assertEquals(0, records.size());
      assertEquals(0, transactions.size());

      try
      {
         journalImpl.appendCommitRecord(77l);
         // This was supposed to throw an exception, as the transaction was forgotten (interrupted by a reload).
         fail("Supposed to throw exception");
      }
      catch (Exception e)
      {
         log.debug("Expected exception " + e, e);
      }

      setupJournal(JOURNAL_SIZE, 100);
      
      assertEquals(0, records.size());
      assertEquals(0, transactions.size());
      
      assertEquals(12, factory.listFiles("tt").size());

      journalImpl.checkAndReclaimFiles();
      
      System.out.println("Journal: " + journalImpl.debug());
      
      assertEquals(2, factory.listFiles("tt").size());
      
   }
   
   public void testReloadWithCompletedTransaction() throws Exception
   {
      final int JOURNAL_SIZE = 2000;
      
      setupJournal(JOURNAL_SIZE, 100);
      
      assertEquals(0, records.size());
      assertEquals(0, transactions.size());
      
      for (int i = 0; i < 10; i++)
      {
         journalImpl.appendAddRecordTransactional(1, 1, (byte) 1, new SimpleEncoding(1,(byte) 1));
         journalImpl.forceMoveNextFile();
      }
      
      journalImpl.appendCommitRecord(1l);

      journalImpl.debugWait();

      assertEquals(12, factory.listFiles("tt").size());

      setupJournal(JOURNAL_SIZE, 100);

      assertEquals(10, records.size());
      assertEquals(0, transactions.size());
      
      journalImpl.checkAndReclaimFiles();
      
      assertEquals(10, journalImpl.getDataFilesCount());
      
      assertEquals(12, factory.listFiles("tt").size());
      
      for (int i = 0; i < 10; i++)
      {
         journalImpl.appendDeleteRecordTransactional(2l, (long)i);
         journalImpl.forceMoveNextFile();
      }
      
      journalImpl.appendCommitRecord(2l);
      
      journalImpl.appendAddRecord(100, (byte)1, new SimpleEncoding(5, (byte)1));
      
      journalImpl.forceMoveNextFile();
      
      journalImpl.appendAddRecord(101, (byte)1, new SimpleEncoding(5, (byte)1));
      
      journalImpl.checkAndReclaimFiles();
      
      assertEquals(1, journalImpl.getDataFilesCount());
      
      setupJournal(JOURNAL_SIZE, 100);
      
      assertEquals(1, journalImpl.getDataFilesCount());
      
      assertEquals(3, factory.listFiles("tt").size());
   }
   
   
   
   public void testTotalSize() throws Exception
   {
      final int JOURNAL_SIZE = 2000;
      
      setupJournal(JOURNAL_SIZE, 100);
      
      assertEquals(0, records.size());
      assertEquals(0, transactions.size());
      
      journalImpl.appendAddRecordTransactional(1l, 2l, (byte)3, new SimpleEncoding(1900 - JournalImpl.SIZE_ADD_RECORD_TX, (byte)4));
      
      journalImpl.appendCommitRecord(1l);
      
      journalImpl.debugWait();
      
      setupJournal(JOURNAL_SIZE, 100);
      
      assertEquals(1, records.size());
      
   }
   
   
   public void testReloadInvalidVariableSize() throws Exception
   {
      // Test to be written
   }
   
   public void testReloadIncompleteTransaction() throws Exception
   {
      // We should miss one record (hole) on the transaction
   }
   
   public void testAsynchronousCommit() throws Exception
   {
      // We should miss one record (hole) on the transaction
   }
   
   public void testAsynchronousRollback() throws Exception
   {
      // We should miss one record (hole) on the transaction
   }
   
   public void testGarbageBetweenRecords() throws Exception
   {
      // We should miss one record (hole) on the transaction
   }
   
   
   
   
   public void testReloadWithPreparedTransaction() throws Exception
   {
      final int JOURNAL_SIZE = 3 * 1024;
      
      setupJournal(JOURNAL_SIZE, 1);
      
      assertEquals(0, records.size());
      assertEquals(0, transactions.size());
      
      for (int i = 0; i < 10; i++)
      {
         journalImpl.appendAddRecordTransactional(1, 1, (byte) 1, new SimpleEncoding(50,(byte) 1));
         journalImpl.forceMoveNextFile();
      }
      
      journalImpl.debugWait();
      
      //System.out.println("files = " + journalImpl.debug());
      
      journalImpl.appendPrepareRecord(1l);

      assertEquals(12, factory.listFiles("tt").size());

      setupJournal(JOURNAL_SIZE, 1024);

      assertEquals(0, records.size());
      assertEquals(1, transactions.size());
      
      journalImpl.checkAndReclaimFiles();
      
      assertEquals(10, journalImpl.getDataFilesCount());
      
      assertEquals(12, factory.listFiles("tt").size());
      
      journalImpl.appendCommitRecord(1l);
      
      setupJournal(JOURNAL_SIZE, 1024);

      assertEquals(10, records.size());
      
      journalImpl.checkAndReclaimFiles();
      
      for (int i = 0; i < 10; i++)
      {
         journalImpl.appendDeleteRecordTransactional(2l, (long)i);
      }
      
      journalImpl.appendPrepareRecord(2l);
      
      setupJournal(JOURNAL_SIZE, 1);
      
      assertEquals(10, journalImpl.getDataFilesCount());

      assertEquals(12, factory.listFiles("tt").size());
      
      journalImpl.appendCommitRecord(2l);
      
      setupJournal(JOURNAL_SIZE, 1);
      
      assertEquals(0, records.size());
      assertEquals(0, transactions.size());

      journalImpl.forceMoveNextFile();
      journalImpl.checkAndReclaimFiles();
      
      assertEquals(2, factory.listFiles("tt").size());

      
   }
   
   
   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
      super.setUp();
      
      records = new ArrayList<RecordInfo>();

      transactions = new ArrayList<PreparedTransactionInfo>();
      
      factory = null;
      
      journalImpl = null;
      
   }

   @Override
   protected void tearDown() throws Exception
   {
      super.tearDown();
      
      if (journalImpl != null)
      {
         try
         {
            journalImpl.stop();
         }
         catch (Throwable ignored)
         {
         }
      }
   }
   
  
   // Private -------------------------------------------------------

   private void setupJournal(final int journalSize, final int alignment) throws Exception
   {
      if (factory == null)
      {
         this.alignment = alignment;
         
         factory = new FakeSequentialFileFactory(alignment,
               true, false);
      }
      
      if (journalImpl != null)
      {
         journalImpl.stop();
      }
      
      journalImpl = new JournalImpl(journalSize, 2,
            true, true,
            factory, 
            "tt", "tt", 1000, 10000);
      
      journalImpl.start();
      
      records.clear();
      transactions.clear();
      
      journalImpl.load(records, transactions);
   }
   

   // Inner classes -------------------------------------------------
   
   
}
