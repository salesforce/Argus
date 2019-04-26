package com.salesforce.dva.argus.util;

import org.apache.commons.beanutils.BeanUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommonUtils {
    /**
     * Copies properties.
     *
     * @param   dest    The object to which the properties will be copied.
     * @param   source  The object whose properties are copied
     *
     * @throws  Exception  Throws exception if beanutils encounter a problem.
     */
    public static void copyProperties(Object dest, Object source) throws Exception {
        try {
            BeanUtils.copyProperties(dest, source);
        } catch (Exception e) {
            throw new Exception(e.getCause().getMessage());
        }
    }

    /**
     * Returns whether two lists are equivalent
     */
    public static boolean listsAreEquivelent(List<? extends Object> list1, List<? extends Object> list2) {
        if (list1 == null) {
            if (list2 == null) {
                return true;
            } else {
                return false;
            }
        }

        if (list2 == null) {
            return false;
        }
        if (list1.size() != list2.size()) return false;

        Map<Object, Integer> tempMap = new HashMap<>();
        for (Object object : list1) {
            Integer currentCount = tempMap.get(object.hashCode());
            if (currentCount == null) {
                tempMap.put(object.hashCode(), 1);
            } else {
                tempMap.put(object.hashCode(), currentCount + 1);
            }
        }
        for (Object object : list2) {
            Integer currentCount = tempMap.get(object.hashCode());
            if (currentCount == null) {
                return false;
            } else {
                tempMap.put(object.hashCode(), currentCount - 1);
            }
        }
        for (Integer count : tempMap.values()) {
            if (count != 0) {
                return false;
            }
        }
        return true;
    }
}
