package com.tcs.nmp.handler;

import java.io.IOException;

import org.json.JSONException;

import com.google.api.services.prediction.model.Output;

public interface DataHandlerIntf {
	
	String handleFavourableData(String input, Output output, String convId, String name, boolean favInd) throws JSONException, IOException;
	
	String handleNonFavourableData(String input, Output output, String convId, String name) throws JSONException, IOException;
	
	

}
