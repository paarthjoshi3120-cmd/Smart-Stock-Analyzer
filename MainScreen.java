package ssapp;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

import static ssapp.UIUtils.*;

public class MainScreen extends JFrame {

    AppData.User me;
    List<AppData.Holding> mine;

    Set<String> watchlist = new LinkedHashSet<>();

    JLabel status;
    JLabel cashL, invL, valL, plL, holdL;

    DefaultTableModel marketM, watchM, portM, histM, userM;

    JTable marketT, watchT, portT, userT;

    JTabbedPane mainTabs;

    CardLayout watchCardLayout;
    JPanel watchCardStack;

    MainScreen(AppData.User user) {

        super("SSA Pro - " + user.displayName);

        me = user;
        mine = AppData.portfolioOf(user.username);

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        setSize(1280, 800);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(1050, 680));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DEEP);

        root.add(topBar(), BorderLayout.NORTH);
        root.add(tabs(), BorderLayout.CENTER);
        root.add(statusBar(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ---------- TOP BAR ----------

    JPanel topBar() {

        JPanel bar = new JPanel(new BorderLayout());

        bar.setBackground(BG_PANEL);

        bar.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(
                                0, 0, 1, 0, BORDER
                        ),
                        new EmptyBorder(12, 20, 12, 20)
                )
        );

        JPanel left = new JPanel();
        left.setOpaque(false);

        left.setLayout(
                new BoxLayout(left, BoxLayout.X_AXIS)
        );

        JLabel logo = new JLabel("\u25C8");

        logo.setFont(
                new Font("SansSerif", Font.BOLD, 24)
        );

        logo.setForeground(ACCENT);

        left.add(logo);
        left.add(Box.createHorizontalStrut(10));

        JPanel titlePanel = new JPanel();

        titlePanel.setOpaque(false);

        titlePanel.setLayout(
                new BoxLayout(titlePanel, BoxLayout.Y_AXIS)
        );

        JLabel title = new JLabel("SSA Pro");
        title.setFont(H2);
        title.setForeground(TEXT);

        JLabel subtitle = muted(
                "Smart Stock Analyzer  ·  Global Markets"
        );

        titlePanel.add(title);
        titlePanel.add(subtitle);

        left.add(titlePanel);

        JPanel right = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 10, 0)
        );

        right.setOpaque(false);

        JLabel role = new JLabel(" " + me.role + " ");

        role.setFont(
                new Font("SansSerif", Font.BOLD, 10)
        );

        role.setOpaque(true);

        role.setBorder(
                new EmptyBorder(5, 10, 5, 10)
        );

        if ("ADMIN".equals(me.role)) {

            role.setBackground(
                    new Color(85, 65, 25)
            );

            role.setForeground(WARNING);

        } else {

            role.setBackground(
                    new Color(30, 55, 100)
            );

            role.setForeground(ACCENT);
        }

        JLabel name = new JLabel(me.displayName);

        name.setFont(H3);
        name.setForeground(TEXT);

        JButton logout = btn(
                "Logout",
                new Color(70, 40, 48)
        );

        logout.addActionListener(e -> {

            dispose();

            new LoginScreen().setVisible(true);
        });

        right.add(role);
        right.add(name);
        right.add(logout);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    // ---------- TABS ----------

    JTabbedPane tabs() {

        mainTabs = new JTabbedPane();

        mainTabs.setFont(H3);
        mainTabs.setBackground(BG_PANEL);
        mainTabs.setForeground(Color.WHITE);

        mainTabs.setBorder(
                new EmptyBorder(10, 14, 10, 14)
        );

        mainTabs.setUI(new BasicTabbedPaneUI() {

            @Override
            protected void paintTabBackground(
                    Graphics g,
                    int tabPlacement,
                    int tabIndex,
                    int x,
                    int y,
                    int w,
                    int h,
                    boolean selected
            ) {

                Graphics2D g2 =
                        (Graphics2D) g.create();

                g2.setColor(
                        selected ? BG_CARD : BG_PANEL
                );

                g2.fillRoundRect(
                        x + 2,
                        y + 2,
                        w - 4,
                        h - 4,
                        8,
                        8
                );

                if (selected) {

                    g2.setColor(ACCENT);

                    g2.fillRoundRect(
                            x + 6,
                            y + h - 5,
                            w - 12,
                            3,
                            3,
                            3
                    );
                }

                g2.dispose();
            }

            @Override
            protected void paintTabBorder(
                    Graphics g,
                    int tabPlacement,
                    int tabIndex,
                    int x,
                    int y,
                    int w,
                    int h,
                    boolean selected
            ) {
            }

            @Override
            protected void paintContentBorder(
                    Graphics g,
                    int tabPlacement,
                    int selectedIndex
            ) {
            }
        });

        mainTabs.addTab("Dashboard", dashboardTab());
        mainTabs.addTab("Market", marketTab());
        mainTabs.addTab("Trade", tradeTab());
        mainTabs.addTab("Portfolio", portfolioTab());
        mainTabs.addTab("Watchlist", watchlistTab());
        mainTabs.addTab("History", historyTab());
        mainTabs.addTab("Guide", guideTab());

        if ("ADMIN".equals(me.role)) {
            mainTabs.addTab("Admin", adminTab());
        }

        mainTabs.addChangeListener(e -> {

            int index = mainTabs.getSelectedIndex();

            if (index == 0) {
                updateMetrics();
            }

            if (index == 3) {
                refreshPortfolio();
            }

            if (index == 4) {
                refreshWatch();
            }

            if (index == 5) {
                refreshHistory();
            }
        });

        return mainTabs;
    }

    // ---------- DASHBOARD ----------

    JPanel dashboardTab() {

        JPanel panel = new JPanel(
                new BorderLayout(12, 12)
        );

        panel.setBackground(BG_DEEP);

        panel.setBorder(
                new EmptyBorder(14, 6, 6, 6)
        );

        JPanel heading = new JPanel(
                new BorderLayout()
        );

        heading.setOpaque(false);

        heading.add(
                h1("Portfolio Dashboard"),
                BorderLayout.WEST
        );

        heading.add(
                muted("Your account overview"),
                BorderLayout.EAST
        );

        panel.add(heading, BorderLayout.NORTH);

        JPanel metrics = new JPanel(
                new GridLayout(2, 3, 12, 12)
        );

        metrics.setBackground(BG_DEEP);

        metrics.setBorder(
                new EmptyBorder(16, 0, 16, 0)
        );

        cashL = new JLabel();
        invL = new JLabel();
        valL = new JLabel();
        plL = new JLabel();
        holdL = new JLabel();

        metrics.add(
                metricCard("AVAILABLE CASH", cashL, ACCENT)
        );

        metrics.add(
                metricCard("INVESTED", invL, WARNING)
        );

        metrics.add(
                metricCard("PORTFOLIO VALUE", valL, SUCCESS)
        );

        metrics.add(
                metricCard("TOTAL P&L", plL, SUCCESS)
        );

        metrics.add(
                metricCard("HOLDINGS", holdL, TEXT)
        );

        metrics.add(
                metricCard(
                        "MEMBER SINCE",
                        new JLabel("Today"),
                        TEXT_MUTED
                )
        );

        JPanel middle = new JPanel(
                new BorderLayout()
        );

        middle.setBackground(BG_DEEP);

        middle.add(metrics, BorderLayout.NORTH);

        JPanel lists = new JPanel(
                new GridLayout(1, 2, 12, 0)
        );

        lists.setBackground(BG_DEEP);

        lists.add(gainersPanel());
        lists.add(losersPanel());

        middle.add(lists, BorderLayout.CENTER);

        panel.add(middle, BorderLayout.CENTER);

        updateMetrics();

        return panel;
    }

    JPanel metricCard(
            String label,
            JLabel value,
            Color accent
    ) {

        JPanel panel = new JPanel();

        panel.setLayout(
                new BoxLayout(panel, BoxLayout.Y_AXIS)
        );

        panel.setBackground(BG_CARD);

        panel.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        new EmptyBorder(14, 16, 14, 16)
                )
        );

        JLabel heading = muted(label);

        value.setFont(
                new Font("SansSerif", Font.BOLD, 20)
        );

        value.setForeground(accent);

        panel.add(heading);
        panel.add(Box.createVerticalStrut(6));
        panel.add(value);

        return panel;
    }

    JPanel gainersPanel() {

        JPanel panel = new JPanel(
                new BorderLayout(8, 8)
        );

        panel.setBackground(BG_DEEP);

        panel.add(
                h2("Top Gainers"),
                BorderLayout.NORTH
        );

        DefaultTableModel model =
                new DefaultTableModel(
                        new Object[]{
                                "Symbol", "Price", "Change"
                        },
                        0
                ) {
                    @Override
                    public boolean isCellEditable(
                            int row,
                            int column
                    ) {
                        return false;
                    }
                };

        JTable table = darkTable(model);

        table.getColumnModel()
                .getColumn(1)
                .setCellRenderer(rightR());

        table.getColumnModel()
                .getColumn(2)
                .setCellRenderer(rightR());

        AppData.COMPANIES.stream()
                .filter(c -> c.changePct > 0)
                .sorted(
                        (a, b) -> Double.compare(
                                b.changePct,
                                a.changePct
                        )
                )
                .limit(8)
                .forEach(c -> model.addRow(
                        new Object[]{
                                c.symbol,
                                AppData.inr(c.price),
                                AppData.pct(c.changePct)
                        }
                ));

        panel.add(scroll(table), BorderLayout.CENTER);

        return panel;
    }

    JPanel losersPanel() {

        JPanel panel = new JPanel(
                new BorderLayout(8, 8)
        );

        panel.setBackground(BG_DEEP);

        panel.add(
                h2("Top Losers"),
                BorderLayout.NORTH
        );

        DefaultTableModel model =
                new DefaultTableModel(
                        new Object[]{
                                "Symbol", "Price", "Change"
                        },
                        0
                ) {
                    @Override
                    public boolean isCellEditable(
                            int row,
                            int column
                    ) {
                        return false;
                    }
                };

        JTable table = darkTable(model);

        table.getColumnModel()
                .getColumn(1)
                .setCellRenderer(rightR());

        table.getColumnModel()
                .getColumn(2)
                .setCellRenderer(rightR());

        AppData.COMPANIES.stream()
                .filter(c -> c.changePct < 0)
                .sorted(
                        (a, b) -> Double.compare(
                                a.changePct,
                                b.changePct
                        )
                )
                .limit(8)
                .forEach(c -> model.addRow(
                        new Object[]{
                                c.symbol,
                                AppData.inr(c.price),
                                AppData.pct(c.changePct)
                        }
                ));

        panel.add(scroll(table), BorderLayout.CENTER);

        return panel;
    }

    void updateMetrics() {

        if (cashL == null) {
            return;
        }

        double invested =
                AppData.invested(mine);

        double value =
                AppData.totalValue(mine);

        double profitLoss =
                value - invested;

        cashL.setText(
                AppData.inr(me.cash)
        );

        invL.setText(
                AppData.inr(invested)
        );

        valL.setText(
                AppData.inr(value)
        );

        plL.setText(
                (profitLoss >= 0 ? "+" : "")
                        + AppData.inr(profitLoss)
        );

        holdL.setText(
                String.valueOf(mine.size())
        );

        plL.setForeground(
                profitLoss >= 0 ? SUCCESS : DANGER
        );
    }

    // ---------- MARKET ----------

    JPanel marketTab() {

        JPanel panel = new JPanel(
                new BorderLayout(10, 10)
        );

        panel.setBackground(BG_DEEP);

        panel.setBorder(
                new EmptyBorder(14, 6, 6, 6)
        );

        JPanel top = new JPanel(
                new BorderLayout()
        );

        top.setOpaque(false);

        top.add(
                h1("Market Watch  ·  Global"),
                BorderLayout.WEST
        );

        JPanel actions = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 8, 0)
        );

        actions.setOpaque(false);

        JComboBox<String> filter =
                new JComboBox<>(
                        new String[]{
                                "All Countries",
                                "India",
                                "USA",
                                "UK",
                                "Japan",
                                "Germany",
                                "China"
                        }
                );

        styleCombo(filter);

        filter.addActionListener(
                e -> refreshMarket(
                        (String) filter.getSelectedItem()
                )
        );

        JButton add = btn(
                "Add to Watchlist",
                ACCENT
        );

        add.addActionListener(
                e -> addSelectedToWatchlist()
        );

        actions.add(muted("Filter:"));
        actions.add(filter);
        actions.add(add);

        top.add(actions, BorderLayout.EAST);

        panel.add(top, BorderLayout.NORTH);

        marketM = new DefaultTableModel(
                new Object[]{
                        "Symbol",
                        "Company",
                        "Sector",
                        "Country",
                        "Price",
                        "Change %"
                },
                0
        ) {
            @Override
            public boolean isCellEditable(
                    int row,
                    int column
            ) {
                return false;
            }
        };

        marketT = darkTable(marketM);

        marketT.getColumnModel()
                .getColumn(4)
                .setCellRenderer(rightR());

        marketT.getColumnModel()
                .getColumn(5)
                .setCellRenderer(rightR());

        refreshMarket("All Countries");

        panel.add(
                scroll(marketT),
                BorderLayout.CENTER
        );

        return panel;
    }

    void refreshMarket(String filter) {

        if (marketM == null) {
            return;
        }

        marketM.setRowCount(0);

        for (AppData.Company company :
                AppData.COMPANIES) {

            if (
                    filter.equals("All Countries")
                    || AppData.countryFlag(
                            company.country
                    ).contains(filter)
            ) {

                marketM.addRow(
                        new Object[]{
                                company.symbol,
                                company.name,
                                company.sector,
                                AppData.countryFlag(
                                        company.country
                                ),
                                AppData.inr(
                                        company.price
                                ),
                                AppData.pct(
                                        company.changePct
                                )
                        }
                );
            }
        }
    }

    void addSelectedToWatchlist() {

        int row =
                marketT.getSelectedRow();

        if (row == -1) {

            setStatus(
                    "Select a company first.",
                    true
            );

            return;
        }

        String symbol =
                (String) marketM.getValueAt(row, 0);

        if (watchlist.add(symbol)) {

            refreshWatch();

            setStatus(
                    symbol + " added to watchlist.",
                    false
            );

        } else {

            setStatus(
                    symbol + " is already in your watchlist.",
                    true
            );
        }
    }

    // ---------- TRADE ----------

    JPanel tradeTab() {

        JPanel wrapper = new JPanel(
                new GridBagLayout()
        );

        wrapper.setBackground(BG_DEEP);

        JPanel card = new JPanel(
                new GridBagLayout()
        );

        card.setBackground(BG_CARD);

        card.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        new EmptyBorder(20, 28, 20, 28)
                )
        );

        GridBagConstraints g =
                new GridBagConstraints();

        g.insets = new Insets(7, 7, 7, 7);
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 2;

        card.add(
                h2("Place Market Order"),
                g
        );

        g.gridwidth = 1;

        JComboBox<String> companyBox =
                new JComboBox<>();

        for (AppData.Company company :
                AppData.COMPANIES) {

            companyBox.addItem(
                    company.symbol
                            + " - "
                            + company.name
                            + " ("
                            + company.country
                            + ")"
            );
        }

        styleCombo(companyBox);

        JSpinner quantity =
                new JSpinner(
                        new SpinnerNumberModel(
                                1,
                                1,
                                1000000,
                                1
                        )
                );

        JRadioButton buy =
                new JRadioButton("BUY", true);

        JRadioButton sell =
                new JRadioButton("SELL");

        buy.setOpaque(false);
        sell.setOpaque(false);

        buy.setForeground(TEXT);
        sell.setForeground(TEXT);

        buy.setFont(H3);
        sell.setFont(H3);

        ButtonGroup group =
                new ButtonGroup();

        group.add(buy);
        group.add(sell);

        JLabel priceLabel =
                new JLabel("Rs.0.00");

        JLabel totalLabel =
                new JLabel("Rs.0.00");

        JLabel cashAfterLabel =
                new JLabel("Rs.0.00");

        priceLabel.setForeground(ACCENT);
        totalLabel.setForeground(ACCENT);
        cashAfterLabel.setForeground(SUCCESS);

        priceLabel.setFont(H2);
        totalLabel.setFont(H2);
        cashAfterLabel.setFont(H3);

        Runnable update = () -> {

            int index =
                    companyBox.getSelectedIndex();

            if (index < 0) {
                return;
            }

            AppData.Company company =
                    AppData.COMPANIES.get(index);

            int qty =
                    (Integer) quantity.getValue();

            double total =
                    company.price * qty;

            priceLabel.setText(
                    AppData.inr(company.price)
            );

            totalLabel.setText(
                    AppData.inr(total)
            );

            double cashAfter =
                    buy.isSelected()
                            ? me.cash - total
                            : me.cash + total;

            cashAfterLabel.setText(
                    AppData.inr(cashAfter)
            );

            cashAfterLabel.setForeground(
                    cashAfter < 0
                            ? DANGER
                            : SUCCESS
            );
        };

        companyBox.addActionListener(
                e -> update.run()
        );

        quantity.addChangeListener(
                e -> update.run()
        );

        buy.addActionListener(
                e -> update.run()
        );

        sell.addActionListener(
                e -> update.run()
        );

        int row = 1;

        row = addRow(
                card,
                g,
                row,
                "Company",
                companyBox
        );

        row = addRow(
                card,
                g,
                row,
                "Current Price",
                priceLabel
        );

        row = addRow(
                card,
                g,
                row,
                "Quantity",
                quantity
        );

        g.gridx = 0;
        g.gridy = row;
        g.weightx = 0;

        card.add(
                muted("Order Type"),
                g
        );

        JPanel radios = new JPanel(
                new FlowLayout(
                        FlowLayout.LEFT,
                        12,
                        0
                )
        );

        radios.setOpaque(false);

        radios.add(buy);
        radios.add(sell);

        g.gridx = 1;
        g.weightx = 1;

        card.add(radios, g);

        row++;

        row = addRow(
                card,
                g,
                row,
                "Estimated Total",
                totalLabel
        );

        row = addRow(
                card,
                g,
                row,
                "Cash After Trade",
                cashAfterLabel
        );

        JButton submit =
                btn("Submit Order", SUCCESS);

        g.gridx = 0;
        g.gridy = row;
        g.gridwidth = 2;

        g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.EAST;

        card.add(submit, g);

        submit.addActionListener(e -> {

            int index =
                    companyBox.getSelectedIndex();

            if (index < 0) {
                return;
            }

            AppData.Company company =
                    AppData.COMPANIES.get(index);

            int qty =
                    (Integer) quantity.getValue();

            boolean isBuy =
                    buy.isSelected();

            double total =
                    company.price * qty;

            AppData.Holding holding =
                    AppData.findHolding(
                            mine,
                            company.symbol
                    );

            if (isBuy) {

                if (me.cash < total) {

                    setStatus(
                            "Insufficient cash.",
                            true
                    );

                    return;
                }

                me.cash -= total;

                if (holding == null) {

                    mine.add(
                            new AppData.Holding(
                                    company.symbol,
                                    qty,
                                    company.price
                            )
                    );

                } else {

                    holding.avgPrice =
                            (
                                    holding.avgPrice
                                            * holding.qty
                                            + company.price * qty
                            )
                                    / (holding.qty + qty);

                    holding.qty += qty;
                }

            } else {

                if (
                        holding == null
                                || holding.qty < qty
                ) {

                    setStatus(
                            "Not enough shares to sell.",
                            true
                    );

                    return;
                }

                me.cash += total;

                holding.qty -= qty;

                if (holding.qty == 0) {
                    mine.remove(holding);
                }
            }

            AppData.HISTORY.add(
                    new String[]{
                            AppData.now(),
                            isBuy ? "BUY" : "SELL",
                            company.symbol,
                            String.valueOf(qty),
                            AppData.inr(company.price),
                            AppData.inr(total)
                    }
            );

            updateMetrics();
            refreshPortfolio();
            refreshHistory();
            refreshWatch();

            setStatus(
                    (isBuy ? "Bought " : "Sold ")
                            + qty
                            + " x "
                            + company.symbol
                            + " for "
                            + AppData.inr(total),
                    false
            );

            update.run();
        });

        update.run();

        wrapper.add(card);

        return wrapper;
    }

    int addRow(
            JPanel panel,
            GridBagConstraints g,
            int row,
            String label,
            JComponent component
    ) {

        g.gridx = 0;
        g.gridy = row;
        g.weightx = 0;

        panel.add(
                muted(label),
                g
        );

        g.gridx = 1;
        g.weightx = 1;

        panel.add(
                component,
                g
        );

        return row + 1;
    }

    // ---------- PORTFOLIO ----------

    JPanel portfolioTab() {

        JPanel panel = new JPanel(
                new BorderLayout(10, 10)
        );

        panel.setBackground(BG_DEEP);

        panel.setBorder(
                new EmptyBorder(14, 6, 6, 6)
        );

        panel.add(
                h1("My Portfolio"),
                BorderLayout.NORTH
        );

        portM = new DefaultTableModel(
                new Object[]{
                        "Symbol",
                        "Company",
                        "Qty",
                        "Avg Price",
                        "Current",
                        "Value",
                        "P&L"
                },
                0
        ) {
            @Override
            public boolean isCellEditable(
                    int row,
                    int column
            ) {
                return false;
            }
        };

        portT = darkTable(portM);

        DefaultTableCellRenderer renderer =
                rightR();

        for (int i = 2; i < 7; i++) {

            portT.getColumnModel()
                    .getColumn(i)
                    .setCellRenderer(renderer);
        }

        refreshPortfolio();

        panel.add(
                scroll(portT),
                BorderLayout.CENTER
        );

        return panel;
    }

    void refreshPortfolio() {

        if (portM == null) {
            return;
        }

        portM.setRowCount(0);

        for (AppData.Holding holding : mine) {

            AppData.Company company =
                    AppData.findCompany(
                            holding.symbol
                    );

            double current =
                    company != null
                            ? company.price
                            : holding.avgPrice;

            double invested =
                    holding.qty * holding.avgPrice;

            double value =
                    holding.qty * current;

            double profitLoss =
                    value - invested;

            portM.addRow(
                    new Object[]{
                            holding.symbol,
                            company != null
                                    ? company.name
                                    : holding.symbol,
                            holding.qty,
                            AppData.inr(
                                    holding.avgPrice
                            ),
                            AppData.inr(current),
                            AppData.inr(value),
                            (profitLoss >= 0 ? "+" : "")
                                    + AppData.inr(profitLoss)
                    }
            );
        }
    }

    // ---------- WATCHLIST ----------

    JPanel watchlistTab() {

        JPanel panel = new JPanel(
                new BorderLayout(10, 10)
        );

        panel.setBackground(BG_DEEP);

        panel.setBorder(
                new EmptyBorder(14, 6, 6, 6)
        );

        JPanel top = new JPanel(
                new BorderLayout()
        );

        top.setOpaque(false);

        JPanel titles = new JPanel();

        titles.setOpaque(false);

        titles.setLayout(
                new BoxLayout(
                        titles,
                        BoxLayout.Y_AXIS
                )
        );

        titles.add(h1("My Watchlist"));
        titles.add(
                muted(
                        "Companies you're tracking"
                )
        );

        top.add(titles, BorderLayout.WEST);

        JButton remove =
                btn("Remove Selected", DANGER);

        remove.addActionListener(e -> {

            int row =
                    watchT.getSelectedRow();

            if (row == -1) {

                setStatus(
                        "Select a row first.",
                        true
                );

                return;
            }

            String symbol =
                    (String) watchM.getValueAt(row, 0);

            watchlist.remove(symbol);

            refreshWatch();

            setStatus(
                    symbol + " removed from watchlist.",
                    false
            );
        });

        top.add(remove, BorderLayout.EAST);

        panel.add(top, BorderLayout.NORTH);

        watchM = new DefaultTableModel(
                new Object[]{
                        "Symbol",
                        "Company",
                        "Sector",
                        "Country",
                        "Price",
                        "Change %"
                },
                0
        ) {
            @Override
            public boolean isCellEditable(
                    int row,
                    int column
            ) {
                return false;
            }
        };

        watchT = darkTable(watchM);

        watchT.getColumnModel()
                .getColumn(4)
                .setCellRenderer(rightR());

        watchT.getColumnModel()
                .getColumn(5)
                .setCellRenderer(rightR());

        watchCardStack =
                new JPanel(new CardLayout());

        watchCardStack.setBackground(BG_DEEP);

        JPanel empty = new JPanel(
                new BorderLayout()
        );

        empty.setBackground(BG_CARD);

        empty.setBorder(
                BorderFactory.createLineBorder(BORDER)
        );

        JLabel hint = new JLabel(
                "<html><center>"
                        + "<br><br>"
                        + "<b>Your watchlist is empty</b>"
                        + "<br><br>"
                        + "Go to Market and add a company."
                        + "<br><br></center></html>",
                SwingConstants.CENTER
        );

        hint.setForeground(TEXT_MUTED);

        empty.add(hint, BorderLayout.CENTER);

        JPanel table = new JPanel(
                new BorderLayout()
        );

        table.setBackground(BG_DEEP);

        table.add(
                scroll(watchT),
                BorderLayout.CENTER
        );

        watchCardStack.add(empty, "EMPTY");
        watchCardStack.add(table, "TABLE");

        watchCardLayout =
                (CardLayout) watchCardStack.getLayout();

        refreshWatch();

        panel.add(
                watchCardStack,
                BorderLayout.CENTER
        );

        return panel;
    }

    void refreshWatch() {

        if (watchM == null) {
            return;
        }

        watchM.setRowCount(0);

        for (String symbol : watchlist) {

            AppData.Company company =
                    AppData.findCompany(symbol);

            if (company != null) {

                watchM.addRow(
                        new Object[]{
                                company.symbol,
                                company.name,
                                company.sector,
                                AppData.countryFlag(
                                        company.country
                                ),
                                AppData.inr(
                                        company.price
                                ),
                                AppData.pct(
                                        company.changePct
                                )
                        }
                );
            }
        }

        if (watchCardLayout != null) {

            watchCardLayout.show(
                    watchCardStack,
                    watchlist.isEmpty()
                            ? "EMPTY"
                            : "TABLE"
            );
        }
    }

    // ---------- HISTORY ----------

    JPanel historyTab() {

        JPanel panel = new JPanel(
                new BorderLayout(10, 10)
        );

        panel.setBackground(BG_DEEP);

        panel.setBorder(
                new EmptyBorder(14, 6, 6, 6)
        );

        panel.add(
                h1("Transaction History"),
                BorderLayout.NORTH
        );

        histM = new DefaultTableModel(
                new Object[]{
                        "Time",
                        "Type",
                        "Symbol",
                        "Qty",
                        "Price",
                        "Total"
                },
                0
        ) {
            @Override
            public boolean isCellEditable(
                    int row,
                    int column
            ) {
                return false;
            }
        };

        JTable table = darkTable(histM);

        DefaultTableCellRenderer renderer =
                rightR();

        for (int i = 3; i < 6; i++) {

            table.getColumnModel()
                    .getColumn(i)
                    .setCellRenderer(renderer);
        }

        refreshHistory();

        panel.add(
                scroll(table),
                BorderLayout.CENTER
        );

        return panel;
    }

    void refreshHistory() {

        if (histM == null) {
            return;
        }

        histM.setRowCount(0);

        for (String[] history :
                AppData.HISTORY) {

            histM.addRow(history);
        }
    }

    // ---------- GUIDE ----------

    JPanel guideTab() {

        JPanel outer = new JPanel(
                new BorderLayout()
        );

        outer.setBackground(BG_DEEP);

        outer.setBorder(
                new EmptyBorder(14, 6, 6, 6)
        );

        outer.add(
                h1("Investing Guide"),
                BorderLayout.NORTH
        );

        JPanel content = new JPanel();

        content.setLayout(
                new BoxLayout(
                        content,
                        BoxLayout.Y_AXIS
                )
        );

        content.setBackground(BG_DEEP);

        content.add(
                guideCard(
                        "1. Create Your Account",
                        "Create a new account from the login screen. "
                                + "Each account starts with virtual cash."
                )
        );

        content.add(
                guideCard(
                        "2. Explore the Market",
                        "Open the Market tab to view companies, "
                                + "prices, sectors and countries."
                )
        );

        content.add(
                guideCard(
                        "3. Build a Watchlist",
                        "Select a company from the Market tab "
                                + "and add it to your watchlist."
                )
        );

        content.add(
                guideCard(
                        "4. Place Your First Trade",
                        "Go to Trade, select a company, enter "
                                + "quantity and choose BUY or SELL."
                )
        );

        content.add(
                guideCard(
                        "5. Track Your Portfolio",
                        "The Portfolio tab displays your holdings, "
                                + "current value and profit or loss."
                )
        );

        content.add(
                guideCard(
                        "6. Review Your History",
                        "Every completed transaction appears "
                                + "in the History tab."
                )
        );

        content.add(
                guideCard(
                        "7. Understand the Dashboard",
                        "Dashboard displays cash, invested amount, "
                                + "portfolio value and total P&L."
                )
        );

        JScrollPane scroll =
                new JScrollPane(content);

        scroll.setBorder(
                BorderFactory.createLineBorder(BORDER)
        );

        scroll.getViewport()
                .setBackground(BG_DEEP);

        outer.add(
                scroll,
                BorderLayout.CENTER
        );

        return outer;
    }

    JPanel guideCard(
            String title,
            String body
    ) {

        JPanel card = new JPanel();

        card.setLayout(
                new BoxLayout(
                        card,
                        BoxLayout.Y_AXIS
                )
        );

        card.setBackground(BG_CARD);

        card.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        new EmptyBorder(16, 20, 16, 20)
                )
        );

        JLabel heading = new JLabel(title);

        heading.setFont(H3);
        heading.setForeground(ACCENT);

        JLabel description = new JLabel(
                "<html><div style='width:800px;'>"
                        + body
                        + "</div></html>"
        );

        description.setFont(BODY);
        description.setForeground(TEXT);

        card.add(heading);
        card.add(Box.createVerticalStrut(8));
        card.add(description);

        JPanel wrapper = new JPanel(
                new BorderLayout()
        );

        wrapper.setBackground(BG_DEEP);

        wrapper.setBorder(
                new EmptyBorder(0, 0, 12, 0)
        );

        wrapper.add(card, BorderLayout.CENTER);

        return wrapper;
    }

    // ---------- ADMIN ----------

    JPanel adminTab() {

        JPanel panel = new JPanel(
                new BorderLayout(10, 10)
        );

        panel.setBackground(BG_DEEP);

        panel.setBorder(
                new EmptyBorder(14, 6, 6, 6)
        );

        panel.add(
                h1("Admin Panel"),
                BorderLayout.NORTH
        );

        userM = new DefaultTableModel(
                new Object[]{
                        "Username",
                        "Display Name",
                        "Role",
                        "Cash"
                },
                0
        ) {
            @Override
            public boolean isCellEditable(
                    int row,
                    int column
            ) {
                return false;
            }
        };

        userT = darkTable(userM);

        userT.getColumnModel()
                .getColumn(3)
                .setCellRenderer(rightR());

        for (AppData.User user :
                AppData.USERS) {

            userM.addRow(
                    new Object[]{
                            user.username,
                            user.displayName,
                            user.role,
                            AppData.inr(user.cash)
                    }
            );
        }

        panel.add(
                scroll(userT),
                BorderLayout.CENTER
        );

        JPanel buttons = new JPanel(
                new FlowLayout(
                        FlowLayout.RIGHT,
                        10,
                        0
                )
        );

        buttons.setOpaque(false);

        JButton add =
                btn("Add User", SUCCESS);

        add.addActionListener(
                e -> addUser()
        );

        JButton delete =
                btn("Delete Selected", DANGER);

        delete.addActionListener(
                e -> deleteUser()
        );

        buttons.add(add);
        buttons.add(delete);

        panel.add(
                buttons,
                BorderLayout.SOUTH
        );

        return panel;
    }

    void addUser() {

        JTextField username = input();
        JPasswordField password = passInput();
        JTextField name = input();

        JPanel form = new JPanel(
                new GridLayout(0, 1, 6, 6)
        );

        form.setBackground(BG_CARD);

        form.add(muted("Username"));
        form.add(username);

        form.add(muted("Password"));
        form.add(password);

        form.add(muted("Display Name"));
        form.add(name);

        int result =
                JOptionPane.showConfirmDialog(
                        this,
                        form,
                        "Add User",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String user =
                username.getText().trim();

        String pass =
                new String(password.getPassword());

        String display =
                name.getText().trim();

        if (
                user.isEmpty()
                        || pass.isEmpty()
                        || display.isEmpty()
        ) {

            setStatus(
                    "All fields are required.",
                    true
            );

            return;
        }

        for (AppData.User existing :
                AppData.USERS) {

            if (
                    existing.username.equalsIgnoreCase(
                            user
                    )
            ) {

                setStatus(
                        "Username already exists.",
                        true
                );

                return;
            }
        }

        AppData.USERS.add(
                new AppData.User(
                        user,
                        pass,
                        display,
                        "USER",
                        100000
                )
        );

        userM.addRow(
                new Object[]{
                        user,
                        display,
                        "USER",
                        AppData.inr(100000)
                }
        );

        setStatus(
                "User added successfully.",
                false
        );
    }

    void deleteUser() {

        int row =
                userT.getSelectedRow();

        if (row == -1) {

            setStatus(
                    "Select a user first.",
                    true
            );

            return;
        }

        String username =
                (String) userM.getValueAt(row, 0);

        if (
                username.equals(me.username)
        ) {

            setStatus(
                    "You cannot delete your own account.",
                    true
            );

            return;
        }

        AppData.USERS.removeIf(
                user -> user.username.equals(username)
        );

        userM.removeRow(row);

        setStatus(
                "User deleted successfully.",
                false
        );
    }

    // ---------- STATUS BAR ----------

    JPanel statusBar() {

        JPanel bar = new JPanel(
                new BorderLayout()
        );

        bar.setBackground(BG_PANEL);

        bar.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(
                                1, 0, 0, 0, BORDER
                        ),
                        new EmptyBorder(8, 20, 8, 20)
                )
        );

        status = new JLabel("Ready");

        status.setFont(SMALL);
        status.setForeground(TEXT_MUTED);

        JLabel version =
                new JLabel("SSA Pro v4.0");

        version.setFont(SMALL);
        version.setForeground(TEXT_MUTED);

        bar.add(status, BorderLayout.WEST);
        bar.add(version, BorderLayout.EAST);

        return bar;
    }

    void setStatus(
            String message,
            boolean error
    ) {

        if (status == null) {
            return;
        }

        status.setText(message);

        status.setForeground(
                error ? DANGER : SUCCESS
        );
    }
}
