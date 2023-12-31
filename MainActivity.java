package com.example.stt_app;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import android.media.MediaRecorder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.auth.oauth2.GoogleCredentials;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Set;
import java.util.UUID;

import com.google.cloud.speech.v1.*;
import com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION_CODE = 101;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 201;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private Button recordButton;
    private EditText resultTextView;
    String bucketName = "speechtotext_app";

    String languageCode = "ko-KR";

    String audioFile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recordButton = findViewById(R.id.recordBtn);
        resultTextView = findViewById(R.id.resultTextView);

        //request permission
        requestPermissions();

        //Press the Button to start & stop recording
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    startRecording();
                } else {
                    try {
                        stopRecording();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    //Request permissions
    private void requestPermissions() {
        //Check the version, API 26 or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            boolean allPermissionsGranted = true; // Authorization status

            //Check current permission status
            for (String permission : permissions) {
                //If permission is not granted
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false; //Set to 'false' if permission is not granted
                    ActivityCompat.requestPermissions(this, new String[]{permission},
                            getPermissionRequestCode(permission)); //Request permissions
                }
            }
            //Check with log if all permissions have been granted
            if (allPermissionsGranted) {
                Log.d("TAG", "Permission is granted!");
            }
        }
    }

    //Permission request code
    private int getPermissionRequestCode(String permission) {
        switch (permission) {
            case Manifest.permission.RECORD_AUDIO:
                return REQUEST_RECORD_AUDIO_PERMISSION;
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                return REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION;
            default:
                return REQUEST_PERMISSION_CODE;
        }
    }

    // Handle permission request results
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                handlePermissionResult(grantResults, Manifest.permission.RECORD_AUDIO, "Audio_Permission");
                break;

            case REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION:
                handlePermissionResult(grantResults, Manifest.permission.WRITE_EXTERNAL_STORAGE, "Write_Storage_Permission");
                break;

            case REQUEST_PERMISSION_CODE:
                handleMultiplePermissionResult(grantResults, "Multiple_Permissions");
                break;
        }
    }

    private void handlePermissionResult(int[] grantResults, String permissionName, String logMessage) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("TAG", logMessage + " is granted!");
        } else {
            Toast.makeText(this, "설정 앱으로 가서 " + permissionName + " 권한을 활성화해주세요.", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleMultiplePermissionResult(int[] grantResults, String logMessage) {
        handlePermissionResult(grantResults, logMessage, logMessage);
    }

    public String FileName() {

        String uuid = UUID.randomUUID().toString().replace("-", "");
        //System.out.println(uuid);

        return uuid;
    }

    public String createAudioFile() {
        // 오디오 파일 생성
        // parameters
        // Returns
        // String outputFilePath

        // Set output file name and path
        String uuid = FileName() + ".awb";

        // 외부 저장소 앱별 디렉터리에 파일 생성
        //String timeStamp = new SimpleDateFormat("yyMMdd_HHmm", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC); // 디렉터리 변경

        if (storageDir != null) {
            audioFile = new File(storageDir, uuid).getAbsolutePath();
            System.out.println(audioFile);
        } else {
            Toast.makeText(this, "외부 저장소 접근 불가능합니다.", Toast.LENGTH_SHORT).show();
        }
        return audioFile;
    }

    private void startRecording() {

        if (isRecording) {
            return;
        }

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);

        // 설정한 출력 포맷 및 인코더가 올바른지 확인
        try {
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Toast.makeText(this, "오디오 설정을 지원하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 파일 경로 생성
        String audiofile = createAudioFile();

        // setOutputFile() 메서드를 사용하여 출력 파일 경로 설정
        mediaRecorder.setOutputFile(audiofile);


        if (audiofile != null) {
            try {
                mediaRecorder.prepare();
                mediaRecorder.start();
                isRecording = true;
                recordButton.setText("녹음 중지");
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "녹음을 시작할 수 없습니다.", Toast.LENGTH_SHORT).show();
                // 녹음을 시작할 수 없을 때는 MediaRecorder를 해제해야 합니다.
                mediaRecorder.release();
            }
        } else {
            Toast.makeText(this, "오디오 파일을 생성할 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {

        if (!isRecording) {
            return;
        }

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            isRecording = false;
            recordButton.setText("녹음 시작");
            //convertSpeechToText();
            startTranscription();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Toast.makeText(this, "녹음을 중지할 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    public void startTranscription() {
        Thread transcriptionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    getResouceID();
                    readfile();
                    getCredentials();
                    SpeechSettings s = getSpeechSettings();
                    bucket();
                    transcribeSpeech(s);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        transcriptionThread.start();
    }

    public int getResouceID() {

        // Read the API key
        Resources resources = getResources();
        int resourceId = resources.getIdentifier("apikey", "raw", getPackageName());

        if (resourceId == 0) {
            throw new RuntimeException("API key file not found in resources.");
        }

        return resourceId;
    }


    public String readfile() throws IOException {

        Resources resources = getResources();
        int resourceId = getResouceID();

        String key;
        InputStream inputStream = null;

        try {
            inputStream = resources.openRawResource(resourceId);
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            key = new String(buffer);

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing InputStream: " + e.getMessage());
                }
            }
        }
        return key;
    }

    public GoogleCredentials getCredentials() throws IOException {

        String key = readfile();

        // 다시 InputStream을 초기화합니다.
        try (InputStream inputStream = new ByteArrayInputStream(key.getBytes())) {
            // Set up the Google Cloud credentials
            GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream);

            return credentials;
        }
    }

    public SpeechSettings getSpeechSettings() throws IOException {

        GoogleCredentials credentials = getCredentials();

        SpeechSettings speechSettings = SpeechSettings.newBuilder().setCredentialsProvider(() -> credentials).build();

        return speechSettings;
    }

    public void bucket() throws IOException {

        try {

            GoogleCredentials credentials = getCredentials();
            //String audioFileName = createAudioFile();

            if (audioFile == null) {
                Log.e(TAG, "Audio file does not exist.");
                return;
            }

            Storage storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService();
            Path audioFilePath = Paths.get(audioFile);
            byte[] audioData = Files.readAllBytes(audioFilePath);

            BlobId blobId = BlobId.of(bucketName, audioFilePath.getFileName().toString());
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
            storage.create(blobInfo, audioData);

        } catch (IOException e) {
            Log.e(TAG, "Error reading or uploading audio file: " + e.getMessage());
        }

    }


    public void transcribeSpeech(SpeechSettings speechSettings) throws IOException {

        try (SpeechClient speechClient = SpeechClient.create(speechSettings)) {
            //String audioFileName = createAudioFile();
            Path audioFilePath = Paths.get(audioFile);

            // Build the RecognitionAudio object
            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setUri("gs://" + bucketName + "/" + audioFilePath.getFileName().toString())
                    .build();

            // Configure the recognition request
            RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                    .setEncoding(AudioEncoding.AMR_WB)
                    .setSampleRateHertz(16000)
                    .setLanguageCode(languageCode) //수정
                    .build();

            RecognizeRequest recognizeRequest = RecognizeRequest.newBuilder()
                    .setConfig(recognitionConfig)
                    .setAudio(audio)
                    .build();

            // Process the response
            // Perform speech recognition
            RecognizeResponse response = speechClient.recognize(recognizeRequest);
            List<SpeechRecognitionResult> results = response.getResultsList();

            if (results.isEmpty()) {
                Log.d("Transcription", "No results found");
            } else {
                StringBuilder transcription = new StringBuilder();

                for (SpeechRecognitionResult result : results) {
                    transcription.append(result.getAlternatives(0).getTranscript()).append(" ");
                    String transcript = result.getAlternatives(0).getTranscript();
                    Log.d("Transcription : ", transcript);
                }

                // Set the transcription in the TextView
                runOnUiThread(() -> {
                    resultTextView.setText(transcription.toString());
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error performing speech recognition: " + e.getMessage());
        }
    }
}
