package one.util.ideaplugin.screenshoter;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.SystemProperties;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SaveImageAction extends AnAction {

    private static final DateTimeFormatter DATE_TIME_PATTERN = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public void actionPerformed(AnActionEvent event) {
        var project = event.getProject();
        if (project == null) return;
        var editor = CopyImagePlugin.getEditor(event);
        if (editor == null) {
            CopyImagePlugin.showError(project, "'Save as Image' is available in text editors only");
            return;
        }
        if (!editor.getSelectionModel().hasSelection()) {
            CopyImagePlugin.showError(project, "Please select the text fragment to save");
            return;
        }

        var imageBuilder = new ImageBuilder(editor);
        if (imageBuilder.getSelectedSize() > CopyImageAction.SIZE_LIMIT_TO_WARN) {
            if (Messages.showYesNoDialog(project,
                    "Saving such a big image could be slow and may take a lot of memory. Proceed?",
                    "Code Screenshots", "Yes, Save It!", "Cancel", null) != Messages.YES) {
                return;
            }
        }
        var image = imageBuilder.createImage();
        if (image != null) {
            saveImage(image, project);
        }
    }

    private void saveImage(TransferableImage<?> image, Project project) {
        var options = CopyImageOptionsProvider.getInstance(project).getState();
        var initialDir = options.myDirectoryToSave;
        if (StringUtil.isEmpty(initialDir)) {
            initialDir = Paths.get(SystemProperties.getUserHome(), "Downloads").toString();
        }
        initialDir = initialDir.trim();

        var ext = image.format.ext;
        var suggestedName = "Shot_" + DATE_TIME_PATTERN.format(LocalDateTime.now()) + "." + ext;

        var descriptor = new FileSaverDescriptor("Save Code Screenshot", "Choose where to save the image", ext);
        var dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project);

        var baseDir = LocalFileSystem.getInstance().findFileByPath(initialDir);
        var wrapper = dialog.save(baseDir, suggestedName);
        if (wrapper == null) return;

        var path = wrapper.getFile().toPath();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream os = Files.newOutputStream(path)) {
                image.write(os);
            }

            var pathRepresentation = StringUtil.escapeXmlEntities(StringUtil.shortenPathWithEllipsis(path.toString(), 50));
            var notification = CopyImagePlugin.getNotificationGroup()
                    .createNotification(pathRepresentation, NotificationType.INFORMATION)
                    .setTitle("Code screenshots")
                    .setSubtitle("Image was saved:");
            if (Desktop.isDesktopSupported()) {
                notification.addAction(new NotificationAction("Open") {
                    @Override
                    public void actionPerformed(AnActionEvent anActionEvent, Notification notification) {
                        try {
                            Desktop.getDesktop().open(path.toFile());
                        } catch (IOException e) {
                            CopyImagePlugin.showError(project, "Cannot open image: " + StringUtil.escapeXmlEntities(
                                    path.toString()) + ":<br>" + StringUtil.escapeXmlEntities(
                                    StringUtil.notNullize(e.getLocalizedMessage())));
                        }
                    }
                });
            }
            notification.notify(project);
        } catch (IOException e) {
            CopyImagePlugin.showError(project, "Cannot save image: " + StringUtil.escapeXmlEntities(
                    path.toString()) + ":<br>" + StringUtil.escapeXmlEntities(
                    StringUtil.notNullize(e.getLocalizedMessage())));
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
