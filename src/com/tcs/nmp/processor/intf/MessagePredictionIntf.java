package com.tcs.nmp.processor.intf;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.json.JSONException;

public interface MessagePredictionIntf {
	
	public String predict(String input,String conversationId, String name) throws IOException, JSONException, GeneralSecurityException;
	
	

}
