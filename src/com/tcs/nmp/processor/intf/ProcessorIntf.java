package com.tcs.nmp.processor.intf;

import java.io.IOException;

import org.json.JSONException;

import com.tcs.nmp.dto.RequestResponseDTO;

public abstract class ProcessorIntf {

	public abstract RequestResponseDTO process(RequestResponseDTO requestDTO) throws JSONException, IOException;

}
