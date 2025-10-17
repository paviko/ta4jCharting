package de.sjwimmer.ta4jchart.chartbuilder;

import de.sjwimmer.ta4jchart.chartbuilder.converter.*;
import de.sjwimmer.ta4jchart.chartbuilder.data.TacDataTableModel;
import de.sjwimmer.ta4jchart.chartbuilder.dataset.DynamicWindowOHLCDataset;
import de.sjwimmer.ta4jchart.chartbuilder.dataset.DynamicWindowXYDataset;
import de.sjwimmer.ta4jchart.chartbuilder.listener.DomainAxisRangeChangeHandler;
import de.sjwimmer.ta4jchart.chartbuilder.renderer.*;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeriesCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import com.limemojito.trading.model.bar.Bar.Period;
import org.ta4j.core.num.Num;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;

public class TacDynamicChartBuilder implements IChartBuilderAdapter { // Implement adapter

    private static final Logger log = LoggerFactory.getLogger(TacDynamicChartBuilder.class);

	private final TacChartTheme theme;
	private BarSeries currentFullBarSeries; // The full BarSeries for the *current* timeframe
	private final IBarSeriesMultiTf multiTfBarSeries;
	private TradingRecord tradingRecord;
	private final BarSeriesConverter barSeriesConverter; // For data table model
	private final IndicatorToTimeSeriesConverter indicatorToTimeSeriesConverter;
	private final IndicatorToBarDataConverter indicatorToBarDataConverter;
	private JFreeChart chart; // Chart instance is mutable on timeframe switch

    private DynamicWindowOHLCDataset dynamicOHLCDataset;
    private DomainAxisRangeChangeHandler axisListener; // Store to remove/re-add on TF switch

	private final TacDataTableModel dataTableModel = new TacDataTableModel();
    private final List<IndicatorConfiguration.Builder<?>> indicatorConfigBuilders = new ArrayList<>();

	private int overlayIndicatorIndex = 1; // Dataset 0 is DynamicWindowOHLCDataset

	private static final int BARS_PER_100PX_VIEWPORT = 8; // For initial viewport
    private static final int DYNAMIC_DATASET_BUFFER_BARS = 100; // Buffer for dynamic dataset

	public static TacDynamicChartBuilder of(BarSeries barSeries) {
		return of(barSeries, Theme.LIGHT);
	}

	public static TacDynamicChartBuilder of(BarSeries barSeries, Theme theme) {
		return new TacDynamicChartBuilder(barSeries, theme);
	}

	private TacDynamicChartBuilder(BarSeries barSeries, Theme theme) {
		this(barSeries, new BarSeriesConverterImpl(), new IndicatorToTimeSeriesConverterImpl(), new IndicatorToBarDataConverterImpl(), theme);
	}
	
	private TacDynamicChartBuilder(BarSeries initialBarSeries, BarSeriesConverter barSeriesPlotter, 
                                 IndicatorToTimeSeriesConverter indicatorConverter, IndicatorToBarDataConverter indToBarDataConverter, Theme chartTheme) {
		this.theme = (chartTheme == Theme.DARK) ? new DarkTacChartTheme() : new LightTacChartTheme();
		this.barSeriesConverter = barSeriesPlotter;
		this.indicatorToTimeSeriesConverter = indicatorConverter;
		this.indicatorToBarDataConverter = indToBarDataConverter;
		
        this.currentFullBarSeries = initialBarSeries;
		if (initialBarSeries instanceof IBarSeriesMultiTf) {
			this.multiTfBarSeries = (IBarSeriesMultiTf) initialBarSeries;
		} else {
			this.multiTfBarSeries = null;
		}
		this.chart = createNewChartWithDynamicDataset(this.currentFullBarSeries);
	}

	public TacChart build() {
        // Add stored indicator configurations using the initial full bar series
        for (IndicatorConfiguration.Builder<?> builder : indicatorConfigBuilders) {
            addIndicatorToPlot(builder.build(), this.currentFullBarSeries);
        }
        // TacChart needs the IChartBuilderAdapter instance
		return new TacChart(chart, currentFullBarSeries, dataTableModel, tradingRecord, this);
	}
    
    @Override
    public DynamicWindowOHLCDataset getDynamicOHLCDataset() {
        return this.dynamicOHLCDataset;
    }

    @Override
    public void setInitialChartViewport(JFreeChart chartToConfigure, int chartPanelWidth) {
        if (this.currentFullBarSeries == null || this.currentFullBarSeries.isEmpty() || chartToConfigure == null || this.dynamicOHLCDataset == null) {
            if (this.dynamicOHLCDataset != null) this.dynamicOHLCDataset.updateWindow(0,0); // Clear window
            return;
        }

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chartToConfigure.getPlot();
        DateAxis dateAxis = (DateAxis) combinedPlot.getDomainAxis();

        long avgBarDuration = calculateAverageBarDuration(this.currentFullBarSeries);
        int barsToDisplay = calculateBarsToDisplayForViewport(chartPanelWidth, this.currentFullBarSeries.getBarCount());

        if (barsToDisplay <= 0) {
             dateAxis.setAutoRange(true);
             if (!this.currentFullBarSeries.isEmpty()) {
             this.axisListener.axisChanged(null); // Trigger update for all datasets
             }
             return;
        }
        
        int endIndex = this.currentFullBarSeries.getEndIndex();
        long lastBarEndTime = this.currentFullBarSeries.getBar(endIndex).getEndTime().toInstant().toEpochMilli();
        long viewUpperBound = lastBarEndTime + avgBarDuration; // Add padding

        int firstVisibleBarFullIndex = Math.max(this.currentFullBarSeries.getBeginIndex(), endIndex - barsToDisplay + 1);
        long viewLowerBound;
        if (firstVisibleBarFullIndex > this.currentFullBarSeries.getBeginIndex()) {
            // Start view from the end time of the bar *before* the first visible one
            viewLowerBound = this.currentFullBarSeries.getBar(firstVisibleBarFullIndex - 1).getEndTime().toInstant().toEpochMilli();
        } else {
            // First visible is the first bar in series, approximate its start time
            viewLowerBound = this.currentFullBarSeries.getBar(firstVisibleBarFullIndex).getEndTime().toInstant().toEpochMilli() - avgBarDuration;
        }
        
        if (viewLowerBound < viewUpperBound) {
            dateAxis.setRange(new Date(viewLowerBound), new Date(viewUpperBound)); // This triggers axisListener -> dynamicOHLCDataset.updateWindow()
            dateAxis.setAutoRange(false); 
        } else {
            dateAxis.setAutoRange(true); 
        if (!this.currentFullBarSeries.isEmpty()) {
            this.axisListener.axisChanged(new org.jfree.chart.event.AxisChangeEvent(dateAxis)); // Trigger update for all datasets
        }
        }
    }

    private long calculateAverageBarDuration(BarSeries series) {
        if (series == null || series.getBarCount() < 2) return 5 * 60 * 1000L; // Default 5 min
        if (series instanceof IBarSeriesMultiTf) {
            Period p = ((IBarSeriesMultiTf) series).getPeriod();
            if (p != null && p.getDuration() != null) return p.getDuration().toMillis();
        }
        long diff = series.getBar(series.getEndIndex()).getEndTime().toInstant().toEpochMilli() -
                    series.getBar(series.getEndIndex() - 1).getEndTime().toInstant().toEpochMilli();
        return (diff > 0) ? diff : 5 * 60 * 1000L;
    }

    private int calculateBarsToDisplayForViewport(int panelWidth, int totalBars) {
        int bars = (panelWidth > 0) ? (int)(((double)panelWidth / 100.0) * BARS_PER_100PX_VIEWPORT) : 100;
        bars = Math.max(10, bars); // Min 10 bars
        return Math.min(bars, totalBars);
    }

    @Override
    public JFreeChart switchTimeframe(Period newTimeframe) {
        if (this.multiTfBarSeries == null) {
            log.warn("Multi-timeframe series not available for switching.");
            return this.chart;
        }
        if (this.tradingRecord instanceof ITradingRecordMultiTf) {
			((ITradingRecordMultiTf) this.tradingRecord).setTargetPeriod(newTimeframe);
		}

        DateAxis oldDateAxis = (DateAxis) ((CombinedDomainXYPlot) this.chart.getPlot()).getDomainAxis();
        Date oldLower = oldDateAxis.getMinimumDate();
        Date oldUpper = oldDateAxis.getMaximumDate();

        this.currentFullBarSeries = this.multiTfBarSeries.at(newTimeframe);
        if (this.currentFullBarSeries == null || this.currentFullBarSeries.isEmpty()) {
            log.error("Failed to get BarSeries for timeframe: {} or series is empty.", newTimeframe);
            this.currentFullBarSeries = new org.ta4j.core.BaseBarSeries("Empty " + newTimeframe.name());
        }
        this.dynamicOHLCDataset.setFullBarSeries(this.currentFullBarSeries, this.currentFullBarSeries.getName());


        // Chart title
        this.chart.setTitle(this.currentFullBarSeries.getName());
        
        // Update data table model (uses full series)
        this.dataTableModel.clearAllEntries();
        if (!this.currentFullBarSeries.isEmpty()){
            this.dataTableModel.addEntries(this.barSeriesConverter.convert(this.currentFullBarSeries));
        }


    CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) this.chart.getPlot();
    XYPlot mainPlot = (XYPlot) combinedPlot.getSubplots().get(0);
    this.overlayIndicatorIndex = 1; // Reset for new indicators
    for (int i = mainPlot.getDatasetCount() - 1; i >= overlayIndicatorIndex; i--) {
        mainPlot.setDataset(i, null);
        mainPlot.setRenderer(i, null);
    }

    this.axisListener.clearAllDatasets();
    this.axisListener.addDataset(this.dynamicOHLCDataset);

    List<XYPlot> subplotsToRemove = new ArrayList<>();
    for (int i = 1; i < combinedPlot.getSubplots().size(); i++) { // Keep main plot at index 0
        subplotsToRemove.add((XYPlot) combinedPlot.getSubplots().get(i));
    }
        for (XYPlot subplot : subplotsToRemove) {
            combinedPlot.remove(subplot);
        }

        // Re-add all indicators using the new currentFullBarSeries
        for (IndicatorConfiguration.Builder<?> icBuilder : this.indicatorConfigBuilders) {
            addIndicatorToPlot(icBuilder.build(), this.currentFullBarSeries);
        }
        
        // Attempt to restore viewport or set initial for new timeframe
        // This will trigger the axisListener, which updates dynamicOHLCDataset's window
        DateAxis dateAxis = (DateAxis) combinedPlot.getDomainAxis();
        boolean restored = false;
        if (oldLower != null && oldUpper != null && !this.currentFullBarSeries.isEmpty()) {
            long firstBarTime = this.currentFullBarSeries.getBar(this.currentFullBarSeries.getBeginIndex()).getEndTime().toInstant().toEpochMilli();
            long lastBarTime = this.currentFullBarSeries.getBar(this.currentFullBarSeries.getEndIndex()).getEndTime().toInstant().toEpochMilli();
            long newLower = Math.max(oldLower.getTime(), firstBarTime);
            long newUpper = Math.min(oldUpper.getTime(), lastBarTime);
            if (newLower < newUpper && (newUpper - newLower > 60000)) { // Valid and reasonable range
                 dateAxis.setRange(new Date(newLower), new Date(newUpper));
                 dateAxis.setAutoRange(false);
                 restored = true;
            }
        }
        if (!restored) {
            setInitialChartViewport(this.chart, -1); // Use a default width if not restored
        }
        
        return this.chart;
    }

	private JFreeChart createNewChartWithDynamicDataset(final BarSeries seriesForChart) {
		final String seriesName = (seriesForChart != null) ? seriesForChart.getName() : "No Data";
		final DateAxis timeAxis = new DateAxis("Time");
		final NumberAxis valueAxis = new NumberAxis("Price/Value");
		final TacCandlestickRenderer candlestickRenderer = new TacCandlestickRenderer();
		
        this.dynamicOHLCDataset = new DynamicWindowOHLCDataset(seriesForChart, seriesName, DYNAMIC_DATASET_BUFFER_BARS);
        
		final XYPlot mainPlot = new XYPlot(this.dynamicOHLCDataset, null, valueAxis, candlestickRenderer);
		setPlotTheme(mainPlot); // Theme the main plot
		final CombinedDomainXYPlot combinedDomainPlot = new CombinedDomainXYPlot(timeAxis);
        combinedDomainPlot.setGap(10.0); // Gap between main plot and subplots
		combinedDomainPlot.add(mainPlot, 10); // Main plot gets more weight

		valueAxis.setAutoRangeIncludesZero(false);
		candlestickRenderer.setAutoWidthMethod(TacCandlestickRenderer.WIDTHMETHOD_SMALLEST);
		candlestickRenderer.setDrawVolume(false); // Candlestick renderer itself won't draw volume from OHLCDataset
		candlestickRenderer.setDefaultItemLabelsVisible(false);

		JFreeChart newChart = new JFreeChart(seriesName, JFreeChart.DEFAULT_TITLE_FONT, combinedDomainPlot, true);
		theme.apply(newChart);

        // Populate data table model with full data
        if (seriesForChart != null && !seriesForChart.isEmpty()) {
            dataTableModel.addEntries(this.barSeriesConverter.convert(seriesForChart));
        }

        // Detach old listener if any, and attach new one
        if (this.axisListener != null && combinedDomainPlot.getDomainAxis() != null) {
            combinedDomainPlot.getDomainAxis().removeChangeListener(this.axisListener);
        }
    this.axisListener = new DomainAxisRangeChangeHandler(combinedDomainPlot.getDomainAxis(), this.dynamicOHLCDataset);
        combinedDomainPlot.getDomainAxis().addChangeListener(this.axisListener);
        
        return newChart;
	}

    public TacDynamicChartBuilder withIndicator(IndicatorConfiguration.Builder<?> indicatorConfigBuilder) {
        this.indicatorConfigBuilders.add(indicatorConfigBuilder);
        // Indicators are added during build() or switchTimeframe()
        return this;
    }

    private void addIndicatorToPlot(IndicatorConfiguration<?> config, BarSeries fullSeriesForIndicator) {
        // Important: Indicators operate on the *full* series provided.
        // This logic largely matches the original TacChartBuilder.
		final CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) this.chart.getPlot();
		final XYPlot mainCandlestickPlot = (XYPlot) combinedPlot.getSubplots().get(0);
		final Indicator<?> indicator = config.getIndicator(); // This indicator uses fullSeriesForIndicator

		if(config.getPlotType() == PlotType.OVERLAY) {
			if(config.getChartType() == ChartType.LINE) {
				final TimeSeriesCollection tsColl = this.indicatorToTimeSeriesConverter.convert(indicator, config.getName());
				final XYLineAndShapeRenderer lineRend = createLineRenderer(config);
				mainCandlestickPlot.setRenderer(overlayIndicatorIndex, lineRend);
				mainCandlestickPlot.setDataset(overlayIndicatorIndex, tsColl);
				if (config.isAddToDataTable()) this.dataTableModel.addEntries(tsColl);
				overlayIndicatorIndex++;
			} else if(config.getChartType() == ChartType.BAR) {
				final TacBarDataset barDs = indicatorToBarDataConverter.convert(indicator, config.getName());
				final TacBarRenderer barRend = createBarRenderer(config);
				mainCandlestickPlot.setRenderer(overlayIndicatorIndex, barRend);
				mainCandlestickPlot.setDataset(overlayIndicatorIndex, barDs);
				if(config.isAddToDataTable()) this.dataTableModel.addEntries(barDs);
				overlayIndicatorIndex++;
			}
		} else if (config.getPlotType() == PlotType.SUBPLOT) {
            XYPlot subplot;
            NumberAxis subplotYAxis = new NumberAxis(config.getName());
            subplotYAxis.setAutoRangeIncludesZero(false); // Common for financial indicators
            subplotYAxis.setLabel(""); // Keep it clean

			if(config.getChartType() == ChartType.BAR) {
				final TacBarDataset barDs = indicatorToBarDataConverter.convert(indicator, config.getName());
				final TacBarRenderer barRend = createBarRenderer(config);
                subplot = new XYPlot(barDs, null, subplotYAxis, barRend);
				if (config.isAddToDataTable()) this.dataTableModel.addEntries(barDs);
		} else {
            @SuppressWarnings("unchecked")
            final DynamicWindowXYDataset indicatorDataset = new DynamicWindowXYDataset(fullSeriesForIndicator, (Indicator<Num>) indicator, config.getName(), DYNAMIC_DATASET_BUFFER_BARS);
            this.axisListener.addDataset(indicatorDataset);
				final XYLineAndShapeRenderer lineRend = createLineRenderer(config);
            subplot = new XYPlot(indicatorDataset, null, subplotYAxis, lineRend);
			if (config.isAddToDataTable()) {
                this.dataTableModel.addEntries(this.indicatorToTimeSeriesConverter.convert(indicator, config.getName()));
			}
		}
        setPlotTheme(subplot);
        combinedPlot.add(subplot, 1);
		}
	}

	private void setPlotTheme(XYPlot plot) {
		final Color labelFg = UIManager.getColor("Label.foreground");
        final Color panelBg = UIManager.getColor("Panel.background");
		plot.setBackgroundPaint(panelBg != null ? panelBg : Color.WHITE);
        plot.setDomainGridlinePaint(UIManager.getColor("controlShadow"));
        plot.setRangeGridlinePaint(UIManager.getColor("controlShadow"));

		if(plot.getRangeAxis() != null) {
			plot.getRangeAxis().setTickLabelPaint(labelFg);
			plot.getRangeAxis().setLabelPaint(labelFg);
		}
        // Domain axis is shared for CombinedDomainXYPlot, usually themed once on main plot or combined plot
		if(plot.getDomainAxis() != null && !(plot.getParent() instanceof CombinedDomainXYPlot) ) {
			plot.getDomainAxis().setTickLabelPaint(labelFg);
			plot.getDomainAxis().setLabelPaint(labelFg);
		}
	}

	private TacBarRenderer createBarRenderer(IndicatorConfiguration<?> cfg) {
		return new TacBarRenderer(cfg.getColor());
	}

	private XYLineAndShapeRenderer createLineRenderer(IndicatorConfiguration<?> cfg) {
		XYLineAndShapeRenderer rend = new XYLineAndShapeRenderer(true, true); // lines, shapes
		rend.setSeriesShape(0, cfg.getShape());
		rend.setSeriesPaint(0, cfg.getColor());
        rend.setSeriesStroke(0, new BasicStroke(1.5f));
		return rend;
	}

	public TacDynamicChartBuilder withTradingRecord(TradingRecord tr) {
		this.tradingRecord = tr;
		return this;
	}

	public void buildAndShow(String title) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame(title);
			frame.setLayout(new BorderLayout());
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.add(this.build());
			frame.pack();
            frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}
	public void buildAndShow(){ buildAndShow("Ta4j Dynamic Charting"); }

    // --- IChartBuilderAdapter Implementation ---
    @Override public BarSeries getCurrentBarSeries() { return this.currentFullBarSeries; }
    @Override public IBarSeriesMultiTf getMultiTfBarSeries() { return this.multiTfBarSeries; }
}