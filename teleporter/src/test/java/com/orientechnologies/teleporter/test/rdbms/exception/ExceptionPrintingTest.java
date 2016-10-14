/*
 * Copyright 2016 OrientDB LTD (info--at--orientdb.com)
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

package com.orientechnologies.teleporter.test.rdbms.exception;

import com.orientechnologies.teleporter.context.OOutputStreamManager;
import com.orientechnologies.teleporter.context.OTeleporterContext;
import com.orientechnologies.teleporter.exception.OTeleporterIOException;
import com.orientechnologies.teleporter.exception.OTeleporterRuntimeException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Gabriele Ponzi
 * @email  <gabriele.ponzi--at--gmail.com>
 *
 */

public class ExceptionPrintingTest {

  private OTeleporterContext context;

  @Before
  public void init() {
    this.context = OTeleporterContext.newInstance();
    this.context.setOutputManager(new OOutputStreamManager(0));
    context.setOutputManager(new OOutputStreamManager(0));
  }

  /**
   * Tests behaviour of method printExceptionMessage(..) with the exception OTeleporterIOException
   */
  @Test
  public void test1() {

    /*
     * Printing with INFO level
     */

    Exception e1 = new OTeleporterIOException("Teleporter IO Exception raised");
    Exception e2 = new OTeleporterIOException(new IOException("(cause-exception message) IO Exception raised"));
    Exception e3 = new OTeleporterIOException("Teleporter IO Exception raised", new IOException("IO Exception raised"));
    Exception e4 = new OTeleporterIOException();

    String message1 = this.context.printExceptionMessage(e1, "Error during execution:", "info");
    String message2 = this.context.printExceptionMessage(e2, "Error during execution:", "info");
    String message3 = this.context.printExceptionMessage(e3, "Error during execution:", "info");
    String message4 = this.context.printExceptionMessage(e4, "Error during execution:", "info");

    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterIOException - Teleporter IO Exception raised", message1);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterIOException - java.io.IOException: (cause-exception message) IO Exception raised", message2);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterIOException - Teleporter IO Exception raised", message3);
    assertEquals("Error during execution:\n" + "com.orientechnologies.teleporter.exception.OTeleporterIOException", message4);


    /*
     * Printing with DEBUG level
     */

    message1 = this.context.printExceptionMessage(e1, "Error during execution:", "debug");
    message2 = this.context.printExceptionMessage(e2, "Error during execution:", "debug");
    message3 = this.context.printExceptionMessage(e3, "Error during execution:", "debug");
    message4 = this.context.printExceptionMessage(e4, "Error during execution:", "debug");

    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterIOException - Teleporter IO Exception raised",message1);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterIOException - java.io.IOException: (cause-exception message) IO Exception raised",message2);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterIOException - Teleporter IO Exception raised",message3);
    assertEquals("Error during execution:\n" + "com.orientechnologies.teleporter.exception.OTeleporterIOException", message4);


    /*
     * Printing with WARNING level
     */

    message1 = this.context.printExceptionMessage(e1, "Error during execution:", "warn");
    message2 = this.context.printExceptionMessage(e2, "Error during execution:", "warn");
    message3 = this.context.printExceptionMessage(e3, "Error during execution:", "warn");
    message4 = this.context.printExceptionMessage(e4, "Error during execution:", "warn");

    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterIOException - Teleporter IO Exception raised",message1);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterIOException - java.io.IOException: (cause-exception message) IO Exception raised",message2);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterIOException - Teleporter IO Exception raised",message3);
    assertEquals("Error during execution:\n" + "com.orientechnologies.teleporter.exception.OTeleporterIOException", message4);


     /*
     * Printing with ERROR level
     */

    message1 = this.context.printExceptionMessage(e1, "Error during execution:", "error");
    message2 = this.context.printExceptionMessage(e2, "Error during execution:", "error");
    message3 = this.context.printExceptionMessage(e3, "Error during execution:", "error");
    message4 = this.context.printExceptionMessage(e4, "Error during execution:", "error");

    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterIOException - Teleporter IO Exception raised",message1);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterIOException - java.io.IOException: (cause-exception message) IO Exception raised",message2);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterIOException - Teleporter IO Exception raised",message3);
    assertEquals("Error during execution:\n" + "com.orientechnologies.teleporter.exception.OTeleporterIOException", message4);

  }


  /**
   * Tests behaviour of method printExceptionMessage(..) with the exception OTeleporterRuntimeException
   */
  @Test
  public void test2() {

    /*
     * Printing with INFO level
     */

    Exception e1 = new OTeleporterRuntimeException("Teleporter Runtime Exception raised");
    Exception e2 = new OTeleporterRuntimeException(new RuntimeException("(cause-exception message) Runtime Exception raised"));
    Exception e3 = new OTeleporterRuntimeException("Teleporter Runtime Exception raised", new IOException("Runtime Exception raised"));
    Exception e4 = new OTeleporterRuntimeException();

    String message1 = this.context.printExceptionMessage(e1, "Error during execution:", "info");
    String message2 = this.context.printExceptionMessage(e2, "Error during execution:", "info");
    String message3 = this.context.printExceptionMessage(e3, "Error during execution:", "info");
    String message4 = this.context.printExceptionMessage(e4, "Error during execution:", "info");

    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException - Teleporter Runtime Exception raised",message1);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException - java.lang.RuntimeException: (cause-exception message) Runtime Exception raised",message2);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException - Teleporter Runtime Exception raised",message3);
    assertEquals("Error during execution:\n" + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException", message4);


    /*
     * Printing with DEBUG level
     */

    message1 = this.context.printExceptionMessage(e1, "Error during execution:", "debug");
    message2 = this.context.printExceptionMessage(e2, "Error during execution:", "debug");
    message3 = this.context.printExceptionMessage(e3, "Error during execution:", "debug");
    message4 = this.context.printExceptionMessage(e4, "Error during execution:", "debug");

    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException - Teleporter Runtime Exception raised",message1);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException - java.lang.RuntimeException: (cause-exception message) Runtime Exception raised",message2);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException - Teleporter Runtime Exception raised",message3);
    assertEquals("Error during execution:\n" + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException", message4);


    /*
     * Printing with WARNING level
     */

    message1 = this.context.printExceptionMessage(e1, "Error during execution:", "warn");
    message2 = this.context.printExceptionMessage(e2, "Error during execution:", "warn");
    message3 = this.context.printExceptionMessage(e3, "Error during execution:", "warn");
    message4 = this.context.printExceptionMessage(e4, "Error during execution:", "warn");

    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException - Teleporter Runtime Exception raised",message1);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException - java.lang.RuntimeException: (cause-exception message) Runtime Exception raised",message2);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException - Teleporter Runtime Exception raised",message3);
    assertEquals("Error during execution:\n" + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException", message4);


    /*
     * Printing with ERROR level
     */

    message1 = this.context.printExceptionMessage(e1, "Error during execution:", "error");
    message2 = this.context.printExceptionMessage(e2, "Error during execution:", "error");
    message3 = this.context.printExceptionMessage(e3, "Error during execution:", "error");
    message4 = this.context.printExceptionMessage(e4, "Error during execution:", "error");

    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException - Teleporter Runtime Exception raised",message1);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException - java.lang.RuntimeException: (cause-exception message) Runtime Exception raised",message2);
    assertEquals("Error during execution:\n"
        + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException - Teleporter Runtime Exception raised",message3);
    assertEquals("Error during execution:\n" + "com.orientechnologies.teleporter.exception.OTeleporterRuntimeException", message4);

  }

  /**
   * Tests behaviour of method printExceptionStackTrace(..) with the exception OTeleporterIOException
   */
  @Test
  public void test3() {

    /*
     * Printing with INFO level
     */

    Exception e1 = new OTeleporterIOException("Teleporter IO Exception raised");
    Exception e2 = new OTeleporterIOException(new IOException("(cause-exception message) IO Exception raised"));
    Exception e3 = new OTeleporterIOException("Teleporter IO Exception raised", new IOException("IO Exception raised"));
    Exception e4 = new OTeleporterIOException();

    String message1 = this.context.printExceptionStackTrace(e1, "info");
    String message2 = this.context.printExceptionStackTrace(e2, "info");
    String message3 = this.context.printExceptionStackTrace(e3, "info");
    String message4 = this.context.printExceptionStackTrace(e4, "info");


    assertNotNull(message1);
    assertTrue(message1.length() > 1000);
    assertNotNull(message2);
    assertTrue(message2.length() > 1000);
    assertNotNull(message3);
    assertTrue(message3.length() > 1000);
    assertNotNull(message4);
    assertTrue(message4.length() > 1000);

    /*
     * Printing with DEBUG level
     */

    message1 = this.context.printExceptionStackTrace(e1, "debug");
    message2 = this.context.printExceptionStackTrace(e2, "debug");
    message3 = this.context.printExceptionStackTrace(e3, "debug");
    message4 = this.context.printExceptionStackTrace(e4, "debug");

    assertNotNull(message1);
    assertTrue(message1.length() > 1000);
    assertNotNull(message2);
    assertTrue(message2.length() > 1000);
    assertNotNull(message3);
    assertTrue(message3.length() > 1000);
    assertNotNull(message4);
    assertTrue(message4.length() > 1000);


    /*
     * Printing with WARNING level
     */

    message1 = this.context.printExceptionStackTrace(e1, "warn");
    message2 = this.context.printExceptionStackTrace(e2, "warn");
    message3 = this.context.printExceptionStackTrace(e3, "warn");
    message4 = this.context.printExceptionStackTrace(e4, "warn");

    assertNotNull(message1);
    assertTrue(message1.length() > 1000);
    assertNotNull(message2);
    assertTrue(message2.length() > 1000);
    assertNotNull(message3);
    assertTrue(message3.length() > 1000);
    assertNotNull(message4);
    assertTrue(message4.length() > 1000);


     /*
     * Printing with ERROR level
     */

    message1 = this.context.printExceptionStackTrace(e1, "error");
    message2 = this.context.printExceptionStackTrace(e2, "error");
    message3 = this.context.printExceptionStackTrace(e3, "error");
    message4 = this.context.printExceptionStackTrace(e4, "error");

    assertNotNull(message1);
    assertTrue(message1.length() > 1000);
    assertNotNull(message2);
    assertTrue(message2.length() > 1000);
    assertNotNull(message3);
    assertTrue(message3.length() > 1000);
    assertNotNull(message4);
    assertTrue(message4.length() > 1000);

  }


  /**
   * Tests behaviour of method printExceptionStackTrace(..) with the exception OTeleporterRuntimeException
   */
  @Test
  public void test4() {

    /*
     * Printing with INFO level
     */

    Exception e1 = new OTeleporterRuntimeException("Teleporter Runtime Exception raised");
    Exception e2 = new OTeleporterRuntimeException(new RuntimeException("(cause-exception message) Runtime Exception raised"));
    Exception e3 = new OTeleporterRuntimeException("Teleporter Runtime Exception raised", new IOException("Runtime Exception raised"));
    Exception e4 = new OTeleporterRuntimeException();

    String message1 = this.context.printExceptionStackTrace(e1, "info");
    String message2 = this.context.printExceptionStackTrace(e2, "info");
    String message3 = this.context.printExceptionStackTrace(e3, "info");
    String message4 = this.context.printExceptionStackTrace(e4, "info");

    assertNotNull(message1);
    assertTrue(message1.length() > 1000);
    assertNotNull(message2);
    assertTrue(message2.length() > 1000);
    assertNotNull(message3);
    assertTrue(message3.length() > 1000);
    assertNotNull(message4);
    assertTrue(message4.length() > 1000);


    /*
     * Printing with DEBUG level
     */

    message1 = this.context.printExceptionStackTrace(e1, "debug");
    message2 = this.context.printExceptionStackTrace(e2, "debug");
    message3 = this.context.printExceptionStackTrace(e3, "debug");
    message4 = this.context.printExceptionStackTrace(e4, "debug");

    assertNotNull(message1);
    assertTrue(message1.length() > 1000);
    assertNotNull(message2);
    assertTrue(message2.length() > 1000);
    assertNotNull(message3);
    assertTrue(message3.length() > 1000);
    assertNotNull(message4);
    assertTrue(message4.length() > 1000);


    /*
     * Printing with WARNING level
     */

    message1 = this.context.printExceptionStackTrace(e1, "warn");
    message2 = this.context.printExceptionStackTrace(e2, "warn");
    message3 = this.context.printExceptionStackTrace(e3, "warn");
    message4 = this.context.printExceptionStackTrace(e4, "warn");

    assertNotNull(message1);
    assertTrue(message1.length() > 1000);
    assertNotNull(message2);
    assertTrue(message2.length() > 1000);
    assertNotNull(message3);
    assertTrue(message3.length() > 1000);
    assertNotNull(message4);
    assertTrue(message4.length() > 1000);


    /*
     * Printing with ERROR level
     */

    message1 = this.context.printExceptionStackTrace(e1, "error");
    message2 = this.context.printExceptionStackTrace(e2, "error");
    message3 = this.context.printExceptionStackTrace(e3, "error");
    message4 = this.context.printExceptionStackTrace(e4, "error");

    assertNotNull(message1);
    assertTrue(message1.length() > 1000);
    assertNotNull(message2);
    assertTrue(message2.length() > 1000);
    assertNotNull(message3);
    assertTrue(message3.length() > 1000);
    assertNotNull(message4);
    assertTrue(message4.length() > 1000);

  }
}
