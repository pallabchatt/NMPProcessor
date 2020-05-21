package com.tcs.nmp.addressprocessor.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tcs.nmp.dto.RequestResponseDTO;

public class ResidenceAddressProcessImpl extends BaseAddressProcessorImpl {
	
	private static final Logger LOGGER = LogManager.getLogger(ResidenceAddressProcessImpl.class);
	
	@Override
	public RequestResponseDTO process(RequestResponseDTO requestDTO) {
		return requestDTO;
	}
	

}
