package com.blackcat.remote

object HidReports {
    const val LEFT=1; const val RIGHT=2; const val MIDDLE=4
    const val CTRL=1; const val SHIFT=2; const val ALT=4; const val GUI=8
    fun char(c:Char):Pair<Int,Int>?{
        val shifted=c.isUpperCase()||c in "!@#\$%^&*()_+{}|:\"<>?~"
        val x=c.lowercaseChar()
        val key=when(x){
            in 'a'..'z'->4+(x-'a'); in '1'..'9'->30+(x-'1'); '0'->39; '\n'->40; '\t'->43; ' '->44;
            '-'->45;'='->46;'['->47;']'->48;'\\'->49;';'->51;'\''->52;'`'->53;','->54;'.'->55;'/'->56;
            '!'->30;'@'->31;'#'->32;'$'->33;'%'->34;'^'->35;'&'->36;'*'->37;'('->38;')'->39;'_'->45;'+'->46;'{'->47;'}'->48;'|'->49;':'->51;'"'->52;'~'->53;'<'->54;'>'->55;'?'->56;else->return null}
        return key to if(shifted) SHIFT else 0
    }
}