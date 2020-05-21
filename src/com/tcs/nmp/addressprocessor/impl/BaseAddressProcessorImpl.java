package com.tcs.nmp.addressprocessor.impl;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import com.tcs.nmp.dao.beans.ProcessingLogResultBean;
import com.tcs.nmp.dao.intf.ProcessingLogMasterDAOIntf;
import com.tcs.nmp.dto.RequestResponseDTO;
import com.tcs.nmp.exception.NMPException;
import com.tcs.nmp.processor.impl.EmailProcessorImpl;
import com.tcs.nmp.processor.impl.GoogleNaturalLanguageProcessorImpl;
import com.tcs.nmp.processor.intf.EmailProcessorIntf;
import com.tcs.nmp.processor.intf.LanguageProcessorIntf;
import com.tcs.nmp.processor.intf.ProcessorIntf;
import com.tcs.nmp.utils.NMPConstant;
import com.tcs.nmp.utils.NMPUtility;
import com.tcs.nmp.utils.PredictionResultType;
import com.tcs.nmp.utils.TransactionType;

public class BaseAddressProcessorImpl extends ProcessorIntf {
	
	@Autowired
	private LanguageProcessorIntf languageProcessor;
	
	@Autowired
	private EmailProcessorIntf emailProcessor;
	
	@Autowired
	private ProcessingLogMasterDAOIntf processingLogDAO;
	
	private static final Logger LOGGER = LogManager.getLogger(BaseAddressProcessorImpl.class);
	private String responseString;
	private static final String GEO_API_KEY = "https://maps.googleapis.com/maps/api/geocode/json?key=AIzaSyBOJMsfyq6htyPxr1pn21h6DhVeQSFb1-g&address=";
	private HashMap<String , String> identifierMap;

	@Override
	public RequestResponseDTO process(RequestResponseDTO requestDTO) throws JSONException, IOException  {
		LOGGER.info(NMPConstant.METH_START, "process");
		RequestResponseDTO responseDTO = evaluteInstruction(requestDTO);
		LOGGER.info(NMPConstant.METH_END, "process");
		return responseDTO;
	}
	
	public BaseAddressProcessorImpl(){
	}
	/**
	 * @throws JSONException 
	 * @throws IOException 
	 * @throws Exception 
	 * 
	 */
	private RequestResponseDTO evaluteInstruction(RequestResponseDTO requestDTO) throws JSONException, IOException {
		//If Action --> It has to be processed for valid input. For invalid input proper error message to be communicated
		LOGGER.info(NMPConstant.METH_START, "evaluteInstruction");
		RequestResponseDTO inputDTO = requestDTO;
		boolean processFlag = false;
		identifierMap = new HashMap<String, String>();
		identifierMap.put(NMPConstant.CUST_INDENTIFIER, inputDTO.getCustomerName());
		if(PredictionResultType.ACTION_TYPE.getValue().equals(inputDTO.getInstructionLabel())){
			//New Request from Customer --> To Process for Address
			processFlag = true;
			StringBuffer formattedAddress = retrieveAddress(inputDTO.getInputMessage());
			processUserAddress(formattedAddress,inputDTO);
		}else if(PredictionResultType.CONFIRMATION_TYPE.getValue().equals(inputDTO.getInstructionLabel())){
			/**
			 * Processing for confirmation
			 * Case 1: Confirmation After one Action
			 * Case 3: Orphan Confirmation
			 * Case 2: two consecutive Confirmations
			 */
			List<ProcessingLogResultBean> processingLogResultBeanList = retrievePreviousInstruction(inputDTO);
			if(null!=processingLogResultBeanList){
				//Case 1: Confirmation After Action
				ProcessingLogResultBean processingLogResultBean = processingLogResultBeanList.get(0);
				if(processingLogResultBeanList.size() > 1){
					processingLogResultBean = processingLogResultBeanList.get(1);					
				}
				String previousTransaction = processingLogResultBean.getCurrentInstruction();
				if(PredictionResultType.ACTION_TYPE.getValue().equals(previousTransaction)){
					processFlag = true;
					//Step 1 --> Update TRansaction DB and set the process flag to "Y" with communication ID
					updateTransactionLog(inputDTO, processingLogResultBean);
					if(NMPConstant.CONFIRMATION_NO.equals(inputDTO.getPredictionResultLabel())){
						//Creating confirmation message for No
						responseString = processConfirmationNo(identifierMap, processingLogResultBeanList);
					}else{
						//Step 2 -->Invoke service to update transactions - Integration with ESB Layer/Insurance Providers
						updateAddress(inputDTO);
						//Creating confirmation message for Yes
						responseString = processConfirmation(identifierMap, processingLogResultBeanList);
					}
					//Send back response
					inputDTO = communicateReceiver(inputDTO, responseString);
				}
			}else{
				//Case 3: Orphan Confirmation --> No processing Required.
				responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.ORPHAN_CONF_MSG);
				inputDTO = communicateReceiver(inputDTO, responseString);
			}
		}
		
		//For all other case error message to be return
		if(!processFlag){
			responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.INVALID_INPUT_MSG);
			inputDTO = communicateReceiver(inputDTO, responseString);
		}
		LOGGER.info(NMPConstant.METH_END, "evaluteInstruction");
		return inputDTO;
		
	}
	
	private String processConfirmation(HashMap<String , String> identifierMap, List<ProcessingLogResultBean> processingLogResultBeanList){
		LOGGER.info(NMPConstant.METH_START, "processConfirmation");
		if(processingLogResultBeanList.size()>1){
			//Received one confirmation out of more than one pending Action
			identifierMap.put(NMPConstant.CURR_TRANS_TYPE_INDT, (processingLogResultBeanList.get(1)).getTransIND());
			identifierMap.put(NMPConstant.NEXT_TRANS_TYPE_INDT, (processingLogResultBeanList.get(0)).getTransIND());
			responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.SUCCESSFULL_TRANS_PEND_CONF_MSG);
		}else{
			//Received confirmation for the pending Action
			identifierMap.put(NMPConstant.GREETINGS_INDENTIFIER, NMPUtility.createGreetings());
			String transactionType = processingLogResultBeanList.get(0).getTransIND();
			if(TransactionType.TYPE_ADDRESS.getValue().equals(transactionType)){
				responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.SUCCESS_ADDR_TRANS_CONF_MSG);
			}else if(TransactionType.TYPE_EMAIL.getValue().equals(transactionType)){
				responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.SUCCESS_EMAIL_TRANS_CONF_MSG);
			}else{
				responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.SUCCESS_PHONE_TRANS_CONF_MSG);
			}
			
		}
		LOGGER.info(NMPConstant.METH_END, "processConfirmation");
		return responseString;
	}
	
	private String processConfirmationNo(HashMap<String , String> identifierMap, List<ProcessingLogResultBean> processingLogResultBeanList){
		LOGGER.info(NMPConstant.METH_START, "processConfirmationNo");
		if(processingLogResultBeanList.size()>1){
			//Received one confirmation out of more than one pending Action
			identifierMap.put(NMPConstant.CURR_TRANS_TYPE_INDT, (processingLogResultBeanList.get(1)).getTransIND());
			identifierMap.put(NMPConstant.NEXT_TRANS_TYPE_INDT, (processingLogResultBeanList.get(0)).getTransIND());
			responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.NO_TRANS_PEND_CONF_MSG);
		}else{
			//Received confirmation for the pending Action
			identifierMap.put(NMPConstant.GREETINGS_INDENTIFIER, NMPUtility.createGreetings());
			String transactionType = processingLogResultBeanList.get(0).getTransIND();
			if(TransactionType.TYPE_ADDRESS.getValue().equals(transactionType)){
				responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.NO_ADDR_TRANS_CONF_MSG);
			}else if(TransactionType.TYPE_EMAIL.getValue().equals(transactionType)){
				responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.NO_EMAIL_TRANS_CONF_MSG);
			}else{
				responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.NO_PHONE_TRANS_CONF_MSG);
			}
			
		}
		LOGGER.info(NMPConstant.METH_END, "processConfirmationNo");
		return responseString;
	}
	/**
	 * 
	 * @param formattedAddress
	 * @param requestDTO
	 * @throws JSONException
	 * @throws IOException
	 */
	private void processUserAddress(StringBuffer formattedAddress,RequestResponseDTO requestDTO) throws JSONException,IOException  {
		LOGGER.info(NMPConstant.METH_START, "processUserAddress");
		JSONObject json = new JSONObject(formattedAddress.toString());
		String status = json.getString("status");
		RequestResponseDTO inputDTO = requestDTO;
		//Check the status of GEO_API result
		if(NMPConstant.SUCCESS_RESPONSE.equals(status)){
			//Matching Address Found
			JSONArray addressLinesJSON = json.getJSONArray("results");
			if(null!=addressLinesJSON && addressLinesJSON.length()>1){
				// More than one address line available
				identifierMap = new HashMap<String, String>();
				identifierMap.put(NMPConstant.CUST_INDENTIFIER, inputDTO.getCustomerName());
				responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.MULTIPLE_ADDR_CONF_MSG);
				for(int iterator = 0; iterator<addressLinesJSON.length(); iterator++){
					JSONObject addressJSON = addressLinesJSON.getJSONObject(iterator);
					String addressLine = addressJSON.getString("formatted_address");
					responseString = responseString.concat("\n"+addressLine+"\n");
				}
				communicateReceiver(inputDTO, responseString);
			}else{
				identifierMap = new HashMap<String, String>();
				identifierMap.put(NMPConstant.SALUTE_INDENTIFIER, NMPUtility.createSalutation());
				identifierMap.put(NMPConstant.CUST_INDENTIFIER, inputDTO.getCustomerName());
				//Findbug fix 
				if(null != addressLinesJSON.getJSONObject(0)){					
					identifierMap.put(NMPConstant.ADDRESS_INDENTIFIER, (addressLinesJSON.getJSONObject(0)).getString("formatted_address"));
				}
				responseString = NMPUtility.formatMessage(identifierMap, NMPConstant.SNGL_ADDR_CONF_MSG);
				createProcessingLog(inputDTO, TransactionType.TYPE_ADDRESS.getValue(), (addressLinesJSON.getJSONObject(0)).getString("formatted_address"));
				communicateReceiver(inputDTO, responseString);
			}
			
		}else{
			//Invoke Natural Language process API
			inputDTO.setOtherModuleProcessingIND(TransactionType.TYPE_ADDRESS.getValue());
			inputDTO = languageProcessor.process(inputDTO);
			if(!inputDTO.isOtherModuleProcessingFlag() && !NMPUtility.isStringNullOrNotBlank(inputDTO.getOutputMessage())){
				responseString = NMPUtility.formatMessage(identifierMap,NMPConstant.INVALID_ADDR_CONF_MSG);
				communicateReceiver(inputDTO, responseString);
			}
		}
		LOGGER.info(NMPConstant.METH_END, "processUserAddress");
		
	}
	/**
	 * 
	 * @param requestDTO
	 * @param identifier
	 * @param message
	 * @param processIND
	 * @param instruction
	 */
	public void createProcessingLog(RequestResponseDTO requestDTO, String identifier, String message){
		String processIND = "N";
		String expectedInstruction = PredictionResultType.CONFIRMATION_TYPE.getValue();
		ProcessingLogResultBean processingLogResultBean = new ProcessingLogResultBean();
		processingLogResultBean.setTransIND(identifier);
		processingLogResultBean.setMessage(message);
		processingLogResultBean.setProcessIND(processIND);
		processingLogResultBean.setExpectedInstruction(expectedInstruction);
		processingLogResultBean.setFormID(requestDTO.getCustomerName());
		processingLogResultBean.setConversationID(requestDTO.getCommunicationID());
		processingLogResultBean.setCurrentInstruction(requestDTO.getInstructionLabel());
		processingLogDAO.insert(processingLogResultBean);
	}

	/**
	 * Method to update Address in case of successful transaction
	 * @param requestDTO
	 */
	protected void updateAddress(RequestResponseDTO requestDTO){
		
	}
	
	/**
	 * Method to update transaction log for successful update and remove all entry for the confirmation ID
	 * @param requestDTO
	 */
	private void updateTransactionLog(RequestResponseDTO requestDTO, ProcessingLogResultBean processingLogResultBean){
		LOGGER.debug(NMPConstant.METH_START, "updateTransactionLog");
		processingLogDAO.updateProcessLog(processingLogResultBean);
		LOGGER.debug(NMPConstant.METH_END, "updateTransactionLog");
	}
	
	/**
	 * @param
	 */
	public RequestResponseDTO communicateReceiver(RequestResponseDTO requestDTO, String outputMessage){
		RequestResponseDTO inputDTO = requestDTO;
		inputDTO.setOutputMessage(outputMessage);
		//any processing required to communicate Receiver
		LOGGER.debug(outputMessage);
		return inputDTO;
		
	}
	
	
	/**
	 * Method to access previous instruction
	 * @param requestDTO
	 * @return
	 */
	
	private List<ProcessingLogResultBean> retrievePreviousInstruction(RequestResponseDTO requestDTO){
		LOGGER.info(NMPConstant.METH_START, "retrievePreviousInstruction");
		List<ProcessingLogResultBean> processingLogResultBeanList = processingLogDAO.getPrevInstruction(requestDTO.getCommunicationID());
		LOGGER.info(NMPConstant.METH_END, "retrievePreviousInstruction");
		return processingLogResultBeanList;
		
	}
	/**
	 * 
	 * @param inputString
	 * @return StringBuffer
	 * @throws Exception
	 */
	@SuppressWarnings("deprecation")
	private StringBuffer retrieveAddress(String inputString) {
		StringBuffer outputSting = new StringBuffer("");
		try {
			StringBuffer addressURL = new StringBuffer(GEO_API_KEY);
			addressURL.append(URLEncoder.encode(inputString));
			URL url = new URL(addressURL.toString());
			
			HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");
			if (conn.getResponseCode() != 200) {
			    throw new NMPException("Failed : HTTP error code : "+ conn.getResponseCode());
			}

			BufferedReader br =  new BufferedReader(new InputStreamReader((conn.getInputStream())));
			String output;
            while ((output = br.readLine()) != null) {
            	outputSting.append(output);
             }
			conn.disconnect();
			
		} catch (MalformedURLException e) {
            LOGGER.error(e);
		} catch (IOException e) {
            LOGGER.error(e);
		}
		return outputSting; 
		
	}
	
	
	
	public void setLanguageProcessor(LanguageProcessorIntf languageProcessor) {
		this.languageProcessor = languageProcessor;
	}
	

	public void setEmailProcessor(EmailProcessorIntf emailProcessor) {
		this.emailProcessor = emailProcessor;
	}

	/**
	 * 
	 * @param agrs
	 * @throws Exception 
	 */
	public static void main(String agrs[]) throws Exception{
		RequestResponseDTO requestResponseDTO = new RequestResponseDTO();
		requestResponseDTO.setCommunicationID("C1");
		requestResponseDTO.setCustomerName("Shekhar Sarkar");
		requestResponseDTO.setInstructionLabel(PredictionResultType.ACTION_TYPE.getValue());
		requestResponseDTO.setInputMessage("change my address abc.xyz@gmail.com");
		
		BaseAddressProcessorImpl baseProcessorImpl = new BaseAddressProcessorImpl();
		LanguageProcessorIntf languageProcessor1 = new GoogleNaturalLanguageProcessorImpl();
		baseProcessorImpl.setLanguageProcessor(languageProcessor1);
		EmailProcessorIntf emailProcessor1 = new EmailProcessorImpl();
		baseProcessorImpl.setEmailProcessor(emailProcessor1);
		baseProcessorImpl.process(requestResponseDTO);
		
	}

}
