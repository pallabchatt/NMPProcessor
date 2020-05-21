package com.tcs.nmp.processor.intf;

import java.io.IOException;

import org.json.JSONException;

import com.tcs.nmp.dto.RequestResponseDTO;

public interface EmailProcessorIntf {
	/**
	 * 
	 * @param requestDTO
	 * @return 
	 * @throws JSONException 
	 * @throws Exception
	 */
	public RequestResponseDTO process(RequestResponseDTO requestDTO) throws IOException, JSONException;

}
