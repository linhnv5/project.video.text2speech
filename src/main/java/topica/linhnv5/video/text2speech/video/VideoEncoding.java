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
	    	System.out.println("Open input");
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

	    inputAudioStream = inputAudioCtx.streams(stream_index);

        /* find decoder for the stream */
        AVCodec codec = avcodec_find_decoder(inputAudioStream.codecpar().codec_id());
        if (codec.isNull())
        	throw new VideoEncodingException("Failed to find "+av_get_media_type_string(type)+" codec");

        /* Allocate a codec context for the decoder */
        decodeCtx = avcodec_alloc_context3(codec);
        if (decodeCtx.isNull())
        	throw new VideoEncodingException("Failed to allocate the "+av_get_media_type_string(type)+" codec context");

	    /* Copy codec parameters from input stream to output codec context */
        if (avcodec_parameters_to_context(decodeCtx, inputAudioStream.codecpar()) < 0)
        	throw new VideoEncodingException("Failed to copy "+av_get_media_type_string(type)+" codec parameters to decoder context\n");

        /* Init the decoders, with or without reference counting */
        if (avcodec_open2(decodeCtx, codec, (AVDictionary) null) < 0)
        	throw new VideoEncodingException("Failed to open "+av_get_media_type_string(type)+" codec");

        /* Init resample */
        AVCodecContext encCtx = audioStream.encodeCtx;
        AVCodecContext decCtx = decodeCtx;
        SwrContext swrCtx     = audioStream.swrCtx;

        /* set options */
        av_opt_set_int       (swrCtx, "in_channel_count",   decCtx.channels(),     0);
        av_opt_set_int       (swrCtx, "in_sample_rate",     decCtx.sample_rate(),  0);
        av_opt_set_sample_fmt(swrCtx, "in_sample_fmt",      decCtx.sample_fmt(),   0);
        av_opt_set_int       (swrCtx, "out_channel_count",  encCtx.channels(),     0);
        av_opt_set_int       (swrCtx, "out_sample_rate",    encCtx.sample_rate(),  0);
        av_opt_set_sample_fmt(swrCtx, "out_sample_fmt",     encCtx.sample_fmt(),   0);

        /* Open the resampler with the specified parameters. */
        if ((ret = swr_init(swrCtx)) < 0)
            throw new VideoEncodingException("Could not open resample context: "+av_err2str(ret));

		//
        int nb_samples = (decCtx.codec().capabilities() & AV_CODEC_CAP_VARIABLE_FRAME_SIZE) > 0 ?  10000 : decCtx.frame_size();

	    /* tmp Frame */
		audioStream.tmpFrame = allocAudioFrame(decCtx.sample_fmt(), decCtx.channel_layout(), decCtx.sample_rate(), nb_samples);

        // Set current audio start
		audioEnd = false;
	}

	/**
	 * Close audio input stream
	 * @throws VideoEncodingException 
	 */
	private void closeAudioInput() throws VideoEncodingException {
        /* CLose input */
		if (inputAudioCtx != null) {
			avformat_close_input(inputAudioCtx);
			inputAudioCtx = null;
		}
		/* TMP Frame */
		if (audioStream.tmpFrame != null) {
			av_frame_free(audioStream.tmpFrame);
			audioStream.tmpFrame = null;
		}
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

        /* Close the output file. */
	    if ((fmt.flags() & AVFMT_NOFILE) == 0) {
			System.out.println("Close output");
			avio_closep(oc.pb());
		}

		/* free the stream */
	    avformat_free_context(oc);
	}

	/**
	 * a wrapper around a single output AVStream
	 * @author ljnk975
	 *
	 */
	static class AVOutputStream {
		// Param of stream
	    AVStream st;
	    AVCodec codec;
	    AVCodecContext encodeCtx;

	    // pts of the next frame that will be generated
	    long nextPts;

	    // Next frame of stream
	    AVFrame frame;

	    // Temp frame
	    AVFrame tmpFrame;

	    // frame count and sample rate for video and audio stream
		int frameCount;
	    int samplesCount;
	    
	    // sws context
		SwsContext swsCtx;

		// Resample context
	    SwrContext swrCtx;
	};

	/**
	 * Get AV Error String
	 * @param errnum error code
	 * @return error string
	 */
	public static String av_err2str(int errnum) {
		BytePointer data = null;
		try {
			data = new BytePointer(AV_ERROR_MAX_STRING_SIZE);
			av_make_error_string(data, AV_ERROR_MAX_STRING_SIZE, errnum);
			return data.getString();
		} finally {
			try {
				data.deallocate();
			} catch(Exception e) {
			}
		}
	}

	/**
	 * Write frame to endcoder
	 * @param ctx
	 * @param timeBase
	 * @param st
	 * @param pkt
	 * @return
	 */
	private int writeAVPacket(AVFormatContext ctx, AVRational timeBase, AVStream st, AVPacket pkt) {
		/* Set stream index */
	    pkt.stream_index(st.index());
	    /* rescale output packet timestamp values from codec to stream timebase */
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
	private void encodeAVFrame(AVStream st, AVCodecContext c, AVFrame frame) throws VideoEncodingException {
		int ret;

		// Print
//		System.out.println("WriteFrame st="+st.index()+" time="+av_stream_get_end_pts(st) * av_q2d(st.time_base()));

		/* encode the image */
        if ((ret = avcodec_send_frame(c, frame)) < 0)
        	throw new VideoEncodingException("Failed to send frame: "+av_err2str(ret));

        // Packet receiver
        AVPacket pkt = new AVPacket();
        av_init_packet(pkt);
        pkt.data(null);
        pkt.size(0);

        // loop encoder
        while ((ret = avcodec_receive_packet(c, pkt)) == 0) {
    	    /* Write the compressed frame to the media file. */
    	    writeAVPacket(oc, c.time_base(), st, pkt);

    	    /* Un ref packet */
            av_packet_unref(pkt);
        }

        // Error handle
        if (ret != AVERROR_EAGAIN() && ret != AVERROR_EOF)
        	throw new VideoEncodingException("Error during encoding: "+av_err2str(ret));
	}

	/**************************************************************/
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

	/**
	 * Alloc new Audio Frame
	 * @param sample_fmt
	 * @param channel_layout
	 * @param sample_rate
	 * @param nb_samples
	 * @return
	 * @throws VideoEncodingException
	 */
	private AVFrame allocAudioFrame(int sample_fmt, long channel_layout, int sample_rate, int nb_samples) throws VideoEncodingException {
		int ret;

		AVFrame frame = av_frame_alloc();

		if (frame.isNull())
			throw new VideoEncodingException("Error allocating an audio frame\n");

		frame.format(sample_fmt);
		frame.channel_layout(channel_layout);
		frame.sample_rate(sample_rate);
		frame.nb_samples(nb_samples);

		if ((ret = av_frame_get_buffer(frame, 0)) < 0)
			throw new VideoEncodingException("Error allocating an audio buffer: "+av_err2str(ret));

		return frame;
	}

	/**
	 * Open audio stream
	 * @throws VideoEncodingException
	 */
	private void openAudio() throws VideoEncodingException {
        /* Create a resampler context for the conversion. */
		audioStream.swrCtx = swr_alloc();

        // Error
        if (audioStream.swrCtx.isNull())
        	throw new VideoEncodingException("Could not allocate resample context");

		AVCodecContext c  = audioStream.encodeCtx;

		//
        int nb_samples = (c.codec().capabilities() & AV_CODEC_CAP_VARIABLE_FRAME_SIZE) > 0 ?  10000 : c.frame_size();
        
	    /* Frame */
		audioStream.frame    = allocAudioFrame(c.sample_fmt(), c.channel_layout(), c.sample_rate(), nb_samples);
	}

	/**
	 * Write audio frame
	 * @throws VideoEncodingException
	 */
    private void encodeAudioFrame(AVOutputStream stx) throws VideoEncodingException {
	    AVCodecContext c   = stx.encodeCtx;
	    AVFrame frame      = stx.frame;

        /* Convert pts */
        AVRational r = new AVRational(); r.num(1); r.den(c.sample_rate());
        frame.pts(av_rescale_q(audioStream.samplesCount, r, c.time_base()));
        audioStream.samplesCount += frame.nb_samples();

        /* send the frame for encoding */
        encodeAVFrame(stx.st, stx.encodeCtx, frame);
    }

	/**
	 * Resample frame to output
	 * @throws VideoEncodingException
	 */
	private void resampleAudioFrame(AVOutputStream stx, AVFrame tmpFrame) throws VideoEncodingException {
    	int ret;

    	if (swr_is_initialized(stx.swrCtx) == 0)
    		return;

    	/* write to swr buffer */
    	if (tmpFrame != null) {
        	if ((ret = swr_convert(stx.swrCtx, null, 0, tmpFrame.data(), tmpFrame.nb_samples())) < 0)
        		throw new VideoEncodingException("Error while converting: "+av_err2str(ret));
    	}

    	// write
        while (swr_get_delay(stx.swrCtx, stx.encodeCtx.sample_rate()) >= stx.frame.nb_samples()) {
        	if ((ret = swr_convert(stx.swrCtx, stx.frame.data(), stx.frame.nb_samples(), null, 0)) < 0)
        		throw new VideoEncodingException("Error while converting: "+av_err2str(ret));
        	// Call endcode
        	encodeAudioFrame(stx);
        }

        // 
    	int offset;
    	if (tmpFrame == null && (offset = (int) swr_get_delay(stx.swrCtx, stx.encodeCtx.sample_rate())) > 0) {
        	if ((ret = swr_convert(stx.swrCtx, stx.frame.data(), stx.frame.nb_samples(), null, 0)) < 0)
        		throw new VideoEncodingException("Error while converting: "+av_err2str(ret));
        	//
        	av_samples_set_silence(stx.frame.data(), offset, stx.frame.nb_samples()-offset, stx.frame.channels(), stx.frame.format());
        	// Call endcode
        	encodeAudioFrame(stx);
    	}
	}

    /**
     * Write no sound audio frame
     * @throws VideoEncodingException 
     */
	private void writeSilentAudioFrame(AVOutputStream stx) throws VideoEncodingException {
    	av_samples_set_silence(stx.frame.data(), 0, stx.frame.nb_samples(), stx.frame.channels(), stx.frame.format());
    	encodeAudioFrame(stx);
	}

	/**
	 * Write audio frame
	 * @throws VideoEncodingException
	 */
    private void writeNextAudioFrame() throws VideoEncodingException {
    	int ret;

    	// if audio end then send no sound
		if (audioEnd) {
			writeSilentAudioFrame(audioStream);
			return;
		}

		//
    	AVStream st        = inputAudioStream;
		AVPacket pkt       = new AVPacket();

        // Return the next frame of a stream.
		while ((ret = av_read_frame(inputAudioCtx, pkt)) == 0 && pkt.stream_index() != st.index());

		// Error handle
		if (ret < 0) {
			// Flush swr
			audioEnd = true;
			resampleAudioFrame(audioStream, null);
			return;
		}

		// Decoder
	    /* send the packet with the compressed data to the decoder */
	    ret = avcodec_send_packet(decodeCtx, pkt);
	    if (ret < 0)
	        throw new VideoEncodingException("Error submitting the packet to the decoder");
	    av_packet_unref(pkt);

	    // TMP
		AVFrame frame     = audioStream.tmpFrame;

	    /* read all the output frames (in general there may be any number of them */
	    while ((ret = avcodec_receive_frame(decodeCtx, frame)) == 0)
	    	resampleAudioFrame(audioStream, frame);

	    // Error handle
        if (ret != AVERROR_EAGAIN() && ret != AVERROR_EOF)
        	throw new VideoEncodingException("Error during decoding");
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
    	// flush audio
        encodeAVFrame(audioStream.st, audioStream.encodeCtx, null);

        // Close stream
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

	    /* sws context */
    	videoStream.swsCtx = sws_getContext(c.width(), c.height(), AV_PIX_FMT_0RGB32, c.width(), c.height(), AV_PIX_FMT_YUV420P, SWS_BICUBIC, null, null, (DoublePointer) null);

    	/* Allocate the encoded raw picture. */
	    videoStream.frame = allocVideoFrame(c.pix_fmt(), c.width(), c.height());
	    
	    /* Current video */
	    current = new BufferedImage(c.width(), c.height(), BufferedImage.TYPE_4BYTE_ABGR);
	}

    /**
     * 
     * @param stx
     * @throws VideoEncodingException
     */
    private void writeVideoFrame(AVOutputStream stx) throws VideoEncodingException {
    	// 
		stx.frame.pts(stx.nextPts++);

		// Frame count
		stx.frameCount++;

        // Add frames
    	encodeAVFrame(stx.st, stx.encodeCtx, stx.frame);

	    // Check audio time
	    while (audioStream != null && getAudioTime() < getVideoTime())
	    	writeNextAudioFrame();
    }
   
	/**
     * Write next video frame in argb format
     * @param data rgb data
     * @throws VideoEncodingException
     */
	private void writeVideoFrame(AVOutputStream stx, PointerPointer<BytePointer> data) throws VideoEncodingException {
	    AVCodecContext c = stx.encodeCtx;
	    AVFrame frame = stx.frame;

	    //
	    IntPointer inLinesize = null;
	    try {
		    inLinesize = new IntPointer(new int[] {4 * c.width()});

	        // From RGB to YUV
	        sws_scale(stx.swsCtx, data, inLinesize, 0, c.height(), frame.data(), frame.linesize());

	        // 
	        writeVideoFrame(stx);
	    } finally {
	    	try {
	    		inLinesize.deallocate();
	    	} catch(Exception e) {
	    	}
	    }
	}

	private BufferedImage current;

	/**
	 * Write next video frame
	 * @param image the next video frame image
	 * @throws VideoEncodingException
	 */
	public void writeVideoFrame(BufferedImage image) throws VideoEncodingException {
	    if (image == current) {
			writeVideoFrame(videoStream);
			return;
	    }
	    current = image;
		int[] rgb = new int[image.getWidth()*image.getHeight()];
		image.getRGB(0, 0, image.getWidth(), image.getHeight(), rgb, 0, image.getWidth());
		PointerPointer<BytePointer> pointer = null;
		try {
			pointer = new PointerPointer<BytePointer>(rgb);
			writeVideoFrame(videoStream, pointer);
		} finally {
			try {
				pointer.deallocate();
			} catch(Exception e) {
			}
		}
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
        // close video stream
		closeStream(videoStream);
	}

	/**
	 * Close av outputstream
	 * @param ost
	 */
	private void closeStream(AVOutputStream ost) {
		if (ost.encodeCtx != null)
			avcodec_free_context(ost.encodeCtx);
		if (ost.frame != null)
			av_frame_free(ost.frame);
		if (ost.tmpFrame != null)
			av_frame_free(ost.tmpFrame);
		if (ost.swsCtx != null)
			sws_freeContext(ost.swsCtx);
		if (ost.swrCtx != null)
			swr_free(ost.swrCtx);
	}

}
