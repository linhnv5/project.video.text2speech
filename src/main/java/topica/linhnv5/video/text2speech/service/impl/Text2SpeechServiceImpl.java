package topica.linhnv5.video.text2speech.service.impl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import topica.linhnv5.video.text2speech.service.Text2SpeechService;
import topica.linhnv5.video.text2speech.util.HttpUtil;

@Service
public class Text2SpeechServiceImpl implements Text2SpeechService {

	@Override
	public byte[] text2Speech(String text, String voice) throws Exception {
		Map<String, Object> params = new HashMap<String, Object>();

		params.put("text",     text);
		params.put("voice",    voice);
		params.put("download", true);
		params.put("accept",   "audio/mp3");
		
		return HttpUtil.sendRequestBinary("https://text-to-speech-demo.ng.bluemix.net/api/v1/synthesize", params, null);
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

	@Override
	public byte[] text2SpeechFPT(String text, String voice) throws Exception {
		URL url = new URL("https://api.fpt.ai/hmi/tts/v5");

		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		conn.setRequestProperty("api-key", "MdmHPkF0Kj4EA0ap6sEPVbjA7enNevJV");
		conn.setRequestProperty("speed", "-2");
		conn.setRequestProperty("voice", "banmai");

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
