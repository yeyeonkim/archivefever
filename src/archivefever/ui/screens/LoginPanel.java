package archivefever.ui.screens;

import archivefever.ui.components.KakaoLoginButton;
import archivefever.ui.theme.AppTheme;
import archivefever.ui.theme.AppColors;
import archivefever.ui.theme.AppFonts;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class LoginPanel extends JPanel {
    private final BufferedImage wallpaper = AppTheme.image("images/login-wallpaper.png");
    private final BufferedImage wood = AppTheme.image("images/login-wood.png");
    private final BufferedImage carpet = AppTheme.image("images/login-carpet.png");

    public LoginPanel(Runnable onLogin) {
        setLayout(null);
        setBackground(AppColors.WALL);
        setPreferredSize(new Dimension(AppTheme.WIDTH, AppTheme.HEIGHT));
        JLabel title = new JLabel("ARCHIVE FEVER");
        title.setFont(AppFonts.heading(36));
        title.setForeground(AppColors.TITLE);
        title.setBounds(74, 370, 320, 46);
        add(title);

        KakaoLoginButton button = new KakaoLoginButton(onLogin);
        button.setBounds(64, 429, 284, 43);
        add(button);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        cover(g, carpet, 0, 783, 412, 135);
        g.setColor(AppColors.CARPET_OVERLAY);
        g.fillRect(0, 783, 412, 135);
        cover(g, wallpaper, -29, 0, 450, 446);
        cover(g, wallpaper, -29, 244, 450, 446);
        cover(g, wood, 181, 517, 347, 304);
        // The left wood panel is a clipped copy, as in the Figma frame.
        Graphics2D left = (Graphics2D) g.create();
        left.clipRect(-114, 517, 302, 304);
        left.drawImage(wood, -114, 517, 347, 304, null);
        left.dispose();
        g.dispose();
    }

    private static void cover(Graphics2D g, BufferedImage image,
                              int x, int y, int width, int height) {
        double scale = Math.max((double) width / image.getWidth(),
                                (double) height / image.getHeight());
        int w = (int) Math.round(image.getWidth() * scale);
        int h = (int) Math.round(image.getHeight() * scale);
        Graphics2D clipped = (Graphics2D) g.create();
        clipped.clipRect(x, y, width, height);
        clipped.drawImage(image, x + (width - w) / 2, y + (height - h) / 2, w, h, null);
        clipped.dispose();
    }
}
