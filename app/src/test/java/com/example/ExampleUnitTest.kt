package com.example

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun inspectYouTubePlayer() {
    println("=== YouTubePlayerView methods ===")
    for (m in YouTubePlayerView::class.java.methods) {
      if (m.declaringClass != Any::class.java) {
        println("${m.name}(${m.parameterTypes.map { it.simpleName }.joinToString()}) -> ${m.returnType.simpleName}")
      }
    }
    println("=== Default IFramePlayerOptions ===")
    val defaultOptions = IFramePlayerOptions.default
    println("default: $defaultOptions")
  }
}


