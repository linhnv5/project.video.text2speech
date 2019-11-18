package topica.linhnv5.video.text2speech.controller;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.servlet.ServletContext;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.common.net.HttpHeaders;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import topica.linhnv5.video.text2speech.controller.response.TaskCreate;
import topica.linhnv5.video.text2speech.controller.response.TaskInfo;
import topica.linhnv5.video.text2speech.model.Sentence;
import topica.linhnv5.video.text2speech.model.Task;
import topica.linhnv5.video.text2speech.model.TaskExecute;
import topica.linhnv5.video.text2speech.service.TaskService;
import topica.linhnv5.video.text2speech.service.VideoTaskService;
import topica.linhnv5.video.text2speech.util.FileUtil;

/**
 * Controller for api main <br/>
 * Return a task id if success or return error
 * @author ljnk975
 */
@RestController
@RequestMapping(path = "/api")
@Api(tags = "CreateVideoAPI")
public class ApiController {

	@Value("${video.teaching.infolder}")
	private String inFolder;

	@Value("${video.teaching.outfolder}")
	private String outFolder;

	@Autowired
	private TaskService taskService;

	@Autowired
	private VideoTaskService videoTaskService;

	@SuppressWarnings("resource")
	private List<Sentence> readSentences(File configFile) throws Exception {
		// Input
		FileInputStream is = new FileInputStream(configFile);

		// Create Workbook instance holding reference to .xlsx file
        XSSFWorkbook workbook = new XSSFWorkbook(is);

        // Get first/desired sheet from the workbook
        XSSFSheet sheet = workbook.getSheetAt(0);

        // Config
        List<Sentence> sentences = new ArrayList<Sentence>();

        // 
        Iterator<Row> rowIterator = sheet.iterator();

        // Header
        Row header = rowIterator.next();

        // Data
        while (rowIterator.hasNext()) {
	        Row data = rowIterator.next();

	        // Sentence
	        Sentence s = new Sentence();

	        // Iterate through each cells one by one
	        Iterator<Cell> cellIterator = header.iterator();

	        while (cellIterator.hasNext()) {
	        	Cell h = cellIterator.next();
	        	Cell d = data.getCell(h.getColumnIndex());
	  
	        	if (d == null)
	        		continue;

	        	switch (h.getStringCellValue().toLowerCase()) {
	        		case "engsub":
	        			s.setEngSub(d.getStringCellValue());
	        			break;

	        		case "engapi":
	        			s.setEngApi(d.getStringCellValue());
	        			break;

	        		case "viesub":
	        			s.setVieSub(d.getStringCellValue());
	        			break;
				}
	        }
	        
	        System.out.println("Eng: "+s.getEngSub()+" api: "+s.getEngApi()+" vie: "+s.getVieSub());

	        // Cheek
	        if (s.getEngSub() == null || s.getEngApi() == null || s.getVieSub() == null)
	        	throw new Exception("Row "+data.getRowNum()+" error!");
	        
	        if (s.getEngSub().equals(""))
	        	continue;
	        
	        sentences.add(s);
        }
    
        System.out.println("Sentences= "+sentences.size());
        return sentences;
	}

	@PostMapping(path = "/task.create")
	@ApiOperation(value = "Create video task", response = TaskCreate.class, tags = "Task")
	@ApiResponses({
		@ApiResponse(code = 200, message = "Result SUCCESS status if successful, ERROR if some error occur"),
		@ApiResponse(code = 404, message = "Input file not specific")
	})
	public ResponseEntity<TaskCreate> createVideoTask(
			@ApiParam(value = "Input file (excel)", required = false)		
			@RequestParam(value = "input", required = false)
				MultipartFile input
			) {
		// The response
		TaskCreate response = null;

		// Check config
		if (input == null)
			return new ResponseEntity<TaskCreate>(HttpStatus.NOT_FOUND);

		try {
			File f = FileUtil.matchFileName(inFolder, input.getOriginalFilename());
			input.transferTo(f);
			List<Sentence> sentences = readSentences(f);

			// Create and return a task
			Task task = videoTaskService.createVideoTask(sentences);

			// task input
			task.setInputFile(f.getName());

			// Task id
			System.out.println("   return task id="+task.getId());

			// Return task name
			response = new TaskCreate("SUCCESS", "", task.getId());
		} catch (Exception e) {
			System.out.println("   err: "+e.getMessage());
			response = new TaskCreate("ERROR", e.getMessage(), "");
		}

		return new ResponseEntity<TaskCreate>(response, HttpStatus.OK);
	}

	@GetMapping(path = "/task.info")
	@ApiOperation(value = "Get task infomation", response = TaskInfo.class, tags = "Task")
	@ApiResponses({
		@ApiResponse(code = 200, message = "Return Task infomation"),
		@ApiResponse(code = 404, message = "TaskID is not found")
	})
	public ResponseEntity<TaskInfo> getTaskInfo(@RequestParam("id") String id) throws InterruptedException, ExecutionException {
		// Find task
		Task task = taskService.getTaskById(id);

		if (task == null)
			return new ResponseEntity<TaskInfo>(HttpStatus.NOT_FOUND);

		// Result
		TaskInfo result = new TaskInfo(); TaskExecute execute;

		// Is execute
		if (task.getError() != null || task.getOutputFile() != null) {
			if (task.getError() != null) {
				result.setStatus("ERROR");
				result.setError(task.getError());
			} else
				result.setStatus("SUCCESS");
			result.setProgress(100);
			result.setTimeConsume(task.getTimeConsume());
		} else if ((execute = taskService.getTaskExecuteById(id)) != null) {
			result.setStatus("RUNNING");
			result.setTimeConsume(System.currentTimeMillis()-execute.getStartMilis());
			result.setProgress(execute.getProgress());
		} else {
			result.setStatus("ERROR");
			result.setError("Task can't eexecute");
		}

		return new ResponseEntity<TaskInfo>(result, HttpStatus.OK);
	}

	@Autowired
	private ServletContext servletContext;

	/**
	 * Helper function, return media type of input file name
	 * @param fileName the file name
	 * @return media type of file name
	 */
	private MediaType getMediaTypeForFileName(String fileName) {
		// application/pdf
		// application/xml
		// image/gif, ...
		String mineType = servletContext.getMimeType(fileName);
		try {
			return MediaType.parseMediaType(mineType);
		} catch (Exception e) {
		}
		return MediaType.APPLICATION_OCTET_STREAM;
	}

	@GetMapping(path = "/task.result")
	@ApiOperation(value = "Get result video of task", tags = "Task")
	@ApiResponses({
		@ApiResponse(code = 200, message = "Return result video"),
		@ApiResponse(code = 404, message = "TaskID is not found"),
		@ApiResponse(code = 403, message = "Task result error"),
		@ApiResponse(code = 204, message = "Task isn't finnished yet")
	})
	public ResponseEntity<?> getTaskResult(@RequestParam("id") String id) throws Exception {
		Task task = taskService.getTaskById(id);

		if (task == null)
			return new ResponseEntity<String>("Task not found!", HttpStatus.NOT_FOUND);

		if (task.getError() == null && task.getOutputFile() == null)
			return new ResponseEntity<String>("Task not finish!", HttpStatus.NO_CONTENT);

		// If error
		if (task.getError() != null)
			return new ResponseEntity<String>("Task error!", HttpStatus.FORBIDDEN);

		// Return value
		MediaType mediaType = getMediaTypeForFileName(task.getOutputFile());

		File output = new File(outFolder+task.getOutputFile());
		InputStreamResource resource = new InputStreamResource(new FileInputStream(output));

		return ResponseEntity.ok()
				// Content-Disposition
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + output.getName())
				// Content-Type
				.contentType(mediaType)
				// Contet-Length
				.contentLength(output.length()) //
				.body(resource);
	}

}
