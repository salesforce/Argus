package com.salesforce.dva.argus.service.alert.testing;

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Metric;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AlertTestResults
{
    private String testUuid;
    private Alert alert;
    private String serializedAlert;
    private String expression;
    private String cronEntry;
    private Long evaluationTime;
    private List<Metric> metrics;
    private Map<BigInteger, Map<Metric,Long>> triggerFirings;
    private Set<BigInteger> evaluatedTriggers;
    private Set<BigInteger> nonEvaluatedTriggers;
    private List<String> messages;
    private boolean bIsSkipped;
    private boolean bIsFailed;
    private boolean bIsNoData;
    private boolean bIsValid;
    private Map<String,String> tags;
    private long latency;
    private long evaluteOnlyLatency;


    public AlertTestResults() {}
    public AlertTestResults(String testUuid) { this.testUuid = testUuid; }

    public void setTestUuid(String testUuid) { this.testUuid = testUuid; }
    public String getTestUuid() { return this.testUuid; }

    public void setAlert(Alert a) { this.alert = a; }
    public Alert getAlert() { return this.alert; }

    public void setSerializedAlert(String serializedAlert)  { this.serializedAlert = serializedAlert; }// NOTE - redundant
    public String getSerializedAlert() { return this.serializedAlert; }

    public void setExpression(String expression) { this.expression = expression; } // NOTE - redundant
    public String getExpression() { return this.expression; }

    public void setCronEntry(String cronEntry) { this.cronEntry = cronEntry; }    // NOTE - redundant
    public String getCronEntry() { return this.cronEntry; }

    public void setEvaluationTime(Long evaluationTime) { this.evaluationTime = evaluationTime; }
    public Long getEvaluationTime() { return this.evaluationTime; }

    public void setMetrics(List<Metric> metrics) { this.metrics = metrics; }
    public List<Metric> getMetrics() { return this.metrics; }

    // NOTE - we are assuming that triggers are uniquely named in the serialized alerts.
    // TODO - better result format: e.g. triggerName -> (map: metric -> firingTime)
    // todo - better yet  firingTime(s)  & name -> (map: metric -> firingTime(s))
    // map: triggerId -> (map: metric -> firingTime)
    // TODO - replace with trigger names
    public void setTriggerFirings(Map<BigInteger, Map<Metric, Long>> triggerFirings) { this.triggerFirings = triggerFirings; }
    public Map<BigInteger, Map<Metric, Long>> getTriggerFirings() { return this.triggerFirings; }

    public void setEvaluatedTriggers(Set<BigInteger> triggerIds) { this.evaluatedTriggers = triggerIds; }
    public Set<BigInteger> getEvaluatedTriggers() { return this.evaluatedTriggers; }

    public void setNonEvaluatedTriggers(Set<BigInteger> triggerIds) { this.nonEvaluatedTriggers = triggerIds; }
    public Set<BigInteger> getNonEvaluatedTriggers() { return this.nonEvaluatedTriggers; }

    // TODO - add support for recording notifications fired

    public void setMessages(List<String> messages) { this.messages = messages; }
    public List<String> getMessages() { return this.messages; }

    public void setIsSkipped(boolean skipped) { this.bIsSkipped = skipped; }
    public boolean getIsSkipped() { return this.bIsSkipped; }

    public void setIsFailed(boolean failed) { this.bIsFailed = failed; }
    public boolean getIsFailed() { return this.bIsFailed; }

    public void setIsNoData(boolean noData) { this.bIsNoData = noData; }
    public boolean getIsNoData() { return this.bIsNoData; }

    public void setIsValid(boolean valid) { this.bIsValid = valid; }
    public boolean getIsValid() { return this.bIsValid; }

    public void setTags( Map<String,String> tags) { this.tags = tags; }
    public Map<String,String> getTags() { return this.tags; }

    public void setLatency(long latency) { this.latency = latency; }
    public long getLatency() { return this.latency; }

    public void setEvaluateOnlyLatency(long latency) { this.evaluteOnlyLatency = latency; }
    public long getEvaluteOnlyLatency() { return this.evaluteOnlyLatency; }

    // TODO - void addMessage(String msg);
    // TODO - void setTriggerFirings(firings,notEvaluated);
    // TODO - void setTestRequest(Alert a, Long evalTime);
    // TODO - void setSummary(boolean failed, boolean skipped, boolean noData, Map<String,String> tags, long latency, long evaluateOnlyLatency, List<String> messages);
}





