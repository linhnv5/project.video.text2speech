package topica.linhnv5.video.text2speech;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.ibm.cloud.sdk.core.http.HttpConfigOptions;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.text_to_speech.v1.TextToSpeech;

@SpringBootApplication
public class VideoText2SpeechApplication {

	@Value("${google.translate.apikey}")
	private String gooleAPIKey;

	@SuppressWarnings("deprecation")
	@Bean
	public Translate getTranslate() {
		return TranslateOptions.newBuilder().setApiKey(gooleAPIKey).build().getService();
	}

	@Value("${ibm.watson.apikey}")
	private String watsonAPIKey;

	@Value("${ibm.watson.url}")
	private String watsonUrl;

	@Bean
	public TextToSpeech getTextToSpeech() {
		IamAuthenticator authenticator = new IamAuthenticator(watsonAPIKey);

		TextToSpeech textToSpeech = new TextToSpeech(authenticator);
		textToSpeech.setServiceUrl(watsonUrl);

		HttpConfigOptions configOptions = new HttpConfigOptions.Builder()
				.disableSslVerification(true)
				.build();
		textToSpeech.configureClient(configOptions);
				
		return textToSpeech;
	}

	public static void main(String[] args) {
		SpringApplication.run(VideoText2SpeechApplication.class, args);
	}

}
