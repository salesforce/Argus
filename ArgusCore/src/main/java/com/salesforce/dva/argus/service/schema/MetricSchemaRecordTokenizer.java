package com.salesforce.dva.argus.service.schema;

import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.service.SchemaService.RecordType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class MetricSchemaRecordTokenizer {

    public static SortedSet<String> GetUniqueTokens(List<MetricSchemaRecord> records, RecordType type, int indexLevel) {

        SortedSet<String> tokens = new TreeSet<>();
        String _delimiterRegex = "\\.";

        List<String> entries = _getValueForType(records, type);

        for(String entry : entries) {

            String[] allTokens = entry.split(_delimiterRegex);

            if(allTokens.length > indexLevel) {
                tokens.add(allTokens[indexLevel]);
            }
        }
        return tokens;
    }

    private static List<String> _getValueForType(List<MetricSchemaRecord> records, RecordType type) {

        List<String> result=new ArrayList<>();

        for(MetricSchemaRecord record:records){
            result.add(_getValueForType(record, type));
        }

        return result;
    }

    private static String _getValueForType(MetricSchemaRecord record, RecordType type) {
        switch (type) {
            case NAMESPACE:
                return record.getNamespace();
            case SCOPE:
                return record.getScope();
            case METRIC:
                return record.getMetric();
            case TAGK:
                return record.getTagKey();
            case TAGV:
                return record.getTagValue();
            default:
                return null;
        }
    }
}
