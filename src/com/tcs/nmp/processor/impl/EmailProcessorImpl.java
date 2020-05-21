package com.tcs.nmp.processor.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;

import com.tcs.nmp.addressprocessor.impl.BaseAddressProcessorImpl;
import com.tcs.nmp.dto.RequestResponseDTO;
import com.tcs.nmp.processor.intf.EmailProcessorIntf;
import com.tcs.nmp.utils.NMPConstant;
import com.tcs.nmp.utils.NMPUtility;
import com.tcs.nmp.utils.TransactionType;

public class EmailProcessorImpl implements EmailProcessorIntf{

	private HashMap<String , String> identifierMap;
	
	@Autowired
	private BaseAddressProcessorImpl baseProcessor;
	
	private String responseString;
	@Override
	public RequestResponseDTO process(RequestResponseDTO requestDTO)throws IOException {
		processEmailAddress(requestDTO);
		return requestDTO;
	}

	
	private RequestResponseDTO processEmailAddress(RequestResponseDTO requestDTO) {
		RequestResponseDTO inputDTO = requestDTO;
		identifierMap = new HashMap<String, String>();
		identifierMap.put(NMPConstant.CUST_INDENTIFIER, inputDTO.getCustomerName());
		List<String> validEmailList = extarctEmailAddress(inputDTO);
		if(validEmailList.size()<1){
			responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.NO_EMAIL_MSG);
			inputDTO = baseProcessor.communicateReceiver(inputDTO, responseString);
		}else if(validEmailList.size()>1){
			responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.MULTIPLE_EMAIL_MSG);
			for(String email: validEmailList){
				responseString = responseString.concat("\n "+email);
				inputDTO = baseProcessor.communicateReceiver(inputDTO, responseString);
			}
		}else{
			//Create process log for email Address
			baseProcessor.createProcessingLog(inputDTO, TransactionType.TYPE_EMAIL.getValue(), validEmailList.get(0));
			identifierMap.put(NMPConstant.SALUTE_INDENTIFIER, NMPUtility.createSalutation());
			identifierMap.put(NMPConstant.EMAIL_INDENTIFIER, (validEmailList.get(0)));
			responseString = NMPUtility.formatMessage(identifierMap, NMPConstant.SNGL_EMAIL_CONF_MSG);
			inputDTO = baseProcessor.communicateReceiver(inputDTO, responseString);
			
		}
		return inputDTO;
	}


	private List<String> extarctEmailAddress(RequestResponseDTO requestDTO){
		List<String> emailElements = extarctEmail(requestDTO.getInputMessage());
		List<String> validatedEmail = new ArrayList<String>();
		if(null!=emailElements && !emailElements.isEmpty()){
			for(String emailID:emailElements){
				if(NMPUtility.validateEmail(emailID)){
					validatedEmail.add(emailID);
				}
			}
		}
		return validatedEmail;
	}
	
	private static List<String> extarctEmail(String inputString){
		Matcher m = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+").matcher(inputString);
	    List<String> emailList = new ArrayList<String>();
		while (m.find()) {
			emailList.add(m.group());
	    }
		return emailList;
	}

}
