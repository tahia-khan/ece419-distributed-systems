/*
   Copyright (C) 2004 Geoffrey Alan Washburn

   This program is free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation; either version 2
   of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
   USA.
   */

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import javax.swing.BorderFactory;
import java.io.Serializable;

import java.io.*;
import java.net.*;

/**
 * The entry point and glue code for the game.  It also contains some helpful
 * global utility methods.
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: Mazewar.java 371 2004-02-10 21:55:32Z geoffw $
 */

public class Mazewar extends JFrame {

    /**
     * The default width of the {@link Maze}.
     */
    private final int mazeWidth = 20;

    /**
     * The default height of the {@link Maze}.
     */
    private final int mazeHeight = 10;

    /**
     * The default random seed for the {@link Maze}.
     * All implementations of the same protocol must use 
     * the same seed value, or your mazes will be different.
     */
    private final int mazeSeed = 42;

    /**
     * The {@link Maze} that the game uses.
     */
    private Maze maze = null;

    /**
     * The {@link GUIClient} for the game.
     */
    private GUIClient guiClient = null;

    /**
     * The panel that displays the {@link Maze}.
     */
    private OverheadMazePanel overheadPanel = null;

    /**
     * The table the displays the scores.
     */
    private JTable scoreTable = null;

    /* ADDING - network resources */
    Socket mwsSocket = null;
    ObjectOutputStream out = null; 
    ObjectInputStream in = null;

    /* ADDING - game data */
    BlockingQueue<MazeEvent> eventQueue;
    ConcurrentMap<String, String> clientTable;

    /** 
     * Create the textpane statically so that we can 
     * write to it globally using
     * the static consolePrint methods  
     */
    private static final JTextPane console = new JTextPane();

    /** 
     * Write a message to the console followed by a newline.
     * @param msg The {@link String} to print.
     */ 
    public static synchronized void consolePrintLn(String msg) {
        console.setText(console.getText()+msg+"\n");
    }

    /** 
     * Write a message to the console.
     * @param msg The {@link String} to print.
     */ 
    public static synchronized void consolePrint(String msg) {
        console.setText(console.getText()+msg);
    }

    /** 
     * Clear the console. 
     */
    public static synchronized void clearConsole() {
        console.setText("");
    }

    /**
     * Static method for performing cleanup before exiting the game.
     */
    public static void quit() {
        // Put any network clean-up code you might have here.
        // (inform other implementations on the network that you have 
        //  left, etc.)


        System.exit(0);
    }

    /** 
     * The place where all the pieces are put together. 
     */
    public Mazewar() {
        super("ECE419 Mazewar");
        consolePrintLn("ECE419 Mazewar started!");

        // Create the maze
        maze = new MazeImpl(new Point(mazeWidth, mazeHeight), mazeSeed);
        assert(maze != null);

        // Have the ScoreTableModel listen to the maze to find
        // out how to adjust scores.
        ScoreTableModel scoreModel = new ScoreTableModel();
        assert(scoreModel != null);
        maze.addMazeListener(scoreModel);

        // Throw up a dialog to get the GUIClient name.
        String name = JOptionPane.showInputDialog("Enter your name");
        if((name == null) || (name.length() == 0)) {
            Mazewar.quit();
        }

        //pass into initialize socket after error checking. port, host, name
        /*String host = JOptionPane.showInputDialog("Enter host name");
          if((host == null) || (host.length() == 0)) {
          Mazewar.quit();
          }*/

        // You may want to put your network initialization code somewhere in
        // here.

        initializeSocket();             

        // Create the GUIClient and connect it to the KeyListener queue
        guiClient = new GUIClient(name);
        maze.addClient(guiClient);
        this.addKeyListener(guiClient);

        // Register client to server over socket
        registerClient();

        // Use braces to force constructors not to be called at the beginning of the
        // constructor.
        {
            maze.addClient(new RobotClient("Norby"));
            maze.addClient(new RobotClient("Robbie"));
            maze.addClient(new RobotClient("Clango"));
            maze.addClient(new RobotClient("Marvin"));
        }


        // Create the panel that will display the maze.
        overheadPanel = new OverheadMazePanel(maze, guiClient);
        assert(overheadPanel != null);
        maze.addMazeListener(overheadPanel);

        // Don't allow editing the console from the GUI
        console.setEditable(false);
        console.setFocusable(false);
        console.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder()));

        // Allow the console to scroll by putting it in a scrollpane
        JScrollPane consoleScrollPane = new JScrollPane(console);
        assert(consoleScrollPane != null);
        consoleScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Console"));

        // Create the score table
        scoreTable = new JTable(scoreModel);
        assert(scoreTable != null);
        scoreTable.setFocusable(false);
        scoreTable.setRowSelectionAllowed(false);

        // Allow the score table to scroll too.
        JScrollPane scoreScrollPane = new JScrollPane(scoreTable);
        assert(scoreScrollPane != null);
        scoreScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Scores"));

        // Create the layout manager
        GridBagLayout layout = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        getContentPane().setLayout(layout);

        // Define the constraints on the components.
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.weighty = 3.0;
        c.gridwidth = GridBagConstraints.REMAINDER;
        layout.setConstraints(overheadPanel, c);
        c.gridwidth = GridBagConstraints.RELATIVE;
        c.weightx = 2.0;
        c.weighty = 1.0;
        layout.setConstraints(consoleScrollPane, c);
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.weightx = 1.0;
        layout.setConstraints(scoreScrollPane, c);

        // Add the components
        getContentPane().add(overheadPanel);
        getContentPane().add(consoleScrollPane);
        getContentPane().add(scoreScrollPane);

        // Pack everything neatly.
        pack();

        // Let the magic begin.
        setVisible(true);
        overheadPanel.repaint();
        this.requestFocusInWindow();
    }


    /**
     * Entry point for the game.  
     * @param args Command-line arguments.
     */
    public static void main(String args[]) {

        /* Create the GUI */
        new Mazewar();
    }

    public boolean initializeSocket(){
        /* Connect to central game server. */
        try {
            /* Using this hardcoded port for now, eventually make this userinput at GUI interface in Mazewar.java*/
            String hostname = "localhost";
            int port = 4444;

            mwsSocket = new Socket(hostname,port);
            out = new ObjectOutputStream(mwsSocket.getOutputStream());
            in = new ObjectInputStream(mwsSocket.getInputStream());

        } catch (Exception e) {
            System.exit(1);
        }

        return true;

    }

    public boolean registerClient(){
        MazePacket packetToServer = new MazePacket();
        MazePacket packetFromServer = new MazePacket();

        try{
            /* Initialize handshaking with server */
            Random rand = new Random();

            packetToServer.packet_type = MazePacket.CLIENT_REGISTER;
            packetToServer.sequence_num = rand.nextInt(1000) + 1; /* Where to store ? should this even be in Maze.java? (not user data) */
            packetToServer.client_name = "Kanye"        /* Using a hardcoded value for now - add to GUI interface eventually */
                out.writeObject(packetToServer);

            /* Wait for server acknowledgement */
            packetFromServer = in.readObject();
            if (packetFromServer == null || packetFromServer.packet_type != MazePacket.SERVER_ACK) {
                System.out.println("Server did not verify connection");
            }



            System.out.println("Server verified connection!");

        }catch (Exception e){

        }

        return true;
    }


}
