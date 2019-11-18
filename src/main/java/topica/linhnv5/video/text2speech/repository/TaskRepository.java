package topica.linhnv5.video.text2speech.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import topica.linhnv5.video.text2speech.model.Task;

@Repository
public interface TaskRepository extends CrudRepository<Task, String> {

}
