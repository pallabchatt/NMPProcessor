package com.tcs.nmp.processor.impl;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.prediction.Prediction;
import com.google.api.services.prediction.PredictionScopes;
import com.google.api.services.prediction.model.Input;
import com.google.api.services.prediction.model.Input.InputInput;
import com.google.api.services.prediction.model.Output;
import com.google.api.services.prediction.model.Output.OutputMulti;
import com.google.api.services.storage.StorageScopes;
import com.tcs.nmp.handler.DataHandlerIntf;
import com.tcs.nmp.processor.intf.MessagePredictionIntf;

import edu.emory.mathcs.backport.java.util.Collections;

@Component
public class GoogleMessagePredictionImpl implements MessagePredictionIntf {
	private static final Logger LOGGER =  LogManager.getLogger(GoogleMessagePredictionImpl.class);

	private static final String APPLICATION_NAME = "TestGoogleApi";

	static final String MODEL_ID = "NMPTrainer01122016";

	static final String PROJECT_ID = "symmetric-ray-147418";
	static final String SERVICE_ACCT_EMAIL = "testgoogleapi@symmetric-ray-147418.iam.gserviceaccount.com";
	static final String SERVICE_ACCT_KEYFILE = "/com/tcs/nmp/processor/keys/TestGoogleApi-5cddc8f41a51.p12";

	/** Global instance of the HTTP transport. */
	private static HttpTransport httpTransport;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = JacksonFactory
			.getDefaultInstance();
	
	@Autowired
	private DataHandlerIntf predictionResulthandler;
	
	

	@Override
	public String predict(String inputValue,String conversationId, String name) throws 
			IOException, JSONException, GeneralSecurityException {
		String outputValue = "";
		httpTransport = GoogleNetHttpTransport.newTrustedTransport();

		Prediction prediction = getPrediction();

		Input input = new Input();
		InputInput dataInput = new InputInput();
		dataInput.setCsvInstance(Collections.singletonList(inputValue));
		input.setInput(dataInput);
		Output output = prediction.trainedmodels()
				.predict(PROJECT_ID, MODEL_ID, input).execute();
		LOGGER.debug("Text: " + inputValue);
		LOGGER.debug("Output label :" +output.getOutputLabel()); 
		List<OutputMulti> outputList = output.getOutputMulti();
		boolean predictionFound = false;
		if (null != outputList && outputList.size() > 0) {
			for (OutputMulti om : outputList) {
				LOGGER.debug("Label : " +om.getLabel()+", Score :" + om.getScore());
				if (Double.valueOf(om.getScore()) > .75) {
					LOGGER.debug("Predicted language: " + om.getLabel());
					predictionFound = true;
					break;
				}
			}
			if (!predictionFound) {
				
				outputValue = predictionResulthandler.handleNonFavourableData(inputValue, output, conversationId, name);
			}else{
				outputValue = predictionResulthandler.handleFavourableData(inputValue, output, conversationId, name, true);
			}
		}
		return outputValue;
	}

	public static Prediction getPrediction() throws GeneralSecurityException,
			IOException {
		// authorization
		GoogleCredential credential = authorize();
		Prediction prediction = new Prediction.Builder(httpTransport,
				JSON_FACTORY, setHttpTimeout(credential)).setApplicationName(
				APPLICATION_NAME).build();
		return prediction;
	}

	/**
	 * Authorizes the installed application to access user's protected data.
	 * 
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	private static GoogleCredential authorize()
			throws GeneralSecurityException, IOException {
		return new GoogleCredential.Builder()
				.setTransport(httpTransport)
				.setJsonFactory(JSON_FACTORY)
				.setServiceAccountId(SERVICE_ACCT_EMAIL)
				.setServiceAccountPrivateKeyFromP12File(
						new File(GoogleMessagePredictionImpl.class.getResource(
								SERVICE_ACCT_KEYFILE).getFile()))
				.setServiceAccountScopes(
						Arrays.asList(PredictionScopes.PREDICTION,
								StorageScopes.DEVSTORAGE_READ_ONLY)).build();
	}

	private static HttpRequestInitializer setHttpTimeout(
			final HttpRequestInitializer requestInitializer) {
		return new HttpRequestInitializer() {
			@Override
			public void initialize(HttpRequest httpRequest) throws IOException {
				requestInitializer.initialize(httpRequest);
				httpRequest.setConnectTimeout(3 * 60000); // 3 minutes connect
															// timeout
				httpRequest.setReadTimeout(3 * 60000); // 3 minutes read timeout
			}
		};
	}
}
