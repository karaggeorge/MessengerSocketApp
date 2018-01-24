import javax.swing.DefaultListModel;
import java.util.*;


public class MessengerClientController {
	
	private MessengerClientFrame frame;
	private MessengerClient client;
	private ArrayList< PrivateMessageFrame > PMs = new ArrayList<PrivateMessageFrame> ();
	
	public MessengerClientController(){
		
	}
	
	public void setFrame(MessengerClientFrame frame){ 
		this.frame = frame;
	}
	
	public void setClient(MessengerClient client){ 
		this.client = client;
	}
	
	
	public void addUser(String username){
		frame.getArea().append("User " + username + " has joined\n");
		frame.scroll();
		frame.addPerson(username);
	}
	
	public void removeUser(String username){
		frame.getArea().append("User " + username + " has left\n");
		frame.scroll();
		frame.removePerson(username);
	}

	public void init(String names){
		String[] splited = names.split("\\s+");
		for(int i = 0;i < splited.length;i++){
			frame.addPerson(splited[i]);
		}
	}
	
	public void startPM(String name){
		PMs.add(new PrivateMessageFrame(name,this));
	}
	
	public void removePM(String name){
		for(int i = 0;i < PMs.size();i++){
			if(PMs.get(i).getName().equals(name)){
				PMs.remove(i);
			}
		}
	}
	
	public void sendPM(String name, String message){
		client.sendPM(name,message);
	}
	
	public void PMreceived(String name, String message){
		for(int i = 0;i < PMs.size();i++){
			if(PMs.get(i).getName().equals(name)){
				PMs.get(i).messageReceived(message);
				return;
			}
		}
		startPM(name);
		PMreceived(name,message);
	}
	
	public void receiveMessage(String username, String message){
		frame.getArea().append(username + ": " + message + "\n");
		frame.scroll();
	}
	
	public void sendMessage(String message){
		client.sendMessage(message);
	}
}
