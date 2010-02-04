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

import org.hornetq.core.config.Configuration;
import org.hornetq.core.deployers.DeploymentManager;
import org.hornetq.core.deployers.impl.XmlDeployer;
import org.hornetq.core.logging.Logger;
import org.hornetq.jms.server.JMSServerConfigParser;
import org.hornetq.jms.server.JMSServerManager;
import org.hornetq.jms.server.config.ConnectionFactoryConfiguration;
import org.hornetq.jms.server.config.QueueConfiguration;
import org.hornetq.jms.server.config.TopicConfiguration;
import org.w3c.dom.Node;

/**
 * @author <a href="ataylor@redhat.com">Andy Taylor</a>
 * @author <a href="tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="jmesnil@redhat.com">Jeff Mesnil</a>
 */
public class JMSServerDeployer extends XmlDeployer
{
   private static final Logger log = Logger.getLogger(JMSServerDeployer.class);

   private final Configuration configuration;
   
   private final JMSServerConfigParser parser;

   private final JMSServerManager jmsServerControl;

   protected static final String CONNECTOR_REF_ELEMENT = "connector-ref";

   protected static final String DISCOVERY_GROUP_ELEMENT = "discovery-group-ref";

   protected static final String ENTRIES_NODE_NAME = "entries";

   protected static final String ENTRY_NODE_NAME = "entry";

   protected static final String CONNECTORS_NODE_NAME = "connectors";

   protected static final String CONNECTION_FACTORY_NODE_NAME = "connection-factory";

   protected static final String QUEUE_NODE_NAME = "queue";

   protected static final String QUEUE_SELECTOR_NODE_NAME = "selector";

   protected static final String TOPIC_NODE_NAME = "topic";

   protected static final boolean DEFAULT_QUEUE_DURABILITY = true;

   public JMSServerDeployer(final JMSServerManager jmsServerManager,
                            final DeploymentManager deploymentManager,
                            final Configuration config)
   {
      super(deploymentManager);

      jmsServerControl = jmsServerManager;

      configuration = config;
      
      parser = new JMSServerConfigParserImpl(configuration);
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
         deployConnectionFactory(node);
      }
      else if (node.getNodeName().equals(JMSServerDeployer.QUEUE_NODE_NAME))
      {
         deployQueue(node);
      }
      else if (node.getNodeName().equals(JMSServerDeployer.TOPIC_NODE_NAME))
      {
         deployTopic(node);
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

   
   
   
   /**
    * @param node
    * @throws Exception
    */
   private void deployTopic(final Node node) throws Exception
   {
      TopicConfiguration topicConfig = parser.parseTopicConfiguration(node);
      for (String jndi : topicConfig.getBindings())
      {
         jmsServerControl.createTopic(topicConfig.getName(), jndi);
      }
   }

   /**
    * @param node
    * @throws Exception
    */
   private void deployQueue(final Node node) throws Exception
   {
      QueueConfiguration queueconfig = parser.parseQueueConfiguration(node);
      for (String jndiName : queueconfig.getBindings())
      {
         jmsServerControl.createQueue(queueconfig.getName(), jndiName, queueconfig.getSelector(), queueconfig.isDurable());
      }
   }

   /**
    * @param node
    * @throws Exception
    */
   private void deployConnectionFactory(final Node node) throws Exception
   {
      ConnectionFactoryConfiguration cfConfig = parser.parseConnectionFactoryConfiguration(node);

      ArrayList<String> listBindings = new ArrayList<String>();
      for (String str: cfConfig.getBindings())
      {
         listBindings.add(str);
      }
      
      if (cfConfig.getDiscoveryAddress() != null)
      {
         jmsServerControl.createConnectionFactory(cfConfig.getName(),
                                                  cfConfig.getDiscoveryAddress(),
                                                  cfConfig.getDiscoveryPort(),
                                                  cfConfig.getClientID(),
                                                  cfConfig.getDiscoveryRefreshTimeout(),
                                                  cfConfig.getClientFailureCheckPeriod(),
                                                  cfConfig.getConnectionTTL(),
                                                  cfConfig.getCallTimeout(),
                                                  cfConfig.isCacheLargeMessagesClient(),
                                                  cfConfig.getMinLargeMessageSize(),
                                                  cfConfig.getConsumerWindowSize(),
                                                  cfConfig.getConsumerMaxRate(),
                                                  cfConfig.getConfirmationWindowSize(),
                                                  cfConfig.getProducerWindowSize(),
                                                  cfConfig.getProducerMaxRate(),
                                                  cfConfig.isBlockOnAcknowledge(),
                                                  cfConfig.isBlockOnDurableSend(),
                                                  cfConfig.isBlockOnNonDurableSend(),
                                                  cfConfig.isAutoGroup(),
                                                  cfConfig.isPreAcknowledge(),
                                                  cfConfig.getLoadBalancingPolicyClassName(),
                                                  cfConfig.getTransactionBatchSize(),
                                                  cfConfig.getDupsOKBatchSize(),
                                                  cfConfig.getInitialWaitTimeout(),
                                                  cfConfig.isUseGlobalPools(),
                                                  cfConfig.getScheduledThreadPoolMaxSize(),
                                                  cfConfig.getThreadPoolMaxSize(),
                                                  cfConfig.getRetryInterval(),
                                                  cfConfig.getRetryIntervalMultiplier(),
                                                  cfConfig.getMaxRetryInterval(),
                                                  cfConfig.getReconnectAttempts(),
                                                  cfConfig.isFailoverOnServerShutdown(),
                                                  cfConfig.getGroupID(),
                                                  listBindings);
      }
      else
      {
         jmsServerControl.createConnectionFactory(cfConfig.getName(),
                                                  cfConfig.getConnectorConfigs(),
                                                  cfConfig.getClientID(),
                                                  cfConfig.getClientFailureCheckPeriod(),
                                                  cfConfig.getConnectionTTL(),
                                                  cfConfig.getCallTimeout(),
                                                  cfConfig.isCacheLargeMessagesClient(),
                                                  cfConfig.getMinLargeMessageSize(),
                                                  cfConfig.getConsumerWindowSize(),
                                                  cfConfig.getConsumerMaxRate(),
                                                  cfConfig.getConfirmationWindowSize(),
                                                  cfConfig.getProducerWindowSize(),
                                                  cfConfig.getProducerMaxRate(),
                                                  cfConfig.isBlockOnAcknowledge(),
                                                  cfConfig.isBlockOnDurableSend(),
                                                  cfConfig.isBlockOnNonDurableSend(),
                                                  cfConfig.isAutoGroup(),
                                                  cfConfig.isPreAcknowledge(),
                                                  cfConfig.getLoadBalancingPolicyClassName(),
                                                  cfConfig.getTransactionBatchSize(),
                                                  cfConfig.getDupsOKBatchSize(),
                                                  cfConfig.isUseGlobalPools(),
                                                  cfConfig.getScheduledThreadPoolMaxSize(),
                                                  cfConfig.getThreadPoolMaxSize(),
                                                  cfConfig.getRetryInterval(),
                                                  cfConfig.getRetryIntervalMultiplier(),
                                                  cfConfig.getMaxRetryInterval(),
                                                  cfConfig.getReconnectAttempts(),
                                                  cfConfig.isFailoverOnServerShutdown(),
                                                  cfConfig.getGroupID(),
                                                  listBindings);
      }
   }

   
}
