/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.messaging.core.remoting.impl.mina;

import static org.jboss.messaging.core.remoting.impl.mina.FilterChainSupport.addBlockingRequestResponseFilter;
import static org.jboss.messaging.core.remoting.impl.mina.FilterChainSupport.addCodecFilter;
import static org.jboss.messaging.core.remoting.impl.mina.FilterChainSupport.addExecutorFilter;
import static org.jboss.messaging.core.remoting.impl.mina.FilterChainSupport.addKeepAliveFilter;
import static org.jboss.messaging.core.remoting.impl.mina.FilterChainSupport.addLoggingFilter;
import static org.jboss.messaging.core.remoting.impl.mina.FilterChainSupport.addMDCFilter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.mina.common.CloseFuture;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.DefaultIoFilterChainBuilder;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoService;
import org.apache.mina.common.IoServiceListener;
import org.apache.mina.common.IoSession;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.jboss.jms.client.api.FailureListener;
import org.jboss.messaging.core.remoting.ConnectionExceptionListener;
import org.jboss.messaging.core.remoting.KeepAliveFactory;
import org.jboss.messaging.core.remoting.NIOConnector;
import org.jboss.messaging.core.remoting.NIOSession;
import org.jboss.messaging.core.remoting.PacketDispatcher;
import org.jboss.messaging.core.remoting.RemotingConfiguration;
import org.jboss.messaging.core.remoting.wireformat.AbstractPacket;
import org.jboss.messaging.core.remoting.wireformat.SessionSetIDMessage;
import org.jboss.messaging.util.Logger;
import org.jboss.messaging.util.MessagingException;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * 
 * @version <tt>$Revision$</tt>
 * 
 */
public class MinaConnector implements NIOConnector, ConnectionExceptionNotifier
{
   // Constants -----------------------------------------------------

   private final Logger log = Logger.getLogger(MinaConnector.class);
   
   // Attributes ----------------------------------------------------

   private RemotingConfiguration configuration;

   private NioSocketConnector connector;

   private ScheduledExecutorService blockingScheduler;

   private IoSession session;

   // FIXME clean up this listener mess
   private Map<FailureListener, IoServiceListener> listeners = new HashMap<FailureListener, IoServiceListener>();
   private ConnectionExceptionListener listener;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public MinaConnector(RemotingConfiguration configuration)
   {
      this(configuration, new ClientKeepAliveFactory());
   }

   public MinaConnector(RemotingConfiguration configuration, KeepAliveFactory keepAliveFactory)
   {
      assert configuration != null;
      assert keepAliveFactory != null;

      this.configuration = configuration;

      this.connector = new NioSocketConnector();
      DefaultIoFilterChainBuilder filterChain = connector.getFilterChain();

      addMDCFilter(filterChain);
      addCodecFilter(filterChain);
      addLoggingFilter(filterChain);
      blockingScheduler = addBlockingRequestResponseFilter(filterChain);
      addKeepAliveFilter(filterChain, keepAliveFactory, configuration.getKeepAliveInterval(),
            configuration.getKeepAliveTimeout());
      addExecutorFilter(filterChain);

      connector.setHandler(new MinaHandler(PacketDispatcher.client, this));
      connector.getSessionConfig().setKeepAlive(true);
      connector.getSessionConfig().setReuseAddress(true);
   }

   // NIOConnector implementation -----------------------------------

   public NIOSession connect() throws IOException
   {
      if (session != null && session.isConnected())
      {
         return new MinaSession(session);
      }
      InetSocketAddress address = new InetSocketAddress(configuration.getHost(), configuration.getPort());
      ConnectFuture future = connector.connect(address);
      connector.setDefaultRemoteAddress(address);

      future.awaitUninterruptibly();
      if (!future.isConnected())
      {
         throw new IOException("Cannot connect to " + address.toString());
      }
      this.session = future.getSession();
      AbstractPacket packet = new SessionSetIDMessage(Long.toString(session
            .getId()));
      session.write(packet);

      return new MinaSession(session);
   }

   public boolean disconnect()
   {
      if (session == null)
      {
         return false;
      }

      CloseFuture closeFuture = session.close().awaitUninterruptibly();
      boolean closed = closeFuture.isClosed();

      connector.dispose();
      blockingScheduler.shutdown();

      connector = null;
      blockingScheduler = null;
      session = null;

      return closed;
   }

   public void addFailureListener(final FailureListener listener)
   {
      assert listener != null;
      assert connector != null;

      IoServiceListener ioListener = new IoServiceListenerAdapter(listener);
      connector.addListener(ioListener);
      listeners.put(listener, ioListener);

      if (log.isTraceEnabled())
         log.trace("added listener " + listener + " to " + this);
   }

   public void removeFailureListener(FailureListener listener)
   {
      assert listener != null;
      assert connector != null;

      connector.removeListener(listeners.get(listener));
      listeners.remove(listener);

      if (log.isTraceEnabled())
         log.trace("removed listener " + listener + " from " + this);
   }

   public String getServerURI()
   { 
      return configuration.getURI();
   }

   public void setConnectionExceptionListener(ConnectionExceptionListener listener)
   {
      assert listener != null;
      
      this.listener = listener;
   }
   
   // ConnectionExceptionNotifier implementation -------------------------------
   
   public void fireConnectionException(Throwable cause, String remoteSessionID)
   {
      if (listener != null)
         listener.handleConnectionException(cause, remoteSessionID);
      
      for (FailureListener listener: listeners.keySet())
      {
         MessagingException me = new MessagingException(MessagingException.CONNECTION_TIMEDOUT, "Timed out");
         
         listener.onFailure(me);
      }
   }

   // Public --------------------------------------------------------
   
   @Override
   public String toString()
   {
      return "MinaConnector@" + System.identityHashCode(this) + "[configuration=" + configuration + "]"; 
   }
   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------

   private final class IoServiceListenerAdapter implements IoServiceListener
   {
      private final Logger log = Logger
            .getLogger(IoServiceListenerAdapter.class);

      private final FailureListener listener;

      private IoServiceListenerAdapter(FailureListener listener)
      {
         this.listener = listener;
      }

      public void serviceActivated(IoService service)
      {
         if (log.isTraceEnabled())
            log.trace("activated " + service);
      }

      public void serviceDeactivated(IoService service)
      {
         if (log.isTraceEnabled())
            log.trace("deactivated " + service);
      }

      public void serviceIdle(IoService service, IdleStatus idleStatus)
      {
         if (log.isTraceEnabled())
            log.trace("idle " + service + ", status=" + idleStatus);
      }

      public void sessionCreated(IoSession session)
      {
         if (log.isInfoEnabled())
            log.info("created session " + session);
      }

      public void sessionDestroyed(IoSession session)
      {
         log.warn("destroyed session " + session);

         MessagingException me =
            new MessagingException(MessagingException.INTERNAL_ERROR, "MINA session has been destroyed");
         if (listener != null)
            listener.onFailure(me);
      }
   }
}
