/**
 * JBoss, the OpenSource J2EE WebOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.jms.tools;

import org.jboss.jms.server.ServerPeer;
import org.jboss.jms.server.remoting.JMSServerInvocationHandler;
import org.jboss.jms.destination.JBossQueue;
import org.jboss.jms.destination.JBossTopic;
import org.jboss.aop.AspectXmlLoader;
import org.jboss.logging.Logger;
import org.jboss.remoting.InvokerLocator;
import org.jboss.remoting.transport.Connector;

import javax.naming.InitialContext;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.Binding;
import java.net.URL;
import java.util.Hashtable;
import java.util.Set;

/**
 * A place-holder for the micro-container. Used to bootstrap a server instance, until proper
 * integration with the micro-container. Run it with bin/runserver.
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 */
public class ServerWrapper
{
   // Constants -----------------------------------------------------

   protected static final Logger log = Logger.getLogger(ServerWrapper.class);

   // Static --------------------------------------------------------

   public static void main(String[] args) throws Exception
   {
      log.info("Starting the server ...");
      new ServerWrapper().start();
   }

   // Attributes ----------------------------------------------------

   private ServerPeer serverPeer;
   private InvokerLocator locator;
   private Connector connector;
   private String serverPeerID;
   private InitialContext initialContext;
   private Hashtable jndiEnvironment;

   // Constructors --------------------------------------------------

   /**
    * Uses default jndi properties (jndi.properties file)
    */
   public ServerWrapper() throws Exception
   {
      this(null);
   }

   /**
    * @param jndiEnvironment - Contains jndi properties. Null means using default properties
    *        (jndi.properties file)
    */
   public ServerWrapper(Hashtable jndiEnvironment) throws Exception
   {
      this.jndiEnvironment = jndiEnvironment;
      initialContext = new InitialContext(jndiEnvironment);
      locator = new InvokerLocator("socket://localhost:9890");
      serverPeerID = "ServerPeer0";
   }

   // Public --------------------------------------------------------

   public void start() throws Exception
   {
      loadAspects();
      setupJNDI();
      initializeRemoting();
      serverPeer = new ServerPeer(serverPeerID, locator, jndiEnvironment);
      serverPeer.start();
      log.info("server started");
   }

   public void stop() throws Exception
   {
      serverPeer.stop();
      serverPeer = null;
      tearDownRemoting();
      tearDownJNDI();
      unloadAspects();
      log.info("server stopped");
   }

   public void deployTopic(String name) throws Exception
   {
      Context c = (Context)((Context)initialContext.lookup("messaging")).lookup("topics");
      c.rebind(name, new JBossTopic(name));
   }

   public void undeployTopic(String name) throws Exception
   {
      Context c = (Context)((Context)initialContext.lookup("messaging")).lookup("topics");
      c.unbind(name);
   }

   public void deployQueue(String name) throws Exception
   {
      Context c = (Context)((Context)initialContext.lookup("messaging")).lookup("queues");
      c.rebind(name, new JBossQueue(name));
   }

   public void undeployQueue(String name) throws Exception
   {
      Context c = (Context)((Context)initialContext.lookup("messaging")).lookup("queues");
      c.unbind(name);
   }

   /**
    * @return the active connections clientIDs (as Strings)
    */
   public Set getConnections() throws Exception
   {
      return serverPeer.getConnections();
   }




   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------
   
   // Private -------------------------------------------------------

   private void loadAspects() throws Exception
   {
      URL url = this.getClass().getClassLoader().getResource("jms-aop.xml");
      AspectXmlLoader.deployXML(url);
   }

   private void unloadAspects() throws Exception
   {
      URL url = this.getClass().getClassLoader().getResource("jms-aop.xml");
      AspectXmlLoader.undeployXML(url);
   }

   private void setupJNDI() throws Exception
   {
      Context c = null;
      String path = "/";
      String name = "messaging";

      try
      {
         c = (Context)initialContext.lookup(name);
      }
      catch(NameNotFoundException e)
      {
         c = initialContext.createSubcontext(name);
         log.info("Created context " + path + name);
      }

      path += name + "/";
      String[] names = {"topics", "queues" };

      for (int i = 0; i < names.length; i++)
      {
         try
         {
            c.lookup(names[i]);
         }
         catch(NameNotFoundException e)
         {
            c.createSubcontext(names[i]);
            log.info("Created context " + path + names[i]);
         }
      }
   }

   private void tearDownJNDI() throws Exception
   {
      Context messaging = (Context)initialContext.lookup("/messaging");
      tearDownRecursively(messaging);
      initialContext.unbind("messaging");
      log.info("unbound messaging");
   }

   private void tearDownRecursively(Context c) throws Exception
   {
      for(NamingEnumeration ne = c.listBindings(""); ne.hasMore(); )
      {
         Binding b = (Binding)ne.next();
         String name = b.getName();
         Object object = b.getObject();
         if (object instanceof Context)
         {
            tearDownRecursively((Context)object);
         }
         c.unbind(name);
         log.info("unbound " + name);
      }
   }

   private void initializeRemoting() throws Exception
   {
      connector = new Connector();
      connector.setInvokerLocator(locator.getLocatorURI());
      connector.start();

      // also add the JMS subsystem
      connector.addInvocationHandler("JMS", new JMSServerInvocationHandler());
   }

   private void tearDownRemoting() throws Exception
   {
      connector.removeInvocationHandler("JMS");
      connector.stop();
      connector = null;
   }

   // Inner classes -------------------------------------------------
}
