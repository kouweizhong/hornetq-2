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
import org.hornetq.utils.DataConstants;

/**
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>.
 * 
 * @version <tt>$Revision$</tt>
 */
public class CreateSessionResponseMessage extends PacketImpl
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   private int serverVersion;
   
   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   public CreateSessionResponseMessage(final int serverVersion)
   {
      super(CREATESESSION_RESP);
      
      this.serverVersion = serverVersion;
   }

   public CreateSessionResponseMessage()
   {
      super(CREATESESSION_RESP);
   }

   // Public --------------------------------------------------------

   @Override
   public boolean isResponse()
   {
      return true;
   }

   public int getServerVersion()
   {
      return serverVersion;
   }

   @Override
   public void encodeBody(final HornetQBuffer buffer)
   {     
      buffer.writeInt(serverVersion);
   }

   @Override
   public void decodeBody(final HornetQBuffer buffer)
   {      
      serverVersion = buffer.readInt();
   }
   
   public int getRequiredBufferSize()
   {
      return BASIC_PACKET_SIZE + DataConstants.SIZE_INT; 
   }

   @Override
   public boolean equals(final Object other)
   {
      if (other instanceof CreateSessionResponseMessage == false)
      {
         return false;
      }

      CreateSessionResponseMessage r = (CreateSessionResponseMessage)other;

      boolean matches = super.equals(other) && serverVersion == r.serverVersion;

      return matches;
   }

   @Override
   public final boolean isRequiresConfirmations()
   {
      return false;
   }

   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
