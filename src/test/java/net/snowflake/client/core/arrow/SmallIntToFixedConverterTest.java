/*
 * Copyright (c) 2012-2019 Snowflake Computing Inc. All rights reserved.
 */
package net.snowflake.client.core.arrow;

import net.snowflake.client.TestUtil;
import net.snowflake.client.core.SFException;
import net.snowflake.client.jdbc.ErrorCode;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.SmallIntVector;
import org.apache.arrow.vector.types.Types;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class SmallIntToFixedConverterTest
{
  /**
   * allocator for arrow
   */
  private BufferAllocator allocator = new RootAllocator(Integer.MAX_VALUE);

  /**
   * Random seed
   */
  private Random random = new Random();

  @Test
  public void testFixedNoScale() throws SFException
  {
    final int rowCount = 1000;
    List<Short> expectedValues = new ArrayList<>();
    Set<Integer> nullValIndex = new HashSet<>();
    for (int i = 0; i < rowCount; i++)
    {
      expectedValues.add((short) random.nextInt(1 << 16));
    }

    Map<String, String> customFieldMeta = new HashMap<>();
    customFieldMeta.put("logicalType", "FIXED");
    customFieldMeta.put("precision", "10");
    customFieldMeta.put("scale", "0");

    FieldType fieldType = new FieldType(true,
                                        Types.MinorType.SMALLINT.getType(),
                                        null, customFieldMeta);

    SmallIntVector vector = new SmallIntVector("col_one", fieldType, allocator);
    for (int i = 0; i < rowCount; i++)
    {
      boolean isNull = random.nextBoolean();
      if (isNull)
      {
        vector.setNull(i);
        nullValIndex.add(i);
      }
      else
      {
        vector.setSafe(i, expectedValues.get(i));
      }
    }

    ArrowVectorConverter converter = new SmallIntToFixedConverter(vector);

    for (int i = 0; i < rowCount; i++)
    {
      short shortVal = converter.toShort(i);
      Object shortObject = converter.toObject(i);
      String shortString = converter.toString(i);

      if (nullValIndex.contains(i))
      {
        assertThat(shortVal, is((short) 0));
        assertThat(shortObject, is(nullValue()));
        assertThat(shortString, is(nullValue()));
      }
      else
      {
        assertThat(shortVal, is(expectedValues.get(i)));
        assertThat(shortObject, is(expectedValues.get(i)));
        assertThat(shortString, is(expectedValues.get(i).toString()));
      }
    }
    vector.clear();
  }

  @Test
  public void testFixedWithScale() throws SFException
  {
    final int rowCount = 1000;
    List<Short> expectedValues = new ArrayList<>();
    Set<Integer> nullValIndex = new HashSet<>();
    for (int i = 0; i < rowCount; i++)
    {
      expectedValues.add((short) random.nextInt(1 << 16));
    }

    Map<String, String> customFieldMeta = new HashMap<>();
    customFieldMeta.put("logicalType", "FIXED");
    customFieldMeta.put("precision", "10");
    customFieldMeta.put("scale", "3");

    FieldType fieldType = new FieldType(true,
                                        Types.MinorType.SMALLINT.getType(),
                                        null, customFieldMeta);

    SmallIntVector vector = new SmallIntVector("col_one", fieldType, allocator);
    for (int i = 0; i < rowCount; i++)
    {
      boolean isNull = random.nextBoolean();
      if (isNull)
      {
        vector.setNull(i);
        nullValIndex.add(i);
      }
      else
      {
        vector.setSafe(i, expectedValues.get(i));
      }
    }

    ArrowVectorConverter converter = new SmallIntToFixedConverter(vector);

    for (int i = 0; i < rowCount; i++)
    {
      BigDecimal bigDecimalVal = converter.toBigDecimal(i);
      Object objectVal = converter.toObject(i);
      String stringVal = converter.toString(i);

      if (nullValIndex.contains(i))
      {
        assertThat(bigDecimalVal, nullValue());
        assertThat(objectVal, nullValue());
        assertThat(stringVal, nullValue());
      }
      else
      {
        BigDecimal expectedVal = BigDecimal.valueOf(expectedValues.get(i), 3);
        assertThat(bigDecimalVal, is(expectedVal));
        assertThat(objectVal, is(expectedVal));
        assertThat(stringVal, is(expectedVal.toString()));
      }
    }

    vector.clear();
  }

  @Test
  public void testInvalidConversion()
  {
    // try convert to int/long/byte/short with scale > 0
    Map<String, String> customFieldMeta = new HashMap<>();
    customFieldMeta.put("logicalType", "FIXED");
    customFieldMeta.put("precision", "10");
    customFieldMeta.put("scale", "3");

    FieldType fieldType = new FieldType(true,
                                        Types.MinorType.SMALLINT.getType(),
                                        null, customFieldMeta);

    SmallIntVector vector = new SmallIntVector("col_one", fieldType, allocator);
    vector.setSafe(0, 200);

    final ArrowVectorConverter converter = new SmallIntToFixedConverter(vector);
    final int invalidConversionErrorCode =
        ErrorCode.INVALID_VALUE_CONVERT.getMessageCode();

    TestUtil.assertSFException(invalidConversionErrorCode,
                               () -> converter.toBoolean(0));
    TestUtil.assertSFException(invalidConversionErrorCode,
                               () -> converter.toFloat(0));
    TestUtil.assertSFException(invalidConversionErrorCode,
                               () -> converter.toDouble(0));
    TestUtil.assertSFException(invalidConversionErrorCode,
                               () -> converter.toLong(0));
    TestUtil.assertSFException(invalidConversionErrorCode,
                               () -> converter.toInt(0));
    TestUtil.assertSFException(invalidConversionErrorCode,
                               () -> converter.toShort(0));
    TestUtil.assertSFException(invalidConversionErrorCode,
                               () -> converter.toByte(0));
    TestUtil.assertSFException(invalidConversionErrorCode,
                               () -> converter.toBytes(0));
    TestUtil.assertSFException(invalidConversionErrorCode,
                               () -> converter.toDate(0));
    TestUtil.assertSFException(invalidConversionErrorCode,
                               () -> converter.toTime(0));
    TestUtil.assertSFException(invalidConversionErrorCode,
                               () -> converter.toTimestamp(0));
    vector.clear();
  }

  @Test
  public void testGetSmallerIntegralType() throws SFException
  {
    // try convert to int/long/byte/short with scale > 0
    Map<String, String> customFieldMeta = new HashMap<>();
    customFieldMeta.put("logicalType", "FIXED");
    customFieldMeta.put("precision", "10");
    customFieldMeta.put("scale", "0");

    FieldType fieldType = new FieldType(true,
                                        Types.MinorType.SMALLINT.getType(),
                                        null, customFieldMeta);

    // test value which is out of range of int, but falls in long
    SmallIntVector vectorFoo = new SmallIntVector("col_one", fieldType,
                                                  allocator);
    vectorFoo.setSafe(0, 200);
    vectorFoo.setSafe(1, -200);

    final ArrowVectorConverter converterFoo =
        new SmallIntToFixedConverter(vectorFoo);
    final int invalidConversionErrorCode =
        ErrorCode.INVALID_VALUE_CONVERT.getMessageCode();

    TestUtil.assertSFException(invalidConversionErrorCode,
                               () -> converterFoo.toByte(0));
    TestUtil.assertSFException(invalidConversionErrorCode,
                               () -> converterFoo.toByte(1));
    vectorFoo.clear();

    // test value which is in range of byte, all get method should return
    SmallIntVector vectorBar = new SmallIntVector("col_one", fieldType,
                                                  allocator);
    // set value which is out of range of int, but falls in long
    vectorBar.setSafe(0, 10);
    vectorBar.setSafe(1, -10);

    final ArrowVectorConverter converterBar =
        new SmallIntToFixedConverter(vectorBar);

    assertThat(converterBar.toByte(0), is((byte) 10));
    assertThat(converterBar.toByte(1), is((byte) -10));
    assertThat(converterBar.toInt(0), is(10));
    assertThat(converterBar.toInt(1), is(-10));
    assertThat(converterBar.toLong(0), is(10L));
    assertThat(converterBar.toLong(1), is(-10L));
    vectorBar.clear();
  }
}
