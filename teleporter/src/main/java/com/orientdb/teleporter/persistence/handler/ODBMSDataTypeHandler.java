/*
 * Copyright 2015 OrientDB LTD (info--at--orientdb.com)
 * All Rights Reserved. Commercial License.
 * 
 * NOTICE:  All information contained herein is, and remains the property of
 * OrientDB LTD and its suppliers, if any.  The intellectual and
 * technical concepts contained herein are proprietary to
 * OrientDB LTD and its suppliers and may be covered by United
 * Kingdom and Foreign Patents, patents in process, and are protected by trade
 * secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from OrientDB LTD.
 * 
 * For more information: http://www.orientdb.com
 */

package com.orientdb.teleporter.persistence.handler;

import java.util.HashMap;
import java.util.Map;

import com.orientdb.teleporter.context.OTeleporterContext;
import com.orientdb.teleporter.model.dbschema.OEntity;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;

/**
 * Generic Handler that executes generic type conversions to the OrientDB types.
 * 
 * @author Gabriele Ponzi
 * @email  <gabriele.ponzi--at--gmail.com>
 *
 */

public class ODBMSDataTypeHandler implements ODriverDataTypeHandler {

  protected Map<String,OType> dbmsType2OrientType;
  public boolean jsonImplemented;
  public boolean geospatialImplemented;


  public ODBMSDataTypeHandler() {
    this.dbmsType2OrientType = this.fillTypesMap();
    this.jsonImplemented = false;
    this.geospatialImplemented = false;
  }

  /**  
   * The method returns the Orient Type starting from the string name type of the original DBMS.
   * If the starting type is not mapped, OType.STRING is returned.
   */
  public OType resolveType(String type, OTeleporterContext context) {

    // Defined Types
    if(this.dbmsType2OrientType.keySet().contains(type))
      return this.dbmsType2OrientType.get(type);

    // Undefined Types
    else {
      context.getStatistics().warningMessages.add("The original type '" + type + "' is not convertible into any OrientDB type thus, in order to prevent data loss, it will be converted to the OrientDB Type String.");
      return OType.STRING;
    }
  }


  private Map<String, OType> fillTypesMap() {

    Map<String, OType> dbmsType2OrientType = new HashMap<String, OType>();


    /*
     * Character Types
     */
    dbmsType2OrientType.put("text", OType.STRING);
    dbmsType2OrientType.put("character", OType.STRING);
    dbmsType2OrientType.put("character varying", OType.STRING);
    dbmsType2OrientType.put("char", OType.STRING);
    dbmsType2OrientType.put("varchar", OType.STRING);
    dbmsType2OrientType.put("varchar2", OType.STRING);


    /*
     * Numeric Types
     */
    dbmsType2OrientType.put("int2", OType.SHORT); 
    dbmsType2OrientType.put("int", OType.INTEGER); 
    dbmsType2OrientType.put("integer", OType.INTEGER); 
    dbmsType2OrientType.put("int4", OType.INTEGER);
    dbmsType2OrientType.put("int8", OType.LONG);
    dbmsType2OrientType.put("real", OType.LONG); 
    dbmsType2OrientType.put("float", OType.LONG); 
    dbmsType2OrientType.put("float4", OType.LONG);
    dbmsType2OrientType.put("float8", OType.DOUBLE);
    dbmsType2OrientType.put("double", OType.DOUBLE);
    dbmsType2OrientType.put("double precision", OType.DOUBLE);
    dbmsType2OrientType.put("numeric", OType.DECIMAL);
    dbmsType2OrientType.put("decimal", OType.DECIMAL);


    /*
     * Date/Time Types
     */    
    dbmsType2OrientType.put("date", OType.DATE);
    dbmsType2OrientType.put("datetime", OType.DATETIME);
    dbmsType2OrientType.put("timestamp", OType.DATETIME);
    dbmsType2OrientType.put("timestamp with time zone", OType.DATETIME);
    dbmsType2OrientType.put("timestamp with local time zone", OType.DATETIME);


    /*
     * Boolean Type
     */
    dbmsType2OrientType.put("boolean", OType.BOOLEAN);
    dbmsType2OrientType.put("bool", OType.BOOLEAN);


    /*
     * Binary Data Types
     */
    dbmsType2OrientType.put("blob", OType.BINARY);


    /*
     * User Defined Types  (Object data types and object views)
     */
    //    TODO! in EMBEDDED


    return dbmsType2OrientType;
  }


  public ODocument convertJSONToDocument(String currentProperty, byte[] currentBinaryValue) {
    return null;
  }

  /**
   * @param currentOriginalType
   * @return
   */
  public boolean isGeospatial(String currentOriginalType) {
    // TODO Auto-generated method stub
    return false;
  }

  /**
   * @param entity
   * @param context
   * @return
   */
  public String buildGeospatialQuery(OEntity entity, OTeleporterContext context) {
    // TODO Auto-generated method stub
    return null;
  }

}
