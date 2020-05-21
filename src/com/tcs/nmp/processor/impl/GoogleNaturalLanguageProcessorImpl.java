package com.tcs.nmp.processor.impl;

import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.language.v1beta1.CloudNaturalLanguageAPI;
import com.google.api.services.language.v1beta1.CloudNaturalLanguageAPIRequestInitializer;
import com.google.api.services.language.v1beta1.model.AnalyzeEntitiesRequest;
import com.google.api.services.language.v1beta1.model.AnalyzeEntitiesResponse;
import com.google.api.services.language.v1beta1.model.AnalyzeSentimentRequest;
import com.google.api.services.language.v1beta1.model.AnalyzeSentimentResponse;
import com.google.api.services.language.v1beta1.model.AnnotateTextRequest;
import com.google.api.services.language.v1beta1.model.AnnotateTextResponse;
import com.google.api.services.language.v1beta1.model.Document;
import com.google.api.services.language.v1beta1.model.Entity;
import com.google.api.services.language.v1beta1.model.Features;
import com.google.api.services.language.v1beta1.model.Sentiment;
import com.google.api.services.language.v1beta1.model.Token;
import com.tcs.nmp.addressprocessor.impl.BaseAddressProcessorImpl;
import com.tcs.nmp.dto.RequestResponseDTO;
import com.tcs.nmp.processor.intf.LanguageProcessorIntf;
import com.tcs.nmp.utils.EntityResultType;
import com.tcs.nmp.utils.NMPConstant;
import com.tcs.nmp.utils.NMPUtility;
import com.tcs.nmp.utils.PredictionResultType;

public class GoogleNaturalLanguageProcessorImpl implements LanguageProcessorIntf {
	
	private static final Logger LOGGER = LogManager.getLogger(GoogleNaturalLanguageProcessorImpl.class);
	@Autowired
	private EmailProcessorImpl emailProcessor;
	
	@Autowired
	private BaseAddressProcessorImpl baseProcessor;

	private static final String APPLICATION_NAME = "My First Project";
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static HttpTransport httpTransport;
	private static final String LANGUAGE_API_KEY = "AIzaSyCuiZ_kDp62a7ZcnjqnWHwSmsG7F0bJZus";
	private final CloudNaturalLanguageAPI languageApi;
	private HashMap<String , String> identifierMap;
	private String reponseString;
	
	  /**
	   * Constructs a {@link NaturalLanguageAPI} which connects to the Cloud Natural Language API.
	 * @throws IOException 
	 * @throws GeneralSecurityException 
	   */
	
	  public GoogleNaturalLanguageProcessorImpl() throws GeneralSecurityException, IOException {
	    this.languageApi = getLanguageService();
	  }
	
	public RequestResponseDTO process(RequestResponseDTO requestDTO) throws IOException, JSONException  {
		//Checking Entity
		RequestResponseDTO inputDTO = requestDTO;
		inputDTO = processEntities(analyzeEntities(inputDTO.getInputMessage()),inputDTO);
		return inputDTO;
	}
	
	
	
	private RequestResponseDTO processEntities(List<Entity> analyzeEntities, RequestResponseDTO requestDTO) throws IOException, JSONException {
		RequestResponseDTO inputDTO = requestDTO;
		identifierMap = new HashMap<String, String>();
		identifierMap.put(NMPConstant.CUST_INDENTIFIER, inputDTO.getCustomerName());
		boolean languageFlag = false;
		if (null == analyzeEntities || analyzeEntities.size() == 0) {
			// No entity Found 
			if(null!=inputDTO.getOtherModuleProcessingIND()){
				inputDTO.setOtherModuleProcessingFlag(false);
			}else{
				if(null != inputDTO.getPredictionResultLabel() && PredictionResultType.ACTION_TYPE.getValue().equals(inputDTO.getInstructionLabel())){
					identifierMap.put(NMPConstant.SALUTE_INDENTIFIER, NMPUtility.createSalutation());
					identifierMap.put(NMPConstant.LABEL_NAME, inputDTO.getPredictionResultLabel());
					reponseString = NMPUtility.formatMessage(identifierMap,NMPConstant.NEED_MORE_INFO);
					inputDTO = baseProcessor.communicateReceiver(inputDTO, reponseString);
					
					
				}else{
					identifierMap.put(NMPConstant.SALUTE_INDENTIFIER, NMPUtility.createSalutation());
					reponseString = NMPUtility.formatMessage(identifierMap,NMPConstant.NO_INSTRUCTION_MSG);
					inputDTO = baseProcessor.communicateReceiver(inputDTO, reponseString);					
				}
				
			}
		}else{
			 for (Entity entity : analyzeEntities) {
				 if(EntityResultType.TYPE_LOCATION.getValue().equals(entity.getType())){
					 identifierMap.put(NMPConstant.LOCATION_INDENTIFIER, entity.getName());
					 reponseString = NMPUtility.formatMessage(identifierMap,NMPConstant.LOCATION_ENTITY_MSG);
					 inputDTO = baseProcessor.communicateReceiver(inputDTO, reponseString);
				 }else if(EntityResultType.TYPE_OTHER.getValue().equals(entity.getType())){
					 //Searching if email address available
					 if(NMPUtility.isStringNullOrNotBlank(entity.getName()) && entity.getName().contains(NMPConstant.EMAIL_IDENT_STRING)){
						 //invoke email processor
						 languageFlag = true;
						 emailProcessor.process(inputDTO);
					 }
				 }
				 if(!languageFlag && null==inputDTO.getOutputMessage()){
					 if(null != inputDTO.getPredictionResultLabel() && PredictionResultType.ACTION_TYPE.getValue().equals(inputDTO.getInstructionLabel())){
						identifierMap.put(NMPConstant.SALUTE_INDENTIFIER, NMPUtility.createSalutation());
						identifierMap.put(NMPConstant.LABEL_NAME, inputDTO.getPredictionResultLabel());
						reponseString = NMPUtility.formatMessage(identifierMap,NMPConstant.NEED_MORE_INFO);
						inputDTO = baseProcessor.communicateReceiver(inputDTO, reponseString);						
							
					}else{
						identifierMap.put(NMPConstant.SALUTE_INDENTIFIER, NMPUtility.createSalutation());
						reponseString = NMPUtility.formatMessage(identifierMap,NMPConstant.NO_INSTRUCTION_MSG);
						inputDTO = baseProcessor.communicateReceiver(inputDTO, reponseString);
					}
					 
				 }
			 }
		}
		return inputDTO;
	}
	
	
	 /**
	   * Print a list of {@code entities}.
	   */
	  public static void printEntities(PrintStream out, List<Entity> entities) {
	    if (entities == null || entities.size() == 0) {
	      out.println("No entities found.");
	      return;
	    }
	    out.printf("Found %d entit%s.\n", entities.size(), entities.size() == 1 ? "y" : "ies");
	    for (Entity entity : entities) {
	      out.printf("%s\n", entity.getName());
	      out.printf("\tSalience: %.3f\n", entity.getSalience());
	      out.printf("\tType: %s\n", entity.getType());
	      if (entity.getMetadata() != null) {
	        for (Map.Entry<String, String> metadata : entity.getMetadata().entrySet()) {
	          out.printf("\tMetadata: %s = %s\n", metadata.getKey(), metadata.getValue());
	        }
	      }
	    }
	  }

	/**
	   * Connects to the Natural Language API using Application Default Credentials.
	 * @throws IOException 
	 * @throws GeneralSecurityException 
	 * @throws Exception 
	   */
	  private static CloudNaturalLanguageAPI getLanguageService() throws GeneralSecurityException, IOException  {
	   
	    httpTransport = GoogleNetHttpTransport.newTrustedTransport();
	    
	    CloudNaturalLanguageAPI api = new CloudNaturalLanguageAPI.Builder(httpTransport,JSON_FACTORY,null).setApplicationName(APPLICATION_NAME)
	    		.setGoogleClientRequestInitializer(new CloudNaturalLanguageAPIRequestInitializer(LANGUAGE_API_KEY)).build();
	    return api;
	  }
	  
	
	/**
	   * Gets {@link Entity}s from the string {@code text}.
	 * @throws IOException 
	   */
	  private List<Entity> analyzeEntities(String text) throws IOException  {
	    AnalyzeEntitiesRequest request =
	        new AnalyzeEntitiesRequest()
	            .setDocument(new Document().setContent(text).setType("PLAIN_TEXT"))
	            .setEncodingType("UTF16");
	    CloudNaturalLanguageAPI.Documents.AnalyzeEntities analyze =
	        languageApi.documents().analyzeEntities(request);

	    AnalyzeEntitiesResponse response = analyze.execute();
	    return response.getEntities();
	  }

	  /**
	   * Gets {@link Sentiment} from the string {@code text}.
	   */
	  public Sentiment analyzeSentiment(String text) throws IOException {
	    AnalyzeSentimentRequest request =
	        new AnalyzeSentimentRequest()
	            .setDocument(new Document().setContent(text).setType("PLAIN_TEXT"));
	    CloudNaturalLanguageAPI.Documents.AnalyzeSentiment analyze =
	        languageApi.documents().analyzeSentiment(request);

	    AnalyzeSentimentResponse response = analyze.execute();
	    return response.getDocumentSentiment();
	  }

	  /**
	   * Gets {@link Token}s from the string {@code text}.
	   */
	  public List<Token> analyzeSyntax(String text) throws IOException {
	    AnnotateTextRequest request =
	        new AnnotateTextRequest()
	            .setDocument(new Document().setContent(text).setType("PLAIN_TEXT"))
	            .setFeatures(new Features().setExtractSyntax(true))
	            .setEncodingType("UTF16");
	    CloudNaturalLanguageAPI.Documents.AnnotateText analyze =
	        languageApi.documents().annotateText(request);

	    AnnotateTextResponse response = analyze.execute();
	    return response.getTokens();
	  }
	  

	  /**
	   * Print the Sentiment {@code sentiment}.
	   */
	  public static void printSentiment(PrintStream out, Sentiment sentiment) {
	    if (sentiment == null) {
	      LOGGER.debug("No sentiment found");
	      return;
	    }
	    LOGGER.debug("Found sentiment.");
	    LOGGER.debug("\tMagnitude: %.3f\n", sentiment.getMagnitude());
	    LOGGER.debug("\tPolarity: %.3f\n", sentiment.getPolarity());
	  }

	  public static void printSyntax(PrintStream out, List<Token> tokens) {
	    if (tokens == null || tokens.size() == 0) {
	      LOGGER.debug("No syntax found");
	      return;
	    }
	    LOGGER.debug("Found %d token%s.\n", tokens.size(), tokens.size() == 1 ? "" : "s");
	    for (Token token : tokens) {
	      LOGGER.debug("TextSpan");
	      LOGGER.debug("\tText: %s\n", token.getText().getContent());
	      LOGGER.debug("\tBeginOffset: %d\n", token.getText().getBeginOffset());
	      LOGGER.debug("Lemma: %s\n", token.getLemma());
	      LOGGER.debug("PartOfSpeechTag: %s\n", token.getPartOfSpeech().getTag());
	      LOGGER.debug("DependencyEdge");
	      LOGGER.debug("\tHeadTokenIndex: %d\n", token.getDependencyEdge().getHeadTokenIndex());
	      LOGGER.debug("\tLabel: %s\n", token.getDependencyEdge().getLabel());
	    }
	  }

}
