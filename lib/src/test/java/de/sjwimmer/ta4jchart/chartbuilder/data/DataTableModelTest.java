package de.sjwimmer.ta4jchart.chartbuilder.data;

import de.sjwimmer.ta4jchart.chartbuilder.BarSeriesHelper;
import de.sjwimmer.ta4jchart.chartbuilder.converter.BarSeriesConverterImpl;
import org.jfree.data.xy.DefaultHighLowDataset;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseBarSeries;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataTableModelTest {

    @Test
    public void testDataModelWithCustomDataOnly() {
        final String columnName = "Test";
        final List<Object> columnValues = Arrays.asList("A", "B", "C", "1", "2", "3");
        TacDataTableModel dataTableModel = new TacDataTableModel();
        dataTableModel.addEntries(columnName, columnValues);

        assertEquals(columnName, dataTableModel.getColumnName(0));
        assertEquals(1, dataTableModel.getColumnCount());
        assertEquals(Object.class, dataTableModel.getColumnClass(0));

        for(int i = 0; i < columnValues.size(); i++){
            assertEquals(columnValues.get(i), dataTableModel.getValueAt(i,0));
        }
    }

    @Test
    public void testDataModelWithOHLCData() {
        TacDataTableModel dataTableModel = new TacDataTableModel();
        
        // Create OHLC data
        BaseBarSeries barSeries = new BaseBarSeries("Test Series", BarSeriesHelper.createBars(10.0, 20.0, 30.0));
        BarSeriesConverterImpl converter = new BarSeriesConverterImpl();
        DefaultHighLowDataset ohlcDataset = converter.convert(barSeries);
        
        dataTableModel.addEntries(ohlcDataset);
        
        // Should have Date and Close columns
        assertEquals("Date", dataTableModel.getColumnName(0));
        assertEquals("Close", dataTableModel.getColumnName(1));
        assertEquals(2, dataTableModel.getColumnCount());
        
        // Add custom data
        final String customColumnName = "Custom";
        final List<Object> customValues = Arrays.asList("X", "Y", "Z");
        dataTableModel.addEntries(customColumnName, customValues);
        
        // Should now have Date, Close, and Custom columns
        assertEquals("Date", dataTableModel.getColumnName(0));
        assertEquals("Close", dataTableModel.getColumnName(1));
        assertEquals("Custom", dataTableModel.getColumnName(2));
        assertEquals(3, dataTableModel.getColumnCount());
    }
}