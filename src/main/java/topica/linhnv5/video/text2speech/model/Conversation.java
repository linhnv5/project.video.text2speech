package topica.linhnv5.video.text2speech.model;

import java.util.List;

/**
 * This class hold information about a conversation, list of sentence
 * 
 * @author ljnk975
 *
 */
public class Conversation {

	private List<Sentence> listOfSentences;
	
	public Conversation(List<Sentence> listOfSentences) {
		this.listOfSentences = listOfSentences;
	}

	/**
	 * @return the listOfSentences
	 */
	public List<Sentence> getListOfSentences() {
		return listOfSentences;
	}

	/**
	 * @param listOfSentences the listOfSentences to set
	 */
	public void setListOfSentences(List<Sentence> listOfSentences) {
		this.listOfSentences = listOfSentences;
	}
	
}
