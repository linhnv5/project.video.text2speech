package topica.linhnv5.video.text2speech.video;

import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.swresample.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.swscale.*;

import java.awt.image.BufferedImage;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacpp.avcodec.AVCodec;
import org.bytedeco.javacpp.avcodec.AVCodecContext;
import org.bytedeco.javacpp.avcodec.AVPacket;
import org.bytedeco.javacpp.avformat.AVFormatContext;
import org.bytedeco.javacpp.avformat.AVIOContext;
import org.bytedeco.javacpp.avformat.AVInputFormat;
import org.bytedeco.javacpp.avformat.AVOutputFormat;
import org.bytedeco.javacpp.avformat.AVStream;
import org.bytedeco.javacpp.avutil.AVDictionary;
import org.bytedeco.javacpp.avutil.AVFrame;
import org.bytedeco.javacpp.avutil.AVRational;
import org.bytedeco.javacpp.swresample.SwrContext;
import org.bytedeco.javacpp.swscale.SwsContext;

/**
 * Encoding video, using ffmpeg lib<br/>
 * Input image frame and audio file
 * @author ljnk975
 */
public class VideoEncoding {

	/**
	 * The output file name
	 */
	private String filename;

	/**
	 * output width
	 */
	private int width;
	
	/**
	 * output height
	 */
	private int height;

	/**
	 * Create video encoding with no sound stream
	 * @param filename
	 * @param width
	 * @param height
	 * @throws VideoEncodingException
	 */
	public VideoEncoding(String filename, int width, int height) throws VideoEncodingException {
		this.filename = filename;
		this.width = width;
		this.height = height;

		this.init();
	}

	/**
	 * @return the filename
	 */
	public String getFilename() {
		return filename;
	}

	/**
	 * @return the width
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * @return the height
	 */
	public int getHeight() {
		return height;
	}

	/**
	 * @return the fps
	 */
	public int getFrameRate() {
		return STREAM_NB_FRAMES;
	}

	private AVOutputFormat fmt;
    private AVFormatContext oc;

    private AVOutputStream videoStream;
    private AVOutputStream audioStream;

    private final int STREAM_NB_FRAMES = 30;
	private final int STREAM_PIX_FMT = AV_PIX_FMT_YUV420P;

	/**
	 * Init ffmpeg libary
	 * @param inStream audio stream input
	 * @throws VideoEncodingException
	 */
	private void init() throws VideoEncodingException {
		int ret;

		/* Format Context */
		oc = new AVFormatContext();

		/* allocate the output media context */
	    ret = avformat_alloc_output_context2(oc, null, null, filename);
	    if (ret < 0)
	        throw new VideoEncodingException("Could not deduce output format from file extension: "+av_err2str(ret));
	    fmt = oc.oformat();

	    /* Add the audio and video streams using the default format codecs
	     * and initialize the codecs. */
	    videoStream = null;
	    audioStream = null;

	    /* Open video stream */
	    if (fmt.video_codec() == AV_CODEC_ID_NONE)
	    	throw new VideoEncodingException("Video Codec None!");
	    videoStream = addVideoStream(fmt.video_codec());
	    if (fmt.audio_codec() == AV_CODEC_ID_NONE)
	    	throw new VideoEncodingException("Audio Codec None!");
	    audioStream = addAudioStream(fmt.audio_codec());

	    /* Now that all the parameters are set, we can open the codecs and allocate the necessary encode buffers. */
	    if (videoStream != null)
	        openVideo();

	    /* Open audio stream */
	    if (audioStream != null)
	        openAudio();

	    /* Print format */
	    av_dump_format(oc, 0, filename, 1);
	    System.err.flush();

	    /* open the output file, if needed */
	    if ((fmt.flags() & AVFMT_NOFILE) == 0) {
	    	AVIOContext pb = new AVIOContext(null);
	        ret = avio_open(pb, filename, AVIO_FLAG_WRITE);
	        if (ret < 0)
	        	throw new VideoEncodingException("Could not open file: "+av_err2str(ret));
	        oc.pb(pb);
	    }

	    /* Write the stream header, if any. */
	    ret = avformat_write_header(oc, new AVDictionary(null));
	    if (ret < 0)
	    	throw new VideoEncodingException("Error occurred when opening output file: "+av_err2str(ret));
	}

	/**
	 * Current audio end
	 */
	private boolean audioEnd = true;

	/**
	 * Create no sound time
	 * @param time time in seconds
	 */
	public void seekAudioTime(double time) {
		AVRational r = new AVRational(); r.num(1); r.den(audioStream.encodeCtx.sample_rate());
		audioStream.samplesCount += time / av_q2d(r);
	}

	/**
	 * Get current audio duration
	 * @return duration of current audio input in seccond
	 */
	public double getCurrentAudioDuration() {
		return inputAudioStream.duration() * av_q2d(inputAudioStream.time_base());
	}

	/**
	 * Check if current audio end
	 * @return 
	 */
	public boolean isCurrentAudioEnd() {
		return audioEnd;
	}

	private AVFormatContext inputAudioCtx;
	private AVStream inputAudioStream;
	private AVCodecContext decodeCtx = new AVCodecContext(null);

	/**
	 * Set current audio file
	 * @param inAudioFile audio file name
	 * @throws VideoEncodingException 
	 */
	public void setCurrentAudio(String inAudioFile) throws VideoEncodingException {
		int ret;

		/* CLose input */
		closeAudioInput();

		/* Open audio file */
		inputAudioCtx = new AVFormatContext(null);

		AVInputFormat inputAVFormat = new AVInputFormat(null);
		ret = avformat_open_input(inputAudioCtx, inAudioFile, inputAVFormat, new AVDictionary(null));
		if (ret < 0)
			throw new VideoEncodingException("Could not open input file "+ inAudioFile+": "+VideoEncoding.av_err2str(ret));

		// Read packets of a media file to get stream information
		ret = avformat_find_stream_info(inputAudioCtx, new AVDictionary(null));
		if (ret < 0)
			throw new VideoEncodingException("avformat_find_stream_info() error: "+VideoEncoding.av_err2str(ret));

		// Type audio
		int type = AVMEDIA_TYPE_AUDIO;

		// In stream
	    int stream_index = av_find_best_stream(inputAudioCtx, type, -1, -1, (AVCodec) null, 0);
	    if (stream_index < 0)
	    	throw new VideoEncodingException("Could not find "+av_get_media_type_string(type)+" stream in input file "+inAudioFile);

        AVStream st = inputAudioCtx.streams(stream_index);

        /* find decoder for the stream */
        AVCodec codec = avcodec_find_decoder(st.codecpar().codec_id());
        if (codec.isNull())
        	throw new VideoEncodingException("Failed to find "+av_get_media_type_string(type)+" codec");

        /* Allocate a codec context for the decoder */
        AVCodecContext decCtx = avcodec_alloc_context3(codec);
        if (decCtx.isNull())
        	throw new VideoEncodingException("Failed to allocate the "+av_get_media_type_string(type)+" codec context");

	    /* Copy codec parameters from input stream to output codec context */
        if (avcodec_parameters_to_context(decCtx, st.codecpar()) < 0)
        	throw new VideoEncodingException("Failed to copy "+av_get_media_type_string(type)+" codec parameters to decoder context\n");

        /* Init the decoders, with or without reference counting */
        if (avcodec_open2(decCtx, codec, (AVDictionary) null) < 0)
        	throw new VideoEncodingException("Failed to open "+av_get_media_type_string(type)+" codec");

        /* Init resample */
        SwrContext swrCtx = audioStream.swrCtx;
        AVCodecContext encCtx = audioStream.encodeCtx;

        /* set options */
        av_opt_set_int(swrCtx, "in_channel_layout",  decCtx.channel_layout(), 0);
        av_opt_set_int(swrCtx, "out_channel_layout", encCtx.channel_layout(), 0);

        av_opt_set_int(swrCtx, "in_sample_rate",  decCtx.sample_rate(), 0);
        av_opt_set_int(swrCtx, "out_sample_rate", encCtx.sample_rate(), 0);

        av_opt_set_sample_fmt(swrCtx, "in_sample_fmt",  decCtx.sample_fmt(), 0);
        av_opt_set_sample_fmt(swrCtx, "out_sample_fmt", encCtx.sample_fmt(), 0);

        /* initialize the resampling context */
        if ((ret = swr_init(swrCtx)) < 0)
        	throw new VideoEncodingException("Failed to initialize the resampling context: "+av_err2str(ret));

        // Set input stream
        inputAudioStream = st;

        // Det decoder
        decodeCtx = decCtx;

        // Set current audio start
		audioEnd = false;
	}

	/**
	 * Close audio input stream
	 */
	private void closeAudioInput() {
		/* CLose input */
		if (inputAudioCtx != null)
			avformat_close_input(inputAudioCtx);
	}

	/**
	 * Close video encoding
	 * @throws VideoEncodingException
	 */
	public void close() throws VideoEncodingException {
		/* Close each codec. */
	    if (videoStream != null)
	        closeVideo();

		if (audioStream != null)
	        closeAudio();

		/* Write the trailer, if any. The trailer must be written before you
	     * close the CodecContexts open when you wrote the header; otherwise
	     * av_write_trailer() may try to use memory that was freed on
	     * av_codec_close(). */
	    av_write_trailer(oc);

		if ((fmt.flags() & AVFMT_NOFILE) > 0)
	        /* Close the output file. */
	        avio_close(oc.pb());

		/* free the stream */
	    avformat_free_context(oc);
	}

	// a wrapper around a single output AVStream
	static class AVOutputStream {
	    AVStream st;
	    AVCodec codec;
	    AVCodecContext encodeCtx;

	    // pts of the next frame that will be generated
	    long nextPts;

	    AVFrame frame;

		int frameCount;
	    int samplesCount;

		SwsContext swsCtx;
	    SwrContext swrCtx;
	};

	public static String av_err2str(int errnum) {
		BytePointer data = new BytePointer(new byte[AV_ERROR_MAX_STRING_SIZE]);
		av_make_error_string(data, AV_ERROR_MAX_STRING_SIZE, errnum);
		return data.getString();
	}

	private int writeFrame(AVFormatContext ctx, AVRational timeBase, AVStream st, AVPacket pkt) {
	    /* rescale output packet timestamp values from codec to stream timebase */
	    pkt.stream_index(st.index());
	    av_packet_rescale_ts(pkt, timeBase, st.time_base());
	    /* Write the compressed frame to the media file. */
	    return av_interleaved_write_frame(ctx, pkt);
	}

	/**
	 * Send frame to encoder
	 * @param st
	 * @param c
	 * @param frame
	 * @throws VideoEncodingException
	 */
	private void sendAVFrame(AVOutputStream stx, AVFrame frame) throws VideoEncodingException {
		int ret;

		AVStream st = stx.st;
		AVCodecContext c = stx.encodeCtx;

		System.out.println("Write frame st="+st.index()+" time="+av_stream_get_end_pts(st) * av_q2d(st.time_base()));

		/* encode the image */
	    ret = avcodec_send_frame(c, frame);
        if (ret < 0)
        	throw new VideoEncodingException("Failed to send frame: "+av_err2str(ret));

        AVPacket pkt = new AVPacket();
        av_init_packet(pkt);
        pkt.data(null);
        pkt.size(0);

        while (ret >= 0) {
            ret = avcodec_receive_packet(c, pkt);
           
            if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF)
                return;

    	    if (ret < 0)
            	throw new VideoEncodingException("Error during encoding: "+av_err2str(ret));

    	    /* Write the compressed frame to the media file. */
    	    writeFrame(oc, c.time_base(), st, pkt);

            av_packet_unref(pkt);
        }

        stx.frameCount++;
	}

	/**************************************************************/
	/* audio output */
	/**
	 * Add new audio stream
	 * @param inStream input audio stream
	 * @return the audio stream
	 * @throws VideoEncodingException
	 */
	private AVOutputStream addAudioStream(int codecId) throws VideoEncodingException {
		int ret;

		AVOutputStream otx = new AVOutputStream();

		/* find the encoder */
		AVCodec codec = avcodec_find_encoder(codecId);
		if (codec.isNull())
			throw new VideoEncodingException("Could not find encoder for " + avcodec_get_name(codecId));
		otx.codec = codec;

		AVCodecContext c;
	    if ((c = avcodec_alloc_context3(codec)).isNull())
	    	throw new VideoEncodingException("Failed to allocate codec context");
	    otx.encodeCtx = c;

		System.out.println("Add audio stream, codec="+codec.name().getString());

		AVStream st = avformat_new_stream(oc, codec);
		if (st == null)
			throw new VideoEncodingException("Could not allocate stream");
		otx.st = st;

		st.id(oc.nb_streams() - 1);

		/* put sample parameters */
	    c.bit_rate(64000);

	    /* check that the encoder supports s16 pcm input */
	    c.sample_fmt(AV_SAMPLE_FMT_FLTP);

	    /* select other audio parameters supported by the encoder */
	    c.sample_rate(44100);
	    c.channel_layout(AV_CH_LAYOUT_STEREO);
	    c.channels(av_get_channel_layout_nb_channels(c.channel_layout()));

		/* Some formats want stream headers to be separate. */
		if ((oc.oformat().flags() & AVFMT_GLOBALHEADER) > 0)
			c.flags(c.flags() | AV_CODEC_FLAG_GLOBAL_HEADER);

	    /* open it */
	    ret = avcodec_open2(c, codec, new AVDictionary(null));
	    if (ret < 0)
	    	throw new VideoEncodingException("Could not open audio codec: "+av_err2str(ret));

	    /* Codec */
	    ret = avcodec_parameters_from_context(st.codecpar(), c);
	    if (ret < 0)
	    	throw new VideoEncodingException("Could not copy codec parameters: "+av_err2str(ret));

	    return otx;
	}

	private AVFrame allocAudioFrame(int sample_fmt, long channel_layout, int sample_rate, int nb_samples) throws VideoEncodingException {
		int ret;

		AVFrame frame = av_frame_alloc();

		if (frame.isNull())
			throw new VideoEncodingException("Error allocating an audio frame\n");

		frame.format(sample_fmt);
		frame.channel_layout(channel_layout);
		frame.sample_rate(sample_rate);
		frame.nb_samples(nb_samples);

		if (nb_samples != 0) {
			ret = av_frame_get_buffer(frame, 0);
			if (ret < 0)
				throw new VideoEncodingException("Error allocating an audio buffer\n");
		}

		return frame;
	}

	/**
	 * Open audio stream
	 * @throws VideoEncodingException
	 */
	private void openAudio() throws VideoEncodingException {
		AVOutputStream ost = audioStream;
		AVCodecContext c = ost.encodeCtx;

	    int nb_samples;

	    if ((c.codec().capabilities() & AV_CODEC_CAP_VARIABLE_FRAME_SIZE) > 0)
	        nb_samples = 10000;
	    else
	        nb_samples = c.frame_size();

	    ost.frame = allocAudioFrame(c.sample_fmt(), c.channel_layout(), c.sample_rate(), nb_samples);

	    /* create resampler context */
        ost.swrCtx = swr_alloc();

        if (ost.swrCtx.isNull())
        	throw new VideoEncodingException("Could not allocate resampler context");
	}

    /**
     * Send audio frame to encode
     * @param frame the video frame
     * @throws VideoEncodingException
     */
    private void audioSendFrame(AVFrame frame) throws VideoEncodingException {
        sendAVFrame(audioStream, frame);
    }

    /**
	 * Write audio frame
	 * @throws VideoEncodingException
	 */
    private void writeAudioFrame() throws VideoEncodingException {
    	int ret;

    	AVStream st    = inputAudioStream;
	    AVFrame frame  = audioStream.frame;

		AVPacket pkt = new AVPacket();

		do {
	        // Return the next frame of a stream.
			if (av_read_frame(inputAudioCtx, pkt) < 0) {
				audioEnd = true;
				return;
			}
		} while (pkt.stream_index() != st.index());

		// Decoder
	    /* send the packet with the compressed data to the decoder */
	    ret = avcodec_send_packet(decodeCtx, pkt);
	    if (ret < 0)
	        throw new VideoEncodingException("Error submitting the packet to the decoder");
	    av_packet_unref(pkt);

	    AVCodecContext c   = audioStream.encodeCtx;
	    SwrContext swrCtx  = audioStream.swrCtx;

	    int nb_samples;

	    if ((c.codec().capabilities() & AV_CODEC_CAP_VARIABLE_FRAME_SIZE) > 0)
	        nb_samples = 10000;
	    else
	        nb_samples = c.frame_size();

		AVFrame tframe = allocAudioFrame(c.sample_fmt(), c.channel_layout(), c.sample_rate(), nb_samples);

		/* read all the output frames (in general there may be any number of them */
	    while (ret >= 0) {
	        ret = avcodec_receive_frame(decodeCtx, tframe);

	        if (ret == AVERROR_EAGAIN() || ret == AVERROR_EOF)
	        	return;

	        if (ret < 0)
	            throw new VideoEncodingException("Error during decoding");

		    /* convert samples from native format to destination codec format, using the resampler */
	    	/* compute destination number of samples */
//	    	int dst_nb_samples = (int) av_rescale_rnd(swr_get_delay(swrCtx, c.sample_rate()) + tframe.nb_samples(), c.sample_rate(), c.sample_rate(), AV_ROUND_UP);

	    	/* convert to destination format */
	        ret = swr_convert(swrCtx, frame.data(), frame.nb_samples(), tframe.data(), tframe.nb_samples());
	        if (ret < 0)
	        	throw new VideoEncodingException("Error while converting");

	        AVRational r = new AVRational(); r.num(1); r.den(c.sample_rate());
	        frame.pts(av_rescale_q(audioStream.samplesCount, r, c.time_base()));
	        audioStream.samplesCount += frame.nb_samples();

	        /* send the frame for encoding */
	        audioSendFrame(frame);
	    }
	    
	    av_frame_free(tframe);
    }
   
    /**
     * Get audio stream time
     * @return audio stream time
     */
    public double getAudioTime() {
        return av_stream_get_end_pts(audioStream.st) * av_q2d(audioStream.st.time_base());
    }

    /**
     * Close audio stream
     * @throws VideoEncodingException 
     */
    private void closeAudio() throws VideoEncodingException {
    	// flush video
		audioSendFrame(null);

		closeAudioInput();
    	closeStream(audioStream);
	}

	/**************************************************************/
	/* video output */
	/**
	 * Add new video stream.
	 * @param codecId video codec id
	 * @return the video stream
	 * @throws VideoEncodingException
	 */
	private AVOutputStream addVideoStream(int codecId) throws VideoEncodingException {
		int ret;

		AVOutputStream otx = new AVOutputStream();

		/* find the encoder */
		AVCodec codec = avcodec_find_encoder(codecId);
		if (codec.isNull())
			throw new VideoEncodingException("Could not find encoder for " + avcodec_get_name(codecId));
		otx.codec = codec;

		AVCodecContext c;
	    if ((c = avcodec_alloc_context3(codec)).isNull())
	    	throw new VideoEncodingException("Failed to allocate codec context");
	    otx.encodeCtx = c;

		System.out.println("Add video stream, codec="+codec.name().getString());

		AVStream st = avformat_new_stream(oc, codec);
		if (st == null)
			throw new VideoEncodingException("Could not allocate stream");
		otx.st = st;

		st.id(oc.nb_streams() - 1);

		/* Bit Rate */
		c.bit_rate(400000);

		/* Resolution must be a multiple of two. */
		c.width(width);
		c.height(height);

		/*
		 * timebase: This is the fundamental unit of time (in seconds) in terms of which
		 * frame timestamps are represented. For fixed-fps content, timebase should be
		 * 1/framerate and timestamp increments should be identical to 1.
		 */
		c.time_base().den(STREAM_NB_FRAMES);
		c.time_base().num(1);
		c.gop_size(12); /* emit one intra frame every twelve frames at most */
		c.pix_fmt(STREAM_PIX_FMT);
		c.max_b_frames(2);

		if (c.codec_id() == AV_CODEC_ID_MPEG1VIDEO) {
			/*
			 * Needed to avoid using macroblocks in which some coeffs overflow. This does
			 * not happen with normal video, it just happens here as the motion of the
			 * chroma plane does not match the luma plane.
			 */
			c.mb_decision(2);
		}

		if (c.codec_id() == AV_CODEC_ID_H264)
	        av_opt_set(c, "preset", "ultrafast", 0);

		/* Some formats want stream headers to be separate. */
		if ((oc.oformat().flags() & AVFMT_GLOBALHEADER) > 0)
			c.flags(c.flags() | AV_CODEC_FLAG_GLOBAL_HEADER);

	    /* open it */
	    ret = avcodec_open2(c, codec, new AVDictionary(null));
	    if (ret < 0)
	    	throw new VideoEncodingException("Could not open codec: "+av_err2str(ret));

	    /* Codec */
	    ret = avcodec_parameters_from_context(st.codecpar(), c);
	    if (ret < 0)
	    	throw new VideoEncodingException("Could not copy codec parameters: "+av_err2str(ret));

	    return otx;
	}

	private AVFrame allocVideoFrame(int pix_fmt, int width, int height) throws VideoEncodingException {
	    int ret;

	    AVFrame picture;

	    picture = av_frame_alloc();
	    if (picture.isNull())
	        return null;

	    picture.format(pix_fmt);
	    picture.width(width);
	    picture.height(height);

	    /* allocate the buffers for the frame data */
	    ret = av_frame_get_buffer(picture, 32);
	    if (ret < 0)
	    	throw new VideoEncodingException("Could not allocate frame data.\n");

	    return picture;
	}

	/**
	 * Open video stream
	 * @throws VideoEncodingException
	 */
    private void openVideo() throws VideoEncodingException {
	    AVCodecContext c = videoStream.encodeCtx;

	    /* Allocate the encoded raw picture. */
	    videoStream.frame = allocVideoFrame(c.pix_fmt(), c.width(), c.height());
	}

    /**
     * Send video frame to encode
     * @param frame the video frame
     * @throws VideoEncodingException
     */
    private void videoSendFrame(AVFrame frame) throws VideoEncodingException {
    	sendAVFrame(videoStream, frame);
    }

    /**
     * Write next video frame in argb format
     * @param data rgb data
     * @throws VideoEncodingException
     */
	public void writeVideoFrame(PointerPointer<BytePointer> data) throws VideoEncodingException {
	    AVCodecContext c = videoStream.encodeCtx;
	    AVFrame frame = videoStream.frame;

	    // Check audio time
	    while (audioStream != null && !audioEnd && getAudioTime() < getVideoTime())
	    	writeAudioFrame();

	    // 
	    if (videoStream.swsCtx == null)
	    	videoStream.swsCtx = sws_getContext(c.width(), c.height(), AV_PIX_FMT_0RGB32, c.width(), c.height(), AV_PIX_FMT_YUV420P, SWS_BICUBIC, null, null, (DoublePointer) null);

	    SwsContext swsCtx = videoStream.swsCtx;

	    IntPointer inLinesize = new IntPointer(new int[] {4 * c.width()});

        // From RGB to YUV
        sws_scale(swsCtx, data, inLinesize, 0, c.height(), frame.data(), frame.linesize());

        // 
        frame.pts(videoStream.nextPts++);

        // Add frames
	    videoSendFrame(frame);
	}

    /**
     * Write next video frame in argb format
     * @param rgb rgb data
     * @throws VideoEncodingException
     */
	public void writeVideoFrame(int[] rgb) throws VideoEncodingException {
		writeVideoFrame(new PointerPointer<BytePointer>(rgb));
	}

	/**
	 * Write next video frame
	 * @param image the next video frame image
	 * @throws VideoEncodingException
	 */
	public void writeVideoFrame(BufferedImage image) throws VideoEncodingException {
		int[] rgb = new int[image.getWidth()*image.getHeight()];
		image.getRGB(0, 0, image.getWidth(), image.getHeight(), rgb, 0, image.getWidth());
		writeVideoFrame(rgb);
	}

	/**
	 * Get video stream time
	 * @return video stream time
	 */
	public double getVideoTime() {
        return av_stream_get_end_pts(videoStream.st) * av_q2d(videoStream.st.time_base());
	}

	/**
	 * Close video stream
	 * @throws VideoEncodingException
	 */
	private void closeVideo() throws VideoEncodingException {
		// flush video
		videoSendFrame(null);

		closeStream(videoStream);
	}

	private void closeStream(AVOutputStream ost) {
		if (ost.encodeCtx != null)
			avcodec_free_context(ost.encodeCtx);

		if (ost.frame != null)
			av_frame_free(ost.frame);

	    if (ost.swsCtx != null)
	    	sws_freeContext(ost.swsCtx);

	    if (ost.swrCtx != null)
	    	swr_free(ost.swrCtx);
	}

	/**
	 * Get current frame count
	 * @return the frame count
	 */
	public int getFrameCount() {
		return videoStream.frameCount;
	}

}
