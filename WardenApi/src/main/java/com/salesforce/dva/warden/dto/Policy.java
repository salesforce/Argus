package com.salesforce.dva.warden.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Policy Dto.
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com)
 * Failing to add comment.
 */
@SuppressWarnings( "serial" )
@JsonIgnoreProperties( ignoreUnknown = true )
public class Policy
    extends Entity
{
    private String service;
    private String name;
    private List<String> owners = new ArrayList<String>(  );
    private List<String> users = new ArrayList<String>(  );
    private String subSystem;
    private String metricName;
    private TriggerType triggerType;
    private Aggregator aggregator;
    private List<Double> threshold;
    private String timeUnit;
    private Double defaultValue;
    private String cronEntry;
    private List<BigInteger> suspensionLevelIds = new ArrayList<BigInteger>(  );

    public String getService(  )
    {
        return service;
    }

    public void setService( String service )
    {
        this.service = service;
    }

    public String getName(  )
    {
        return name;
    }

    public void setName( String name )
    {
        this.name = name;
    }

    public List<String> getOwners(  )
    {
        return owners;
    }

    public void setOwners( List<String> owner )
    {
        this.owners = owner;
    }

    public List<String> getUsers(  )
    {
        return users;
    }

    public void setUsers( List<String> user )
    {
        this.users = user;
    }

    public String getSubSystem(  )
    {
        return subSystem;
    }

    public void setSubSystem( String subSystem )
    {
        this.subSystem = subSystem;
    }

    public String getMetricName(  )
    {
        return metricName;
    }

    public void setMetricName( String metricName )
    {
        this.metricName = metricName;
    }

    public TriggerType getTriggerType(  )
    {
        return triggerType;
    }

    public void setTriggerType( TriggerType triggerType )
    {
        this.triggerType = triggerType;
    }

    public Aggregator getAggregator(  )
    {
        return aggregator;
    }

    public void setAggregator( Aggregator aggregator )
    {
        this.aggregator = aggregator;
    }

    public List<Double> getThresholds(  )
    {
        return threshold;
    }

    public void setThresholds( List<Double> threshold )
    {
        this.threshold = threshold;
    }

    public String getTimeUnit(  )
    {
        return timeUnit;
    }

    public void setTimeUnit( String timeUnit )
    {
        this.timeUnit = timeUnit;
    }

    public Double getDefaultValue(  )
    {
        return defaultValue;
    }

    public void setDefaultValue( Double defaultValue )
    {
        this.defaultValue = defaultValue;
    }

    public String getCronEntry(  )
    {
        return cronEntry;
    }

    public void setCronEntry( String cronEntry )
    {
        this.cronEntry = cronEntry;
    }

    public List<BigInteger> getSuspensionLevels(  )
    {
        return suspensionLevelIds;
    }

    public void setSuspensionLevels( List<BigInteger> suspensionLevels )
    {
        this.suspensionLevelIds = suspensionLevels;
    }

    /**
     * The type of trigger.
     *
     */
    public enum TriggerType
    {
        /** Greater than. */
        GREATER_THAN,
        /** Greater than or equal to. */
        GREATER_THAN_OR_EQ,
        /** Less than. */
        LESS_THAN,
        /** Less than or equal to. */
        LESS_THAN_OR_EQ,
        /** Equal to. */
        EQUAL,
        /** Not equal to. */
        NOT_EQUAL,
        /** Between. */
        BETWEEN,
        /** Not between. */
        NOT_BETWEEN;
        /**
         * Converts a string to a trigger type.
         *
         * @param   name  The trigger type name.
         *
         * @return  The corresponding trigger type.
         *
         * @throws  IllegalArgumentException  If no corresponding trigger type is found.
         */
        @JsonCreator
        public static TriggerType fromString( String name )
        {
            for ( TriggerType t : TriggerType.values(  ) )
            {
                if ( t.toString(  ).equalsIgnoreCase( name ) )
                {
                    return t;
                }
            }

            throw new IllegalArgumentException( "Trigger Type does not exist." );
        }

        /**
         * Returns the name of the trigger type.
         *
         * @return  The name of the trigger type.
         */
        @JsonValue
        public String value(  )
        {
            return this.toString(  );
        }
    }

    /**
     * The supported methods for aggregation and downsampling.
     *
     */
    public enum Aggregator
    {MIN( "min" ),
        MAX( "max" ),
        SUM( "sum" ),
        AVG( "avg" ),
        DEV( "dev" ),
        ZIMSUM( "zimsum" ),
        MINMIN( "minmin" ),
        MINMAX( "minmax" );

        private final String _description;

        private Aggregator( String description )
        {
            _description = description;
        }

        /**
         * Returns the element corresponding to the given name.
         *
         * @param   name  The aggregator name.
         *
         * @return  The corresponding aggregator element.
         */
        public static Aggregator fromString( String name )
        {
            if ( ( name != null ) && ! name.isEmpty(  ) )
            {
                for ( Aggregator aggregator : Aggregator.values(  ) )
                {
                    if ( name.equalsIgnoreCase( aggregator.name(  ) ) )
                    {
                        return aggregator;
                    }
                }
            }

            return null;
        }

        /**
         * Returns the short hand description of the method.
         *
         * @return  The method description.
         */
        public String getDescription(  )
        {
            return _description;
        }
    }
    @Override
    public Object createExample(  )
    {
        Policy result = new Policy(  );

        result.setId( BigInteger.ONE );
        result.setCreatedById( BigInteger.ONE );
        result.setCreatedDate( new Date(  ) );
        result.setModifiedById( BigInteger.TEN );
        result.setModifiedDate( new Date(  ) );

        result.setService( "example-service" );
        result.setName( "example-name" );
        result.setOwners( Arrays.asList( "example-owners" ) );
        result.setUsers( Arrays.asList( "example-users" ) );
        result.setSubSystem( "example-subSystem" );
        result.setMetricName( "example-metricName" );
        result.setTriggerType( TriggerType.NOT_BETWEEN );
        result.setAggregator( Aggregator.SUM );
        result.setThresholds( Arrays.asList( 0.0 ) );
        result.setTimeUnit( "5min" );
        result.setDefaultValue( 0.0 );
        result.setCronEntry( "0 */4 * * *" );

        return result;
    }
}
