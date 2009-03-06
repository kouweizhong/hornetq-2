/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.messaging.utils;

import static org.jboss.messaging.utils.DataConstants.BOOLEAN;
import static org.jboss.messaging.utils.DataConstants.BYTE;
import static org.jboss.messaging.utils.DataConstants.BYTES;
import static org.jboss.messaging.utils.DataConstants.CHAR;
import static org.jboss.messaging.utils.DataConstants.DOUBLE;
import static org.jboss.messaging.utils.DataConstants.FLOAT;
import static org.jboss.messaging.utils.DataConstants.INT;
import static org.jboss.messaging.utils.DataConstants.LONG;
import static org.jboss.messaging.utils.DataConstants.NOT_NULL;
import static org.jboss.messaging.utils.DataConstants.NULL;
import static org.jboss.messaging.utils.DataConstants.SHORT;
import static org.jboss.messaging.utils.DataConstants.SIZE_BOOLEAN;
import static org.jboss.messaging.utils.DataConstants.SIZE_BYTE;
import static org.jboss.messaging.utils.DataConstants.SIZE_CHAR;
import static org.jboss.messaging.utils.DataConstants.SIZE_DOUBLE;
import static org.jboss.messaging.utils.DataConstants.SIZE_FLOAT;
import static org.jboss.messaging.utils.DataConstants.SIZE_INT;
import static org.jboss.messaging.utils.DataConstants.SIZE_LONG;
import static org.jboss.messaging.utils.DataConstants.SIZE_SHORT;
import static org.jboss.messaging.utils.DataConstants.STRING;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.jboss.messaging.core.logging.Logger;
import org.jboss.messaging.core.remoting.spi.MessagingBuffer;

/**
 * 
 * A TypedProperties
 * 
 * TODO - should have typed property getters and do conversions herein
 * 
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 *
 */
public class TypedProperties
{
   private static final Logger log = Logger.getLogger(TypedProperties.class);

   private Map<SimpleString, PropertyValue> properties;

   private volatile int size;

   public TypedProperties()
   {
   }

   public TypedProperties(final TypedProperties other)
   {
      properties = other.properties == null ? null : new HashMap<SimpleString, PropertyValue>(other.properties);
      size = other.size;
   }

   public void putBooleanProperty(final SimpleString key, final boolean value)
   {
      checkCreateProperties();
      doPutValue(key, new BooleanValue(value));
   }

   public void putByteProperty(final SimpleString key, final byte value)
   {
      checkCreateProperties();
      doPutValue(key, new ByteValue(value));
   }

   public void putBytesProperty(final SimpleString key, final byte[] value)
   {
      checkCreateProperties();
      doPutValue(key, value == null ? new NullValue() : new BytesValue(value));
   }

   public void putShortProperty(final SimpleString key, final short value)
   {
      checkCreateProperties();
      doPutValue(key, new ShortValue(value));
   }

   public void putIntProperty(final SimpleString key, final int value)
   {
      checkCreateProperties();
      doPutValue(key, new IntValue(value));
   }

   public void putLongProperty(final SimpleString key, final long value)
   {
      checkCreateProperties();
      doPutValue(key, new LongValue(value));
   }

   public void putFloatProperty(final SimpleString key, final float value)
   {
      checkCreateProperties();
      doPutValue(key, new FloatValue(value));
   }

   public void putDoubleProperty(final SimpleString key, final double value)
   {
      checkCreateProperties();
      doPutValue(key, new DoubleValue(value));
   }

   public void putStringProperty(final SimpleString key, final SimpleString value)
   {
      checkCreateProperties();
      doPutValue(key, value == null ? new NullValue() : new StringValue(value));
   }

   public void putCharProperty(final SimpleString key, final char value)
   {
      checkCreateProperties();
      doPutValue(key, new CharValue(value));
   }

   public void putTypedProperties(final TypedProperties otherProps)
   {
      if (otherProps == null || otherProps.properties == null)
      {
         return;
      }
      
      checkCreateProperties();
      Set<Entry<SimpleString,PropertyValue>> otherEntries = otherProps.properties.entrySet();
      for (Entry<SimpleString, PropertyValue> otherEntry : otherEntries)
      {
         doPutValue(otherEntry.getKey(), otherEntry.getValue());
      }
   }
   
   public Object getProperty(final SimpleString key)
   {
      return doGetProperty(key);
   }

   public Object removeProperty(final SimpleString key)
   {
      return doRemoveProperty(key);
   }

   public boolean containsProperty(final SimpleString key)
   {
      if (properties != null)
      {
         return properties.containsKey(key);
      }
      else
      {
         return false;
      }
   }

   public Set<SimpleString> getPropertyNames()
   {
      if (properties != null)
      {
         return properties.keySet();
      }
      else
      {
         return Collections.EMPTY_SET;
      }
   }

   public synchronized void decode(final MessagingBuffer buffer)
   {
      byte b = buffer.readByte();

      if (b == NULL)
      {
         properties = null;
      }
      else
      {
         int numHeaders = buffer.readInt();

         properties = new HashMap<SimpleString, PropertyValue>(numHeaders);
         size = 0;

         for (int i = 0; i < numHeaders; i++)
         {
            int len = buffer.readInt();
            byte[] data = new byte[len];
            buffer.readBytes(data);
            SimpleString key = new SimpleString(data);

            byte type = buffer.readByte();

            PropertyValue val;

            switch (type)
            {
               case NULL:
               {
                  val = new NullValue();
                  doPutValue(key, val);
                  break;
               }
               case CHAR:
               {
                  val = new CharValue(buffer);
                  doPutValue(key, val);
                  break;
               }
               case BOOLEAN:
               {
                  val = new BooleanValue(buffer);
                  doPutValue(key, val);
                  break;
               }
               case BYTE:
               {
                  val = new ByteValue(buffer);
                  doPutValue(key, val);
                  break;
               }
               case BYTES:
               {
                  val = new BytesValue(buffer);
                  doPutValue(key, val);
                  break;
               }
               case SHORT:
               {
                  val = new ShortValue(buffer);
                  doPutValue(key, val);
                  break;
               }
               case INT:
               {
                  val = new IntValue(buffer);
                  doPutValue(key, val);
                  break;
               }
               case LONG:
               {
                  val = new LongValue(buffer);
                  doPutValue(key, val);
                  break;
               }
               case FLOAT:
               {
                  val = new FloatValue(buffer);
                  doPutValue(key, val);
                  break;
               }
               case DOUBLE:
               {
                  val = new DoubleValue(buffer);
                  doPutValue(key, val);
                  break;
               }
               case STRING:
               {
                  val = new StringValue(buffer);
                  doPutValue(key, val);
                  break;
               }
               default:
               {
                  throw new IllegalArgumentException("Invalid type: " + type);
               }
            }
         }
      }
   }

   public synchronized void encode(final MessagingBuffer buffer)
   {
      if (properties == null)
      {
         buffer.writeByte(NULL);
      }
      else
      {
         buffer.writeByte(NOT_NULL);

         buffer.writeInt(properties.size());

         for (Map.Entry<SimpleString, PropertyValue> entry : properties.entrySet())
         {
            SimpleString s = entry.getKey();
            byte[] data = s.getData();
            buffer.writeInt(data.length);
            buffer.writeBytes(data);

            entry.getValue().write(buffer);
         }
      }
   }

   public int getEncodeSize()
   {
      if (properties == null)
      {
         return SIZE_BYTE;
      }
      else
      {
         return SIZE_BYTE + SIZE_INT + size;

      }
   }

   public void clear()
   {
      if (properties != null)
      {
         properties.clear();
      }
   }

   // Private ------------------------------------------------------------------------------------

   private void checkCreateProperties()
   {
      if (properties == null)
      {
         properties = new HashMap<SimpleString, PropertyValue>();
      }
   }

   private synchronized void doPutValue(final SimpleString key, final PropertyValue value)
   {
      PropertyValue oldValue = properties.put(key, value);
      if (oldValue != null)
      {
         size += value.encodeSize() - oldValue.encodeSize();
      }
      else
      {
         size += SimpleString.sizeofString(key) + value.encodeSize();
      }
   }

   private synchronized Object doRemoveProperty(final SimpleString key)
   {
      if (properties == null)
      {
         return null;
      }

      PropertyValue val = properties.remove(key);

      if (val == null)
      {
         return null;
      }
      else
      {
         size -= SimpleString.sizeofString(key) + val.encodeSize();

         return val.getValue();
      }
   }

   private synchronized Object doGetProperty(final Object key)
   {
      if (properties == null)
      {
         return null;
      }

      PropertyValue val = properties.get(key);

      if (val == null)
      {
         return null;
      }
      else
      {
         return val.getValue();
      }
   }

   // Inner classes ------------------------------------------------------------------------------

   private interface PropertyValue
   {
      Object getValue();

      void write(MessagingBuffer buffer);

      int encodeSize();
   }

   private static final class NullValue implements PropertyValue
   {
      public NullValue()
      {
      }

      public Object getValue()
      {
         return null;
      }

      public void write(final MessagingBuffer buffer)
      {
         buffer.writeByte(NULL);
      }

      public int encodeSize()
      {
         return SIZE_BYTE;
      }

   }

   private static final class BooleanValue implements PropertyValue
   {
      final boolean val;

      public BooleanValue(final boolean val)
      {
         this.val = val;
      }

      public BooleanValue(final MessagingBuffer buffer)
      {
         val = buffer.readBoolean();
      }

      public Object getValue()
      {
         return val;
      }

      public void write(final MessagingBuffer buffer)
      {
         buffer.writeByte(BOOLEAN);
         buffer.writeBoolean(val);
      }

      public int encodeSize()
      {
         return SIZE_BYTE + SIZE_BOOLEAN;
      }

   }

   private static final class ByteValue implements PropertyValue
   {
      final byte val;

      public ByteValue(final byte val)
      {
         this.val = val;
      }

      public ByteValue(final MessagingBuffer buffer)
      {
         val = buffer.readByte();
      }

      public Object getValue()
      {
         return val;
      }

      public void write(final MessagingBuffer buffer)
      {
         buffer.writeByte(BYTE);
         buffer.writeByte(val);
      }

      public int encodeSize()
      {
         return SIZE_BYTE + SIZE_BYTE;
      }
   }

   private static final class BytesValue implements PropertyValue
   {
      final byte[] val;

      public BytesValue(final byte[] val)
      {
         this.val = val;
      }

      public BytesValue(final MessagingBuffer buffer)
      {
         int len = buffer.readInt();
         val = new byte[len];
         buffer.readBytes(val);
      }

      public Object getValue()
      {
         return val;
      }

      public void write(final MessagingBuffer buffer)
      {
         buffer.writeByte(BYTES);
         buffer.writeInt(val.length);
         buffer.writeBytes(val);
      }

      public int encodeSize()
      {
         return SIZE_BYTE + SIZE_INT + val.length;
      }

   }

   private static final class ShortValue implements PropertyValue
   {
      final short val;

      public ShortValue(final short val)
      {
         this.val = val;
      }

      public ShortValue(final MessagingBuffer buffer)
      {
         val = buffer.readShort();
      }

      public Object getValue()
      {
         return val;
      }

      public void write(final MessagingBuffer buffer)
      {
         buffer.writeByte(SHORT);
         buffer.writeShort(val);
      }

      public int encodeSize()
      {
         return SIZE_BYTE + SIZE_SHORT;
      }
   }

   private static final class IntValue implements PropertyValue
   {
      final int val;

      public IntValue(final int val)
      {
         this.val = val;
      }

      public IntValue(final MessagingBuffer buffer)
      {
         val = buffer.readInt();
      }

      public Object getValue()
      {
         return val;
      }

      public void write(final MessagingBuffer buffer)
      {
         buffer.writeByte(INT);
         buffer.writeInt(val);
      }

      public int encodeSize()
      {
         return SIZE_BYTE + SIZE_INT;
      }
   }

   private static final class LongValue implements PropertyValue
   {
      final long val;

      public LongValue(final long val)
      {
         this.val = val;
      }

      public LongValue(final MessagingBuffer buffer)
      {
         val = buffer.readLong();
      }

      public Object getValue()
      {
         return val;
      }

      public void write(final MessagingBuffer buffer)
      {
         buffer.writeByte(LONG);
         buffer.writeLong(val);
      }

      public int encodeSize()
      {
         return SIZE_BYTE + SIZE_LONG;
      }
   }

   private static final class FloatValue implements PropertyValue
   {
      final float val;

      public FloatValue(final float val)
      {
         this.val = val;
      }

      public FloatValue(final MessagingBuffer buffer)
      {
         val = buffer.readFloat();
      }

      public Object getValue()
      {
         return val;
      }

      public void write(final MessagingBuffer buffer)
      {
         buffer.writeByte(FLOAT);
         buffer.writeFloat(val);
      }

      public int encodeSize()
      {
         return SIZE_BYTE + SIZE_FLOAT;
      }

   }

   private static final class DoubleValue implements PropertyValue
   {
      final double val;

      public DoubleValue(final double val)
      {
         this.val = val;
      }

      public DoubleValue(final MessagingBuffer buffer)
      {
         val = buffer.readDouble();
      }

      public Object getValue()
      {
         return val;
      }

      public void write(final MessagingBuffer buffer)
      {
         buffer.writeByte(DOUBLE);
         buffer.writeDouble(val);
      }

      public int encodeSize()
      {
         return SIZE_BYTE + SIZE_DOUBLE;
      }
   }

   private static final class CharValue implements PropertyValue
   {
      final char val;

      public CharValue(final char val)
      {
         this.val = val;
      }

      public CharValue(final MessagingBuffer buffer)
      {
         val = buffer.readChar();
      }

      public Object getValue()
      {
         return val;
      }

      public void write(final MessagingBuffer buffer)
      {
         buffer.writeByte(CHAR);
         buffer.writeChar(val);
      }

      public int encodeSize()
      {
         return SIZE_BYTE + SIZE_CHAR;
      }
   }

   private static final class StringValue implements PropertyValue
   {
      final SimpleString val;

      public StringValue(final SimpleString val)
      {
         this.val = val;
      }

      public StringValue(final MessagingBuffer buffer)
      {
         val = buffer.readSimpleString();
      }

      public Object getValue()
      {
         return val;
      }

      public void write(final MessagingBuffer buffer)
      {
         buffer.writeByte(STRING);
         buffer.writeSimpleString(val);
      }

      public int encodeSize()
      {
         return SIZE_BYTE + SimpleString.sizeofString(val);
      }
   }
}