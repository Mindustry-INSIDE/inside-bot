package inside.util;

import reactor.util.annotation.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class DurationFormatBuilder{
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
    private List<DurationPrinter> printers;

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
        return formatter;
    }

    public DurationPrinter toPrinter(){
        return toFormatter().getPrinter();
    }

    public void clear(){
        minPrintedDigits = 1;
        printZeroSetting = PRINT_ZERO_RARELY_LAST;
        maxParsedDigits = 2;
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
        Preconditions.requireState(prefix == null, "Prefix not followed by field");
        printers.add(formatter.getPrinter());
        return this;
    }

    public DurationFormatBuilder append(DurationPrinter printer){
        Preconditions.requireState(prefix == null, "Prefix not followed by field");
        printers.add(printer);
        return this;
    }

    public DurationFormatBuilder appendLiteral(String text){
        Preconditions.requireState(prefix == null, "Prefix not followed by field");
        printers.add(new Literal(text));
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

    public DurationFormatBuilder rejectSignedValues(boolean rejectSignedValues){
        this.rejectSignedValues = rejectSignedValues;
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
        printers.add(field);
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
        DurationPrinter originalPrinter = printers.size() > 0 ? printers.get(printers.size() - 1) : null;
        if(!(originalPrinter instanceof FieldFormatter f)){
            throw new IllegalStateException("No field to apply suffix to");
        }

        Preconditions.requireState(prefix == null, "Prefix not followed by field");
        FieldFormatter newField = new FieldFormatter(f, suffix);
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
        Preconditions.requireState(prefix == null, "Prefix not followed by field");

        // optimise zero formatter case
        List<DurationPrinter> pairs = printers;
        if(pairs.isEmpty()){
            if(useAfter && !useBefore){
                Separator separator = new Separator(text, finalText, Literal.EMPTY, false, true);
                printers.add(separator);
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

    private static DurationFormatter toFormatter(List<? extends DurationPrinter> printers){
        int size = printers.size();
        if(size >= 2 && printers.get(0) instanceof Separator sep){
            if(sep.afterPrinter == null){
                DurationFormatter f = toFormatter(printers.subList(2, size));
                return new DurationFormatter(sep.finish(f.getPrinter()));
            }
        }

        DurationPrinter formatter = printers.isEmpty() ? Literal.EMPTY : printers.get(0);
        return new DurationFormatter(formatter);
    }

    interface PeriodFieldAffix{

        int calculatePrintedLength(int value);

        void formatTo(StringBuilder buf, int value);

        int parse(String periodStr, int position);

        int scan(String periodStr, int position);

        String[] getAffixes();

        void finish(Set<? extends PeriodFieldAffix> affixesToIgnore);
    }

    static abstract class IgnorableAffix implements PeriodFieldAffix{
        private volatile String[] otherAffixes;

        @Override
        public void finish(Set<? extends PeriodFieldAffix> periodFieldAffixesToIgnore){
            if(otherAffixes == null){
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
                otherAffixes = affixesToIgnore.toArray(new String[0]);
            }
        }

        protected boolean matchesOtherAffix(int textLength, String periodStr, int position){
            if(otherAffixes != null){
                // ignore case when affix length differs
                // match case when affix length is same
                for(String affixToIgnore : otherAffixes){
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
        private final String text;

        SimpleAffix(String text){
            this.text = Objects.requireNonNull(text, "text");
        }

        @Override
        public int calculatePrintedLength(int value){
            return text.length();
        }

        @Override
        public void formatTo(StringBuilder buf, int value){
            buf.append(text);
        }

        @Override
        public int parse(String periodStr, int position){
            int textLength = text.length();
            if(periodStr.regionMatches(true, position, text, 0, textLength)){
                if(!matchesOtherAffix(textLength, periodStr, position)){
                    return position + textLength;
                }
            }
            return ~position;
        }

        @Override
        public int scan(String periodStr, int position){
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
            return new String[]{text};
        }
    }

    static class PluralAffix extends IgnorableAffix{
        private final String singularText;
        private final String pluralText;

        PluralAffix(String singularText, String pluralText){
            this.singularText = Objects.requireNonNull(singularText, "singularText");
            this.pluralText = Objects.requireNonNull(pluralText, "pluralText");
        }

        @Override
        public int calculatePrintedLength(int value){
            return (value == 1 ? singularText : pluralText).length();
        }

        @Override
        public void formatTo(StringBuilder buf, int value){
            buf.append(value == 1 ? singularText : pluralText);
        }

        @Override
        public int parse(String periodStr, int position){
            String text1 = pluralText;
            String text2 = singularText;

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
            String text1 = pluralText;
            String text2 = singularText;

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
            return new String[]{singularText, pluralText};
        }
    }

    static class RegExAffix extends IgnorableAffix{
        private static final Comparator<String> LENGTH_DESC_COMPARATOR = (o1, o2) -> o2.length() - o1.length();

        private final String[] suffixes;
        private final Pattern[] patterns;

        // The parse method has to iterate over the suffixes from the longest one to the shortest one
        // Otherwise it might consume not enough characters.
        private final String[] suffixesSortedDescByLength;

        RegExAffix(String[] regExes, String[] texts){
            suffixes = texts.clone();
            patterns = new Pattern[regExes.length];
            for(int i = 0; i < regExes.length; i++){
                Pattern pattern = PATTERNS.get(regExes[i]);
                if(pattern == null){
                    pattern = Pattern.compile(regExes[i]);
                    PATTERNS.putIfAbsent(regExes[i], pattern);
                }
                patterns[i] = pattern;
            }
            suffixesSortedDescByLength = suffixes.clone();
            Arrays.sort(suffixesSortedDescByLength, LENGTH_DESC_COMPARATOR);
        }

        private int selectSuffixIndex(int value){
            String valueString = String.valueOf(value);
            for(int i = 0; i < patterns.length; i++){
                if(patterns[i].matcher(valueString).matches()){
                    return i;
                }
            }
            return patterns.length - 1;
        }

        @Override
        public int calculatePrintedLength(int value){
            return suffixes[selectSuffixIndex(value)].length();
        }

        @Override
        public void formatTo(StringBuilder buf, int value){
            buf.append(suffixes[selectSuffixIndex(value)]);
        }

        @Override
        public int parse(String periodStr, int position){
            for(String text : suffixesSortedDescByLength){
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
                for(String text : suffixesSortedDescByLength){
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
            return suffixes.clone();
        }
    }

    static class CompositeAffix extends IgnorableAffix{
        private final PeriodFieldAffix left;
        private final PeriodFieldAffix right;
        private final String[] leftRightCombinations;

        CompositeAffix(PeriodFieldAffix left, PeriodFieldAffix right){
            this.left = Objects.requireNonNull(left, "left");
            this.right = Objects.requireNonNull(right, "right");

            // We need to construct all possible combinations of left and right.
            // We are doing it once in constructor so that getAffixes() is quicker.
            Set<String> result = new HashSet<>();
            for(String leftText : this.left.getAffixes()){
                for(String rightText : this.right.getAffixes()){
                    result.add(leftText + rightText);
                }
            }
            leftRightCombinations = result.toArray(new String[0]);
        }

        @Override
        public int calculatePrintedLength(int value){
            return left.calculatePrintedLength(value)
                    + right.calculatePrintedLength(value);
        }

        @Override
        public void formatTo(StringBuilder buf, int value){
            left.formatTo(buf, value);
            right.formatTo(buf, value);
        }

        @Override
        public int parse(String periodStr, int position){
            int pos = left.parse(periodStr, position);
            if(pos >= 0){
                pos = right.parse(periodStr, pos);
                if(pos >= 0 && matchesOtherAffix(parse(periodStr, pos) - pos, periodStr, position)){
                    return ~position;
                }
            }
            return pos;
        }

        @Override
        public int scan(String periodStr, int position){
            int leftPosition = left.scan(periodStr, position);
            if(leftPosition >= 0){
                int rightPosition = right.scan(periodStr, left.parse(periodStr, leftPosition));
                if(!(rightPosition >= 0 && matchesOtherAffix(right.parse(periodStr, rightPosition) - leftPosition, periodStr, position))){
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
            return leftRightCombinations.clone();
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
            this.fieldFormatters = Objects.requireNonNull(fieldFormatters, "fieldFormatters");
            this.prefix = prefix;
            this.suffix = suffix;
        }

        FieldFormatter(FieldFormatter field, PeriodFieldAffix suffix){
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(suffix, "suffix");
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
            Objects.requireNonNull(fieldFormatters, "fieldFormatters");
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
        public int countFieldsToFormat(Duration duration, int stopAt, Locale locale){
            if(stopAt <= 0){
                return 0;
            }
            if(printZeroSetting == PRINT_ZERO_ALWAYS || getFieldValue(duration) != Long.MAX_VALUE){
                return 1;
            }
            return 0;
        }

        @Override
        public int calculateFormattedLength(Duration duration, Locale locale){
            long valueLong = getFieldValue(duration);
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
        public void formatTo(StringBuilder buf, Duration duration, Locale locale){
            long valueLong = getFieldValue(duration);
            if(valueLong == Long.MAX_VALUE){
                return;
            }
            int value = (int)valueLong;

            if(prefix != null){
                prefix.formatTo(buf, value);
            }
            int minDigits = minPrintedDigits;
            if(minDigits <= 1){
                appendUnpaddedInteger(buf, value);
            }else{
                appendPaddedInteger(buf, value, minDigits);
            }
            if(suffix != null){
                suffix.formatTo(buf, value);
            }
        }

        long getFieldValue(Duration duration){
            long value;

            switch(fieldType){
                case DAYS:
                    value = duration.toDaysPart();
                    break;
                case HOURS:
                    value = duration.toHoursPart();
                    break;
                case MINUTES:
                    value = duration.toMinutesPart();
                    break;
                case SECONDS:
                    value = duration.toSecondsPart();
                    break;
                case MILLIS:
                    value = duration.toMillisPart();
                    break;
                default:
                    return Long.MAX_VALUE;
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

        private final String text;

        Literal(String text){
            this.text = Objects.requireNonNull(text, "text");
        }

        @Override
        public int countFieldsToFormat(Duration duration, int stopAt, Locale locale){
            return 0;
        }

        @Override
        public int calculateFormattedLength(Duration duration, Locale locale){
            return text.length();
        }

        @Override
        public void formatTo(StringBuilder buf, Duration duration, Locale locale){
            buf.append(text);
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
            this.text = Objects.requireNonNull(text, "text");
            this.finalText = Objects.requireNonNull(finalText, "finalText");
            this.beforePrinter = Objects.requireNonNull(beforePrinter, "beforePrinter");
            this.useBefore = useBefore;
            this.useAfter = useAfter;
        }

        @Override
        public int countFieldsToFormat(Duration duration, int stopAt, Locale locale){
            int sum = beforePrinter.countFieldsToFormat(duration, stopAt, locale);
            if(sum < stopAt){
                sum += afterPrinter.countFieldsToFormat(duration, stopAt, locale);
            }
            return sum;
        }

        @Override
        public int calculateFormattedLength(Duration duration, Locale locale){
            DurationPrinter before = beforePrinter;
            DurationPrinter after = afterPrinter;

            int sum = before.calculateFormattedLength(duration, locale)
                    + after.calculateFormattedLength(duration, locale);

            if(useBefore){
                if(before.countFieldsToFormat(duration, 1, locale) > 0){
                    if(useAfter){
                        int afterCount = after.countFieldsToFormat(duration, 2, locale);
                        if(afterCount > 0){
                            sum += (afterCount > 1 ? text : finalText).length();
                        }
                    }else{
                        sum += text.length();
                    }
                }
            }else if(useAfter && after.countFieldsToFormat(duration, 1, locale) > 0){
                sum += text.length();
            }

            return sum;
        }

        @Override
        public void formatTo(StringBuilder buf, Duration duration, Locale locale){
            DurationPrinter before = beforePrinter;
            DurationPrinter after = afterPrinter;

            before.formatTo(buf, duration, locale);
            if(useBefore){
                if(before.countFieldsToFormat(duration, 1, locale) > 0){
                    if(useAfter){
                        int afterCount = after.countFieldsToFormat(duration, 2, locale);
                        if(afterCount > 0){
                            buf.append(afterCount > 1 ? text : finalText);
                        }
                    }else{
                        buf.append(text);
                    }
                }
            }else if(useAfter && after.countFieldsToFormat(duration, 1, locale) > 0){
                buf.append(text);
            }
            after.formatTo(buf, duration, locale);
        }

        Separator finish(DurationPrinter afterPrinter){
            this.afterPrinter = afterPrinter;
            return this;
        }
    }

    static void appendPaddedInteger(StringBuilder buf, int value, int size){
        if(value < 0){
            buf.append('-');
            if(value != Integer.MIN_VALUE){
                value = -value;
            }else{
                for(; size > 10; size--){
                    buf.append('0');
                }
                buf.append(-(long)Integer.MIN_VALUE);
                return;
            }
        }
        if(value < 10){
            for(; size > 1; size--){
                buf.append('0');
            }
            buf.append((char)(value + '0'));
        }else if(value < 100){
            for(; size > 2; size--){
                buf.append('0');
            }
            // Calculate value div/mod by 10 without using two expensive
            // division operations. (2 ^ 27) / 10 = 13421772. Add one to
            // value to correct rounding error.
            int d = (value + 1) * 13421772 >> 27;
            buf.append((char)(d + '0'));
            // Append remainder by calculating (value - d * 10).
            buf.append((char)(value - (d << 3) - (d << 1) + '0'));
        }else{
            int digits = Mathf.digits(value);
            for(; size > digits; size--){
                buf.append('0');
            }
            buf.append(value);
        }
    }

    static void appendUnpaddedInteger(StringBuilder buf, int value){
        if(value < 0){
            buf.append('-');
            if(value != Integer.MIN_VALUE){
                value = -value;
            }else{
                buf.append(-(long)Integer.MIN_VALUE);
                return;
            }
        }
        if(value < 10){
            buf.append((char)(value + '0'));
        }else if(value < 100){
            // Calculate value div/mod by 10 without using two expensive
            // division operations. (2 ^ 27) / 10 = 13421772. Add one to
            // value to correct rounding error.
            int d = (value + 1) * 13421772 >> 27;
            buf.append((char)(d + '0'));
            // Append remainder by calculating (value - d * 10).
            buf.append((char)(value - (d << 3) - (d << 1) + '0'));
        }else{
            buf.append(value);
        }
    }
}
