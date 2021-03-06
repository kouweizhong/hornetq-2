/*
 * Copyright 2010 Red Hat, Inc.
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

package org.hornetq.core.paging.cursor;



/**
 * A PagePosition
 *
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 *
 *
 */
public interface PagePosition extends Comparable<PagePosition>
{

   // The recordID associated during ack
   long getRecordID();

   // The recordID associated during ack
   void setRecordID(long recordID);

   long getPageNr();

   int getMessageNr();
   
   void setPageCache(PageCache pageCache);
   
   /**
    * PagePosition will hold the page with a weak reference.
    * So, this could be eventually null case soft-cache was released
    * @return
    */
   PageCache getPageCache();

   PagePosition nextMessage();

   PagePosition nextPage();
   
   /** This will just test if the current position is the immediate next to the parameter position */
   boolean isRightAfter(PagePosition previous);

}
