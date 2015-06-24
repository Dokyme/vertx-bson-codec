package com.jetdrone.vertx.codec.bson.impl;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class ThreadLocalUTCDateFormat extends ThreadLocal<DateFormat> {

  public final String format(Date date) {
    return get().format(date);
  }

  public String format(Object value) {
    return get().format(value);
  }

  @Override
  public DateFormat get() {
    return super.get();
  }

  public final Date parse(String text) throws ParseException {
    return get().parse(text);
  }

  @Override
  public void remove() {
    super.remove();
  }

  @Override
  public void set(DateFormat value) {
    super.set(value);
  }

  @Override
  protected DateFormat initialValue() {
    final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    df.setTimeZone(TimeZone.getTimeZone("UTC"));

    return df;
  }
}
