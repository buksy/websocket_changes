package com.extl.vnc.websockets;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.java_websocket.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.framing.Framedata.Opcode;
import org.java_websocket.framing.FramedataImpl1;
import org.java_websocket.handshake.ServerHandshake;

import com.extl.vnc.socket.ByteBufferInputStream;

import sun.misc.BASE64Decoder;



public class RFBWebsocketConnection extends WebSocketClient{

	private static Map<String, String >heards ; 
	
	private InputStream inputStream; 
	private OutputStream outputStream;

	BASE64Decoder base64 = new BASE64Decoder();
	
	private int connection = 0; // 0 pending 1 = success ; 2 = falied;
	
	static{
		heards = new HashMap<String, String>();
		heards.put("Sec-WebSocket-Protocol", "base64");
		//heards.put("Cookie", "PHPSESSID="+System.currentTimeMillis()+"; CPSID0x749862cfcd964d4381a8eb8aa8e1a5e1=bmm3li45s8qn47uvhej45u7vg6; __utma=186504489.1917228108.1336645072.1337702365.1337771573.5; __utmb=186504489.1.10.1337771573; __utmc=186504489; __utmz=186504489.1336645072.1.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none)");
	}
	
	
	public RFBWebsocketConnection(URI uri) {
		super(uri, new Draft_17(),heards);
	}
	
	public void connect(){
		super.connect();
		while(connection == 0) {
			synchronized (Thread.currentThread()) {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}	
		}
	}
	

	@Override
	public void onOpen(ServerHandshake handshakedata) {
		
		System.out.println("Success fully connected to the remote vnc server ");
		try {
			inputStream = new ByteBufferInputStream();//new WebSocketInputStream();
			outputStream = new WebSockOutputStream(this);
			connection = 1;
			
//			new Thread() {
//				public void run() {
//					while(true) {
//						
//						try {
//							Thread.currentThread().sleep(5000);
//							if(getBufferedAmount()<=0)
//								getConnection().sendFrame(new FramedataImpl1(Opcode.PING));
//						} catch (InterruptedException e) {
//							// TODO Auto-generated catch block
//							e.printStackTrace();
//						}
//					}
//				}
//			}.start();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			connection = 2;
		}
		
		
		
	}

	@Override
	public void onMessage(String message) {
		try {
			//System.out.println(message);
			
			((ByteBufferInputStream)inputStream).addToReadQueue(ByteBuffer.wrap(base64.decodeBuffer(message)));
		} catch (IOException e) {
			this.close();
			try {
				inputStream.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			e.printStackTrace();
		}
		
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		// TODO Auto-generated method stub
		System.out.println("close "+code+" "+reason);
		if(code==1005) {
			return;
		}
		try {
			if(inputStream !=null )
				inputStream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			if(outputStream !=null )
				outputStream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		connection = 2;
		System.exit(-1);
	}

	@Override
	public void onError(Exception ex) {
		System.out.println(ex.getMessage());
		System.exit(-1);
	}

	public InputStream getInputStream() throws IOException{
		if(connection == 2)
			throw new IOException("Connection not extablished");
		
		return inputStream;
	}

	public OutputStream getOutputStream() throws IOException{
		if(connection == 2)
			throw new IOException("Connection not extablished");
		
		return outputStream;
	}
	
	
	public boolean isConnected(){
		return (connection==1);
	}
}
