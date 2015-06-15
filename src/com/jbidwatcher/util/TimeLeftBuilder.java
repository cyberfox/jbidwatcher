package com.jbidwatcher.util;

import com.jbidwatcher.util.config.JConfig;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;

/**
 * Created by mrs on 6/14/15.
 */
public class TimeLeftBuilder {
  private static final String mf_min_sec = "{6}{2,number,##}m, {7}{3,number,##}s";
  private static final String mf_hrs_min = "{5}{1,number,##}h, {6}{2,number,##}m";
  private static final String mf_day_hrs = "{4}{0,number,##}d, {5}{1,number,##}h";
  private static final String mf_min_sec_detailed = "{6}{2,number,##} minute{2,choice,0#, |1#, |1<s,} {7}{3,number,##} second{3,choice,0#|1#|1<s}";
  private static final String mf_hrs_min_detailed = "{5}{1,number,##} hour{1,choice,0#, |1#, |1<s,} {6}{2,number,##} minute{2,choice,0#|1#|1<s}";
  private static final String mf_day_hrs_detailed = "{4}{0,number,##} day{0,choice,0#, |1#, |1<s,}  {5}{1,number,##} hour{1,choice,0#|1#|1<s}";

  private static String convertToMsgFormat(String simpleFormat) {
    String msgFmt = simpleFormat.replaceAll("DD", "{4}{0,number,##}");
    msgFmt = msgFmt.replaceAll("HH", "{5}{1,number,##}");
    msgFmt = msgFmt.replaceAll("MM", "{6}{2,number,##}");
    msgFmt = msgFmt.replaceAll("SS", "{7}{3,number,##}");

    return msgFmt;
  }

  @SuppressWarnings({"FeatureEnvy"})
  private static String getTimeFormatter(long days, long hours) {
    String mf;
    boolean use_detailed = JConfig.queryConfiguration("timeleft.detailed", "false").equals("true");
    String cfg;
    if(days == 0) {
      if(hours == 0) {
        mf = use_detailed?mf_min_sec_detailed:mf_min_sec;
        cfg = JConfig.queryConfiguration("timeleft.minutes");
        if(cfg != null) mf = convertToMsgFormat(cfg);
      } else {
        mf = use_detailed?mf_hrs_min_detailed:mf_hrs_min;
        cfg = JConfig.queryConfiguration("timeleft.hours");
        if (cfg != null) mf = convertToMsgFormat(cfg);
      }
    } else {
      mf = use_detailed?mf_day_hrs_detailed:mf_day_hrs;
      cfg = JConfig.queryConfiguration("timeleft.days");
      if (cfg != null) mf = convertToMsgFormat(cfg);
    }
    return mf;
  }

  private static String pad(long x) {
    return (x < 10) ? " " : "";
  }

  @Nullable
  public static String getTimeLeftString(long dateDiff) {
    if (dateDiff > Constants.ONE_DAY * 60) return "N/A";

    if (dateDiff >= 0) {
      long days = dateDiff / (Constants.ONE_DAY);
      dateDiff -= days * (Constants.ONE_DAY);
      long hours = dateDiff / (Constants.ONE_HOUR);
      dateDiff -= hours * (Constants.ONE_HOUR);
      long minutes = dateDiff / (Constants.ONE_MINUTE);
      dateDiff -= minutes * (Constants.ONE_MINUTE);
      long seconds = dateDiff / Constants.ONE_SECOND;

      String mf = TimeLeftBuilder.getTimeFormatter(days, hours);

      Object[] timeArgs = {days, hours, minutes, seconds,
          TimeLeftBuilder.pad(days), TimeLeftBuilder.pad(hours), TimeLeftBuilder.pad(minutes), TimeLeftBuilder.pad(seconds)};

      return (MessageFormat.format(mf, timeArgs));
    }
    return null;
  }
}
