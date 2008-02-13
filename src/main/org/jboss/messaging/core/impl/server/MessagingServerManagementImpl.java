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

import java.util.ArrayList;
import java.util.List;

import org.jboss.aop.microcontainer.aspects.jmx.JMX;
import org.jboss.jms.client.api.ClientConnectionFactory;
import org.jboss.jms.client.impl.ClientConnectionFactoryImpl;
import org.jboss.messaging.core.Binding;
import org.jboss.messaging.core.MessagingServer;
import org.jboss.messaging.core.MessagingServerManagement;
import org.jboss.messaging.core.Queue;
import org.jboss.messaging.util.MessageQueueNameHelper;

/**
 * This interface describes the properties and operations that comprise the management interface of the
 * Messaging Server.
 * 
 * It includes operations to create and destroy queues and provides various statistics measures
 * such as message count for queues and topics.
 * 
 * @author <a href="mailto:ataylor@redhat.com>Andy Taylor</a>
 * @author <a href="mailto:ataylor@redhat.com>Andy Taylor</a>
 * 
 */
@JMX(name = "jboss.messaging:service=MessagingServerManagement", exposedInterface = MessagingServerManagement.class)
public class MessagingServerManagementImpl implements MessagingServerManagement
{
   private MessagingServer messagingServer;

   public void setMessagingServer(MessagingServer messagingServer)
   {
      this.messagingServer = messagingServer;
   }

//   public String getServerVersion()
//   {      
//      return messagingServer.getVersion().getProviderVersion();
//   }
//
//   public Configuration getConfiguration()
//   {
//      return messagingServer.getConfiguration();
//   }
//   
   public boolean isStarted()
   {
      return messagingServer.isStarted();
   }
   
   public void createQueue(String address, String name) throws Exception
   {
      messagingServer.createQueue(address, name);
   }

   public void destroyQueue(String name) throws Exception
   {
      messagingServer.destroyQueue(name);
   }

   public boolean addAddress(String address)
   {
      return messagingServer.addAddress(address);
   }

   public boolean removeAddress(String address)
   {
      return messagingServer.removeAddress(address);
   }

   public ClientConnectionFactory createClientConnectionFactory(boolean strictTck,int prefetchSize)
   {
      return new ClientConnectionFactoryImpl(messagingServer.getConfiguration().getMessagingServerID(),
                      messagingServer.getConfiguration().getRemotingConfiguration(), messagingServer.getVersion(), messagingServer.getConfiguration().isStrictTck() || strictTck, prefetchSize);
   }

   public void removeAllMessagesForAddress(String address) throws Exception
   {
      messagingServer.removeAllMessagesForAddress(address);
   }

   public void removeAllMessagesForBinding(String name) throws Exception
   {
      messagingServer.removeAllMessagesForBinding(name);
   }
   
   public int getMessageCountForQueue(String queue) throws Exception
   {
      return getQueue(queue).getMessageCount();
   }
  
   public List<SubscriptionInfo> listAllSubscriptionsForAddress(String address) throws Exception
   {
      return listSubscriptions(address, ListType.ALL);
   }

   public List<SubscriptionInfo> listDurableSubscriptionsForAddress(String address) throws Exception
   {
      return listSubscriptions(address, ListType.DURABLE);
   }

   public List<SubscriptionInfo> listNonSubscriptionsForAddress(String address) throws Exception
   {
      return listSubscriptions(address, ListType.NON_DURABLE);
   }

//
////   public int getDeliveringCountForQueue(String queue) throws Exception
////   {
////      return getQueue(queue).getDeliveringCount();
////   }
//
//   public int getScheduledMessageCountForQueue(String queue) throws Exception
//   {
//      return getQueue(queue).getScheduledCount();
//   }
//
////   public MessageCounter getMessageCounterForQueue(String queue) throws Exception
////   {
////      return getQueue(queue).getMessageCounter();
////   }
//
////   public MessageStatistics getMessageStatisticsForQueue(String queue) throws Exception
////   {
////      List counters = new ArrayList();
////      counters.add(getQueue(queue).getMessageCounter());
////
////      List stats = MessageCounter.getMessageStatistics(counters);
////
////      return (MessageStatistics)stats.get(0);
////   }
//
//   public int getConsumerCountForQueue(String queue) throws Exception
//   {
//      return getQueue(queue).getConsumerCount();
//   }
//
////   public void resetMessageCounterForQueue(String queue) throws Exception
////   {
////      getMessageCounterForQueue(queue).resetCounter();
////   }
////
////   public void resetMessageCounterHistoryForQueue(String queue) throws Exception
////   {
////      getMessageCounterForQueue(queue).resetHistory();
////   }
//
////   public List<Message> listAllMessagesForQueue(String queue) throws Exception
////   {
////      return getQueue(queue).listAllMessages(null);
////   }
//
//   public List<Message> listAllMessagesForQueue(String queue, String selector) throws Exception
//   {
//      return listAllMessages(getQueue(queue), selector);
//   }
//
//   public List<Message> listDurableMessagesForQueue(String queue) throws Exception
//   {
//      return listDurableMessages(getQueue(queue), null);
//   }
//
//   public List<Message> listDurableMessagesForQueue(String queue, String selector) throws Exception
//   {
//      return listDurableMessages(getQueue(queue), selector);
//   }
//
//   public List<Message> listNonDurableMessagesForQueue(String queue) throws Exception
//   {
//      return listNonDurableMessages(getQueue(queue), null);
//   }
//
//   public List<Message> listNonDurableMessagesForQueue(String queue, String selector) throws Exception
//   {
//      return listNonDurableMessages(getQueue(queue), selector);
//   }
//
////   public String listMessageCounterAsHTMLForQueue(String queue) throws Exception
////   {
////      return listMessageCounterAsHTML(new MessageCounter[] { getMessageCounterForQueue(queue) });
////   }
////
////   public String listMessageCounterHistoryAsHTMLForQueue(String queue) throws Exception
////   {
////      return listMessageCounterHistoryAsHTML(new MessageCounter[] {getMessageCounterForQueue(queue)});
////   }
//
//   public int getAllMessageCountForTopic(String topicName) throws Exception
//   {
//      return getAllMessageCountForTopic(topicName);
//   }
//
//   public int getDurableMessageCountForTopic(String topicName) throws Exception
//   {
//      return getDurableMessageCountForTopic(topicName);
//   }
//
//   public int getNonDurableMessageCountForTopic(String topicName) throws Exception
//   {
//      return getNonDurableMessageCountForTopic(topicName);
//   }
//
//   public int getAllSubscriptionsCountForTopic(String topicName) throws Exception
//   {
//      return getAllSubscriptionsCountForTopic(topicName);
//   }
//
//   public int getDurableSubscriptionsCountForTopic(String topicName) throws Exception
//   {
//      return getDurableSubscriptionsCountForTopic(topicName);
//   }
//
//   public int getNonDurableSubscriptionsCountForTopic(String topicName) throws Exception
//   {
//      return getNonDurableSubscriptionsCountForTopic(topicName);
//   }
//
//
//
//
//   public List<SubscriptionInfo> listDurableSubscriptionsForTopic(String topicName) throws Exception
//   {
//      return listDurableSubscriptions(topicName);
//   }
//
//   public List<SubscriptionInfo> listNonDurableSubscriptionsForTopic(String topicName) throws Exception
//   {
//      return listNonDurableSubscriptions(topicName);
//   }
//
//   public String listAllSubscriptionsAsHTMLForTopic(String topicName) throws Exception
//   {
//      return listAllSubscriptionsAsHTML(topicName);
//   }
//
//   public String listDurableSubscriptionsAsHTMLForTopic(String topicName) throws Exception
//   {
//      return listDurableSubscriptionsAsHTML(topicName);
//   }
//
//   public String listNonDurableSubscriptionsAsHTMLForTopic(String topicName) throws Exception
//   {
//      return listNonDurableSubscriptionsAsHTML(topicName);
//   }
//
////   public List<Message> listAllMessagesForSubscription(String subscriptionId) throws Exception
////   {
////      return listAllMessagesForSubscription(subscriptionId, null);
////   }
////
////   public List<Message> listAllMessagesForSubscription(String subscriptionId, String selector) throws Exception
////   {
////      return listAllMessages(subscriptionId, selector);
////   }
////
////   public List<Message> listDurableMessagesForSubscription(String subscriptionId) throws Exception
////   {
////      return listDurableMessages(subscriptionId, null);
////   }
////
////   public List<Message> listDurableMessagesForTopic(String topicName, String subscriptionId, String selector) throws Exception
////   {
////      return listDurableMessages(topicName, subscriptionId, selector);
////   }
////
////   public List<Message> listNonDurableMessagesForTopic(String topicName, String subscriptionId) throws Exception
////   {
////      return listNonDurableMessages(subscriptionId, null);
////   }
////
////   public List<Message> listNonDurableMessagesForTopic(String topicName, String subscriptionId, String selector) throws Exception
////   {
////      return listNonDurableMessages(subscriptionId, selector);
////   }
//
//   public List<MessageCounter> getMessageCountersForTopic(String topicName) throws Exception
//   {
//      return getMessageCounters(topicName);
//   }
//
//   public String showActiveClientsAsHTML() throws Exception
//   {
//      CharArrayWriter charArray = new CharArrayWriter();
//      PrintWriter out = new PrintWriter(charArray);
//
//      List endpoints = messagingServer.getConnectionManager().getActiveConnections();
//
//      out.println("<table><tr><td>ID</td><td>Host</td><td>User</td><td>#Sessions</td></tr>");
//      for (Iterator iter = endpoints.iterator(); iter.hasNext();)
//      {
//         ServerConnectionEndpoint endpoint = (ServerConnectionEndpoint) iter.next();
//
//         out.println("<tr>");
//         out.println("<td>" + endpoint.toString() + "</td>");
//         // FIXME display URI of client
//         out.println("<td>" + endpoint.getUsername() + "</td>");
//         out.println("<td>" + endpoint.getSessions().size() + "</td>");
//         out.println("</tr>");
//      }
//
//      out.println("</table>");
//
//
//      return charArray.toString();
//   }
//   
////   public String showPreparedTransactionsAsHTML()
////   {
////      List txs = messagingServer.getTxRepository().getPreparedTransactions();
////      JBossStringBuilder buffer = new JBossStringBuilder();
////      buffer.append("<table width=\"100%\" border=\"1\" cellpadding=\"1\" cellspacing=\"1\">");
////      buffer.append("<tr><th>Xid</th></tr>");
////      for (Iterator i = txs.iterator(); i.hasNext();)
////      {
////         Xid xid = (Xid) i.next();
////         if (xid != null)
////         {
////            buffer.append("<tr><td>");
////            buffer.append(xid);
////            buffer.append("</td></tr>");
////         }
////      }
////      buffer.append("</table>");
////      return buffer.toString();
////   }
//
////   public String listMessageCountersAsHTML() throws Exception
////   {
////      List counters = messagingServer.getMessageCounters();
////
////      Collections.sort(counters, new Comparator()
////      {
////         public int compare(Object o1, Object o2)
////         {
////            MessageCounter m1 = (MessageCounter) o1;
////            MessageCounter m2 = (MessageCounter) o2;
////            return m1.getDestinationName().compareTo(m2.getDestinationName());
////         }
////      });
////
////      String ret =
////              "<table width=\"100%\" border=\"1\" cellpadding=\"1\" cellspacing=\"1\">"
////                      + "<tr>"
////                      + "<th>Type</th>"
////                      + "<th>Name</th>"
////                      + "<th>Subscription</th>"
////                      + "<th>Durable</th>"
////                      + "<th>Count</th>"
////                      + "<th>CountDelta</th>"
////                      + "<th>Depth</th>"
////                      + "<th>DepthDelta</th>"
////                      + "<th>Last Add</th>"
////                      + "</tr>";
////
////      String strNameLast = null;
////      String strTypeLast = null;
////      String strDestLast = null;
////
////      String destData = "";
////      int destCount = 0;
////
////      int countTotal = 0;
////      int countDeltaTotal = 0;
////      int depthTotal = 0;
////      int depthDeltaTotal = 0;
////
////      int i = 0; // define outside of for statement, so variable
////      // still exists after for loop, because it is
////      // needed during output of last module data string
////
////      Iterator iter = counters.iterator();
////
////      while (iter.hasNext())
////      {
////         MessageCounter counter = (MessageCounter) iter.next();
////
////         // get counter data
////         StringTokenizer tokens = new StringTokenizer(counter.getCounterAsString(), ",");
////
////         String strType = tokens.nextToken();
////         String strName = tokens.nextToken();
////         String strSub = tokens.nextToken();
////         String strDurable = tokens.nextToken();
////
////         String strDest = strType + "-" + strName;
////
////         String strCount = tokens.nextToken();
////         String strCountDelta = tokens.nextToken();
////         String strDepth = tokens.nextToken();
////         String strDepthDelta = tokens.nextToken();
////         String strDate = tokens.nextToken();
////
////         // update total count / depth values
////         countTotal += Integer.parseInt(strCount);
////         depthTotal += Integer.parseInt(strDepth);
////
////         countDeltaTotal += Integer.parseInt(strCountDelta);
////         depthDeltaTotal += Integer.parseInt(strDepthDelta);
////
////         if (strCountDelta.equalsIgnoreCase("0"))
////            strCountDelta = "-"; // looks better
////
////         if (strDepthDelta.equalsIgnoreCase("0"))
////            strDepthDelta = "-"; // looks better
////
////         // output destination counter data as HTML table row
////         // ( for topics with multiple subscriptions output
////         //   type + name field as rowspans, looks better )
////         if (strDestLast != null && strDestLast.equals(strDest))
////         {
////            // still same destination -> append destination subscription data
////            destData += "<tr bgcolor=\"#" + ((i % 2) == 0 ? "FFFFFF" : "F0F0F0") + "\">";
////            destCount += 1;
////         }
////         else
////         {
////            // startnew destination data
////            if (strDestLast != null)
////            {
////               // store last destination data string
////               ret += "<tr bgcolor=\"#"
////                       + ((i % 2) == 0 ? "FFFFFF" : "F0F0F0")
////                       + "\"><td rowspan=\""
////                       + destCount
////                       + "\">"
////                       + strTypeLast
////                       + "</td><td rowspan=\""
////                       + destCount
////                       + "\">"
////                       + strNameLast
////                       + "</td>"
////                       + destData;
////
////               destData = "";
////            }
////
////            destCount = 1;
////         }
////
////         // counter data row
////         destData += "<td>"
////                 + strSub
////                 + "</td>"
////                 + "<td>"
////                 + strDurable
////                 + "</td>"
////                 + "<td>"
////                 + strCount
////                 + "</td>"
////                 + "<td>"
////                 + strCountDelta
////                 + "</td>"
////                 + "<td>"
////                 + strDepth
////                 + "</td>"
////                 + "<td>"
////                 + strDepthDelta
////                 + "</td>"
////                 + "<td>"
////                 + strDate
////                 + "</td>";
////
////         // store current destination data for change detection
////         strTypeLast = strType;
////         strNameLast = strName;
////         strDestLast = strDest;
////      }
////
////      if (strDestLast != null)
////      {
////         // store last module data string
////         ret += "<tr bgcolor=\"#"
////                 + ((i % 2) == 0 ? "FFFFFF" : "F0F0F0")
////                 + "\"><td rowspan=\""
////                 + destCount
////                 + "\">"
////                 + strTypeLast
////                 + "</td><td rowspan=\""
////                 + destCount
////                 + "\">"
////                 + strNameLast
////                 + "</td>"
////                 + destData;
////      }
////
////      // append summation info
////      ret += "<tr>"
////              + "<td><![CDATA[ ]]></td><td><![CDATA[ ]]></td>"
////              + "<td><![CDATA[ ]]></td><td><![CDATA[ ]]></td><td>"
////              + countTotal
////              + "</td><td>"
////              + (countDeltaTotal == 0 ? "-" : Integer.toString(countDeltaTotal))
////              + "</td><td>"
////              + depthTotal
////              + "</td><td>"
////              + (depthDeltaTotal == 0 ? "-" : Integer.toString(depthDeltaTotal))
////              + "</td><td>Total</td></tr></table>";
////
////      return ret;
////   }
//
//
//
//   public List<Message> listAllMessages(Queue queue, String selector) throws Exception
//   {
//      return listMessages(queue, ListType.ALL, selector);
//   }
//   
//   public List<Message> listDurableMessages(Queue queue, String selector) throws Exception
//   {
//      return listMessages(queue, ListType.DURABLE, selector);
//   }
//   
//   public List<Message> listNonDurableMessages(Queue queue, String selector) throws Exception
//   {
//      return listMessages(queue, ListType.NON_DURABLE, selector);
//   }
//
//   
//   public List<SubscriptionInfo> listDurableSubscriptions(String topicName) throws Exception
//   {
//      return listSubscriptions(topicName, ListType.DURABLE);
//   }
//   
//   public List<SubscriptionInfo> listNonDurableSubscriptions(String topicName) throws Exception
//   {
//      return listSubscriptions(topicName, ListType.NON_DURABLE);
//   }
//   
//   public String listAllSubscriptionsAsHTML(String topicName) throws Exception
//   {
//      return listSubscriptionsAsHTML(topicName, ListType.ALL);
//   }
//   
//   public String listDurableSubscriptionsAsHTML(String topicName) throws Exception
//   {
//      return listSubscriptionsAsHTML(topicName, ListType.DURABLE);
//   }
//   
//   public String listNonDurableSubscriptionsAsHTML(String topicName) throws Exception
//   {
//      return listSubscriptionsAsHTML(topicName, ListType.NON_DURABLE);
//   }
//      
//   public List<Message> listDurableMessagesForSubscription(String subId, String selector) throws Exception
//   {
//      return listMessagesForSubscription(ListType.DURABLE, subId, selector);
//   }
//   
//   public List<Message> listNonDurableMessagesForSubscription(String subId, String selector) throws Exception
//   {
//      return listMessagesForSubscription(ListType.NON_DURABLE, subId, selector);
//   }
//   
//   public List<MessageCounter> getMessageCounters(String topicName) throws Exception
//   {
//      List<MessageCounter> counters = new ArrayList<MessageCounter>();
//      
//      Condition condition = new ConditionImpl(DestinationType.TOPIC, topicName);
//      
//      List<Binding> bindings = messagingServer.getPostOffice().getBindingsForCondition(condition);
//      
//      for (Binding binding: bindings)
//      {
//         Queue queue = binding.getQueue();
//         
//         //TODO - get message counters
//         
////         String counterName = SUBSCRIPTION_MESSAGECOUNTER_PREFIX + queue.getName();
////         
////         MessageCounter counter = messagingServer.getMessageCounterManager().getMessageCounter(counterName);
////         
////         if (counter == null)
////         {
////            throw new IllegalStateException("Cannot find counter with name " + counterName);
////         }
////         
////         counters.add(counter);
//      }
//      
//      return counters; 
//   }
//   
//   
////   public void setMessageCounterHistoryDayLimit(String topicName, int limit) throws Exception
////   {
////      Condition condition = new ConditionImpl(DestinationType.TOPIC, topicName);
////      
////      List<Binding> bindings = messagingServer.getPostOffice().getBindingsForCondition(condition);
////         
////      for (Binding binding: bindings)
////      {
////         Queue queue = binding.getQueue();
////         
////         queue.setMessageCounterHistoryDayLimit(limit);
////      }      
////   }
//   
//   // Private ---------------------------------------------------------------------------
//   
   private Queue getQueue(String queueName) throws Exception
   {
      Binding binding = messagingServer.getPostOffice().getBinding(queueName);
      if (binding == null)
      {
         throw new IllegalArgumentException("No queue with name " + queueName);
      }
      
      return binding.getQueue();
   }
//   
//    
//   
//   private List<Message> listMessages(Queue queue, ListType type, String jmsSelector) throws Exception
//   {                        
//      if (jmsSelector != null && "".equals(jmsSelector.trim()))
//      {
//         jmsSelector = null;
//      }
//      
//      Filter filter = null;
//            
//      if (jmsSelector != null)
//      {        
//         filter = new FilterImpl(SelectorTranslator.convertToJBMFilterString(jmsSelector));
//      }
//      
//      List<Message> msgs = new ArrayList<Message>();
//      
//      List<MessageReference> allRefs = queue.list(filter);
//        
//      for (MessageReference ref: allRefs)
//      {
//         Message msg = ref.getMessage();
//         
//         if (type == ListType.ALL ||
//            (type == ListType.DURABLE && msg.isDurable()) ||
//            (type == ListType.NON_DURABLE && !msg.isDurable()))
//         {
//            msgs.add(msg);
//         }
//      }
//      
//      return msgs;
//   }
//   
//   
//   private String listMessageCounterAsHTML(MessageCounter[] counters)
//   {
//      if (counters == null)
//         return null;
//
//      String ret = "<table width=\"100%\" border=\"1\" cellpadding=\"1\" cellspacing=\"1\">"  +
//                   "<tr>"                  +
//                   "<th>Type</th>"         +
//                   "<th>Name</th>"         +
//                   "<th>Subscription</th>" +
//                   "<th>Durable</th>"      +
//                   "<th>Count</th>"        +
//                   "<th>CountDelta</th>"   +
//                   "<th>Depth</th>"        +
//                   "<th>DepthDelta</th>"   +
//                   "<th>Last Add</th>"     +
//                   "</tr>";
//
//      for( int i=0; i<counters.length; i++ )
//      {
//         String            data = counters[i].getCounterAsString();
//         StringTokenizer token = new StringTokenizer( data, ",");
//         String            value;
//
//         ret += "<tr bgcolor=\"#" + ( (i%2)==0 ? "FFFFFF" : "F0F0F0") + "\">";
//
//         ret += "<td>" + token.nextToken() + "</td>"; // type
//         ret += "<td>" + token.nextToken() + "</td>"; // name
//         ret += "<td>" + token.nextToken() + "</td>"; // subscription
//         ret += "<td>" + token.nextToken() + "</td>"; // durable
//
//         ret += "<td>" + token.nextToken() + "</td>"; // count
//
//         value = token.nextToken(); // countDelta
//
//         if( value.equalsIgnoreCase("0") )
//             value = "-";
//
//         ret += "<td>" + value + "</td>";
//
//         ret += "<td>" + token.nextToken() + "</td>"; // depth
//
//         value = token.nextToken(); // depthDelta
//
//         if( value.equalsIgnoreCase("0") )
//             value = "-";
//
//         ret += "<td>" + value + "</td>";
//
//         ret += "<td>" + token.nextToken() + "</td>"; // date last add
//
//         ret += "</tr>";
//      }
//
//      ret += "</table>";
//
//      return ret;
//   }
//
//   private String listMessageCounterHistoryAsHTML(MessageCounter[] counters)
//   {
//      if (counters == null)
//         return null;
//
//      String           ret = "";
//
//      for( int i=0; i<counters.length; i++ )
//      {
//         // destination name
//         ret += ( counters[i].getDestinationTopic() ? "Topic '" : "Queue '" );
//         ret += counters[i].getDestinationName() + "'";
//
//         if( counters[i].getDestinationSubscription() != null )
//            ret += "Subscription '" + counters[i].getDestinationSubscription() + "'";
//
//
//         // table header
//         ret += "<table width=\"100%\" border=\"1\" cellpadding=\"1\" cellspacing=\"1\">"  +
//                "<tr>"                  +
//                "<th>Date</th>";
//
//         for( int j = 0; j < 24; j++ )
//            ret += "<th width=\"4%\">" + j + "</th>";
//
//         ret += "<th>Total</th></tr>";
//
//         // get history data as CSV string
//         StringTokenizer tokens = new StringTokenizer( counters[i].getHistoryAsString(), ",\n");
//
//         // get history day count
//         int days = Integer.parseInt( tokens.nextToken() );
//
//         for( int j=0; j<days; j++ )
//         {
//            // next day counter row
//            ret += "<tr bgcolor=\"#" + ((j%2)==0 ? "FFFFFF" : "F0F0F0") + "\">";
//
//            // date
//            ret += "<td>" + tokens.nextToken() + "</td>";
//
//            // 24 hour counters
//            int total = 0;
//
//            for( int k=0; k<24; k++ )
//            {
//               int value = Integer.parseInt( tokens.nextToken().trim() );
//
//               if( value == -1 )
//               {
//                    ret += "<td></td>";
//               }
//               else
//               {
//                    ret += "<td>" + value + "</td>";
//
//                    total += value;
//               }
//            }
//
//            ret += "<td>" + total + "</td></tr>";
//         }
//
//         ret += "</table><br><br>";
//      }
//
//      return ret;
//   }
//   
   private enum ListType
   {
      ALL, DURABLE, NON_DURABLE
   }
//   
//  
//   private List<Message> listMessagesForSubscription(ListType type, String subId, String jmsSelector) throws Exception
//   { 
//      List<Message> msgs = new ArrayList<Message>();
//      
//      if (subId == null || "".equals(subId.trim()))
//      {
//         return msgs;
//      }
//      
//      List<Binding> bindings = messagingServer.getPostOffice().getBindingsForQueueName(subId);
//      
//      if (bindings.isEmpty())
//      {
//         throw new IllegalArgumentException("Cannot find subscription with id " + subId);
//      }
//      
//      
//      if (jmsSelector != null && "".equals(jmsSelector.trim()))
//      {
//         jmsSelector = null;
//      }
//      
//      Filter sel = null;
//               
//      if (jmsSelector != null)
//      {  
//         sel = new FilterImpl(SelectorTranslator.convertToJBMFilterString(jmsSelector));
//      }
//      
//      Binding binding = bindings.get(0);
//      
//      List<MessageReference> allRefs = binding.getQueue().list(sel);
//         
//      for (MessageReference ref: allRefs)
//      {
//         Message msg = ref.getMessage();
//     
//         if (type == ListType.ALL || (type == ListType.DURABLE && msg.isDurable()) || (type == ListType.NON_DURABLE && !msg.isDurable()))
//         {
//            msgs.add(msg);
//         }
//      }
//      
//      return msgs;
//   }
//   
   private List<SubscriptionInfo> listSubscriptions(String address, ListType type) throws Exception
   {      
      List<SubscriptionInfo> subs = new ArrayList<SubscriptionInfo>();
      
      List<Binding> bindings = messagingServer.getPostOffice().getBindingsForAddress(address);
      
      for (Binding binding: bindings)
      {
         Queue queue = binding.getQueue();
         
         if (type == ListType.ALL || (type == ListType.DURABLE && queue.isDurable()) || (type == ListType.NON_DURABLE && !queue.isDurable()))
         {         
            String subName = null;
            String clientID = null;
            
            if (queue.isDurable())
            {
               MessageQueueNameHelper helper = MessageQueueNameHelper.createHelper(queue.getName());
               subName = helper.getSubName();
               clientID = helper.getClientId();
            }
            
            SubscriptionInfo info = new SubscriptionInfo(queue.getName(), queue.isDurable(), subName, clientID,
                     queue.getFilter() == null ? null : queue.getFilter().getFilterString(), queue.getMessageCount(), queue.getMaxSize());
            
            subs.add(info);
         }
      }
      
      return subs;
   }
//   
//   private int getMessageCount(String topicName, ListType type) throws Exception
//   {
//      Condition condition = new ConditionImpl(DestinationType.TOPIC, topicName);
//      
//      List<Binding> bindings = messagingServer.getPostOffice().getBindingsForCondition(condition);
//      
//      int count = 0;
//      
//      for (Binding binding: bindings)
//      {
//         Queue queue = binding.getQueue();
//         
//         if (type == ListType.ALL || (type == ListType.DURABLE && queue.isDurable())
//             || (type == ListType.NON_DURABLE && !queue.isDurable()))
//         {            
//            count += queue.getMessageCount();
//         }
//      }
//
//      return count;
//   }  
//   
//   private int getSubscriptionsCount(String topicName, boolean durable) throws Exception
//   {
//      Condition condition = new ConditionImpl(DestinationType.TOPIC, topicName);
//      
//      List<Binding> bindings = messagingServer.getPostOffice().getBindingsForCondition(condition);
//      
//      int count = 0;
//      
//      for (Binding binding: bindings)
//      {
//         Queue queue = binding.getQueue();
//                  
//         if ((queue.isDurable() && durable) || (!queue.isDurable() && !durable))
//         {
//            count++;
//         }
//      }
//
//      return count;
//   }
//   
//   
//   private String listSubscriptionsAsHTML(String topicName, ListType type) throws Exception
//   {
//      Condition condition = new ConditionImpl(DestinationType.TOPIC, topicName);
//      
//      List<Binding> bindings = messagingServer.getPostOffice().getBindingsForCondition(condition);
//      
//      StringBuffer sb = new StringBuffer();
//      
//      sb.append("<table width=\"100%\" border=\"1\" cellpadding=\"1\" cellspacing=\"1\">"  +
//                  "<tr>"                  +
//                  "<th>Id</th>"         +
//                  "<th>Durable</th>" +
//                  "<th>Subscription Name</th>"      +
//                  "<th>Client ID</th>"        +
//                  "<th>Selector</th>"   +
//                  "<th>Message Count</th>"        +
//                  "<th>Max Size</th>"   +
//                  "</tr>");
//      
//      for (Binding binding: bindings)
//      {
//         Queue queue = binding.getQueue();
//
//         if (type == ListType.ALL || (type == ListType.DURABLE && queue.isDurable())
//                  || (type == ListType.NON_DURABLE && !queue.isDurable()))
//         {            
//            String filterString = queue.getFilter() != null ? queue.getFilter().getFilterString() : null;
//                     
//            String subName = null;
//            String clientID = null;
//            
//            if (queue.isDurable())
//            {
//               MessageQueueNameHelper helper = MessageQueueNameHelper.createHelper(queue.getName());
//               subName = helper.getSubName();
//               clientID = helper.getClientId();
//            }
//            
//            sb.append("<tr><td>").append(queue.getName()).append("</td>");
//            sb.append("<td>").append(queue.isDurable() ? "Durable" : "Non Durable").append("</td>");
//            sb.append("<td>").append(subName != null ? subName : "").append("</td>");
//            sb.append("<td>").append(clientID != null ? clientID : "").append("</td>");
//            sb.append("<td>").append(filterString != null ? filterString : "").append("</td>");
//            sb.append("<td>").append(queue.getMessageCount()).append("</td>");
//            sb.append("<td>").append(queue.getMaxSize()).append("</td>");
//            sb.append("</tr>");
//         }
//      }
//      sb.append("</table>");
//      
//      return sb.toString();                                
//   }
   
   
}
