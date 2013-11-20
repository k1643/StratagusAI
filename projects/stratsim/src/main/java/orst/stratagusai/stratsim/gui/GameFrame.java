package orst.stratagusai.stratsim.gui;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import org.apache.log4j.Logger;
import orst.stratagusai.Player;
import orst.stratagusai.Unit;
import orst.stratagusai.stratplan.PlayerGroups;
import orst.stratagusai.stratplan.StrategicPlan;
import orst.stratagusai.stratplan.UnitGroup;
import orst.stratagusai.stratplan.analysis.GroupAnalysis;
import orst.stratagusai.stratplan.mgr.StrategyController;
import orst.stratagusai.stratplan.model.GameMap;
import orst.stratagusai.stratplan.model.GameState;
import orst.stratagusai.stratplan.model.GameStates;
import orst.stratagusai.stratplan.model.Region;
import orst.stratagusai.stratplan.persist.StrategicPlanDOT;
import orst.stratagusai.stratplan.persist.StrategicPlanWriter;
import orst.stratagusai.stratsim.model.SimController;
import orst.stratagusai.stratsim.model.SimState;
import orst.stratagusai.stratsim.planner.GoalDrivenPlanner;
import orst.stratagusai.stratsim.planner.SwitchingPlanner;
import orst.stratagusai.stratsim.planner.StrategyTemplate;
import orst.stratagusai.util.Randomizer;

/**
 * View and control game state.
 * 
 * @author bking
 */
public class GameFrame extends javax.swing.JFrame {
    private static final Logger log = Logger.getLogger(GameFrame.class);

    protected WindowListener wl = new WindowAdapter() {
        @Override
        public void windowOpened(WindowEvent e) {
            newGame();
        }
    };

    private String gameFile = "2bases.txt";
    private String strategy0;
    private String strategy1;

    /** run simulation and update viewer */
    protected class SimRunner implements Runnable {

        /** flag to pause the simulation */
        private boolean pause = false;

        private SimController sim;
        private GoalDrivenPlanner planner0;
        private StrategicPlan plan0;
        private GoalDrivenPlanner planner1;
        private StrategicPlan plan1;

        public void SimRunner() {

        }

        public void newGame() {
            sim = null;
            planner0 = null;
            planner1 = null;
        }

        public void setPause(boolean pause) {
            this.pause = pause;
        }

        public void run() {

            if (sim == null) {
                GameState state = GameStates.getGameState(gameFile);
                System.out.println("running map " + gameFile);

                long seed = Long.valueOf(seedField.getText());
                Randomizer.init(seed);
                planner0 = new GoalDrivenPlanner();
                planner0.setTemplate(StrategyTemplate.getNamedTemplate(strategy0));
                planner1 = new GoalDrivenPlanner();
                planner1.setTemplate(StrategyTemplate.getNamedTemplate(strategy1));
                PlayerGroups[] gs = GroupAnalysis.defineGroups(state);
                SimState s = new SimState(gs, state);
                plan0 = planner0.makePlan(0, s);  // planner adds new groups to StrategicState.
                plan1 = planner1.makePlan(1, s);

                sim = new SimController(s);
                sim.setPlan(0, plan0);
                sim.setPlan(1, plan1);

                logPlan(0, plan0, sim.getCycle());
                logPlan(1, plan1, sim.getCycle());

                mapView.setGame(s.getGameState());
            }           

            try {
                int prevPlanCycle = sim.getCycle();
                while (!sim.isTerminal() && 
                        sim.getCycle() < SimController.MAX_CYCLES &&
                        !pause) {
                    SimState s = (SimState) sim.getState();
                    sim.simulate(SimController.CYCLE_INCREMENT);
                    updateGame(s.getGameState(), sim.getPlans());
                    repaint();
                    Thread.sleep(300);  // from 100-500
                    if (jReplan.isSelected() && sim.getCycle() - prevPlanCycle > StrategyController.REPLAN_CYCLES) {                        
                        log.debug("replan at cycle " + sim.getCycle());
                        // need to clean active commands because merging groups will remove GroupSim units.
                        sim.update(); // prepare for planning
                        plan0 = planner0.replan(0, plan0, s);
                        plan1 = planner1.replan(1, plan1, s);
                        logPlan(0, plan0, sim.getCycle());
                        logPlan(1, plan1, sim.getCycle());
                        sim.setPlan(0, plan0);
                        sim.setPlan(1, plan1);
                        prevPlanCycle = sim.getCycle();
                    }
                }
                pause = false;
                updateGame(sim.getState().getGameState(), sim.getPlans());
                repaint();
            } catch (InterruptedException e) {
                log.error(e);
            }

            simThread = null;
            newGameButton.setEnabled(true);
            runButton.setActionCommand("Run");
            runButton.setText("Run");
        }
    }

    private SimRunner simRunner = new SimRunner();

    private Thread simThread;
    
    /** Creates new form GameFrame */
    public GameFrame() {
        initComponents();
        player0Label.setForeground(MapView.ownerColor[0]);
        player1Label.setForeground(MapView.ownerColor[1]);
        String strategy_set = "atk-dfnd";
        StrategyTemplate[] strategies = SwitchingPlanner.getStrategies(strategy_set);
        if (strategies.length > 0) {
            strategy0 = strategies[0].getName();
            strategy1 = strategy0;
        }
        for (int i = 0; i < strategies.length; i++) {
            player0Strategy.addItem(strategies[i].getName());
            player1Strategy.addItem(strategies[i].getName());
        }
        mapSelection.addItem("2bases");
        mapSelection.addItem("2bases switched");
        mapSelection.addItem("the-right-strategy");
        mapSelection.addItem("the-right-strategy switched");
        gameFile = "2bases-game.txt";
        
        runButton.setActionCommand("Run");
        addWindowListener(wl);
    }

    public void setStrategy0(String strategy0) {
        this.strategy0 = strategy0;
    }

    public void setStrategy1(String strategy1) {
        this.strategy1 = strategy1;
    }

    void clearGame() {
        timeField.setText("");
        player0Points.setText("0");
        player1Points.setText("0");
        pointSum.setText("0");

        for (int i = 0; i < groupTable.getRowCount(); i++) {
            for (int j = 0; j < 5; j++) {
                groupTable.setValueAt("", i, j);
            }
        }
    }

    void updateGame(GameState state, StrategicPlan...plans) {

        timeField.setText(String.valueOf(state.getCycle()));

        // set player information.
        Set<Player> players = state.getPlayers();
        int[] scores = state.getScores();
        player0Points.setText(String.valueOf(scores[0]));  // hit points
        player1Points.setText(String.valueOf(scores[1]));  // hit points
        pointSum.setText(String.valueOf(scores[0]-scores[1]));

        // show group info
        int rows = 0;
        GameMap map = state.getMap();
        rows = showGroupInfo(0, rows, map, plans[0].getPlayerGroups());
        rows = showGroupInfo(1, rows, map, plans[1].getPlayerGroups());
        for (int i = rows; i < groupTable.getRowCount(); i++) {
            for (int j = 0; j < 5; j++) {
                groupTable.setValueAt("", i, j);
            }
        }
        
        mapView.setGame(state);
    }

    protected void newGame() {
        clearGame();
        simRunner.newGame();
        timeField.setText("0");
        runButton.setSelected(false);
        runButton.setEnabled(true);
        newGameButton.setEnabled(false);
    }

    private int showGroupInfo(int playerId, int row, GameMap map, PlayerGroups groups) {
        // sort the groups
        Map<Integer,UnitGroup> sortedGroups = new TreeMap<Integer,UnitGroup>();
        for (UnitGroup g : groups.getGroups()) {
            sortedGroups.put(g.getId(), g);
        }
        for (UnitGroup g : sortedGroups.values()) {
            groupTable.setValueAt(g.getId(), row, 0);
            groupTable.setValueAt(playerId, row, 1);  // owner
            groupTable.setValueAt(g.isCombat() ? "Combat" : "Production",
                                  row, 2);
            groupTable.setValueAt(g.getHitPoints(), row, 3);  // hit points
            // show location of GroupSim.
            if (g.isEmpty()) {
                groupTable.setValueAt("empty", row, 4);  // location
            } else {
                Unit u = (Unit) g.getRepresentative();
                Region r = map.getRegion(u);
                groupTable.setValueAt(r.getId(), row, 4);  // location
            }
            row++;
        }
        return row;
    }

    protected final static String NL = System.getProperty("line.separator");

    private void logPlan(int playerId, StrategicPlan plan, int cycle) {
        if (!log.isDebugEnabled()) {
            return;
        }
        String filebase = "plan_" + playerId + "_" + System.currentTimeMillis();
        try {
            String label = String.format("Strategy %s, Cycle %d, Time %s", plan.getName(), cycle, DateFormat.getTimeInstance().format(new Date()));
            StrategicPlanDOT.writeDOTFile(plan, label, filebase + ".dot");
            StrategicPlanWriter.write(plan, filebase + ".txt");
        } catch (IOException ex) {
            log.error("unable to write " + filebase, ex);
        }
    }



    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jButton1 = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        timeLabel = new javax.swing.JLabel();
        timeField = new javax.swing.JTextField();
        jScrollPane2 = new javax.swing.JScrollPane();
        groupTable = new javax.swing.JTable();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        player0Strategy = new javax.swing.JComboBox();
        jLabel4 = new javax.swing.JLabel();
        player0Points = new javax.swing.JTextField();
        player1Points = new javax.swing.JTextField();
        player0Label = new javax.swing.JLabel();
        player1Strategy = new javax.swing.JComboBox();
        player1Label = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        pointSum = new javax.swing.JTextField();
        newGameButton = new javax.swing.JButton();
        mapView = new orst.stratagusai.stratsim.gui.MapView();
        jReplan = new javax.swing.JCheckBox();
        runButton = new javax.swing.JButton();
        mapSelection = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        seedField = new javax.swing.JTextField();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        jMenuItem2 = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        jMenuItem3 = new javax.swing.JMenuItem();

        jButton1.setText("jButton1");

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Game State"));

        timeLabel.setText("Cycle");

        timeField.setEditable(false);
        timeField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        timeField.setText("0");

        groupTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null}
            },
            new String [] {
                "Group", "Player", "Type", "Points", "Region"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane2.setViewportView(groupTable);
        groupTable.getColumnModel().getColumn(0).setPreferredWidth(20);
        groupTable.getColumnModel().getColumn(1).setPreferredWidth(20);
        groupTable.getColumnModel().getColumn(3).setPreferredWidth(50);

        jLabel2.setText("Player");

        jLabel3.setText("Strategy");

        player0Strategy.setEditable(true);
        player0Strategy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                player0StrategyActionPerformed(evt);
            }
        });

        jLabel4.setText("Points");

        player0Points.setEditable(false);
        player0Points.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        player0Points.setText("0");
        player0Points.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                player0PointsActionPerformed(evt);
            }
        });

        player1Points.setEditable(false);
        player1Points.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        player1Points.setText("0");
        player1Points.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                player1PointsActionPerformed(evt);
            }
        });

        player0Label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        player0Label.setText("0");

        player1Strategy.setEditable(true);
        player1Strategy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                player1StrategyActionPerformed(evt);
            }
        });

        player1Label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        player1Label.setText("1");

        jLabel7.setText("Sum");

        pointSum.setEditable(false);
        pointSum.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        pointSum.setText("0");
        pointSum.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pointSumActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(timeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(timeField, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabel2)
                            .addComponent(player1Label, javax.swing.GroupLayout.DEFAULT_SIZE, 37, Short.MAX_VALUE)
                            .addComponent(player0Label, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3)
                            .addComponent(player0Strategy, javax.swing.GroupLayout.PREFERRED_SIZE, 257, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(player1Strategy, javax.swing.GroupLayout.PREFERRED_SIZE, 257, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabel4)
                            .addComponent(player0Points, javax.swing.GroupLayout.DEFAULT_SIZE, 52, Short.MAX_VALUE)
                            .addComponent(player1Points))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel7)
                            .addComponent(pointSum, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(12, 12, 12))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(13, 13, 13)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(timeLabel)
                    .addComponent(timeField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel2)
                            .addComponent(jLabel3))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(player0Strategy, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(player0Label))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(player1Strategy, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(player1Label)))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel4)
                            .addComponent(jLabel7))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(player0Points, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(pointSum, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(player1Points, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 385, Short.MAX_VALUE)
                .addGap(43, 43, 43))
        );

        newGameButton.setText("New");
        newGameButton.setActionCommand("new");
        newGameButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newGameAction(evt);
            }
        });

        javax.swing.GroupLayout mapViewLayout = new javax.swing.GroupLayout(mapView);
        mapView.setLayout(mapViewLayout);
        mapViewLayout.setHorizontalGroup(
            mapViewLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 573, Short.MAX_VALUE)
        );
        mapViewLayout.setVerticalGroup(
            mapViewLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 688, Short.MAX_VALUE)
        );

        jReplan.setSelected(true);
        jReplan.setText("Replan");

        runButton.setText("Run");
        runButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runButtonActionPerformed(evt);
            }
        });

        mapSelection.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mapSelectionActionPerformed(evt);
            }
        });

        jLabel1.setText("Map");

        jLabel8.setText("Seed");

        seedField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        seedField.setText("14");

        jMenu1.setText("File");

        jMenuItem1.setText("Open");
        jMenuItem1.setActionCommand("open");
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openSelected(evt);
            }
        });
        jMenu1.add(jMenuItem1);

        jMenuItem2.setText("Save...");
        jMenuItem2.setActionCommand("save");
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveSelected(evt);
            }
        });
        jMenu1.add(jMenuItem2);
        jMenu1.add(jSeparator1);

        jMenuItem3.setText("Exit");
        jMenuItem3.setActionCommand("exit");
        jMenuItem3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitSelected(evt);
            }
        });
        jMenu1.add(jMenuItem3);

        jMenuBar1.add(jMenu1);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(mapView, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(newGameButton, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(runButton)
                                .addGap(12, 12, 12)
                                .addComponent(jReplan))
                            .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(12, 12, 12)
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(mapSelection, javax.swing.GroupLayout.PREFERRED_SIZE, 221, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(seedField, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(mapSelection, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel8)
                    .addComponent(seedField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(30, 30, 30)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(newGameButton)
                    .addComponent(jReplan)
                    .addComponent(runButton))
                .addContainerGap())
            .addComponent(mapView, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void openSelected(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openSelected
        String cmd = evt.getActionCommand();
        if ("open".equals(cmd)) {
            log.debug("open...");
        }
    }//GEN-LAST:event_openSelected

    private void saveSelected(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveSelected
        String cmd = evt.getActionCommand();
        if ("save".equals(cmd)) {
            log.debug("save...");
/*
            try {
                GameStateWriter.write(game, "game.txt");
            } catch (IOException e) {
                log.error("unable to save game state", e);
            } */
        }
    }//GEN-LAST:event_saveSelected

    private void exitSelected(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitSelected
        String cmd = evt.getActionCommand();
        if ("exit".equals(cmd)) {
            setVisible(false);
            dispose();
            System.exit(0);
        }
    }//GEN-LAST:event_exitSelected

    private void newGameAction(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newGameAction
        newGame();
    }//GEN-LAST:event_newGameAction

    private void runButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runButtonActionPerformed
        // TODO add your handling code here:

        String cmd = evt.getActionCommand();
        javax.swing.JButton button = (javax.swing.JButton) evt.getSource();
        if ("Run".equals(cmd)) {
          log.debug("Run game.");

          if (simThread == null || !simThread.isAlive()) {
              
              simThread = new Thread(simRunner);
              simThread.start();
              button.setText("Pause");
              button.setActionCommand("Pause");
              
          } else {
              log.error("Simulation thread still running.");
          }

        } else if ("Pause".equals(cmd)) {
          log.debug("pause game.");
          if (simRunner != null) {
              simRunner.setPause(true);
          }
          button.setActionCommand("Run");
          button.setText("Run");
        }
    }//GEN-LAST:event_runButtonActionPerformed

    private void mapSelectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mapSelectionActionPerformed
        // TODO add your handling code here:
        JComboBox cb = (JComboBox)evt.getSource();
        String mapName = (String)cb.getSelectedItem();
        if ("2bases".equals(mapName))
            gameFile = "2bases-game.txt";
        else if ("2bases switched".equals(mapName))
            gameFile = "2bases_switched.txt";
        else if ("the-right-strategy".equals(mapName))
            gameFile = "the-right-strategy-game.txt";
        else if ("the-right-strategy switched".equals(mapName))
            gameFile = "the-right-strategy-game_switched.txt";
        else
            JOptionPane.showMessageDialog(this, "Unknown map:\n" + mapName);

    }//GEN-LAST:event_mapSelectionActionPerformed

    private void player0PointsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_player0PointsActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_player0PointsActionPerformed

    private void player1PointsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_player1PointsActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_player1PointsActionPerformed

    private void player0StrategyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_player0StrategyActionPerformed
        JComboBox cb = (JComboBox)evt.getSource();
        strategy0 = (String)cb.getSelectedItem();
    }//GEN-LAST:event_player0StrategyActionPerformed

    private void player1StrategyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_player1StrategyActionPerformed
        JComboBox cb = (JComboBox)evt.getSource();
        strategy1 = (String)cb.getSelectedItem();
    }//GEN-LAST:event_player1StrategyActionPerformed

    private void pointSumActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pointSumActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_pointSumActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTable groupTable;
    private javax.swing.JButton jButton1;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JMenuItem jMenuItem3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JCheckBox jReplan;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JComboBox mapSelection;
    private orst.stratagusai.stratsim.gui.MapView mapView;
    private javax.swing.JButton newGameButton;
    private javax.swing.JLabel player0Label;
    private javax.swing.JTextField player0Points;
    private javax.swing.JComboBox player0Strategy;
    private javax.swing.JLabel player1Label;
    private javax.swing.JTextField player1Points;
    private javax.swing.JComboBox player1Strategy;
    private javax.swing.JTextField pointSum;
    private javax.swing.JButton runButton;
    private javax.swing.JTextField seedField;
    private javax.swing.JTextField timeField;
    private javax.swing.JLabel timeLabel;
    // End of variables declaration//GEN-END:variables


}
