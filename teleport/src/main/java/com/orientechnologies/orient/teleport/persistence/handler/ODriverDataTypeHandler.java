/*
 *
 *  *  Copyright 2015 Orient Technologies LTD (info(at)orientechnologies.com)
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *  * For more information: http://www.orientechnologies.com
 *
 */

package com.orientechnologies.orient.teleport.persistence.handler;

import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.teleport.context.OTeleportContext;

/**
 * Interface that executes the right type conversions between DBMS and OrientDB types according 
 * to the specific driver of the source DB.
 * 
 * @author Gabriele Ponzi
 * @email  <gabriele.ponzi--at--gmail.com>
 *
 */

public interface ODriverDataTypeHandler {
  
  public OType resolveType(String type, OTeleportContext context);
  
}