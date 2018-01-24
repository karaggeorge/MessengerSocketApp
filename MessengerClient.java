/******
 * MessengerClient
 * Author: Christian Duncan
 *
 * This client sends streams of messages to the InventoryServer.
 * It is designed as a means to test the server but could easily be
 * modified to be more interactive.
 * See the InternetServer code for details on the protocol.
 ******/
import java.net.*;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.regex.PatternSyntaxException;
import java.util.Scanner;
import java.math.*;

public class MessengerClient {
    private String hostname;
    private int port;
    private Item list[];
    private boolean done = false;   // Initiate a reset upon startup
    private String clientName;   // The name for this client
    private Socket socket = null;
    private PrintWriter out = null;
    private BufferedReader in = null;
    private MessengerClientController controller;
    private BigInteger publicKey;
	private RSA rsa = new RSA(1024);

    static class Item {
	String name; int amount;
	public Item(String n, int a) { name = n; amount = a; }
    }

    /**
     * Constructor
     * @param hostname The name of the machine to connect to
     * @param port The host's port
     * @param list An array of Items to send to the server (host)
     * @param resetFlag True if the first instruction sent to server is to reset.
     * @param clientName For debugging purposes, the name of the client.
     **/
    public MessengerClient(String hostname, int port, String clientName, MessengerClientController controller) {
	this.hostname = hostname;
	this.port = port;
	this.clientName = clientName;
	this.controller = controller;
    }

    public void setController(MessengerClientController controller){
    	this.controller = controller;
    }
    
    /**
     * Start running the thread for this connection
     **/
    public void run() {
    try {
	// First get the I/O streams for communication.
	//   in -- Used to read input from client
	//   out -- Used to send output to client
    	socket = new Socket(hostname, port);
		out = new PrintWriter(socket.getOutputStream(), true);
		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		String inRL = in.readLine();
		//System.out.println("inRL: "+inRL);
		publicKey = new BigInteger(inRL);
		//System.out.println("pubkey: "+publicKey);
		//System.out.println("ecnrypted message (" + rsa.getE().toString() + ")\n" + publicKey + "\n= " + rsa.encrypt(rsa.getE().toString(), publicKey));
		out.println(rsa.encrypt(rsa.getE().toString(), publicKey));
		out.println("USERLIST");
		String line1 = in.readLine();
		controller.init(line1);
		out.println("NAME " + clientName);
		
		while (!done) {
		    String line = in.readLine();
		    processLine(line);
		}
    } catch (UnknownHostException e) {
	     printMessage("Unknown host: " + hostname);
	     printMessage("             " + e.getMessage());
	} catch (IOException e) {
		printMessage("I/O Error while communicating with Client.  Terminating connection.");
		printMessage("    Message: " + e.getMessage());
    }

	try {
	    // Clean up the streams by closing them
	    if (out != null) out.close();
	    if (in != null) in.close();
	    if (socket != null) socket.close();
	} catch (IOException e) { 
	    printMessage("Error closing the streams.");
	} 
    }


    private void processLine(String line) {
	    // Process the line
	    if (line == null || line.equals("EXIT")) {
	  //   	user.logout();
	  //   	userLeft(user);
			// done = true;   // Nothing left in input stream or EXIT command sent
			// out.println("Server disconnecting");
	    } else if(line.equals("VERSION")) {
	    	out.println("1");
	    } else if(line.startsWith("USERA ")) {
	    	String username = line.substring(6);
	    	addUser(username);
	    } else if(line.startsWith("USERL ")) {
	    	String username = line.substring(6);
	    	removeUser(username);
	    } else if (line.startsWith("MESSAGE ")) {
		// Add the item to the inventorys
			String[] splited = line.split("\\s+");
			String message = line.substring(splited[1].length()+9);
			receiveMessage(splited[1],rsa.decrypt(message));
	    } else if (line.startsWith("PM ")) {
		// Add the item to the inventorys
			String[] splited = line.split("\\s+");
			receivePM(splited[1],line.substring(splited[1].length()+4));
	    } else {
			out.println("UNRECOGNIZED COMMAND: " + line);
	    }
	}
    /**
     * Print out the message (with a little name id in front)
     * @param message The message to print out
     **/
    private void printMessage(String message) {
	System.out.println("["+clientName+"]: " + message);
    }

    public void sendPM(String name, String message){
    	out.println("PM " + name + " " + message);
    }
    
    public void sendMessage(String message) {
    	out.println("SEND " + rsa.encrypt(message,publicKey));
    }

    private void addUser(String username){
    	if(controller == null || username.equals(clientName)) return;
    	controller.addUser(username);
    }

    private void removeUser(String username){
    	controller.removeUser(username);
    }
    
    private void receivePM(String name, String message){
    	controller.PMreceived(name, message);
    }
    
    private void receiveMessage(String username, String message){
    	controller.receiveMessage(username,message);
    }

    /**
     * The main entry point.  It just processes the command line arguments
     * and starts an instance of the MessengerClient running.
     **/
    
}
