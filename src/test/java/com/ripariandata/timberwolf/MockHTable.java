/**
 * Copyright 2012 Riparian Data
 * http://www.ripariandata.com
 * contact@ripariandata.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * This file is licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package com.ripariandata.timberwolf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.TreeMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Increment;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Row;
import org.apache.hadoop.hbase.client.RowLock;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.Filter.ReturnCode;
import org.apache.hadoop.hbase.util.Bytes;

/**
 * Mock implementation of HTableInterface. Holds any supplied data in a
 * multi-dimensional NavigableMap which acts as a in-memory database. Useful for
 * testing classes that operate on data using an HTableInterface.
 * <p>
 * Instances should be get using <code>MockHTable.create()</code>. So while a
 * DAO with a saving operation like
 *
 * <pre>
 * public class MyDAO {
 *   private HTableInterface table;
 *
 *   public MyDAO(HTableInterface table) {
 *     this.table = table;
 *   }
 *
 *   public void saveData(byte[] id, byte[] data) throws IOException{
 *     Put put = new Put(id)
 *     put.add(family, qualifier, data);
 *     table.put(put);
 *   }
 * }
 * </pre>
 * <p>
 * is used in production like
 *
 * <pre>
 * MyDAO(new HTable(conf, tableName)).saveData(id, data);
 * </pre>
 * <p>
 * can be tested like
 *
 * <pre>
 * &#064;Test
 * public void testSave() {
 *   MockHTable table = MockHTable.create();
 *   MyDAO(table).saveData(id, data);
 *   Get get = new Get(id);
 *   Result result = table.get(get);
 *   assertArrayEquals(data, result.getValue(family, qualifier));
 * }
 * </pre>
 * <p>
 * MockHTable instances can also be initialized with pre-loaded data using one
 * of the String[][] or Map<String, Map<String, String>> data formats. While
 * String[][] parameter lets directly loading data from source code, Map can be
 * generated from a YAML document, using a parser.
 *
 * <pre>
 * // String[][]
 * MockHTable table = MockHTable.with(new String[][] {
 *   { &quot;&lt;rowid&gt;&quot;, &quot;&lt;column&gt;&quot;, &quot;&lt;value&gt;&quot; },
 *   { &quot;id&quot;, &quot;family:qualifier1&quot;, &quot;data1&quot; },
 *   { &quot;id&quot;, &quot;family:qualifier2&quot;, &quot;data2&quot; }
 * });
 * // YAML
 * String database = "id:\n  family:qualifier1: data1\n  family:qualifier2: data2\n";
 * MockHTable table = MockHTable.with((Map<String, Map<String, String>) new Yaml().load(database));
 * </pre>
 * <p>
 * If value is not supposed to be a String, but an int, double or anything,
 * <code>MockHTable.toEString()</code> can be used to turn it into a String.
 *
 * <p>
 * In order to simplify assertions for tests that should put anything into
 * database, MockHTable.read() works with two parameters (id and column) and
 * returns anything written to that row/column. So, previous test can be reduced to
 *
 * <pre>
 * &#064;Test
 * public void testSave() {
 *   MockHTable table = MockHTable.create();
 *   MyDAO(table).saveData(id, data);
 *   assertArrayEquals(data, table.read(id, "family:qualifier"));
 * }
 * </pre>
 * <p>
 *
 * @author erdem
 *
 */
public final class MockHTable implements HTableInterface
{
  /**
   * This is all the data for a MockHTable instance.
   */
  private NavigableMap<byte[], NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>>> data =
          new TreeMap<byte[], NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>>>(
                  Bytes.BYTES_COMPARATOR);

  /**
   * The table name, if specified in create.
   */
  private String name;

  /**
   * Helper method to convert some data into a list of KeyValue's.
   *
   * @param row
   *          row value of the KeyValue's
   * @param rowdata
   *          data to decode
   * @param maxVersions
   *          number of versions to return
   * @return List of KeyValue's
   */
  private static List<KeyValue> toKeyValue(final byte[] row,
                                           final NavigableMap<byte[], NavigableMap<byte[],
          NavigableMap<Long, byte[]>>> rowdata,
                                           final int maxVersions)
  {
    return toKeyValue(row, rowdata, 0, Long.MAX_VALUE, maxVersions);
  }

  /**
   * Helper method to convert some data into a list of KeyValue's with timestamp.
   * constraint
   *
   * @param row
   *          row value of the KeyValue's
   * @param rowdata
   *          data to decode
   * @param timestampStart
   *          start of the timestamp constraint
   * @param timestampEnd
   *          end of the timestamp constraint
   * @param maxVersions
   *          number of versions to return
   * @return List of KeyValue's
   */
  private static List<KeyValue> toKeyValue(final byte[] row,
                                           final NavigableMap<byte[], NavigableMap<byte[],
          NavigableMap<Long, byte[]>>> rowdata,
                                           final long timestampStart,
                                           final long timestampEnd,
                                           final int maxVersions)
  {
    List<KeyValue> ret = new ArrayList<KeyValue>();
    for (byte[] family : rowdata.keySet())
    {
      for (byte[] qualifier : rowdata.get(family).keySet())
      {
        int versionsAdded = 0;
        for (Entry<Long, byte[]> tsToVal : rowdata.get(family).get(qualifier).descendingMap().entrySet())
        {
          if (versionsAdded++ == maxVersions)
          {
            break;
          }
          Long timestamp = tsToVal.getKey();
          if (timestamp < timestampStart)
          {
            continue;
          }
          if (timestamp > timestampEnd)
          {
            continue;
          }
          byte[] value = tsToVal.getValue();
          ret.add(new KeyValue(row, family, qualifier, timestamp, value));
        }
      }
    }
    return ret;
  }

  /**
   * Clients should not rely on table names so this returns null, unless
   * specified.
   * @return null, unless the table name has been specified.
   */
  @Override
  public byte[] getTableName()
  {
      if (name != null)
      {
          return Bytes.toBytes(name);
      }
      return null;
  }

  /**
   * No configuration needed to work so this returns null.
   * @return null
   */
  @Override
  public Configuration getConfiguration()
  {
      return null;
  }

  /**
   * No table descriptor needed so this returns null.
   * @return null
   */
  @Override
  public HTableDescriptor getTableDescriptor()
  {
      return null;
  }

  @Override
  public boolean exists(final Get get) throws IOException
  {
    return data.containsKey(get.getRow());
  }

  @Override
  public Result get(final Get get) throws IOException
  {
    if (!exists(get))
    {
      return new Result();
    }
    byte[] row = get.getRow();
    List<KeyValue> kvs = new ArrayList<KeyValue>();
    if (!get.hasFamilies())
    {
      kvs = toKeyValue(row, data.get(row), get.getMaxVersions());
    }
    else
    {
      for (byte[] family : get.getFamilyMap().keySet())
      {
        if (data.get(row).get(family) == null)
        {
            continue;
        }
        NavigableSet<byte[]> qualifiers = get.getFamilyMap().get(family);
        if (qualifiers == null)
        {
            qualifiers = data.get(row).get(family).navigableKeySet();
        }
        for (byte[] qualifier : qualifiers)
        {
          if (qualifier == null)
          {
            qualifier = "".getBytes();
          }
          if (!data.get(row).containsKey(family) || !data.get(row).get(family).containsKey(qualifier)
                  || data.get(row).get(family).get(qualifier).isEmpty())
          {
              continue;
          }
          Entry<Long, byte[]> timestampAndValue = data.get(row).get(family).get(qualifier).lastEntry();
          kvs.add(new KeyValue(row, family, qualifier, timestampAndValue.getKey(), timestampAndValue.getValue()));
        }
      }
    }
    Filter filter = get.getFilter();
    if (filter != null)
    {
      filter.reset();
      List<KeyValue> nkvs = new ArrayList<KeyValue>(kvs.size());
      for (KeyValue kv : kvs)
      {
        if (filter.filterAllRemaining())
        {
          break;
        }
        if (filter.filterRowKey(kv.getBuffer(), kv.getRowOffset(), kv.getRowLength()))
        {
          continue;
        }
        if (filter.filterKeyValue(kv) == ReturnCode.INCLUDE)
        {
          nkvs.add(kv);
        }
        // ignoring next key hint which is a optimization to reduce file system IO
      }
      if (filter.hasFilterRow())
      {
        filter.filterRow(nkvs);
      }
      kvs = nkvs;
    }

    return new Result(kvs);
  }

  @Override
  public Result getRowOrBefore(final byte[] row, final byte[] family) throws IOException
  {
    // FIXME: implement
    return null;
  }

  @Override
  public ResultScanner getScanner(final Scan scan) throws IOException
  {
    final List<Result> ret = new ArrayList<Result>();
    byte[] st = scan.getStartRow();
    byte[] sp = scan.getStopRow();
    Filter filter = scan.getFilter();

    for (byte[] row : data.keySet())
    {
      // if row is equal to startRow emit it. When startRow (inclusive) and
      // stopRow (exclusive) is the same, it should not be excluded which would
      // happen w/o this control.
      if (st != null && st.length > 0 && Bytes.toString(st).compareTo(Bytes.toString(row)) != 0)
      {
        // if row is before startRow do not emit, pass to next row
        if (st != null && st.length > 0 && Bytes.toString(st).compareTo(Bytes.toString(row)) > 0)
        {
            continue;
        }
      // if row is equal to stopRow or after it do not emit, stop iteration
        if (sp != null && sp.length > 0 && Bytes.toString(sp).compareTo(Bytes.toString(row)) <= 0)
        {
            break;
        }
      }

      List<KeyValue> kvs = null;
      if (!scan.hasFamilies())
      {
        kvs = toKeyValue(row,
                         data.get(row),
                         scan.getTimeRange().getMin(),
                         scan.getTimeRange().getMax(),
                         scan.getMaxVersions());
      }
      else
      {
        kvs = new ArrayList<KeyValue>();
        for (byte[] family : scan.getFamilyMap().keySet())
        {
          if (data.get(row).get(family) == null)
          {
              continue;
          }
          NavigableSet<byte[]> qualifiers = scan.getFamilyMap().get(family);
          if (qualifiers == null)
          {
              qualifiers = data.get(row).get(family).navigableKeySet();
          }
          for (byte[] qualifier : qualifiers)
          {
            for (Long timestamp : data.get(row).get(family).get(qualifier).keySet())
            {
              if (timestamp < scan.getTimeRange().getMin())
              {
                  continue;
              }
              if (timestamp > scan.getTimeRange().getMax())
              {
                  continue;
              }
              byte[] value = data.get(row).get(family).get(qualifier).get(timestamp);
              kvs.add(new KeyValue(row, family, qualifier, timestamp, value));
            }
          }
        }
      }
      if (filter != null)
      {
        filter.reset();
        List<KeyValue> nkvs = new ArrayList<KeyValue>(kvs.size());
        for (KeyValue kv : kvs)
        {
          if (filter.filterAllRemaining())
          {
            break;
          }
          if (filter.filterRowKey(kv.getBuffer(), kv.getRowOffset(), kv.getRowLength()))
          {
            continue;
          }
          ReturnCode filterResult = filter.filterKeyValue(kv);
          if (filterResult == ReturnCode.INCLUDE)
          {
            nkvs.add(kv);
          }
          else if (filterResult == ReturnCode.NEXT_ROW)
          {
            break;
          }
          // ignoring next key hint which is a optimization to reduce file system IO
        }
        if (filter.hasFilterRow())
        {
          filter.filterRow(nkvs);
        }
        kvs = nkvs;
      }
      if (!kvs.isEmpty())
      {
        ret.add(new Result(kvs));
      }
    }

    return new ResultScanner()
    {
      private final Iterator<Result> iterator = ret.iterator();

      public Iterator<Result> iterator()
      {
        return iterator;
      }

      public Result[] next(final int nbRows) throws IOException
      {
        ArrayList<Result> resultSets = new ArrayList<Result>(nbRows);
        for (int i = 0; i < nbRows; i++)
        {
          Result next = next();
          if (next != null)
          {
            resultSets.add(next);
          }
          else
          {
            break;
          }
        }
        return resultSets.toArray(new Result[resultSets.size()]);
      }

      public Result next() throws IOException
      {
        try
        {
          return iterator().next();
        }
        catch (NoSuchElementException e)
        {
          return null;
        }
      }

      public void close()
      {

      }

    };
  }

  @Override
  public ResultScanner getScanner(final byte[] family) throws IOException
  {
    Scan scan = new Scan();
    scan.addFamily(family);
    return getScanner(scan);
  }

  @Override
  public ResultScanner getScanner(final byte[] family, final byte[] qualifier) throws IOException
  {
    Scan scan = new Scan();
    scan.addColumn(family, qualifier);
    return getScanner(scan);
  }

  @Override
  public void put(final Put put) throws IOException
  {
    byte[] row = put.getRow();
    NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> rowData = forceFind(data, row,
            new TreeMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>>(Bytes.BYTES_COMPARATOR));
    for (byte[] family : put.getFamilyMap().keySet())
    {
      NavigableMap<byte[], NavigableMap<Long, byte[]>> familyData = forceFind(rowData, family, new TreeMap<byte[],
              NavigableMap<Long, byte[]>>(Bytes.BYTES_COMPARATOR));
      for (KeyValue kv : put.getFamilyMap().get(family))
      {
        kv.updateLatestStamp(Bytes.toBytes(System.currentTimeMillis()));
        byte[] qualifier = kv.getQualifier();
        NavigableMap<Long, byte[]> qualifierData = forceFind(familyData, qualifier, new TreeMap<Long, byte[]>());
        qualifierData.put(kv.getTimestamp(), kv.getValue());
      }
    }
  }

  /**
   * Helper method to find a key in a map. If key is not found, newObject is
   * added to map and returned
   *
   * @param map
   *          map to extract value from
   * @param key
   *          key to look for
   * @param newObject
   *          set key to this if not found
   * @return found value or newObject if not found
   */
  private <K, V> V forceFind(final NavigableMap<K, V> map, final K key, final V newObject)
  {
    V dataLocal = map.get(key);
    if (dataLocal == null)
    {
        dataLocal = newObject;
      map.put(key, dataLocal);
    }
    return dataLocal;
  }

  @Override
  public void put(final List<Put> puts) throws IOException
  {
    for (Put put : puts)
    {
        put(put);
    }
  }

  /**
   * Checks if the value with given details exists in database, or is
   * non-existent in the case of value being null.
   *
   * @param row
   *          row
   * @param family
   *          family
   * @param qualifier
   *          qualifier
   * @param value
   *          value
   * @return true if value is not null and exists in db, or value is null and
   *         not exists in db, false otherwise
   */
  private boolean check(final byte[] row, final byte[] family, final byte[] qualifier, final byte[] value)
  {
    if (value == null || value.length == 0)
    {
        return !data.containsKey(row) || !data.get(row).containsKey(family)
                || !data.get(row).get(family).containsKey(qualifier);
    }
    else
    {
        return data.containsKey(row)
                && data.get(row).containsKey(family)
                && data.get(row).get(family).containsKey(qualifier)
                && !data.get(row).get(family).get(qualifier).isEmpty()
                && Arrays.equals(data.get(row).get(family).get(qualifier).lastEntry().getValue(), value);
    }

  }

  @Override
  public boolean checkAndPut(final byte[] row, final byte[] family, final byte[] qualifier, final byte[] value,
                             final Put put) throws IOException
  {
    if (check(row, family, qualifier, value))
    {
      put(put);
      return true;
    }
    return false;
  }

  @Override
  public void delete(final Delete delete) throws IOException
  {
    byte[] row = delete.getRow();
    if (data.get(row) == null)
    {
        return;
    }
    if (delete.getFamilyMap().size() == 0)
    {
      data.remove(row);
      return;
    }
    for (byte[] family : delete.getFamilyMap().keySet())
    {
      if (data.get(row).get(family) == null)
      {
          continue;
      }
      if (delete.getFamilyMap().get(family).isEmpty())
      {
        data.get(row).remove(family);
        continue;
      }
      for (KeyValue kv : delete.getFamilyMap().get(family))
      {
        data.get(row).get(kv.getFamily()).remove(kv.getQualifier());
      }
    }
  }

  @Override
  public void delete(final List<Delete> deletes) throws IOException
  {
    for (Delete delete : deletes)
    {
        delete(delete);
    }
  }

  @Override
  public boolean checkAndDelete(final byte[] row, final byte[] family, final byte[] qualifier,
      final byte[] value, final Delete delete) throws IOException
  {
    if (check(row, family, qualifier, value))
    {
      delete(delete);
      return true;
    }
    return false;
  }

  @Override
  public long incrementColumnValue(final byte[] row, final byte[] family,
      final byte[] qualifier, final long amount) throws IOException
  {
    return incrementColumnValue(row, family, qualifier, amount, true);
  }

  @Override
  public long incrementColumnValue(final byte[] row, final byte[] family,
      final byte[] qualifier, final long amount, final boolean writeToWAL) throws IOException
  {
    if (check(row, family, qualifier, null))
    {
      Put put = new Put(row);
      put.add(family, qualifier, Bytes.toBytes(amount));
      put(put);
      return amount;
    }
    long newValue = Bytes.toLong(data.get(row).get(family).get(qualifier).lastEntry().getValue()) + amount;
    data.get(row).get(family).get(qualifier).put(System.currentTimeMillis(), Bytes.toBytes(newValue));
    return newValue;
  }

  @Override
  public boolean isAutoFlush()
  {
    return true;
  }

  @Override
  public void flushCommits() throws IOException
  {

  }

  @Override
  public void close() throws IOException
  {

  }

  @Override
  public RowLock lockRow(final byte[] row) throws IOException
  {
    return null;
  }

  @Override
  public void unlockRow(final RowLock rl) throws IOException
  {

  }

  @Override
  public Object[] batch(final List<Row> actions) throws IOException, InterruptedException
  {
    List<Result> results = new ArrayList<Result>();
    for (Row r : actions)
    {
      if (r instanceof Delete)
      {
        delete((Delete) r);
        continue;
      }
      if (r instanceof Put)
      {
        put((Put) r);
        continue;
      }
      if (r instanceof Get)
      {
        results.add(get((Get) r));
      }
    }
    return results.toArray();
  }

  @Override
  public void batch(final List<Row> actions, final Object[] results) throws IOException, InterruptedException
  {

  }

  @Override
  public Result[] get(final List<Get> gets) throws IOException
  {
    List<Result> results = new ArrayList<Result>();
    for (Get g : gets)
    {
      results.add(get(g));
    }
    return results.toArray(new Result[results.size()]);
  }

  @Override
  public Result increment(final Increment increment) throws IOException
  {
    List<KeyValue> kvs = new ArrayList<KeyValue>();
    Map<byte[], NavigableMap<byte[], Long>> famToVal = increment.getFamilyMap();
    for (Entry<byte[], NavigableMap<byte[], Long>> ef : famToVal.entrySet())
    {
      byte[] family = ef.getKey();
      NavigableMap<byte[], Long> qToVal = ef.getValue();
      for (Entry<byte[], Long> eq : qToVal.entrySet())
      {
        incrementColumnValue(increment.getRow(), family, eq.getKey(), eq.getValue());
        kvs.add(new KeyValue(increment.getRow(), family, eq.getKey(), Bytes.toBytes(eq.getValue())));
      }
    }
    return new Result(kvs);
  }

  private MockHTable()
  {

  }

  /**
   * Default way of constructing a MockHTable.
   * @return a new MockHTable
   */
  public static MockHTable create()
  {
    return new MockHTable();
  }

  /**
   * Creates a MockHTable with the specified name.
   * @param name The name of this MockHTable.
   * @return a new MockHTable.
   */
  public static MockHTable create(final String name)
  {
      MockHTable table = new MockHTable();
      table.name = name;
      return table;
  }

  /**
   * Create a MockHTable with some pre-loaded data. Parameter should be a map of
   * column-to-data mappings of rows. It can be created with a YAML like
   *
   * <pre>
   * rowid:
   *   family1:qualifier1: value1
   *   family2:qualifier2: value2
   * </pre>
   *
   * @param dump
   *          pre-loaded data
   * @return a new MockHTable loaded with given data
   */
  public static MockHTable with(final Map<String, Map<String, String>> dump)
  {
    MockHTable ret = new MockHTable();
    for (String row : dump.keySet())
    {
      for (String column : dump.get(row).keySet())
      {
        String val = dump.get(row).get(column);
        put(ret, row, column, val);
      }
    }
    return ret;
  }

  /**
   * Helper method of pre-loaders, adds parameters to data.
   *
   * @param ret
   *          data to load into
   * @param row
   *          rowid
   * @param column
   *          family:qualifier encoded value
   * @param val
   *          value
   */
  private static void put(final MockHTable ret, final String row, final String column, final String val)
  {
    String[] fq = split(column);
    byte[] family = Bytes.toBytesBinary(fq[0]);
    byte[] qualifier = Bytes.toBytesBinary(fq[1]);
    NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> families =
            ret.forceFind(ret.data, Bytes.toBytesBinary(row), new TreeMap<byte[], NavigableMap<byte[],
                    NavigableMap<Long, byte[]>>>(Bytes.BYTES_COMPARATOR));
    NavigableMap<byte[], NavigableMap<Long, byte[]>> qualifiers = ret.forceFind(families, family, new TreeMap<byte[],
            NavigableMap<Long, byte[]>>(Bytes.BYTES_COMPARATOR));
    NavigableMap<Long, byte[]> values = ret.forceFind(qualifiers, qualifier, new TreeMap<Long, byte[]>());
    values.put(System.currentTimeMillis(), Bytes.toBytesBinary(val));
  }

  /**
   * Create a MockHTable with some pre-loaded data. Parameter should be an array
   * of string arrays which define every column value individually.
   *
   * <pre>
   * new String[][] {
   *   { "&lt;rowid&gt;", "&lt;column&gt;", "&lt;value&gt;" },
   *   { "id", "family:qualifier1", "data1" },
   *   { "id", "family:qualifier2", "data2" }
   * });
   * </pre>
   *
   * @param dump
   * @return
   */
  public static MockHTable with(final String[][] dump)
  {
    MockHTable ret = new MockHTable();
    for (String[] row : dump)
    {
      put(ret, row[0], row[1], row[2]);
    }
    return ret;
  }

  /**
   * Column identification helper.
   *
   * @param column
   *          column name in the format family:qualifier
   * @return <code>{"family", "qualifier"}</code>
   */
  private static String[] split(final String column)
  {
    return new String[]
            {
        column.substring(0, column.indexOf(':')),
        column.substring(column.indexOf(':') + 1)
            };
  }

  /**
   * Read a value saved in the object. Useful for making assertions in tests.
   *
   * @param rowid
   *          rowid of the data to read
   * @param column
   *          family:qualifier of the data to read
   * @return value or null if row or column of the row does not exist
   */
  public byte[] read(final String rowid, final String column)
  {
    NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> row = data.get(Bytes.toBytesBinary(rowid));
    if (row == null)
    {
        return null;
    }
    String[] fq = split(column);
    byte[] family = Bytes.toBytesBinary(fq[0]);
    byte[] qualifier = Bytes.toBytesBinary(fq[1]);
    if (!row.containsKey(family))
    {
        return null;
    }
    if (!row.get(family).containsKey(qualifier))
    {
        return null;
    }
    return row.get(family).get(qualifier).lastEntry().getValue();
  }

  public static String toEString(final boolean val)
  {
    return Bytes.toStringBinary(Bytes.toBytes(val));
  }
  public static String toEString(final double val)
  {
    return Bytes.toStringBinary(Bytes.toBytes(val));
  }
  public static String toEString(final float val)
  {
    return Bytes.toStringBinary(Bytes.toBytes(val));
  }
  public static String toEString(final int val)
  {
    return Bytes.toStringBinary(Bytes.toBytes(val));
  }
  public static String toEString(final long val)
  {
    return Bytes.toStringBinary(Bytes.toBytes(val));
  }
  public static String toEString(final short val)
  {
    return Bytes.toStringBinary(Bytes.toBytes(val));
  }
}
