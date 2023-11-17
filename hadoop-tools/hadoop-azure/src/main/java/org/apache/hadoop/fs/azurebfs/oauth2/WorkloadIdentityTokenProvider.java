/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.fs.azurebfs.oauth2;

import java.io.File;
import java.io.IOException;

import org.apache.hadoop.thirdparty.com.google.common.base.Strings;
import org.apache.hadoop.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;


/**
 * Provides tokens based on Azure AD Workload Identity.
 */
public class WorkloadIdentityTokenProvider extends AccessTokenProvider {

  private static final String OAUTH2_TOKEN_PATH = "/oauth2/v2.0/token";
  private final String authEndpoint;

  private final String clientId;

  private final String tokenFile;

  private long tokenFetchTime = -1;

  private static final long ONE_HOUR = 3600 * 1000;

  private static final Logger LOG = LoggerFactory.getLogger(AccessTokenProvider.class);

  public WorkloadIdentityTokenProvider(final String authority, final String tenantId,
      final String clientId, final String tokenFile) {
    Preconditions.checkNotNull(authority, "authority");
    Preconditions.checkNotNull(tenantId, "tenantId");
    Preconditions.checkNotNull(clientId, "clientId");
    Preconditions.checkNotNull(tokenFile, "tokenFile");

    this.authEndpoint = authority + tenantId + OAUTH2_TOKEN_PATH;
    this.clientId = clientId;
    this.tokenFile = tokenFile;
  }

  @Override
  protected AzureADToken refreshToken() throws IOException {
    LOG.debug("AADToken: refreshing token from JWT Assertion");
    String clientAssertion = getClientAssertion(tokenFile);
    AzureADToken token = AzureADAuthenticator
        .getTokenUsingJWTAssertion(authEndpoint, clientId, clientAssertion);
    tokenFetchTime = System.currentTimeMillis();
    return token;
  }

  /**
   * Checks if the token is about to expire as per base expiry logic.
   * Otherwise try to expire every 1 hour.
   *
   * @return true if the token is expiring in next 1 hour or if a token has
   * never been fetched
   */
  @Override
  protected boolean isTokenAboutToExpire() {
    if (tokenFetchTime == -1 || super.isTokenAboutToExpire()) {
      return true;
    }

    boolean expiring = false;
    long elapsedTimeSinceLastTokenRefreshInMillis =
        System.currentTimeMillis() - tokenFetchTime;
    // In case token is not refreshed for 1 hr or any clock skew issues,
    // refresh token.
    expiring = elapsedTimeSinceLastTokenRefreshInMillis >= ONE_HOUR
        || elapsedTimeSinceLastTokenRefreshInMillis < 0;
    if (expiring) {
      LOG.debug("JWTToken: token renewing. Time elapsed since last token fetch:"
          + " {} milliseconds", elapsedTimeSinceLastTokenRefreshInMillis);
    }

    return expiring;
  }

  private static String getClientAssertion(String tokenFile)
      throws IOException {
    File file = new File(tokenFile);
    String clientAssertion = FileUtils.readFileToString(file, "UTF-8");
    if (Strings.isNullOrEmpty(clientAssertion))
        throw new IOException("Empty token file.");
    return clientAssertion;
  }
}
