package dev.radixhomework.gcodeide.model.svg;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Loads an SVG document into stroked geometry runs (user units) plus
 * warnings for unsupported constructs. Supported: path, rect, circle,
 * ellipse, line, polyline, polygon; transforms translate/scale/rotate/
 * matrix. Everything else is skipped with a warning. Pure Java (JDK DOM).
 */
final class SvgDocumentLoader {

    private static final Set<String> SUPPORTED =
            Set.of("svg", "g", "path", "rect", "circle", "ellipse", "line",
                    "polyline", "polygon");

    private final List<SvgRun> runs = new ArrayList<>();
    private final Set<String> warnings = new LinkedHashSet<>();

    private SvgDocumentLoader() {
    }

    static SvgDocument load(String svgText) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            var dom = factory.newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(
                            new java.io.StringReader(svgText)));
            SvgDocumentLoader loader = new SvgDocumentLoader();
            loader.walk(dom.getDocumentElement(), Mat.IDENTITY, null);
            double[] size = loader.readSize(dom.getDocumentElement());
            return new SvgDocument(loader.runs, new ArrayList<>(loader.warnings), size[0], size[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("unparseable SVG: " + e.getMessage(), e);
        }
    }

    // -- tree walk ------------------------------------------------------------------

    private void walk(Element el, Mat ctm, String inheritedStroke) {
        String tag = el.getTagName();
        int colon = tag.indexOf(':');
        if (colon >= 0) {
            tag = tag.substring(colon + 1);
        }
        String stroke = el.hasAttribute("stroke") ? el.getAttribute("stroke")
                : inheritedStroke;
        if (!SUPPORTED.contains(tag)) {
            if (!tag.equals("svg") && !tag.equals("defs") && !tag.equals("title")
                    && !tag.equals("desc") && !tag.equals("metadata")) {
                warnings.add("unsupported element '" + tag + "' skipped");
            }
            return;
        }
        Mat local = ctm;
        if (el.hasAttribute("transform")) {
            try {
                local = ctm.multiply(Mat.parse(el.getAttribute("transform")));
            } catch (RuntimeException e) {
                warnings.add("unsupported transform skipped: " + e.getMessage());
            }
        }
        switch (tag) {
            case "svg", "g" -> walkChildren(el, local, stroke);
            case "path" -> addPath(el, local, stroke);
            case "rect" -> addShape(el, local, stroke, rectCmds(el));
            case "circle" -> addShape(el, local, stroke, circleCmds(el));
            case "ellipse" -> addShape(el, local, stroke, ellipseCmds(el));
            case "line" -> addShape(el, local, stroke, lineCmds(el));
            case "polyline" -> addShape(el, local, stroke, pointsCmds(el, false));
            case "polygon" -> addShape(el, local, stroke, pointsCmds(el, true));
            default -> warnings.add("unsupported element '" + tag + "' skipped");
        }
    }

    private void walkChildren(Element el, Mat ctm, String stroke) {
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                walk(child, ctm, stroke);
            }
        }
    }

    private void addPath(Element el, Mat ctm, String stroke) {
        if (!stroked(el, stroke)) {
            return;
        }
        addCmds(el.getAttribute("d"), ctm);
    }

    private void addShape(Element el, Mat ctm, String stroke, List<SvgPathData.Cmd> cmds) {
        if (!stroked(el, stroke) || cmds == null) {
            return;
        }
        addCmdsList(cmds, ctm);
    }

    private void addCmds(String d, Mat ctm) {
        try {
            addCmdsList(SvgPathData.parse(d), ctm);
        } catch (RuntimeException e) {
            warnings.add("bad path data skipped: " + e.getMessage());
        }
    }

    private void addCmdsList(List<SvgPathData.Cmd> cmds, Mat ctm) {
        for (SvgFlattener.Run run : SvgFlattener.flatten(cmds)) {
            List<double[]> points = new ArrayList<>();
            for (double[] p : run.points()) {
                points.add(ctm.apply(p[0], p[1]));
            }
            if (points.size() >= 2) {
                runs.add(new SvgRun(points, run.closed()));
            }
        }
    }

    private static boolean stroked(Element el, String inheritedStroke) {
        String stroke = el.hasAttribute("stroke") ? el.getAttribute("stroke")
                : inheritedStroke;
        if (stroke != null && !stroke.isBlank() && !stroke.equals("none")) {
            return true;
        }
        String style = el.getAttribute("style");
        for (String decl : style.split(";")) {
            String[] kv = decl.split(":", 2);
            if (kv.length == 2 && kv[0].trim().equals("stroke")
                    && !kv[1].trim().equals("none")) {
                return true;
            }
        }
        return false;
    }

    // -- shape -> path commands -------------------------------------------------------

    private static List<SvgPathData.Cmd> rectCmds(Element el) {
        double x = num(el.getAttribute("x")), y = num(el.getAttribute("y"));
        double w = num(el.getAttribute("width")), h = num(el.getAttribute("height"));
        if (w <= 0 || h <= 0) {
            return null;
        }
        return List.of(
                new SvgPathData.Cmd('M', new double[] {x, y}),
                new SvgPathData.Cmd('L', new double[] {x + w, y}),
                new SvgPathData.Cmd('L', new double[] {x + w, y + h}),
                new SvgPathData.Cmd('L', new double[] {x, y + h}),
                new SvgPathData.Cmd('Z', new double[0]));
    }

    private static List<SvgPathData.Cmd> circleCmds(Element el) {
        double cx = num(el.getAttribute("cx")), cy = num(el.getAttribute("cy"));
        double r = num(el.getAttribute("r"));
        if (r <= 0) {
            return null;
        }
        return List.of(
                new SvgPathData.Cmd('M', new double[] {cx + r, cy}),
                new SvgPathData.Cmd('A', new double[] {r, r, 0, 1, 1, cx - r, cy}),
                new SvgPathData.Cmd('A', new double[] {r, r, 0, 1, 1, cx + r, cy}),
                new SvgPathData.Cmd('Z', new double[0]));
    }

    private static List<SvgPathData.Cmd> ellipseCmds(Element el) {
        double cx = num(el.getAttribute("cx")), cy = num(el.getAttribute("cy"));
        double rx = num(el.getAttribute("rx")), ry = num(el.getAttribute("ry"));
        if (rx <= 0 || ry <= 0) {
            return null;
        }
        return List.of(
                new SvgPathData.Cmd('M', new double[] {cx + rx, cy}),
                new SvgPathData.Cmd('A', new double[] {rx, ry, 0, 1, 1, cx - rx, cy}),
                new SvgPathData.Cmd('A', new double[] {rx, ry, 0, 1, 1, cx + rx, cy}),
                new SvgPathData.Cmd('Z', new double[0]));
    }

    private static List<SvgPathData.Cmd> lineCmds(Element el) {
        return List.of(
                new SvgPathData.Cmd('M', new double[] {num(el.getAttribute("x1")),
                        num(el.getAttribute("y1"))}),
                new SvgPathData.Cmd('L', new double[] {num(el.getAttribute("x2")),
                        num(el.getAttribute("y2"))}));
    }

    private static List<SvgPathData.Cmd> pointsCmds(Element el, boolean close) {
        String[] nums = el.getAttribute("points").trim().split("[\\s,]+");
        List<SvgPathData.Cmd> cmds = new ArrayList<>();
        double x = 0, y = 0;
        for (int i = 0; i + 1 < nums.length; i += 2) {
            x = num(nums[i]);
            y = num(nums[i + 1]);
            cmds.add(new SvgPathData.Cmd(cmds.isEmpty() ? 'M' : 'L',
                    new double[] {x, y}));
        }
        if (close && cmds.size() >= 2) {
            cmds.add(new SvgPathData.Cmd('Z', new double[0]));
        }
        return cmds;
    }

    private static double num(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Double.parseDouble(value.trim().replaceAll("[a-z%]+$", ""));
    }

    // -- document size ------------------------------------------------------------------

    private double[] readSize(Element root) {
        double[] viewBox = null;
        if (root.hasAttribute("viewBox")) {
            String[] parts = root.getAttribute("viewBox").trim().split("[\\s,]+");
            if (parts.length == 4) {
                try {
                    viewBox = new double[] {Double.parseDouble(parts[0]),
                            Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                            Double.parseDouble(parts[3])};
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        double w = root.hasAttribute("width") ? num(root.getAttribute("width")) : 0;
        double h = root.hasAttribute("height") ? num(root.getAttribute("height")) : 0;
        if (w <= 0 && viewBox != null) {
            w = viewBox[2];
        }
        if (h <= 0 && viewBox != null) {
            h = viewBox[3];
        }
        return new double[] {Math.max(0, w), Math.max(0, h)};
    }
}
