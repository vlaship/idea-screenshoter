package one.util.ideaplugin.screenshoter;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

import org.jspecify.annotations.Nullable;

class CopyImagePlugin {

    @Nullable
    static Editor getEditor(AnActionEvent event) {
        var dataContext = event.getDataContext();
        return CommonDataKeys.EDITOR.getData(dataContext);
    }

    static NotificationGroup getNotificationGroup() {
        return NotificationGroupManager.getInstance()
                .getNotificationGroup("Code Screenshots");
    }

    static void showError(@Nullable Project project, String error) {
        getNotificationGroup()
                .createNotification(error, NotificationType.ERROR)
                .setTitle("Code screenshots")
                .notify(project);
    }
}
