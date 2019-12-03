package topica.linhnv5.video.text2speech.model;

/**
 * This class hold information about a sentence
 * 
 * @author ljnk975
 *
 */
public class Sentence {

	private String engSub;
	private String engApi;
	private String vieSub;

	/**
	 * @return the engSub
	 */
	public String getEngSub() {
		return engSub;
	}

	/**
	 * @param engSub the engSub to set
	 */
	public void setEngSub(String engSub) {
		this.engSub = engSub;
	}
	
	/**
	 * @return the engApi
	 */
	public String getEngApi() {
		return engApi;
	}
	
	/**
	 * @param engApi the engApi to set
	 */
	public void setEngApi(String engApi) {
		this.engApi = engApi;
	}
	
	/**
	 * @return the vieSub
	 */
	public String getVieSub() {
		return vieSub;
	}
	
	/**
	 * @param vieSub the vieSub to set
	 */
	public void setVieSub(String vieSub) {
		this.vieSub = vieSub;
	}

}
