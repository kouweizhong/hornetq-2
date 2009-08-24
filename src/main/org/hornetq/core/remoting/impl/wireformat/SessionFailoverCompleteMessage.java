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

package org.hornetq.core.remoting.impl.wireformat;

import org.hornetq.core.remoting.spi.HornetQBuffer;

/**
 * 
 * A SessionFailoverCompleteMessage
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 */
public class SessionFailoverCompleteMessage extends PacketImpl
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private String name;

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   public SessionFailoverCompleteMessage(final String name)
   {
      super(SESS_FAILOVER_COMPLETE);

      this.name = name;
   }

   public SessionFailoverCompleteMessage()
   {
      super(SESS_FAILOVER_COMPLETE);
   }

   // Public --------------------------------------------------------

   public String getName()
   {
      return name;
   }

   public int getRequiredBufferSize()
   {
      return BASIC_PACKET_SIZE + stringEncodeSize(name);
   }

   @Override
   public void encodeBody(final HornetQBuffer buffer)
   {
      buffer.writeString(name);
   }

   @Override
   public void decodeBody(final HornetQBuffer buffer)
   {
      name = buffer.readString();
   }

   @Override
   public boolean isRequiresConfirmations()
   {
      return false;
   }

   @Override
   public boolean equals(final Object other)
   {
      if (other instanceof SessionFailoverCompleteMessage == false)
      {
         return false;
      }

      SessionFailoverCompleteMessage r = (SessionFailoverCompleteMessage)other;

      return super.equals(other) && name.equals(r.name);
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}