package de.sjwimmer.ta4jchart.chartbuilder.mouse;

import de.sjwimmer.ta4jchart.chartbuilder.toolbar.TacAutoRangeButton;
import de.sjwimmer.ta4jchart.chartbuilder.utils.TacChartUtils;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;

import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Rectangle2D;

public class PanAndShiftZoomHandler extends MouseAdapter implements MouseWheelListener {

    private final ChartPanel chartPanel;
    private final TacAutoRangeButton tacAutoRangeButton; // Can be null if not used

    private boolean panningActive = false;
    private Point panLastPoint;

    private boolean delegatingToChartPanelZoom = false;
    private Point zoomInitialScreenPoint; // Store the initial point for zoom gesture detection

    public PanAndShiftZoomHandler(ChartPanel chartPanel, TacAutoRangeButton tacAutoRangeButton) {
        this.chartPanel = chartPanel;
        this.tacAutoRangeButton = tacAutoRangeButton;
        // Register this handler as a mouse wheel listener
        chartPanel.addMouseWheelListener(this);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        // Determine if the press is within the data area
        Rectangle2D screenDataArea = chartPanel.getScreenDataArea(e.getX(), e.getY());
        if (screenDataArea == null) { // Not over a plot, or plot not ready
             // If you want to ensure click must be in data area:
             // if (screenDataArea == null || !screenDataArea.contains(e.getPoint())) return;
        }


        if (e.isShiftDown()) {
            // Shift is down: prepare to delegate to ChartPanel's zoom mechanism
            delegatingToChartPanelZoom = true;
            zoomInitialScreenPoint = e.getPoint(); // Store for later check if actual zoom drag occurred
            
            // Temporarily enable ChartPanel's zoom. ChartPanel's own listener
            // (which runs before this one if added in constructor) will then handle it.
            // If our listener runs first (which it does as it's added after ChartPanel's internals),
            // we enable zoom, and then ChartPanel's listener sees zoom is enabled.
            this.chartPanel.setDomainZoomable(true);
            this.chartPanel.setRangeZoomable(true); 
            // Do NOT consume the event, so ChartPanel's internal listeners can process it.
        } else {
            // No Shift: initiate panning, only if inside data area
            if (screenDataArea != null && screenDataArea.contains(e.getPoint())) {
                panningActive = true;
                panLastPoint = e.getPoint();
                chartPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                e.consume(); // Consume to prevent any other default ChartPanel actions
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (panningActive) {
            // Ensure button is still pressed; it might have been released outside the window
            if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == 0) {
                panningActive = false;
                chartPanel.setCursor(Cursor.getDefaultCursor());
                return;
            }

            JFreeChart chart = chartPanel.getChart();
            if (chart == null) return;

            org.jfree.chart.plot.Plot plot = chart.getPlot();
            ValueAxis domainAxis = null;
            ValueAxis rangeAxis = null;

            if (plot instanceof CombinedDomainXYPlot) {
                domainAxis = ((CombinedDomainXYPlot) plot).getDomainAxis();
                // For CombinedDomainXYPlot, we'd need to determine which subplot the mouse is over
                // This is a simplified approach that uses the first subplot's range axis
                if (((CombinedDomainXYPlot) plot).getSubplots().size() > 0) {
                    XYPlot subplot = (XYPlot) ((CombinedDomainXYPlot) plot).getSubplots().get(0);
                    rangeAxis = subplot.getRangeAxis();
                }
            } else if (plot instanceof XYPlot) {
                domainAxis = ((XYPlot) plot).getDomainAxis();
                rangeAxis = ((XYPlot) plot).getRangeAxis();
            }

            if (domainAxis instanceof DateAxis) {
                DateAxis dateAxis = (DateAxis) domainAxis;
                
                double dx = e.getX() - panLastPoint.getX();
                // Pan based on pixel movement relative to chart width
                Rectangle2D dataArea = chartPanel.getScreenDataArea();
                if (dataArea == null || dataArea.getWidth() <= 0) return;

                double chartWidth = dataArea.getWidth();
                double panPercentX = -dx / chartWidth; // Negative: drag right moves time forward (data left)
                
                dateAxis.pan(panPercentX);
                
                // Handle Y-axis panning when auto-range is disabled
                if (tacAutoRangeButton != null && !tacAutoRangeButton.isSelected() && rangeAxis != null) {
                    double dy = e.getY() - panLastPoint.getY();
                    double chartHeight = dataArea.getHeight();
                    if (chartHeight > 0) {
                        double panPercentY = dy / chartHeight; // Positive: drag down moves values up
                        rangeAxis.pan(panPercentY);
                    }
                }
                
                panLastPoint = e.getPoint();

                if (tacAutoRangeButton != null && tacAutoRangeButton.isSelected()) {
                    TacChartUtils.applyAutoRangeState(chart, true); 
                }
            }
            e.consume();
        } else if (delegatingToChartPanelZoom) {
            // If delegating, ChartPanel's internal mouseDragged listener will handle drawing the zoom rectangle
            // because setDomainZoomable/setRangeZoomable were set to true.
            // Do NOT consume the event.
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        if (panningActive) {
            panningActive = false;
            chartPanel.setCursor(Cursor.getDefaultCursor());
            e.consume();
        } else if (delegatingToChartPanelZoom) {
            // Zoom was active. ChartPanel's internal mouseReleased listener will perform the actual zoom.
            // We need to reset the zoomable flags *after* ChartPanel has had a chance to process the release.
            final Point releaseScreenPoint = e.getPoint(); // Capture for use in invokeLater

            SwingUtilities.invokeLater(() -> {
                chartPanel.setDomainZoomable(false);
                chartPanel.setRangeZoomable(false);
                
                // If zoom occurred and auto-range Y is on, re-apply it.
                // A zoom is considered to have occurred if the mouse was dragged sufficiently.
                if (tacAutoRangeButton != null && tacAutoRangeButton.isSelected() && chartPanel.getChart() != null) {
                    if (zoomInitialScreenPoint != null &&
                        (Math.abs(releaseScreenPoint.x - zoomInitialScreenPoint.x) > ChartPanel.DEFAULT_MINIMUM_DRAW_WIDTH ||
                         Math.abs(releaseScreenPoint.y - zoomInitialScreenPoint.y) > ChartPanel.DEFAULT_MINIMUM_DRAW_HEIGHT)) {
                        
                        TacChartUtils.applyAutoRangeState(chartPanel.getChart(), true);
                    }
                }
                zoomInitialScreenPoint = null; // Reset for next potential zoom
            });
            delegatingToChartPanelZoom = false;
            // Do NOT consume the event, so ChartPanel's listener can perform the zoom.
        }
    }
    
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        // Get the chart and check if it's valid
        JFreeChart chart = chartPanel.getChart();
        if (chart == null) {
            return;
        }
        
        // Get the data area at the mouse position
        Rectangle2D screenDataArea = chartPanel.getScreenDataArea(e.getX(), e.getY());
        if (screenDataArea == null || !screenDataArea.contains(e.getPoint())) {
            return; // Not over the plot area
        }
        
        // Get the plot and domain axis
        org.jfree.chart.plot.Plot plot = chart.getPlot();
        ValueAxis domainAxis = null;
        ValueAxis rangeAxis = null;
        
        if (plot instanceof CombinedDomainXYPlot) {
            domainAxis = ((CombinedDomainXYPlot) plot).getDomainAxis();
            // For CombinedDomainXYPlot, get the range axis of the subplot under the mouse
            if (((CombinedDomainXYPlot) plot).getSubplots().size() > 0) {
                XYPlot subplot = (XYPlot) ((CombinedDomainXYPlot) plot).getSubplots().get(0);
                rangeAxis = subplot.getRangeAxis();
            }
        } else if (plot instanceof XYPlot) {
            domainAxis = ((XYPlot) plot).getDomainAxis();
            rangeAxis = ((XYPlot) plot).getRangeAxis();
        }
        
        // Check if Shift key is down and auto-range is disabled for Y-axis zooming
        boolean shiftDown = e.isShiftDown();
        boolean autoRangeDisabled = tacAutoRangeButton != null && !tacAutoRangeButton.isSelected();
        
        // Calculate zoom factor - use a smaller factor than TacZoomButtons
        // Negative rotation = zoom in, positive rotation = zoom out
        double zoomFactor = e.getWheelRotation() < 0 ? 0.85 : 1.15; // Smaller factor for more precise zooming
        
        // If Shift is pressed and auto-range is disabled, zoom Y axis
        if (shiftDown && autoRangeDisabled && rangeAxis != null) {
            // Get current Y-axis range
            double lower = rangeAxis.getRange().getLowerBound();
            double upper = rangeAxis.getRange().getUpperBound();
            double length = upper - lower;
            double newLength = length * zoomFactor;
            
            // Calculate the mouse position as a fraction of the data area height
            double mouseY = e.getY();
            double areaY = screenDataArea.getY();
            double areaHeight = screenDataArea.getHeight();
            double positionFactor = 1.0 - ((mouseY - areaY) / areaHeight); // Invert because Y coordinates increase downward
            
            // Calculate new bounds centered around mouse position
            double pointOnAxis = lower + (length * positionFactor);
            double newLower = pointOnAxis - (newLength * positionFactor);
            double newUpper = newLower + newLength;
            
            // Set the new range
            rangeAxis.setRange(newLower, newUpper);
            e.consume();
        }
        // Otherwise zoom X axis (default behavior)
        else if (domainAxis instanceof DateAxis) {
            DateAxis dateAxis = (DateAxis) domainAxis;
            
            // Get current X-axis range
            double lower = dateAxis.getRange().getLowerBound();
            double upper = dateAxis.getRange().getUpperBound();
            double length = upper - lower;
            double newLength = length * zoomFactor;
            
            // Calculate the mouse position as a fraction of the data area width
            double mouseX = e.getX();
            double areaX = screenDataArea.getX();
            double areaWidth = screenDataArea.getWidth();
            double positionFactor = (mouseX - areaX) / areaWidth;
            
            // Calculate new bounds centered around mouse position
            double pointOnAxis = lower + (length * positionFactor);
            double newLower = pointOnAxis - (newLength * positionFactor);
            double newUpper = newLower + newLength;
            
            // Set the new range
            dateAxis.setRange(newLower, newUpper);
            
            // Handle Y-axis auto-range if needed
            if (tacAutoRangeButton != null && tacAutoRangeButton.isSelected()) {
                TacChartUtils.applyAutoRangeState(chart, true);
            }
            
            e.consume();
        }
    }
}