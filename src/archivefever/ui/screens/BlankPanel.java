package archivefever.ui.screens;

import archivefever.ui.theme.AppColors;
import javax.swing.*;

/** Deliberately empty destination; navigation is supplied by AppShell. */
public final class BlankPanel extends JPanel {
    public BlankPanel(String name) {
        setName(name);setBackground(AppColors.WALL);
        getAccessibleContext().setAccessibleName(name);
    }
}
