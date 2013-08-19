package org.unicode.cldr.tool;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.tool.GeneratedPluralSamples.Range.Status;
import org.unicode.cldr.tool.Option.Options;
import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo.Count;
import org.unicode.cldr.util.SupplementalDataInfo.PluralType;

import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.Relation;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.text.PluralRules.FixedDecimal;

public class GeneratedPluralSamples {
    static TestInfo testInfo = TestInfo.getInstance();
    static Info INFO = new Info();

    private static final int SAMPLE_LIMIT = 8;
    private static final int UNBOUNDED_LIMIT = 20;
    private static final String RANGE_SEPARATOR = "~";
    public static final String SEQUENCE_SEPARATOR = ", ";

    static class Range implements Comparable<Range>{
        // invariant: visibleFractionDigitCount are the same
        private final long startValue;
        private long endValue;
        final long offset;
        final long visibleFractionDigitCount;

        /**
         * Must only be called if visibleFractionDigitCount are the same.
         */
        public Range(FixedDecimal start, FixedDecimal end) {
            int temp = 1;
            for (long i = start.getVisibleDecimalDigitCount(); i > 0; --i) {
                temp *= 10;
            }
            offset = temp;
            visibleFractionDigitCount = start.getVisibleDecimalDigitCount();
            startValue = start.getIntegerValue() * offset + start.getDecimalDigits();
            endValue = end.getIntegerValue() * offset + end.getDecimalDigits();
            if (startValue < 0 || endValue < 0) {
                throw new IllegalArgumentException("Must not be negative");
            }
        }
        public Range(Range other) {
            startValue = other.startValue;
            endValue = other.endValue;
            offset = other.offset;
            visibleFractionDigitCount = other.visibleFractionDigitCount;
        }
        @Override
        public int compareTo(Range o) {
            // TODO Auto-generated method stub
            int diff = startValue == o.startValue ? 0 : startValue < o.startValue ? -1 : 1;
            if (diff != 0) {
                return diff;
            }
            return endValue == o.endValue ? 0 : endValue < o.endValue ? -1 : 1;
        }
        enum Status {inside, rightBefore, other}
        /**
         * Must only be called if visibleFractionDigitCount are the same.
         */
        Status getStatus(FixedDecimal ni) {
            long newValue = ni.getIntegerValue() * offset + ni.getDecimalDigits();
            if (newValue < 0) {
                throw new IllegalArgumentException("Must not be negative");
            }
            Status status = startValue <= newValue && newValue <= endValue ? Status.inside
                    : endValue + 1 == newValue ? Status.rightBefore 
                            : Status.other;
            if (status == Status.rightBefore) {
                endValue = newValue; // just extend it
            }
            return status;
        }
        public StringBuilder format(StringBuilder b) {
            if (visibleFractionDigitCount == 0) {
                b.append(startValue);
                if (startValue != endValue) {
                    b.append(startValue + 1 == endValue ? SEQUENCE_SEPARATOR : RANGE_SEPARATOR).append(endValue);
                }
            } else {
                append(b, startValue, visibleFractionDigitCount);
                if (startValue != endValue) {
                    b.append(startValue + 1 == endValue ? SEQUENCE_SEPARATOR : RANGE_SEPARATOR);
                    append(b, endValue, visibleFractionDigitCount);
                }
            }
            return b;
        }
        public String toString() {
            return format(new StringBuilder()).toString();
        }
    }

    private static void append(StringBuilder b, long startValue2, long visibleFractionDigitCount2) {
        int len = b.length();
        for (int i = 0; i < visibleFractionDigitCount2; ++i) {
            b.insert(len, startValue2%10);
            startValue2 /= 10;
        }
        b.insert(len,'.');
        b.insert(len, startValue2);
    }

    public static long getFlatValue(FixedDecimal start) {
        int temp = 1;
        for (long i = start.getVisibleDecimalDigitCount(); i != 0; i /= 10) {
            temp *= 10;
        }
        return start.getIntegerValue() * temp + start.getDecimalDigits();
    }

    /**
     * Add-only set of ranges.
     */
    static class Ranges {
        Set<Range>[] data = new Set[5];
        int size = 0;
        {
            for (int i = 0; i < data.length; ++i) {
                data[i] = new TreeSet<Range>();
            }
        }
        public Ranges(Ranges other) {
            for (int i = 0; i < data.length; ++i) {
                for (Range range : other.data[i]) {
                    data[i].add(new Range(range));
                }
            }
        }
        public Ranges() {
            // TODO Auto-generated constructor stub
        }
        void add(FixedDecimal ni) {
            Set<Range> set = data[ni.getVisibleDecimalDigitCount()];
            for (Range item : set) {
                switch (item.getStatus(ni)) {
                case inside: 
                    return;
                case rightBefore:
                    ++size;
                    return;
                }
            }
            set.add(new Range(ni,ni));
            ++size;
        }
        public int size() {
            return size;
        }
        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            for (Set<Range> datum : data) {
                for (Range range : datum) {
                    if (b.length() != 0) {
                        b.append(", ");
                    }
                    range.format(b);
                }
            }
            return b.toString();
        }
        public void trim(int sampleLimit) {
            // limit to a total of sampleLimit ranges, *but* also include have at least one of each fraction length
            for (int i = 0; i < data.length; ++i) {
                if (sampleLimit < 2) {
                    sampleLimit = 2;
                }
                for (Iterator it = data[i].iterator(); it.hasNext();) {
                    it.next();
                    --sampleLimit;
                    if (sampleLimit < 0) {
                        it.remove();
                    }
                }
            }

        }
    }

    static class Info {
        Set<String> bounds = new TreeSet();

        public void add(String string) {
            if (string != null && !string.isEmpty()) {
                bounds.add(string);
            }
        }

        public void print() {
            for (String infoItem : bounds) {
                System.err.println(infoItem);
            }
        }
    }

    static class DataSample {
        int count;
        int countNoTrailing = -1;
        final Set<Double> noTrailing = new HashSet();
        final Ranges samples = new Ranges();
        final FixedDecimal[] digitToSample = new FixedDecimal[10];
        final boolean isInteger;

        public DataSample(boolean isInteger) {
            this.isInteger = isInteger;
        }
        public String toString(boolean isKnownBounded, String keyword, String rule) {
            boolean first = false;
            if (countNoTrailing < 0) {
                countNoTrailing = noTrailing.size();
                first = true;
            }
            boolean isBounded = computeBoundedWithSize(isKnownBounded, keyword, rule, first);
            if (countNoTrailing >= 0) {
                noTrailing.clear(); // to avoid running out of memory.
            }

            if (!isBounded) {
                samples.trim(SAMPLE_LIMIT);  // to avoid running out of memory.
            }
            Ranges samples2 = new Ranges(samples);
            for (FixedDecimal ni : digitToSample) {
                if (ni != null) {
                    samples2.add(ni);
                }
            }
            return samples2 + (isBounded ? "" : ", …");
        }

        public boolean computeBoundedWithSize(boolean isKnownBounded, String keyword, String rule, boolean first) {
            boolean isBounded = isKnownBounded || countNoTrailing < UNBOUNDED_LIMIT;
            if (isBounded != isKnownBounded && first) {
                INFO.add((isInteger ? "integer" : "decimal") 
                        + " computation from rule ≠ from items, rule: " + keyword + ": " + rule 
                        + "; count: " + noTrailing);
            }
            return isBounded;
        }

        private void add(FixedDecimal ni) {
            ++count;
            if (samples.size() < SAMPLE_LIMIT) {
                samples.add(ni);
            }
            if (noTrailing.size() <= UNBOUNDED_LIMIT) {
                noTrailing.add(ni.source);
            }
            int digit = getDigit(ni);
            if (digitToSample[digit] == null) {
                digitToSample[digit] = ni;
            }
        }
        @Override
        public boolean equals(Object obj) {
            DataSample other = (DataSample)obj;
            return count == other.count
                    && samples.equals(other.samples)
                    && digitToSample.equals(other.digitToSample);
        }
        @Override
        public int hashCode() {
            // TODO Auto-generated method stub
            return count ^ samples.hashCode() ^ Arrays.asList(digitToSample).hashCode();
        }
    }

    class DataSamples {
        private final String keyword; // for debugging
        private final String rule;
        private final DataSample integers = new DataSample(true);
        private final DataSample decimals = new DataSample(false);
        private final boolean isKnownIntegerBounded;
        private final boolean isKnownDecimalBounded;
        private boolean boundsComputed;

        DataSamples(String keyword, String rule) {
            this.keyword = keyword;
            this.rule = rule;
            isKnownIntegerBounded = computeBounded(rule, true);
            isKnownDecimalBounded = computeBounded(rule, false);
        }

        private void add(FixedDecimal ni) {
            if (boundsComputed) {
                throw new IllegalArgumentException("Can't call 'add' after 'toString'");
            }
            if (ni.getVisibleDecimalDigitCount() == 0) {
                integers.add(ni);
            } else {
                decimals.add(ni);
            }
        }
        public String toString() {
            boundsComputed = true;
            String integersString = integers.toString(isKnownIntegerBounded, keyword, rule);
            String decimalsString = type == PluralType.ordinal ? "" 
                    : decimals.toString(isKnownDecimalBounded, keyword, rule);
            return (integersString.isEmpty() ? "\t\t" : "\t@integers\t" + integersString)
                    + (decimalsString.isEmpty() ? "" : "\t@decimals\t" + decimalsString);
        }
        @Override
        public boolean equals(Object obj) {
            DataSamples other = (DataSamples)obj;
            return integers.equals(other.integers) && decimals.equals(other.decimals);
        }
    }

    static boolean computeBounded(String orRule, boolean integer) {
        if (orRule == null) {
            return false;
        }
        // every 'or' rule must be bounded for the whole thing to be
        for (String andRule : orRule.split("\\s*or\\s*")) {
            boolean intBounded = false;
            boolean decBounded = integer; // when gathering for integers, dec is bounded.
            // if any 'and' rule is bounded, then the 'or' rule is
            boolean specificInteger;
            for (String atomicRule : andRule.split("\\s*and\\s*")) {
                char operand = atomicRule.charAt(0);
                String remainder = atomicRule.substring(1).trim();
                // check to see that the integer values are bounded and that the decimal values are
                // once this happens, then the 'and' rule is bounded.
                
                // if the fractional parts must be zero, then this rule is empty for decimals (and thus bounded)
                decBounded |= (operand == 'v' || operand == 'w' || operand == 'f' || operand == 't')
                        && remainder.equals("is 0");
                // if f and t cannot be zero, then this rule is empty for integers (and thus bounded)
                intBounded |= (operand == 'f' || operand == 't') 
                        && (remainder.equals("is 1") || remainder.equals("is not 0")); // should flesh out with parser
                
                if(!atomicRule.contains("mod") && !atomicRule.contains("not")&& !atomicRule.contains("!")) {
                    intBounded |= operand == 'i' || operand == 'n';
                    decBounded |= operand == 'n' && !atomicRule.contains("within");
                }
                if (intBounded && decBounded) {
                    break;
                }
            }
            if (!intBounded && !decBounded) {
                return false;
            }
        }
        return true;
    }

    static private int getDigit(FixedDecimal ni) {
        int result = 0;
        long value = ni.getIntegerValue();
        do {
            ++result;
            value /= 10;
        } while (value != 0);
        return result;
    }

    private final TreeMap<String,DataSamples> keywordToData = new TreeMap();
    private final PluralType type;

    GeneratedPluralSamples(PluralInfo pluralInfo, PluralType type) {
        this.type = type;
        PluralInfo pluralRule = pluralInfo;
        // 9999, powers; no decimals
        collect(pluralRule, 10000, 0);
        collect10s(pluralRule, 100000, 0);

        if (type != PluralType.cardinal) {
            return;
        }

        // 9999.9, powers .0
        collect(pluralRule, 10000, 1);
        collect10s(pluralRule, 1000000, 1);

        // 999.99, powers .00
        collect(pluralRule, 1000, 2);
        collect10s(pluralRule, 1000000, 2);

        // 99.999, powers .000
        collect(pluralRule, 100, 3);
        collect10s(pluralRule, 1000000, 3);

        // 9.9999, powers .0000
        collect(pluralRule, 10, 4);
        collect10s(pluralRule, 1000000, 4);
    }

    private void collect10s(PluralInfo pluralInfo, int limit, int decimals) {
        double power = Math.pow(10, decimals);
        for (long i = 1; i <= limit*(int)power; i *= 10) {
            add(pluralInfo, i/power, decimals);
        }
    }

    private void collect(PluralInfo pluralInfo, int limit, int decimals) {
        double power = Math.pow(10, decimals);
        for (int i = 0; i <= limit*(int)power; ++i) {
            add(pluralInfo, i/power, decimals);
        }
    }

    private void add(PluralInfo pluralInfo, double d, int visibleDecimals) {
        FixedDecimal ni = new FixedDecimal(d, visibleDecimals);
        PluralRules pluralRules = pluralInfo.getPluralRules();
        String keyword = pluralRules.select(ni);

        INFO.add(checkForDuplicates(pluralRules, ni));
        DataSamples data = keywordToData.get(keyword);
        if (data == null) {
            keywordToData.put(keyword, data = new DataSamples(keyword, pluralInfo.getRule(Count.valueOf(keyword))));
        }
        data.add(ni);
    }

    public static String checkForDuplicates(PluralRules pluralRules, FixedDecimal ni) {
        // add test that there are no duplicates
        Set<String> keywords = new LinkedHashSet();
        for (String keywordCheck : pluralRules.getKeywords()) {
            if (pluralRules.matches(ni, keywordCheck)) {
                keywords.add(keywordCheck);
            }
        }
        if (keywords.size() != 1) {
            String message = "";
            for (String keywordCheck : keywords) {
                message += keywordCheck + ": " + pluralRules.getRules(keywordCheck) + "; ";
            }
            return "Duplicate rules with " + ni + ":\t" + message;
        }
        return null;
    }

    private DataSamples getData(String keyword) {
        return keywordToData.get(keyword);
    }

    @Override
    public boolean equals(Object obj) {
        return keywordToData.equals(((GeneratedPluralSamples)obj).keywordToData);
    }

    @Override
    public int hashCode() {
        return keywordToData.hashCode();
    }

    final static Options myOptions = new Options();

    enum MyOptions {
        output(".*", CldrUtility.GEN_DIRECTORY + "picker/", "output data directory"),
        filter(".*", null, "filter locales"),
        xml(null, null, "xml file format");
        // boilerplate
        final Option option;

        MyOptions(String argumentPattern, String defaultArgument, String helpText) {
            option = myOptions.add(this, argumentPattern, defaultArgument, helpText);
        }
    }

    public static void main(String[] args) throws Exception {
        myOptions.parse(MyOptions.filter, args, true);


        Matcher localeMatcher = !MyOptions.filter.option.doesOccur() ? null : Pattern.compile(MyOptions.filter.option.getValue()).matcher("");
        boolean fileFormat = MyOptions.xml.option.doesOccur();
        
        
        computeBounded("n is not 0 and n mod 1000000 is 0", false);
        int failureCount = 0;

        PluralRules pluralRules2 = PluralRules.createRules("one: n is 3..9; two: n is 7..12");
        checkForDuplicates(pluralRules2 , new FixedDecimal(8));

        for (PluralType type : PluralType.values()) {
            if (fileFormat) {
                WritePluralRules.writePluralHeader(type);
            }
            Set<String> locales = testInfo.getSupplementalDataInfo().getPluralLocales(type);
            Relation<PluralInfo, String> seenAlready = Relation.of(new TreeMap(), TreeSet.class);

            //System.out.println(type + ": " + locales);
            for (String locale : locales) {
                if (localeMatcher != null && !localeMatcher.reset(locale).find()) {
                    continue;
                }
                PluralInfo pluralInfo = testInfo.getSupplementalDataInfo().getPlurals(type, locale);
                seenAlready.put(pluralInfo, locale);
            }

            Relation<GeneratedPluralSamples, PluralInfo> samplesToPlurals = Relation.of(new LinkedHashMap(), LinkedHashSet.class);
            for (Entry<PluralInfo, Set<String>> entry : seenAlready.keyValuesSet()) {
                PluralInfo pluralInfo = entry.getKey();
                Set<String> equivalentLocales = entry.getValue();

                if (fileFormat) {
                    WritePluralRules.writePluralRuleHeader(equivalentLocales);
                }
                PluralRules pluralRules = pluralInfo.getPluralRules();
                GeneratedPluralSamples samples = new GeneratedPluralSamples(pluralInfo, type);
                samplesToPlurals.put(samples, pluralInfo);
                for (String keyword : pluralRules.getKeywords()) {
                    Count count = Count.valueOf(keyword);
                    String rule = pluralInfo.getRule(count);
                    if (rule == null && count != Count.other) {
                        pluralInfo.getRule(count);
                        throw new IllegalArgumentException("No rule for " + count);
                    }
                    String representative = equivalentLocales.iterator().next();
                    if (!fileFormat) {
                        System.out.print(type + "\t" + representative + "\t" + keyword + "\t" + (rule == null ? "" : rule));
                    }
                    DataSamples data = samples.getData(keyword);
                    if (data == null) {
                        System.err.println("***Failure");
                        failureCount++;
                        continue;
                    }
                    if (fileFormat) {
                        String fileRule = ((rule == null ? "" : rule) + data.toString())
                                .replace("\t@", "\n                          @").replace('\t', ' ');
                        WritePluralRules.writePluralRule(keyword, fileRule);
                    } else {
                        System.out.println(data.toString());
                    }
                }
                if (fileFormat) {
                    WritePluralRules.writePluralRuleFooter();
                } else {
                    System.out.println();
                }
            }
            if (fileFormat) {
                WritePluralRules.writePluralFooter();
            } else {
                for (Entry<PluralInfo, Set<String>> entry : seenAlready.keyValuesSet()) {
                    if (entry.getValue().size() == 1) {
                        continue;
                    }
                    Set<String> remainder = new LinkedHashSet(entry.getValue());
                    String first = remainder.iterator().next();
                    remainder.remove(first);
                    System.err.println(type + "\tEQUIV:\t\t" + first + "\t≣\t" + CollectionUtilities.join(remainder, ", "));
                }
                System.out.println();
            }
            for (Entry<GeneratedPluralSamples, Set<PluralInfo>> entry : samplesToPlurals.keyValuesSet()) {
                Set<PluralInfo> set = entry.getValue();
                if (set.size() != 1) {
                    System.err.println("***Failure: Duplicate results " + set);
                    failureCount++;
                }
            }
        }
        if (failureCount > 0) {
            System.err.println("***Failures: " + failureCount);
        }
        INFO.print();
    }
}