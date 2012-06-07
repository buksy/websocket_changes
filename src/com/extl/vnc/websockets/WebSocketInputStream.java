package com.extl.vnc.websockets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class WebSocketInputStream extends InputStream{

	boolean readOk = true;
	
	private PipedInputStream pi ; 
	private PipedOutputStream po;
	
	WebSocketInputStream() throws IOException {
		po = new PipedOutputStream();
		pi = new PipedInputStream(po);
	}
	
	@Override
	public int read() throws IOException {
		
		return pi.read();
		
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		return pi.read(b);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		// TODO Auto-generated method stub
		return pi.read(b, off, len);
	}

	@Override
	public int available() throws IOException {
		return pi.available();
	}

	protected void setToRead(byte []b) throws IOException {
		po.write(b);
	}
	

	@Override
	public void close() throws IOException {
		pi.close();
		po.close();
	}
	
}
