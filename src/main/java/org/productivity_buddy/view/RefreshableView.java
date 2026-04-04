package org.productivity_buddy.view;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import org.productivity_buddy.model.ProcessInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public interface RefreshableView {
    void refreshUI();

    default <T> void retainSortOrder(TableView<T> table, Runnable updateAction) {
        List<TableColumn<T, ?>> sortOrder = new ArrayList<>(table.getSortOrder());
        updateAction.run();
        if (!sortOrder.isEmpty()) {
            table.getSortOrder().setAll(sortOrder);
            table.sort();
        }
    }

    /**
     * Postavlja custom sort policy na TableView<ProcessInfo> koji uvek
     * gura Uncategorized procese na dno, bez obzira na smer sortiranja.
     */
    default void applyUncategorizedLastSortPolicy(TableView<ProcessInfo> table) {
        table.setSortPolicy(new Callback<TableView<ProcessInfo>, Boolean>() {
            @Override
            public Boolean call(TableView<ProcessInfo> param) {
                Comparator<ProcessInfo> tableComparator = param.getComparator();
                if (tableComparator == null) {
                    return true;
                }

                // obmotaj originalni comparator — Uncategorized uvek na dno
                Comparator<ProcessInfo> wrapped = new Comparator<ProcessInfo>() {
                    @Override
                    public int compare(ProcessInfo a, ProcessInfo b) {
                        boolean aUncat = "Uncategorized".equals(a.getCategory());
                        boolean bUncat = "Uncategorized".equals(b.getCategory());
                        if (aUncat && !bUncat) return 1;
                        if (!aUncat && bUncat) return -1;
                        return tableComparator.compare(a, b);
                    }
                };

                FXCollections.sort(param.getItems(), wrapped);
                return true;
            }
        });
    }
}
