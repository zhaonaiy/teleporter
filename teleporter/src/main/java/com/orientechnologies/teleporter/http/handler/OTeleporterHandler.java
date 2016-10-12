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

package com.orientechnologies.teleporter.http.handler;

import com.orientechnologies.teleporter.context.OOutputStreamManager;
import com.orientechnologies.teleporter.context.OTeleporterContext;
import com.orientechnologies.teleporter.util.ODriverConfigurator;
import com.orientechnologies.orient.core.record.impl.ODocument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by Enrico Risa on 27/11/15.
 */
public class OTeleporterHandler {
  private ExecutorService pool       = Executors.newFixedThreadPool(1);

  OTeleporterJob          currentJob = null;

  /**
   * Execute import with jsonConfiguration;
   *
   * @param args
   */
  public ODocument execute(ODocument args) {

    OTeleporterJob job = new OTeleporterJob(args, new OTeleporterListener() {
      @Override
      public void onEnd(OTeleporterJob oTeleporterJob) {
        currentJob = null;
      }
    });

    job.validate();

    currentJob = job;
    Future<ODocument> future = pool.submit(job);
    ODocument executionResult = null;

    try {
      //print the return value of Future, notice the output delay in console
      // because Future.get() waits for task to get completed
      executionResult = future.get();
    } catch (Exception e) {
      e.printStackTrace();
    }

    return executionResult;
  }

  /**
   * Check If the connection with given parameters is alive
   *
   * @param args
   * @throws Exception
   */
  public void checkConnection(ODocument args) throws Exception {

    ODriverConfigurator configurator = new ODriverConfigurator();

    final String driver = args.field("driver");
    final String jurl = args.field("jurl");
    final String username = args.field("username");
    final String password = args.field("password");
    OTeleporterContext oTeleporterContext = new OTeleporterContext();
    oTeleporterContext.setOutputManager(new OOutputStreamManager(2));
    configurator.checkConnection(driver, jurl, username, password, oTeleporterContext);
  }

  /**
   * Status of the Running Jobs
   *
   * @return ODocument
   */
  public ODocument status() {

    ODocument status = new ODocument();

    Collection<ODocument> jobs = new ArrayList<ODocument>();
    if (currentJob != null) {
      jobs.add(currentJob.status());
    }
    status.field("jobs", jobs);
    return status;
  }
}