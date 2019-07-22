package com.salesforce.dva.argus.service.metric.metadata;

/**
 * POJO representing an IDB API query for a specific field of a resource, eg. the operationalStatus of a specific host
 */
public class IDBFieldQuery {
    private ResourceType type;
    private String datacenter;
    private String resourceName;
    private String field;

    /**
     * @param type          Whether this query is for a cluster or host
     * @param datacenter    Datacenter that the resource is in
     * @param resourceName  Name of the host or cluster
     * @param field         Field to return, eg. operationalStatus, environment
     */
    public IDBFieldQuery(ResourceType type, String datacenter, String resourceName, String field) {
        this.type = type;
        this.datacenter = datacenter;
        this.resourceName = resourceName;
        this.field = field;
    }

    public ResourceType getType() {
        return type;
    }

    public String getDatacenter() {
        return datacenter;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getField() {
        return field;
    }

    enum ResourceType {
        CLUSTER,
        HOST
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IDBFieldQuery that = (IDBFieldQuery) o;

        if (type != that.type) return false;
        if (datacenter != null ? !datacenter.equals(that.datacenter) : that.datacenter != null) return false;
        if (resourceName != null ? !resourceName.equals(that.resourceName) : that.resourceName != null) return false;
        return field != null ? field.equals(that.field) : that.field == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (datacenter != null ? datacenter.hashCode() : 0);
        result = 31 * result + (resourceName != null ? resourceName.hashCode() : 0);
        result = 31 * result + (field != null ? field.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "IDBFieldQuery{" +
                "type=" + type +
                ", datacenter='" + datacenter + '\'' +
                ", resourceName='" + resourceName + '\'' +
                ", field='" + field + '\'' +
                '}';
    }
}
