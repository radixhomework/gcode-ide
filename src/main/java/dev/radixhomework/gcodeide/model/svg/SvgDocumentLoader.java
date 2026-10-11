package dev.radixhomework.gcodeide.model.svg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Loads an SVG document into stroked geometry runs (user units) plus
 * warnings for unsupported constructs. Supported: path, rect, circle,
 * ellipse, line, polyline, polygon; transforms translate/scale/rotate/
 * matrix; presentation via attributes and CSS tag/class selectors from
 * &lt;style&gt; blocks. Shape geometry imports regardless of paint
 * (stroked, filled, or unpainted - the outline is the toolpath). Everything
 * else is skipped with a warning. Pure Java (JDK DOM).
 */
final class SvgDocumentLoader {

    private static final Set<String> SUPPORTED =
            Set.of("svg", "g", "path", "rect", "circle", "ellipse", "line",
                    "polyline", "polygon");

    private static final Set<String> TRANSPARENT_TAGS =
            Set.of("svg", "g", "defs", "title", "desc", "metadata", "style");

    private final List<SvgRun> runs = new ArrayList<>();
    private final Set<String> warnings = new LinkedHashSet<>();
    /** CSS selector -> declarations (later rules win). Keyed ".class", "tag", "#id". */
    private final Map<String, Map<String, String>> cssRules = new LinkedHashMap<>();

    private SvgDocumentLoader() {
    }

    static SvgDocument load(String svgText) {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        SvgDocumentLoader loader = new SvgDocumentLoader();
        Element root;
        try {
            var dom = factory.newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(
                            new java.io.StringReader(svgText)));
            root = dom.getDocumentElement();
        } catch (Exception e) {
            throw new IllegalArgumentException("unparseable SVG: " + e.getMessage(), e);
        }
        loader.collectStyleRules(root);
        loader.walk(root, Mat.IDENTITY);
        double[] size = loader.readSize(root);
        if (loader.runs.isEmpty()) {
            loader.warnings.add("no shape geometry found - the drawing has no "
                    + "importable elements (only text/images/etc, or it is empty)");
        }
        return new SvgDocument(loader.runs, new ArrayList<>(loader.warnings),
                size[0], size[1]);
    }

    // -- CSS: <style> blocks -> selector rules ----------------------------------

    private void collectStyleRules(Element el) {
        if (localName(el).equals("style")) {
            parseRules(el.getTextContent());
        }
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                collectStyleRules(child);
            }
        }
    }

    private void parseRules(String css) {
        if (css == null || css.isBlank()) {
            return;
        }
        for (String block : css.split("}")) {
            int brace = block.indexOf('{');
            if (brace < 0) {
                continue;
            }
            String selectors = block.substring(0, brace).trim();
            if (selectors.startsWith("@")) {
                continue; // @media and other at-rules: not supported
            }
            Map<String, String> decls = parseDecls(block.substring(brace + 1));
            for (String sel : selectors.split(",")) {
                String key = sel.trim().replaceAll("(:.*)$", "").trim();
                if (!key.isEmpty()) {
                    cssRules.computeIfAbsent(key, k -> new LinkedHashMap<>())
                            .putAll(decls);
                }
            }
        }
    }

    private static Map<String, String> parseDecls(String decls) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String decl : decls.split(";")) {
            String[] kv = decl.split(":", 2);
            if (kv.length == 2) {
                out.put(kv[0].trim(), kv[1].trim());
            }
        }
        return out;
    }

    /** Effective declarations for an element: tag rule, then class rules,
     *  then id rule, then inline style (later wins). */
    private Map<String, String> declsFor(Element el) {
        Map<String, String> m = new LinkedHashMap<>();
        String tag = localName(el);
        Map<String, String> tagRule = cssRules.get(tag);
        if (tagRule != null) {
            m.putAll(tagRule);
        }
        for (String c : el.getAttribute("class").split("\\s+")) {
            Map<String, String> rule = cssRules.get("." + c);
            if (rule != null) {
                m.putAll(rule);
            }
        }
        if (el.hasAttribute("id")) {
            Map<String, String> rule = cssRules.get("#" + el.getAttribute("id"));
            if (rule != null) {
                m.putAll(rule);
            }
        }
        m.putAll(parseDecls(el.getAttribute("style")));
        return m;
    }

    private static String localName(Element el) {
        String tag = el.getTagName();
        int colon = tag.indexOf(':');
        return colon >= 0 ? tag.substring(colon + 1) : tag;
    }

    // -- tree walk ------------------------------------------------------------------

    private void walk(Element el, Mat ctm) {
        String tag = localName(el);
        if (!SUPPORTED.contains(tag)) {
            if (!TRANSPARENT_TAGS.contains(tag)) {
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
            case "svg", "g" -> walkChildren(el, local);
            case "path" -> addPath(el, local);
            case "rect" -> addShape(el, local, rectCmds(el));
            case "circle" -> addShape(el, local, circleCmds(el));
            case "ellipse" -> addShape(el, local, ellipseCmds(el));
            case "line" -> addShape(el, local, lineCmds(el));
            case "polyline" -> addShape(el, local, pointsCmds(el, false));
            case "polygon" -> addShape(el, local, pointsCmds(el, true));
            default -> warnings.add("unsupported element '" + tag + "' skipped");
        }
    }

    private void walkChildren(Element el, Mat ctm) {
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                walk(child, ctm);
            }
        }
    }

    // -- geometry collection ---------------------------------------------------------

    private void addPath(Element el, Mat ctm) {
        // geometry imports regardless of paint: the outline of a filled or
        // unpainted shape is exactly what a CNC toolpath needs
        addCmds(el.getAttribute("d"), ctm);
    }

    private void addShape(Element el, Mat ctm, List<SvgPathData.Cmd> cmds) {
        if (cmds == null) {
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
