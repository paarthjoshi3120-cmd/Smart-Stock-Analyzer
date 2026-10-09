package ssapp;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

import static ssapp.UIUtils.*;

public class LoginScreen extends JFrame {

  JTextField userField = input();
	//JTextField userField = new JtextField();

    JPasswordField passField = passInput();
    //JPasswordField passField = new JPasswordField();

    JLabel errorLabel = new JLabel(" ");

    String selectedRole = "USER";

    LoginScreen() {

        super("SSA Pro - Login");

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        setSize(900, 540);
        setLocationRelativeTo(null);

        setResizable(false);

        JPanel root = new JPanel(
                new GridLayout(1, 2)
        );

        root.setBackground(BG_DEEP);

        root.add(brandPanel());
        root.add(formPanel());

        setContentPane(root);
    }

    JPanel brandPanel() {

        JPanel p = new JPanel();

        p.setBackground(
                new Color(30, 45, 90)
        );

        p.setLayout(
                new BoxLayout(p, BoxLayout.Y_AXIS)
        );

        p.setBorder(
                new EmptyBorder(60, 50, 60, 50)
        );

        JLabel logo = new JLabel("\u25C8");

        logo.setFont(
                new Font("SansSerif", Font.BOLD, 64)
        );

        logo.setForeground(ACCENT);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("SSA Pro");

        title.setFont(
                new Font("SansSerif", Font.BOLD, 36)
        );

        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tag = new JLabel(
                "Smart Stock Analyzer"
        );

        tag.setFont(
                new Font("SansSerif", Font.BOLD, 15)
        );

        tag.setForeground(
                new Color(180, 195, 225)
        );

        tag.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel desc = new JLabel(
                "<html><br>Professional-grade<br>"
                + "trading terminal.<br>"
                + "Live markets, portfolio<br>"
                + "analytics and more.</html>"
        );

        desc.setFont(BODY);

        desc.setForeground(
                new Color(170, 185, 215)
        );

        desc.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.add(logo);
        p.add(Box.createVerticalStrut(20));

        p.add(title);
        p.add(Box.createVerticalStrut(6));

        p.add(tag);
        p.add(Box.createVerticalStrut(24));

        p.add(desc);
        p.add(Box.createVerticalGlue());

        JLabel version = new JLabel("v4.0");

        version.setFont(SMALL);
        version.setForeground(
                new Color(150, 165, 195)
        );

        version.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.add(version);

        return p;
    }

    JPanel formPanel() {

        JPanel wrap = new JPanel(
                new GridBagLayout()
        );

        wrap.setBackground(BG_PANEL);

        JPanel c = new JPanel();

        c.setBackground(BG_CARD);

        c.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        new EmptyBorder(24, 28, 24, 28)
                )
        );

        c.setLayout(
                new BoxLayout(c, BoxLayout.Y_AXIS)
        );

        c.setPreferredSize(
                new Dimension(340, 440)
        );

        JLabel heading = h1("Welcome back");

        heading.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        JLabel sub = muted(
                "Sign in to continue"
        );

        sub.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        c.add(heading);
        c.add(Box.createVerticalStrut(4));
        c.add(sub);

        c.add(Box.createVerticalStrut(20));

        JLabel roleLabel = muted("SIGN IN AS");

        roleLabel.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        c.add(roleLabel);
        c.add(Box.createVerticalStrut(6));

        JPanel roleRow = new JPanel(
                new GridLayout(1, 2, 8, 0)
        );

        roleRow.setOpaque(false);

        roleRow.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 38)
        );

        roleRow.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        JToggleButton userBtn =
                new JToggleButton("USER", true);

        JToggleButton adminBtn =
                new JToggleButton("ADMIN");

        userBtn.setFont(H3);
        adminBtn.setFont(H3);

        userBtn.setForeground(Color.WHITE);
        adminBtn.setForeground(TEXT);

        userBtn.setBackground(ACCENT);
        adminBtn.setBackground(BG_INPUT);

        userBtn.setOpaque(true);
        adminBtn.setOpaque(true);

        userBtn.setBorderPainted(false);
        adminBtn.setBorderPainted(false);

        userBtn.setFocusPainted(false);
        adminBtn.setFocusPainted(false);

        ButtonGroup group = new ButtonGroup();

        group.add(userBtn);
        group.add(adminBtn);

        userBtn.addActionListener(e -> {

            selectedRole = "USER";

            userBtn.setBackground(ACCENT);
            userBtn.setForeground(Color.WHITE);

            adminBtn.setBackground(BG_INPUT);
            adminBtn.setForeground(TEXT);
        });

        adminBtn.addActionListener(e -> {

            selectedRole = "ADMIN";

            adminBtn.setBackground(ACCENT);
            adminBtn.setForeground(Color.WHITE);

            userBtn.setBackground(BG_INPUT);
            userBtn.setForeground(TEXT);
        });

        roleRow.add(userBtn);
        roleRow.add(adminBtn);

        c.add(roleRow);
        c.add(Box.createVerticalStrut(18));

        JLabel usernameLabel = muted("USERNAME");

        usernameLabel.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        c.add(usernameLabel);
        c.add(Box.createVerticalStrut(6));

        userField.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 36)
        );

        userField.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        c.add(userField);
        c.add(Box.createVerticalStrut(14));

        JLabel passwordLabel = muted("PASSWORD");

        passwordLabel.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        c.add(passwordLabel);
        c.add(Box.createVerticalStrut(6));

        passField.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 36)
        );

        passField.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        c.add(passField);
        c.add(Box.createVerticalStrut(10));

        errorLabel.setFont(SMALL);
        errorLabel.setForeground(DANGER);

        errorLabel.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        c.add(errorLabel);
        c.add(Box.createVerticalStrut(8));

        JButton loginBtn = btn(
                "Sign In",
                ACCENT
        );

        loginBtn.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 42)
        );

        loginBtn.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        loginBtn.addActionListener(
                e -> attemptLogin()
        );

        c.add(loginBtn);
        c.add(Box.createVerticalStrut(8));

        JButton registerBtn = btn(
                "Create New Account",
                BG_INPUT
        );

        registerBtn.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 38)
        );

        registerBtn.setAlignmentX(
                Component.LEFT_ALIGNMENT
        );

        registerBtn.addActionListener(
                e -> showRegister()
        );

        c.add(registerBtn);

        getRootPane().setDefaultButton(loginBtn);

        wrap.add(c);

        return wrap;
    }

    void attemptLogin() {

        String username =
                userField.getText().trim();

        String password =
                new String(passField.getPassword());

        if (username.isEmpty()
                || password.isEmpty()) {

            errorLabel.setForeground(DANGER);

            errorLabel.setText(
                    "Please fill in both fields."
            );

            return;
        }

        for (AppData.User user : AppData.USERS) {

            if (user.username.equals(username)
                    && user.password.equals(password)) {

                if (!user.role.equals(selectedRole)) {

                    errorLabel.setForeground(DANGER);

                    errorLabel.setText(
                            "Not a " + selectedRole
                                    + " account."
                    );

                    return;
                }

                new MainScreen(user).setVisible(true);

                dispose();

                return;
            }
        }

        errorLabel.setForeground(DANGER);

        errorLabel.setText(
                "Invalid username or password."
        );
    }

    void showRegister() {

        JTextField usernameField = input();

        JPasswordField passwordField = passInput();

        JTextField nameField = input();

        JPanel form = new JPanel(
                new GridLayout(0, 1, 6, 6)
        );

        form.setBackground(BG_CARD);

        form.add(muted("Username"));
        form.add(usernameField);

        form.add(muted("Password"));
        form.add(passwordField);

        form.add(muted("Display Name"));
        form.add(nameField);

        int result = JOptionPane.showConfirmDialog(
                this,
                form,
                "Create Account",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String username =
                usernameField.getText().trim();

        String password =
                new String(passwordField.getPassword());

        String displayName =
                nameField.getText().trim();

        if (username.isEmpty()
                || password.isEmpty()
                || displayName.isEmpty()) {

            errorLabel.setForeground(DANGER);

            errorLabel.setText(
                    "All fields required."
            );

            return;
        }

        for (AppData.User user : AppData.USERS) {

            if (user.username.equalsIgnoreCase(username)) {

                errorLabel.setForeground(DANGER);

                errorLabel.setText(
                        "Username already taken."
                );

                return;
            }
        }

        AppData.USERS.add(
                new AppData.User(
                        username,
                        password,
                        displayName,
                        "USER",
                        100000
                )
        );

        errorLabel.setForeground(SUCCESS);

        errorLabel.setText(
                "Account created! You can now log in."
        );
    }
}