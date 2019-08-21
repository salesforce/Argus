package com.salesforce.dva.argus.service.mail;

import com.salesforce.dva.argus.service.MailService;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;
import java.util.Set;

public class EmailContext {

    private Set<String> recipients;
    private String subject;
    private String emailBody;
    private String contentType;
    private MailService.Priority emailPriority;
    private Pair<String, byte[]> imageDetails;

    public Set<String> getRecipients() {
        return recipients;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmailBody() {
        return emailBody;
    }

    public String getContentType() {
        return contentType;
    }

    public MailService.Priority getEmailPriority() {
        return emailPriority;
    }

    public Optional<Pair<String, byte[]>> getImageDetails() {
        return Optional.ofNullable(imageDetails);
    }

    private EmailContext(Builder emailContextBuilder) {
        this.recipients = emailContextBuilder.recipients;
        this.subject = emailContextBuilder.subject;
        this.emailBody = emailContextBuilder.emailBody;
        this.contentType = emailContextBuilder.contentType;
        this.emailPriority = emailContextBuilder.emailPriority;
        this.imageDetails = emailContextBuilder.imageDetails;
    }

    public static class Builder {

        private Set<String> recipients;
        private String subject;
        private String emailBody;
        private String contentType;
        private MailService.Priority emailPriority;
        private Pair<String, byte[]> imageDetails;

        public Builder withRecipients(Set<String> recipients) {
            this.recipients = recipients;
            return this;
        }

        public Builder withSubject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder withEmailBody(String emailBody) {
            this.emailBody = emailBody;
            return this;
        }

        public Builder withContentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder withEmailPriority(MailService.Priority emailPriority) {
            this.emailPriority = emailPriority;
            return this;
        }

        public Builder withImageDetails(Pair<String,byte[]> imageDetails) {
            this.imageDetails = imageDetails;
            return this;
        }

        public EmailContext build() {
            return new EmailContext(this);
        }

    }
}
