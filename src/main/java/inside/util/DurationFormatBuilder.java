package inside.util;

import reactor.util.annotation.Nullable;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class DurationFormatBuilder{
    private static final double LOG_10 = Math.log(10);

    private static final int PRINT_ZERO_RARELY_FIRST = 1;
    private static final int PRINT_ZERO_RARELY_LAST = 2;
    private static final int PRINT_ZERO_IF_SUPPORTED = 3;
    private static final int PRINT_ZERO_ALWAYS = 4;
    private static final int PRINT_ZERO_NEVER = 5;

    private static final int YEARS = 0;
    private static final int MONTHS = 1;
    private static final int WEEKS = 2;
    private static final int DAYS = 3;
    private static final int HOURS = 4;
    private static final int MINUTES = 5;
    private static final int SECONDS = 6;
    private static final int MILLIS = 7;
    private static final int MAX_FIELD = MILLIS;

    private static final ConcurrentMap<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    private int minPrintedDigits;
    private int printZeroSetting;
    private int maxParsedDigits;
    private boolean rejectSignedValues;

    private PeriodFieldAffix prefix;

    // List of Printers and Parsers used to build a final formatter.
    private List<DurationPrinter> printers; // TODO: remove decomposing

    // Last DurationPrinter appended of each field type.
    private FieldFormatter[] fieldFormatters;

    public DurationFormatBuilder(){
        clear();
    }

    public DurationFormatter toFormatter(){
        DurationFormatter formatter = toFormatter(printers);
        for(FieldFormatter fieldFormatter : fieldFormatters){
            if(fieldFormatter != null){
                fieldFormatter.finish(fieldFormatters);
            }
        }
        fieldFormatters = fieldFormatters.clone();
        return formatter;
    }

    public DurationPrinter toPrinter(){
        return toFormatter().getPrinter();
    }

    public void clear(){
        minPrintedDigits = 1;
        printZeroSetting = PRINT_ZERO_RARELY_LAST;
        maxParsedDigits = 10;
        rejectSignedValues = false;
        prefix = null;
        if(printers == null){
            printers = new ArrayList<>();
        }else{
            printers.clear();
        }
        fieldFormatters = new FieldFormatter[MAX_FIELD + 1];
    }

    public DurationFormatBuilder append(DurationFormatter formatter){
        clearPrefix();
        append0(formatter.getPrinter());
        return this;
    }

    public DurationFormatBuilder append(DurationPrinter printer){
        clearPrefix();
        append0(printer);
        return this;
    }

    public DurationFormatBuilder appendLiteral(String text){
        clearPrefix();
        append0(new Literal(text));
        return this;
    }

    public DurationFormatBuilder minimumPrintedDigits(int minDigits){
        minPrintedDigits = minDigits;
        return this;
    }

    public DurationFormatBuilder maximumParsedDigits(int maxDigits){
        maxParsedDigits = maxDigits;
        return this;
    }

    public DurationFormatBuilder rejectSignedValues(boolean v){
        rejectSignedValues = v;
        return this;
    }

    public DurationFormatBuilder printZeroRarelyLast(){
        printZeroSetting = PRINT_ZERO_RARELY_LAST;
        return this;
    }

    public DurationFormatBuilder printZeroRarelyFirst(){
        printZeroSetting = PRINT_ZERO_RARELY_FIRST;
        return this;
    }

    public DurationFormatBuilder printZeroIfSupported(){
        printZeroSetting = PRINT_ZERO_IF_SUPPORTED;
        return this;
    }

    public DurationFormatBuilder printZeroAlways(){
        printZeroSetting = PRINT_ZERO_ALWAYS;
        return this;
    }

    public DurationFormatBuilder printZeroNever(){
        printZeroSetting = PRINT_ZERO_NEVER;
        return this;
    }

    public DurationFormatBuilder appendPrefix(String text){
        return appendPrefix(new SimpleAffix(text));
    }

    public DurationFormatBuilder appendPrefix(String singularText, String pluralText){
        return appendPrefix(new PluralAffix(singularText, pluralText));
    }

    public DurationFormatBuilder appendPrefix(String[] regularExpressions, String[] prefixes){
        if(regularExpressions.length < 1 || regularExpressions.length != prefixes.length){
            throw new IllegalArgumentException();
        }
        return appendPrefix(new RegExAffix(regularExpressions, prefixes));
    }

    private DurationFormatBuilder appendPrefix(PeriodFieldAffix prefix){
        if(this.prefix != null){
            prefix = new CompositeAffix(this.prefix, prefix);
        }
        this.prefix = prefix;
        return this;
    }

    public DurationFormatBuilder appendYears(){
        appendField(YEARS);
        return this;
    }

    public DurationFormatBuilder appendMonths(){
        appendField(MONTHS);
        return this;
    }

    public DurationFormatBuilder appendWeeks(){
        appendField(WEEKS);
        return this;
    }

    public DurationFormatBuilder appendDays(){
        appendField(DAYS);
        return this;
    }

    public DurationFormatBuilder appendHours(){
        appendField(HOURS);
        return this;
    }

    public DurationFormatBuilder appendMinutes(){
        appendField(MINUTES);
        return this;
    }

    public DurationFormatBuilder appendSeconds(){
        appendField(SECONDS);
        return this;
    }

    public DurationFormatBuilder appendMillis(){
        appendField(MILLIS);
        return this;
    }

    public DurationFormatBuilder appendMillis3Digit(){
        appendField(7, 3);
        return this;
    }

    private void appendField(int type){
        appendField(type, minPrintedDigits);
    }

    private void appendField(int type, int minPrinted){
        FieldFormatter field = new FieldFormatter(minPrinted, printZeroSetting,
                maxParsedDigits, rejectSignedValues, type, fieldFormatters, prefix, null);
        append0(field);
        fieldFormatters[type] = field;
        prefix = null;
    }

    public DurationFormatBuilder appendSuffix(String text){
        return appendSuffix(new SimpleAffix(text));
    }

    public DurationFormatBuilder appendSuffix(String singularText, String pluralText){
        return appendSuffix(new PluralAffix(singularText, pluralText));
    }

    public DurationFormatBuilder appendSuffix(String[] regularExpressions, String[] suffixes){
        if(regularExpressions.length < 1 || regularExpressions.length != suffixes.length){
            throw new IllegalArgumentException();
        }
        return appendSuffix(new RegExAffix(regularExpressions, suffixes));
    }

    private DurationFormatBuilder appendSuffix(PeriodFieldAffix suffix){
        DurationPrinter originalPrinter;
        if(printers.size() > 0){
            originalPrinter = printers.get(printers.size() - 1);
            //
        }else{
            originalPrinter = null;
        }

        if(!(originalPrinter instanceof FieldFormatter f)){
            throw new IllegalStateException("No field to apply suffix to");
        }

        clearPrefix();
        FieldFormatter newField = new FieldFormatter(f, suffix);
        printers.set(printers.size() - 2, newField);
        printers.set(printers.size() - 1, newField);
        fieldFormatters[newField.getFieldType()] = newField;

        return this;
    }

    public DurationFormatBuilder appendSeparator(String text){
        return appendSeparator(text, text, true, true);
    }

    public DurationFormatBuilder appendSeparatorIfFieldsAfter(String text){
        return appendSeparator(text, text, false, true);
    }

    public DurationFormatBuilder appendSeparatorIfFieldsBefore(String text){
        return appendSeparator(text, text, true, false);
    }

    public DurationFormatBuilder appendSeparator(String text, String finalText){
        return appendSeparator(text, finalText, true, true);
    }

    private DurationFormatBuilder appendSeparator(String text, String finalText, boolean useBefore, boolean useAfter){
        clearPrefix();

        // optimise zero formatter case
        List<DurationPrinter> pairs = printers;
        if(pairs.isEmpty()){
            if(useAfter && !useBefore){
                Separator separator = new Separator(text, finalText, Literal.EMPTY, false, true);
                append0(separator);
            }
            return this;
        }

        // find the last separator added
        int i;
        Separator lastSeparator = null;
        for(i = pairs.size(); --i >= 0; ){
            if(pairs.get(i) instanceof Separator s){
                lastSeparator = s;
                pairs = pairs.subList(i + 1, pairs.size());
                break;
            }
            i--;  // element pairs
        }

        // merge formatters
        if(lastSeparator != null && pairs.isEmpty()){
            throw new IllegalStateException("Cannot have two adjacent separators");
        }
        Separator separator = new Separator(text, finalText, pairs.get(0), useBefore, useAfter);
        pairs.clear();
        pairs.add(separator);
        pairs.add(separator);
        return this;
    }

    private void clearPrefix(){
        if(prefix != null){
            throw new IllegalStateException("Prefix not followed by field");
        }
    }

    private void append0(DurationPrinter printer){
        printers.add(printer);
        printers.add(printer);
    }

    private static DurationFormatter toFormatter(List<DurationPrinter> printers){
        int size = printers.size();
        if(size >= 2 && printers.get(0) instanceof Separator sep){
            if(sep.afterPrinter == null){
                DurationFormatter f = toFormatter(printers.subList(2, size));
                return new DurationFormatter(sep.finish(f.getPrinter()));
            }
        }
        DurationPrinter[] comp = printers.isEmpty()
                ? new DurationPrinter[]{Literal.EMPTY}
                : printers.toArray(DurationPrinter[]::new);
        return new DurationFormatter(comp[0]);
    }

    interface PeriodFieldAffix{

        int calculatePrintedLength(int value);

        void printTo(StringBuffer buf, int value);

        void printTo(Writer out, int value) throws IOException;

        int parse(String periodStr, int position);

        int scan(String periodStr, int position);

        String[] getAffixes();

        void finish(Set<PeriodFieldAffix> affixesToIgnore);
    }

    static abstract class IgnorableAffix implements PeriodFieldAffix{
        private volatile String[] iOtherAffixes;

        @Override
        public void finish(Set<PeriodFieldAffix> periodFieldAffixesToIgnore){
            if(iOtherAffixes == null){
                // Calculate the shortest affix in this instance.
                int shortestAffixLength = Integer.MAX_VALUE;
                String shortestAffix = null;
                for(String affix : getAffixes()){
                    if(affix.length() < shortestAffixLength){
                        shortestAffixLength = affix.length();
                        shortestAffix = affix;
                    }
                }

                // Pick only affixes that are longer than the shortest affix in this instance.
                // This will reduce the number of parse operations and thus speed up the DurationPrinter.
                // also need to pick affixes that differ only in case (but not those that are identical)
                Set<String> affixesToIgnore = new HashSet<>();
                for(PeriodFieldAffix periodFieldAffixToIgnore : periodFieldAffixesToIgnore){
                    if(periodFieldAffixToIgnore != null){
                        for(String affixToIgnore : periodFieldAffixToIgnore.getAffixes()){
                            if(affixToIgnore.length() > shortestAffixLength ||
                                    affixToIgnore.equalsIgnoreCase(shortestAffix) && !affixToIgnore.equals(shortestAffix)){
                                affixesToIgnore.add(affixToIgnore);
                            }
                        }
                    }
                }
                iOtherAffixes = affixesToIgnore.toArray(new String[0]);
            }
        }


        protected boolean matchesOtherAffix(int textLength, String periodStr, int position){
            if(iOtherAffixes != null){
                // ignore case when affix length differs
                // match case when affix length is same
                for(String affixToIgnore : iOtherAffixes){
                    int textToIgnoreLength = affixToIgnore.length();
                    if(textLength < textToIgnoreLength && periodStr.regionMatches(true, position, affixToIgnore, 0, textToIgnoreLength) ||
                            textLength == textToIgnoreLength && periodStr.regionMatches(false, position, affixToIgnore, 0, textToIgnoreLength)){
                        return true;
                    }
                }
            }
            return false;
        }
    }

    static class SimpleAffix extends IgnorableAffix{
        private final String iText;

        SimpleAffix(String text){
            iText = text;
        }

        @Override
        public int calculatePrintedLength(int value){
            return iText.length();
        }

        @Override
        public void printTo(StringBuffer buf, int value){
            buf.append(iText);
        }

        @Override
        public void printTo(Writer out, int value) throws IOException{
            out.write(iText);
        }

        @Override
        public int parse(String periodStr, int position){
            String text = iText;
            int textLength = text.length();
            if(periodStr.regionMatches(true, position, text, 0, textLength)){
                if(!matchesOtherAffix(textLength, periodStr, position)){
                    return position + textLength;
                }
            }
            return ~position;
        }

        @Override
        public int scan(String periodStr, final int position){
            String text = iText;
            int textLength = text.length();
            int sourceLength = periodStr.length();
            search:
            for(int pos = position; pos < sourceLength; pos++){
                if(periodStr.regionMatches(true, pos, text, 0, textLength)){
                    if(!matchesOtherAffix(textLength, periodStr, pos)){
                        return pos;
                    }
                }
                // Only allow number characters to be skipped in search of suffix.
                switch(periodStr.charAt(pos)){
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                    case '.':
                    case ',':
                    case '+':
                    case '-':
                        break;
                    default:
                        break search;
                }
            }
            return ~position;
        }

        @Override
        public String[] getAffixes(){
            return new String[]{iText};
        }
    }

    static class PluralAffix extends IgnorableAffix{
        private final String iSingularText;
        private final String iPluralText;

        PluralAffix(String singularText, String pluralText){
            iSingularText = singularText;
            iPluralText = pluralText;
        }

        @Override
        public int calculatePrintedLength(int value){
            return (value == 1 ? iSingularText : iPluralText).length();
        }

        @Override
        public void printTo(StringBuffer buf, int value){
            buf.append(value == 1 ? iSingularText : iPluralText);
        }

        @Override
        public void printTo(Writer out, int value) throws IOException{
            out.write(value == 1 ? iSingularText : iPluralText);
        }

        @Override
        public int parse(String periodStr, int position){
            String text1 = iPluralText;
            String text2 = iSingularText;

            if(text1.length() < text2.length()){
                // Swap in order to match longer one first.
                String temp = text1;
                text1 = text2;
                text2 = temp;
            }

            if(periodStr.regionMatches(true, position, text1, 0, text1.length())){
                if(!matchesOtherAffix(text1.length(), periodStr, position)){
                    return position + text1.length();
                }
            }
            if(periodStr.regionMatches(true, position, text2, 0, text2.length())){
                if(!matchesOtherAffix(text2.length(), periodStr, position)){
                    return position + text2.length();
                }
            }

            return ~position;
        }

        @Override
        public int scan(String periodStr, int position){
            String text1 = iPluralText;
            String text2 = iSingularText;

            if(text1.length() < text2.length()){
                // Swap in order to match longer one first.
                String temp = text1;
                text1 = text2;
                text2 = temp;
            }

            int textLength1 = text1.length();
            int textLength2 = text2.length();

            int sourceLength = periodStr.length();
            for(int pos = position; pos < sourceLength; pos++){
                if(periodStr.regionMatches(true, pos, text1, 0, textLength1)){
                    if(!matchesOtherAffix(text1.length(), periodStr, pos)){
                        return pos;
                    }
                }
                if(periodStr.regionMatches(true, pos, text2, 0, textLength2)){
                    if(!matchesOtherAffix(text2.length(), periodStr, pos)){
                        return pos;
                    }
                }
            }
            return ~position;
        }

        @Override
        public String[] getAffixes(){
            return new String[]{iSingularText, iPluralText};
        }
    }

    static class RegExAffix extends IgnorableAffix{
        private static final Comparator<String> LENGTH_DESC_COMPARATOR = (o1, o2) -> o2.length() - o1.length();

        private final String[] iSuffixes;
        private final Pattern[] iPatterns;

        // The parse method has to iterate over the suffixes from the longest one to the shortest one
        // Otherwise it might consume not enough characters.
        private final String[] iSuffixesSortedDescByLength;

        RegExAffix(String[] regExes, String[] texts){
            iSuffixes = texts.clone();
            iPatterns = new Pattern[regExes.length];
            for(int i = 0; i < regExes.length; i++){
                Pattern pattern = PATTERNS.get(regExes[i]);
                if(pattern == null){
                    pattern = Pattern.compile(regExes[i]);
                    PATTERNS.putIfAbsent(regExes[i], pattern);
                }
                iPatterns[i] = pattern;
            }
            iSuffixesSortedDescByLength = iSuffixes.clone();
            Arrays.sort(iSuffixesSortedDescByLength, LENGTH_DESC_COMPARATOR);
        }

        private int selectSuffixIndex(int value){
            String valueString = String.valueOf(value);
            for(int i = 0; i < iPatterns.length; i++){
                if(iPatterns[i].matcher(valueString).matches()){
                    return i;
                }
            }
            return iPatterns.length - 1;
        }

        @Override
        public int calculatePrintedLength(int value){
            return iSuffixes[selectSuffixIndex(value)].length();
        }

        @Override
        public void printTo(StringBuffer buf, int value){
            buf.append(iSuffixes[selectSuffixIndex(value)]);
        }

        @Override
        public void printTo(Writer out, int value) throws IOException{
            out.write(iSuffixes[selectSuffixIndex(value)]);
        }

        @Override
        public int parse(String periodStr, int position){
            for(String text : iSuffixesSortedDescByLength){
                if(periodStr.regionMatches(true, position, text, 0, text.length())){
                    if(!matchesOtherAffix(text.length(), periodStr, position)){
                        return position + text.length();
                    }
                }
            }
            return ~position;
        }

        @Override
        public int scan(String periodStr, int position){
            int sourceLength = periodStr.length();
            for(int pos = position; pos < sourceLength; pos++){
                for(String text : iSuffixesSortedDescByLength){
                    if(periodStr.regionMatches(true, pos, text, 0, text.length())){
                        if(!matchesOtherAffix(text.length(), periodStr, pos)){
                            return pos;
                        }
                    }
                }
            }
            return ~position;
        }

        @Override
        public String[] getAffixes(){
            return iSuffixes.clone();
        }
    }

    static class CompositeAffix extends IgnorableAffix{
        private final PeriodFieldAffix iLeft;
        private final PeriodFieldAffix iRight;
        private final String[] iLeftRightCombinations;

        CompositeAffix(PeriodFieldAffix left, PeriodFieldAffix right){
            iLeft = left;
            iRight = right;

            // We need to construct all possible combinations of left and right.
            // We are doing it once in constructor so that getAffixes() is quicker.
            Set<String> result = new HashSet<>();
            for(String leftText : iLeft.getAffixes()){
                for(String rightText : iRight.getAffixes()){
                    result.add(leftText + rightText);
                }
            }
            iLeftRightCombinations = result.toArray(new String[0]);
        }

        @Override
        public int calculatePrintedLength(int value){
            return iLeft.calculatePrintedLength(value)
                    + iRight.calculatePrintedLength(value);
        }

        @Override
        public void printTo(StringBuffer buf, int value){
            iLeft.printTo(buf, value);
            iRight.printTo(buf, value);
        }

        @Override
        public void printTo(Writer out, int value) throws IOException{
            iLeft.printTo(out, value);
            iRight.printTo(out, value);
        }

        @Override
        public int parse(String periodStr, int position){
            int pos = iLeft.parse(periodStr, position);
            if(pos >= 0){
                pos = iRight.parse(periodStr, pos);
                if(pos >= 0 && matchesOtherAffix(parse(periodStr, pos) - pos, periodStr, position)){
                    return ~position;
                }
            }
            return pos;
        }

        @Override
        public int scan(String periodStr, final int position){
            int leftPosition = iLeft.scan(periodStr, position);
            if(leftPosition >= 0){
                int rightPosition = iRight.scan(periodStr, iLeft.parse(periodStr, leftPosition));
                if(!(rightPosition >= 0 && matchesOtherAffix(iRight.parse(periodStr, rightPosition) - leftPosition, periodStr, position))){
                    if(leftPosition > 0){
                        return leftPosition;
                    }
                    return rightPosition;
                }
            }
            return ~position;
        }

        @Override
        public String[] getAffixes(){
            return iLeftRightCombinations.clone();
        }
    }

    static class FieldFormatter implements DurationPrinter{
        private final int minPrintedDigits;
        private final int printZeroSetting;
        private final int maxParsedDigits;
        private final boolean rejectSignedValues;

        private final int fieldType;

        private final FieldFormatter[] fieldFormatters;

        @Nullable
        private final PeriodFieldAffix prefix;
        @Nullable
        private final PeriodFieldAffix suffix;

        FieldFormatter(int minPrintedDigits, int printZeroSetting,
                       int maxParsedDigits, boolean rejectSignedValues,
                       int fieldType, FieldFormatter[] fieldFormatters,
                       @Nullable PeriodFieldAffix prefix, @Nullable PeriodFieldAffix suffix){
            this.minPrintedDigits = minPrintedDigits;
            this.printZeroSetting = printZeroSetting;
            this.maxParsedDigits = maxParsedDigits;
            this.rejectSignedValues = rejectSignedValues;
            this.fieldType = fieldType;
            this.fieldFormatters = fieldFormatters;
            this.prefix = prefix;
            this.suffix = suffix;
        }

        FieldFormatter(FieldFormatter field, PeriodFieldAffix suffix){
            minPrintedDigits = field.minPrintedDigits;
            printZeroSetting = field.printZeroSetting;
            maxParsedDigits = field.maxParsedDigits;
            rejectSignedValues = field.rejectSignedValues;
            fieldType = field.fieldType;
            fieldFormatters = field.fieldFormatters;
            prefix = field.prefix;
            if(field.suffix != null){
                suffix = new CompositeAffix(field.suffix, suffix);
            }
            this.suffix = suffix;
        }

        public void finish(FieldFormatter[] fieldFormatters){
            // find all other affixes that are in use
            Set<PeriodFieldAffix> prefixesToIgnore = new HashSet<>();
            Set<PeriodFieldAffix> suffixesToIgnore = new HashSet<>();
            for(FieldFormatter fieldFormatter : fieldFormatters){
                if(fieldFormatter != null && !equals(fieldFormatter)){
                    prefixesToIgnore.add(fieldFormatter.prefix);
                    suffixesToIgnore.add(fieldFormatter.suffix);
                }
            }
            // if we have a prefix then allow to ignore behaviour
            if(prefix != null){
                prefix.finish(prefixesToIgnore);
            }
            // if we have a suffix then allow to ignore behaviour
            if(suffix != null){
                suffix.finish(suffixesToIgnore);
            }
        }

        @Override
        public int countFieldsToPrint(Duration period, int stopAt, Locale locale){
            if(stopAt <= 0){
                return 0;
            }
            if(printZeroSetting == PRINT_ZERO_ALWAYS || getFieldValue(period) != Long.MAX_VALUE){
                return 1;
            }
            return 0;
        }

        @Override
        public int calculatePrintedLength(Duration period, Locale locale){
            long valueLong = getFieldValue(period);
            if(valueLong == Long.MAX_VALUE){
                return 0;
            }

            int sum = Math.max(Mathf.digits(valueLong), minPrintedDigits);
            int value = (int)valueLong;

            if(prefix != null){
                sum += prefix.calculatePrintedLength(value);
            }
            if(suffix != null){
                sum += suffix.calculatePrintedLength(value);
            }

            return sum;
        }

        @Override
        public void printTo(StringBuffer buf, Duration period, Locale locale){
            long valueLong = getFieldValue(period);
            if(valueLong == Long.MAX_VALUE){
                return;
            }
            int value = (int)valueLong;

            if(prefix != null){
                prefix.printTo(buf, value);
            }
            int minDigits = minPrintedDigits;
            if(minDigits <= 1){
                appendUnpaddedInteger(buf, value);
            }else{
                appendPaddedInteger(buf, value, minDigits);
            }
            if(suffix != null){
                suffix.printTo(buf, value);
            }
        }

        @Override
        public void printTo(Writer out, Duration period, Locale locale) throws IOException{
            long valueLong = getFieldValue(period);
            if(valueLong == Long.MAX_VALUE){
                return;
            }
            int value = (int)valueLong;

            if(prefix != null){
                prefix.printTo(out, value);
            }
            int minDigits = minPrintedDigits;
            if(minDigits <= 1){
                writeUnpaddedInteger(out, value);
            }else{
                writePaddedInteger(out, value, minDigits);
            }
            if(suffix != null){
                suffix.printTo(out, value);
            }
        }

        long getFieldValue(Duration duration){
            long value;

            switch(fieldType){
                default:
                    return Long.MAX_VALUE;
                case DAYS:
                    value = duration.toDays();
                    break;
                case HOURS:
                    value = duration.toHours();
                    break;
                case MINUTES:
                    value = duration.toMinutes();
                    break;
                case SECONDS:
                    value = duration.toSeconds();
                    break;
                case MILLIS:
                    value = duration.toMillis();
                    break;
            }

            // determine if duration is zero and this is the last field
            if(value == 0){
                switch(printZeroSetting){
                    case PRINT_ZERO_NEVER:
                        return Long.MAX_VALUE;
                    case PRINT_ZERO_RARELY_LAST:
                        if(duration.isZero() && fieldFormatters[fieldType] == this){
                            for(int i = fieldType + 1; i <= MAX_FIELD; i++){
                                if(fieldFormatters[i] != null){
                                    return Long.MAX_VALUE;
                                }
                            }
                        }else{
                            return Long.MAX_VALUE;
                        }
                        break;
                    case PRINT_ZERO_RARELY_FIRST:
                        if(duration.isZero() && fieldFormatters[fieldType] == this){
                            int i = fieldType;
                            i--;
                            for(; i >= 0 && i <= MAX_FIELD; i--){
                                if(fieldFormatters[i] != null){
                                    return Long.MAX_VALUE;
                                }
                            }
                        }else{
                            return Long.MAX_VALUE;
                        }
                        break;
                }
            }

            return value;
        }

        int getFieldType(){
            return fieldType;
        }
    }

    static class Literal implements DurationPrinter{
        static final Literal EMPTY = new Literal("");

        private final String iText;

        Literal(String text){
            iText = text;
        }

        @Override
        public int countFieldsToPrint(Duration period, int stopAt, Locale locale){
            return 0;
        }

        @Override
        public int calculatePrintedLength(Duration period, Locale locale){
            return iText.length();
        }

        @Override
        public void printTo(StringBuffer buf, Duration period, Locale locale){
            buf.append(iText);
        }

        @Override
        public void printTo(Writer out, Duration period, Locale locale) throws IOException{
            out.write(iText);
        }
    }

    static class Separator implements DurationPrinter{
        private final String text;
        private final String finalText;

        private final boolean useBefore;
        private final boolean useAfter;

        private final DurationPrinter beforePrinter;
        private volatile DurationPrinter afterPrinter;

        Separator(String text, String finalText,
                  DurationPrinter beforePrinter,
                  boolean useBefore, boolean useAfter){
            this.text = text;
            this.finalText = finalText;
            this.beforePrinter = beforePrinter;
            this.useBefore = useBefore;
            this.useAfter = useAfter;
        }

        @Override
        public int countFieldsToPrint(Duration duration, int stopAt, Locale locale){
            int sum = beforePrinter.countFieldsToPrint(duration, stopAt, locale);
            if(sum < stopAt){
                sum += afterPrinter.countFieldsToPrint(duration, stopAt, locale);
            }
            return sum;
        }

        @Override
        public int calculatePrintedLength(Duration period, Locale locale){
            DurationPrinter before = beforePrinter;
            DurationPrinter after = afterPrinter;

            int sum = before.calculatePrintedLength(period, locale)
                    + after.calculatePrintedLength(period, locale);

            if(useBefore){
                if(before.countFieldsToPrint(period, 1, locale) > 0){
                    if(useAfter){
                        int afterCount = after.countFieldsToPrint(period, 2, locale);
                        if(afterCount > 0){
                            sum += (afterCount > 1 ? text : finalText).length();
                        }
                    }else{
                        sum += text.length();
                    }
                }
            }else if(useAfter && after.countFieldsToPrint(period, 1, locale) > 0){
                sum += text.length();
            }

            return sum;
        }

        @Override
        public void printTo(StringBuffer buf, Duration period, Locale locale){
            DurationPrinter before = beforePrinter;
            DurationPrinter after = afterPrinter;

            before.printTo(buf, period, locale);
            if(useBefore){
                if(before.countFieldsToPrint(period, 1, locale) > 0){
                    if(useAfter){
                        int afterCount = after.countFieldsToPrint(period, 2, locale);
                        if(afterCount > 0){
                            buf.append(afterCount > 1 ? text : finalText);
                        }
                    }else{
                        buf.append(text);
                    }
                }
            }else if(useAfter && after.countFieldsToPrint(period, 1, locale) > 0){
                buf.append(text);
            }
            after.printTo(buf, period, locale);
        }

        @Override
        public void printTo(Writer out, Duration period, Locale locale) throws IOException{
            DurationPrinter before = beforePrinter;
            DurationPrinter after = afterPrinter;

            before.printTo(out, period, locale);
            if(useBefore){
                if(before.countFieldsToPrint(period, 1, locale) > 0){
                    if(useAfter){
                        int afterCount = after.countFieldsToPrint(period, 2, locale);
                        if(afterCount > 0){
                            out.write(afterCount > 1 ? text : finalText);
                        }
                    }else{
                        out.write(text);
                    }
                }
            }else if(useAfter && after.countFieldsToPrint(period, 1, locale) > 0){
                out.write(text);
            }
            after.printTo(out, period, locale);
        }

        Separator finish(DurationPrinter afterPrinter){
            this.afterPrinter = afterPrinter;
            return this;
        }
    }

    static void appendPaddedInteger(StringBuffer buf, int value, int size){
        try{
            appendPaddedInteger((Appendable)buf, value, size);
        }catch(IOException e){
            // StringBuffer does not throw IOException
        }
    }

    static void appendPaddedInteger(Appendable appendable, int value, int size) throws IOException{
        if(value < 0){
            appendable.append('-');
            if(value != Integer.MIN_VALUE){
                value = -value;
            }else{
                for(; size > 10; size--){
                    appendable.append('0');
                }
                appendable.append("" + -(long)Integer.MIN_VALUE);
                return;
            }
        }
        if(value < 10){
            for(; size > 1; size--){
                appendable.append('0');
            }
            appendable.append((char)(value + '0'));
        }else if(value < 100){
            for(; size > 2; size--){
                appendable.append('0');
            }
            // Calculate value div/mod by 10 without using two expensive
            // division operations. (2 ^ 27) / 10 = 13421772. Add one to
            // value to correct rounding error.
            int d = ((value + 1) * 13421772) >> 27;
            appendable.append((char)(d + '0'));
            // Append remainder by calculating (value - d * 10).
            appendable.append((char)(value - (d << 3) - (d << 1) + '0'));
        }else{
            int digits;
            if(value < 1000){
                digits = 3;
            }else if(value < 10000){
                digits = 4;
            }else{
                digits = (int)(Math.log(value) / LOG_10) + 1;
            }
            for(; size > digits; size--){
                appendable.append('0');
            }
            appendable.append(Integer.toString(value));
        }
    }

    static void writePaddedInteger(Writer out, int value, int size)
            throws IOException{
        if(value < 0){
            out.write('-');
            if(value != Integer.MIN_VALUE){
                value = -value;
            }else{
                for(; size > 10; size--){
                    out.write('0');
                }
                out.write("" + -(long)Integer.MIN_VALUE);
                return;
            }
        }
        if(value < 10){
            for(; size > 1; size--){
                out.write('0');
            }
            out.write(value + '0');
        }else if(value < 100){
            for(; size > 2; size--){
                out.write('0');
            }
            // Calculate value div/mod by 10 without using two expensive
            // division operations. (2 ^ 27) / 10 = 13421772. Add one to
            // value to correct rounding error.
            int d = ((value + 1) * 13421772) >> 27;
            out.write(d + '0');
            // Append remainder by calculating (value - d * 10).
            out.write(value - (d << 3) - (d << 1) + '0');
        }else{
            int digits;
            if(value < 1000){
                digits = 3;
            }else if(value < 10000){
                digits = 4;
            }else{
                digits = (int)(Math.log(value) / LOG_10) + 1;
            }
            for(; size > digits; size--){
                out.write('0');
            }
            out.write(Integer.toString(value));
        }
    }

    static void appendUnpaddedInteger(StringBuffer buf, int value){
        try{
            appendUnpaddedInteger((Appendable)buf, value);
        }catch(IOException e){
            // StringBuffer do not throw IOException
        }
    }

    static void appendUnpaddedInteger(Appendable appendable, int value) throws IOException{
        if(value < 0){
            appendable.append('-');
            if(value != Integer.MIN_VALUE){
                value = -value;
            }else{
                appendable.append("" + -(long)Integer.MIN_VALUE);
                return;
            }
        }
        if(value < 10){
            appendable.append((char)(value + '0'));
        }else if(value < 100){
            // Calculate value div/mod by 10 without using two expensive
            // division operations. (2 ^ 27) / 10 = 13421772. Add one to
            // value to correct rounding error.
            int d = ((value + 1) * 13421772) >> 27;
            appendable.append((char)(d + '0'));
            // Append remainder by calculating (value - d * 10).
            appendable.append((char)(value - (d << 3) - (d << 1) + '0'));
        }else{
            appendable.append(Integer.toString(value));
        }
    }

    static void writeUnpaddedInteger(Writer out, int value)
            throws IOException{
        if(value < 0){
            out.write('-');
            if(value != Integer.MIN_VALUE){
                value = -value;
            }else{
                out.write("" + -(long)Integer.MIN_VALUE);
                return;
            }
        }
        if(value < 10){
            out.write(value + '0');
        }else if(value < 100){
            // Calculate value div/mod by 10 without using two expensive
            // division operations. (2 ^ 27) / 10 = 13421772. Add one to
            // value to correct rounding error.
            int d = ((value + 1) * 13421772) >> 27;
            out.write(d + '0');
            // Append remainder by calculating (value - d * 10).
            out.write(value - (d << 3) - (d << 1) + '0');
        }else{
            out.write(Integer.toString(value));
        }
    }
}
