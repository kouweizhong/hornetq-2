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

package org.hornetq.tests.unit.core.journal.impl.fakes;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hornetq.core.asyncio.BufferCallback;
import org.hornetq.core.journal.IOCallback;
import org.hornetq.core.journal.SequentialFile;
import org.hornetq.core.journal.SequentialFileFactory;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.remoting.spi.HornetQBuffer;

/**
 * 
 * A FakeSequentialFileFactory
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 *
 */
public class FakeSequentialFileFactory implements SequentialFileFactory
{
   private static final Logger log = Logger.getLogger(FakeSequentialFileFactory.class);

   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private final Map<String, FakeSequentialFile> fileMap = new ConcurrentHashMap<String, FakeSequentialFile>();

   private final int alignment;

   private final boolean supportsCallback;

   private volatile boolean holdCallbacks;

   private ListenerHoldCallback holdCallbackListener;

   private volatile boolean generateErrors;

   private final List<CallbackRunnable> callbacksInHold;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   public FakeSequentialFileFactory(final int alignment, final boolean supportsCallback)
   {
      this.alignment = alignment;
      this.supportsCallback = supportsCallback;
      callbacksInHold = new ArrayList<CallbackRunnable>();
   }

   public FakeSequentialFileFactory()
   {
      this(1, false);
   }

   // Public --------------------------------------------------------

   public SequentialFile createSequentialFile(final String fileName, final int maxAIO)
   {
      FakeSequentialFile sf = fileMap.get(fileName);

      if (sf == null || sf.data == null)
      {
         sf = newSequentialFile(fileName);

         fileMap.put(fileName, sf);
      }
      else
      {
         sf.getData().position(0);

         // log.debug("positioning data to 0");
      }

      return sf;
   }

   public void clearBuffer(final ByteBuffer buffer)
   {
      final int limit = buffer.limit();
      buffer.rewind();

      for (int i = 0; i < limit; i++)
      {
         buffer.put((byte)0);
      }

      buffer.rewind();
   }

   public List<String> listFiles(final String extension)
   {
      List<String> files = new ArrayList<String>();

      for (String s : fileMap.keySet())
      {
         if (s.endsWith("." + extension))
         {
            files.add(s);
         }
      }

      return files;
   }

   public Map<String, FakeSequentialFile> getFileMap()
   {
      return fileMap;
   }

   public void clear()
   {
      fileMap.clear();
   }

   public boolean isSupportsCallbacks()
   {
      return supportsCallback;
   }

   public ByteBuffer newBuffer(int size)
   {
      if (size % alignment != 0)
      {
         size = (size / alignment + 1) * alignment;
      }
      return ByteBuffer.allocateDirect(size);
   }

   public int calculateBlockSize(final int position)
   {
      int alignment = getAlignment();

      int pos = (position / alignment + (position % alignment != 0 ? 1 : 0)) * alignment;

      return pos;
   }

   public ByteBuffer wrapBuffer(final byte[] bytes)
   {
      return ByteBuffer.wrap(bytes);
   }

   public synchronized boolean isHoldCallbacks()
   {
      return holdCallbacks;
   }

   public synchronized void setHoldCallbacks(final boolean holdCallbacks,
                                             final ListenerHoldCallback holdCallbackListener)
   {
      this.holdCallbacks = holdCallbacks;
      this.holdCallbackListener = holdCallbackListener;
   }

   public boolean isGenerateErrors()
   {
      return generateErrors;
   }

   public void setGenerateErrors(final boolean generateErrors)
   {
      this.generateErrors = generateErrors;
   }

   public synchronized void flushAllCallbacks()
   {
      for (Runnable action : callbacksInHold)
      {
         action.run();
      }

      callbacksInHold.clear();
   }

   public synchronized void flushCallback(final int position)
   {
      Runnable run = callbacksInHold.get(position);
      run.run();
      callbacksInHold.remove(run);
   }

   public synchronized void setCallbackAsError(final int position)
   {
      callbacksInHold.get(position).setSendError(true);
   }

   public synchronized int getNumberOfCallbacks()
   {
      return callbacksInHold.size();
   }

   public int getAlignment()
   {
      return alignment;
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   protected FakeSequentialFile newSequentialFile(final String fileName)
   {
      return new FakeSequentialFile(fileName);
   }

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

   /** This listener will return a message to the test with each callback added */
   public static interface ListenerHoldCallback
   {
      public void callbackAdded(final ByteBuffer bytes);
   }

   private class CallbackRunnable implements Runnable
   {

      final FakeSequentialFile file;

      final ByteBuffer bytes;

      final IOCallback callback;

      volatile boolean sendError;

      CallbackRunnable(final FakeSequentialFile file, final ByteBuffer bytes, final IOCallback callback)
      {
         this.file = file;
         this.bytes = bytes;
         this.callback = callback;
      }

      public void run()
      {

         if (sendError)
         {
            callback.onError(1, "Fake aio error");
         }
         else
         {
            try
            {
               file.data.put(bytes);
               if (callback != null)
               {
                  callback.done();
               }

               if (file.bufferCallback != null)
               {
                  file.bufferCallback.bufferDone(bytes);
               }
            }
            catch (Throwable e)
            {
               e.printStackTrace();
               callback.onError(-1, e.getMessage());
            }
         }
      }

      public boolean isSendError()
      {
         return sendError;
      }

      public void setSendError(final boolean sendError)
      {
         this.sendError = sendError;
      }
   }

   public class FakeSequentialFile implements SequentialFile
   {
      private volatile boolean open;

      private String fileName;

      private ByteBuffer data;

      private BufferCallback bufferCallback;

      public ByteBuffer getData()
      {
         return data;
      }

      public boolean isOpen()
      {
         // log.debug("is open" + System.identityHashCode(this) +" open is now "
         // + open);
         return open;
      }

      public void flush()
      {
      }

      public FakeSequentialFile(final String fileName)
      {
         this.fileName = fileName;
      }

      public synchronized void close() throws Exception
      {
         open = false;

         if (data != null)
         {
            data.position(0);
         }

         this.notifyAll();
      }

      public synchronized void waitForClose() throws Exception
      {
         while (open)
         {
            this.wait();
         }
      }

      public void delete() throws Exception
      {
         if (!open)
         {
            close();
         }

         fileMap.remove(fileName);
      }

      public String getFileName()
      {
         return fileName;
      }

      public void open() throws Exception
      {
         open(0);
      }

      public synchronized void open(final int currentMaxIO) throws Exception
      {
         open = true;
         checkAndResize(0);
      }

      public void setBufferCallback(final BufferCallback callback)
      {
         bufferCallback = callback;
      }

      public void fill(final int pos, final int size, final byte fillCharacter) throws Exception
      {
         if (!open)
         {
            throw new IllegalStateException("Is closed");
         }

         checkAndResize(pos + size);

         // log.debug("size is " + size + " pos is " + pos);

         for (int i = pos; i < size + pos; i++)
         {
            data.array()[i] = fillCharacter;

            // log.debug("Filling " + pos + " with char " + fillCharacter);
         }
      }

      public int read(final ByteBuffer bytes) throws Exception
      {
         return read(bytes, null);
      }

      public int read(final ByteBuffer bytes, final IOCallback callback) throws Exception
      {
         if (!open)
         {
            throw new IllegalStateException("Is closed");
         }

         byte[] bytesRead = new byte[bytes.limit()];

         data.get(bytesRead);

         bytes.put(bytesRead);

         bytes.rewind();

         if (callback != null)
         {
            callback.done();
         }

         return bytesRead.length;
      }

      public void position(final long pos) throws Exception
      {
         if (!open)
         {
            throw new IllegalStateException("Is closed");
         }

         checkAlignment(pos);

         data.position((int)pos);
      }

      public long position() throws Exception
      {
         return data.position();
      }

      public synchronized void write(final ByteBuffer bytes, final boolean sync, final IOCallback callback) throws Exception
      {
         if (!open)
         {
            throw new IllegalStateException("Is closed");
         }

         final int position = data == null ? 0 : data.position();

         // checkAlignment(position);

         // checkAlignment(bytes.limit());

         checkAndResize(bytes.limit() + position);

         CallbackRunnable action = new CallbackRunnable(this, bytes, callback);

         if (generateErrors)
         {
            action.setSendError(true);
         }

         if (holdCallbacks)
         {
            addCallback(bytes, action);
         }
         else
         {
            action.run();
         }

      }

      public void sync() throws Exception
      {
         if (supportsCallback)
         {
            throw new IllegalStateException("sync is not supported when supportsCallback=true");
         }
      }

      public long size() throws Exception
      {
         if (data == null)
         {
            return 0;
         }
         else
         {
            return data.limit();
         }
      }

      public void write(final ByteBuffer bytes, final boolean sync) throws Exception
      {
         write(bytes, sync, null);
      }

      private void checkAndResize(final int size)
      {
         int oldpos = data == null ? 0 : data.position();

         if (data == null || data.array().length < size)
         {
            byte[] newBytes = new byte[size];

            if (data != null)
            {
               System.arraycopy(data.array(), 0, newBytes, 0, data.array().length);
            }

            data = ByteBuffer.wrap(newBytes);

            data.position(oldpos);
         }
      }

      /**
       * @param bytes
       * @param action
       */
      private void addCallback(final ByteBuffer bytes, final CallbackRunnable action)
      {
         synchronized (FakeSequentialFileFactory.this)
         {
            callbacksInHold.add(action);
            if (holdCallbackListener != null)
            {
               holdCallbackListener.callbackAdded(bytes);
            }
         }
      }

      public int getAlignment() throws Exception
      {
         return alignment;
      }

      public int calculateBlockStart(final int position) throws Exception
      {
         int pos = (position / alignment + (position % alignment != 0 ? 1 : 0)) * alignment;

         return pos;
      }

      @Override
      public String toString()
      {
         return "FakeSequentialFile:" + fileName;
      }

      private void checkAlignment(final long position)
      {
         if (position % alignment != 0)
         {
            throw new IllegalStateException("Position is not aligned to " + alignment);
         }
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.journal.SequentialFile#renameTo(org.hornetq.core.journal.SequentialFile)
       */
      public void renameTo(String newFileName) throws Exception
      {
         fileMap.remove(this.fileName);
         this.fileName = newFileName;
         fileMap.put(newFileName, this);
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.journal.SequentialFile#fits(int)
       */
      public boolean fits(int size)
      {
         return data.position() + size <= data.limit();
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.journal.SequentialFile#setBuffering(boolean)
       */
      public void setBuffering(boolean buffering)
      {
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.journal.SequentialFile#lockBuffer()
       */
      public void lockBuffer()
      {
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.journal.SequentialFile#unlockBuffer()
       */
      public void unlockBuffer()
      {
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.journal.SequentialFile#write(org.hornetq.core.remoting.spi.HornetQBuffer, boolean, org.hornetq.core.journal.IOCallback)
       */
      public void write(HornetQBuffer bytes, boolean sync, IOCallback callback) throws Exception
      {
         write(ByteBuffer.wrap(bytes.array()), sync, callback);

      }

      /* (non-Javadoc)
       * @see org.hornetq.core.journal.SequentialFile#write(org.hornetq.core.remoting.spi.HornetQBuffer, boolean)
       */
      public void write(HornetQBuffer bytes, boolean sync) throws Exception
      {
         write(ByteBuffer.wrap(bytes.array()), sync);
      }

      /* (non-Javadoc)
       * @see org.hornetq.core.journal.SequentialFile#exists()
       */
      public boolean exists()
      {
         FakeSequentialFile file = fileMap.get(fileName);
         
         return file != null && file.data != null && file.data.capacity() > 0;
      }

   }

   /* (non-Javadoc)
    * @see org.hornetq.core.journal.SequentialFileFactory#createDirs()
    */
   public void createDirs() throws Exception
   {
      // nothing to be done on the fake Sequential file
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.journal.SequentialFileFactory#releaseBuffer(java.nio.ByteBuffer)
    */
   public void releaseBuffer(ByteBuffer buffer)
   {
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.journal.SequentialFileFactory#getBufferCallback()
    */
   public BufferCallback getBufferCallback()
   {
      return null;
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.journal.SequentialFileFactory#setBufferCallback(org.hornetq.core.journal.BufferCallback)
    */
   public void setBufferCallback(BufferCallback bufferCallback)
   {
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.journal.SequentialFileFactory#controlBuffersLifeCycle(boolean)
    */
   public void controlBuffersLifeCycle(boolean value)
   {
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.journal.SequentialFileFactory#stop()
    */
   public void stop()
   {
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.journal.SequentialFileFactory#activate(org.hornetq.core.journal.SequentialFile)
    */
   public void activate(SequentialFile file)
   {
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.journal.SequentialFileFactory#start()
    */
   public void start()
   {
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.journal.SequentialFileFactory#deactivate(org.hornetq.core.journal.SequentialFile)
    */
   public void deactivate(SequentialFile file)
   {
   }

   /* (non-Javadoc)
    * @see org.hornetq.core.journal.SequentialFileFactory#testFlush()
    */
   public void testFlush()
   {
   }

}