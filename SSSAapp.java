package ssapp;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class SSSAapp {

    public static void main(String[] args) {

        try {
            UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName()
            );
        } catch (Exception ignored) {
        }

        UIManager.put("Label.foreground", UIUtils.TEXT);
        UIManager.put("Panel.background", UIUtils.BG_PANEL);
        UIManager.put("OptionPane.background", UIUtils.BG_CARD);
        UIManager.put("OptionPane.messageForeground", UIUtils.TEXT);

        SwingUtilities.invokeLater(() -> {
            new LoginScreen().setVisible(true);
        });
    }
}