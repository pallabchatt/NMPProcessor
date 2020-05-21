package com.tcs.nmp.handler;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.google.api.services.language.v1beta1.model.Sentiment;
import com.google.api.services.prediction.model.Output;
import com.google.api.services.prediction.model.Output.OutputMulti;
import com.tcs.nmp.dao.beans.PredictionResultBean;
import com.tcs.nmp.dao.intf.PredictionMessageDAOIntf;
import com.tcs.nmp.dto.RequestResponseDTO;
import com.tcs.nmp.processor.intf.LanguageProcessorIntf;
import com.tcs.nmp.processor.intf.ProcessorIntf;
import com.tcs.nmp.utils.PredictionComparator;
import com.tcs.nmp.utils.PredictionResultType;

import edu.emory.mathcs.backport.java.util.Collections;

public class DataHandlerImpl implements DataHandlerIntf {
	
	private static final Logger LOGGER =  LogManager.getLogger(DataHandlerImpl.class);

	private List<String> actionLabelList = new ArrayList<String>();

	private List<String> confirmationLabelList = new ArrayList<String>();

	private Map<String, String> labelValidatorMap = new HashMap<String, String>();
	
	
	private static final Double MINIMUM_SCORE = .4;
	
	@Autowired
	private LanguageProcessorIntf languageProcessorIntf;
	
	@Autowired
	private PredictionMessageDAOIntf predictionMessageDAOIntf;
	
	@Autowired
	private ApplicationContext applicationContext;
	

	public List<String> getActionLabelList() {
		return actionLabelList;
	}

	public void setActionLabelList(List<String> actionLabelList) {
		this.actionLabelList = actionLabelList;
	}

	public List<String> getConfirmationLabelList() {
		return confirmationLabelList;
	}

	public void setConfirmationLabelList(List<String> confirmationLabelList) {
		this.confirmationLabelList = confirmationLabelList;
	}

	public Map<String, String> getLabelValidatorMap() {
		return labelValidatorMap;
	}

	public void setLabelValidatorMap(Map<String, String> labelValidatorMap) {
		this.labelValidatorMap = labelValidatorMap;
	}

	public DataHandlerImpl() {

	}

	public List<String> getActionlabellist() {
		return actionLabelList;
	}

	public List<String> getConfirmationlabellist() {
		return confirmationLabelList;
	}

	public Map<String, String> getLabelvalidatormap() {
		return labelValidatorMap;
	}

	@Override
	public String handleFavourableData(String input, Output output, String convId,
			String name, boolean favInd) throws JSONException, IOException{
		String returnValue = output.getOutputLabel();
		String label = output.getOutputLabel();
		Sentiment sentiment = languageProcessorIntf.analyzeSentiment(input);
		if (actionLabelList.contains(label)) {
			//bean to be inserted in db
			savePredictionResult(input, convId, label, PredictionResultType.ACTION_TYPE.getValue(), sentiment);
			//the below dto would be passed to validator layer
			RequestResponseDTO requestDTO = new RequestResponseDTO();
			requestDTO.setCommunicationID(convId);
			requestDTO.setCustomerName(name);
			requestDTO.setInputMessage(input);
			requestDTO.setInstructionLabel(PredictionResultType.ACTION_TYPE.getValue());
			requestDTO.setPredictionResultLabel(label);
			requestDTO.setFavourableIndicator(favInd);
			String validatorClassName = labelValidatorMap.get(label);
			LOGGER.debug(validatorClassName + " will be invoked");
			ProcessorIntf processor = (ProcessorIntf) applicationContext.getBean(validatorClassName);
			RequestResponseDTO responseDTO = processor.process(requestDTO);		
			returnValue = responseDTO.getOutputMessage();
			
		} else if (confirmationLabelList.contains(label)) {
			//bean to be inserted in db
			savePredictionResult(input, convId, label, PredictionResultType.CONFIRMATION_TYPE.getValue(), sentiment);
			//the below dto would be passed to validator layer
			RequestResponseDTO requestDTO = new RequestResponseDTO();
			requestDTO.setCommunicationID(convId);
			requestDTO.setCustomerName(name);
			requestDTO.setInputMessage(input);
			requestDTO.setInstructionLabel(PredictionResultType.CONFIRMATION_TYPE.getValue());		
			requestDTO.setPredictionResultLabel(label);
			requestDTO.setFavourableIndicator(favInd);
			String validatorClassName = labelValidatorMap.get(label);
			LOGGER.debug(validatorClassName + " will be invoked");
			ProcessorIntf processor = (ProcessorIntf) applicationContext.getBean(validatorClassName);
			RequestResponseDTO responseDTO = processor.process(requestDTO);		
			returnValue = responseDTO.getOutputMessage();
			
		}
		if(null ==  returnValue){
			returnValue = createGenericResponse(name);
		}
		return returnValue;
	}

	private void savePredictionResult(String input, String convId,
			String label, String resultType, Sentiment sentiment) {
		PredictionResultBean predictionResultBean = new PredictionResultBean();
		predictionResultBean.setConversationId(convId);
		predictionResultBean.setCreatedTS(new Timestamp(System.currentTimeMillis()));
		predictionResultBean.setLabel(label);
		predictionResultBean.setMessage(input);
		predictionResultBean.setType(resultType);
		if (sentiment != null) {
			predictionResultBean.setSentimentMagnitde(String.valueOf(sentiment.getMagnitude()));
			predictionResultBean.setSentimentPolarity(String.valueOf(sentiment.getPolarity()));
		}
		predictionMessageDAOIntf.insert(predictionResultBean);
	}

	@Override
	public String handleNonFavourableData(String input, Output output,
			String convId, String name) throws JSONException, IOException{
		String label = output.getOutputLabel();
		String returnValue = null;
		List<OutputMulti> outputList = output.getOutputMulti();
		//less than 45 ,return all label and score
		boolean isMinimumScorePresent = checkIfMinimumScorePresent(outputList);
		
		if(isMinimumScorePresent){			
			Collections.sort(outputList, new PredictionComparator());
			label = outputList.get(0).getLabel();
			output.setOutputLabel(label);
			LOGGER.debug("Non favourable data label :"+label);
			returnValue = handleFavourableData(input, output, convId, name, false);			
		}
		if(null ==  returnValue){
			Sentiment sentiment = languageProcessorIntf.analyzeSentiment(input);
			savePredictionResult(input, convId, null, null, sentiment);
			returnValue = createGenericResponse(name);
		}
		return returnValue;
	}

	private String createGenericResponse(String name) {
		String returnValue;
		StringBuilder actionItems = new StringBuilder();
		Iterator<String> listIterator = actionLabelList.iterator();
		while(listIterator.hasNext()){
			String eachItem = listIterator.next();
			actionItems.append(eachItem);
			if(listIterator.hasNext()){					
				actionItems.append("/");
			}
		}
		returnValue = "Hi " + name + ", we are unable to process your request.\nPlease mention if you would like to -\n " +actionItems.toString();
		return returnValue;
	}

	private boolean checkIfMinimumScorePresent(List<OutputMulti> outputList) {
		boolean isMinimumScorePresent = false;
		for(OutputMulti om : outputList){
			if(Double.valueOf(om.getScore()) >= MINIMUM_SCORE ){
				isMinimumScorePresent = true;
				break;
			}
		}
		return isMinimumScorePresent;
	}

}
