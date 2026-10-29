package one.util.ideaplugin.screenshoter;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jspecify.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import java.util.Arrays;

/**
 * @author Tagir Valeev
 */
public class CopyImageConfigurable implements SearchableConfigurable, Configurable.NoScroll {
    private CopyImageOptionsPanel myPanel;
    private final Project myProject;

    public CopyImageConfigurable(Project project) {
        this.myProject = project;
    }

    @Override
    public String getDisplayName() {
        return "Copy code as image";
    }

    @Override
    public String getId() {
        return "screenshoter";
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        myPanel = new CopyImageOptionsPanel();
        myPanel.init();
        return myPanel.myWholePanel;
    }

    @Override
    public boolean isModified() {
        var provider = myProject.getService(CopyImageOptionsProvider.class);
        return !provider.getState().equals(myPanel.toState());
    }

    @Override
    public void apply() {
        var provider = myProject.getService(CopyImageOptionsProvider.class);
        provider.loadState(myPanel.toState());
    }

    @Override
    public void reset() {
        var provider = myProject.getService(CopyImageOptionsProvider.class);
        myPanel.fromState(provider.getState());
    }

    @Override
    public void disposeUIResources() {
        myPanel = null;
    }

    public class CopyImageOptionsPanel {
        private static final double SLIDER_SCALE = 2;

        private JTextField myScale;
        private JCheckBox myChopIndentation;
        private JCheckBox myRemoveCaret;
        private JPanel myWholePanel;
        private JSlider mySlider;
        private JPanel mySaveDirectoryPanel;
        private JTextField myPadding;
        private TextFieldWithBrowseButton mySaveDirectory;
        private JComboBox<TransferableImage.Format> myFormat;

        CopyImageOptionsProvider.State toState() {
            var state = new CopyImageOptionsProvider.State();
            state.myChopIndentation = myChopIndentation.isSelected();
            state.myRemoveCaret = myRemoveCaret.isSelected();
            try {
                state.myScale = Double.parseDouble(myScale.getText().trim());
            } catch (NumberFormatException ignored) {
            }

            state.myDirectoryToSave = StringUtil.nullize(mySaveDirectory.getText());
            try {
                state.myPadding = Integer.parseInt(myPadding.getText());
            } catch (NumberFormatException ignored) {
            }

            state.myFormat = TransferableImage.Format.values()[myFormat.getSelectedIndex()];

            return state;
        }

        void fromState(CopyImageOptionsProvider.State state) {
            myChopIndentation.setSelected(state.myChopIndentation);
            myRemoveCaret.setSelected(state.myRemoveCaret);
            mySlider.setValue((int) (state.myScale * SLIDER_SCALE));
            mySaveDirectory.setText(StringUtil.notNullize(state.myDirectoryToSave));
            myPadding.setText(String.valueOf(state.myPadding));
            myFormat.setSelectedIndex(state.myFormat == null ? 0 : state.myFormat.ordinal());
        }

        void init() {
            mySlider.addChangeListener(e -> myScale.setText(String.valueOf(mySlider.getValue() / SLIDER_SCALE)));
            Arrays.asList(TransferableImage.Format.values()).forEach(myFormat::addItem);
        }

        private void createUIComponents() {
            var field = new TextFieldWithBrowseButton();
            field.addActionListener(e -> {
                var descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
                var current = field.getText().trim();
                var initialDir = current.isEmpty() ? null : LocalFileSystem.getInstance().findFileByPath(current);
                var chosen = FileChooser.chooseFile(descriptor, myProject, initialDir);
                if (chosen != null) {
                    field.setText(chosen.getPath());
                }
            });
            mySaveDirectoryPanel = field;
            mySaveDirectory = field;
        }
    }
}
