package com.studyflow.view.materials;

import com.studyflow.model.QuestionSet;
import com.studyflow.view.ViewStyle;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

/** Displays each validated question and its answer and sources on demand. */
final class QuestionSetPanel extends JPanel {
    private final DefaultListModel<QuestionSet.Question> questions = new DefaultListModel<>();
    private final JList<QuestionSet.Question> items = new JList<>(questions);
    private final JTextArea details = new JTextArea();
    private Function<String, String> sourceName;
    private boolean showAnswers;
    private boolean showReferences;

    QuestionSetPanel() {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        items.setName("materialQuestionList");
        items.getAccessibleContext().setAccessibleName("Generated questions");
        items.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        items.setVisibleRowCount(3);
        items.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean selected, boolean focused) {
                String prompt = value instanceof QuestionSet.Question question ? question.prompt() : "";
                super.getListCellRendererComponent(list, (index + 1) + ". " + prompt, index, selected, focused);
                setToolTipText(null);
                setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
                return this;
            }
        });
        items.addListSelectionListener(event -> displaySelected());
        details.setName("materialQuestionDetail");
        details.getAccessibleContext().setAccessibleName("Selected question, answer, and references");
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setBorder(BorderFactory.createEmptyBorder(8, 9, 8, 9));
        add(ViewStyle.scroll(items), BorderLayout.PAGE_START);
        add(ViewStyle.scroll(details), BorderLayout.CENTER);
    }

    void showSet(QuestionSet set, Function<String, String> sourceName) {
        this.sourceName = sourceName;
        questions.clear();
        questions.addAll(set.questions());
        items.setSelectedIndex(0);
    }

    void setVisibility(boolean answers, boolean references) {
        showAnswers = answers;
        showReferences = references;
        displaySelected();
    }

    private void displaySelected() {
        QuestionSet.Question question = items.getSelectedValue();
        if (question == null) {
            details.setText("");
            return;
        }
        StringBuilder text = new StringBuilder(question.prompt());
        for (int index = 0; index < question.choices().size(); index++) {
            text.append("\n\n").append(QuestionSet.choiceMarker(index)).append(" ").append(question.choices().get(index));
        }
        if (showAnswers) {
            text.append("\n\nAnswer\n").append(question.answerText()).append("\n\nWhy\n").append(question.explanation());
        }
        if (showReferences) {
            text.append("\n\nSources\n").append(String.join("\n", question.sourceIds().stream().map(sourceName).toList()));
        }
        details.setText(text.toString());
        details.setCaretPosition(0);
    }
}
