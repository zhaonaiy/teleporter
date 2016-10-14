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

package com.orientechnologies.teleporter.util;

import com.orientechnologies.teleporter.context.OTeleporterContext;
import com.orientechnologies.teleporter.exception.OTeleporterRuntimeException;
import com.orientechnologies.teleporter.model.dbschema.OSourceDatabaseInfo;
import com.orientechnologies.teleporter.persistence.util.ODBSourceConnection;
import com.orientechnologies.orient.core.record.impl.ODocument;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * Executes an automatic migrationConfigDoc of the chosen driver JDBC.
 *
 * @author Gabriele Ponzi
 * @email <gabriele.ponzi--at--gmail.com>
 *
 */

public class ODriverConfigurator {

  public static final String DRIVERS = "http://orientdb.com/jdbc-drivers.json";
  private final String localJsonPath = "../config/jdbc-drivers.json";
  private Map<String, List<String>> driver2filesIdentifier;

  public ODriverConfigurator() {
    this.driver2filesIdentifier = new LinkedHashMap<String, List<String>>();
    this.fillMap();
  }

  /**
   *
   */
  private void fillMap() {

    // Oracle identifiers
    List<String> oracleIdentifiers = new LinkedList<String>();
    oracleIdentifiers.add("ojdbc");
    driver2filesIdentifier.put("oracle", oracleIdentifiers);

    // SQLServer identifiers
    List<String> sqlserverIdentifiers = new LinkedList<String>();
    sqlserverIdentifiers.add("sqljdbc");
    sqlserverIdentifiers.add("jtds");
    driver2filesIdentifier.put("sqlserver", sqlserverIdentifiers);

    // MySQL identifiers
    List<String> mysqlIdentifiers = new LinkedList<String>();
    mysqlIdentifiers.add("mysql");
    driver2filesIdentifier.put("mysql", mysqlIdentifiers);

    // PostgreSQL identifiers
    List<String> postgresqlIdentifiers = new LinkedList<String>();
    postgresqlIdentifiers.add("postgresql");
    driver2filesIdentifier.put("postgresql", postgresqlIdentifiers);

    // HyperSQL identifiers
    List<String> hypersqlIdentifiers = new LinkedList<String>();
    hypersqlIdentifiers.add("hsqldb");
    driver2filesIdentifier.put("hypersql", hypersqlIdentifiers);

  }

  /*
   * It Checks if the requested driver is already present in the classpath, if not present it downloads the last available driver
   * version. Moreover it gets the driver class name corresponding to the chosen DBMS.
   */
  public String checkConfiguration(String driverName) {

    String classPath = "../lib/";
    String driverClassName = null;
    driverName = driverName.toLowerCase();

    try {

      // fetching online JSON
      ODocument json = readJsonFromUrl(DRIVERS);

      ODocument fields = null;

      // recovering driver class name
      if (driverName.equals("oracle")) {
        fields = json.field("Oracle");
      }
      if (driverName.equals("sqlserver")) {
        fields = json.field("SQLServer");
      } else if (driverName.equals("mysql")) {
        fields = json.field("MySQL");
      } else if (driverName.equals("postgresql")) {
        fields = json.field("PostgreSQL");
      } else if (driverName.equals("hypersql")) {
        fields = json.field("HyperSQL");
      }
      driverClassName = (String) fields.field("className");

      // if the driver is not present, it will be downloaded
      String driverPath = isDriverAlreadyPresent(driverName, classPath);

      if (driverPath == null) {

        OTeleporterContext.getInstance().getOutputManager().info("\nDownloading the necessary JDBC driver in ORIENTDB_HOME/lib ...\n");

        // download last available jdbc driver version
        String driverDownldUrl = (String) fields.field("url");
        URL website = new URL(driverDownldUrl);
        String fileName = driverDownldUrl.substring(driverDownldUrl.lastIndexOf('/') + 1, driverDownldUrl.length());
        ReadableByteChannel rbc = Channels.newChannel(website.openStream());
        @SuppressWarnings("resource")
        FileOutputStream fos = new FileOutputStream(classPath + fileName);
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

        driverPath = classPath + fileName;

        if (driverName.equalsIgnoreCase("SQLServer")) {
          OFileManager.extractAll(driverPath, classPath);
          OFileManager.deleteFile(driverPath);
          String[] split = driverPath.split(".jar");
          driverPath = split[0] + ".jar";
        }

        OTeleporterContext.getInstance().getOutputManager().info("Driver JDBC downloaded.\n");
      }

      // saving driver
      OTeleporterContext.getInstance().setDriverDependencyPath(driverPath);

    } catch (Exception e) {
      String mess = "";
      OTeleporterContext.getInstance().printExceptionMessage(e, mess, "error");
      OTeleporterContext.getInstance().printExceptionStackTrace(e, "error");
      throw new OTeleporterRuntimeException(e);
    }

    return driverClassName;
  }

  /**
   * @param driverName
   * @return the path of the driver
   */
  private String isDriverAlreadyPresent(String driverName, String classPath) {

    File dir = new File(classPath);
    File[] files = dir.listFiles();

    for (String identifier : this.driver2filesIdentifier.get(driverName)) {

      for (int i = 0; i < files.length; i++) {
        if (files[i].getName().startsWith(identifier))
          return files[i].getPath();
      }
    }

    // the driver is not present, thus it's returned null as path
    return null;
  }

  public ODocument readJsonFromUrl(String url) {

    InputStream is = null;
    ODocument json = null;

    try {

      URL urlObj = new URL(url);
      URLConnection urlConn = urlObj.openConnection();
      urlConn.setRequestProperty("User-Agent", "Teleporter");

      try {
        is = urlConn.getInputStream();
      } catch (IOException e1) {

        try {
          // read json from the file in the ORIENTDB_HOME/config path
          is = new FileInputStream(new File(this.localJsonPath));
        } catch (IOException e2) {
          String mess = "The jdbc-drivers migrationConfigDoc cannot be found. The connection to orientdb.com did not succeed and the migrationConfigDoc file \"jdbc-drivers.json\" is not present in ORIENTDB_HOME/config.\n";
          OTeleporterContext.getInstance().printExceptionMessage(e2, mess, "error");
          OTeleporterContext.getInstance().printExceptionStackTrace(e2, "error");
          throw new OTeleporterRuntimeException(e2);
        }
      }

      json = new ODocument();

      BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
      String jsonText = OFileManager.readAllTextFile(rd);
      json.fromJSON(jsonText, "noMap");

    } catch (Exception e) {
      String mess = "";
      OTeleporterContext.getInstance().printExceptionMessage(e, mess, "error");
      OTeleporterContext.getInstance().printExceptionStackTrace(e, "error");
      throw new OTeleporterRuntimeException(e);
    } finally {
      try {
        is.close();
      } catch (Exception e) {
        String mess = "";
        OTeleporterContext.getInstance().printExceptionMessage(e, mess, "error");
        OTeleporterContext.getInstance().printExceptionStackTrace(e, "error");
        throw new OTeleporterRuntimeException(e);
      }
    }
    return json;
  }

  public void checkConnection(String driver, String uri, String username, String password)
      throws Exception {

    String driverName = checkConfiguration(driver);
    Connection connection = null;
    try {
      connection = ODBSourceConnection.getConnection(driverName, uri, username, password);
    } finally {
      if (connection != null) {
        connection.close();
      }
    }
  }

}