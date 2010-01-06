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

package org.hornetq.jms.server.impl;

import java.util.ArrayList;
import java.util.List;

import org.hornetq.api.Pair;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.config.TransportConfiguration;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.impl.Validators;
import org.hornetq.core.deployers.DeploymentManager;
import org.hornetq.core.deployers.impl.XmlDeployer;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.server.cluster.DiscoveryGroupConfiguration;
import org.hornetq.jms.server.JMSServerManager;
import org.hornetq.utils.XMLConfigurationUtil;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author <a href="ataylor@redhat.com">Andy Taylor</a>
 * @author <a href="tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="jmesnil@redhat.com">Jeff Mesnil</a>
 */
public class JMSServerDeployer extends XmlDeployer
{
   private static final Logger log = Logger.getLogger(JMSServerDeployer.class);

   private final Configuration configuration;

   private final JMSServerManager jmsServerControl;

   private static final String CONNECTOR_REF_ELEMENT = "connector-ref";

   private static final String DISCOVERY_GROUP_ELEMENT = "discovery-group-ref";

   private static final String ENTRIES_NODE_NAME = "entries";

   private static final String ENTRY_NODE_NAME = "entry";

   private static final String CONNECTORS_NODE_NAME = "connectors";

   private static final String CONNECTION_FACTORY_NODE_NAME = "connection-factory";

   private static final String QUEUE_NODE_NAME = "queue";

   private static final String QUEUE_SELECTOR_NODE_NAME = "selector";

   private static final String TOPIC_NODE_NAME = "topic";

   private static final boolean DEFAULT_QUEUE_DURABILITY = true;

   public JMSServerDeployer(final JMSServerManager jmsServerManager,
                            final DeploymentManager deploymentManager,
                            final Configuration config)
   {
      super(deploymentManager);

      jmsServerControl = jmsServerManager;

      configuration = config;
   }

   /**
    * the names of the elements to deploy
    * 
    * @return the names of the elements todeploy
    */
   @Override
   public String[] getElementTagName()
   {
      return new String[] { JMSServerDeployer.QUEUE_NODE_NAME,
                           JMSServerDeployer.TOPIC_NODE_NAME,
                           JMSServerDeployer.CONNECTION_FACTORY_NODE_NAME };
   }

   @Override
   public void validate(final Node rootNode) throws Exception
   {
      org.hornetq.utils.XMLUtil.validate(rootNode, "schema/hornetq-jms.xsd");
   }

   /**
    * deploy an element
    * 
    * @param node the element to deploy
    * @throws Exception .
    */
   @Override
   public void deploy(final Node node) throws Exception
   {
      createAndBindObject(node);
   }

   /**
    * creates the object to bind, this will either be a JBossConnectionFActory, HornetQQueue or HornetQTopic
    * 
    * @param node the config
    * @throws Exception .
    */
   private void createAndBindObject(final Node node) throws Exception
   {
      if (node.getNodeName().equals(JMSServerDeployer.CONNECTION_FACTORY_NODE_NAME))
      {
         Element e = (Element)node;

         long clientFailureCheckPeriod = XMLConfigurationUtil.getLong(e,
                                                                      "client-failure-check-period",
                                                                      HornetQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD,
                                                                      Validators.MINUS_ONE_OR_GT_ZERO);
         long connectionTTL = XMLConfigurationUtil.getLong(e,
                                                           "connection-ttl",
                                                           HornetQClient.DEFAULT_CONNECTION_TTL,
                                                           Validators.MINUS_ONE_OR_GE_ZERO);
         long callTimeout = XMLConfigurationUtil.getLong(e,
                                                         "call-timeout",
                                                         HornetQClient.DEFAULT_CALL_TIMEOUT,
                                                         Validators.GE_ZERO);
         String clientID = XMLConfigurationUtil.getString(e, "client-id", null, Validators.NO_CHECK);
         int dupsOKBatchSize = XMLConfigurationUtil.getInteger(e,
                                                               "dups-ok-batch-size",
                                                               HornetQClient.DEFAULT_ACK_BATCH_SIZE,
                                                               Validators.GT_ZERO);
         int transactionBatchSize = XMLConfigurationUtil.getInteger(e,
                                                                    "transaction-batch-size",
                                                                    HornetQClient.DEFAULT_ACK_BATCH_SIZE,
                                                                    Validators.GT_ZERO);
         int consumerWindowSize = XMLConfigurationUtil.getInteger(e,
                                                                  "consumer-window-size",
                                                                  HornetQClient.DEFAULT_CONSUMER_WINDOW_SIZE,
                                                                  Validators.MINUS_ONE_OR_GE_ZERO);
         int producerWindowSize = XMLConfigurationUtil.getInteger(e,
                                                                  "producer-window-size",
                                                                  HornetQClient.DEFAULT_PRODUCER_WINDOW_SIZE,
                                                                  Validators.MINUS_ONE_OR_GT_ZERO);
         int consumerMaxRate = XMLConfigurationUtil.getInteger(e,
                                                               "consumer-max-rate",
                                                               HornetQClient.DEFAULT_CONSUMER_MAX_RATE,
                                                               Validators.MINUS_ONE_OR_GT_ZERO);
         int confirmationWindowSize = XMLConfigurationUtil.getInteger(e,
                                                                      "confirmation-window-size",
                                                                      HornetQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE,
                                                                      Validators.MINUS_ONE_OR_GT_ZERO);
         int producerMaxRate = XMLConfigurationUtil.getInteger(e,
                                                               "producer-max-rate",
                                                               HornetQClient.DEFAULT_PRODUCER_MAX_RATE,
                                                               Validators.MINUS_ONE_OR_GT_ZERO);
         boolean cacheLargeMessagesClient = XMLConfigurationUtil.getBoolean(e,
                                                                            "cache-large-message-client",
                                                                            HornetQClient.DEFAULT_CACHE_LARGE_MESSAGE_CLIENT);
         int minLargeMessageSize = XMLConfigurationUtil.getInteger(e,
                                                                   "min-large-message-size",
                                                                   HornetQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE,
                                                                   Validators.GT_ZERO);
         boolean blockOnAcknowledge = XMLConfigurationUtil.getBoolean(e,
                                                                      "block-on-acknowledge",
                                                                      HornetQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE);
         boolean blockOnNonDurableSend = XMLConfigurationUtil.getBoolean(e,
                                                                            "block-on-non-durable-send",
                                                                            HornetQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND);
         boolean blockOnDurableSend = XMLConfigurationUtil.getBoolean(e,
                                                                         "block-on-durable-send",
                                                                         HornetQClient.DEFAULT_BLOCK_ON_DURABLE_SEND);
         boolean autoGroup = XMLConfigurationUtil.getBoolean(e,
                                                             "auto-group",
                                                             HornetQClient.DEFAULT_AUTO_GROUP);
         boolean preAcknowledge = XMLConfigurationUtil.getBoolean(e,
                                                                  "pre-acknowledge",
                                                                  HornetQClient.DEFAULT_PRE_ACKNOWLEDGE);
         long retryInterval = XMLConfigurationUtil.getLong(e,
                                                           "retry-interval",
                                                           HornetQClient.DEFAULT_RETRY_INTERVAL,
                                                           Validators.GT_ZERO);
         double retryIntervalMultiplier = XMLConfigurationUtil.getDouble(e,
                                                                         "retry-interval-multiplier",
                                                                         HornetQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER,
                                                                         Validators.GT_ZERO);
         long maxRetryInterval = XMLConfigurationUtil.getLong(e,
                                                              "max-retry-interval",
                                                              HornetQClient.DEFAULT_MAX_RETRY_INTERVAL,
                                                              Validators.GT_ZERO);
         int reconnectAttempts = XMLConfigurationUtil.getInteger(e,
                                                                 "reconnect-attempts",
                                                                 HornetQClient.DEFAULT_RECONNECT_ATTEMPTS,
                                                                 Validators.MINUS_ONE_OR_GE_ZERO);
         boolean failoverOnServerShutdown = XMLConfigurationUtil.getBoolean(e,
                                                                            "failover-on-server-shutdown",
                                                                            HornetQClient.DEFAULT_FAILOVER_ON_SERVER_SHUTDOWN);
         boolean useGlobalPools = XMLConfigurationUtil.getBoolean(e,
                                                                  "use-global-pools",
                                                                  HornetQClient.DEFAULT_USE_GLOBAL_POOLS);
         int scheduledThreadPoolMaxSize = XMLConfigurationUtil.getInteger(e,
                                                                          "scheduled-thread-pool-max-size",
                                                                          HornetQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE,
                                                                          Validators.MINUS_ONE_OR_GT_ZERO);
         int threadPoolMaxSize = XMLConfigurationUtil.getInteger(e,
                                                                 "thread-pool-max-size",
                                                                 HornetQClient.DEFAULT_THREAD_POOL_MAX_SIZE,
                                                                 Validators.MINUS_ONE_OR_GT_ZERO);
         String connectionLoadBalancingPolicyClassName = XMLConfigurationUtil.getString(e,
                                                                                        "connection-load-balancing-policy-class-name",
                                                                                        HornetQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME,
                                                                                        Validators.NOT_NULL_OR_EMPTY);
         long discoveryInitialWaitTimeout = XMLConfigurationUtil.getLong(e,
                                                                         "discovery-initial-wait-timeout",
                                                                         HornetQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT,
                                                                         Validators.GT_ZERO);
         String groupid = XMLConfigurationUtil.getString(e, "group-id", null, Validators.NO_CHECK);
         List<String> jndiBindings = new ArrayList<String>();
         List<Pair<TransportConfiguration, TransportConfiguration>> connectorConfigs = new ArrayList<Pair<TransportConfiguration, TransportConfiguration>>();
         DiscoveryGroupConfiguration discoveryGroupConfiguration = null;

         NodeList children = node.getChildNodes();

         for (int j = 0; j < children.getLength(); j++)
         {
            Node child = children.item(j);

            if (JMSServerDeployer.ENTRIES_NODE_NAME.equals(child.getNodeName()))
            {
               NodeList entries = child.getChildNodes();
               for (int i = 0; i < entries.getLength(); i++)
               {
                  Node entry = entries.item(i);
                  if (JMSServerDeployer.ENTRY_NODE_NAME.equals(entry.getNodeName()))
                  {
                     String jndiName = entry.getAttributes().getNamedItem("name").getNodeValue();

                     jndiBindings.add(jndiName);
                  }
               }
            }
            else if (JMSServerDeployer.CONNECTORS_NODE_NAME.equals(child.getNodeName()))
            {
               NodeList entries = child.getChildNodes();
               for (int i = 0; i < entries.getLength(); i++)
               {
                  Node entry = entries.item(i);
                  if (JMSServerDeployer.CONNECTOR_REF_ELEMENT.equals(entry.getNodeName()))
                  {
                     String connectorName = entry.getAttributes().getNamedItem("connector-name").getNodeValue();
                     TransportConfiguration connector = configuration.getConnectorConfigurations().get(connectorName);

                     if (connector == null)
                     {
                        JMSServerDeployer.log.warn("There is no connector with name '" + connectorName + "' deployed.");
                        return;
                     }

                     TransportConfiguration backupConnector = null;
                     Node backupNode = entry.getAttributes().getNamedItem("backup-connector-name");
                     if (backupNode != null)
                     {
                        String backupConnectorName = backupNode.getNodeValue();
                        backupConnector = configuration.getConnectorConfigurations().get(backupConnectorName);

                        if (backupConnector == null)
                        {
                           JMSServerDeployer.log.warn("There is no backup connector with name '" + connectorName +
                                                      "' deployed.");
                           return;
                        }
                     }

                     connectorConfigs.add(new Pair<TransportConfiguration, TransportConfiguration>(connector,
                                                                                                   backupConnector));
                  }
               }
            }
            else if (JMSServerDeployer.DISCOVERY_GROUP_ELEMENT.equals(child.getNodeName()))
            {
               String discoveryGroupName = child.getAttributes().getNamedItem("discovery-group-name").getNodeValue();

               discoveryGroupConfiguration = configuration.getDiscoveryGroupConfigurations().get(discoveryGroupName);

               if (discoveryGroupConfiguration == null)
               {
                  JMSServerDeployer.log.warn("There is no discovery group with name '" + discoveryGroupName +
                                             "' deployed.");

                  return;
               }
            }
         }

         String name = node.getAttributes().getNamedItem(getKeyAttribute()).getNodeValue();

         if (discoveryGroupConfiguration != null)
         {
            jmsServerControl.createConnectionFactory(name,
                                                     discoveryGroupConfiguration.getGroupAddress(),
                                                     discoveryGroupConfiguration.getGroupPort(),
                                                     clientID,
                                                     discoveryGroupConfiguration.getRefreshTimeout(),
                                                     clientFailureCheckPeriod,
                                                     connectionTTL,
                                                     callTimeout,
                                                     cacheLargeMessagesClient,
                                                     minLargeMessageSize,
                                                     consumerWindowSize,
                                                     consumerMaxRate,
                                                     confirmationWindowSize,
                                                     producerWindowSize,
                                                     producerMaxRate,
                                                     blockOnAcknowledge,
                                                     blockOnDurableSend,
                                                     blockOnNonDurableSend,
                                                     autoGroup,
                                                     preAcknowledge,
                                                     connectionLoadBalancingPolicyClassName,
                                                     transactionBatchSize,
                                                     dupsOKBatchSize,
                                                     discoveryInitialWaitTimeout,
                                                     useGlobalPools,
                                                     scheduledThreadPoolMaxSize,
                                                     threadPoolMaxSize,
                                                     retryInterval,
                                                     retryIntervalMultiplier,
                                                     maxRetryInterval,
                                                     reconnectAttempts,
                                                     failoverOnServerShutdown,
                                                     groupid,
                                                     jndiBindings);
         }
         else
         {
            jmsServerControl.createConnectionFactory(name,
                                                     connectorConfigs,
                                                     clientID,
                                                     clientFailureCheckPeriod,
                                                     connectionTTL,
                                                     callTimeout,
                                                     cacheLargeMessagesClient,
                                                     minLargeMessageSize,
                                                     consumerWindowSize,
                                                     consumerMaxRate,
                                                     confirmationWindowSize,
                                                     producerWindowSize,
                                                     producerMaxRate,
                                                     blockOnAcknowledge,
                                                     blockOnDurableSend,
                                                     blockOnNonDurableSend,
                                                     autoGroup,
                                                     preAcknowledge,
                                                     connectionLoadBalancingPolicyClassName,
                                                     transactionBatchSize,
                                                     dupsOKBatchSize,
                                                     useGlobalPools,
                                                     scheduledThreadPoolMaxSize,
                                                     threadPoolMaxSize,
                                                     retryInterval,
                                                     retryIntervalMultiplier,
                                                     maxRetryInterval,
                                                     reconnectAttempts,
                                                     failoverOnServerShutdown,
                                                     groupid,
                                                     jndiBindings);
         }
      }
      else if (node.getNodeName().equals(JMSServerDeployer.QUEUE_NODE_NAME))
      {
         Element e = (Element)node;
         NamedNodeMap atts = node.getAttributes();
         String queueName = atts.getNamedItem(getKeyAttribute()).getNodeValue();
         String selectorString = null;
         boolean durable = XMLConfigurationUtil.getBoolean(e, "durable", JMSServerDeployer.DEFAULT_QUEUE_DURABILITY);
         NodeList children = node.getChildNodes();
         ArrayList<String> jndiNames = new ArrayList<String>();
         for (int i = 0; i < children.getLength(); i++)
         {
            Node child = children.item(i);

            if (JMSServerDeployer.ENTRY_NODE_NAME.equals(children.item(i).getNodeName()))
            {
               String jndiName = child.getAttributes().getNamedItem("name").getNodeValue();
               jndiNames.add(jndiName);
            }
            else if (JMSServerDeployer.QUEUE_SELECTOR_NODE_NAME.equals(children.item(i).getNodeName()))
            {
               Node selectorNode = children.item(i);
               Node attNode = selectorNode.getAttributes().getNamedItem("string");
               selectorString = attNode.getNodeValue();
            }
         }
         for (String jndiName : jndiNames)
         {
            jmsServerControl.createQueue(queueName, jndiName, selectorString, durable);
         }
      }
      else if (node.getNodeName().equals(JMSServerDeployer.TOPIC_NODE_NAME))
      {
         String topicName = node.getAttributes().getNamedItem(getKeyAttribute()).getNodeValue();
         NodeList children = node.getChildNodes();
         for (int i = 0; i < children.getLength(); i++)
         {
            Node child = children.item(i);

            if (JMSServerDeployer.ENTRY_NODE_NAME.equals(children.item(i).getNodeName()))
            {
               String jndiName = child.getAttributes().getNamedItem("name").getNodeValue();
               jmsServerControl.createTopic(topicName, jndiName);
            }
         }
      }
   }

   /**
    * undeploys an element
    * 
    * @param node the element to undeploy
    * @throws Exception .
    */
   @Override
   public void undeploy(final Node node) throws Exception
   {
      if (node.getNodeName().equals(JMSServerDeployer.CONNECTION_FACTORY_NODE_NAME))
      {
         String cfName = node.getAttributes().getNamedItem(getKeyAttribute()).getNodeValue();
         jmsServerControl.destroyConnectionFactory(cfName);
      }
      else if (node.getNodeName().equals(JMSServerDeployer.QUEUE_NODE_NAME))
      {
         String queueName = node.getAttributes().getNamedItem(getKeyAttribute()).getNodeValue();
         jmsServerControl.undeployDestination(queueName);
      }
      else if (node.getNodeName().equals(JMSServerDeployer.TOPIC_NODE_NAME))
      {
         String topicName = node.getAttributes().getNamedItem(getKeyAttribute()).getNodeValue();
         jmsServerControl.undeployDestination(topicName);
      }
   }

   @Override
   public String[] getDefaultConfigFileNames()
   {
      return new String[] { "hornetq-jms.xml" };
   }

}
