package com.tcs.nmp.processor.intf;

import java.io.IOException;

import org.json.JSONException;

import com.google.api.services.language.v1beta1.model.Sentiment;
import com.tcs.nmp.dto.RequestResponseDTO;

public interface LanguageProcessorIntf {
	/**
	 * 
	 * @param requestDTO
	 * @return 
	 * @throws JSONException 
	 * @throws Exception
	 */
	public RequestResponseDTO process(RequestResponseDTO requestDTO) throws IOException, JSONException;
	
	
	 public Sentiment analyzeSentiment(String text) throws IOException ;

}
