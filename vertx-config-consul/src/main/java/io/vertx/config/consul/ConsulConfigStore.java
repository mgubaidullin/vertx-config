/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */
package io.vertx.config.consul;

import io.vertx.config.spi.ConfigStore;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.ext.consul.KeyValue;
import io.vertx.ext.consul.KeyValueList;

/**
 * @author <a href="mailto:ruslan.sennov@gmail.com">Ruslan Sennov</a>
 */
public class ConsulConfigStore implements ConfigStore {

  private final ConsulClient client;
  private final String delimiter;
  private final String prefix;

  ConsulConfigStore(Vertx vertx, JsonObject configuration) {
    client = ConsulClient.create(vertx, new ConsulClientOptions(configuration));
    delimiter = configuration.getString("delimiter", "/");
    prefix = prefix(configuration.getString("prefix"), delimiter);
  }

  @Override
  public void get(Handler<AsyncResult<Buffer>> completionHandler) {
    client.getValues(prefix, kv -> {
      if (kv.succeeded()) {
        KeyValueList list = kv.result();
        if (list.isPresent()) {
          JsonObject tree = getTree(list, prefix.length(), delimiter);
          completionHandler.handle(Future.succeededFuture(Buffer.buffer(tree.toString())));
        } else {
          completionHandler.handle(Future.succeededFuture(Buffer.buffer("{}")));
        }
      } else {
        completionHandler.handle(Future.failedFuture(kv.cause()));
      }
    });
  }

  @Override
  public void close(Handler<Void> completionHandler) {
    client.close();
    completionHandler.handle(null);
  }

  private static JsonObject getTree(KeyValueList list, int prefix, String delimiter) {
    JsonObject tree = new JsonObject();
    for (KeyValue keyValue : list.getList()) {
      if (keyValue.getKey().endsWith(delimiter)) {
        continue;
      }
      JsonObject json = tree;
      String[] arr = keyValue.getKey().substring(prefix).split(delimiter);
      for (int i = 0; i < arr.length; i++) {
        String key = arr[i];
        if (i == arr.length - 1) {
          json.put(key, keyValue.getValue());
        } else {
          JsonObject next = json.getJsonObject(key);
          if (next == null) {
            next = new JsonObject();
            json.put(key, next);
          }
          json = next;
        }
      }
    }
    return tree;
  }

  private static String prefix(String prefix, String delimiter) {
    if (prefix == null || prefix.isEmpty()) {
      return "";
    } else {
      return prefix.endsWith(delimiter) ? prefix : prefix + delimiter;
    }
  }
}
