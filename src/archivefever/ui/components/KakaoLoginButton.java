package archivefever.ui.components;

import archivefever.ui.theme.AppColors;
import javax.swing.*;
import java.awt.*;

/** Native text and original SVG geometry remain sharp at any preview scale. */
public final class KakaoLoginButton extends JButton {
    private static final Shape LOGO = SceneImages.svgPath("icons/kakao-logo.svg");
    private static final Font LABEL_FONT =
            new Font(Font.SANS_SERIF, Font.BOLD, 14).deriveFont(14.16f);

    public KakaoLoginButton(Runnable onLogin) {
        setText("카카오 로그인");
        setBorder(BorderFactory.createEmptyBorder());
        setOpaque(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getAccessibleContext().setAccessibleName("카카오 로그인");
        addActionListener(event -> onLogin.run());
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        // Bypass platform button painting so Aqua cannot add its own button chrome.
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(AppColors.KAKAO_BACKGROUND);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

        Graphics2D icon = (Graphics2D) g.create();
        icon.translate(13.216, (getHeight() - 16.992) / 2);
        icon.setColor(Color.BLACK);
        icon.fill(LOGO);
        icon.dispose();

        g.setFont(LABEL_FONT);
        g.setColor(AppColors.KAKAO_TEXT);
        FontMetrics metrics = g.getFontMetrics();
        float x = (float)((getWidth() - metrics.getStringBounds(getText(), g).getWidth()) / 2);
        float y = (getHeight() - metrics.getHeight()) / 2f + metrics.getAscent();
        g.drawString(getText(), x, y);

        if (getModel().isArmed() && getModel().isPressed()) {
            g.setColor(AppColors.BUTTON_PRESSED_OVERLAY);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        }
        if (hasFocus()) {
            g.setColor(AppColors.INK);
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 10, 10);
        }
        g.dispose();
    }
}
