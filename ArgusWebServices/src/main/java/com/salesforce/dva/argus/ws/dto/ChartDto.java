package com.salesforce.dva.argus.ws.dto;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.salesforce.dva.argus.entity.Chart;
import com.salesforce.dva.argus.entity.Chart.ChartQuery;
import com.salesforce.dva.argus.entity.Chart.ChartQueryType;
import com.salesforce.dva.argus.entity.Chart.ChartType;
import com.salesforce.dva.argus.entity.PrincipalUser;

@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class ChartDto extends EntityDTO {
	
	@JsonIgnore
	private String _href;
	private String title;
	private String description;
	private ChartType type;
	private List<ChartQuery> queries;
    private Map<String, String> preferences;
	private BigInteger entityId;
	private String ownerName;
	private PrincipalUser owner;
	
	
	public static ChartDto transformToDto(Chart chart) {
		if (chart == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        ChartDto result = createDtoObject(ChartDto.class, chart);

        result.setOwnerName(chart.getOwner().getUserName());
        if(chart.getEntity() != null) {
        	result.setEntityId(chart.getEntity().getId());
        }
        
        return result;
	}
	
	public static List<ChartDto> transformToDto(List<Chart> charts) {
		if (charts == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        List<ChartDto> result = new ArrayList<>(charts.size());
        for (Chart chart : charts) {
            result.add(transformToDto(chart));
        }
        
        return result;
	}
	
	@JsonProperty("_href")
	public String getHref() {
		return _href;
	}
	
	@JsonIgnore
	public void setHref(String href) {
		this._href = href;
	}
	
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ChartType getType() {
		return type;
	}

	public void setType(ChartType type) {
		this.type = type;
	}

	public List<ChartQuery> getQueries() {
		return queries;
	}

	public void setQueries(List<ChartQuery> queries) {
		this.queries = queries;
	}

	public BigInteger getEntityId() {
		return entityId;
	}

	public void setEntityId(BigInteger entityId) {
		this.entityId = entityId;
	}

	public String getOwnerName() {
		return ownerName;
	}

	public void setOwnerName(String ownerName) {
		this.ownerName = ownerName;
	}

	public void setOwner(PrincipalUser owner) {
		this.owner = owner;
	}

    public Map<String, String> getPreferences() {
        return preferences;
    }

    public void setPreferences(Map<String, String> preferences) {
        this.preferences = preferences;
    }

	@Override
	public Object createExample() {
		ChartDto result = new ChartDto();

		result.setId(BigInteger.ONE);
        result.setCreatedById(BigInteger.ONE);
        result.setCreatedDate(new Date());
        result.setModifiedById(BigInteger.ONE);
        result.setModifiedDate(new Date());
        
        result.setTitle("This is an example chart title.");
        result.setType(ChartType.LINE);
        
        ChartQuery query1 = new ChartQuery(ChartQueryType.METRIC, "-1h:argus.jvm:mem.heap.used:avg");
        ChartQuery query2 = new ChartQuery(ChartQueryType.METRIC, "-1h:argus.jvm:mem.nonheap.used:avg");
        
        result.setQueries(Arrays.asList(query1, query2));
        result.setOwnerName("admin");
        result.setEntityId(BigInteger.TEN);
        return result;
	}

}
