package topica.linhnv5.video.text2speech.service;

import topica.linhnv5.video.text2speech.model.Conversation;
import topica.linhnv5.video.text2speech.model.Task;

/**
 * Video service, create video task
 * @author ljnk975
 */
public interface VideoTaskService {

	/**
	 * Create task
	 * 
	 * @param conversation The conversation
	 * @return the task
	 * @throws VideoTaskException
	 */
	public Task createVideoTask(Conversation conversation) throws VideoTaskException;

}
