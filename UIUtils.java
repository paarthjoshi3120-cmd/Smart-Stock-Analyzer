package ssapp;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class UIUtils {

    // ---------- COLORS ----------

    static final Color BG_DEEP = new Color(13, 16, 24);
    static final Color BG_PANEL = new Color(20, 24, 34);
    static final Color BG_CARD = new Color(28, 33, 46);
    static final Color BG_INPUT = new Color(38, 44, 60);
    static final Color BORDER = new Color(52, 60, 82);

    static final Color TEXT = new Color(240, 244, 252);
    static final Color TEXT_MUTED = new Color(150, 160, 185);

    static final Color ACCENT = new Color(99, 155, 255);
    static final Color SUCCESS = new Color(76, 217, 145);
    static final Color DANGER = new Color(255, 96, 110);
    static final Color WARNING = new Color(255, 195, 90);
    static final Color GOLD = new Color(255, 200, 90);

    // ---------- FONTS ----------

    static final Font H1 =
            new Font("SansSerif", Font.BOLD, 22);

    static final Font H2 =
            new Font("SansSerif", Font.BOLD, 16);

    static final Font H3 =
            new Font("SansSerif", Font.BOLD, 13);

    static final Font BODY =
            new Font("SansSerif", Font.PLAIN, 13);

    static final Font SMALL =
            new Font("SansSerif", Font.PLAIN, 11);

    // ---------- BUTTON ----------

    static JButton btn(String text, Color bg) {

        JButton b = new JButton(text);

        b.setFont(H3);
        b.setForeground(Color.WHITE);
        b.setBackground(bg);

        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);

        b.setCursor(new Cursor(Cursor.HAND_CURSOR));

        b.setBorder(
                new EmptyBorder(9, 18, 9, 18)
        );

        b.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseEntered(MouseEvent e) {
                b.setBackground(bg.brighter());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                b.setBackground(bg);
            }
        });

        return b;
    }

    // ---------- TEXT FIELD ----------

    static JTextField input() {

        JTextField f = new JTextField();

        f.setBackground(BG_INPUT);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setFont(BODY);

        f.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        new EmptyBorder(9, 12, 9, 12)
                )
        );

        f.setPreferredSize(
                new Dimension(220, 36)
        );

        return f;
    }

    // ---------- PASSWORD FIELD ----------

    static JPasswordField passInput() {

        JPasswordField f = new JPasswordField();

        f.setBackground(BG_INPUT);
        f.setForeground(TEXT);
        f.setCaretColor(TEXT);
        f.setFont(BODY);

        f.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        new EmptyBorder(9, 12, 9, 12)
                )
        );

        f.setPreferredSize(
                new Dimension(220, 36)
        );

        return f;
    }

    // ---------- COMBO BOX ----------

    static void styleCombo(JComboBox<?> c) {

        c.setBackground(BG_INPUT);
        c.setForeground(TEXT);
        c.setFont(BODY);
    }

    // ---------- HEADINGS ----------

    static JLabel h1(String s) {

        JLabel l = new JLabel(s);

        l.setFont(H1);
        l.setForeground(TEXT);

        return l;
    }

    static JLabel h2(String s) {

        JLabel l = new JLabel(s);

        l.setFont(H2);
        l.setForeground(TEXT);

        return l;
    }

    static JLabel muted(String s) {

        JLabel l = new JLabel(s);

        l.setFont(SMALL);
        l.setForeground(TEXT_MUTED);

        return l;
    }

    // ---------- TABLE ----------

    static JTable darkTable(DefaultTableModel m) {

        JTable t = new JTable(m);

        t.setBackground(BG_CARD);
        t.setForeground(TEXT);

        t.setRowHeight(30);
        t.setFont(BODY);

        t.setGridColor(BORDER);
        t.setShowVerticalLines(false);

        t.setSelectionBackground(
                new Color(60, 90, 160)
        );

        t.setSelectionForeground(Color.WHITE);

        t.setFillsViewportHeight(true);

        t.getTableHeader().setBackground(BG_PANEL);
        t.getTableHeader().setForeground(TEXT_MUTED);
        t.getTableHeader().setFont(H3);

        t.getTableHeader().setReorderingAllowed(false);

        return t;
    }

    // ---------- SCROLL PANE ----------

    static JScrollPane scroll(JComponent c) {

        JScrollPane sp = new JScrollPane(c);

        sp.setBorder(
                BorderFactory.createLineBorder(BORDER)
        );

        sp.getViewport().setBackground(BG_CARD);

        return sp;
    }

    // ---------- RIGHT ALIGNMENT ----------

    static DefaultTableCellRenderer rightR() {

        DefaultTableCellRenderer r =
                new DefaultTableCellRenderer();

        r.setHorizontalAlignment(
                SwingConstants.RIGHT
        );

        return r;
    }

    // ---------- COUNTRY FLAG ----------

    static String countryFlag(String c) {

        switch (c) {

            case "IN":
                return "🇮🇳 India";

            case "US":
                return "🇺🇸 USA";

            case "UK":
                return "🇬🇧 UK";

            case "JP":
                return "🇯🇵 Japan";

            case "DE":
                return "🇩🇪 Germany";

            case "CN":
                return "🇨🇳 China";

            default:
                return c;
        }
    }
}