package topica.linhnv5.video.text2speech.service.impl;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import topica.linhnv5.video.text2speech.model.Conversation;
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
	private BufferedImage number;

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
			number       = ImageIO.read(new File(workingFolder+"1x"+File.separator+"number.png"));
		}
	}

	/**
	 * Split string if string width out maxw
	 * @return the string has splited
	 */
	public String[] split(String s, int maxw, FontMetrics metric) {
		String[] as = s.split(" ");
		List<String> list = new ArrayList<String>();

		StringBuilder buff = new StringBuilder();
		for (int i = 0; i < as.length; i++) {
			if (buff.length() > 0)
				buff.append(" ");
			if (metric.stringWidth(buff.toString()+as[i]) > maxw) {
				list.add(buff.toString()); buff.setLength(0);
			}
			buff.append(as[i]);
		}
		if (buff.length() > 0)
			list.add(buff.toString());
	
		return list.toArray(new String[0]);
	}

	/**
	 * Draw background frame
	 * @param g
	 * @param i
	 * @param max
	 */
	private void drawBack(Graphics2D g, int i, int max) {
		// progress y
		int fromY = 194; int toY = 876; int numb = toY - fromY;

		// Font
	    Font dynamicFont1 = new Font("lato", Font.BOLD,  63);

	    // back ground
		g.drawImage(background, 0, 0, null);
		g.drawImage(sound, 0, 0, null);
		g.drawImage(time, 0, 0, null);

		// progress bar 875-195
		int y = fromY+(int)(numb*(max-i)/max);
		g.setClip(0, y, background.getWidth(), background.getHeight()-y);
		g.drawImage(time2, 0, 0, null);

		g.setClip(0, 0, background.getWidth(), background.getHeight());

		// o so
		g.drawImage(number, 0, 0, null);
		
		// so
		String number = String.valueOf(i);
	    g.setColor(new Color(247, 173, 70));
	    g.setFont(dynamicFont1);
	    g.drawString(number, 1785-g.getFontMetrics().stringWidth(number)/2, 915-g.getFontMetrics().getHeight()/2+g.getFontMetrics().getAscent());
	}

	private BufferedImage createListeningFrame(float alpha, int i, int max) throws Exception {
		// Create outpur image
		BufferedImage img = new BufferedImage(background.getWidth(), background.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);

		// Get the graphics
		Graphics2D g = (Graphics2D) img.getGraphics();

		// back ground
		drawBack(g, i, max);

        // Set the Graphics composite to Alpha
//        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Listening
		int w = listening.getWidth()/2; int h = listening.getHeight()/2;
		g.drawImage(listening, background.getWidth()/2-w/2, background.getHeight()/2-h/2, w, h, null);
		
		return img;
	}

	private BufferedImage createAPIFrame(String engSub, String engApi, String vieSub, float alpha, int i, int max) throws Exception {
		// Create outpur image
		BufferedImage img = new BufferedImage(background.getWidth(), background.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);

		// Get the graphics
		Graphics2D g = (Graphics2D) img.getGraphics();

		// back ground
		drawBack(g, i, max);

        // Set the Graphics composite to Alpha
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Listening
		// Font
	    Font dynamicFont1 = new Font("lato", Font.BOLD,  63);
	    Font dynamicFont2 = new Font("lato",  Font.PLAIN, 60);
	    Font dynamicFont3 = new Font("lato",  Font.PLAIN, 60);

	    g.setColor(new Color(247, 173, 70));
	    g.setFont(dynamicFont1);

	    String[] as = split(engSub, 1800, g.getFontMetrics());
	    for (i = 0; i < as.length; i++)
	    	g.drawString(as[i], img.getWidth()/2-g.getFontMetrics().stringWidth(as[i])/2, img.getHeight()/4+g.getFontMetrics().getHeight()*i);

	    g.setColor(Color.BLUE);
	    g.setFont(dynamicFont2);
	    g.drawString(engApi, img.getWidth()/2-g.getFontMetrics().stringWidth(engApi)/2, img.getHeight()/2);

	    g.setColor(Color.WHITE);
	    g.setFont(dynamicFont3);

	    as = split(vieSub, 1800, g.getFontMetrics());
	    for (i = 0; i < as.length; i++)
	    	g.drawString(as[i], img.getWidth()/2-g.getFontMetrics().stringWidth(as[i])/2, img.getHeight()*3/4+g.getFontMetrics().getHeight()*i);

		return img;
	}

	private void writeListeningFrame(VideoEncoding videoEncoding, File input, BufferedImage listening, double audioTime, double delayIn, double delayOut) throws VideoEncodingException {
		double startTime = videoEncoding.getAudioTime(), duration;

		// Start and duration
		startTime = videoEncoding.getAudioTime();
		duration  = delayIn;

		// 1s delay
		while (videoEncoding.getAudioTime() - startTime < duration)
			videoEncoding.writeVideoFrame(listening);

		// Set audio stream
		videoEncoding.setCurrentAudio(input.getPath());

		// Start and duration
		startTime = videoEncoding.getAudioTime();
		duration  = videoEncoding.getCurrentAudioDuration();

		// Write to end of audio
		while (videoEncoding.getAudioTime() - startTime < duration)
			videoEncoding.writeVideoFrame(listening);

		// Start and duration
		startTime = videoEncoding.getAudioTime();
		duration  = delayOut;

		// 1s delay
		while (videoEncoding.getAudioTime() - startTime < duration)
			videoEncoding.writeVideoFrame(listening);
	}

	private void writeApiFrame(VideoEncoding videoEncoding, File input, BufferedImage apiFrame, double delayIn, double delayOut) throws VideoEncodingException {
		double startTime = videoEncoding.getAudioTime(), duration;

		// Start and duration
		startTime = videoEncoding.getAudioTime();
		duration  = delayIn;

		// 2s delay
		while (videoEncoding.getAudioTime() - startTime < duration)
			videoEncoding.writeVideoFrame(apiFrame);

		// Set audio stream
		videoEncoding.setCurrentAudio(input.getPath());

		// Start and duration
		startTime = videoEncoding.getAudioTime();
		duration  = videoEncoding.getCurrentAudioDuration();

		// Write to end of audio
		while (videoEncoding.getAudioTime() - startTime < duration)
			videoEncoding.writeVideoFrame(apiFrame);

		// Start and duration
		startTime = videoEncoding.getAudioTime();
		duration  = delayOut;

		// 2s delay
		while (videoEncoding.getAudioTime() - startTime < duration)
			videoEncoding.writeVideoFrame(apiFrame);
	}

	@Autowired
	private Text2SpeechService text2SpeechService;

	private String createSlowListening(String engSub) {
		return Stream.of(engSub.replaceAll("[,.?!]", " ").split(" "))
				.map(s -> s.equals("") ? "" : s.equalsIgnoreCase("i") || s.equalsIgnoreCase("a") ? s+": " : s+". ")
				.collect(Collectors.joining());
//		return engSub.replaceAll("[,.?!]", " ").replace(" ",  ". ");		
	}

	private void addText(VideoEncoding videoEncoding, String engSub, String engApi, String vieSub, int i, int max) throws Exception {
		// Encode video
		BufferedImage listening = createListeningFrame(1.0F, i, max);

		// Write out video
		BufferedImage apiFrame = createAPIFrame(engSub, engApi, vieSub, 1.0F, i, max);

		/// Lan 1 doc nhanh
		// Input
		File input = FileUtil.matchFileName(inFolder, "EngSubFeMale.mp3");

		// Write file
		FileUtil.writeFileContent(input, text2SpeechService.text2Speech(engSub, "en-US_AllisonVoice"));

		// Audio time
		double audioTime = videoEncoding.getAudioTime();

		// Write listening
		writeListeningFrame(videoEncoding, input, listening, audioTime, 1.0, 2.0);

		/// Lan 2 Doc cham
		String engSub2 = createSlowListening(engSub);

		// Input
		input = FileUtil.matchFileName(inFolder, "EngSubFeMale.Slow.mp3");

		// Write file
		FileUtil.writeFileContent(input, text2SpeechService.text2Speech(engSub2, "en-US_AllisonVoice"));

		// Write listening
		writeListeningFrame(videoEncoding, input, listening, audioTime, 0.0, 1.0);

		// Lan 3 Giong nam, doi ngu dieu
		// Input
		input = FileUtil.matchFileName(inFolder, "EngSubMale.mp3");

		// Write file
		FileUtil.writeFileContent(input, text2SpeechService.text2Speech(engSub, "en-US_MichaelV3Voice"));

		// Write api
		writeApiFrame(videoEncoding, input, apiFrame, 1.0, 2.0);

		// Lan 4 Tieng viet
		// Input
		input = FileUtil.matchFileName(inFolder, "VieSub.mp3");

		// Write file
		FileUtil.writeFileContent(input, text2SpeechService.text2SpeechFPT(vieSub, "banmai"));

		// Write api
		writeApiFrame(videoEncoding, input, apiFrame, 0.0, 1.0);
	}

	/**
	 * Execute video task
	 * @param execute the task execute
	 */
	@Async("threadPoolExecutor")
	public void doVideoTask(Conversation conversation, TaskExecute execute) {
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

			// Sub frame
			for (int i = 0, size = conversation.getListOfSentences().size(); i < size; i++) {
				Sentence s = conversation.getListOfSentences().get(i);

				String engSub = s.getEngSub();
				String engApi = s.getEngApi();
				String vieSub = s.getVieSub();

				addText(videoEncoding, engSub, engApi, vieSub, i+1, size);
				execute.setProgress((byte)(i*100/size));
				System.out.println("Task progress: "+execute.getProgress());
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
