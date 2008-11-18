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

package org.jboss.messaging.core.config.impl;

import static org.jboss.messaging.core.config.impl.ConfigurationImpl.DEFAULT_MAX_FORWARD_BATCH_SIZE;
import static org.jboss.messaging.core.config.impl.ConfigurationImpl.DEFAULT_MAX_FORWARD_BATCH_TIME;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.messaging.core.config.TransportConfiguration;
import org.jboss.messaging.core.config.cluster.BroadcastGroupConfiguration;
import org.jboss.messaging.core.config.cluster.DiscoveryGroupConfiguration;
import org.jboss.messaging.core.config.cluster.MessageFlowConfiguration;
import org.jboss.messaging.core.logging.Logger;
import org.jboss.messaging.core.server.JournalType;
import org.jboss.messaging.util.SimpleString;
import org.jboss.messaging.util.XMLUtil;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * ConfigurationImpl
 * This class allows the Configuration class to be configured via a config file.
 *
 * @author <a href="ataylor@redhat.com">Andy Taylor</a>
 */
public class FileConfiguration extends ConfigurationImpl
{
   private static final long serialVersionUID = -4766689627675039596L;

   private static final Logger log = Logger.getLogger(FileConfiguration.class);

   // Constants ------------------------------------------------------------------------

   private static final String DEFAULT_CONFIGURATION_URL = "jbm-configuration.xml";

   // Attributes ----------------------------------------------------------------------

   private String configurationUrl = DEFAULT_CONFIGURATION_URL;

   // Public -------------------------------------------------------------------------

   public void start() throws Exception
   {
      URL url = getClass().getClassLoader().getResource(configurationUrl);
      Reader reader = new InputStreamReader(url.openStream());
      String xml = XMLUtil.readerToString(reader);
      xml = XMLUtil.replaceSystemProps(xml);
      Element e = XMLUtil.stringToElement(xml);

      clustered = getBoolean(e, "clustered", clustered);

      backup = getBoolean(e, "backup", backup);

      queueActivationTimeout = getLong(e, "queue-activation-timeout", queueActivationTimeout);

      // NOTE! All the defaults come from the super class

      scheduledThreadPoolMaxSize = getInteger(e, "scheduled-max-pool-size", scheduledThreadPoolMaxSize);

      requireDestinations = getBoolean(e, "require-destinations", requireDestinations);

      securityEnabled = getBoolean(e, "security-enabled", securityEnabled);

      jmxManagementEnabled = getBoolean(e, "jmx-management-enabled", jmxManagementEnabled);

      securityInvalidationInterval = getLong(e, "security-invalidation-interval", securityInvalidationInterval);

      connectionScanPeriod = getLong(e, "connection-scan-period", connectionScanPeriod);

      transactionTimeout = getLong(e, "transaction-timeout", transactionTimeout);

      transactionTimeoutScanPeriod = getLong(e, "transaction-timeout-scan-period", transactionTimeoutScanPeriod);

      managementAddress = new SimpleString(getString(e, "management-address", managementAddress.toString()));

      NodeList interceptorNodes = e.getElementsByTagName("remoting-interceptors");

      ArrayList<String> interceptorList = new ArrayList<String>();

      if (interceptorNodes.getLength() > 0)
      {
         NodeList interceptors = interceptorNodes.item(0).getChildNodes();

         for (int i = 0; i < interceptors.getLength(); i++)
         {
            if ("class-name".equalsIgnoreCase(interceptors.item(i).getNodeName()))
            {
               String clazz = interceptors.item(i).getTextContent();

               interceptorList.add(clazz);
            }
         }
      }
      
      interceptorClassNames = interceptorList;

      NodeList backups = e.getElementsByTagName("backup-connector");

      // There should be only one - this will be enforced by the DTD

      if (backups.getLength() > 0)
      {
         Node backupNode = backups.item(0);

         backupConnectorConfig = parseTransportConfiguration(backupNode);
      }

      NodeList acceptorNodes = e.getElementsByTagName("acceptor");

      for (int i = 0; i < acceptorNodes.getLength(); i++)
      {
         Node acceptorNode = acceptorNodes.item(i);

         TransportConfiguration acceptorConfig = parseTransportConfiguration(acceptorNode);

         acceptorConfigs.add(acceptorConfig);
      }

      NodeList bgNodes = e.getElementsByTagName("broadcast-group");

      for (int i = 0; i < bgNodes.getLength(); i++)
      {
         Element bgNode = (Element)bgNodes.item(i);

         parseBroadcastGroupConfiguration(bgNode);
      }
      
      NodeList dgNodes = e.getElementsByTagName("discovery-group");

      for (int i = 0; i < dgNodes.getLength(); i++)
      {
         Element dgNode = (Element)dgNodes.item(i);

         parseDiscoveryGroupConfiguration(dgNode);
      }
      
      NodeList mfNodes = e.getElementsByTagName("message-flow");

      for (int i = 0; i < mfNodes.getLength(); i++)
      {
         Element mfNode = (Element)mfNodes.item(i);

         parseMessageFlowConfiguration(mfNode);
      }

      // Persistence config

      bindingsDirectory = getString(e, "bindings-directory", bindingsDirectory);

      createBindingsDir = getBoolean(e, "create-bindings-dir", createBindingsDir);

      journalDirectory = getString(e, "journal-directory", journalDirectory);

      pagingDirectory = getString(e, "paging-directory", pagingDirectory);

      pagingMaxGlobalSize = getLong(e, "paging-max-global-size-bytes", pagingMaxGlobalSize);

      createJournalDir = getBoolean(e, "create-journal-dir", createJournalDir);

      String s = getString(e, "journal-type", journalType.toString());

      if (s == null || (!s.equals(JournalType.NIO.toString()) && !s.equals(JournalType.ASYNCIO.toString())))
      {
         throw new IllegalArgumentException("Invalid journal type " + s);
      }

      if (s.equals(JournalType.NIO.toString()))
      {
         journalType = JournalType.NIO;
      }
      else if (s.equals(JournalType.ASYNCIO.toString()))
      {
         journalType = JournalType.ASYNCIO;
      }

      journalSyncTransactional = getBoolean(e, "journal-sync-transactional", journalSyncTransactional);

      journalSyncNonTransactional = getBoolean(e, "journal-sync-non-transactional", journalSyncNonTransactional);

      journalFileSize = getInteger(e, "journal-file-size", journalFileSize);

      journalBufferReuseSize = getInteger(e, "journal-buffer-reuse-size", journalBufferReuseSize);

      journalMinFiles = getInteger(e, "journal-min-files", journalMinFiles);

      journalMaxAIO = getInteger(e, "journal-max-aio", journalMaxAIO);

      wildcardRoutingEnabled = getBoolean(e, "wild-card-routing-enabled", wildcardRoutingEnabled);

      messageCounterEnabled = getBoolean(e, "message-counter-enabled", messageCounterEnabled);
   }

   public String getConfigurationUrl()
   {
      return configurationUrl;
   }

   public void setConfigurationUrl(String configurationUrl)
   {
      this.configurationUrl = configurationUrl;
   }

   // Private -------------------------------------------------------------------------

   private Boolean getBoolean(Element e, String name, Boolean def)
   {
      NodeList nl = e.getElementsByTagName(name);
      if (nl.getLength() > 0)
      {
         return parseBoolean(nl.item(0));
      }
      return def;
   }

   private Integer getInteger(Element e, String name, Integer def)
   {
      NodeList nl = e.getElementsByTagName(name);
      if (nl.getLength() > 0)
      {
         return parseInt(nl.item(0));
      }
      return def;
   }

   private Long getLong(Element e, String name, Long def)
   {
      NodeList nl = e.getElementsByTagName(name);
      if (nl.getLength() > 0)
      {
         return parseLong(nl.item(0));
      }
      return def;
   }

   private String getString(Element e, String name, String def)
   {
      NodeList nl = e.getElementsByTagName(name);
      if (nl.getLength() > 0)
      {
         return nl.item(0).getTextContent().trim();
      }
      return def;
   }

   private TransportConfiguration parseTransportConfiguration(final Node node)
   {
      NodeList children = node.getChildNodes();

      String clazz = null;

      Map<String, Object> params = new HashMap<String, Object>();

      for (int i = 0; i < children.getLength(); i++)
      {
         String nodeName = children.item(i).getNodeName();

         if ("factory-class".equalsIgnoreCase(nodeName))
         {
            clazz = children.item(i).getTextContent();
         }
         else if ("params".equalsIgnoreCase(nodeName))
         {
            NodeList nlParams = children.item(i).getChildNodes();

            for (int j = 0; j < nlParams.getLength(); j++)
            {
               if ("param".equalsIgnoreCase(nlParams.item(j).getNodeName()))
               {
                  Node paramNode = nlParams.item(j);

                  NamedNodeMap attributes = paramNode.getAttributes();

                  Node nkey = attributes.getNamedItem("key");

                  String key = nkey.getTextContent();

                  Node nValue = attributes.getNamedItem("value");

                  Node nType = attributes.getNamedItem("type");

                  String type = nType.getTextContent();

                  if (type.equalsIgnoreCase("Integer"))
                  {
                     int iVal = parseInt(nValue);

                     params.put(key, iVal);
                  }
                  else if (type.equalsIgnoreCase("Long"))
                  {
                     long lVal = parseLong(nValue);

                     params.put(key, lVal);
                  }
                  else if (type.equalsIgnoreCase("String"))
                  {
                     params.put(key, nValue.getTextContent().trim());
                  }
                  else if (type.equalsIgnoreCase("Boolean"))
                  {
                     boolean bVal = parseBoolean(nValue);

                     params.put(key, bVal);
                  }
                  else
                  {
                     throw new IllegalArgumentException("Invalid parameter type " + type);
                  }
               }
            }
         }
      }

      return new TransportConfiguration(clazz, params);
   }
   
   private void parseBroadcastGroupConfiguration(final Element bgNode)
   {
      String name = bgNode.getAttribute("name");

      String localBindAddress = null;

      int localBindPort = -1;

      String groupAddress = null;

      int groupPort = -1;

      long broadcastPeriod = ConfigurationImpl.DEFAULT_BROADCAST_PERIOD;

      NodeList children = bgNode.getChildNodes();

      for (int j = 0; j < children.getLength(); j++)
      {
         Node child = children.item(j);

         if (child.getNodeName().equals("local-bind-address"))
         {
            localBindAddress = child.getTextContent().trim();
         }
         else if (child.getNodeName().equals("local-bind-port"))
         {
            localBindPort = parseInt(child);
         }
         else if (child.getNodeName().equals("group-address"))
         {
            groupAddress = child.getTextContent().trim();
         }
         else if (child.getNodeName().equals("group-port"))
         {
            groupPort = parseInt(child);
         }
         else if (child.getNodeName().equals("broadcast-period"))
         {
            broadcastPeriod = parseLong(child);
         }
      }

      BroadcastGroupConfiguration config = new BroadcastGroupConfiguration(name,
                                                                           localBindAddress,
                                                                           localBindPort,
                                                                           groupAddress,
                                                                           groupPort,
                                                                           broadcastPeriod);
      
      broadcastGroupConfigurations.add(config);
   }
   
   private void parseDiscoveryGroupConfiguration(final Element bgNode)
   {
      String name = bgNode.getAttribute("name");

      String groupAddress = null;

      int groupPort = -1;

      long refreshTimeout = ConfigurationImpl.DEFAULT_BROADCAST_REFRESH_TIMEOUT;

      NodeList children = bgNode.getChildNodes();

      for (int j = 0; j < children.getLength(); j++)
      {
         Node child = children.item(j);

         if (child.getNodeName().equals("group-address"))
         {
            groupAddress = child.getTextContent().trim();
         }
         else if (child.getNodeName().equals("group-port"))
         {
            groupPort = parseInt(child);
         }
         else if (child.getNodeName().equals("refresh-timeout"))
         {
            refreshTimeout = parseLong(child);
         }
      }

      DiscoveryGroupConfiguration config = new DiscoveryGroupConfiguration(name,
                                                                           groupAddress,
                                                                           groupPort,                                                              
                                                                           refreshTimeout);
      
      discoveryGroupConfigurations.add(config);
   }
   
   private void parseMessageFlowConfiguration(final Element bgNode)
   {
      String name = bgNode.getAttribute("name");

      String address = null;

      String filterString = null;

      boolean fanout = false;

      int maxBatchSize = DEFAULT_MAX_FORWARD_BATCH_SIZE;

      long maxBatchTime = DEFAULT_MAX_FORWARD_BATCH_TIME;

      List<TransportConfiguration> staticConnectors = new ArrayList<TransportConfiguration>();

      String discoveryGroupName = null;

      String transformerClassName = null;

      NodeList children = bgNode.getChildNodes();

      for (int j = 0; j < children.getLength(); j++)
      {
         Node child = children.item(j);

         if (child.getNodeName().equals("address"))
         {
            address = child.getTextContent().trim();
         }
         else if (child.getNodeName().equals("filter-string"))
         {
            filterString = child.getTextContent().trim();
         }
         else if (child.getNodeName().equals("fanout"))
         {
            fanout = parseBoolean(child);
         }
         else if (child.getNodeName().equals("max-batch-size"))
         {
            maxBatchSize = parseInt(child);
         }
         else if (child.getNodeName().equals("max-batch-time"))
         {
            maxBatchTime = parseLong(child);
         }
         else if (child.getNodeName().equals("discovery-group-name"))
         {
            discoveryGroupName = child.getTextContent().trim();
         }
         else if (child.getNodeName().equals("transformer-class-name"))
         {
            transformerClassName = child.getTextContent().trim();
         }
         else if (child.getNodeName().equals("connectors"))
         {
            NodeList connectorNodes = ((Element)child).getElementsByTagName("connector");
            
            for (int k = 0; k < connectorNodes.getLength(); k++)
            {
               TransportConfiguration connector = parseTransportConfiguration(connectorNodes.item(k));
               
               staticConnectors.add(connector);
            }
         }
      }

      MessageFlowConfiguration config;
      
      if (!staticConnectors.isEmpty())
      {
         config = new MessageFlowConfiguration(name, address, filterString, fanout, maxBatchSize, maxBatchTime,
                                               transformerClassName, staticConnectors);
      }
      else
      {
         config = new MessageFlowConfiguration(name, address, filterString, fanout, maxBatchSize, maxBatchTime,
                                               transformerClassName, discoveryGroupName);
      }
      
      messageFlowConfigurations.add(config);
   }

   private long parseLong(final Node elem)
   {
      String value = elem.getTextContent().trim();

      try
      {
         return Long.parseLong(value);
      }
      catch (NumberFormatException e)
      {
         throw new IllegalArgumentException("Element " + elem +
                                            " requires a valid Long value, but '" +
                                            value +
                                            "' cannot be parsed as a Long");
      }
   }

   private int parseInt(final Node elem)
   {
      String value = elem.getTextContent().trim();

      try
      {
         return Integer.parseInt(value);
      }
      catch (NumberFormatException e)
      {
         throw new IllegalArgumentException("Element " + elem +
                                            " requires a valid Integer value, but '" +
                                            value +
                                            "' cannot be parsed as an Integer");
      }
   }

   private boolean parseBoolean(final Node elem)
   {
      String value = elem.getTextContent().trim();

      try
      {
         return Boolean.parseBoolean(value);
      }
      catch (NumberFormatException e)
      {
         throw new IllegalArgumentException("Element " + elem +
                                            " requires a valid Boolean value, but '" +
                                            value +
                                            "' cannot be parsed as a Boolean");
      }
   }
}
