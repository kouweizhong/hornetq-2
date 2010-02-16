/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hornetq.tests.integration.stomp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import junit.framework.Assert;

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.protocol.stomp.Stomp;
import org.hornetq.core.remoting.impl.invm.InVMAcceptorFactory;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServers;
import org.hornetq.integration.transports.netty.NettyAcceptorFactory;
import org.hornetq.integration.transports.netty.TransportConstants;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.hornetq.jms.server.JMSServerManager;
import org.hornetq.jms.server.config.JMSConfiguration;
import org.hornetq.jms.server.config.impl.JMSConfigurationImpl;
import org.hornetq.jms.server.config.impl.JMSQueueConfigurationImpl;
import org.hornetq.jms.server.config.impl.TopicConfigurationImpl;
import org.hornetq.jms.server.impl.JMSServerManagerImpl;
import org.hornetq.spi.core.protocol.ProtocolType;
import org.hornetq.tests.util.UnitTestCase;

public class StompTest extends UnitTestCase {
    private static final transient Logger log = Logger.getLogger(StompTest.class);
    private int port = 61613;
    private Socket stompSocket;
    private ByteArrayOutputStream inputBuffer;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private Queue queue;
    private Topic topic;
    private JMSServerManager server;

    public void testConnect() throws Exception {

       String connect_frame = "CONNECT\n" + "login: brianm\n" + "passcode: wombats\n" + "request-id: 1\n" + "\n" + Stomp.NULL;
       sendFrame(connect_frame);

        String f = receiveFrame(10000);
        Assert.assertTrue(f.startsWith("CONNECTED"));
        Assert.assertTrue(f.indexOf("response-id:1") >= 0);
    }
    
    public void testDisconnectAndError() throws Exception {

       String connectFrame = "CONNECT\n" + "login: brianm\n" + "passcode: wombats\n" + "request-id: 1\n" + "\n" + Stomp.NULL;
       sendFrame(connectFrame);

       String f = receiveFrame(10000);
       Assert.assertTrue(f.startsWith("CONNECTED"));
       Assert.assertTrue(f.indexOf("response-id:1") >= 0);
       
       String disconnectFrame = "DISCONNECT\n\n" + Stomp.NULL;
       sendFrame(disconnectFrame);
       
       waitForFrameToTakeEffect();
       
       // sending a message will result in an error
       String frame =
          "SEND\n" +
                  "destination:" + getQueuePrefix() + getQueueName() + "\n\n" +
                  "Hello World" +
                  Stomp.NULL;
       try {
          sendFrame(frame);
          Assert.fail("the socket must have been closed when the server handled the DISCONNECT");
       } catch (IOException e)
       {
       }
   }


    public void testSendMessage() throws Exception {

        MessageConsumer consumer = session.createConsumer(queue);

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SEND\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n\n" +
                        "Hello World" +
                        Stomp.NULL;

        sendFrame(frame);
        
        TextMessage message = (TextMessage) consumer.receive(1000);
        Assert.assertNotNull(message);
        Assert.assertEquals("Hello World", message.getText());

        // Make sure that the timestamp is valid - should
        // be very close to the current time.
        long tnow = System.currentTimeMillis();
        long tmsg = message.getJMSTimestamp();
        Assert.assertTrue(Math.abs(tnow - tmsg) < 1000);
    }
    
    public void testSendMessageWithReceipt() throws Exception {

       MessageConsumer consumer = session.createConsumer(queue);

       String frame =
               "CONNECT\n" +
                       "login: brianm\n" +
                       "passcode: wombats\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("CONNECTED"));

       frame =
               "SEND\n" +
                       "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                       "receipt: 1234\n\n" +
                       "Hello World" +
                       Stomp.NULL;

       sendFrame(frame);

       String f = receiveFrame(10000);
       Assert.assertTrue(f.startsWith("RECEIPT"));
       Assert.assertTrue(f.indexOf("receipt-id:1234") >= 0);

       TextMessage message = (TextMessage) consumer.receive(1000);
       Assert.assertNotNull(message);
       Assert.assertEquals("Hello World", message.getText());

       // Make sure that the timestamp is valid - should
       // be very close to the current time.
       long tnow = System.currentTimeMillis();
       long tmsg = message.getJMSTimestamp();
       Assert.assertTrue(Math.abs(tnow - tmsg) < 1000);
   }
    
    public void testSendMessageWithContentLength() throws Exception {

       MessageConsumer consumer = session.createConsumer(queue);

       String frame =
               "CONNECT\n" +
                       "login: brianm\n" +
                       "passcode: wombats\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("CONNECTED"));

       byte[] data = new byte[] {1, 2, 3, 4};
        
       frame =
               "SEND\n" +
                       "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                       "content-length:" + data.length + "\n\n" +
                       new String(data) +
                       Stomp.NULL;

       sendFrame(frame);
       
       BytesMessage message = (BytesMessage) consumer.receive(1000);
       Assert.assertNotNull(message);
       assertEquals(data.length, message.getBodyLength());
       assertEquals(data[0], message.readByte());
       assertEquals(data[1], message.readByte());
       assertEquals(data[2], message.readByte());
       assertEquals(data[3], message.readByte());

       // Make sure that the timestamp is valid - should
       // be very close to the current time.
       long tnow = System.currentTimeMillis();
       long tmsg = message.getJMSTimestamp();
       Assert.assertTrue(Math.abs(tnow - tmsg) < 1000);
   }

    public void testJMSXGroupIdCanBeSet() throws Exception {

        MessageConsumer consumer = session.createConsumer(queue);

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SEND\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "JMSXGroupID: TEST\n\n" +
                        "Hello World" +
                        Stomp.NULL;

        sendFrame(frame);

        TextMessage message = (TextMessage) consumer.receive(1000);
        Assert.assertNotNull(message);
        // differ from StompConnect
        Assert.assertEquals("TEST", ((TextMessage) message).getStringProperty("JMSXGroupID"));
    }

    public void testSendMessageWithCustomHeadersAndSelector() throws Exception {

        MessageConsumer consumer = session.createConsumer(queue, "foo = 'abc'");

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SEND\n" +
                        "foo:abc\n" +
                        "bar:123\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n\n" +
                        "Hello World" +
                        Stomp.NULL;

        sendFrame(frame);

        TextMessage message = (TextMessage) consumer.receive(1000);
        Assert.assertNotNull(message);
        Assert.assertEquals("Hello World", message.getText());
        Assert.assertEquals("foo", "abc", message.getStringProperty("foo"));
        Assert.assertEquals("bar", "123", message.getStringProperty("bar"));
    }

    public void testSendMessageWithStandardHeaders() throws Exception {

        MessageConsumer consumer = session.createConsumer(queue);

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SEND\n" +
                        "correlation-id:c123\n" +
                        "persistent:true\n" +
                        "priority:3\n" +
                        "type:t345\n" +
                        "JMSXGroupID:abc\n" +
                        "foo:abc\n" +
                        "bar:123\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n\n" +
                        "Hello World" +
                        Stomp.NULL;

        sendFrame(frame);

        TextMessage message = (TextMessage) consumer.receive(1000);
        Assert.assertNotNull(message);
        Assert.assertEquals("Hello World", message.getText());
        Assert.assertEquals("JMSCorrelationID", "c123", message.getJMSCorrelationID());
        Assert.assertEquals("getJMSType", "t345", message.getJMSType());
        Assert.assertEquals("getJMSPriority", 3, message.getJMSPriority());
        Assert.assertEquals(DeliveryMode.PERSISTENT, message.getJMSDeliveryMode());
        Assert.assertEquals("foo", "abc", message.getStringProperty("foo"));
        Assert.assertEquals("bar", "123", message.getStringProperty("bar"));

        Assert.assertEquals("JMSXGroupID", "abc", message.getStringProperty("JMSXGroupID"));
        // FIXME do we support it?
        //Assert.assertEquals("GroupID", "abc", amqMessage.getGroupID());
    }

    public void testSubscribeWithAutoAck() throws Exception {

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame = receiveFrame(100000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SUBSCRIBE\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "ack:auto\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        sendMessage(getName());

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("MESSAGE"));
        Assert.assertTrue(frame.indexOf("destination:") > 0);
        Assert.assertTrue(frame.indexOf(getName()) > 0);

        frame =
                "DISCONNECT\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);
        
        // message should not be received as it was auto-acked
        MessageConsumer consumer = session.createConsumer(queue);
        TextMessage message = (TextMessage) consumer.receive(1000);
        Assert.assertNull(message);

    }

    public void testSubscribeWithAutoAckAndBytesMessage() throws Exception {

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame = receiveFrame(100000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SUBSCRIBE\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "ack:auto\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        byte[] payload = new byte[]{1, 2, 3, 4, 5}; 
        sendBytesMessage(payload);

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("MESSAGE"));

        Pattern cl = Pattern.compile("Content-length:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher cl_matcher = cl.matcher(frame);
        Assert.assertTrue(cl_matcher.find());
        Assert.assertEquals("5", cl_matcher.group(1));

        Assert.assertFalse(Pattern.compile("type:\\s*null", Pattern.CASE_INSENSITIVE).matcher(frame).find());
        Assert.assertTrue(frame.indexOf(new String(payload)) > -1);
        
        frame =
                "DISCONNECT\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);
    }

    public void testSubscribeWithMessageSentWithProperties() throws Exception {

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame = receiveFrame(100000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SUBSCRIBE\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "ack:auto\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        MessageProducer producer = session.createProducer(queue);
        TextMessage message = session.createTextMessage("Hello World");
        message.setStringProperty("S", "value");
        message.setBooleanProperty("n", false);
        message.setByteProperty("byte", (byte) 9);
        message.setDoubleProperty("d", 2.0);
        message.setFloatProperty("f", (float) 6.0);
        message.setIntProperty("i", 10);
        message.setLongProperty("l", 121);
        message.setShortProperty("s", (short) 12);
        producer.send(message);

        frame = receiveFrame(10000);
        Assert.assertNotNull(frame);
        Assert.assertTrue(frame.startsWith("MESSAGE"));
        Assert.assertTrue(frame.indexOf("S:") > 0);
        Assert.assertTrue(frame.indexOf("n:") > 0);
        Assert.assertTrue(frame.indexOf("byte:") > 0);
        Assert.assertTrue(frame.indexOf("d:") > 0);
        Assert.assertTrue(frame.indexOf("f:") > 0);
        Assert.assertTrue(frame.indexOf("i:") > 0);
        Assert.assertTrue(frame.indexOf("l:") > 0);
        Assert.assertTrue(frame.indexOf("s:") > 0);
        Assert.assertTrue(frame.indexOf("Hello World") > 0);

//        System.out.println("out: "+frame);

        frame =
                "DISCONNECT\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);
    }
    
    public void testSubscribeWithID() throws Exception {

       String frame =
               "CONNECT\n" +
                       "login: brianm\n" +
                       "passcode: wombats\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       frame = receiveFrame(100000);
       Assert.assertTrue(frame.startsWith("CONNECTED"));

       frame =
               "SUBSCRIBE\n" +
                       "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                       "ack:auto\n" +
                       "id: mysubid\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       sendMessage(getName());

       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("MESSAGE"));
       Assert.assertTrue(frame.indexOf("destination:") > 0);
       Assert.assertTrue(frame.indexOf("subscription:") > 0);
       Assert.assertTrue(frame.indexOf(getName()) > 0);

       frame =
               "DISCONNECT\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);
   }

    public void testMessagesAreInOrder() throws Exception {
        int ctr = 10;
        String[] data = new String[ctr];

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame = receiveFrame(100000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SUBSCRIBE\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "ack:auto\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        for (int i = 0; i < ctr; ++i) {
            data[i] = getName() + i;
            sendMessage(data[i]);
        }

        for (int i = 0; i < ctr; ++i) {
            frame = receiveFrame(1000);
            Assert.assertTrue("Message not in order", frame.indexOf(data[i]) >= 0);
        }

        // sleep a while before publishing another set of messages
        waitForFrameToTakeEffect();

        for (int i = 0; i < ctr; ++i) {
            data[i] = getName() + ":second:" + i;
            sendMessage(data[i]);
        }

        for (int i = 0; i < ctr; ++i) {
            frame = receiveFrame(1000);
            Assert.assertTrue("Message not in order", frame.indexOf(data[i]) >= 0);
        }

        frame =
                "DISCONNECT\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);
    }

    public void testSubscribeWithAutoAckAndSelector() throws Exception {

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame = receiveFrame(100000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SUBSCRIBE\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "selector: foo = 'zzz'\n" +
                        "ack:auto\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        sendMessage("Ignored message", "foo", "1234");
        sendMessage("Real message", "foo", "zzz");

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("MESSAGE"));
        Assert.assertTrue("Should have received the real message but got: " + frame, frame.indexOf("Real message") > 0);

        frame =
                "DISCONNECT\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);
    }

    public void testSubscribeWithClientAck() throws Exception {

       String frame =
               "CONNECT\n" +
                       "login: brianm\n" +
                       "passcode: wombats\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("CONNECTED"));

       frame =
               "SUBSCRIBE\n" +
                       "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                       "ack:client\n\n" +
                       Stomp.NULL;

       sendFrame(frame);
       
       sendMessage(getName());
       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("MESSAGE"));
       Pattern cl = Pattern.compile("message-id:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
       Matcher cl_matcher = cl.matcher(frame);
       Assert.assertTrue(cl_matcher.find());
       String messageID = cl_matcher.group(1);

       frame =
          "ACK\n" +
                  "message-id: " + messageID + "\n\n" +
                  Stomp.NULL;
       sendFrame(frame);

       frame =
               "DISCONNECT\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       // message should not be received since message was acknowledged by the client
       MessageConsumer consumer = session.createConsumer(queue);
       TextMessage message = (TextMessage) consumer.receive(1000);
       Assert.assertNull(message);
   }
    
    public void testRedeliveryWithClientAck() throws Exception {

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SUBSCRIBE\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "ack:client\n\n" +
                        Stomp.NULL;

        sendFrame(frame);
        
        sendMessage(getName());
        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("MESSAGE"));

        frame =
                "DISCONNECT\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        // message should be received since message was not acknowledged
        MessageConsumer consumer = session.createConsumer(queue);
        TextMessage message = (TextMessage) consumer.receive(1000);
        Assert.assertNotNull(message);
        Assert.assertTrue(message.getJMSRedelivered());
    }
    
    public void testSubscribeWithClientAckThenConsumingAgainWithAutoAckWithNoDisconnectFrame() throws Exception {
        assertSubscribeWithClientAckThenConsumeWithAutoAck(false);
    }

    public void testSubscribeWithClientAckThenConsumingAgainWithAutoAckWithExplicitDisconnect() throws Exception {
        assertSubscribeWithClientAckThenConsumeWithAutoAck(true);
    }

    protected void assertSubscribeWithClientAckThenConsumeWithAutoAck(boolean sendDisconnect) throws Exception {

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SUBSCRIBE\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "ack:client\n\n" +
                        Stomp.NULL;

        sendFrame(frame);
        sendMessage(getName());

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("MESSAGE"));

        log.info("Reconnecting!");
        
        if (sendDisconnect) {
            frame =
                    "DISCONNECT\n" +
                            "\n\n" +
                            Stomp.NULL;
            sendFrame(frame);
            waitForFrameToTakeEffect();
            reconnect();
        }
        else {
            reconnect(1000);
            waitForFrameToTakeEffect();
        }


        // message should be received since message was not acknowledged
        frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SUBSCRIBE\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n\n" +
                        Stomp.NULL;

        sendFrame(frame);

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("MESSAGE"));

        frame =
                "DISCONNECT\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);
        waitForFrameToTakeEffect();
        
        // now lets make sure we don't see the message again
        reconnect();

        frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SUBSCRIBE\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "receipt: 1234\n\n" +
                        Stomp.NULL;

        sendFrame(frame);
        // wait for SUBSCRIBE's receipt
        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("RECEIPT"));

        sendMessage("shouldBeNextMessage");

        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("MESSAGE"));
        System.out.println(frame);
        Assert.assertTrue(frame.contains("shouldBeNextMessage"));
    }

    public void testUnsubscribe() throws Exception {

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);
        frame = receiveFrame(100000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SUBSCRIBE\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "ack:auto\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        //send a message to our queue
        sendMessage("first message");

        //receive message from socket
        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("MESSAGE"));

        //remove suscription
        frame =
                "UNSUBSCRIBE\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "receipt:567\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);
        waitForReceipt();

        //send a message to our queue
        sendMessage("second message");

        try {
            frame = receiveFrame(1000);
            log.info("Received frame: " + frame);
            Assert.fail("No message should have been received since subscription was removed");
        }
        catch (SocketTimeoutException e) {

        }
    }

    public void testUnsubscribeWithID() throws Exception {

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);
        frame = receiveFrame(100000);
        Assert.assertTrue(frame.startsWith("CONNECTED"));

        frame =
                "SUBSCRIBE\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "id: mysubid\n" +
                        "ack:auto\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        //send a message to our queue
        sendMessage("first message");

        //receive message from socket
        frame = receiveFrame(10000);
        Assert.assertTrue(frame.startsWith("MESSAGE"));

        //remove suscription
        frame =
                "UNSUBSCRIBE\n" +
                        "id:mysubid\n" +
                        "receipt: 345\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);
        waitForReceipt();

        //send a message to our queue
        sendMessage("second message");

        try {
            frame = receiveFrame(1000);
            log.info("Received frame: " + frame);
            Assert.fail("No message should have been received since subscription was removed");
        }
        catch (SocketTimeoutException e) {

        }
    }

    public void testTransactionCommit() throws Exception {
        MessageConsumer consumer = session.createConsumer(queue);

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        String f = receiveFrame(1000);
        Assert.assertTrue(f.startsWith("CONNECTED"));

        frame =
                "BEGIN\n" +
                        "transaction: tx1\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame =
                "SEND\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "transaction: tx1\n" +
                        "receipt: 123\n" +
                        "\n\n" +
                        "Hello World" +
                        Stomp.NULL;
        sendFrame(frame);
        waitForReceipt();
        
        // check the message is not committed
        assertNull(consumer.receive(100));
        
        frame =
                "COMMIT\n" +
                        "transaction: tx1\n" +
                        "receipt:456\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);
        waitForReceipt();

        TextMessage message = (TextMessage) consumer.receive(1000);
        Assert.assertNotNull("Should have received a message", message);
    }
    
    public void testSuccessiveTransactionsWithSameID() throws Exception {
       MessageConsumer consumer = session.createConsumer(queue);

       String frame =
               "CONNECT\n" +
                       "login: brianm\n" +
                       "passcode: wombats\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       String f = receiveFrame(1000);
       Assert.assertTrue(f.startsWith("CONNECTED"));

       // first tx
       frame =
               "BEGIN\n" +
                       "transaction: tx1\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       frame =
               "SEND\n" +
                       "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                       "transaction: tx1\n" +
                       "\n\n" +
                       "Hello World" +
                       Stomp.NULL;
       sendFrame(frame);

       frame =
               "COMMIT\n" +
                       "transaction: tx1\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       TextMessage message = (TextMessage) consumer.receive(1000);
       Assert.assertNotNull("Should have received a message", message);

       // 2nd tx with same tx ID
       frame =
               "BEGIN\n" +
                       "transaction: tx1\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       frame =
               "SEND\n" +
                       "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                       "transaction: tx1\n" +
                       "\n\n" +
                       "Hello World" +
                       Stomp.NULL;
       sendFrame(frame);

       frame =
               "COMMIT\n" +
                       "transaction: tx1\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       message = (TextMessage) consumer.receive(1000);
       Assert.assertNotNull("Should have received a message", message);
}
    
    public void testBeginSameTransactionTwice() throws Exception {
       String frame =
               "CONNECT\n" +
                       "login: brianm\n" +
                       "passcode: wombats\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       String f = receiveFrame(1000);
       Assert.assertTrue(f.startsWith("CONNECTED"));

       frame =
               "BEGIN\n" +
                       "transaction: tx1\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       // begin the tx a 2nd time
       frame =
          "BEGIN\n" +
                  "transaction: tx1\n" +
                  "\n\n" +
                  Stomp.NULL;
       sendFrame(frame);

       f = receiveFrame(1000);
       Assert.assertTrue(f.startsWith("ERROR"));

   }

    public void testTransactionRollback() throws Exception {
        MessageConsumer consumer = session.createConsumer(queue);

        String frame =
                "CONNECT\n" +
                        "login: brianm\n" +
                        "passcode: wombats\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        String f = receiveFrame(1000);
        Assert.assertTrue(f.startsWith("CONNECTED"));

        frame =
                "BEGIN\n" +
                        "transaction: tx1\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame =
                "SEND\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "transaction: tx1\n" +
                        "\n" +
                        "first message" +
                        Stomp.NULL;
        sendFrame(frame);

        //rollback first message
        frame =
                "ABORT\n" +
                        "transaction: tx1\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame =
                "BEGIN\n" +
                        "transaction: tx1\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);

        frame =
                "SEND\n" +
                        "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                        "transaction: tx1\n" +
                        "\n" +
                        "second message" +
                        Stomp.NULL;
        sendFrame(frame);

        frame =
                "COMMIT\n" +
                        "transaction: tx1\n" +
                        "receipt:789\n" +
                        "\n\n" +
                        Stomp.NULL;
        sendFrame(frame);
        waitForReceipt();

        //only second msg should be received since first msg was rolled back
        TextMessage message = (TextMessage) consumer.receive(1000);
        Assert.assertNotNull(message);
        Assert.assertEquals("second message", message.getText().trim());
    }
    
    public void testSubscribeToTopic() throws Exception {

       String frame =
               "CONNECT\n" +
                       "login: brianm\n" +
                       "passcode: wombats\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       frame = receiveFrame(100000);
       Assert.assertTrue(frame.startsWith("CONNECTED"));

       frame =
               "SUBSCRIBE\n" +
                       "destination:" + getTopicPrefix() + getTopicName() + "\n" +
                       "receipt: 12\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);
       // wait for SUBSCRIBE's receipt
       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("RECEIPT"));

       sendMessage(getName(), topic);

       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("MESSAGE"));
       Assert.assertTrue(frame.indexOf("destination:") > 0);
       Assert.assertTrue(frame.indexOf(getName()) > 0);

       frame =
          "UNSUBSCRIBE\n" +
                  "destination:" + getTopicPrefix() + getTopicName() + "\n" +
                  "receipt: 1234\n" +
                  "\n\n" +
                  Stomp.NULL;
       sendFrame(frame);
       // wait for UNSUBSCRIBE's receipt
       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("RECEIPT"));
  
       sendMessage(getName(), topic);

       try {
          frame = receiveFrame(1000);
          log.info("Received frame: " + frame);
          Assert.fail("No message should have been received since subscription was removed");
      }
      catch (SocketTimeoutException e) {

      }
       
      frame =
               "DISCONNECT\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);
   }
    
    public void testDurableSubscriberWithReconnection() throws Exception {

       String connectFame =
               "CONNECT\n" +
                       "login: brianm\n" +
                       "passcode: wombats\n" +
                       "client-id: myclientid\n\n" +
                       Stomp.NULL;
       sendFrame(connectFame);

       String frame = receiveFrame(100000);
       Assert.assertTrue(frame.startsWith("CONNECTED"));

       String subscribeFrame =
               "SUBSCRIBE\n" +
                       "destination:" + getTopicPrefix() + getTopicName() + "\n" +
                       "receipt: 12\n" +
                       "durable-subscription-name: " + getName() + "\n" + 
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(subscribeFrame);
       // wait for SUBSCRIBE's receipt
       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("RECEIPT"));

       String disconnectFrame =
          "DISCONNECT\n" +
                  "\n\n" +
                  Stomp.NULL;
       sendFrame(disconnectFrame);
       stompSocket.close();
       
       // send the message when the durable subscriber is disconnected
       sendMessage(getName(), topic);
  

       reconnect(1000);
       sendFrame(connectFame);
       frame = receiveFrame(100000);
       Assert.assertTrue(frame.startsWith("CONNECTED"));
       
       sendFrame(subscribeFrame);
       // wait for SUBSCRIBE's receipt
       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("RECEIPT"));

       // we must have received the message 
       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("MESSAGE"));
       Assert.assertTrue(frame.indexOf("destination:") > 0);
       Assert.assertTrue(frame.indexOf(getName()) > 0);

       String unsubscribeFrame =
          "UNSUBSCRIBE\n" +
                  "destination:" + getTopicPrefix() + getTopicName() + "\n" +
                  "receipt: 1234\n" +
                  "\n\n" +
                  Stomp.NULL;
       sendFrame(unsubscribeFrame);
       // wait for UNSUBSCRIBE's receipt
       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("RECEIPT"));
       
       sendFrame(disconnectFrame);
   }
    
    public void testDurableSubscriber() throws Exception {

       String frame =
               "CONNECT\n" +
                       "login: brianm\n" +
                       "passcode: wombats\n" +
                       "client-id: myclientid\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       frame = receiveFrame(100000);
       Assert.assertTrue(frame.startsWith("CONNECTED"));

       String subscribeFrame =
               "SUBSCRIBE\n" +
                       "destination:" + getTopicPrefix() + getTopicName() + "\n" +
                       "receipt: 12\n" +
                       "durable-subscription-name: " + getName() + "\n" + 
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(subscribeFrame);
       // wait for SUBSCRIBE's receipt
       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("RECEIPT"));

       // creating a subscriber with the same durable-subscriber-name must fail
       sendFrame(subscribeFrame);
       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("ERROR"));
       
       frame =
               "DISCONNECT\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);
   }
    
    public void testSubscribeToTopicWithNoLocal() throws Exception {

       String frame =
               "CONNECT\n" +
                       "login: brianm\n" +
                       "passcode: wombats\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       frame = receiveFrame(100000);
       Assert.assertTrue(frame.startsWith("CONNECTED"));

       frame =
               "SUBSCRIBE\n" +
                       "destination:" + getTopicPrefix() + getTopicName() + "\n" +
                       "receipt: 12\n" +
                       "no-local: true\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);
       // wait for SUBSCRIBE's receipt
       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("RECEIPT"));

       // send a message on the same connection => it should not be received
       frame = "SEND\n" +
          "destination:" + getTopicPrefix() + getTopicName() + "\n\n" +
                  "Hello World" +
                  Stomp.NULL;
       sendFrame(frame);
  
       try {
          frame = receiveFrame(2000);
          log.info("Received frame: " + frame);
          Assert.fail("No message should have been received since subscription is noLocal");
      }
      catch (SocketTimeoutException e) {
      }
      
      // send message on another JMS connection => it should be received
      sendMessage(getName(), topic);
      frame = receiveFrame(10000);
      Assert.assertTrue(frame.startsWith("MESSAGE"));
      Assert.assertTrue(frame.indexOf("destination:") > 0);
      Assert.assertTrue(frame.indexOf(getName()) > 0);
      
      frame =
               "DISCONNECT\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);
   }
    
    public void testClientAckNotPartOfTransaction() throws Exception {

       String frame =
               "CONNECT\n" +
                       "login: brianm\n" +
                       "passcode: wombats\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       frame = receiveFrame(100000);
       Assert.assertTrue(frame.startsWith("CONNECTED"));

       frame =
               "SUBSCRIBE\n" +
                       "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                       "ack:client\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);

       sendMessage(getName());

       frame = receiveFrame(10000);
       Assert.assertTrue(frame.startsWith("MESSAGE"));
       Assert.assertTrue(frame.indexOf("destination:") > 0);
       Assert.assertTrue(frame.indexOf(getName()) > 0);
       Assert.assertTrue(frame.indexOf("message-id:") > 0);
       Pattern cl = Pattern.compile("message-id:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);
       Matcher cl_matcher = cl.matcher(frame);
       Assert.assertTrue(cl_matcher.find());
       String messageID = cl_matcher.group(1);

       frame =
          "BEGIN\n" +
                  "transaction: tx1\n" +
                  "\n\n" +
                  Stomp.NULL;
       sendFrame(frame);

       frame =
          "ACK\n" +
                  "message-id:" + messageID + "\n" +
                  "transaction: tx1\n" +
                  "\n" +
                  "second message" +
                  Stomp.NULL;
       sendFrame(frame);

       frame =
          "ABORT\n" +
                  "transaction: tx1\n" +
                  "\n\n" +
                  Stomp.NULL;
       sendFrame(frame);
  
       try {
           frame = receiveFrame(1000);
           log.info("Received frame: " + frame);
           Assert.fail("No message should have been received as the message was acked even though the transaction has been aborted");
       }
       catch (SocketTimeoutException e) {
       }
       
       frame =
          "UNSUBSCRIBE\n" +
                  "destination:" + getQueuePrefix() + getQueueName() + "\n" +
                  "\n\n" +
                  Stomp.NULL;
       sendFrame(frame);
       
      frame =
               "DISCONNECT\n" +
                       "\n\n" +
                       Stomp.NULL;
       sendFrame(frame);
   }
   
    // Implementation methods
    //-------------------------------------------------------------------------
    protected void setUp() throws Exception {
       super.setUp();
       
       server = createServer();
       server.start();
        connectionFactory = createConnectionFactory();

        stompSocket = createSocket();
        inputBuffer = new ByteArrayOutputStream();

        connection = connectionFactory.createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        queue = session.createQueue(getQueueName());
        topic = session.createTopic(getTopicName());
        connection.start();
    }

    /**
    * @return
    * @throws Exception 
    */
   private JMSServerManager createServer() throws Exception
   {
      Configuration config = new ConfigurationImpl();
      config.setSecurityEnabled(false);
      config.setPersistenceEnabled(false);

      Map<String, Object> params = new HashMap<String, Object>();
      params.put(TransportConstants.PROTOCOL_PROP_NAME, ProtocolType.STOMP.toString());
      params.put(TransportConstants.PORT_PROP_NAME, TransportConstants.DEFAULT_STOMP_PORT);
      TransportConfiguration stompTransport = new TransportConfiguration(NettyAcceptorFactory.class.getName(), params);
      config.getAcceptorConfigurations().add(stompTransport);
      config.getAcceptorConfigurations().add(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
       HornetQServer hornetQServer = HornetQServers.newHornetQServer(config);
       
       JMSConfiguration jmsConfig = new JMSConfigurationImpl();
       jmsConfig.getQueueConfigurations().add(new JMSQueueConfigurationImpl(getQueueName(), null, false, getQueueName()));
       jmsConfig.getTopicConfigurations().add(new TopicConfigurationImpl(getTopicName(), getTopicName()));
       server = new JMSServerManagerImpl(hornetQServer, jmsConfig);
       server.setContext(null);
       return server;
   }

   protected void tearDown() throws Exception {
        connection.close();
        if (stompSocket != null) {
            stompSocket.close();
        }
        server.stop();
        
        super.tearDown();
    }

    protected void reconnect() throws Exception {
        reconnect(0);
    }
    protected void reconnect(long sleep) throws Exception {
        stompSocket.close();

        if (sleep > 0) {
            Thread.sleep(sleep);
        }

        stompSocket = createSocket();
        inputBuffer = new ByteArrayOutputStream();
    }

    protected ConnectionFactory createConnectionFactory() {
       return new HornetQConnectionFactory(new TransportConfiguration(InVMConnectorFactory.class.getName()));
    }

    protected Socket createSocket() throws IOException {
        return new Socket("127.0.0.1", port);
    }

    protected String getQueueName() {
        return "test";
    }

    protected String getQueuePrefix() {
       return "jms.queue.";
   }
    
    protected String getTopicName() {
       return "testtopic";
   }

    protected String getTopicPrefix() {
       return "jms.topic.";
   }

    public void sendFrame(String data) throws Exception {
        byte[] bytes = data.getBytes("UTF-8");
        OutputStream outputStream = stompSocket.getOutputStream();
        for (int i = 0; i < bytes.length; i++) {
            outputStream.write(bytes[i]);
        }
        outputStream.flush();
    }

    public String receiveFrame(long timeOut) throws Exception {
        stompSocket.setSoTimeout((int) timeOut);
        InputStream is = stompSocket.getInputStream();
        int c = 0;
        for (; ;) {
            c = is.read();
            if (c < 0) {
                throw new IOException("socket closed.");
            }
            else if (c == 0) {
                c = is.read();
                if (c != '\n')
                {
                   byte[] ba = inputBuffer.toByteArray();
                   System.out.println(new String(ba, "UTF-8"));
                }
                Assert.assertEquals("Expecting stomp frame to terminate with \0\n", c, '\n');
                byte[] ba = inputBuffer.toByteArray();
                inputBuffer.reset();
                return new String(ba, "UTF-8");
            }
            else {
                inputBuffer.write(c);
            }
        }
    }

    public void sendMessage(String msg) throws Exception {
       sendMessage(msg, "foo", "xyz", queue);
   }

    public void sendMessage(String msg, Destination destination) throws Exception {
        sendMessage(msg, "foo", "xyz", destination);
    }

    public void sendMessage(String msg, String propertyName, String propertyValue) throws JMSException {
       sendMessage(msg, propertyName, propertyValue, queue);
    }
    
    public void sendMessage(String msg, String propertyName, String propertyValue, Destination destination) throws JMSException {
        MessageProducer producer = session.createProducer(destination);
        TextMessage message = session.createTextMessage(msg);
        message.setStringProperty(propertyName, propertyValue);
        producer.send(message);
    }

    public void sendBytesMessage(byte[] msg) throws Exception {
        MessageProducer producer = session.createProducer(queue);
        BytesMessage message = session.createBytesMessage();
        message.writeBytes(msg);
        producer.send(message);
    }

    protected void waitForReceipt() throws Exception {
       String frame = receiveFrame(50000);
       assertNotNull(frame);
       assertTrue(frame.indexOf("RECEIPT") > -1);
   }
    
    protected void waitForFrameToTakeEffect() throws InterruptedException {
        // bit of a dirty hack :)
        // another option would be to force some kind of receipt to be returned
        // from the frame
        Thread.sleep(2000);
    }
}
