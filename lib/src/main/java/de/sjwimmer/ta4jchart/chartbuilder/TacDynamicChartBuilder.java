package de.sjwimmer.ta4jchart.chartbuilder;

import de.sjwimmer.ta4jchart.chartbuilder.axis.BarIndexDateAxis;
import de.sjwimmer.ta4jchart.chartbuilder.converter.*;
import de.sjwimmer.ta4jchart.chartbuilder.data.TacDataTableModel;
import de.sjwimmer.ta4jchart.chartbuilder.dataset.DynamicWindowOHLCDataset;
import de.sjwimmer.ta4jchart.chartbuilder.dataset.DynamicWindowXYDataset;
import de.sjwimmer.ta4jchart.chartbuilder.listener.DomainAxisRangeChangeHandler;
import de.sjwimmer.ta4jchart.chartbuilder.renderer.*;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
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

/**
 * Dynamic chart builder for TA4J that implements IChartBuilderAdapter.
 * Supports dynamic datasets, multiple timeframes, and interactive chart features.
 */
public class TacDynamicChartBuilder implements IChartBuilderAdapter { // Implement adapter

    private static final Logger log = LoggerFactory.getLogger(TacDynamicChartBuilder.class);

	/** Chart theme for styling */
	private final TacChartTheme theme;
	/** The full BarSeries for the current timeframe */
	private BarSeries currentFullBarSeries;
	/** Multi-timeframe bar series for timeframe switching */
	private final IBarSeriesMultiTf multiTfBarSeries;
	/** Trading record for displaying trades on chart */
	private TradingRecord tradingRecord;
	/** Converter for bar series data to table model */
	private final BarSeriesConverter barSeriesConverter;
	/** Converter for indicators to time series */
	private final IndicatorToTimeSeriesConverter indicatorToTimeSeriesConverter;
	/** Converter for indicators to bar data */
	private final IndicatorToBarDataConverter indicatorToBarDataConverter;
	/** Mutable chart instance that changes on timeframe switch */
	private JFreeChart chart;

	   /** Dynamic OHLC dataset with sliding window */
	   private DynamicWindowOHLCDataset dynamicOHLCDataset;
	   /** Axis listener for handling range changes */
	   private DomainAxisRangeChangeHandler axisListener;

	/** Data table model for chart data */
	private final TacDataTableModel dataTableModel = new TacDataTableModel();
	   /** List of indicator configuration builders */
	   private final List<IndicatorConfiguration.Builder<?>> indicatorConfigBuilders = new ArrayList<>();

	/** Index for overlay indicators (dataset 0 is reserved for main dataset) */
	private int overlayIndicatorIndex = 1;

	/** Number of bars to display per 100px of viewport width */
	private static final int BARS_PER_100PX_VIEWPORT = 8;
	   /** Buffer size for dynamic dataset in bars */
	   private static final int DYNAMIC_DATASET_BUFFER_BARS = 100;

	public static TacDynamicChartBuilder of(BarSeries barSeries) {
		return of(barSeries, Theme.LIGHT);
	}

	public static TacDynamicChartBuilder of(BarSeries barSeries, Theme theme) {
		return new TacDynamicChartBuilder(barSeries, theme);
	}

	/**
	 * Private constructor for TacDynamicChartBuilder with bar series and theme.
	 * @param barSeries the initial bar series
	 * @param theme the chart theme
	 */
	private TacDynamicChartBuilder(BarSeries barSeries, Theme theme) {
		this(barSeries, new BarSeriesConverterImpl(), new IndicatorToTimeSeriesConverterImpl(), new IndicatorToBarDataConverterImpl(), theme);
	}

	/**
	 * Private constructor for TacDynamicChartBuilder with full dependency injection.
	 * @param initialBarSeries the initial bar series
	 * @param barSeriesPlotter converter for bar series data
	 * @param indicatorConverter converter for indicators to time series
	 * @param indToBarDataConverter converter for indicators to bar data
	 * @param chartTheme the chart theme
	 */
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
        ValueAxis domainAxis = combinedPlot.getDomainAxis();

        int barsToDisplay = calculateBarsToDisplayForViewport(chartPanelWidth, this.currentFullBarSeries.getBarCount());

        if (barsToDisplay <= 0) {
             domainAxis.setAutoRange(true);
             if (!this.currentFullBarSeries.isEmpty()) {
                 this.axisListener.axisChanged(null); // Trigger update for all datasets
             }
             return;
        }

        // The domain axis is index-based: one integer slot per bar. Show the last `barsToDisplay`
        // bars, with half a slot of padding on the left and one empty slot on the right so the last
        // candle is not glued to the axis. Setting the range triggers the axis listener, which
        // refreshes every dynamic dataset's window.
        int beginIndex = this.currentFullBarSeries.getBeginIndex();
        int endIndex = this.currentFullBarSeries.getEndIndex();
        int firstVisibleBarFullIndex = Math.max(beginIndex, endIndex - barsToDisplay + 1);

        domainAxis.setRange(firstVisibleBarFullIndex - 0.5, endIndex + 1.5);
        domainAxis.setAutoRange(false);
    }

    /**
     * Calculates the number of bars to display for the given viewport width.
     * @param panelWidth the width of the chart panel in pixels
     * @param totalBars the total number of bars available
     * @return number of bars to display, between 10 and totalBars
     */
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

        BarIndexDateAxis domainAxis = (BarIndexDateAxis) ((CombinedDomainXYPlot) this.chart.getPlot()).getDomainAxis();
        // Remember the currently visible time span (via the OLD series) so we can restore the same
        // window after switching, even though a given index means a different time per timeframe.
        BarSeries oldFullBarSeries = this.currentFullBarSeries;
        long oldViewStartTime = Long.MIN_VALUE;
        long oldViewEndTime = Long.MIN_VALUE;
        if (oldFullBarSeries != null && !oldFullBarSeries.isEmpty()) {
            oldViewStartTime = timeAtIndex(oldFullBarSeries, domainAxis.getRange().getLowerBound());
            oldViewEndTime = timeAtIndex(oldFullBarSeries, domainAxis.getRange().getUpperBound());
        }

        this.currentFullBarSeries = this.multiTfBarSeries.at(newTimeframe);
        if (this.currentFullBarSeries == null || this.currentFullBarSeries.isEmpty()) {
            log.error("Failed to get BarSeries for timeframe: {} or series is empty.", newTimeframe);
            this.currentFullBarSeries = new org.ta4j.core.BaseBarSeries("Empty " + newTimeframe.name());
        }
        this.dynamicOHLCDataset.setFullBarSeries(this.currentFullBarSeries, this.currentFullBarSeries.getName());
        domainAxis.setBarSeries(this.currentFullBarSeries);


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
        
        // Attempt to restore viewport or set initial for new timeframe. Setting the axis range
        // triggers the axisListener, which updates every dynamic dataset's window. We map the
        // previously visible time span onto the new series' bar indices.
        boolean restored = false;
        if (oldViewStartTime != Long.MIN_VALUE && !this.currentFullBarSeries.isEmpty()) {
            int newLowerIdx = nearestIndexForTime(this.currentFullBarSeries, oldViewStartTime);
            int newUpperIdx = nearestIndexForTime(this.currentFullBarSeries, oldViewEndTime);
            if (newUpperIdx > newLowerIdx) {
                domainAxis.setRange(newLowerIdx - 0.5, newUpperIdx + 0.5);
                domainAxis.setAutoRange(false);
                restored = true;
            }
        }
        if (!restored) {
            setInitialChartViewport(this.chart, -1); // Use a default width if not restored
        }

        return this.chart;
    }

    /** Returns the end-time millis of the bar nearest to the given (possibly fractional) index. */
    private static long timeAtIndex(BarSeries series, double indexValue) {
        int idx = (int) Math.round(indexValue);
        idx = Math.max(series.getBeginIndex(), Math.min(idx, series.getEndIndex()));
        return series.getBar(idx).getEndTime().toInstant().toEpochMilli();
    }

    /** Binary-searches the series for the bar whose end time is closest to the given millis. */
    private static int nearestIndexForTime(BarSeries series, long timeMillis) {
        int begin = series.getBeginIndex();
        int end = series.getEndIndex();
        int low = begin;
        int high = end;
        while (low < high) {
            int mid = low + (high - low) / 2;
            long midTime = series.getBar(mid).getEndTime().toInstant().toEpochMilli();
            if (midTime < timeMillis) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        // low is the first bar with endTime >= timeMillis; the previous bar may be closer.
        if (low > begin) {
            long lowTime = series.getBar(low).getEndTime().toInstant().toEpochMilli();
            long prevTime = series.getBar(low - 1).getEndTime().toInstant().toEpochMilli();
            if (Math.abs(prevTime - timeMillis) <= Math.abs(lowTime - timeMillis)) {
                return low - 1;
            }
        }
        return low;
    }

	/**
	 * Creates a new JFreeChart with dynamic dataset for the specified bar series.
	 * @param seriesForChart the bar series to create chart for
	 * @return new JFreeChart instance with dynamic dataset
	 */
	private JFreeChart createNewChartWithDynamicDataset(final BarSeries seriesForChart) {
		final String seriesName = (seriesForChart != null) ? seriesForChart.getName() : "No Data";
		final BarIndexDateAxis timeAxis = new BarIndexDateAxis("Time", seriesForChart);
		final NumberAxis valueAxis = new NumberAxis("Price/Value");
		final TacCandlestickRenderer candlestickRenderer = new TacCandlestickRenderer();
		
        this.dynamicOHLCDataset = new DynamicWindowOHLCDataset(seriesForChart, seriesName, DYNAMIC_DATASET_BUFFER_BARS);
        
		final XYPlot mainPlot = new OptimizedXYPlot(this.dynamicOHLCDataset, null, valueAxis, candlestickRenderer);
		setPlotTheme(mainPlot); // Theme the main plot
		final CombinedDomainXYPlot combinedDomainPlot = new OptimizedDomainXYPlot(timeAxis);
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

    /**
     * Adds an indicator to the chart plot based on configuration.
     * @param config the indicator configuration
     * @param fullSeriesForIndicator the full bar series for the indicator
     */
    private void addIndicatorToPlot(IndicatorConfiguration<?> config, BarSeries fullSeriesForIndicator) {
        // Important: Indicators operate on the *full* series provided.
        // This logic largely matches the original TacChartBuilder.
		final CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) this.chart.getPlot();
		final XYPlot mainCandlestickPlot = (XYPlot) combinedPlot.getSubplots().get(0);
		final Indicator<?> indicator = config.getIndicator(); // This indicator uses fullSeriesForIndicator

		if(config.getPlotType() == PlotType.OVERLAY) {
			if(config.getChartType() == ChartType.LINE) {
				// Use an index-based dynamic dataset so overlay lines line up with the gap-free candles
				// (a plain TimeSeriesCollection would plot against real timestamps and fall off the axis).
				@SuppressWarnings("unchecked")
				final DynamicWindowXYDataset overlayDataset = new DynamicWindowXYDataset(fullSeriesForIndicator, (Indicator<Num>) indicator, config.getName(), DYNAMIC_DATASET_BUFFER_BARS);
				this.axisListener.addDataset(overlayDataset);
				final XYLineAndShapeRenderer lineRend = createLineRenderer(config);
				mainCandlestickPlot.setRenderer(overlayIndicatorIndex, lineRend);
				mainCandlestickPlot.setDataset(overlayIndicatorIndex, overlayDataset);
				if (config.isAddToDataTable()) this.dataTableModel.addEntries(this.indicatorToTimeSeriesConverter.convert(indicator, config.getName()));
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
                subplot = new OptimizedXYPlot(barDs, null, subplotYAxis, barRend);
				if (config.isAddToDataTable()) this.dataTableModel.addEntries(barDs);
		} else {
            @SuppressWarnings("unchecked")
            final DynamicWindowXYDataset indicatorDataset = new DynamicWindowXYDataset(fullSeriesForIndicator, (Indicator<Num>) indicator, config.getName(), DYNAMIC_DATASET_BUFFER_BARS);
            this.axisListener.addDataset(indicatorDataset);
				final XYLineAndShapeRenderer lineRend = createLineRenderer(config);
            subplot = new OptimizedXYPlot(indicatorDataset, null, subplotYAxis, lineRend);
			if (config.isAddToDataTable()) {
                this.dataTableModel.addEntries(this.indicatorToTimeSeriesConverter.convert(indicator, config.getName()));
			}
		}
        setPlotTheme(subplot);
        combinedPlot.add(subplot, 1);
		}
	}

	/**
	 * Applies the current theme styling to the specified XY plot.
	 * @param plot the plot to theme
	 */
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

	/**
	 * Creates a bar renderer for the given indicator configuration.
	 * @param cfg the indicator configuration
	 * @return configured bar renderer
	 */
	private TacBarRenderer createBarRenderer(IndicatorConfiguration<?> cfg) {
		return new TacBarRenderer(cfg.getColor());
	}

	/**
	 * Creates a line and shape renderer for the given indicator configuration.
	 * @param cfg the indicator configuration
	 * @return configured line and shape renderer
	 */
	private XYLineAndShapeRenderer createLineRenderer(IndicatorConfiguration<?> cfg) {
		XYLineAndShapeRenderer rend = new OptmizedXYLineAndShapeRenderer(true, true); // lines, shapes
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