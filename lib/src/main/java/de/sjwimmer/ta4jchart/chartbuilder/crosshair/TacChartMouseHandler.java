package de.sjwimmer.ta4jchart.chartbuilder.crosshair;

import de.sjwimmer.ta4jchart.chartbuilder.GlobalConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.event.ChartChangeEventType;
import org.jfree.chart.event.OverlayChangeListener;
import org.jfree.chart.event.PlotChangeEvent;
import org.jfree.chart.event.PlotChangeListener;
import org.jfree.chart.panel.Overlay;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.general.DatasetChangeListener;
import org.jfree.data.xy.XYDataset;
import de.sjwimmer.ta4jchart.chartbuilder.dataset.DynamicWindowOHLCDataset;
import de.sjwimmer.ta4jchart.chartbuilder.IChartBuilderAdapter;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Iterator;

public class TacChartMouseHandler implements ChartMouseListener, Overlay, DatasetChangeListener {

    private static final Logger log = LogManager.getLogger(TacChartMouseHandler.class);

    private final ChartPanel chartPanel;
    private final CombinedDomainXYPlot combinedDomainXYPlot;
    private double x = Double.NaN;
    private double y = Double.NaN;
    private double xx = Double.NaN;
    private double yy = Double.NaN;
    private boolean sticky = true;
    long[] ohlcXValues;

    // Add to TacChartMouseHandler
    private DynamicWindowOHLCDataset dynamicDatasetForCrosshair;

    public TacChartMouseHandler(ChartPanel chartPanel) {
        this.chartPanel = chartPanel;
        this.chartPanel.addChartMouseListener(this);
        this.combinedDomainXYPlot = (CombinedDomainXYPlot) chartPanel.getChart().getPlot();
        final XYPlot mainPlot = (XYPlot) combinedDomainXYPlot.getSubplots().get(0);
        chartPanel.addOverlay(this);

        // Code to make TacChartMouseHandler aware of DynamicWindowOHLCDataset changes
        XYDataset mainDataset = mainPlot.getDataset(0);
        if (mainDataset instanceof DynamicWindowOHLCDataset) {
            this.dynamicDatasetForCrosshair = (DynamicWindowOHLCDataset) mainDataset;
            this.dynamicDatasetForCrosshair.addChangeListener(this); // Assuming TacChartMouseHandler implements DatasetChangeListener
            updateOhlcXValuesFromDynamicDataset(); // Populate initial values
        }

        // Add a PlotChangeListener to the mainPlot to detect if the dataset instance itself is swapped
        mainPlot.addChangeListener(new PlotChangeListener() {
            @Override
            public void plotChanged(PlotChangeEvent event) {
                if (event.getType() == ChartChangeEventType.DATASET_UPDATED) { // Check if dataset at index 0 changed
                    XYDataset currentMainDataset = mainPlot.getDataset(0);
                    if (dynamicDatasetForCrosshair != currentMainDataset) { // Instance changed
                        if (dynamicDatasetForCrosshair != null) {
                            dynamicDatasetForCrosshair.removeChangeListener(TacChartMouseHandler.this);
                        }
                        if (currentMainDataset instanceof DynamicWindowOHLCDataset) {
                            dynamicDatasetForCrosshair = (DynamicWindowOHLCDataset) currentMainDataset;
                            dynamicDatasetForCrosshair.addChangeListener(TacChartMouseHandler.this);
                            updateOhlcXValuesFromDynamicDataset();
                        } else {
                            dynamicDatasetForCrosshair = null;
                            ohlcXValues = new long[0]; // Clear if not dynamic
                        }
                    }
                }
            }
        });
        
        for(Object p: this.combinedDomainXYPlot.getSubplots()){
            if(p instanceof XYPlot) {
                XYPlot subPlot = (XYPlot) p;
                subPlot.setDomainCrosshairVisible(true);
                subPlot.setRangeCrosshairVisible(true);
            }
        }

        final XYDataset dataset = mainPlot.getDataset(0);
        final int entriesCount = dataset.getItemCount(0);
        ohlcXValues = new long[entriesCount];
        for(int i = 0; i< entriesCount; i++) {
            ohlcXValues[i] = (long) dataset.getX(0, i);
        }
    }

    @Override
    public void chartMouseClicked(ChartMouseEvent event) {
    }

    @Override
    public void chartMouseMoved(ChartMouseEvent event) {
        int x = event.getTrigger().getX();
        int y = event.getTrigger().getY();
        final Point p = new Point(x, y);
        final ChartRenderingInfo chartRenderingInfo = chartPanel.getChartRenderingInfo();

        final Point point = chartPanel.translateJava2DToScreen(p);
        final PlotRenderingInfo plotInfo = chartRenderingInfo.getPlotInfo();

        int subplotIndex = plotInfo.getSubplotIndex(point);

        if(subplotIndex >= 0) {
            Rectangle2D dataArea = plotInfo.getDataArea();
            double xx = combinedDomainXYPlot.getDomainAxis().java2DToValue(point.getX(), dataArea, combinedDomainXYPlot.getDomainAxisEdge());
            log.debug("y: {} yy: {}", x, xx);
            final Rectangle2D panelArea = chartPanel.getScreenDataArea(x, y);
            final java.util.List<?>subplots = combinedDomainXYPlot.getSubplots();
            final Iterator<?> iterator = subplots.iterator();
            int plotIndex = 0;
            while (iterator.hasNext()) {
                XYPlot subPlot = (XYPlot) iterator.next();
                subPlot.setDomainCrosshairVisible(true);
                subPlot.setDomainCrosshairValue(findClosestXValue(xx));
                if(subplotIndex == plotIndex && panelArea != null) {
                    double yy = subPlot.getRangeAxis().java2DToValue(point.getY(), panelArea, subPlot.getRangeAxisEdge());
                    log.debug("y: {} yy: {}", y, yy);
                    subPlot.setRangeCrosshairVisible(true);
                    subPlot.setRangeCrosshairValue(yy);
                    this.x = x;
                    this.y = y;
                    this.xx = xx;
                    this.yy = yy;
                } else {
                    subPlot.setRangeCrosshairVisible(false);
                }
                plotIndex++;
            }
        }
    }

    private double findClosestXValue(double xx) {
        if(this.sticky && this.ohlcXValues != null) {
            for (double x : this.ohlcXValues) {
                if (x >= xx) {
                    return x;
                }
            }
        }
        return xx;
    }

    @Override
    public void paintOverlay(Graphics2D g2, ChartPanel chartPanel) {
        if(!Double.isNaN(x) && !Double.isNaN(y)) {
            g2.drawString("Date: " + createDateString(xx), (int)x+10, (int)y+60);
            g2.drawString("Value: " + String.format("%.5f", yy), (int)x+10, (int)y+80);
        }
    }

    private String createDateString(double xx) {
        Date date = Date.from(Instant.ofEpochMilli((long) findClosestXValue(xx)));
        try {
            return GlobalConstants.DATE_WITH_TIME_FORMATTER.valueToString(date);
        } catch (ParseException e) {
            log.error("", e);
        }
        return date.toString();
    }

    @Override
    public void addChangeListener(OverlayChangeListener listener) {

    }

    @Override
    public void removeChangeListener(OverlayChangeListener listener) {

    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    public void updateDataset() {
        XYPlot mainPlot = (XYPlot)this.combinedDomainXYPlot.getSubplots().get(0);
        final XYDataset dataset = mainPlot.getDataset(0);

        if (dataset != null) {
            final int entriesCount = dataset.getItemCount(0);
            ohlcXValues = new long[entriesCount];
            for (int i = 0; i < entriesCount; i++) {
                ohlcXValues[i] = (long) dataset.getX(0, i);
            }
        }
    }

    // Implement DatasetChangeListener
    @Override
    public void datasetChanged(org.jfree.data.general.DatasetChangeEvent event) {
        if (event.getSource() == dynamicDatasetForCrosshair) {
            updateOhlcXValuesFromDynamicDataset();
        }
    }

    private void updateOhlcXValuesFromDynamicDataset() {
        if (dynamicDatasetForCrosshair == null) {
            this.ohlcXValues = new long[0];
            return;
        }
        final int itemCount = dynamicDatasetForCrosshair.getItemCount(0);
        if (this.ohlcXValues == null || this.ohlcXValues.length != itemCount) {
            this.ohlcXValues = new long[itemCount];
        }
        for (int i = 0; i < itemCount; i++) {
            this.ohlcXValues[i] = dynamicDatasetForCrosshair.getX(0, i).longValue();
        }
    }
}
