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
package org.hornetq.core.remoting.impl.invm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

import org.hornetq.core.exception.HornetQException;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.remoting.spi.Acceptor;
import org.hornetq.core.remoting.spi.BufferHandler;
import org.hornetq.core.remoting.spi.Connection;
import org.hornetq.core.remoting.spi.ConnectionLifeCycleListener;
import org.hornetq.core.remoting.spi.Connector;
import org.hornetq.utils.ConfigurationHelper;
import org.hornetq.utils.OrderedExecutorFactory;

/**
 * A InVMConnector
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 */
public class InVMConnector implements Connector
{
   public static final Logger log = Logger.getLogger(InVMConnector.class);

   // Used for testing failure only
   public static volatile boolean failOnCreateConnection;

   public static volatile int numberOfFailures = -1;

   private static volatile int failures;

   public static synchronized void resetFailures()
   {
      failures = 0;
      failOnCreateConnection = false;
      numberOfFailures = -1;
   }

   private static synchronized void incFailures()
   {
      failures++;
      if (failures == numberOfFailures)
      {
         resetFailures();
      }
   }

   protected final int id;

   private final BufferHandler handler;

   private final ConnectionLifeCycleListener listener;

   private final InVMAcceptor acceptor;

   private ConcurrentMap<String, Connection> connections = new ConcurrentHashMap<String, Connection>();

   private volatile boolean started;

   protected final OrderedExecutorFactory executorFactory;

   public InVMConnector(final Map<String, Object> configuration,
                        final BufferHandler handler,
                        final ConnectionLifeCycleListener listener,
                        final Executor threadPool)
   {
      this.listener = listener;

      this.id = ConfigurationHelper.getIntProperty(TransportConstants.SERVER_ID_PROP_NAME, 0, configuration);

      this.handler = handler;

      this.executorFactory = new OrderedExecutorFactory(threadPool);

      InVMRegistry registry = InVMRegistry.instance;

      acceptor = registry.getAcceptor(id);
   }

   public Acceptor getAcceptor()
   {
      return acceptor;
   }

   public synchronized void close()
   {
      if (!started)
      {
         return;
      }

      for (Connection connection : connections.values())
      {
         listener.connectionDestroyed(connection.getID());
      }

      started = false;
   }

   public boolean isStarted()
   {
      return started;
   }

   public Connection createConnection()
   {
      if (failOnCreateConnection)
      {
         incFailures();
         // For testing only
         return null;
      }

      Connection conn = internalCreateConnection(acceptor.getHandler(), new Listener(), acceptor.getExecutorFactory().getExecutor());

      acceptor.connect((String)conn.getID(), handler, this, executorFactory.getExecutor());

      return conn;
   }

   public synchronized void start()
   {
      started = true;
   }

   public BufferHandler getHandler()
   {
      return handler;
   }

   public void disconnect(final String connectionID)
   {
      if (!started)
      {
         return;
      }

      Connection conn = connections.get(connectionID);

      if (conn != null)
      {
         conn.close();
      }
   }

   // This may be an injection point for mocks on tests
   protected Connection internalCreateConnection(final BufferHandler handler,
                                                 final ConnectionLifeCycleListener listener,
                                                 final Executor serverExecutor)
   {
      return new InVMConnection(id, handler, listener, serverExecutor);
   }

   private class Listener implements ConnectionLifeCycleListener
   {
      public void connectionCreated(final Connection connection)
      {
         if (connections.putIfAbsent((String)connection.getID(), connection) != null)
         {
            throw new IllegalArgumentException("Connection already exists with id " + connection.getID());
         }

         listener.connectionCreated(connection);
      }

      public void connectionDestroyed(final Object connectionID)
      {
         if (connections.remove(connectionID) != null)
         {
            // Close the corresponding connection on the other side
            acceptor.disconnect((String)connectionID);
            
            // Execute on different thread to avoid deadlocks
            new Thread()
            {
               public void run()
               {
                  listener.connectionDestroyed(connectionID);                  
               }
            }.start();
         }
      }

      public void connectionException(final Object connectionID, final HornetQException me)
      {                  
         // Execute on different thread to avoid deadlocks
         new Thread()
         {
            public void run()
            {
               listener.connectionException(connectionID, me);       
            }
         }.start();
      }

   }

}