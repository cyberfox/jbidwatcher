package com.jbidwatcher.util;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by mrs on 2/10/17.
 */
public class ScrollingBufferTest {
  ScrollingBuffer scrollBuffer;

  @Before
  public void setup() {
    scrollBuffer = new ScrollingBuffer(25);
  }

  @Test
  public void newBufferIsEmpty() {
    assertEquals("", scrollBuffer.getLog().toString());
  }

  @Test
  public void multilineLogAllShowsUp() {
    scrollBuffer.addLog("Zarf");
    scrollBuffer.addLog("is");
    scrollBuffer.addLog("with");
    scrollBuffer.addLog("you");
    scrollBuffer.addLog("again!");

    assertEquals("Zarf\nis\nwith\nyou\nagain!\n", scrollBuffer.getLog().toString());
  }

  @Test
  public void tooManyCharactersTruncates() {
    scrollBuffer.addLog("Zarf");
    scrollBuffer.addLog("is");
    scrollBuffer.addLog("with");
    scrollBuffer.addLog("you");
    scrollBuffer.addLog("again!");

    scrollBuffer.addLog("Yup!");
    assertEquals("is\nwith\nyou\nagain!\nYup!\n", scrollBuffer.getLog().toString());
  }

  @Test
  public void completelyReplacingBecauseOfLongString() {
    scrollBuffer.addLog("Zarf");
    scrollBuffer.addLog("is");
    scrollBuffer.addLog("with");
    scrollBuffer.addLog("you");
    scrollBuffer.addLog("again!");

    String tooLongString = "This string, all by itself, is longer than the maximum amount allocated for the buffer.";
    scrollBuffer.addLog(tooLongString);
    assertEquals(tooLongString + "\n", scrollBuffer.getLog().toString());
  }

  @Test
  public void nullIsASafeLog() {
    scrollBuffer.addLog(null);
    assertEquals("", scrollBuffer.getLog().toString());
  }
}
