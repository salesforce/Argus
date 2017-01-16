package com.salesforce.dva.warden.dto;
import java.nio.channels.SocketChannel;

/**
 * Change request for socket registeration
 *
 * @author  Ruofan Zhang(rzhang@salesforce.com)
 */
public class ChangeRequest {
	public static final int REGISTER = 1;
	public static final int CHANGEOPS = 2;

	public SocketChannel socket;
	public int type;
	public int ops;
	
	public ChangeRequest(SocketChannel socket, int type, int ops){
		this.socket = socket;
		this.type = type;
		this.ops = ops;
	}
}
