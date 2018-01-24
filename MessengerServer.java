/******
 * MessengerServer
 * Author: Christian Duncan
 *
 * This server tracks inventory sent to it from various
 * clients.  A client connects to the server, establishes a connection
 * which is handled on a separate thread, and sends it commands 
 * processed by the server.
 * The commands (the protocol) supported by the server are:
 *    RESET - Set inventory to empty
 *    ADD X - Add item X to inventory (or increment tally if already there).
 *    REPORT - Return to the client the current inventory list and tallies...
 *    EXIT - Terminate connection
 * Internally, the server stores the inventory in a HashMap.
 * WARNING: THIS CODE IS NOT DESIGNED TO BE THREAD-SAFE!  
 *          IT NEEDS TO BE FIXED TO HANDLE RACE CONDITIONS
 *          AND OTHER THREADING ISSUES.
 ******/
import java.net.ServerSocket;
import java.math.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.*;

public class MessengerServer {
    public static final int DEFAULT_PORT = 1518;
    public static final String END_LIST = "---";

    private RSA rsa = new RSA(1024);
    public int port;      // The port to listen to for this server
    public boolean done;  // Is server still running?
    private PrintWriter log;

    static class Message {
    	private String sender;
    	private String message;
    	Message(String sender, String message) { this.sender = sender; this.message = message;}
    	public String getSender(){ return sender; }
    	public String getMessage(){ return message; }
    }

    // A simple class to store a simple tally (an integer)
    private static class Person {
		private String username;
		private String id;
		private ArrayList<Message> outbox = new ArrayList<Message> ();
		private PrintWriter out;
		private BufferedReader in;
		private BigInteger publicKey;
		private boolean logged = false;
		Person(String id) { this.id = id; username = "Anon"; }
		Person(String id, String name) { this.id = id; username = name; }
		public void setName(String name) { username = name;}
		public void setBuffers(PrintWriter out, BufferedReader in){ this.out = out; this.in = in;}
		public void setPublicKey(BigInteger k){
			publicKey = k;
		}
		public BigInteger getKey(){
			return publicKey;
		}
		public synchronized void sendMessages(){
			for(int i = 0;i < outbox.size();i++){
				Message m = outbox.get(i);
				out.println(m.getMessage());	
				//out.println("MESSAGE " + m.getSender() + " " + m.getMessage());
			}
			outbox.clear();
		}
		public void addMessage(String message, String sender){
			Message m = new Message(sender, message);
			outbox.add(m);
		}
		public String getUsername(){return username;}
		public void setUsername(String name) { username = name; }
		public void login(){ logged=true; }
		public void logout() { logged=false; }
		public boolean isLogged() {return logged; }
    }

    ArrayList<Person> userList = new ArrayList<Person> ();

    // A new thread class to handle connections with a client
    class Connection extends Thread {
		Socket socket;
		PrintWriter out;
		BufferedReader in;
		boolean done;
		String name;   
		Person user;


		/**
		 * The constructor
		 * @param socket The socket attached to this connection
		 * @param name The "name" of this connection - for debugging purposes
		 **/
		public Connection(Socket socket, String name) { 
		    this.socket = socket; done = false; this.name = name; 
		    user = new Person(name);
		} 	

		/**
		 * Start running the thread for this connection
		 **/
		public void run() {
		    try {
			// First get the I/O streams for communication.
			//   in -- Used to read input from client
			//   out -- Used to send output to client
				out = new PrintWriter(socket.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				user.setBuffers(out,in);
				//System.out.println(rsa.getE());
				out.println(rsa.getE().toString());
				String inRL = in.readLine();
				//System.out.println("The irl = " + inRL);
				//System.out.println("The d irl = " + rsa.decrypt(inRL));
				//System.out.println("The d m = " + rsa.decrypt(rsa.encrypt("yo what's up",rsa.getE())));
				//System.out.flush();
				BigInteger newPK = new BigInteger(rsa.decrypt(inRL));
				//System.out.println(newPK);
				user.setPublicKey(newPK);
				while (!done) {
				    String line = in.readLine();
				    processLine(line);
				}
		    } catch (IOException e) {
				printMessage("I/O Error while communicating with Client.  Terminating connection.");
				printMessage("    Message: " + e.getMessage());
		    }
		    
		    // Close the socket
		    try {
				printMessage("CLIENT is closing down.");
				if (in != null) in.close();
				if (out != null) out.close();
				if (socket != null) socket.close();
		    } catch (IOException e) {
				printMessage("Error trying to close socket. " + e.getMessage());
		    }
		}
		
		/**
		 * Process one line that has been sent through this connection
		 * @param line The line to process
		 **/
		private void processLine(String line) {
		    // Process the line
		    if (line == null || line.equals("EXIT")) {
		    	user.logout();
		    	userLeft(user);
				done = true;   // Nothing left in input stream or EXIT command sent
				out.println("Server disconnecting");
		    } else if(line.equals("VERSION")) {
		    	out.println("3");
		    } else if(line.equals("USERLIST")) {
		    	String reply = " ";
		    	for(int i = 0;i < userList.size();i++){
		    		Person user = userList.get(i);
		    		reply += user.getUsername();
		    		reply += " ";
		    	}
		    	out.println(reply);
		    } else if(line.startsWith("NAME ")) {
		    	String oldUsername = user.getUsername();
		    	user.setUsername(line.substring(5));
		    	if(!user.isLogged()){
		    		user.login();
		    		userAdded(user);
		    	} else {
		    		sendToAll("NAMEC " + oldUsername + " " + user.getUsername());
		    	}
		    } else if (line.startsWith("SEND ")) {
				String message = line.substring(5);
				sendMessage(rsa.decrypt(message),user);
		    } else if (line.startsWith("PM ")) {
		    	String[] splited = line.split("\\s+");
				sendPM(user,splited[1],line.substring(splited[1].length()+4));
		    } else {
				out.println("UNRECOGNIZED COMMAND: " + line);
		    }
		}
		
		private void sendPM(Person sender, String name, String message){
			String username = sender.getUsername();
			System.out.println(username + " " + name + " " + message);
	    	String m = "PM " + username + " " + message;
	    	log.println("((( PM from" + username + " to " + name + " :: " + message + " )))\n");
	    	log.flush();
	    	
	    	for(int i = 0;i < userList.size();i++){
	    		Person user = userList.get(i);
	    		if(user.getUsername().equals(name)){
	    			user.addMessage(m,user.getUsername());
	    			user.sendMessages();	
	    			break;
	    		}
	    	}
		}

		/**
		 * Print out the message (with a little name id in front)
		 * @param message The message to print out
		 **/
		private void printMessage(String message) {
		    System.out.println("["+ name + "]: " + message);
		}
	    }

	    // The set of client connections. Not really needed here but useful sometimes.
	    public HashSet<Connection> connection;

	    /**
	     * Basic Constructor 
	     * @param The port to listen to
	     **/
	    public MessengerServer(int port) {
			this.port = port;
			this.done = false;
			this.connection = new HashSet<Connection>();
			//System.out.println(rsa.getE());
			//System.out.println(rsa.getN());
			GregorianCalendar cal = new GregorianCalendar();
			String filename = (cal.get(Calendar.MONTH)+1) + "." + cal.get(Calendar.DAY_OF_MONTH) + "." + cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.HOUR_OF_DAY) + ":" + cal.get(Calendar.MINUTE) + ".log";
			try {
				//log = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename),"utf-8"));
				log = new PrintWriter(filename,"utf-8");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			  // report
			
		//this.inventory = new HashMap<String, Tally>();
	    }

	    /**
	     * Create a new thread associated with the client connection.
	     * And store the thread (for reference sake).
	     * @param Socket The client socket to associate with this connection thread.
	     **/
	    public void addConnection(Socket clientSocket) {
		String name = clientSocket.getInetAddress().toString();
		System.out.println("Messenger Server: Connecting to client: " + name);
		Connection c = new Connection(clientSocket, name);
		connection.add(c);
		c.start();    // Start the thread.
	    }

	    /**
	     * Reset the inventory.
	     *   Just creates a new empty hash map.
	     **/
	    

	    public void userAdded(Person user){
	    	userList.add(user);
	    	sendToAll("USERA " + user.getUsername());
	    	log.println("--> User " + user.getUsername() + " has connected\n");
	    	log.flush();
	    }

	    public void userLeft(Person user){
	    	userList.remove(user);
	    	sendToAll("USERL " + user.getUsername());
	    	log.println("--> User " + user.getUsername() + " has left\n");
	    	log.flush();
	    }

	    public void sendMessage(String message, Person sender){
	    	String username = sender.getUsername();
	    	String m;
	    	log.println(username + " :: " + message + "\n");
	    	log.flush();
	    	//System.out.println("MESSAGE RECEIVED: "+ message);
	    	for(int i = 0;i < userList.size();i++){
	    		Person user = userList.get(i);
	    		//if(!user.getUsername().equals(username)){
	    			//System.out.println(user.getUsername() + " : " + user.getKey());
	    			m = "MESSAGE " + username + " " + rsa.encrypt(message, user.getKey());
	    			user.addMessage(m,user.getUsername());
	    			user.sendMessages();	
	    		//}
	    	}
	    }

	    public void sendToAll(String message){

	    	for(int i = 0;i < userList.size();i++){
	    		Person user = userList.get(i);
	    		user.addMessage(message,"server");
	    		user.sendMessages();
	    	}
	    }

	    

	    /**
	     * Run the main server... just listen for and create connections
	     **/
	    public void run() {
		System.out.println("Server is starting up...");
		try {
		    // Create a server socket bound to the given port
		    ServerSocket serverSocket = new ServerSocket(port);
		    
		    while (!done) {
			// Wait for a client request, establish new thread, and repeat
			Socket clientSocket = serverSocket.accept();
			addConnection(clientSocket);
		    }
		} catch (Exception e) {
		    System.err.println("ABORTING: An error occurred while creating server socket. " + 
				       e.getMessage());
		    System.exit(1);
		}
    }

    /**
     * The main entry point.  It just processes the command line argument
     * and starts an instance of the MessengerServer running.
     **/
    public static void main(String[] args) {
	int port = DEFAULT_PORT;
	
	// Set the port if specified
	if (args.length > 0) {
	    try {
		port = Integer.parseInt(args[0]);
	    } catch (NumberFormatException e) {
		System.err.println("Usage: java MessengerServer [PORT]");
		System.err.println("       PORT must be an integer.");
		System.exit(1);
	    }
	}
	MessengerServer s = new MessengerServer(port);
	s.run();
    }
}
