package de.sjwimmer.ta4jchart.chartbuilder.tradingrecord;

import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.IntConsumer;

public class TradingRecordPanel extends JPanel {

    private TacTradingRecordTradeTable tacTradingRecordTradeTable;
    private final TradingRecord tradingRecord;
    /** Callback that scrolls the chart to a given bar index; may be null when navigation is unavailable. */
    private final IntConsumer onNavigateToBarIndex;

    public TradingRecordPanel(TradingRecord tradingRecord, IntConsumer onNavigateToBarIndex) {
        super();
        this.tradingRecord = tradingRecord;
        this.onNavigateToBarIndex = onNavigateToBarIndex;
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        setBackground(UIManager.getColor("Button.background"));
        add(createPositionsView(tradingRecord));
        add(new JScrollPane(tacTradingRecordTradeTable));
    }

    private Component createPositionsView(TradingRecord tradingRecord) {
        final TacTradingRecordPositionTableModel tacTradingRecordPositionTableModel = new TacTradingRecordPositionTableModel(tradingRecord);
        final TacTradingRecordPositionTable tacTradingRecordPositionTable = new TacTradingRecordPositionTable(tacTradingRecordPositionTableModel);
        tacTradingRecordPositionTable.getSelectionModel().addListSelectionListener(e -> {
            if(!e.getValueIsAdjusting()) {
                final int selectedRow = tacTradingRecordPositionTable.getSelectedRow();
                if (selectedRow < 0) {
                    return;
                }
                final Position position = tradingRecord.getPositions().get(selectedRow);
                final TacTradingRecordTradeTableModel tacTradingRecordTradeTableModel = new TacTradingRecordTradeTableModel(position);
                if(this.tacTradingRecordTradeTable == null) {
                    this.tacTradingRecordTradeTable = new TacTradingRecordTradeTable(tacTradingRecordTradeTableModel);
                    addTradeNavigation(this.tacTradingRecordTradeTable);
                } else {
                    this.tacTradingRecordTradeTable.setModel(tacTradingRecordTradeTableModel);
                }
            }
        });
        addPositionNavigation(tacTradingRecordPositionTable);
        if(tradingRecord.getPositionCount() > 0) {
            tacTradingRecordPositionTable.setRowSelectionInterval(0,0);
        }
        return new JScrollPane(tacTradingRecordPositionTable);
    }

    /** Double-clicking a position row scrolls the chart to that position's entry bar. */
    private void addPositionNavigation(JTable positionTable) {
        positionTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                final int viewRow = positionTable.rowAtPoint(e.getPoint());
                if (viewRow < 0) {
                    return;
                }
                final int row = positionTable.convertRowIndexToModel(viewRow);
                final List<Position> positions = tradingRecord.getPositions();
                if (row < 0 || row >= positions.size()) {
                    return;
                }
                final Position position = positions.get(row);
                navigateToTrade(position != null ? position.getEntry() : null);
            }
        });
    }

    /** Double-clicking a trade row (entry/exit) scrolls the chart to that specific trade's bar. */
    private void addTradeNavigation(JTable tradeTable) {
        tradeTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                final int viewRow = tradeTable.rowAtPoint(e.getPoint());
                if (viewRow < 0) {
                    return;
                }
                final int row = tradeTable.convertRowIndexToModel(viewRow);
                final TableModel model = tradeTable.getModel();
                if (!(model instanceof TacTradingRecordTradeTableModel)) {
                    return;
                }
                final Position position = ((TacTradingRecordTradeTableModel) model).getPosition();
                if (position == null) {
                    return;
                }
                // Row 0 is the entry trade, row 1 is the exit trade (see TacTradingRecordTradeTableModel).
                navigateToTrade(row == 0 ? position.getEntry() : position.getExit());
            }
        });
    }

    private void navigateToTrade(Trade trade) {
        if (trade == null || onNavigateToBarIndex == null) {
            return;
        }
        try {
            onNavigateToBarIndex.accept(trade.getIndex());
        } catch (RuntimeException ex) {
            // A multi-timeframe trade index may not map onto the current series; ignore rather than break the UI.
        }
    }
}
