package com.studyflow.view.materials;

import com.studyflow.model.QuestionSet;
import com.studyflow.model.StudyMaterial;
import com.studyflow.service.MaterialLibrary.Passage;
import com.studyflow.service.StudyAssistant;
import com.studyflow.service.StudyAssistant.Difficulty;
import com.studyflow.service.StudyAssistant.Mode;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StudyAssistantPanelTest {
    @Test
    void disablesQuestionGenerationOfflineAndRoutesConfiguredQuestionsToPreparation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var actions = new Capture();
            var panel = new MaterialPanel(actions);
            panel.showData(List.of(new StudyMaterial("source-id", "Optics", "optics.pdf", "", null, Instant.EPOCH)),
                    List.of());
            component(panel, "materialTabs", JTabbedPane.class).setSelectedIndex(1);
            var modes = component(panel, "materialMode", JComboBox.class);
            var run = component(panel, "materialGenerate", JButton.class);
            modes.setSelectedItem(Mode.MCQ);
            assertFalse(run.isEnabled());
            modes.setSelectedItem(Mode.REVISION_NOTES);
            assertTrue(run.isEnabled());

            panel.setRemoteAvailable(true);
            modes.setSelectedItem(Mode.MCQ);
            var scope = component(panel, "materialScope", JComboBox.class);
            scope.setSelectedIndex(1);
            component(panel, "materialTopic", JTextField.class).setText("Refraction");
            component(panel, "materialQuestionCount", JSpinner.class).setValue(2);
            component(panel, "materialDifficulty", JComboBox.class).setSelectedItem(Difficulty.ADVANCED);
            assertTrue(run.isEnabled());
            run.doClick();
            assertEquals(new StudyAssistant.Request("Refraction", "source-id", Mode.MCQ, true, true, 2,
                    Difficulty.ADVANCED), actions.request);
        });
    }

    @Test
    void questionDetailsShowAnswersAndActualReferencesOnlyWhenSelected() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new MaterialPanel(new Capture());
            var question = new QuestionSet.Question("What bends?", List.of("Light", "Glass", "Prism", "Lamp"), 0,
                    "", "The source explains a change in direction.", List.of("S1"));
            var set = new QuestionSet(QuestionSet.Type.MCQ, List.of(question));
            panel.showAnswer(new StudyAssistant.Answer("Question result", true,
                    List.of(new Passage("source-id", "optics.pdf", 2, "Light bends.")), set));
            var detail = component(panel, "materialQuestionDetail", JTextArea.class);
            assertTrue(detail.getText().contains("What bends?"));
            assertTrue(detail.getText().contains("A. Light"));
            assertFalse(detail.getText().contains("Answer"));
            assertTrue(detail.getText().contains("[optics.pdf, p. 2]"));
            component(panel, "materialShowAnswers", JCheckBox.class).doClick();
            component(panel, "materialShowReferences", JCheckBox.class).doClick();
            assertTrue(detail.getText().contains("Answer"));
            assertTrue(detail.getText().contains("The source explains"));
            assertFalse(detail.getText().contains("optics.pdf"));
        });
    }

    private static <T extends Component> T component(Component root, String name, Class<T> type) {
        T result = find(root, name, type);
        assertNotNull(result, name);
        return result;
    }

    private static <T extends Component> T find(Component root, String name, Class<T> type) {
        if (name.equals(root.getName()) && type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T result = find(child, name, type);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static final class Capture implements MaterialPanel.Actions {
        private StudyAssistant.Request request;

        @Override public void upload(Path source, String title, String description, String subjectId) { }
        @Override public void search(String text) { }
        @Override public void open(String id) { }
        @Override public void delete(String id) { }
        @Override public void prepareStudy(StudyAssistant.Request request) { this.request = request; }
        @Override public void runStudy(StudyAssistant.Prepared prepared) { fail("Run was dispatched before review"); }
        @Override public void loadHistory() { }
        @Override public void clearHistory() { }
        @Override public void deleteHistory(String entryId) { }
    }
}
