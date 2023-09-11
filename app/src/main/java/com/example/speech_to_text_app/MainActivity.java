package com.example.speech_to_text_app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int RECORD_AUDIO_PERMISSION_CODE = 101;
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private ExecutorService executorService;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.sttResult);

        // RECORD_AUDIO 권한 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_CODE);
        }

        // 오디오 스트림을 읽기 위한 스레드 풀 생성
        executorService = Executors.newSingleThreadExecutor();
    }

    public void startSpeechToText(View view) {
        if (!isRecording) {
            // 오디오 녹음 시작
            startRecording();
        } else {
            // 오디오 녹음 중지
            stopRecording();
            // Google Cloud STT API 호출
            transcribeAudioStream();
        }
    }

    private void startRecording() {
        isRecording = true;
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
        audioRecord.startRecording();

        // 오디오 스트림을 API로 전송
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                sendAudioStreamToSTT();
            }
        });
    }

    private void stopRecording() {
        isRecording = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }


    public void startSpeechToText(View view) {
        // Google Cloud 서비스 계정 키 파일 로드
        try (InputStream credentialsStream = getResources().openRawResource(R.raw.stt)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);

            // RecognitionConfig 설정
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                    .setSampleRateHertz(16000)
                    .setLanguageCode("en-US")
                    .build();

            // RecognitionAudio 설정
            byte[] audioData = loadAudioDataFromRawResource(R.raw.audio_file);

            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setContent(ByteString.copyFrom(audioData))
                    .build();

            // SpeechClient 초기화
            try (SpeechClient speechClient = SpeechClient.create(SpeechSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build())) {
                // 음성을 텍스트로 변환
                RecognizeResponse response = speechClient.recognize(config, audio);

                // 결과 처리
                StringBuilder resultText = new StringBuilder();
                for (SpeechRecognitionResult result : response.getResultsList()) {
                    resultText.append(result.getAlternatives(0).getTranscript());
                }
                textView.setText(resultText.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
    // raw 리소스에서 오디오 데이터를 로드하는 메서드
    private byte[] loadAudioDataFromRawResource(int resourceId) throws IOException {
        InputStream inputStream = getResources().openRawResource(resourceId);
        byte[] audioData = new byte[inputStream.available()];
        inputStream.read(audioData);
        inputStream.close();
        return audioData;
    }
}
