package dev.radixhomework.gcodeide.view;

import javafx.scene.Node;

/** Applies the theme-family flag (`dark` style class) to lookup roots.
 *  CSS branches on it for the theme-adaptive palettes (design D1 of
 *  change theme-adaptive-colors). */
public final class UiTheme {

    private UiTheme() {
    }

    public static void applyDark(Node node, boolean dark) {
        var classes = node.getStyleClass();
        if (dark) {
            if (!classes.contains("dark")) {
                classes.add("dark");
            }
        } else {
            classes.remove("dark");
        }
    }

    public static boolean isDark(Node node) {
        return node.getStyleClass().contains("dark");
    }
}
