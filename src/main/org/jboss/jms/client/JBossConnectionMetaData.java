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
package org.jboss.jms.client;

import java.util.Enumeration;
import java.util.Vector;
import java.io.Serializable;

import javax.jms.ConnectionMetaData;
import javax.jms.JMSException;

/**
 * Connection metadata
 * 
 * @author <a href="mailto:tim.l.fox@gmail.com">Tim Fox</a>
 *
 * $Id$
 */
public class JBossConnectionMetaData implements Serializable, ConnectionMetaData {
   
   // Constants -----------------------------------------------------
   
   private static final long serialVersionUID = 327633302671160939L;
   
   // Static --------------------------------------------------------
      
   // Attributes ----------------------------------------------------
   
   // Constructors --------------------------------------------------
   
   /**
    * Create a new JBossConnectionMetaData object.
    */
   public JBossConnectionMetaData() {}
   
   
   // ConnectionMetaData Implementation
   
   public String getJMSVersion() throws JMSException {
      return "1.1";
   }
   
   
   public int getJMSMajorVersion() throws JMSException {
      return 1;
   }
   
   
   public int getJMSMinorVersion() throws JMSException {
      return 1;
   }
   
   
   public String getJMSProviderName() throws JMSException {
      return "JBoss Messaging";
   }
   
   
   public String getProviderVersion() throws JMSException {        
      return "1.0.0 alpha PR1";
   }
   
   
   public int getProviderMajorVersion() throws JMSException {
      return 1;
   }
   
   
   public int getProviderMinorVersion() throws JMSException {
      return 0;
   }
   
   
   public Enumeration getJMSXPropertyNames() throws JMSException {
      Vector v = new Vector();
      v.add("JMSXGroupID");
      v.add("JMSXGroupSeq");
      return v.elements();
   }
   
}
