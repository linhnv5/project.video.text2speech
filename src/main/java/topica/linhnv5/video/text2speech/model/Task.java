package topica.linhnv5.video.text2speech.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Task table, hold information about task
 * @author ljnk975
 */
@Entity
@Table(name = "Task")
public class Task {

	@Id
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(name = "input")
	private String inputFile;

	@Column(name = "output")
	private String outputFile;

	@Column(name = "errorOccur")
	private String error;

	@Column(name = "time")
	private long timeConsume;

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @return the inputFile
	 */
	public String getInputFile() {
		return inputFile;
	}

	/**
	 * @param inputFile the inputFile to set
	 */
	public void setInputFile(String inputFile) {
		this.inputFile = inputFile;
	}

	/**
	 * @return the outputFile
	 */
	public String getOutputFile() {
		return outputFile;
	}

	/**
	 * @param outputFile the outputFile to set
	 */
	public void setOutputFile(String outputFile) {
		this.outputFile = outputFile;
	}

	/**
	 * @return the error
	 */
	public String getError() {
		return error;
	}

	/**
	 * @param error the error to set
	 */
	public void setError(String error) {
		this.error = error;
	}

	/**
	 * @return the timeConsume
	 */
	public Long getTimeConsume() {
		return timeConsume;
	}

	/**
	 * @param timeConsume the timeConsume to set
	 */
	public void setTimeConsume(long timeConsume) {
		this.timeConsume = timeConsume;
	}

}
