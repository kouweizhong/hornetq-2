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
package org.jboss.test.messaging.core;


/**
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version <tt>$Revision$</tt>
 */
public class PointToMultipointRouterAsDistributorTest extends DistributorTest
{
   // Constructors --------------------------------------------------

   public PointToMultipointRouterAsDistributorTest(String name)
   {
      super(name);
   }

//   public void setUp() throws Exception
//   {
//      super.setUp();
//
//      // Create a PointToMultipointRouter to be tested by the superclass tests
//      distributor = new PointToMultipointRouter("P2MPRouterID");
//   }
//
//   public void tearDown()throws Exception
//   {
//      distributor.clear();
//      distributor = null;
//      super.tearDown();
//   }
//
//   //
//   // This test also runs all DistributorTest's tests
//   //
//
//   public void testPointToMultipointRouter() throws Exception
//   {
//      PointToMultipointRouter router = new PointToMultipointRouter("");
//
//      // by default, the router sends the message by reference
//      assertTrue(router.isPassByReference());
//
//      // send without a receiver
//
//      Routable m = new MessageSupport(new Integer(0));
//      Set acks = router.handle(m);
//      assertEquals(0, acks.size());
//
//      // send with one receiver
//
//      ReceiverImpl rOne = new ReceiverImpl("ONE", ReceiverImpl.HANDLING);
//      assertTrue(router.add(rOne));
//
//      m = new MessageSupport(new Integer(1));
//      Set result = router.handle(m);
//      assertEquals(1, result.size());
//      Acknowledgment a = (Acknowledgment)result.iterator().next();
//      assertTrue(a.isPositive());
//      assertEquals("ONE", a.getReceiverID());
//
//      Iterator i = rOne.iterator();
//      Routable n = (Routable)i.next();
//      assertFalse(i.hasNext());
//      assertTrue(m == n);
//
//      rOne.clear();
//
//      // send with two receivers
//
//      ReceiverImpl rTwo = new ReceiverImpl("TWO", ReceiverImpl.HANDLING);
//      assertTrue(router.add(rTwo));
//
//      m = new MessageSupport(new Integer(2));
//      result = router.handle(m);
//      assertEquals(2, result.size());
//      Set ids = new HashSet();
//      for(Iterator j = result.iterator(); j.hasNext(); )
//      {
//         Acknowledgment ack = (Acknowledgment)j.next();
//         assertTrue(ack.isPositive());
//         ids.add(ack.getReceiverID());
//      }
//      assertTrue(ids.contains("ONE"));
//      assertTrue(ids.contains("TWO"));
//
//      Iterator iOne = rOne.iterator();
//      n = (Routable)iOne.next();
//      assertFalse(iOne.hasNext());
//      assertTrue(m == n);
//
//      Iterator iTwo = rTwo.iterator();
//      n = (Routable)iTwo.next();
//      assertFalse(iTwo.hasNext());
//      assertTrue(m == n);
//   }
//
//
//   public void testNackingReceiver() throws Exception
//   {
//      PointToMultipointRouter router = new PointToMultipointRouter("");
//
//      // one NACKing receiver
//
//      ReceiverImpl nacking = new ReceiverImpl("ReceiverONE", ReceiverImpl.NACKING);
//      assertTrue(router.add(nacking));
//
//      Routable m = new MessageSupport("");
//      Set result = router.handle(m);
//      assertEquals(1, result.size());
//      Acknowledgment a = (Acknowledgment)result.iterator().next();
//      assertTrue(a.isNegative());
//      assertEquals("ReceiverONE", a.getReceiverID());
//
//      Iterator i = nacking.iterator();
//      assertFalse(i.hasNext());
//
//      // a NACKing and a handling receiver
//
//      ReceiverImpl handling = new ReceiverImpl("ReceiverTWO", ReceiverImpl.HANDLING);
//      assertTrue(router.add(handling));
//      result = router.handle(m);
//
//      assertEquals(2, result.size());
//      Set booleans = new HashSet();
//      Set ids = new HashSet();
//      for(Iterator j = result.iterator(); j.hasNext(); )
//      {
//         Acknowledgment ack = (Acknowledgment)j.next();
//         booleans.add(new Boolean(ack.isPositive()));
//         ids.add(ack.getReceiverID());
//      }
//      assertTrue(booleans.contains(Boolean.TRUE));
//      assertTrue(booleans.contains(Boolean.FALSE));
//      assertTrue(ids.contains("ReceiverONE"));
//      assertTrue(ids.contains("ReceiverTWO"));
//
//
//      i = nacking.iterator();
//      assertFalse(i.hasNext());
//      i = handling.iterator();
//      assertTrue(m == i.next());
//      assertFalse(i.hasNext());
//
//   }
//
//   public void testBrokenReceiver() throws Exception
//   {
//      PointToMultipointRouter router = new PointToMultipointRouter("");
//
//      // a broken receiver
//
//      ReceiverImpl broken = new ReceiverImpl("ReceiverONE", ReceiverImpl.BROKEN);
//      assertTrue(router.add(broken));
//
//      Routable m = new MessageSupport("");
//      Set result = router.handle(m);
//      assertEquals(0, result.size());
//
//      Iterator i = broken.iterator();
//      assertFalse(i.hasNext());
//
//      // a broken receiver and a handling receiver
//
//      ReceiverImpl handling = new ReceiverImpl("ReceiverTWO", ReceiverImpl.HANDLING);
//      assertTrue(router.add(handling));
//      result = router.handle(m);
//      assertEquals(1, result.size());
//      Acknowledgment a = (Acknowledgment)result.iterator().next();
//      assertTrue(a.isPositive());
//      assertEquals("ReceiverTWO", a.getReceiverID());
//
//
//      i = broken.iterator();
//      assertFalse(i.hasNext());
//      i = handling.iterator();
//      assertTrue(m == i.next());
//      assertFalse(i.hasNext());
//
//      handling.clear();
//
//      // a broken, handling and NACKing receivers
//
//      ReceiverImpl nacking = new ReceiverImpl("ReceiverTHREE", ReceiverImpl.NACKING);
//      assertTrue(router.add(nacking));
//      result = router.handle(m);
//
//      assertEquals(2, result.size());
//      Set booleans = new HashSet();
//      Set ids = new HashSet();
//      for(Iterator j = result.iterator(); j.hasNext(); )
//      {
//         Acknowledgment ack = (Acknowledgment)j.next();
//         booleans.add(new Boolean(ack.isPositive()));
//         ids.add(ack.getReceiverID());
//      }
//      assertTrue(booleans.contains(Boolean.TRUE));
//      assertTrue(booleans.contains(Boolean.FALSE));
//      assertTrue(ids.contains("ReceiverTHREE"));
//      assertTrue(ids.contains("ReceiverTWO"));
//
//
//      i = broken.iterator();
//      assertFalse(i.hasNext());
//      i = handling.iterator();
//      assertTrue(m == i.next());
//      assertFalse(i.hasNext());
//      i = nacking.iterator();
//      assertFalse(i.hasNext());
//   }
}
