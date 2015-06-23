/*
 * Copyright (c) 2015 Spotify AB.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.heroic.httpclient;

import java.util.concurrent.Callable;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import lombok.RequiredArgsConstructor;

import com.spotify.heroic.exceptions.RpcNodeException;

@RequiredArgsConstructor
final class HttpPostRequestResolver<R, T> implements Callable<T> {
    private final R request;
    private final Class<T> bodyType;
    private final WebTarget target;

    @Override
    public T call() throws Exception {
        final Response response;

        try {
            response = target.request().post(Entity.entity(request, MediaType.APPLICATION_JSON));
        } catch (final Exception e) {
            throw new RpcNodeException(target.getUri(), "request failed", e);
        }

        return HttpClientUtils.handleResponse(response, bodyType, target);
    }
}