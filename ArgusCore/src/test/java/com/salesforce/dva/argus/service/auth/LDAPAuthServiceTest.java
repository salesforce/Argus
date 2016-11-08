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
     
package com.salesforce.dva.argus.service.auth;

import org.junit.Test;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.*;

public class LDAPAuthServiceTest {

    @Test
    public void testLDAPValidUsername() {
        String[] usernames = {
                "f", "fubar", "fubar@myCompany", "fu_bar+100", "fu_bar+100-1", "fu_bar+100-1@myCompany", "fu_bar+100-1@myCompany.com",
                "fubar@myCompany.com"
            };
        Set<String> failures = new TreeSet<>();

        for (String username : usernames) {
            if (!LDAPAuthService._isUsernameValid(username)) {
                failures.add(username);
            }
        }
        assertTrue(failures.toString(), failures.isEmpty());
    }

    @Test
    public void testLDAPInvalidUsername() {
        String[] usernames = {
                "*", "fu*", "fu*r", "*@myCompany", "fu*@myCompany", "fu*r@myCompany", "*@myCompany.com", "fu*@myCompany.com", "fu*r@myCompany.com",
                "*@.", "*@.myCompany", "fu*@.myCompany", "fu*r@.myCompany", "*@.myCompany.com", "fu*@.myCompany.com", "fu*r@.myCompany.com",
                "fubar.@myCompany.com", "*@.myCompany.", "fu*@.myCompany.", "fu*r@.myCompany.", "*@.myCompany.com.", "fu*@.myCompany.com.",
                "fu*r@.myCompany.com.", "fubar.@myCompany.com.",
            };
        Set<String> failures = new TreeSet<>();

        for (String username : usernames) {
            if (LDAPAuthService._isUsernameValid(username)) {
                failures.add(username);
            }
        }
        assertTrue(failures.toString(), failures.isEmpty());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
