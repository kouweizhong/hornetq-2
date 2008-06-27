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

package org.jboss.messaging.tests.unit.core.deployers.impl;

import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.jboss.messaging.core.deployers.DeploymentManager;
import org.jboss.messaging.core.deployers.impl.QueueSettingsDeployer;
import org.jboss.messaging.core.settings.HierarchicalRepository;
import org.jboss.messaging.core.settings.impl.QueueSettings;
import org.jboss.messaging.util.SimpleString;
import org.jboss.messaging.util.XMLUtil;

/**
 * @author <a href="ataylor@redhat.com">Andy Taylor</a>
 */
public class QueueSettingsDeployerTest extends TestCase
{
   private String conf = "<queue-settings match=\"queues.*\">\n" +
           "      <clustered>false</clustered>\n" +
           "      <dlq>DLQtest</dlq>\n" +
           "      <expiry-queue>ExpiryQueueTest</expiry-queue>\n" +
           "      <redelivery-delay>100</redelivery-delay>\n" +
           "      <max-size-bytes>-100</max-size-bytes>\n" +
           "      <distribution-policy-class>org.jboss.messaging.core.impl.RoundRobinDistributionPolicy</distribution-policy-class>\n" +
           "      <message-counter-history-day-limit>1000</message-counter-history-day-limit>\n" +
           "   </queue-settings>";

   private QueueSettingsDeployer queueSettingsDeployer;

   private HierarchicalRepository<QueueSettings> repository;

   protected void setUp() throws Exception
   {
      repository = EasyMock.createStrictMock(HierarchicalRepository.class);
      DeploymentManager deploymentManager = EasyMock.createNiceMock(DeploymentManager.class);
      queueSettingsDeployer = new QueueSettingsDeployer(deploymentManager, repository);
   }

   public void testDeploy() throws Exception
   {
      QueueSettings queueSettings = new QueueSettings();
      queueSettings.setClustered(false);
      queueSettings.setRedeliveryDelay((long) 100);
      queueSettings.setMaxSizeBytes(-100);
      queueSettings.setDistributionPolicyClass("org.jboss.messaging.core.impl.RoundRobinDistributionPolicy");
      queueSettings.setMessageCounterHistoryDayLimit(1000);
      queueSettings.setDLQ(new SimpleString("DLQtest"));
      queueSettings.setExpiryQueue(new SimpleString("ExpiryQueueTest"));

      repository.addMatch(EasyMock.eq("queues.*"), settings(queueSettings));

      EasyMock.replay(repository);
      EasyMock.reportMatcher(new QueueSettingsMatcher(queueSettings));
      queueSettingsDeployer.deploy(XMLUtil.stringToElement(conf));
   }

   public void testUndeploy()
   {
      repository.removeMatch(conf);
      EasyMock.replay(repository);

   }

   public static QueueSettings settings(QueueSettings queueSettings)
   {
      EasyMock.reportMatcher(new QueueSettingsMatcher(queueSettings));
      return queueSettings;
   }

   static class QueueSettingsMatcher implements IArgumentMatcher
   {
      QueueSettings queueSettings;

      public QueueSettingsMatcher(QueueSettings queueSettings)
      {
         this.queueSettings = queueSettings;
      }

      public boolean matches(Object o)
      {
         QueueSettings that = (QueueSettings) o;

         if (!queueSettings.getDLQ().equals(that.getDLQ())) return false;
         if (!queueSettings.getExpiryQueue().equals(that.getExpiryQueue())) return false;
         if (!queueSettings.isClustered().equals(that.isClustered())) return false;
         if (!queueSettings.getDistributionPolicyClass().equals(that.getDistributionPolicyClass())) return false;
         if (!queueSettings.getMaxDeliveryAttempts().equals(that.getMaxDeliveryAttempts())) return false;
         if (!queueSettings.getMaxSizeBytes().equals(that.getMaxSizeBytes())) return false;
         if (!queueSettings.getMessageCounterHistoryDayLimit().equals(that.getMessageCounterHistoryDayLimit()))
            return false;
         if (!queueSettings.getRedeliveryDelay().equals(that.getRedeliveryDelay())) return false;

         return true;
      }

      public void appendTo(StringBuffer stringBuffer)
      {
         stringBuffer.append("Invalid Queue Settings created");
      }
   }
}
