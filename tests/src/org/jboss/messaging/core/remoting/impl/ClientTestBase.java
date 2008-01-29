/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.messaging.core.remoting.impl;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.jboss.messaging.core.remoting.impl.mina.integration.test.TestSupport.MANY_MESSAGES;
import static org.jboss.messaging.core.remoting.impl.mina.integration.test.TestSupport.reverse;

import java.util.List;

import junit.framework.TestCase;

import org.jboss.messaging.core.remoting.Client;
import org.jboss.messaging.core.remoting.NIOConnector;
import org.jboss.messaging.core.remoting.PacketDispatcher;
import org.jboss.messaging.core.remoting.PacketSender;
import org.jboss.messaging.core.remoting.ServerLocator;
import org.jboss.messaging.core.remoting.impl.mina.integration.test.ReversePacketHandler;
import org.jboss.messaging.core.remoting.test.unit.TestPacketHandler;
import org.jboss.messaging.core.remoting.wireformat.AbstractPacket;
import org.jboss.messaging.core.remoting.wireformat.TextPacket;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>.
 * 
 * @version <tt>$Revision$</tt>
 */
public abstract class ClientTestBase extends TestCase
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   protected Client client;
 
   protected ReversePacketHandler serverPacketHandler;

   protected PacketDispatcher serverDispatcher;

   private NIOConnector connector;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testConnected() throws Exception
   {
      NIOConnector connector = createNIOConnector();
      Client client = new ClientImpl(connector, createServerLocator());
      
      assertFalse(client.isConnected());

      client.connect();
      assertTrue(client.isConnected());

      assertTrue(client.disconnect());
      assertFalse(client.isConnected());
      assertFalse(client.disconnect());
      
      connector.disconnect();
   }    
      
   public void testSendOneWay() throws Exception
   {
      serverPacketHandler.expectMessage(1);

      TextPacket packet = new TextPacket("testSendOneWay");
      packet.setTargetID(serverPacketHandler.getID());
      client.send(packet, true);

      assertTrue(serverPacketHandler.await(2, SECONDS));

      List<TextPacket> messages = serverPacketHandler.getPackets();
      assertEquals(1, messages.size());
      String response = ((TextPacket) messages.get(0)).getText();
      assertEquals(packet.getText(), response);
   }

   public void testSendManyOneWay() throws Exception
   {
      serverPacketHandler.expectMessage(MANY_MESSAGES);

      TextPacket[] packets = new TextPacket[MANY_MESSAGES];
      for (int i = 0; i < MANY_MESSAGES; i++)
      {
         packets[i] = new TextPacket("testSendManyOneWay " + i);
         packets[i].setTargetID(serverPacketHandler.getID());
         client.send(packets[i], true);
      }

      assertTrue(serverPacketHandler.await(10, SECONDS));

      List<TextPacket> receivedPackets = serverPacketHandler.getPackets();
      assertEquals(MANY_MESSAGES, receivedPackets.size());
      for (int i = 0; i < MANY_MESSAGES; i++)
      {
         TextPacket receivedPacket = (TextPacket) receivedPackets.get(i);
         assertEquals(packets[i].getText(), receivedPacket.getText());
      }
   }

   public void testSendOneWayWithCallbackHandler() throws Exception
   {
      TestPacketHandler callbackHandler = new TestPacketHandler();
      callbackHandler.expectMessage(1);

      PacketDispatcher.client.register(callbackHandler);

      TextPacket packet = new TextPacket("testSendOneWayWithCallbackHandler");
      packet.setTargetID(serverPacketHandler.getID());
      packet.setCallbackID(callbackHandler.getID());

      client.send(packet, true);

      assertTrue(callbackHandler.await(5, SECONDS));

      assertEquals(1, callbackHandler.getPackets().size());
      String response = callbackHandler.getPackets().get(0).getText();
      assertEquals(reverse(packet.getText()), response);
   }

   public void testSendBlocking() throws Exception
   {
      TextPacket request = new TextPacket("testSendBlocking");
      request.setTargetID(serverPacketHandler.getID());

      AbstractPacket receivedPacket = client.send(request, false);

      assertNotNull(receivedPacket);
      assertTrue(receivedPacket instanceof TextPacket);
      TextPacket response = (TextPacket) receivedPacket;
      assertEquals(reverse(request.getText()), response.getText());
   }
   
   public void testCorrelationCounter() throws Exception
   {
      TextPacket request = new TextPacket("testSendBlocking");
      request.setTargetID(serverPacketHandler.getID());

      AbstractPacket receivedPacket = client.send(request, false);
      long correlationID = request.getCorrelationID();
      
      assertNotNull(receivedPacket);      
      assertEquals(request.getCorrelationID(), receivedPacket.getCorrelationID());
      
      receivedPacket = client.send(request, false);
      assertEquals(correlationID + 1, request.getCorrelationID());
      assertEquals(correlationID + 1, receivedPacket.getCorrelationID());      
   }

   public void testClientHandlePacketSentByServer() throws Exception
   {
      TestPacketHandler clientHandler = new TestPacketHandler();
      PacketDispatcher.client.register(clientHandler);

      serverPacketHandler.expectMessage(1);
      clientHandler.expectMessage(1);

      TextPacket packet = new TextPacket(
            "testClientHandlePacketSentByServer from client");
      packet.setTargetID(serverPacketHandler.getID());
      // send a packet to create a sender when the server
      // handles the packet
      client.send(packet, true);

      assertTrue(serverPacketHandler.await(2, SECONDS));

      assertNotNull(serverPacketHandler.getLastSender());
      PacketSender sender = serverPacketHandler.getLastSender();
      TextPacket packetFromServer = new TextPacket(
            "testClientHandlePacketSentByServer from server");
      packetFromServer.setTargetID(clientHandler.getID());
      sender.send(packetFromServer);

      assertTrue(clientHandler.await(2, SECONDS));

      List<TextPacket> packets = clientHandler.getPackets();
      assertEquals(1, packets.size());
      TextPacket packetReceivedByClient = (TextPacket) packets.get(0);
      assertEquals(packetFromServer.getText(), packetReceivedByClient.getText());
   }
   
   // Protected -----------------------------------------------------

   @Override
   protected void setUp() throws Exception
   {
      serverDispatcher = startServer();
      
      ServerLocator serverLocator = createServerLocator();
      connector = createNIOConnector();
      client = new ClientImpl(connector, serverLocator);
      client.connect();
      
      serverPacketHandler = new ReversePacketHandler();
      serverDispatcher.register(serverPacketHandler);
   }

   @Override
   protected void tearDown() throws Exception
   {
      serverDispatcher.unregister(serverPacketHandler.getID());

      connector.disconnect();
      client.disconnect();
      stopServer();
      
      client = null;
      serverDispatcher = null;
   }
   
   protected abstract ServerLocator createServerLocator();
   
   protected abstract NIOConnector createNIOConnector();

   protected abstract PacketDispatcher startServer() throws Exception;
   
   protected abstract void stopServer();
}
