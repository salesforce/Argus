package com.salesforce.dva.argus.service.tsdb;

import java.util.ArrayList;
import java.util.List;

import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.metric.transform.AbstractArithmeticTransform;
import com.salesforce.dva.argus.service.metric.transform.AnomalyDetectionGaussianTransform;
import com.salesforce.dva.argus.service.metric.transform.AnomalyDetectionTransform;
import com.salesforce.dva.argus.service.metric.transform.Transform;
import com.salesforce.dva.argus.service.metric.transform.ValueMapping;
import com.salesforce.dva.argus.service.metric.transform.ValueReducerOrMapping;

/**
 * Defines the shifts associated with each type of Transform, adding additional timestamp shifts
 * from the shift already associated with a used scanner object. Allows the difference between
 * input and output timestamps to be tracked as transforms are cascaded on each other.
 * 
 * @author adelaide.chambers
 *
 */
public enum PageScannerShifts {

	DEVIATION("DEVIATION"), // DeviationVROM , MetricReducerOrMappingT(WithConstant)
	DIFF("DIFF"), // DiffVROM , MetricReducerOrMappingT(WithConstant)
	DIVIDE("DIVIDE"), // DivideVROM , MetricReducerOrMappingT(WithConstant)
	PERCENTILE("PERCENTILE"), // PercentileVROM , MetricReducerOrMappingT(WithConstant)
	RANGE("RANGE"), // RangeVROM , RangeTWrap , MetricReducerOrMappingT(WithConstant)
	SCALE("SCALE"), // ScaleVROM , MetricReducerOrMappingT(WithConstant)
	SUM("SUM"), // SumVROM , MetricReducerOrMappingT(WithConstant)
	ABSOLUTE("ABSOLUTE"), //AbsoluteVM , MetricMappingT
	CONSECUTIVE("CONSECUTIVE"), // ConsecutiveVM , MetricMappingT
	CULL_ABOVE("CULL_ABOVE"), // CullAboveVM , MetricMappingT
	CULL_BELOW("CULL_BELOW"), // CullBelowVM , MetricMappingT
	DERIVATIVE("DERIVATIVE"), // DerivativeVM , DerivativeT , MetricMappingT
	INTEGRAL("INTEGRAL"), // IntegralVM , IntegralT , MetricMappingT
	LOG("LOG"), // LogVM , MetricMappingT
	MOVING("MOVING"), // MovingVM , MetricMappingT
	SHIFT("SHIFT"), // ShiftVM , MetricMppingT
	ALIAS_BY_TAG("ALIASBYTAG"), // AliasByTagT
	ALIAS("ALIAS"), // AliasT
	ANOMALY_DENSITY("ANOMALY_DENSITY"), // AnomalyDetectionGaussianDensityT
	ANOMALY_ZSCORE("ANOMALY_ZSCORE"), // AnomalyDetectioNGaussianZScoreT
	ANOMALY_KMEANS("ANOMALY_KMEANS"), // AnomalyDetectionKMeansT
	ANOMALY_RPCA("ANOMALY_RPCA"), // AnomalyDetectionRPCAT
	ANOMALY_STL("ANOMALY_STL"), // AnomalySTLT
	AVERAGE_BELOW("AVERAGEBELOW"), // AverageBelowT
	COUNT("COUNT"), // CountTWrapUnion , MetricReducerT
	DOWNSAMPLE("DOWNSAMPLE"), // DonwsampleT
	EXCLUDE("EXCLUDE"), // ExcludeTWrap
	FILL_CALCULATE("FILL_CALCULATE"), // FillCalculateT
	FILL("FILL"), // FillT
	GROUP_BY("GROUPBY"), // GroupByT
	GROUP_WRAP("GROUP"), // GroupTWrapUnion
	HW_DEVIATION("HW_DEVIATION"), // HoltWintersDeviation
	HW_FORECAST("HW_FORECAST"), // HoltWintersForecast
	IDENTITY("IDENTITY"), // IdentityT
	INCLUDE("INCLUDE"), // IncludeT
	JOIN("JOIN"), // JoinT
	LIMIT("LIMIT"), // LimitT
	NORMALIZE("NORMALIZE"), // NormalizeTWrap
	PROPAGATE("PROPAGATE"), // PropagateT
	SORT("SORT"), // SortTWrapAboveAndBelow
	ZERO_IF_MISSING("ZEROIFMISSINGSUM"), //ZeroIfMissingSumT
	ABOVE("ABOVE"), // MetricFilterWithInteralReducerT
	BELOW("BELOW"), // MetricFilterWithInteralReducerT
	HIGHEST("HIGHEST"), // MetricFilterWithInteralReducerT
	LOWEST("LOWEST"), // MetricFilterWithInteralReducerT
	AVERAGE("AVERAGE"), // MetricReducerT
	MAX("MAX"), // MetricReducerT
	MIN("MIN"), // MetricReducerT
	UNION("UNION"), // MetricReducerT , MetricUnionTransform
	DIFF_ZIP("DIFF_V"), // MetricZipperT
	DIVIDE_ZIP("DIVIDE_V"), // MetricZipperT
	SCALE_ZIP("SCALE_V"), // MetricZipperT
	SUM_ZIP("SUM_V"), // MetricZipperT
	;
	
	private final String _name;
	
	private PageScannerShifts(String name) {
		_name = name;
	}
	
	public static PageScannerShifts fromString(String name) {
		if (name != null) {
			for (PageScannerShifts t : PageScannerShifts.values()) {
				if (name.equalsIgnoreCase(t.getName())) {
					return t;
				}
			}
		}
		throw new IllegalArgumentException(name);
	}
	
	public String getName() {
		return _name;
	}
	
	protected static ScannerShiftFunction getValueMappingShift(ScannerShiftFunction prevShift, ValueMapping mapping, List<String> constants) {
		PageScannerShifts type = PageScannerShifts.fromString(mapping.name());
		switch(type) {
			case ABSOLUTE:
				return prevShift;
			case CONSECUTIVE:
				return prevShift;
			case CULL_ABOVE:
				return prevShift;
			case CULL_BELOW:
				return prevShift;
			case DERIVATIVE:
				return prevShift;
			case INTEGRAL:
				return prevShift;
			case LOG:
				return prevShift;
			case MOVING:
				return prevShift;
			case SHIFT:
				ScannerShiftFunction shift = (Long t) -> { return prevShift.shift(t) + getOffsetInMilliseconds(constants.get(0)); };
				return shift;
			default:
				throw new UnsupportedOperationException("ValueMapping " + mapping.name() + " is not yet supported by pager!");
		}
	}
	
	protected static ScannerShiftFunction getValueReducerOrMappingShift(ScannerShiftFunction prevShift, ValueReducerOrMapping redMap, List<String> constants) {		
		PageScannerShifts type = PageScannerShifts.fromString(redMap.name());
		
		switch (type) {
			case DEVIATION:
				return prevShift;
			case DIFF:
				return prevShift;
			case DIVIDE:
				return prevShift;
			case PERCENTILE:
				return prevShift;
			case RANGE:
				return prevShift;
			case SCALE:
				return prevShift;
			case SUM:
				return prevShift;
			default:
				throw new UnsupportedOperationException("ValueMappingOrReducer " + redMap.name() + " is not yet supported by pager!");
		}
	}
	
	protected static ScannerShiftFunction getTransformShift(ScannerShiftFunction prevShift, Transform transform, List<String> constants) {
		try {
			return getTransformShiftFromString(prevShift, transform.getResultScopeName(), constants);
		} catch (UnsupportedOperationException ex) {
			/* could be an abstract class with sufficiently defined shifting behavior */
			if (AbstractArithmeticTransform.class.isInstance(transform)) {
				return prevShift;
			} else if (AnomalyDetectionGaussianTransform.class.isInstance(transform)) {
				return prevShift;
			} else if (AnomalyDetectionTransform.class.isInstance(transform)) {
				return prevShift;
			}
			
			throw new UnsupportedOperationException("Transform " + transform.getResultScopeName() + " is not yet supported by pager!");
		}
	}
	
	protected static ScannerShiftFunction getTransformShiftFromString(ScannerShiftFunction prevShift, String scopeName, List<String> constants) {
		PageScannerShifts type;
		try {
			type = PageScannerShifts.fromString(scopeName);
		} catch (Exception ex) {
			throw new UnsupportedOperationException("Transform " + scopeName + " is not yet supported by pager.");
		}
		
		switch (type) {
			case ABOVE:
				return prevShift;
			case ABSOLUTE:
				return prevShift;
			case ALIAS_BY_TAG:
				return prevShift;
			case ALIAS: 
				return prevShift;
			case ANOMALY_DENSITY:
				return prevShift;
			case ANOMALY_ZSCORE:
				return prevShift;
			case ANOMALY_KMEANS:
				return prevShift;
			case ANOMALY_RPCA:
				return prevShift;
			case ANOMALY_STL:
				return prevShift;
			case AVERAGE:
				return prevShift;
			case AVERAGE_BELOW:
				return prevShift;
			case BELOW:
				return prevShift;
			case CONSECUTIVE:
				return prevShift;
			case COUNT:
				return prevShift;
			case CULL_ABOVE:
				return prevShift;
			case CULL_BELOW:
				return prevShift;
			case DERIVATIVE:
				return prevShift;
			case DEVIATION:
				return prevShift;
			case DIFF:
				return prevShift;
			case DIFF_ZIP:
				return prevShift;
			case DIVIDE:
				return prevShift;
			case DIVIDE_ZIP:
				return prevShift;
			case DOWNSAMPLE:
				ScannerShiftFunction shift = (Long t) -> { return prevShift.shift(t) - 
								prevShift.shift(t) % getOffsetInMilliseconds(constants.get(0).split("-")[0]); };
				return shift;
			case EXCLUDE:
				return prevShift;
			case FILL_CALCULATE:
				if (constants.size() == 1) {
					return prevShift;
				} else {
					shift = (Long t) -> { return prevShift.shift(t) + getOffsetInMilliseconds(constants.get(2)); };
				}
				return shift;
			case FILL:
				shift = (Long t) -> { return prevShift.shift(t) + getOffsetInMilliseconds(constants.get(1)); };
				return shift;
			case GROUP_BY:
				return getTransformShiftFromString(prevShift, constants.get(1), new ArrayList<>(constants.subList(2, constants.size())));
			case GROUP_WRAP:
				return prevShift;
			case HIGHEST:
				return prevShift;
			case HW_DEVIATION:
				return prevShift;
			case HW_FORECAST:
				return prevShift;
			case IDENTITY:
				return prevShift;
			case INCLUDE:
				return prevShift;
			case INTEGRAL:
				return prevShift;
			case JOIN:
				return prevShift;
			case LIMIT:
				return prevShift;
			case LOG:
				return prevShift;
			case LOWEST:
				return prevShift;
			case MAX:
				return prevShift;
			case MIN:
				return prevShift;
			case MOVING:
				return prevShift;
			case NORMALIZE:
				return prevShift;
			case PERCENTILE:
				return prevShift;
			case PROPAGATE:
				return prevShift;
			case RANGE:
				return prevShift;
			case SCALE:
				return prevShift;
			case SCALE_ZIP:
				return prevShift;
			case SHIFT:
				shift = (Long t) -> { return prevShift.shift(t) + getOffsetInMilliseconds(constants.get(0)); };
				return shift;
			case SORT:
				return prevShift;
			case SUM:
				return prevShift;
			case SUM_ZIP:
				return prevShift;
			case UNION:
				return prevShift;
			case ZERO_IF_MISSING:
				return prevShift;
			default:
				throw new UnsupportedOperationException("Transform " + scopeName + " is not yet supported by pager!");
		}
	}
	
	private static long getOffsetInMilliseconds(String offset) {
        MetricReader.TimeUnit timeunit = null;
        Long backwards = 1L;

        try {
            if (offset.startsWith("-")) {
                backwards = -1L;
                offset = offset.substring(1);
            }
            if (offset.startsWith("+")) {
                offset = offset.substring(1);
            }
            timeunit = MetricReader.TimeUnit.fromString(offset.substring(offset.length() - 1));

            long timeDigits = Long.parseLong(offset.substring(0, offset.length() - 1));

            return backwards * timeDigits * timeunit.getValue();
        } catch (Exception t) {
            throw new IllegalArgumentException("Fail to parse offset!");
        }
    }
}
