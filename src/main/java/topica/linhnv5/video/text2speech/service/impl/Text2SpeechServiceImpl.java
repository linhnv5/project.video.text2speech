package topica.linhnv5.video.text2speech.service.impl;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.ibm.watson.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.text_to_speech.v1.model.GetPronunciationOptions;
import com.ibm.watson.text_to_speech.v1.model.Pronunciation;
import com.ibm.watson.text_to_speech.v1.model.SynthesizeOptions;

import topica.linhnv5.video.text2speech.service.Text2SpeechService;
import topica.linhnv5.video.text2speech.util.HttpUtil;

@Service
public class Text2SpeechServiceImpl implements Text2SpeechService {

	@Autowired
	private TextToSpeech textToSpeech;

	@Override
	public String getPronoun(String text) throws Exception {
		Pronunciation pronunciation = textToSpeech.getPronunciation(new GetPronunciationOptions.Builder(text)
				.format("ipa")
				.voice("en-US_AllisonVoice")
				.build()
			).execute().getResult();

		return Stream.of(pronunciation.getPronunciation().replace("..", " ").replace(".", "").split(" "))
				.collect(Collectors.joining("/ /", "/", "/"));
	}

	@Override
	public byte[] text2Speech(String text, String voice) throws Exception {
		InputStream is = textToSpeech.synthesize(new SynthesizeOptions.Builder()
				.voice(voice)
				.text(text)
				.accept("audio/mp3")
				.build()).execute().getResult();

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] ab = new byte[1024];

		int lent;
		while ((lent = is.read(ab)) > 0)
			bos.write(ab, 0, lent);

		return bos.toByteArray();
	}

	static class ApiRet {
		@SerializedName("async")
		String async;
		
		@SerializedName("error")
		int error;
		
		@SerializedName("message")
		String message;
		
		@SerializedName("request_id")
		String request_id;
	}

	@Value("${fpt.ai.apikey}")
	private String fptAPIKey;
	
	@Override
	public byte[] text2SpeechFPT(String text, String voice) throws Exception {
		URL url = new URL("https://api.fpt.ai/hmi/tts/v5");

		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		conn.setRequestProperty("api-key", fptAPIKey);
		conn.setRequestProperty("speed", "0");
		conn.setRequestProperty("voice", voice);

		conn.setDoOutput(true);

		OutputStream os = conn.getOutputStream();
		os.write(text.getBytes());

		conn.connect();

		StringBuffer buffer = new StringBuffer();

		InputStream is = conn.getInputStream();
		BufferedReader in = new BufferedReader(new InputStreamReader(is, "UTF-8"));
		String str;

		while ((str = in.readLine()) != null)
			buffer.append(str);

		in.close();

		Gson gson = new Gson();

		ApiRet result = gson.fromJson(buffer.toString(), ApiRet.class);

		while (true) {
			try {
				return HttpUtil.sendRequestBinary(result.async, null, null);
			} catch(Exception e) {
			}
			Thread.sleep(1000L);
		}
	}

}
