package one.util.ideaplugin.screenshoter;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;

import java.awt.Toolkit;

/**
 * @author Tagir Valeev
 */
public class CopyImageAction extends DumbAwareAction {

    static final long SIZE_LIMIT_TO_WARN = 3_000_000L;

    @Override
    public void actionPerformed(AnActionEvent event) {
        var project = event.getProject();
        if (project == null) return;
        var editor = CopyImagePlugin.getEditor(event);

        if (editor == null) {
            CopyImagePlugin.showError(project, "'Copy as Image' is available in text editors only");
            return;
        }

        if (!editor.getSelectionModel().hasSelection()) {
            CopyImagePlugin.showError(project, "Please select the text fragment to copy");
            return;
        }

        var imageBuilder = new ImageBuilder(editor);
        if (imageBuilder.getSelectedSize() > SIZE_LIMIT_TO_WARN) {
            if (Messages.showYesNoDialog(
                    event.getProject(),
                    "Copying such a big image could be slow and may take a lot of memory. Proceed?",
                    "Code Screenshots",
                    "Yes, Copy It!",
                    "Cancel",
                    null
            ) != Messages.YES
            ) {
                return;
            }
        }
        var image = imageBuilder.createImage();

        if (image != null) {
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(image, (clipboard1, contents) -> {
            });

            NotificationGroupManager.getInstance()
                    .getNotificationGroup("Code Screenshots")
                    .createNotification("Image was copied to the clipboard", NotificationType.INFORMATION)
                    .setTitle("Code screenshots")
                    .notify(editor.getProject());
        }
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent event) {
        var presentation = event.getPresentation();
        var editor = CopyImagePlugin.getEditor(event);
        if (ActionPlaces.EDITOR_POPUP.equals(event.getPlace())) {
            var hasSelection = editor != null && editor.getSelectionModel().hasSelection();
            presentation.setEnabledAndVisible(hasSelection);
        } else {
            presentation.setVisible(true);
            presentation.setEnabled(editor != null);
        }
    }

}
