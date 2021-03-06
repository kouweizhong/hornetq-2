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

package org.hornetq.api.core.client;

import org.hornetq.api.core.HornetQException;
import org.hornetq.core.protocol.core.CoreRemotingConnection;


/**
 * A ClientSessionFactory is the entry point to create and configure HornetQ resources to produce and consume messages.
 * <br>
 * It is possible to configure a factory using the setter methods only if no session has been created.
 * Once a session is created, the configuration is fixed and any call to a setter method will throw a IllegalStateException.
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 */
public interface ClientSessionFactory
{
   /**
    * Creates a session with XA transaction semantics.
    * 
    * @return a ClientSession with XA transaction semantics
    * 
    * @throws HornetQException if an exception occurs while creating the session
    */
   ClientSession createXASession() throws HornetQException;

   /**
    * Creates a <em>transacted</em> session.
    * 
    * It is up to the client to commit when sending and acknowledging messages.

    * @return a transacted ClientSession
    * @throws HornetQException if an exception occurs while creating the session
    * 
    * @see ClientSession#commit()
    */
   ClientSession createTransactedSession() throws HornetQException;


   /**
    * Creates a <em>non-transacted</em> session.
    * Message sends and acknowledgements are automatically committed by the session. <em>This does not
    * mean that messages are automatically acknowledged</em>, only that when messages are acknowledged, 
    * the session will automatically commit the transaction containing the acknowledgements.

    * @return a non-transacted ClientSession
    * @throws HornetQException if an exception occurs while creating the session
    */
   ClientSession createSession() throws HornetQException;

   /**
    * Creates a session.
    * 
    * @param autoCommitSends <code>true</code> to automatically commit message sends, <code>false</code> to commit manually
    * @param autoCommitAcks <code>true</code> to automatically commit message acknowledgement, <code>false</code> to commit manually
    * @return a ClientSession
    * @throws HornetQException if an exception occurs while creating the session
    */
   ClientSession createSession(boolean autoCommitSends, boolean autoCommitAcks) throws HornetQException;

   /**
    * Creates a session.
    * 
    * @param autoCommitSends <code>true</code> to automatically commit message sends, <code>false</code> to commit manually
    * @param autoCommitAcks <code>true</code> to automatically commit message acknowledgement, <code>false</code> to commit manually
    * @param ackBatchSize the batch size of the acknowledgements
    * @return a ClientSession
    * @throws HornetQException if an exception occurs while creating the session
    */
   ClientSession createSession(boolean autoCommitSends, boolean autoCommitAcks, int ackBatchSize) throws HornetQException;

   /**
    * Creates a session.
    * 
    * @param xa whether the session support XA transaction semantic or not
    * @param autoCommitSends <code>true</code> to automatically commit message sends, <code>false</code> to commit manually
    * @param autoCommitAcks <code>true</code> to automatically commit message acknowledgement, <code>false</code> to commit manually
    * @return a ClientSession
    * @throws HornetQException if an exception occurs while creating the session
    */
   ClientSession createSession(boolean xa, boolean autoCommitSends, boolean autoCommitAcks) throws HornetQException;

   /**
    * Creates a session.
    * 
    * It is possible to <em>pre-acknowledge messages on the server</em> so that the client can avoid additional network trip
    * to the server to acknowledge messages. While this increase performance, this does not guarantee delivery (as messages
    * can be lost after being pre-acknowledged on the server). Use with caution if your application design permits it.
    * 
    * @param xa whether the session support XA transaction semantic or not
    * @param autoCommitSends <code>true</code> to automatically commit message sends, <code>false</code> to commit manually
    * @param autoCommitAcks <code>true</code> to automatically commit message acknowledgement, <code>false</code> to commit manually
    * @param preAcknowledge <code>true</code> to pre-acknowledge messages on the server, <code>false</code> to let the client acknowledge the messages
    * @return a ClientSession
    * @throws HornetQException if an exception occurs while creating the session
    */
   ClientSession createSession(boolean xa, boolean autoCommitSends, boolean autoCommitAcks, boolean preAcknowledge) throws HornetQException;

   /**
    * Creates an <em>authenticated</em> session.
    * 
    * It is possible to <em>pre-acknowledge messages on the server</em> so that the client can avoid additional network trip
    * to the server to acknowledge messages. While this increase performance, this does not guarantee delivery (as messages
    * can be lost after being pre-acknowledged on the server). Use with caution if your application design permits it.
    * 
    * @param username the user name
    * @param password the user password
    * @param xa whether the session support XA transaction semantic or not
    * @param autoCommitSends <code>true</code> to automatically commit message sends, <code>false</code> to commit manually
    * @param autoCommitAcks <code>true</code> to automatically commit message acknowledgement, <code>false</code> to commit manually
    * @param preAcknowledge <code>true</code> to pre-acknowledge messages on the server, <code>false</code> to let the client acknowledge the messages
    * @return a ClientSession
    * @throws HornetQException if an exception occurs while creating the session
    */
   ClientSession createSession(String username,
                               String password,
                               boolean xa,
                               boolean autoCommitSends,
                               boolean autoCommitAcks,
                               boolean preAcknowledge,
                               int ackBatchSize) throws HornetQException;

   void close();
   
   ServerLocator getServerLocator();
   
   CoreRemotingConnection getConnection();
}
