import java.net.*;
import java.nio.file.Paths;
import java.util.Queue;
import java.util.Scanner;

/**
 * This class represents the client1
 * @author Group 8
 *
 */
public class Client {
	
   private TFTPSocket tftpSocket;
   
   private FileManager fileManager;
   private ErrorHandler errorHandler;
   
   private InetAddress serverAddress;
   private int serverPort;

   /**
    * Constructor
    */
   public Client()
   {
	   tftpSocket = new TFTPSocket();
	   errorHandler =  new  ErrorHandler(tftpSocket);
	   
	   // class for reading and writing files to hard drive 
	   fileManager = new FileManager();
	   
	   try {
		   serverAddress = InetAddress.getLocalHost();
	   } catch (UnknownHostException e) {
		   System.err.println(Globals.getErrorMessage("Client", "cannot get localhost address"));
           e.printStackTrace();
           System.exit(-1);
	   }
	   
	   serverPort = NetworkConfig.PROXY_PORT;
   }
   
   /**
    * Makes a read or write request and returns the response packet
    * 
    * @param packetType   packet type
    * @param fileName     name of the file that is requested to be read or written (in bytes)
    * @param mode         mode (in bytes)
    * @param ipAddress    server IP address
    * @param port         server port
    * 
    * @Return Datagram packet received from the server after making a RRQ or WRQ request
    */
   private void makeReadWriteRequest(TFTPPacketType packetType, String fileName, String mode, InetAddress ipAddress, int port) {

	   TFTPPacket requestPacket = null;
	   
	   if (packetType == TFTPPacketType.RRQ) {
		   // get read request packet
		   requestPacket = TFTPPacketBuilder.getRRQWRQDatagramPacket(TFTPPacketType.RRQ, fileName, mode, ipAddress, port);
	   }
	   else {
		   // get write request packet
		   requestPacket = TFTPPacketBuilder.getRRQWRQDatagramPacket(TFTPPacketType.WRQ, fileName, mode, ipAddress, port);
	   }
	   
	   // send request
	   tftpSocket.send(requestPacket);
   }
   
   private void sendACKPacketToServer(short blockNumber) {
	   ACKPacket ackPacket = TFTPPacketBuilder.getACKDatagram(blockNumber, serverAddress, serverPort);
	   
	   System.out.println(Globals.getVerboseMessage("Client", 
				String.format("sending ACK packet %d to server %s:%d", blockNumber, serverAddress, serverPort)));
	   
	   // sends acknowledgement to client
	   tftpSocket.send(ackPacket);
   }
   
	private void sendDATAPacketToServer(DATAPacket dataPacket) {		
		System.out.println(Globals.getVerboseMessage("Client", 
				String.format("sending DATA packet %d to server %s:%d", dataPacket.getBlockNumber(), dataPacket.getRemoteAddress(), dataPacket.getRemotePort())));
		
		// send DATA datagram packet
		tftpSocket.send(dataPacket);
	}
   
   private DATAPacket receiveDATAPacketFromServer(short expectedBlockNumber) {
	   DATAPacket dataPacket = null;
		
		while (dataPacket == null) {
			TFTPPacket receivePacket = tftpSocket.receive();
				
			if (!receivePacket.getRemoteAddress().equals(serverAddress) ||
					receivePacket.getRemotePort() != serverPort) {
				String errorMessage = String.format("Received packet from unknown source. Expected: %s:%d, Received: %s:%d", 
						serverAddress, serverPort, receivePacket.getRemoteAddress(), receivePacket.getRemotePort());
				
				System.err.println(Globals.getErrorMessage("Client", errorMessage));	
				errorHandler.sendUnknownTrasnferIDErrorPacket(errorMessage, serverAddress, serverPort);
				return null;
			}
			
			
			if (receivePacket.getPacketType() == TFTPPacketType.DATA) {
				try {
					dataPacket = new DATAPacket(receivePacket);
				} catch(TFTPPacketParsingError e) {
					String errorMessage = String.format("cannot parse DATA packet %d", expectedBlockNumber);
					System.err.println(Globals.getErrorMessage("Client", errorMessage));
					errorHandler.sendIllegalOperationErrorPacket(errorMessage, serverAddress, serverPort);
					continue;
				}
				
				if (dataPacket.getBlockNumber() != expectedBlockNumber) {
					String errorMessage = String.format("unexpected DATA packet block number received. Expected: %d, Received: %d", expectedBlockNumber, dataPacket.getBlockNumber());
					System.err.println(Globals.getErrorMessage("Client", errorMessage));
					errorHandler.sendIllegalOperationErrorPacket(errorMessage, serverAddress, serverPort);
					dataPacket = null;
					continue;
				}
				
				System.out.println(Globals.getVerboseMessage("Client", 
						String.format("received DATA packet %d from server %s%d", dataPacket.getBlockNumber(), serverAddress, serverPort)));
			}
			else if (receivePacket.getPacketType() == TFTPPacketType.ERROR) {
				ERRORPacket errorPacket = null;
				
				try {
					errorPacket = new ERRORPacket(receivePacket);
				} catch (TFTPPacketParsingError e) {
					String errorMessage = "cannot parse ERROR packet";
					System.err.println(Globals.getErrorMessage("Client", errorMessage));
					errorHandler.sendIllegalOperationErrorPacket(errorMessage, serverAddress, serverPort);
					dataPacket = null;
					continue;
				}
				
				System.out.println(Globals.getVerboseMessage("Client", 
						String.format("received ERROR packet from server %s%d, errorCode: %d, errorMessage: %s", serverAddress, 
								serverPort, errorPacket.getErrorCode(), errorPacket.getErrorMessage())));
				
				if (errorPacket.getErrorCode() == ERRORPacket.ILLEGAL_TFTP_OPERATION) {
					sendACKPacketToServer((short) (expectedBlockNumber - 1));
					dataPacket = null;
					continue;
				}
				else {
					return null;
				}
			}
			else {
				String errorMessage = "invalid DATA sent";
				System.err.println(Globals.getErrorMessage("Client", errorMessage));
				errorHandler.sendIllegalOperationErrorPacket(errorMessage, serverAddress, serverPort);
				continue;
			}
		}
		
		return dataPacket;
   }	
  	
   	private ACKPacket receiveACKPacketFromServer(short expectedBlockNumber) {	
		ACKPacket ackPacket = null;
		
		while (ackPacket == null) {
			TFTPPacket receivePacket = tftpSocket.receive();
			
			if (!receivePacket.getRemoteAddress().equals(serverAddress) ||
					receivePacket.getRemotePort() != serverPort) {
				String errorMessage = String.format("Received packet from unknown source. Expected: %s:%d, Received: %s:%d", 
						serverAddress, serverPort, receivePacket.getRemoteAddress(), receivePacket.getRemotePort());
				
				System.err.println(Globals.getErrorMessage("Client", errorMessage));	
				errorHandler.sendUnknownTrasnferIDErrorPacket(errorMessage, serverAddress, serverPort);
				return null;
			}
		
			if (receivePacket.getPacketType() == TFTPPacketType.ACK) {
				try {
					ackPacket = new ACKPacket(receivePacket);
					
					if (ackPacket.getBlockNumber() != expectedBlockNumber) {
						String errorMessage = String.format("unexpected ACK packet block number received. Expected: %d, Received: %d", expectedBlockNumber, ackPacket.getBlockNumber());
						System.err.println(Globals.getErrorMessage("Client", errorMessage));
						errorHandler.sendIllegalOperationErrorPacket(errorMessage, serverAddress, serverPort);
						ackPacket = null;
						continue;
					}
					
				} catch(TFTPPacketParsingError e) {
					String errorMessage = String.format("cannot parse ACK packet %d", expectedBlockNumber);
					System.err.println(Globals.getErrorMessage("Client", errorMessage));
					errorHandler.sendIllegalOperationErrorPacket(errorMessage, serverAddress, serverPort);
					continue;
				}
				
				System.out.println(Globals.getVerboseMessage("Client", 
						String.format("received ACK packet %d from server %s%d", ackPacket.getBlockNumber(), serverAddress, serverPort)));
			}
			else if (receivePacket.getPacketType() == TFTPPacketType.ERROR) {
				ERRORPacket errorPacket = null;
				
				try {
					errorPacket = new ERRORPacket(receivePacket);
				} catch (TFTPPacketParsingError e) {
					String errorMessage = "cannot parse ERROR packet";
					System.err.println(Globals.getErrorMessage("Client", errorMessage));
					errorHandler.sendIllegalOperationErrorPacket(errorMessage, serverAddress, serverPort);
				}
				
				System.out.println(Globals.getVerboseMessage("Client", 
						String.format("received ERROR packet from server %s%d, errorCode: %d, errorMessage: %s", serverAddress, 
								serverPort, errorPacket.getErrorCode(), errorPacket.getErrorMessage())));
				
				if (errorPacket.getErrorCode() == ERRORPacket.ILLEGAL_TFTP_OPERATION) {
					continue;
				}
				else {
					return null;
				}
			}
			else {
				String errorMessage = "invalid TFTP packet";
				System.err.println(Globals.getErrorMessage("Client", errorMessage));
				errorHandler.sendIllegalOperationErrorPacket(errorMessage, serverAddress, serverPort);
				receivePacket = null;
				continue;
			}
		}
		
		return ackPacket;
	}
   

   /**
    * Handle DATA packets received from server with file data
    * 
    * @param filePath  path of the file that the client requests
    * @param mode      mode of request
    */
   public void readFile(String filePath, String mode) {
        DATAPacket dataPacket;
        
        // get file name from file path
        String fileName = Paths.get(filePath).getFileName().toString();
        
        // make a read request and wait for response
        try {
            makeReadWriteRequest(TFTPPacketType.RRQ, 
                    fileName, mode, InetAddress.getLocalHost(), NetworkConfig.PROXY_PORT);
        } catch (UnknownHostException e) {
            System.err.println(Globals.getErrorMessage("Client", "cannot get localhost address"));
            e.printStackTrace();
            System.exit(-1);
        }

        
	    short nextBlockNumber = 1; // expect to receive DATA with valid block number
	   
	   	// receive all data packets from server that wants to transfer a file.
		// once the data length is less than 512 bytes then stop listening for
		// data packets from the server
        int fileDataLen = NetworkConfig.DATAGRAM_PACKET_MAX_LEN;
        while (fileDataLen == NetworkConfig.DATAGRAM_PACKET_MAX_LEN) {
            // receive datagram packet
        	dataPacket = receiveDATAPacketFromServer(nextBlockNumber);
        	if (dataPacket == null) {
        		return;
        	}
        	
	        // gets the data bytes from the DATA packet and converts it into a string
        	byte[] fileData = dataPacket.getDataBytes();
	        String fileDataStr = ByteConversions.bytesToString(fileData);
	        
	        System.out.println(Globals.getVerboseMessage("Client", String.format("received file data: %s", fileDataStr)));
	    
	        // save the length of the received packet
	        fileDataLen = dataPacket.getPacketLength();
	        
	        // send ACK packet
	        sendACKPacketToServer(nextBlockNumber);
	        nextBlockNumber++;
        }
    }
    
    /**
        * Handle sending DATA packets to server 
        * 
        * @param filePath path of the file client wants to write to
        * @param mode     mode of the request
        */
    public void writeFile(String filePath, String mode) {
        // get file name from file path
        String fileName = Paths.get(filePath).getFileName().toString();
        
        // make a write request and wait for response
        try {
                makeReadWriteRequest(TFTPPacketType.WRQ, 
                    fileName, mode, InetAddress.getLocalHost(), NetworkConfig.PROXY_PORT);
        } catch (UnknownHostException e) {
            System.err.println(Globals.getErrorMessage("Client", "cannot get localhost address"));
            e.printStackTrace();
            System.exit(-1);
        }

        ACKPacket ackPacket = receiveACKPacketFromServer((short) 0);
        if (ackPacket == null) {
        	return;
        }
        
        if (ackPacket.getBlockNumber() == 0) {

	        // reads a file on client side to create on the server side
        	FileManager.FileManagerResult res = fileManager.readFile(filePath);
    		byte[] fileData = null;
    		
    		if (!res.error) {
    			fileData = res.fileBytes;
    		}
    		else {
    			// access violation error will send an error packet with error code 2 and the connection
    			if (res.accessViolation) 
    				errorHandler.sendAccessViolationErrorPacket(String.format("read access denied to file: %s", fileName), serverAddress, serverPort);
    				
    			return;
    		}
	        
	        // create list of DATA datagram packets that contain up to 512 bytes of file data
	        Queue<DATAPacket> dataPacketStack = TFTPPacketBuilder.getStackOfDATADatagramPackets(fileData, serverAddress, serverPort);
	
	        while (!dataPacketStack.isEmpty()) {
				// send each datagram packet in order and wait for acknowledgement packet from the client
				DATAPacket dataPacket = dataPacketStack.peek();
				
				sendDATAPacketToServer(dataPacket);
				
				ackPacket = receiveACKPacketFromServer(dataPacket.getBlockNumber());
				if (ackPacket == null) {
					return;
				}
				
				dataPacketStack.poll();
	        }
        }
    }
    
    /**
        * Closes the datagram socket when the connection is finished
        */
    public void shutdown() {
        tftpSocket.close();
    }

    public static void main(String args[])
    {
        Client c = new Client();
        
        Scanner sc = new Scanner(System.in);
        
        int userInput = 0;
        do
        {
            System.out.println("\nSYSC 3033 Client");
            System.out.println("1. Write file to Server");
            System.out.println("2. Read file from Server");
            System.out.println("3. Close Client");
            System.out.print("Enter choice (1-3): ");
            userInput = sc.nextInt();
            sc.nextLine();

            if (userInput == 1)
            {
                System.out.print("Enter path to file to write to Server: ");
                String filePath = sc.nextLine();
            
                c.writeFile(filePath, "asciinet");
            }
            else if (userInput == 2)
            {
                System.out.print("Enter the file name to read from Server: ");
                String fileName = sc.nextLine();
                
                c.readFile(fileName, "asciinet");
            }
            else if (userInput == 3)
            {
                c.shutdown();
            }
            else
            {
                System.out.println("Wrong input number!\nEnter integer number 1-3!");
            }
        }
        while (userInput != 3);
        
        sc.close();
    }
}
