package one.util.ideaplugin.screenshoter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.JBColor;
import org.jspecify.annotations.Nullable;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.intellij.codeInsight.hint.EditorFragmentComponent.getBackgroundColor;

class ImageBuilder {

    private static final Pattern EMPTY_SUFFIX = Pattern.compile("\n\\s+$");

    private final Editor editor;
    private final Project project;

    ImageBuilder(Editor editor) {
        this.editor = editor;
        this.project = Objects.requireNonNull(editor.getProject());
    }

    @Nullable
    TransferableImage<?> createImage() {
        var range = getRange(editor);

        var document = editor.getDocument();
        var state = EditorState.from(editor);
        try {
            resetEditor();
            var options = CopyImageOptionsProvider.getInstance(project).getState();
            var scale = options.myScale;
            var contentComponent = editor.getContentComponent();
            var contentGraphics = (Graphics2D) contentComponent.getGraphics();
            var currentTransform = contentGraphics.getTransform();
            var newTransform = new AffineTransform(currentTransform);
            newTransform.scale(scale, scale);
            var format = options.myFormat;
            if (format == null) format = TransferableImage.Format.PNG;
            // To flush glyph cache
            format.paint(contentComponent, newTransform, 1, 1, JBColor.BLACK, 0);
            var text = document.getText(range);
            var r = getSelectionRectangle(range, text, options);

            newTransform.translate(-r.getX(), -r.getY());

            return format.paint(contentComponent, newTransform,
                    (int) (r.getWidth() * scale), (int) (r.getHeight() * scale),
                    getBackgroundColor(editor, false), options.myPadding);
        } catch (IOException e) {
            Logger.getInstance(ImageBuilder.class).error(e);
            return null;
        } finally {
            state.restore(editor);
        }
    }

    private void resetEditor() {
        var document = editor.getDocument();
        var range = getRange(editor);
        editor.getSelectionModel().setSelection(0, 0);
        if (CopyImageOptionsProvider.getInstance(project).getState().myRemoveCaret) {
            editor.getCaretModel().moveToOffset(range.getStartOffset() == 0 ?
                    document.getLineEndOffset(document.getLineCount() - 1) : 0);
            if (editor instanceof EditorEx) {
                ((EditorEx) editor).setCaretEnabled(false);
            }
            editor.getSettings().setCaretRowShown(false);
        }
    }

    long getSelectedSize() {
        var options = CopyImageOptionsProvider.getInstance(project).getState();
        var rectangle = getSelectionRectangle();
        var sizeX = rectangle.getWidth() + options.myPadding * 2;
        var sizeY = rectangle.getHeight() + options.myPadding * 2;
        return (long) (sizeX * sizeY * options.myScale * options.myScale);
    }

    private Rectangle2D getSelectionRectangle() {
        var options = CopyImageOptionsProvider.getInstance(project).getState();
        var range = getRange(editor);
        var document = editor.getDocument();
        var text = document.getText(range);
        return getSelectionRectangle(range, text, options);
    }

    private static TextRange getRange(Editor editor) {
        var selectionModel = editor.getSelectionModel();
        var start = selectionModel.getSelectionStart();
        var end = selectionModel.getSelectionEnd();
        return new TextRange(start, end);
    }

    private Rectangle2D getSelectionRectangle(
            TextRange range,
            String text,
            CopyImageOptionsProvider.State options
    ) {
        var start = range.getStartOffset();
        var end = range.getEndOffset();
        var r = new Rectangle2D.Double();

        for (int i = start; i <= end; i++) {
            if (options.myChopIndentation &&
                    EMPTY_SUFFIX.matcher(text.substring(0, Math.min(i - start + 1, text.length()))).find()) {
                continue;
            }
            var pos = editor.offsetToVisualPosition(i);
            var point = editor.visualPositionToXY(pos);
            includePoint(r, point);
            includePoint(r, new Point2D.Double(point.getX(), point.getY() + editor.getLineHeight()));
        }

        for (var inlay : editor.getInlayModel().getInlineElementsInRange(start, end)) {
            var bounds = inlay.getBounds();
            if (bounds != null) {
                r.add(bounds);
            }
        }

        if (!r.isEmpty()) {
            var lineHeight = editor.getLineHeight();
            var charWidth = editor.visualPositionToXY(new VisualPosition(0, 1)).getX() -
                    editor.visualPositionToXY(new VisualPosition(0, 0)).getX();
            r.setRect(r.getX() - 3 * charWidth, r.getY() - lineHeight,
                    r.getWidth() + 6 * charWidth, r.getHeight() + 2 * lineHeight);
        }
        return r;
    }

    private static void includePoint(Rectangle2D r, Point2D p) {
        if (r.isEmpty()) {
            r.setFrame(p, new Dimension(1, 1));
        } else {
            r.add(p);
        }
    }

    public static class EditorState {

        private final TextRange range;
        private final int offset;
        private final boolean caretRow;

        EditorState(TextRange range, int offset, boolean caretRow) {
            this.range = range;
            this.offset = offset;
            this.caretRow = caretRow;
        }

        static EditorState from(Editor editor) {
            TextRange range = getRange(editor);
            int offset = editor.getCaretModel().getOffset();

            return new EditorState(range, offset, editor.getSettings().isCaretRowShown());
        }

        void restore(Editor editor) {
            editor.getSettings().setCaretRowShown(caretRow);
            var selectionModel = editor.getSelectionModel();
            var caretModel = editor.getCaretModel();
            var provider =
                    CopyImageOptionsProvider.getInstance(Objects.requireNonNull(editor.getProject()));
            if (provider.getState().myRemoveCaret) {
                if (editor instanceof EditorEx) {
                    ((EditorEx) editor).setCaretEnabled(true);
                }
                caretModel.moveToOffset(offset);
            }
            selectionModel.setSelection(range.getStartOffset(), range.getEndOffset());
        }
    }
}
