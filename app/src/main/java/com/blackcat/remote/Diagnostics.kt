package com.blackcat.remote
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class Diagnostics(private val sink:(String)->Unit){
 private val lines=ArrayDeque<String>();private val fmt=SimpleDateFormat("HH:mm:ss",Locale.US)
 @Synchronized fun add(message:String){val line=fmt.format(Date())+" "+message;lines.addLast(line);while(lines.size>200)lines.removeFirst();sink(snapshot())}
 @Synchronized fun clear(){lines.clear();sink(snapshot())}
 @Synchronized fun snapshot()=lines.joinToString("\n")
}