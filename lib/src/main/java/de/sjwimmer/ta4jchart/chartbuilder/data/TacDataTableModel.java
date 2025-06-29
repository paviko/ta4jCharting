package de.sjwimmer.ta4jchart.chartbuilder.data;

import de.sjwimmer.ta4jchart.chartbuilder.converter.TacBarDataset;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.XYSeries;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class TacDataTableModel extends AbstractTableModel{
	private static final long serialVersionUID = 7271440542755921838L;

	private final List<LocalDateTime> dates = new ArrayList<>();
	private final List<Number> closes = new ArrayList<>();

	private final Map<String, List<Object>> data = new LinkedHashMap<>();

	public void clearAllEntries() {
        this.dates.clear();
        this.closes.clear();
        this.data.clear();
        fireTableStructureChanged(); // Notify table that everything might have changed
    }

	public void addEntries(String columnName, List<Object> values) {
		this.data.put(columnName, values);
	}

	public void addEntries(TacBarDataset barDataset) {
		final List<Object> values = new ArrayList<>();
		final XYSeries series = barDataset.getSeries(0);
		for(int i = 0; i < series.getItemCount(); i++) {
			values.add(String.format("%.3f", series.getX(i).doubleValue()));
		}
		this.addEntries(barDataset.getSeriesKey(0).toString(), values);
	}

	public void addEntries(OHLCDataset xyDataset) {
		for (int i = 0; i < xyDataset.getItemCount(0); i++) {
			final Number close = xyDataset.getClose(0, i);
			final long x = xyDataset.getX(0, i).longValue();
			final java.time.LocalDateTime date = Instant.ofEpochMilli(x).atZone(ZoneId.systemDefault()).toLocalDateTime();
			this.closes.add(close);
			this.dates.add(date);
		}
        fireTableStructureChanged(); // Or fireTableDataChanged if structure is same
	}

	public void addEntries(TimeSeriesCollection timeSeriesCollection){
		final List<Object> values = new ArrayList<>();
		for(int i = 0; i < timeSeriesCollection.getSeries(0).getItemCount(); i ++){
			Number value = timeSeriesCollection.getSeries(0).getValue(i);
			values.add(String.format("%.3f", value.doubleValue()));
		}
		this.addEntries(timeSeriesCollection.getSeriesKey(0).toString(), values);
	}
	
	@Override
	public int getRowCount() {
		// If we have date/close data, use that for row count
		if (!dates.isEmpty() && !closes.isEmpty()) {
			return dates.size();
		}
		// Otherwise use custom data
		Optional<List<Object>> entry = data.values().stream().findAny();
		return entry.map(List::size).orElse(0);
	}

	@Override
    public String getColumnName(int col) {
		// If we have date/close data, use the fixed column layout
		if (!dates.isEmpty() && !closes.isEmpty()) {
			if(col == 0){
				return "Date";
			} else if(col == 1) {
				return "Close";
			}
			return data.keySet().toArray()[col - 2].toString();
		} else {
			// If we only have custom data, use the custom column names directly
			return data.keySet().toArray()[col].toString();
		}
    }
    
	@Override
	public int getColumnCount() {
		// If we have date/close data, add 2 for the Date and Close columns
		if (!dates.isEmpty() && !closes.isEmpty()) {
			return data.keySet().size() + 2;
		} else {
			// If we only have custom data, return just the custom column count
			return data.keySet().size();
		}
	}
	
	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		// If we have date/close data, use the fixed column layout
		if (!dates.isEmpty() && !closes.isEmpty()) {
			if(columnIndex == 0){
				return dates.get(rowIndex);
			} else if(columnIndex == 1) {
				return closes.get(rowIndex);
			}
			final String columnName = getColumnName(columnIndex);
			return data.get(columnName).get(rowIndex);
		} else {
			// If we only have custom data, get values directly from custom data
			final String columnName = getColumnName(columnIndex);
			return data.get(columnName).get(rowIndex);
		}
	}

}
