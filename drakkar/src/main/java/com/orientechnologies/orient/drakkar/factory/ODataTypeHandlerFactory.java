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

package com.orientechnologies.orient.drakkar.factory;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.drakkar.persistence.handler.ODriverDataTypeHandler;
import com.orientechnologies.orient.drakkar.persistence.handler.OGenericDataTypeHandler;
import com.orientechnologies.orient.drakkar.persistence.handler.OMySQLDataTypeHandler;
import com.orientechnologies.orient.drakkar.persistence.handler.OOracleDataTypeHandler;
import com.orientechnologies.orient.drakkar.persistence.handler.OPostgreSQLDataTypeHandler;

/**
 * @author Gabriele Ponzi
 * @email  gabriele.ponzi-at-gmaildotcom
 *
 */

public class ODataTypeHandlerFactory {

  public ODriverDataTypeHandler buildDataTypeHandler(String driver) {
    ODriverDataTypeHandler handler = null;

    switch(driver) {

    case "org.postgresql.Driver":   handler = new OPostgreSQLDataTypeHandler();
    break;

    case "com.mysql.jdbc.Driver":   handler = new OMySQLDataTypeHandler();
    break;

    case "oracle.jdbc.driver.OracleDriver": handler = new OOracleDataTypeHandler();
    break;

    default :  handler = new OGenericDataTypeHandler();
    OLogManager.instance().warn(this, "Driver '%s' is not supported. Thus problems may occur during type conversion.\n", driver);
    break;
    }

    return handler;
  }

}
