package com.ripariandata.timberwolf.hbase;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * Unit test for HBaseManager.
 */
public class HBaseManagerTest
{
    /**
     * Our logger for this class.
     */
    private Logger logger = LoggerFactory.getLogger(HBaseManagerTest.class);

    /**
     * Creates an IHBaseTable with a specific name.
     * @param name The name of the IHBaseTable.
     * @return A IHBaseTable with a specific name.
     */
    private HBaseTable createNamedTable(final String name)
    {
        HBaseTable table = mock(HBaseTable.class);
        when(table.getName()).thenReturn(name);
        return table;
    }

    /**
     * Create test.
     */
    @Test
    public void testCreate()
    {
        HBaseManager hbase = new HBaseManager();
    }

    /**
     * Test that we can simply add.
     */
    @Test
    public void testAdd()
    {
        HBaseManager hbase = new HBaseManager();
        HBaseTable table = mock(HBaseTable.class);
        hbase.addTable(table);
    }

    /**
     * Test that we can get a table, and it is the same
     * as the one put in.
     */
    @Test
    public void testGetFromAdd()
    {
        String tableName = "defaultTableName";
        HBaseManager hbase = new HBaseManager();
        HBaseTable table = createNamedTable(tableName);
        hbase.addTable(table);
        IHBaseTable managerTable = hbase.getTable(tableName);
        Assert.assertEquals(table, managerTable);
        Assert.assertEquals(tableName, managerTable.getName());
    }

}
