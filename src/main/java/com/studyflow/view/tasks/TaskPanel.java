package com.studyflow.view.tasks;

import com.studyflow.controller.StudyFlowController.TaskOrder;
import com.studyflow.controller.StudyFlowController.TaskQuery;
import com.studyflow.factory.TaskDraft;
import com.studyflow.model.Priority;
import com.studyflow.model.Subject;
import com.studyflow.model.Task;
import com.studyflow.model.TaskStatus;
import com.studyflow.model.TaskType;
import com.studyflow.service.TaskFilter;
import com.studyflow.view.ViewStyle;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/** Task list, query controls and task editor entry points. */
public final class TaskPanel extends JPanel {
    private static final String NO_SUBJECT = "No subject";
    private final Actions actions;
    private final JButton add = named(ViewStyle.primary(new JButton("Add task")), "taskAdd");
    private final JButton edit = named(new JButton("Edit"), "taskEdit");
    private final JButton delete = named(new JButton("Delete"), "taskDelete");
    private final JButton apply = named(new JButton("Apply"), "taskFilterApply");
    private final JButton reset = named(new JButton("Clear filters"), "taskFilterReset");
    private final JLabel queryError = TaskPresentation.literalLabel("");
    private final JLabel count = ViewStyle.muted("0 tasks");
    private final JTextField search = named(new JTextField(), "taskSearch");
    private final JTextField from = named(new JTextField(), "taskFilterFrom");
    private final JTextField through = named(new JTextField(), "taskFilterThrough");
    private final JComboBox<Choice<String>> subject = named(new JComboBox<>(), "taskFilterSubject");
    private final JComboBox<Choice<Priority>> priority = filter("taskFilterPriority", Priority.values());
    private final JComboBox<Choice<TaskStatus>> status = filter("taskFilterStatus", TaskStatus.values());
    private final JComboBox<Choice<TaskType>> type = filter("taskFilterType", TaskType.values());
    private final JComboBox<Choice<Boolean>> overdue = named(new JComboBox<>(), "taskFilterOverdue");
    private final JComboBox<TaskOrder> order = named(new JComboBox<>(TaskOrder.values()), "taskFilterSort");
    private final TaskTableModel tableModel = new TaskTableModel();
    private final JTable table = named(new JTable(tableModel), "taskTable");
    private final TaskDetailsPanel details;
    private final JSplitPane split;
    private List<Subject> subjects = List.of();
    private Map<String, String> subjectNames = Map.of();
    private TaskFormDialog activeForm;
    private boolean busy;

    public TaskPanel(Actions actions) {
        super(new BorderLayout(0, 14));
        this.actions = actions;
        details = new TaskDetailsPanel(actions::priority, actions::status);
        setBorder(BorderFactory.createEmptyBorder(24, 24, 18, 24));
        JPanel top = new JPanel(new BorderLayout(0, 14));
        top.add(heading(), BorderLayout.PAGE_START);
        top.add(filters(), BorderLayout.CENTER);
        add(top, BorderLayout.PAGE_START);
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, taskList(), details);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(10);
        split.setContinuousLayout(true);
        split.setResizeWeight(.77);
        split.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                reflowDetails();
            }
        });
        add(split, BorderLayout.CENTER);
        refreshControls();
    }

    private JPanel heading() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.add(ViewStyle.heading("My Tasks"), BorderLayout.CENTER);
        add.addActionListener(event -> openCreate());
        JPanel action = new JPanel(new FlowLayout(FlowLayout.TRAILING, 0, 0));
        action.add(add);
        bar.add(action, BorderLayout.LINE_END);
        return bar;
    }

    private JPanel filters() {
        JPanel card = ViewStyle.card(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        search.putClientProperty("JTextField.placeholderText", "Search by title, description or subject");
        search.getAccessibleContext().setAccessibleName("Search tasks");
        search.addActionListener(event -> submitQuery());
        from.putClientProperty("JTextField.placeholderText", "YYYY-MM-DD");
        through.putClientProperty("JTextField.placeholderText", "YYYY-MM-DD");
        from.addActionListener(event -> submitQuery());
        through.addActionListener(event -> submitQuery());
        subject.addItem(new Choice<>("Any subject", null));
        overdue.addItem(new Choice<>("Any", null));
        overdue.addItem(new Choice<>("Overdue only", true));
        overdue.addItem(new Choice<>("Not overdue", false));
        TaskPresentation.useLabels(subject, Choice::label);
        TaskPresentation.useLabels(overdue, Choice::label);
        TaskPresentation.useLabels(order, value -> value == TaskOrder.CREATED
                ? "Created date" : TaskPresentation.label(value));
        apply.addActionListener(event -> submitQuery());
        reset.addActionListener(event -> resetQuery());

        JPanel query = transparent(new BorderLayout(8, 0));
        query.add(search, BorderLayout.CENTER);
        JPanel buttons = transparent(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        buttons.add(reset);
        buttons.add(apply);
        query.add(buttons, BorderLayout.LINE_END);
        card.add(query, BorderLayout.PAGE_START);

        JPanel options = transparent(new GridLayout(2, 4, 8, 7));
        options.add(labeled("Subject", subject));
        options.add(labeled("Priority", priority));
        options.add(labeled("Status", status));
        options.add(labeled("Type", type));
        options.add(labeled("Overdue", overdue));
        options.add(labeled("Due from", from));
        options.add(labeled("Due through", through));
        options.add(labeled("Sort by", order));
        card.add(options, BorderLayout.CENTER);
        queryError.setName("taskFilterError");
        card.add(queryError, BorderLayout.PAGE_END);
        queryError.setVisible(false);
        return card;
    }

    private JPanel taskList() {
        JPanel card = ViewStyle.card(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        card.setMinimumSize(new Dimension(0, 138));
        JPanel header = transparent(new BorderLayout(8, 0));
        header.add(count, BorderLayout.LINE_START);
        JPanel buttons = transparent(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        buttons.add(edit);
        buttons.add(delete);
        header.add(buttons, BorderLayout.LINE_END);
        card.add(header, BorderLayout.PAGE_START);

        table.getAccessibleContext().setAccessibleName("Tasks");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(34);
        table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class, new TaskCell());
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(190);
        table.getColumnModel().getColumn(1).setPreferredWidth(115);
        table.getColumnModel().getColumn(2).setPreferredWidth(92);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(95);
        table.getColumnModel().getColumn(5).setPreferredWidth(102);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelection();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && table.rowAtPoint(event.getPoint()) != -1) {
                    openEdit();
                }
            }
        });
        edit.addActionListener(event -> openEdit());
        delete.addActionListener(event -> deleteSelected());
        card.add(ViewStyle.scroll(table), BorderLayout.CENTER);
        return card;
    }

    private void reflowDetails() {
        int orientation = split.getWidth() < 770 ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
        if (split.getOrientation() != orientation) {
            split.setOrientation(orientation);
            split.setResizeWeight(orientation == JSplitPane.VERTICAL_SPLIT ? .61 : .77);
            SwingUtilities.invokeLater(() -> split.setDividerLocation(split.getResizeWeight()));
        }
    }

    public void showTasks(List<Task> tasks, List<Subject> subjects) {
        Task selected = selectedTask();
        String selectedSubject = value(subject);
        this.subjects = List.copyOf(subjects);
        subjectNames = subjects.stream().collect(Collectors.toMap(Subject::getId, Subject::getName));
        subject.removeAllItems();
        subject.addItem(new Choice<>("Any subject", null));
        for (Subject item : subjects) {
            subject.addItem(new Choice<>(TaskPresentation.subject(item), item.getId()));
            if (item.getId().equals(selectedSubject)) {
                subject.setSelectedIndex(subject.getItemCount() - 1);
            }
        }
        tableModel.setRows(tasks);
        count.setText(tasks.size() == 1 ? "1 task" : tasks.size() + " tasks");
        if (selected != null) {
            for (int row = 0; row < tasks.size(); row++) {
                if (tasks.get(row).getId().equals(selected.getId())) {
                    table.setRowSelectionInterval(row, row);
                    break;
                }
            }
        }
        showSelection();
    }

    public void openCreate() {
        openForm(null);
    }

    public void selectTask(String id) {
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (tableModel.rows.get(row).getId().equals(id)) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return;
            }
        }
    }

    public Task currentTask() {
        return selectedTask();
    }

    public void clearFilters() {
        resetQuery();
    }

    public void addPlanningAction(java.util.function.Consumer<Task> action) {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                javax.swing.KeyStroke.getKeyStroke("ctrl shift P"), "taskPlanning");
        getActionMap().put("taskPlanning", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (!busy && selectedTask() != null) {
                    action.accept(selectedTask());
                }
            }
        });
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem planning = new javax.swing.JMenuItem("Recurrence and subtasks…");
        planning.addActionListener(event -> getActionMap().get("taskPlanning").actionPerformed(event));
        menu.add(planning);
        table.setComponentPopupMenu(menu);
        table.setToolTipText("Right-click the selected task for recurrence and subtasks (Ctrl+Shift+P).");
    }

    private void openEdit() {
        Task task = selectedTask();
        if (task != null) {
            openEdit(task);
        }
    }

    public void openEdit(Task task) {
        openForm(task);
    }

    private void openForm(Task task) {
        if (busy) {
            return;
        }
        if (activeForm != null && activeForm.isDisplayable()) {
            activeForm.toFront();
            return;
        }
        activeForm = new TaskFormDialog(SwingUtilities.getWindowAncestor(this), task, subjects, draft -> {
            if (task == null) {
                actions.create(draft);
            } else {
                actions.update(task.getId(), draft);
            }
        });
        activeForm.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (event.getWindow() == activeForm) {
                    activeForm = null;
                }
                refreshControls();
            }
        });
        activeForm.setVisible(true);
    }

    public void finishEdit() {
        if (activeForm != null && activeForm.isPending()) {
            activeForm.dispose();
        }
    }

    public boolean failEdit(String message) {
        if (activeForm == null || !activeForm.isPending()) {
            return false;
        }
        activeForm.showFailure(message);
        return true;
    }

    public boolean hasUnsavedEdit() {
        return activeForm != null && activeForm.isDisplayable() && activeForm.hasUnsavedChanges();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        refreshControls();
    }

    private void refreshControls() {
        add.setEnabled(!busy);
        edit.setEnabled(!busy && selectedTask() != null);
        delete.setEnabled(!busy && selectedTask() != null);
        for (JComponent input : new JComponent[]{search, subject, priority, status, type, overdue, from,
                through, order, apply, reset}) {
            input.setEnabled(!busy);
        }
        details.setBusy(busy);
    }

    private Task selectedTask() {
        int row = table.getSelectedRow();
        return row >= 0 && row < tableModel.getRowCount() ? tableModel.rows.get(row) : null;
    }

    private void showSelection() {
        Task selected = selectedTask();
        details.showTask(selected, selected == null ? "" : subjectName(selected));
        refreshControls();
    }

    private String subjectName(Task task) {
        String id = task.getSubjectId();
        return id == null ? NO_SUBJECT : subjectNames.getOrDefault(id, NO_SUBJECT);
    }

    private void deleteSelected() {
        Task task = selectedTask();
        if (task != null && JOptionPane.showConfirmDialog(this,
                TaskPresentation.literalLabel("Delete “" + task.getTitle() + "” and its subtasks?"),
                "Delete task", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
            actions.delete(task.getId());
        }
    }

    private void submitQuery() {
        try {
            TaskFilter filter = new TaskFilter(value(subject), value(priority), value(status), value(type),
                    TaskPresentation.optionalDate(from.getText(), "Due from"),
                    TaskPresentation.optionalDate(through.getText(), "Due through"), value(overdue));
            queryError.setVisible(false);
            actions.query(new TaskQuery(search.getText().strip(), filter, order.getItemAt(order.getSelectedIndex())));
        } catch (IllegalArgumentException failure) {
            queryError.setForeground(ViewStyle.color("danger"));
            queryError.setText(failure.getMessage());
            queryError.setVisible(true);
            revalidate();
        }
    }

    private void resetQuery() {
        search.setText("");
        from.setText("");
        through.setText("");
        for (JComboBox<?> input : new JComboBox<?>[]{subject, priority, status, type, overdue, order}) {
            input.setSelectedIndex(0);
        }
        submitQuery();
    }

    private static JPanel labeled(String label, JComponent input) {
        JPanel panel = transparent(new BorderLayout(0, 3));
        JLabel text = ViewStyle.muted(label);
        text.setLabelFor(input);
        panel.add(text, BorderLayout.PAGE_START);
        panel.add(input, BorderLayout.CENTER);
        return panel;
    }

    private static <E extends Enum<E>> JComboBox<Choice<E>> filter(String name, E[] values) {
        JComboBox<Choice<E>> box = named(new JComboBox<>(), name);
        box.addItem(new Choice<>("Any", null));
        for (E value : values) {
            box.addItem(new Choice<>(TaskPresentation.label(value), value));
        }
        TaskPresentation.useLabels(box, Choice::label);
        return box;
    }

    private static <T> T value(JComboBox<Choice<T>> box) {
        int index = box.getSelectedIndex();
        return index < 0 ? null : box.getItemAt(index).value();
    }

    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name);
        return component;
    }

    private static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    public interface Actions {
        void create(TaskDraft draft);
        void update(String id, TaskDraft draft);
        void delete(String id);
        void status(String id, TaskStatus value);
        void priority(String id, Priority value);
        void query(TaskQuery query);
    }

    private record Choice<T>(String label, T value) {
    }

    private final class TaskTableModel extends AbstractTableModel {
        private final String[] columns = {"Task", "Subject", "Type", "Priority", "Status", "Deadline"};
        private List<Task> rows = List.of();

        void setRows(List<Task> tasks) {
            rows = List.copyOf(tasks);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            Task task = rows.get(row);
            return switch (column) {
                case 0 -> task.getTitle();
                case 1 -> subjectName(task);
                case 2 -> TaskPresentation.label(task.getType());
                case 3 -> TaskPresentation.label(task.getPriority());
                case 4 -> TaskPresentation.label(task.getStatus());
                case 5 -> task.getDueDate() == null ? "—" : task.getDueDate().toString();
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }

    private final class TaskCell extends DefaultTableCellRenderer {
        TaskCell() {
            putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override
        public Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(source, value, selected, focused, row, column);
            setToolTipText(value.toString());
            if (!selected && column == 5 && tableModel.rows.get(row).isOverdue()) {
                setForeground(ViewStyle.color("danger"));
                setToolTipText("Overdue · " + value);
            } else {
                setForeground(selected ? source.getSelectionForeground() : source.getForeground());
            }
            return this;
        }
    }
}
