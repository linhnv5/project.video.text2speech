package topica.linhnv5.video.text2speech;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;

@SpringBootApplication
public class VideoText2SpeechApplication {

	@Value("${google.translate.apikey}")
	private String gooleAPIKey;

	@SuppressWarnings("deprecation")
	@Bean
	public Translate getTranslate() {
		return TranslateOptions.newBuilder().setApiKey(gooleAPIKey).build().getService();
	}

	public static void main(String[] args) {
		SpringApplication.run(VideoText2SpeechApplication.class, args);
	}

}
