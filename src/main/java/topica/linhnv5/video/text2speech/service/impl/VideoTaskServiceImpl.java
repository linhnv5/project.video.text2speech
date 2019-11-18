package topica.linhnv5.video.text2speech.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import topica.linhnv5.video.text2speech.model.Sentence;
import topica.linhnv5.video.text2speech.model.Task;
import topica.linhnv5.video.text2speech.model.TaskExecute;
import topica.linhnv5.video.text2speech.service.TaskService;
import topica.linhnv5.video.text2speech.service.VideoTaskException;
import topica.linhnv5.video.text2speech.service.VideoTaskService;

/**
 * Video service, create video task
 * 
 * @author ljnk975
 *
 */
@Service
public class VideoTaskServiceImpl implements VideoTaskService {

	@Autowired
	private TaskService taskService;

	@Autowired
	private VideoTaskExecute execute;

	@Override
	public Task createVideoTask(List<Sentence> sentences) throws VideoTaskException {
		// Create task
		TaskExecute task = new TaskExecute();

		try {
			// Add task
			taskService.addTask(task);

			// Create async job
			execute.doVideoTask(sentences, task);
		} catch(Exception e) {
			e.printStackTrace();
			throw new VideoTaskException(e.getMessage());
		}

		// And just return it
		return task.getTask();
	}

}
