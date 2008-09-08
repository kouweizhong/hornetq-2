/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.messaging.tests.unit.jms.referenceable;

import static org.jboss.messaging.tests.util.RandomUtil.randomString;

import java.util.Map;

import javax.naming.Reference;

import junit.framework.TestCase;

import org.jboss.messaging.core.remoting.spi.ConnectorFactory;
import org.jboss.messaging.jms.client.JBossConnectionFactory;
import org.jboss.messaging.jms.referenceable.ConnectionFactoryObjectFactory;

/**
 * @author <a href="mailto:jmesnil@redhat.com">Jeff Mesnil</a>
 *
 * @version <tt>$Revision$</tt>
 *
 */
public class ConnectionFactoryObjectFactoryTest extends TestCase
{
   // Constants -----------------------------------------------------

   // Attributes ----------------------------------------------------

   // Static --------------------------------------------------------

   // Constructors --------------------------------------------------

   // Public --------------------------------------------------------

   public void testDummy()
   {      
   }
   
//   public void testReference() throws Exception
//   {
//      JBossConnectionFactory cf =
//         new JBossConnectionFactory(null, null, 123, 123, randomString(), 1, 1, 1, 1, 1, true, true, true);
//      Reference reference = cf.getReference();
//
//      ConnectionFactoryObjectFactory factory = new ConnectionFactoryObjectFactory();
//      
//      Object object = factory.getObjectInstance(reference, null, null, null);
//      assertNotNull(object);
//      assertTrue(object instanceof JBossConnectionFactory);
//   }
   
   // Package protected ---------------------------------------------

   // Protected -----------------------------------------------------

   // Private -------------------------------------------------------

   // Inner classes -------------------------------------------------
}
