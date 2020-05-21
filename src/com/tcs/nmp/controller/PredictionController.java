package com.tcs.nmp.controller;


import java.io.IOException;
import java.security.GeneralSecurityException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.tcs.nmp.processor.intf.MessagePredictionIntf;

@RestController
public class PredictionController {
	
	private static final Logger LOGGER =  LogManager.getLogger(PredictionController.class);
	
	@Autowired
	@Qualifier("googleMessagePredictionImpl")
	private MessagePredictionIntf messagePredictionIntf;
	
	
	
	@RequestMapping(method = RequestMethod.GET,value="/predict" )
	@ResponseBody
	   public String predict(String input,String conversationId, String name) {
		LOGGER.debug("Input received for prediction : " + input);
		LOGGER.debug("Conversation Id received for prediction" + conversationId);
		LOGGER.debug("Receipent name" + name);
		String commonMessage = "Hi "+ name +", We are unable to process yor request right now.Our Call Center Team will contact you shortly";
		String predictedData=commonMessage;
		try {
			String predictedMessage = messagePredictionIntf.predict( input,conversationId,name);
			if(predictedMessage != null ){
				predictedData = predictedMessage;
			}
			LOGGER.debug("Data :" + predictedData);
		} catch (GeneralSecurityException gse) {
			predictedData = commonMessage;
		} catch (IOException ioe) {
			predictedData = commonMessage;
		} catch (JSONException je){
			predictedData = commonMessage;
		} catch (Exception e){
			predictedData = commonMessage;
		}
		
	      return predictedData;
	   }

}
