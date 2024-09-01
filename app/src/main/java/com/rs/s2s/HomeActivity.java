/**
 * description
 *
 * @author whs
 * @date 2024/8/31
 */
package com.rs.s2s;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class HomeActivity extends AppCompatActivity {

   private static final int SAMPLE_RATE = 16000; // 采样率
   private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE,
           AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
   private AudioRecord recorder;
   private AudioTrack player;
   private boolean isRecording = false;
   private boolean isPlaying = false;
   private Socket socket;
   private OutputStream outputStream;
   private InputStream inputStream;

   private Button startButton, stopButton;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
//      setContentView(R.layout.activity_main);
//
//      startButton = findViewById(R.id.startButton);
//      stopButton = findViewById(R.id.stopButton);

      // 请求麦克风权限
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
              != PackageManager.PERMISSION_GRANTED) {
         ActivityCompat.requestPermissions(this,
                 new String[]{Manifest.permission.RECORD_AUDIO}, 200);
      }

      startButton.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View v) {
            startStreaming();
         }
      });

      stopButton.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View v) {
            stopStreaming();
         }
      });
   }

   private void startStreaming() {
      isRecording = true;
      isPlaying = true;

      new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               // 连接服务器
               socket = new Socket("192.168.1.100", 5000);
               outputStream = socket.getOutputStream();
               inputStream = socket.getInputStream();

               if (ActivityCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                  // TODO: Consider calling
                  //    ActivityCompat#requestPermissions
                  // here to request the missing permissions, and then overriding
                  //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                  //                                          int[] grantResults)
                  // to handle the case where the user grants the permission. See the documentation
                  // for ActivityCompat#requestPermissions for more details.
                  return;
               }
               recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                       AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);
//               player = new AudioTrack(AudioFormat.STREAM_MUSIC, SAMPLE_RATE,
//                       AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE,
//                       AudioTrack.MODE_STREAM);

               recorder.startRecording();
               player.play();

               byte[] buffer = new byte[BUFFER_SIZE];

               while (isRecording) {
                  // 从麦克风读取音频数据
                  int read = recorder.read(buffer, 0, buffer.length);
                  if (read > 0) {
                     // 发送音频数据到服务器
                     outputStream.write(buffer, 0, read);
                  }
               }
            } catch (IOException e) {
               e.printStackTrace();
            }
         }
      }).start();

      new Thread(new Runnable() {
         @Override
         public void run() {
            byte[] buffer = new byte[BUFFER_SIZE];

            while (isPlaying) {
               try {
                  // 从服务器接收音频数据
                  int read = inputStream.read(buffer, 0, buffer.length);
                  if (read > 0) {
                     // 播放接收到的音频数据
                     player.write(buffer, 0, read);
                  }
               } catch (IOException e) {
                  e.printStackTrace();
               }
            }
         }
      }).start();
   }

   private void stopStreaming() {
      isRecording = false;
      isPlaying = false;

      if (recorder != null) {
         recorder.stop();
         recorder.release();
         recorder = null;
      }

      if (player != null) {
         player.stop();
         player.release();
         player = null;
      }

      try {
         if (socket != null) {
            socket.close();
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   @Override
   public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);

      if (requestCode == 200) {
         if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "麦克风权限已授予", Toast.LENGTH_SHORT).show();
         } else {
            Toast.makeText(this, "麦克风权限被拒绝", Toast.LENGTH_SHORT).show();
         }
      }
   }
}
