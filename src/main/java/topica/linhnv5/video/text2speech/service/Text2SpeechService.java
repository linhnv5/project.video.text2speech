package topica.linhnv5.video.text2speech.service;

/**
 * Text2SpeechService, contain method to covert text to speech mp3
 * @author ljnk975
 */
public interface Text2SpeechService {

	/**
	 * get pronoun of text
	 * @param text
	 * @return
	 * @throws Exception
	 */
	public String getPronoun(String text) throws Exception;

	/**
	 * Covert text to speech
	 * @param text
	 * @param voice
	 * @return
	 * @throws Exception 
	 */
	public byte[] text2Speech(String text, String voice) throws Exception;

	/**
	 * Convert text to speech
	 * @param text
	 * @param voice
	 * @return
	 * @throws Exception
	 */
	public byte[] text2SpeechFPT(String text, String voice) throws Exception;

}
