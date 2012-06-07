package com.extl.vnc.websockets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.NotYetConnectedException;

import org.java_websocket.WebSocketClient;
import org.java_websocket.util.Base64;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

public class WebSockOutputStream extends OutputStream{

	boolean markStremClosed = false;
	
	private WebSocketClient client;
	private ByteArrayOutputStream bio ; 
	BASE64Encoder encode = new BASE64Encoder();
	
	WebSockOutputStream(WebSocketClient client) {
		this.client = client;
		bio = new ByteArrayOutputStream();
	}
	
	@Override
	public void write(int b) throws IOException {
		if(markStremClosed) {
			throw new IOException("Connection Closed to the remote host");
		}
		//bio = null;
		bio.write(b);
		//throw new IOException("Not calling flush");
		//System.out.println("Write called 1");
		//flush();
	}

	@Override
	public void write(byte[] b) throws IOException {
		if(markStremClosed) {
			throw new IOException("Connection Closed to the remote host");
		}
		bio = null;
		bio.write(b);
		//throw new IOException("Not calling flush");
		//System.out.println("Write called 2");
		//flush();
	}
	
	@Override
	public void write(byte[] arg0, int arg1, int arg2) throws IOException {
		if(markStremClosed) {
			throw new IOException("Connection Closed to the remote host");
		}
		//bio = null;
		bio.write(arg0, arg1, arg2);	
		//System.out.println("Write called 3");
		//flush();
	}
	
	@Override
	public synchronized void flush() throws IOException {
		
		if(markStremClosed) {
			throw new IOException("Connection Closed to the remote host");
		}
		byte b[] = null;
		synchronized (bio) {
			b = bio.toByteArray();
			bio.reset();
		}
			if(b!=null && b.length>0) {
				
				String s = encode.encode(b).replaceAll("\r", "").replaceAll("\n", "");
				try {
					client.send(s);
				} catch (NotYetConnectedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					throw new IOException(e.getMessage());
				}
			}
		
		//System.out.println("Flush called");
	}

	@Override
	public void close() throws IOException {
		markStremClosed = true;
	}
	
}
