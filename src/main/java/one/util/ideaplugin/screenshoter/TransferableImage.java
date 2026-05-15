package one.util.ideaplugin.screenshoter;

import com.intellij.ui.scale.JBUIScale;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public abstract class TransferableImage<T> implements Transferable {

    public enum Format {
        PNG("png", DataFlavor.imageFlavor) {
            TransferableImage<?> paint(
                    JComponent contentComponent,
                    AffineTransform at,
                    int width,
                    int height,
                    Color backgroundColor,
                    int padding
            ) {
                var scale = JBUIScale.sysScale(contentComponent);
                //noinspection UndesirableClassUsage
                var img = new BufferedImage((int) (width * scale + 2 * padding),
                        (int) (height * scale + 2 * padding), BufferedImage.TYPE_INT_RGB);
                var g = (Graphics2D) img.getGraphics();
                paint(g, contentComponent, at, width, height, backgroundColor, padding);
                return new Png(img);
            }
        },
        SVG("svg", svgDataFlavor(), DataFlavor.stringFlavor, DataFlavor.getTextPlainUnicodeFlavor()) {
            TransferableImage<?> paint(JComponent contentComponent, AffineTransform at,
                                       int width, int height, Color backgroundColor, int padding) throws IOException {
                var domImpl = GenericDOMImplementation.getDOMImplementation();
                var doc = domImpl.createDocument("http://www.w3.org/2000/svg", "svg", null);
                var g = new SVGGraphics2D(doc);
                var scale = JBUIScale.sysScale(contentComponent);
                var size = new Dimension((int) (width * scale + 2 * padding), (int) (height * scale + 2 * padding));
                g.setSVGCanvasSize(size);
                paint(g, contentComponent, at, width, height, backgroundColor, padding);
                var writer = new CharArrayWriter();
                g.stream(writer, true);
                return new Svg(writer.toString());
            }
        };

        private static DataFlavor svgDataFlavor() {
            return new DataFlavor(String.class, "image/svg+xml; charset=utf-8");
        }

        static void paint(
                Graphics2D g,
                JComponent contentComponent,
                AffineTransform at,
                int width,
                int height,
                Color backgroundColor,
                int padding
        ) {
            var scale = JBUIScale.sysScale(contentComponent);
            var scaledWidth = (int) (width * scale);
            var scaledHeight = (int) (height * scale);
            var imgWidth = scaledWidth + 2 * padding;
            var imgHeight = scaledHeight + 2 * padding;
            g.setColor(backgroundColor);
            g.fillRect(0, 0, imgWidth, imgHeight);
            g.translate(padding, padding);
            g.clipRect(0, 0, scaledWidth, scaledHeight);
            g.transform(at);
            contentComponent.paint(g);
        }

        final String ext;
        final DataFlavor[] flavors;

        Format(String ext, DataFlavor... flavors) {
            this.ext = ext;
            this.flavors = flavors;
        }

        abstract TransferableImage<?> paint(
                JComponent contentComponent,
                AffineTransform at,
                                            int width,
                int height,
                Color backgroundColor,
                int padding
        ) throws IOException;
    }

    final Format format;
    final T transferee;

    TransferableImage(Format format, T transferee) {
        this.format = format;
        this.transferee = transferee;
    }

    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (isDataFlavorSupported(flavor)) {
            return transferee;
        }
        throw new UnsupportedFlavorException(flavor);
    }

    public DataFlavor[] getTransferDataFlavors() {
        return format.flavors.clone();
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return Arrays.stream(format.flavors).anyMatch(flavor::equals);
    }

    abstract void write(OutputStream to) throws IOException;

    static class Png extends TransferableImage<BufferedImage> {
        Png(BufferedImage image) {
            super(Format.PNG, image);
        }

        @Override
        void write(OutputStream to) throws IOException {
            ImageIO.write(transferee, "png", to);
        }
    }

    static class Svg extends TransferableImage<String> {
        Svg(String image) {
            super(Format.SVG, image);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (flavor.equals(DataFlavor.getTextPlainUnicodeFlavor())) {
                return new StringReader(transferee);
            } else {
                return super.getTransferData(flavor);
            }
        }

        @Override
        void write(OutputStream to) throws IOException {
            try (var w = new OutputStreamWriter(to, StandardCharsets.UTF_8)) {
                w.write(transferee);
            }
        }
    }

}
