package topica.linhnv5.video.text2speech.service.impl;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import topica.linhnv5.video.text2speech.model.Sentence;
import topica.linhnv5.video.text2speech.model.Task;
import topica.linhnv5.video.text2speech.model.TaskExecute;
import topica.linhnv5.video.text2speech.service.TaskService;
import topica.linhnv5.video.text2speech.service.Text2SpeechService;
import topica.linhnv5.video.text2speech.util.FileUtil;
import topica.linhnv5.video.text2speech.video.VideoEncoding;
import topica.linhnv5.video.text2speech.video.VideoEncodingException;

/**
 * Execute class, runnable execute task code
 * 
 * @author ljnk975
 *
 */
@Component
public class VideoTaskExecute {

	@Value("${video.teaching.workingfolder}")
	private String workingFolder;

	@Value("${video.teaching.infolder}")
	private String inFolder;

	@Value("${video.teaching.outfolder}")
	private String outFolder;

	@Autowired
	private TaskService taskService;

	/**
	 * Check and mk input output dir
	 */
	private void checkInOutFolder() {
		FileUtil.checkAndMKDir(inFolder);
		FileUtil.checkAndMKDir(outFolder);
	}

	private BufferedImage background;
	private BufferedImage sound;
	private BufferedImage time;
	private BufferedImage time2;
	private BufferedImage listening;
	private BufferedImage listening2;

	/**
	 * Load source image if needed
	 * @throws Exception 
	 */
	private void loadImage() throws Exception {
		// Check if image is not loaded
		if (background == null) {
			background   = ImageIO.read(new File(workingFolder+"1x"+File.separator+"background.png"));
			sound        = ImageIO.read(new File(workingFolder+"1x"+File.separator+"sound.png"));
			time         = ImageIO.read(new File(workingFolder+"1x"+File.separator+"time.png"));
			time2        = ImageIO.read(new File(workingFolder+"1x"+File.separator+"time2.png"));
			listening    = ImageIO.read(new File(workingFolder+"1x"+File.separator+"listening.png"));

			// Blur listening
			float[] filter = new float[400];
			for (int i = 0; i < filter.length; i++)
				filter[i] = 1F/400F;

			Kernel kernel = new Kernel(20, 20, filter);
			BufferedImageOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
			listening2 = op.filter(listening, null);
		}
	}

	private void drawBack(Graphics2D g, double progress) {
		// progress y
		int fromY = 194; int toY = 876; int numb = toY - fromY;

		// back ground
		g.drawImage(background, 0, 0, null);
		g.drawImage(sound, 0, 0, null);
		g.drawImage(time, 0, 0, null);

		// progress bar 875-195
		int y = fromY+(int)(numb*(1-progress));
		g.setClip(0, y, background.getWidth(), background.getHeight()-y);
		g.drawImage(time2, 0, 0, null);
		
		g.setClip(0, 0, background.getWidth(), background.getHeight());
	}

	private BufferedImage createListeningFrame(double progress, boolean blur) throws Exception {
		// Create outpur image
		BufferedImage img = new BufferedImage(background.getWidth(), background.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);

		// Get the graphics
		Graphics2D g = (Graphics2D) img.getGraphics();

		// back ground
		drawBack(g, progress);

		// Listening
		int w = listening.getWidth()/2; int h = listening.getHeight()/2;
		g.drawImage(blur ? listening : listening2, background.getWidth()/2-w/2, background.getHeight()/2-h/2, w, h, null);
		
		return img;
	}

	private BufferedImage createAPIFrame(double progress, String engSub, String engApi, String vieSub) throws Exception {
		// Create outpur image
		BufferedImage img = new BufferedImage(background.getWidth(), background.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);

		// Get the graphics
		Graphics2D g = (Graphics2D) img.getGraphics();

		// back ground
		drawBack(g, progress);

		// Listening
		// Font
	    Font dynamicFont1 = new Font("Corbel", Font.BOLD,  63);
	    Font dynamicFont2 = new Font("Arial",  Font.PLAIN, 60);
	    Font dynamicFont3 = new Font("Arial",  Font.PLAIN, 60);

	    g.setColor(new Color(247, 173, 70));
	    g.setFont(dynamicFont1);
	    g.drawString(engSub, img.getWidth()/2-g.getFontMetrics().stringWidth(engSub)/2, img.getHeight()/4);

	    g.setColor(Color.BLUE);
	    g.setFont(dynamicFont2);
	    g.drawString(engApi, img.getWidth()/2-g.getFontMetrics().stringWidth(engApi)/2, img.getHeight()/2);

	    g.setColor(Color.WHITE);
	    g.setFont(dynamicFont3);
	    g.drawString(vieSub, img.getWidth()/2-g.getFontMetrics().stringWidth(vieSub)/2, img.getHeight()*3/4);

		return img;
	}

	private void writeListeningFrame(VideoEncoding videoEncoding, File input, BufferedImage listening1, BufferedImage listening2) throws VideoEncodingException {
		// Set audio stream
		videoEncoding.setCurrentAudio(input.getPath());

		double audioTime = videoEncoding.getAudioTime();
		double duration  = videoEncoding.getCurrentAudioDuration();

		// Write to end of audio
		while (videoEncoding.getAudioTime() - audioTime < duration+2) {
			double curTime = videoEncoding.getAudioTime() - audioTime;
			videoEncoding.writeVideoFrame((curTime/5) % 2 == 0 ? listening1 : listening2);
//			System.out.println("Send Frame vtime="+videoEncoding.getVideoTime()+" atime="+videoEncoding.getAudioTime());
		}
	}

	private void writeApiFrame(VideoEncoding videoEncoding, File input, BufferedImage apiFrame) throws VideoEncodingException {
		// Set audio stream
		videoEncoding.setCurrentAudio(input.getPath());

		double audioTime = videoEncoding.getAudioTime();
		double duration  = videoEncoding.getCurrentAudioDuration();

		// Write to end of audio
		while (videoEncoding.getAudioTime() - audioTime < duration+2) {
			videoEncoding.writeVideoFrame(apiFrame);
//			System.out.println("Send Frame vtime="+videoEncoding.getVideoTime()+" atime="+videoEncoding.getAudioTime());
		}
	}

	@Autowired
	private Text2SpeechService text2SpeechService;

	private void addText(VideoEncoding videoEncoding, String engSub, String engApi, String vieSub, int i, int max) throws Exception {
		// progress
		double progress = (double) i /max;

		// Encode video
		BufferedImage listening1 = createListeningFrame(progress, false);
		BufferedImage listening2 = createListeningFrame(progress, true);

		// Write out video
		BufferedImage apiFrame = createAPIFrame(progress, engSub, engApi, vieSub);

		/// Lan 1 doc nhanh
		// Input
		File input = FileUtil.matchFileName(inFolder, "EngSubFeMale.mp3");

		// Write file
		FileUtil.writeFileContent(input, text2SpeechService.text2Speech(engSub, "en-US_AllisonVoice"));

		// Write listening
		writeListeningFrame(videoEncoding, input, listening1, listening2);

		/// Lan 2 Doc cham
		String engSub2 = Stream.of(engSub.split(" ")).collect(Collectors.joining(". "));

		// Input
		input = FileUtil.matchFileName(inFolder, "EngSubFeMale.Slow.mp3");

		// Write file
		FileUtil.writeFileContent(input, text2SpeechService.text2Speech(engSub2, "en-US_AllisonVoice"));

		// Write listening
		writeListeningFrame(videoEncoding, input, listening1, listening2);

		// Lan 3 Giong nam, doi ngu dieu
		// Input
		input = FileUtil.matchFileName(inFolder, "EngSubMale.mp3");

		// Write file
		FileUtil.writeFileContent(input, text2SpeechService.text2Speech(engSub, "en-US_MichaelV3Voice"));

		// Write api
		writeApiFrame(videoEncoding, input, apiFrame);

		// Lan 4 Tieng viet
		// Input
		input = FileUtil.matchFileName(inFolder, "VieSub.mp3");

		// Write file
		FileUtil.writeFileContent(input, text2SpeechService.text2SpeechFPT(vieSub, "banmai"));

		// Write api
		writeApiFrame(videoEncoding, input, apiFrame);
	}

	/**
	 * Execute video task
	 * @param execute the task execute
	 */
	@Async("threadPoolExecutor")
	public void doVideoTask(List<Sentence> sentences, TaskExecute execute) {
		// The task
		Task task = execute.getTask();

		// Output file
		File output = FileUtil.matchFileName(outFolder, "Output.mp4");

		VideoEncoding videoEncoding = null;
		try {
			// Check input and output folder
			checkInOutFolder();

			// Load source image
			loadImage();

			// Create test
			videoEncoding = new VideoEncoding(output.getPath(), background.getWidth(), background.getHeight());

			// TODO Tinh frame
			
			// Sub frame
			for (int i = 0; i < sentences.size(); i++) {
				Sentence s = sentences.get(i);
				
				String engSub = s.getEngSub();
				String engApi = s.getEngApi();
				String vieSub = s.getVieSub();

				addText(videoEncoding, engSub, engApi, vieSub, i+1, sentences.size());
				execute.setProgress((byte)(i*100/sentences.size()));
			}
		} catch(Exception e) {
			e.printStackTrace();
			task.setError(e.toString());
		} finally {
			try {
				videoEncoding.close();
			} catch (Exception e) {
			}
		}

		//
		System.out.println("Done! Set output to task");

		// Set time consuming
		task.setTimeConsume(System.currentTimeMillis()-execute.getStartMilis());

		// Set the output file
		if (task.getError() == null)
			task.setOutputFile(output.getName());

		// Save task
		taskService.saveResult(execute);
	}

}
