#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

curl -i -s -X POST http://kong-1:8001/services \
  --data name=example_service \
  --data url='https://httpbin.konghq.com'
  
curl -i -X POST http://kong-1:8001/services/example_service/routes \
  --data 'paths[]=/mock' \
  --data name=example_route
  
curl -X GET http://kong-1:8000/mock/anything

curl -i -X POST http://kong-1:8001/plugins \
  --data name=rate-limiting \
  --data config.minute=5 \
  --data config.policy=local
  
for _ in {1..6}; do curl -s -i kong-1:8000/mock/anything; echo; sleep 1; done