/*
  * JBoss, Home of Professional Open Source
  * Copyright 2005, JBoss Inc., and individual contributors as indicated
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
package org.jboss.messaging.core.impl.server;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.aop.microcontainer.aspects.jmx.JMX;
import org.jboss.jms.server.ConnectionManager;
import org.jboss.jms.server.MessagingTimeoutFactory;
import org.jboss.jms.server.SecurityStore;
import org.jboss.jms.server.TransactionRepository;
import org.jboss.jms.server.connectionmanager.SimpleConnectionManager;
import org.jboss.jms.server.endpoint.ConnectionFactoryAdvisedPacketHandler;
import org.jboss.jms.server.endpoint.ServerSessionEndpoint;
import org.jboss.jms.server.plugin.contract.JMSUserManager;
import org.jboss.jms.server.plugin.NullUserManager;
import org.jboss.jms.server.security.Role;
import org.jboss.jms.server.security.SecurityMetadataStore;
import org.jboss.jms.server.security.NullAuthenticationManager;
import org.jboss.logging.Logger;
import org.jboss.messaging.core.*;
import org.jboss.messaging.core.impl.ConditionImpl;
import org.jboss.messaging.core.impl.QueueFactoryImpl;
import org.jboss.messaging.core.impl.memory.SimpleMemoryManager;
import org.jboss.messaging.core.impl.messagecounter.MessageCounterManager;
import org.jboss.messaging.core.impl.postoffice.PostOfficeImpl;
import org.jboss.messaging.core.remoting.Interceptor;
import org.jboss.messaging.core.remoting.impl.mina.MinaService;
import org.jboss.messaging.core.remoting.RemotingService;
import org.jboss.messaging.core.remoting.RemotingConfiguration;
import org.jboss.messaging.deployers.queue.QueueSettingsDeployer;
import org.jboss.messaging.deployers.security.SecurityDeployer;
import org.jboss.messaging.util.ExceptionUtil;
import org.jboss.messaging.util.HierarchicalObjectRepository;
import org.jboss.messaging.util.HierarchicalRepository;
import org.jboss.messaging.util.Version;
import org.jboss.security.AuthenticationManager;

/**
 * A Messaging Server
 *
 * @author <a href="mailto:ovidiu@feodorov.com">Ovidiu Feodorov</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:juha@jboss.org">Juha Lindfors</a>
 * @author <a href="mailto:aslak@conduct.no">Aslak Knutsen</a>
 * @author <a href="mailto:ataylor@redhat.com>Andy Taylor</a>
 * @version <tt>$Revision: 3543 $</tt>
 *          <p/>
 *          $Id: ServerPeer.java 3543 2008-01-07 22:31:58Z clebert.suconic@jboss.com $
 */
@JMX(name = "jboss.messaging:service=MessagingServer", exposedInterface = MessagingServer.class)
public class MessagingServerImpl implements MessagingServer
{
   // Constants ------------------------------------------------------------------------------------

   private static final Logger log = Logger.getLogger(MessagingServerImpl.class);

   // Static ---------------------------------------------------------------------------------------

   // Attributes -----------------------------------------------------------------------------------

   private Version version;

   private boolean started;

   //private boolean supportsFailover = true;

   private Map<String, ServerSessionEndpoint> sessions;

   // wired components

   private SecurityMetadataStore securityStore;
   private SimpleConnectionManager connectionManager;
   private MemoryManager memoryManager = new SimpleMemoryManager();
   private MessageCounterManager messageCounterManager;
   private TransactionRepository transactionRepository = new TransactionRepository();
   private PostOffice postOffice;
   private SecurityDeployer securityDeployer;
   private QueueSettingsDeployer queueSettingsDeployer;
   private AuthenticationManager authenticationManager = new NullAuthenticationManager();

   // plugins

   private PersistenceManager persistenceManager = new NullPersistenceManager();

   private JMSUserManager jmsUserManager = new NullUserManager();

   private RemotingService remotingService;
   private boolean createTransport = false;

   private Configuration configuration = new Configuration();
   private HierarchicalRepository<HashSet<Role>> securityRepository = new HierarchicalObjectRepository<HashSet<Role>>();
   private HierarchicalRepository<QueueSettings> queueSettingsRepository = new HierarchicalObjectRepository<QueueSettings>();
   private QueueFactoryImpl queueFactory = new QueueFactoryImpl();

   // Constructors ---------------------------------------------------------------------------------
   /**
    * typically called by the MC framework or embedded if the user want to create and start their own RemotingService
    */
   public MessagingServerImpl()
   {
      // Some wired components need to be started here

      version = Version.instance();

      sessions = new ConcurrentHashMap<String, ServerSessionEndpoint>();

      started = false;
   }

   /**
    * called when the usewr wants the MessagingServer to handle the creation of the RemotingTransport
    * @param remotingConfiguration the RemotingConfiguration
    */
   public MessagingServerImpl(RemotingConfiguration remotingConfiguration)
   {
      this();
      createTransport = true;
      remotingService = new MinaService(remotingConfiguration);
   }
   // lifecycle methods ----------------------------------------------------------------

   public synchronized void start() throws Exception
   {
      try
      {
         log.debug("starting MessagingServer");

         if (started)
         {
            return;
         }

         if (configuration.getMessagingServerID() < 0)
         {
            throw new IllegalStateException("MessagingServer ID not set");
         }

         log.debug(this + " starting");

         // Create the wired components

         securityStore = new SecurityMetadataStore(this);
         securityRepository.setDefault(new HashSet<Role>());
         securityStore.setSecurityRepository(securityRepository);
         securityStore.setAuthenticationManager(authenticationManager);
         securityDeployer = new SecurityDeployer();
         securityDeployer.setSecurityRepository(securityRepository);
         queueSettingsDeployer = new QueueSettingsDeployer();
         queueSettingsRepository.setDefault(new QueueSettings());
         queueSettingsDeployer.setQueueSettingsRepository(queueSettingsRepository);
         queueFactory.setQueueSettingsRepository(queueSettingsRepository);
         connectionManager = new SimpleConnectionManager();
         memoryManager = new SimpleMemoryManager();
         messageCounterManager = new MessageCounterManager(configuration.getMessageCounterSamplePeriod());
         configuration.addPropertyChangeListener(new PropertyChangeListener()
         {
            public void propertyChange(PropertyChangeEvent evt)
            {
               if(evt.getPropertyName().equals("messageCounterSamplePeriod"))
                  messageCounterManager.reschedule(configuration.getMessageCounterSamplePeriod());
            }
         });
         postOffice = new PostOfficeImpl(configuration.getMessagingServerID(),
                                         persistenceManager, queueFactory);

         if(createTransport)
         {
            remotingService.start();
         }
         // Start the wired components
         securityDeployer.start();
         queueSettingsDeployer.start();
         connectionManager.start();
         memoryManager.start();
         postOffice.start();
         ConnectionFactoryAdvisedPacketHandler connectionFactoryAdvisedPacketHandler =
                 new ConnectionFactoryAdvisedPacketHandler(this);
         getRemotingService().getDispatcher().register(connectionFactoryAdvisedPacketHandler);
         
         ClassLoader loader = Thread.currentThread().getContextClassLoader();
         for (String interceptorClass: configuration.getDefaultInterceptors())
         {
            try
            {
               Class clazz = loader.loadClass(interceptorClass);
               getRemotingService().addInterceptor((Interceptor)clazz.newInstance());
            }
            catch (Exception e)
            {
               log.warn("Error instantiating interceptor \"" + interceptorClass + "\"", e);
            }
         }
         
         started = true;
         log.info("JBoss Messaging " + getVersion().getProviderVersion() + " server [" +
                 configuration.getMessagingServerID() + "] started");
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " startService");
      }
   }

   public synchronized void stop() throws Exception
   {
      try
      {
         if (!started)
         {
            return;
         }

         log.info(this + " is Stopping. NOTE! Stopping the server peer cleanly will NOT cause failover to occur");

         started = false;

         // Stop the wired components
         securityDeployer.stop();
         queueSettingsDeployer.stop();
         connectionManager.stop();
         connectionManager = null;
         memoryManager.stop();
         memoryManager = null;
         messageCounterManager.stop();
         messageCounterManager = null;
         postOffice.stop();
         postOffice = null;
         if(createTransport)
         {
            remotingService.stop();
         }
         MessagingTimeoutFactory.instance.reset();

         log.info("JMS " + this + " stopped");
      }
      catch (Throwable t)
      {
         throw ExceptionUtil.handleJMXInvocation(t, this + " stopService");
      }
   }


   // MessagingServer implementation -----------------------------------------------------------

   public Version getVersion()
   {
      return version;
   }

   public Configuration getConfiguration()
   {
      return configuration;
   }

   public boolean isStarted()
   {
      return started;
   }

   public void setConfiguration(Configuration configuration)
   {
      this.configuration = configuration;
   }

   public void setRemotingService(RemotingService remotingService)
   {
      this.remotingService = remotingService;
   }

   public RemotingService getRemotingService()
   {
      return remotingService;
   }

   public ServerSessionEndpoint getSession(String sessionID)
   {
      return (ServerSessionEndpoint) sessions.get(sessionID);
   }

   public Collection<ServerSessionEndpoint> getSessions()
   {
      return sessions.values();
   }

   public void addSession(String id, ServerSessionEndpoint session)
   {
      sessions.put(id, session);
   }

   public void removeSession(String id)
   {
      if (sessions.remove(id) == null)
      {
         throw new IllegalStateException("Cannot find session with id " + id + " to remove");
      }
   }

   public synchronized Queue getDefaultDLQInstance() throws Exception
   {
      if (configuration.getDefaultDLQ() != null)
      {
         List<Binding> bindings = postOffice.getBindingsForQueueName(configuration.getDefaultDLQ());

         if (bindings.isEmpty())
         {
            throw new IllegalStateException("Cannot find binding for queue " + configuration.getDefaultDLQ());
         }

         return bindings.get(0).getQueue();
      }
      else
      {
         return null;
      }
   }

   public synchronized Queue getDefaultExpiryQueueInstance() throws Exception
   {
      if (configuration.getDefaultExpiryQueue() != null)
      {
         List<Binding> bindings = postOffice.getBindingsForQueueName(configuration.getDefaultExpiryQueue());

         if (bindings.isEmpty())
         {
            throw new IllegalStateException("Cannot find binding for queue " + configuration.getDefaultExpiryQueue());
         }

         return bindings.get(0).getQueue();
      }
      else
      {
         return null;
      }
   }

   public void enableMessageCounters()
   {
      messageCounterManager.start();
   }

   public void disableMessageCounters()
   {
      messageCounterManager.stop();

      messageCounterManager.resetAllCounters();

      messageCounterManager.resetAllCounterHistories();
   }

   public void createQueue(String name) throws Exception
   {
      Condition queueCond = new ConditionImpl(DestinationType.QUEUE, name);

      if (!getPostOffice().containsCondition(queueCond))
      {
         getPostOffice().addQueue(queueCond, name, null, true, false, false);
         getPostOffice().addCondition(queueCond);
      }
   }

   public void destroyQueue(String name) throws Exception
   {
      destroyDestination(true, name);
   }

   public void createTopic(String name) throws Exception
   {
      Condition topicCond = new ConditionImpl(DestinationType.TOPIC, name);
      if (!getPostOffice().containsCondition(topicCond));
      {
         getPostOffice().addCondition(topicCond);
      }
   }

   public void destroyTopic(String name) throws Exception
   {
      destroyDestination(false, name);
   }

   public void resetAllMessageCounters()
   {
      this.messageCounterManager.resetAllCounters();
   }

   public void resetAllMessageCounterHistories()
   {
      this.messageCounterManager.resetAllCounterHistories();
   }

   public void removeAllMessagesForQueue(String queueName) throws Exception
   {
      Condition condition = new ConditionImpl(DestinationType.QUEUE, queueName);

      List<Binding> bindings = postOffice.getBindingsForCondition(condition);

      if (!bindings.isEmpty())
      {
         Queue queue = bindings.get(0).getQueue();

         persistenceManager.deleteAllReferences(queue);

         queue.removeAllReferences();
      }
   }

   public void removeAllMessagesForTopic(String queueName) throws Exception
   {
      Condition condition = new ConditionImpl(DestinationType.QUEUE, queueName);

      List<Binding> bindings = postOffice.getBindingsForCondition(condition);

      for (Binding binding: bindings)
      {
         Queue queue = binding.getQueue();

         if (queue.isDurable())
         {
            persistenceManager.deleteAllReferences(queue);
         }

         queue.removeAllReferences();
      }
   }

   public SecurityStore getSecurityManager()
   {
      return securityStore;
   }

   public ConnectionManager getConnectionManager()
   {
      return connectionManager;
   }

   public MemoryManager getMemoryManager()
   {
      return memoryManager;
   }

   public TransactionRepository getTransactionRepository()
   {
      return transactionRepository;
   }

   public PersistenceManager getPersistenceManager()
   {
      return persistenceManager;
   }

   public void setPersistenceManager(PersistenceManager persistenceManager)
   {
      this.persistenceManager = persistenceManager;
   }

   public JMSUserManager getJmsUserManagerInstance()
   {
      return jmsUserManager;
   }

   public void setJmsUserManager(JMSUserManager jmsUserManager)
   {
      this.jmsUserManager = jmsUserManager;
   }

   public PostOffice getPostOffice()
   {
      return postOffice;
   }

   public void setPostOffice(PostOffice postOffice)
   {
      this.postOffice = postOffice;
   }


   public HierarchicalRepository<HashSet<Role>> getSecurityRepository()
   {
      return securityRepository;
   }

   public HierarchicalRepository<QueueSettings> getQueueSettingsRepository()
   {
      return queueSettingsRepository;
   }

   public void setAuthenticationManager(AuthenticationManager authenticationManager)
   {
      this.authenticationManager = authenticationManager;
   }



   public String toString()
   {
      return "MessagingServer[" + configuration.getMessagingServerID() + "]";
   }

   // Public ---------------------------------------------------------------------------------------

   // Package protected ----------------------------------------------------------------------------

   // Protected ------------------------------------------------------------------------------------

   // Private --------------------------------------------------------------------------------------

   private boolean destroyDestination(boolean isQueue, String name) throws Exception
   {
      Condition condition = new ConditionImpl(isQueue ? DestinationType.QUEUE : DestinationType.TOPIC, name);

      List<Binding> bindings = getPostOffice().getBindingsForCondition(condition);

      boolean destroyed = false;

      for (Binding binding: bindings)
      {
         Queue queue = binding.getQueue();

         getPersistenceManager().deleteAllReferences(queue);

         queue.removeAllReferences();

         //Durable subs need to be removed on all nodes
         boolean all = !isQueue && queue.isDurable();

         getPostOffice().removeQueue(condition, queue.getName(), all);

         destroyed = true;
      }

      getPostOffice().removeCondition(condition);

      return destroyed;
   }
}
