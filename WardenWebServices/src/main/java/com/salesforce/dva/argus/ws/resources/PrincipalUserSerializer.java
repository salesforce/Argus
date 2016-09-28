/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
	 
package com.salesforce.dva.argus.ws.resources;

import com.salesforce.dva.argus.entity.PrincipalUser;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import java.io.IOException;

/**
 * Custom serializer for user principals that cuts the cycle for created by and last modified fields.
 *
 * @author  Bhinav Sura (bsura@salesforce.com)
 */
public class PrincipalUserSerializer extends JsonSerializer<PrincipalUser> {

    //~ Methods **************************************************************************************************************************************

    @Override
    public void serialize(PrincipalUser value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
        jgen.writeStartObject();
        jgen.writeStringField("id", value.getId().toString());
        jgen.writeStringField("createdBy", value.getCreatedBy() == null ? null : value.getCreatedBy().getUserName());
        jgen.writeNumberField("createdDate", value.getCreatedDate() == null ? null : value.getCreatedDate().getTime());
        jgen.writeStringField("modifiedBy", value.getModifiedBy() == null ? null : value.getModifiedBy().getUserName());
        jgen.writeNumberField("modifiedDate", value.getModifiedDate() == null ? null : value.getModifiedDate().getTime());
        jgen.writeStringField("userName", value.getUserName());
        jgen.writeStringField("email", value.getEmail());
        jgen.writeBooleanField("privileged", value.isPrivileged());
        jgen.writeEndObject();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
