package bz.rxla.audioplayer;

import android.os.Handler;

import androidx.annotation.NonNull;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;

import android.content.Context;
import android.os.Build;

/**
 * Android implementation for AudioPlayerPlugin.
 */
public class AudioplayerPlugin implements FlutterPlugin, MethodCallHandler {
  private static final String ID = "bz.rxla.flutter/audio";

  private final MethodChannel channel;
  private final AudioManager am;
  private final Handler handler = new Handler();
  private MediaPlayer mediaPlayer;

  private AudioplayerPlugin(Context context, MethodChannel channel) {
    this.channel = channel;
    channel.setMethodCallHandler(this);
    this.am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    final MethodChannel channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), ID);
    channel.setMethodCallHandler(new AudioplayerPlugin(flutterPluginBinding.getApplicationContext(), channel));
  }
  // This static function is optional and equivalent to onAttachedToEngine. It supports the old
  // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
  // plugin registration via this function while apps migrate to use the new Android APIs
  // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
  //
  // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
  // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
  // depending on the user's project. onAttachedToEngine or registerWith must both be defined
  // in the same class.

  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), ID);
    channel.setMethodCallHandler(new AudioplayerPlugin(registrar.context().getApplicationContext(), channel));
  }


  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    switch (call.method) {
      case "play":
        play(call.argument("url").toString());
        result.success(null);
        break;
      case "pause":
        pause();
        result.success(null);
        break;
      case "stop":
        stop();
        result.success(null);
        break;
      case "seek":
        double position = call.arguments();
        seek(position);
        result.success(null);
        break;
      case "setVolume":
        double volume = call.arguments();
        setVolume(volume);
        result.success(null);
        break;
      case "mute":
        Boolean muted = call.arguments();
        mute(muted);
        result.success(null);
        break;
      default:
        result.notImplemented();
    }
  }

  private void mute(Boolean muted) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      am.adjustStreamVolume(AudioManager.STREAM_MUSIC, muted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
    } else {
      am.setStreamMute(AudioManager.STREAM_MUSIC, muted);
    }
  }

  private void seek(double position) {
    mediaPlayer.seekTo((int) (position * 1000));
  }

  private void setVolume(double value) {
    float bracketedValue = (float) Math.max(0.0, Math.min(1.0, value));
    mediaPlayer.setVolume(bracketedValue, bracketedValue);
  }

  private void stop() {
    handler.removeCallbacks(sendData);
    if (mediaPlayer != null) {
      mediaPlayer.stop();
      mediaPlayer.release();
      mediaPlayer = null;
      channel.invokeMethod("audio.onStop", null);
    }
  }

  private void pause() {
    handler.removeCallbacks(sendData);
    if (mediaPlayer != null) {
      mediaPlayer.pause();
      channel.invokeMethod("audio.onPause", true);
    }
  }

  private void play(String url) {
    if (mediaPlayer == null) {
      mediaPlayer = new MediaPlayer();
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

      try {
        mediaPlayer.setDataSource(url);
      } catch (IOException e) {
        Log.w(ID, "Invalid DataSource", e);
        channel.invokeMethod("audio.onError", "Invalid Datasource");
        return;
      }

      mediaPlayer.prepareAsync();

      mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){
        @Override
        public void onPrepared(MediaPlayer mp) {
          mediaPlayer.start();
          channel.invokeMethod("audio.onStart", mediaPlayer.getDuration());
        }
      });

      mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
        @Override
        public void onCompletion(MediaPlayer mp) {
          stop();
          channel.invokeMethod("audio.onComplete", null);
        }
      });

      mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener(){
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
          channel.invokeMethod("audio.onError", String.format("{\"what\":%d,\"extra\":%d}", what, extra));
          return true;
        }
      });
    } else {
      mediaPlayer.start();
      channel.invokeMethod("audio.onStart", mediaPlayer.getDuration());
    }
    handler.post(sendData);
  }

  private final Runnable sendData = new Runnable(){
    public void run(){
      try {
        if (!mediaPlayer.isPlaying()) {
          handler.removeCallbacks(sendData);
        }
        int time = mediaPlayer.getCurrentPosition();
        channel.invokeMethod("audio.onCurrentPosition", time);
        handler.postDelayed(this, 200);
      }
      catch (Exception e) {
        Log.w(ID, "When running handler", e);
      }
    }
  };

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
  }
}
