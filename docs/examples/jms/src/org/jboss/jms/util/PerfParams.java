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
package org.jboss.jms.util;

import javax.jms.DeliveryMode;
import java.io.Serializable;

/**
 * @author <a href="ataylor@redhat.com">Andy Taylor</a>
*/
public class PerfParams implements Serializable
{
   int noOfMessagesToSend = 1000;
   long samplePeriod = 1000;
   int deliveryMode  = DeliveryMode.NON_PERSISTENT;

   public int getNoOfMessagesToSend()
   {
      return noOfMessagesToSend;
   }

   public void setNoOfMessagesToSend(int noOfMessagesToSend)
   {
      this.noOfMessagesToSend = noOfMessagesToSend;
   }

   public long getSamplePeriod()
   {
      return samplePeriod;
   }

   public void setSamplePeriod(long samplePeriod)
   {
      this.samplePeriod = samplePeriod;
   }

   public int getDeliveryMode()
   {
      return deliveryMode;
   }

   public void setDeliveryMode(int deliveryMode)
   {
      this.deliveryMode = deliveryMode;
   }

   public String toString()
   {
      return "message to send = " + noOfMessagesToSend + " samplePeriod = " + samplePeriod + "ms" + " DeliveryMode = " +
              (deliveryMode == DeliveryMode.PERSISTENT?"PERSISTENT":"NON_PERSISTENT");
   }
}
