package topica.linhnv5.video.text2speech.service;

import java.util.List;

import topica.linhnv5.video.text2speech.model.Sentence;
import topica.linhnv5.video.text2speech.model.Task;

/**
 * Video service, create video task
 * @author ljnk975
 */
public interface VideoTaskService {

	/**
	 * Create task
	 * @return
	 * @throws VideoTaskException
	 */
	public Task createVideoTask(List<Sentence> sentences) throws VideoTaskException;

}
