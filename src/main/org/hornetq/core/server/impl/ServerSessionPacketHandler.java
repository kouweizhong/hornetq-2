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

package org.hornetq.core.server.impl;

import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.CREATE_QUEUE;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.DELETE_QUEUE;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_ACKNOWLEDGE;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_BINDINGQUERY;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_CLOSE;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_COMMIT;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_CONSUMER_CLOSE;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_CREATECONSUMER;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_EXPIRED;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_FLOWTOKEN;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_FORCE_CONSUMER_DELIVERY;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_QUEUEQUERY;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_ROLLBACK;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_SEND;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_SEND_CONTINUATION;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_SEND_LARGE;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_START;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_STOP;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_XA_COMMIT;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_XA_END;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_XA_FORGET;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_XA_GET_TIMEOUT;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_XA_INDOUBT_XIDS;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_XA_JOIN;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_XA_PREPARE;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_XA_RESUME;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_XA_ROLLBACK;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_XA_SET_TIMEOUT;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_XA_START;
import static org.hornetq.core.remoting.impl.wireformat.PacketImpl.SESS_XA_SUSPEND;

import java.util.List;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.core.exception.HornetQXAException;
import org.hornetq.core.journal.IOAsyncTask;
import org.hornetq.core.logging.Logger;
import org.hornetq.core.persistence.OperationContext;
import org.hornetq.core.persistence.StorageManager;
import org.hornetq.core.remoting.Channel;
import org.hornetq.core.remoting.ChannelHandler;
import org.hornetq.core.remoting.CloseListener;
import org.hornetq.core.remoting.FailureListener;
import org.hornetq.core.remoting.Packet;
import org.hornetq.core.remoting.RemotingConnection;
import org.hornetq.core.remoting.impl.wireformat.CreateQueueMessage;
import org.hornetq.core.remoting.impl.wireformat.HornetQExceptionMessage;
import org.hornetq.core.remoting.impl.wireformat.NullResponseMessage;
import org.hornetq.core.remoting.impl.wireformat.PacketImpl;
import org.hornetq.core.remoting.impl.wireformat.RollbackMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionAcknowledgeMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionBindingQueryMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionBindingQueryResponseMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionConsumerCloseMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionConsumerFlowCreditMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionCreateConsumerMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionDeleteQueueMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionExpiredMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionForceConsumerDelivery;
import org.hornetq.core.remoting.impl.wireformat.SessionProducerCreditsMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionQueueQueryMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionQueueQueryResponseMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionReceiveContinuationMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionReceiveLargeMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionReceiveMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionRequestProducerCreditsMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionSendContinuationMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionSendLargeMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionSendMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXACommitMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAEndMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAForgetMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAGetInDoubtXidsResponseMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAGetTimeoutResponseMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAJoinMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAPrepareMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAResponseMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAResumeMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXARollbackMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXASetTimeoutMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXASetTimeoutResponseMessage;
import org.hornetq.core.remoting.impl.wireformat.SessionXAStartMessage;
import org.hornetq.core.server.BindingQueryResult;
import org.hornetq.core.server.QueueQueryResult;
import org.hornetq.core.server.ServerMessage;
import org.hornetq.core.server.ServerSession;
import org.hornetq.core.server.SessionCallback;

/**
 * A ServerSessionPacketHandler
 *
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:andy.taylor@jboss.org>Andy Taylor</a>
 * @author <a href="mailto:clebert.suconic@jboss.org>Clebert Suconic</a>
 */
public class ServerSessionPacketHandler implements ChannelHandler, CloseListener, FailureListener, SessionCallback
{
   private static final Logger log = Logger.getLogger(ServerSessionPacketHandler.class);

   private final ServerSession session;

   private final OperationContext sessionContext;

   // Storagemanager here is used to set the Context
   private final StorageManager storageManager;

   private final Channel channel;

   private volatile RemotingConnection remotingConnection;

   public ServerSessionPacketHandler(final ServerSession session,
                                     final OperationContext sessionContext,
                                     final StorageManager storageManager,
                                     final Channel channel)
   {
      this.session = session;

      this.storageManager = storageManager;

      this.sessionContext = sessionContext;

      this.channel = channel;

      this.remotingConnection = channel.getConnection();

      addConnectionListeners();
   }

   public long getID()
   {
      return channel.getID();
   }

   public void connectionFailed(final HornetQException exception)
   {
      log.warn("Client connection failed, clearing up resources for session " + session.getName());

      session.runConnectionFailureRunners();

      handleCloseSession();

      log.warn("Cleared up resources for session " + session.getName());
   }

   public void close()
   {
      channel.flushConfirmations();

      handleCloseSession();
   }

   public void connectionClosed()
   {
      session.runConnectionFailureRunners();
   }

   private void addConnectionListeners()
   {
      remotingConnection.addFailureListener(this);
      remotingConnection.addCloseListener(this);
   }

   private void removeConnectionListeners()
   {
      remotingConnection.removeFailureListener(this);
      remotingConnection.removeCloseListener(this);
   }

   public Channel getChannel()
   {
      return channel;
   }

   public int transferConnection(final RemotingConnection newConnection, final int lastReceivedCommandID)
   {
      // We need to disable delivery on all the consumers while the transfer is occurring- otherwise packets might get
      // delivered
      // after the channel has transferred but *before* packets have been replayed - this will give the client the wrong
      // sequence of packets.
      // It is not sufficient to just stop the session, since right after stopping the session, another session start
      // might be executed
      // before we have transferred the connection, leaving it in a started state
      session.setTransferring(true);

      removeConnectionListeners();

      // Note. We do not destroy the replicating connection here. In the case the live server has really crashed
      // then the connection will get cleaned up anyway when the server ping timeout kicks in.
      // In the case the live server is really still up, i.e. a split brain situation (or in tests), then closing
      // the replicating connection will cause the outstanding responses to be be replayed on the live server,
      // if these reach the client who then subsequently fails over, on reconnection to backup, it will have
      // received responses that the backup did not know about.

      channel.transferConnection(newConnection);

      newConnection.syncIDGeneratorSequence(remotingConnection.getIDGeneratorSequence());

      remotingConnection = newConnection;

      addConnectionListeners();

      int serverLastReceivedCommandID = channel.getLastConfirmedCommandID();

      channel.replayCommands(lastReceivedCommandID);

      channel.setTransferring(false);

      session.setTransferring(false);

      return serverLastReceivedCommandID;
   }

   public void handlePacket(final Packet packet)
   {
      byte type = packet.getType();

      storageManager.setContext(sessionContext);

      Packet response = null;
      boolean flush = false;
      boolean closeChannel = false;

      try
      {
         try
         {
            switch (type)
            {
               case SESS_CREATECONSUMER:
               {
                  SessionCreateConsumerMessage request = (SessionCreateConsumerMessage)packet;
                  session.handleCreateConsumer(request.getID(),
                                               request.getQueueName(),
                                               request.getFilterString(),
                                               request.isBrowseOnly());
                  if (request.isRequiresResponse())
                  {
                     // We send back queue information on the queue as a response- this allows the queue to
                     // be automaticall recreated on failover
                     response = new SessionQueueQueryResponseMessage(session.handleExecuteQueueQuery(request.getQueueName()));
                  }

                  break;
               }
               case CREATE_QUEUE:
               {
                  CreateQueueMessage request = (CreateQueueMessage)packet;
                  session.handleCreateQueue(request.getAddress(),
                                            request.getQueueName(),
                                            request.getFilterString(),
                                            request.isTemporary(),
                                            request.isDurable());
                  if (request.isRequiresResponse())
                  {
                     response = new NullResponseMessage();
                  }
                  break;
               }
               case DELETE_QUEUE:
               {
                  SessionDeleteQueueMessage request = (SessionDeleteQueueMessage)packet;
                  session.handleDeleteQueue(request.getQueueName());
                  response = new NullResponseMessage();
                  break;
               }
               case SESS_QUEUEQUERY:
               {
                  SessionQueueQueryMessage request = (SessionQueueQueryMessage)packet;
                  QueueQueryResult result = session.handleExecuteQueueQuery(request.getQueueName());
                  response = new SessionQueueQueryResponseMessage(result);
                  break;
               }
               case SESS_BINDINGQUERY:
               {
                  SessionBindingQueryMessage request = (SessionBindingQueryMessage)packet;
                  BindingQueryResult result = session.handleExecuteBindingQuery(request.getAddress());
                  response = new SessionBindingQueryResponseMessage(result.isExists(), result.getQueueNames());
                  break;
               }
               case SESS_ACKNOWLEDGE:
               {
                  SessionAcknowledgeMessage message = (SessionAcknowledgeMessage)packet;
                  session.handleAcknowledge(message.getConsumerID(), message.getMessageID());
                  if (message.isRequiresResponse())
                  {
                     response = new NullResponseMessage();
                  }
                  break;
               }
               case SESS_EXPIRED:
               {
                  SessionExpiredMessage message = (SessionExpiredMessage)packet;
                  session.handleExpired(message.getConsumerID(), message.getMessageID());
                  break;
               }
               case SESS_COMMIT:
               {
                  session.handleCommit();
                  response = new NullResponseMessage();
                  break;
               }
               case SESS_ROLLBACK:
               {
                  session.handleRollback(((RollbackMessage)packet).isConsiderLastMessageAsDelivered());
                  response = new NullResponseMessage();
                  break;
               }
               case SESS_XA_COMMIT:
               {
                  SessionXACommitMessage message = (SessionXACommitMessage)packet;
                  session.handleXACommit(message.getXid(), message.isOnePhase());
                  response = new SessionXAResponseMessage(false, XAResource.XA_OK, null);
                  break;
               }
               case SESS_XA_END:
               {
                  SessionXAEndMessage message = (SessionXAEndMessage)packet;
                  session.handleXAEnd(message.getXid());
                  response = new SessionXAResponseMessage(false, XAResource.XA_OK, null);
                  break;
               }
               case SESS_XA_FORGET:
               {
                  SessionXAForgetMessage message = (SessionXAForgetMessage)packet;
                  session.handleXAForget(message.getXid());
                  response = new SessionXAResponseMessage(false, XAResource.XA_OK, null);
                  break;
               }
               case SESS_XA_JOIN:
               {
                  SessionXAJoinMessage message = (SessionXAJoinMessage)packet;
                  session.handleXAJoin(message.getXid());
                  response = new SessionXAResponseMessage(false, XAResource.XA_OK, null);
                  break;
               }
               case SESS_XA_RESUME:
               {
                  SessionXAResumeMessage message = (SessionXAResumeMessage)packet;
                  session.handleXAResume(message.getXid());
                  response = new SessionXAResponseMessage(false, XAResource.XA_OK, null);
                  break;
               }
               case SESS_XA_ROLLBACK:
               {
                  SessionXARollbackMessage message = (SessionXARollbackMessage)packet;
                  session.handleXARollback(message.getXid());
                  response = new SessionXAResponseMessage(false, XAResource.XA_OK, null);
                  break;
               }
               case SESS_XA_START:
               {
                  SessionXAStartMessage message = (SessionXAStartMessage)packet;
                  session.handleXAStart(message.getXid());
                  response = new SessionXAResponseMessage(false, XAResource.XA_OK, null);
                  break;
               }
               case SESS_XA_SUSPEND:
               {
                  session.handleXASuspend();
                  response = new SessionXAResponseMessage(false, XAResource.XA_OK, null);
                  break;
               }
               case SESS_XA_PREPARE:
               {
                  SessionXAPrepareMessage message = (SessionXAPrepareMessage)packet;
                  session.handleXAPrepare(message.getXid());
                  response = new SessionXAResponseMessage(false, XAResource.XA_OK, null);
                  break;
               }
               case SESS_XA_INDOUBT_XIDS:
               {
                  List<Xid> xids = session.handleGetInDoubtXids();
                  response = new SessionXAGetInDoubtXidsResponseMessage(xids);
                  break;
               }
               case SESS_XA_GET_TIMEOUT:
               {
                  int timeout = session.handleGetXATimeout();
                  response = new SessionXAGetTimeoutResponseMessage(timeout);
                  break;
               }
               case SESS_XA_SET_TIMEOUT:
               {
                  SessionXASetTimeoutMessage message = (SessionXASetTimeoutMessage)packet;
                  session.handleSetXATimeout(message.getTimeoutSeconds());
                  response = new SessionXASetTimeoutResponseMessage(true);
                  break;
               }
               case SESS_START:
               {
                  session.handleStart();
                  break;
               }
               case SESS_STOP:
               {
                  session.handleStop();
                  response = new NullResponseMessage();
                  break;
               }
               case SESS_CLOSE:
               {
                  handleCloseSession();
                  removeConnectionListeners();
                  response = new NullResponseMessage();
                  flush = true;
                  closeChannel = true;
                  break;
               }
               case SESS_CONSUMER_CLOSE:
               {
                  SessionConsumerCloseMessage message = (SessionConsumerCloseMessage)packet;
                  session.handleCloseConsumer(message.getConsumerID());
                  response = new NullResponseMessage();
                  break;
               }
               case SESS_FLOWTOKEN:
               {
                  SessionConsumerFlowCreditMessage message = (SessionConsumerFlowCreditMessage)packet;
                  session.handleReceiveConsumerCredits(message.getConsumerID(), message.getCredits());
                  break;
               }
               case SESS_SEND:
               {
                  SessionSendMessage message = (SessionSendMessage)packet;
                  session.handleSend((ServerMessage)message.getMessage());
                  if (message.isRequiresResponse())
                  {
                     response = new NullResponseMessage();
                  }
                  break;
               }
               case SESS_SEND_LARGE:
               {
                  SessionSendLargeMessage message = (SessionSendLargeMessage)packet;
                  session.handleSendLargeMessage(message.getLargeMessageHeader());
                  break;
               }
               case SESS_SEND_CONTINUATION:
               {
                  SessionSendContinuationMessage message = (SessionSendContinuationMessage)packet;
                  session.handleSendContinuations(message.getPacketSize(), message.getBody(), message.isContinues());
                  if (message.isRequiresResponse())
                  {
                     response = new NullResponseMessage();
                  }
                  break;
               }
               case SESS_FORCE_CONSUMER_DELIVERY:
               {
                  SessionForceConsumerDelivery message = (SessionForceConsumerDelivery)packet;
                  session.handleForceConsumerDelivery(message.getConsumerID(), message.getSequence());
                  break;
               }
               case PacketImpl.SESS_PRODUCER_REQUEST_CREDITS:
               {
                  SessionRequestProducerCreditsMessage message = (SessionRequestProducerCreditsMessage)packet;
                  session.handleRequestProducerCredits(message.getAddress(), message.getCredits());
                  break;
               }
            }
         }
         catch (HornetQXAException e)
         {
            response = new SessionXAResponseMessage(true, e.errorCode, e.getMessage());
         }
         catch (HornetQException e)
         {
            response = new HornetQExceptionMessage((HornetQException)e);
         }
         catch (Throwable t)
         {
            log.error("Caught unexpected exception", t);
         }
         
         sendResponse(packet, response, flush, closeChannel);
      }
      finally
      {
         storageManager.completeOperations();
         storageManager.clearContext();
      }
   }

   private void sendResponse(final Packet confirmPacket,
                             final Packet response,
                             final boolean flush,
                             final boolean closeChannel)
   {
      storageManager.afterCompleteOperations(new IOAsyncTask()
      {
         public void onError(final int errorCode, final String errorMessage)
         {
            log.warn("Error processing IOCallback code = " + errorCode + " message = " + errorMessage);

            HornetQExceptionMessage exceptionMessage = new HornetQExceptionMessage(new HornetQException(errorCode,
                                                                                                        errorMessage));

            doConfirmAndResponse(confirmPacket, exceptionMessage, flush, closeChannel);
         }

         public void done()
         {
            doConfirmAndResponse(confirmPacket, response, flush, closeChannel);
         }
      });
   }

   private void doConfirmAndResponse(final Packet confirmPacket,
                                     final Packet response,
                                     final boolean flush,
                                     final boolean closeChannel)
   {
      if (confirmPacket != null)
      {
         channel.confirm(confirmPacket);

         if (flush)
         {
            channel.flushConfirmations();
         }
      }

      if (response != null)
      {
         channel.send(response);
      }

      if (closeChannel)
      {
         channel.close();
      }
   }

   private void handleCloseSession()
   {
      storageManager.afterCompleteOperations(new IOAsyncTask()
      {
         public void onError(int errorCode, String errorMessage)
         {
         }

         public void done()
         {
            try
            {
               session.close();
            }
            catch (Exception e)
            {
               log.error("Failed to close session", e);
            }
         }
      });
   }

   public int sendLargeMessage(long consumerID, byte[] headerBuffer, long bodySize, int deliveryCount)
   {
      Packet packet = new SessionReceiveLargeMessage(consumerID, headerBuffer, bodySize, deliveryCount);

      channel.send(packet);

      return packet.getPacketSize();
   }

   public int sendLargeMessageContinuation(long consumerID, byte[] body, boolean continues, boolean requiresResponse)
   {
      Packet packet = new SessionReceiveContinuationMessage(consumerID, body, continues, requiresResponse);

      channel.send(packet);

      return packet.getPacketSize();
   }

   public int sendMessage(ServerMessage message, long consumerID, int deliveryCount)
   {
      Packet packet = new SessionReceiveMessage(consumerID, message, deliveryCount);

      channel.send(packet);

      return packet.getPacketSize();
   }

   public void sendProducerCreditsMessage(int credits, SimpleString address, int offset)
   {
      Packet packet = new SessionProducerCreditsMessage(credits, address, offset);

      channel.send(packet);
   }
}
