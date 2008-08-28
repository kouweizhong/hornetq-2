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

package org.jboss.messaging.core.remoting.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.messaging.core.config.AcceptorInfo;
import org.jboss.messaging.core.config.Configuration;
import org.jboss.messaging.core.exception.MessagingException;
import org.jboss.messaging.core.logging.Logger;
import org.jboss.messaging.core.remoting.Interceptor;
import org.jboss.messaging.core.remoting.PacketDispatcher;
import org.jboss.messaging.core.remoting.RemotingConnection;
import org.jboss.messaging.core.remoting.RemotingHandler;
import org.jboss.messaging.core.remoting.RemotingService;
import org.jboss.messaging.core.remoting.spi.Acceptor;
import org.jboss.messaging.core.remoting.spi.AcceptorFactory;
import org.jboss.messaging.core.remoting.spi.Connection;
import org.jboss.messaging.core.remoting.spi.ConnectionLifeCycleListener;
import org.jboss.messaging.util.JBMThreadFactory;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * @author <a href="mailto:ataylor@redhat.com">Andy Taylor</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 * @version <tt>$Revision$</tt>
 */
public class RemotingServiceImpl implements RemotingService, ConnectionLifeCycleListener
{
   // Constants -----------------------------------------------------

   private static final Logger log = Logger.getLogger(RemotingServiceImpl.class);

   // Attributes ----------------------------------------------------

   private volatile boolean started = false;

   private final Set<AcceptorInfo> acceptorInfos;
   
   private Set<Acceptor> acceptors = new HashSet<Acceptor>();

   private final PacketDispatcher dispatcher;

   private final ExecutorService remotingExecutor;

   private RemotingHandler handler;

   private final long callTimeout;

   private final Map<Object, RemotingConnection> connections = new ConcurrentHashMap<Object, RemotingConnection>();

   private final Timer failedConnectionTimer = new Timer(true);

   private TimerTask failedConnectionsTask;
   
   private final long connectionScanPeriod;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   public RemotingServiceImpl(final Configuration config)
   {
      dispatcher = new PacketDispatcherImpl(null);

      remotingExecutor = Executors.newCachedThreadPool(new JBMThreadFactory("JBM-session-ordering-threads"));

      handler = new RemotingHandlerImpl(dispatcher, remotingExecutor);
      
      final long callTimeout = config.getCallTimeout();
      
      this.acceptorInfos = config.getAcceptorInfos();
      
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      for (String interceptorClass : config.getInterceptorClassNames())
      {
         try
         {
            Class<?> clazz = loader.loadClass(interceptorClass);
            dispatcher.addInterceptor((Interceptor) clazz.newInstance());
         }
         catch (Exception e)
         {
            log.warn("Error instantiating interceptor \"" + interceptorClass + "\"", e);
         }
      }
      
      this.callTimeout = callTimeout;
      
      this.connectionScanPeriod = config.getConnectionScanPeriod();
   }

   // RemotingService implementation -------------------------------

   public synchronized void start() throws Exception
   {
      if (started)
      {
         return;
      }
      
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      
      for (AcceptorInfo info: acceptorInfos)
      {
         try
         {
            Class<?> clazz = loader.loadClass(info.getFactoryClassName());
            
            AcceptorFactory factory = (AcceptorFactory)clazz.newInstance();
            
            Acceptor acceptor = factory.createAcceptor(info.getParams(), handler, this);

            acceptors.add(acceptor);
         }
         catch (Exception e)
         {
            log.warn("Error instantiating acceptor \"" + info.getFactoryClassName() + "\"", e);
         }        
      }

      for (Acceptor a : acceptors)
      {
         a.start();
      }

      failedConnectionsTask = new FailedConnectionsTask();

      failedConnectionTimer.schedule(failedConnectionsTask, connectionScanPeriod, connectionScanPeriod);
      
      started = true;
   }

   public synchronized void stop()
   {
      if (!started)
      {
         return;
      }

      if (failedConnectionsTask != null)
      {
         failedConnectionsTask.cancel();

         failedConnectionsTask = null;
      }

      for (Acceptor acceptor : acceptors)
      {
         acceptor.stop();
      }

      started = false;
   }

   public boolean isStarted()
   {
      return started;
   }

   public PacketDispatcher getDispatcher()
   {
      return dispatcher;
   }

   public Set<Acceptor> getAcceptors()
   {
      return acceptors;
   }

   public RemotingConnection getConnection(final Object remotingConnectionID)
   {
      return connections.get(remotingConnectionID);
   }

   public synchronized Set<RemotingConnection> getConnections()
   {
      return new HashSet<RemotingConnection>(connections.values());
   }

   // ConnectionLifeCycleListener implementation -----------------------------------

   public void connectionCreated(final Connection connection)
   {
//      RemotingConnection backupConnection = null;
//      
//      if (config.isClustered())
//      {
//         Location backupLocation = new LocationImpl(config.getBackupTransport(), config.getBackupHost(),
//                                                    config.getBackupPort());
//         
//         ConnectionRegistry reg = ConnectionRegistryLocator.getRegistry();
//         
//         backupConnection = reg.getConnection(backupLocation, config.getConnectionParams());
//      }
      
      RemotingConnection rc =
         new RemotingConnectionImpl(connection, dispatcher, callTimeout);
          
      Object id = connection.getID();
      
      connections.put(id, rc);
   }

   public void connectionDestroyed(Object connectionID)
   {
      handler.removeExpireTime(connectionID);

      if (connections.remove(connectionID) == null)
      {
         throw new IllegalStateException("Cannot find connection with id " + connectionID);
      }
   }

   public void connectionException(Object connectionID, MessagingException me)
   {
      RemotingConnection rc = connections.remove(connectionID);
      
      if (rc == null)
      {
         throw new IllegalStateException("Cannot find connection with id " + connectionID);
      }

      rc.fail(me);
   }

   // Public --------------------------------------------------------

   /*
    * Used in testing
    */
   public void setHandler(final RemotingHandler handler)
   {
      this.handler = handler;
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

   private class FailedConnectionsTask extends TimerTask
   {
      private boolean cancelled;

      @Override
      public synchronized void run()
      {
         if (cancelled)
         {
            return;
         }

         Set<Object> failedIDs = handler.scanForFailedConnections();
         
         for (Object id: failedIDs)
         {
            log.info("Got failed connection " + id);
            RemotingConnection conn = connections.get(id);

            if (conn == null)
            {
               throw new IllegalStateException("Cannot find connection with id " + id);
            }

            MessagingException me = new MessagingException(MessagingException.CONNECTION_TIMEDOUT,
                  "Did not receive ping on connection. It is likely a client has exited or crashed without " +
                  "closing its connection, or the network between the server and client has failed. The connection will now be closed.");

            conn.fail(me);
         }
      }

      @Override
      public synchronized boolean cancel()
      {
         cancelled = true;

         return super.cancel();
      }

   }

}