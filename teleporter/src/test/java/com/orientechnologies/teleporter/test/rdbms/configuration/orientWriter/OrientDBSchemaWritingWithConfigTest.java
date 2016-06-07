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

package com.orientechnologies.teleporter.test.rdbms.configuration.orientWriter;

import com.orientechnologies.teleporter.context.OOutputStreamManager;
import com.orientechnologies.teleporter.context.OTeleporterContext;
import com.orientechnologies.teleporter.mapper.rdbms.OER2GraphMapper;
import com.orientechnologies.teleporter.nameresolver.OJavaConventionNameResolver;
import com.orientechnologies.teleporter.persistence.handler.OHSQLDBDataTypeHandler;
import com.orientechnologies.teleporter.util.OFileManager;
import com.orientechnologies.teleporter.writer.OGraphModelWriter;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.tinkerpop.blueprints.impls.orient.OrientEdgeType;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;
import com.tinkerpop.blueprints.impls.orient.OrientVertexType;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.Assert.*;

/**
 * @author Gabriele Ponzi
 * @email  gabriele.ponzi--at--gmail.com
 *
 */

public class OrientDBSchemaWritingWithConfigTest {

  private OER2GraphMapper    mapper;
  private OTeleporterContext context;
  private OGraphModelWriter  modelWriter;
  private String             outOrientGraphUri;
  private final String configDirectEdgesPath = "src/test/resources/configuration-mapping/relationships-mapping-direct-edges.json";
  private final String configInverseEdgesPath = "src/test/resources/configuration-mapping/relationships-mapping-inverted-edges.json";
  private final String configJoinTableDirectEdgesPath = "src/test/resources/configuration-mapping/joint-table-relationships-mapping-direct-edges.json";
  private final String configJoinTableInverseEdgesPath = "src/test/resources/configuration-mapping/joint-table-relationships-mapping-inverted-edges.json";

  @Before
  public void init() {
    this.context = new OTeleporterContext();
    this.context.setOutputManager(new OOutputStreamManager(0));
    this.context.setNameResolver(new OJavaConventionNameResolver());
    this.context.setQueryQuoteType("\"");
    this.modelWriter = new OGraphModelWriter();
    this.outOrientGraphUri = "memory:testOrientDB";
  }

  @Test

  /*
   *  Two tables: 2 relationships not declared through foreign keys.
   *  EMPLOYEE --[WorksAtProject]--> PROJECT
   *  PROJECT --[HasManager]--> EMPLOYEE
   *
   *  Properties manually configured on edges:
   *
   *  * WorksAtProject:
   *    - updatedOn (type DATE): mandatory=T, readOnly=F, notNull=F.
   *    - propWithoutTypeField (type not present in config --> property will be dropped): mandatory=T, readOnly=F, notNull=F.
   *  * HasManager:
   *    - updatedOn (type DATE): mandatory=F.
   */

  public void test1() {

    Connection connection = null;
    Statement st = null;
    OrientGraphNoTx orientGraph = null;

    try {

      Class.forName("org.hsqldb.jdbc.JDBCDriver");
      connection = DriverManager.getConnection("jdbc:hsqldb:mem:mydb", "SA", "");

      String parentTableBuilding = "create memory table EMPLOYEE (EMP_ID varchar(256) not null,"+
          " FIRST_NAME varchar(256) not null, LAST_NAME varchar(256) not null, PROJECT varchar(256) not null, primary key (EMP_ID))";
      st = connection.createStatement();
      st.execute(parentTableBuilding);

      String foreignTableBuilding = "create memory table PROJECT (ID  varchar(256),"+
          " TITLE varchar(256) not null, PROJECT_MANAGER varchar(256) not null, primary key (ID))";
      st.execute(foreignTableBuilding);

      ODocument config = OFileManager.buildJsonFromFile(this.configDirectEdgesPath);

      this.mapper = new OER2GraphMapper("org.hsqldb.jdbc.JDBCDriver", "jdbc:hsqldb:mem:mydb", "SA", "", null, null, config);
      mapper.buildSourceSchema(this.context);
      mapper.buildGraphModel(new OJavaConventionNameResolver(), context);
      modelWriter.writeModelOnOrient(mapper.getGraphModel(), new OHSQLDBDataTypeHandler(), this.outOrientGraphUri, context);


      /*
       *  Testing context information
       */

      assertEquals(2, context.getStatistics().totalNumberOfVertexType);
      assertEquals(2, context.getStatistics().wroteVertexType);
      assertEquals(2, context.getStatistics().totalNumberOfEdgeType);
      assertEquals(2, context.getStatistics().wroteEdgeType);
      assertEquals(2, context.getStatistics().totalNumberOfIndices);
      assertEquals(2, context.getStatistics().wroteIndexes);

      /*
       *  Testing built OrientDB schema
       */

      orientGraph = new OrientGraphNoTx(this.outOrientGraphUri);
      OrientVertexType employeeVertexType =  orientGraph.getVertexType("Employee");
      OrientVertexType projectVertexType = orientGraph.getVertexType("Project");
      OrientEdgeType worksAtProjectEdgeType = orientGraph.getEdgeType("WorksAtProject");
      OrientEdgeType hasManagerEdgeType = orientGraph.getEdgeType("HasManager");

      // vertices check
      assertNotNull(employeeVertexType);
      assertNotNull(projectVertexType);

      // properties check
      assertNotNull(employeeVertexType.getProperty("empId"));
      assertEquals("empId", employeeVertexType.getProperty("empId").getName());
      assertEquals(OType.STRING, employeeVertexType.getProperty("empId").getType());
      assertEquals(false, employeeVertexType.getProperty("empId").isMandatory());
      assertEquals(false, employeeVertexType.getProperty("empId").isReadonly());
      assertEquals(false, employeeVertexType.getProperty("empId").isNotNull());

      assertNotNull(employeeVertexType.getProperty("firstName"));
      assertEquals("firstName", employeeVertexType.getProperty("firstName").getName());
      assertEquals(OType.STRING, employeeVertexType.getProperty("firstName").getType());
      assertEquals(false, employeeVertexType.getProperty("firstName").isMandatory());
      assertEquals(false, employeeVertexType.getProperty("firstName").isReadonly());
      assertEquals(false, employeeVertexType.getProperty("firstName").isNotNull());

      assertNotNull(employeeVertexType.getProperty("lastName"));
      assertEquals("lastName", employeeVertexType.getProperty("lastName").getName());
      assertEquals(OType.STRING, employeeVertexType.getProperty("lastName").getType());
      assertEquals(false, employeeVertexType.getProperty("lastName").isMandatory());
      assertEquals(false, employeeVertexType.getProperty("lastName").isReadonly());
      assertEquals(false, employeeVertexType.getProperty("lastName").isNotNull());

      assertNotNull(employeeVertexType.getProperty("project"));
      assertEquals("project", employeeVertexType.getProperty("project").getName());
      assertEquals(OType.STRING, employeeVertexType.getProperty("project").getType());
      assertEquals(false, employeeVertexType.getProperty("project").isMandatory());
      assertEquals(false, employeeVertexType.getProperty("project").isReadonly());
      assertEquals(false, employeeVertexType.getProperty("project").isNotNull());

      assertNotNull(projectVertexType.getProperty("id"));
      assertEquals("id", projectVertexType.getProperty("id").getName());
      assertEquals(OType.STRING, projectVertexType.getProperty("id").getType());
      assertEquals(false, projectVertexType.getProperty("id").isMandatory());
      assertEquals(false, projectVertexType.getProperty("id").isReadonly());
      assertEquals(false, projectVertexType.getProperty("id").isNotNull());

      assertNotNull(projectVertexType.getProperty("title"));
      assertEquals("title", projectVertexType.getProperty("title").getName());
      assertEquals(OType.STRING, projectVertexType.getProperty("title").getType());
      assertEquals(false, projectVertexType.getProperty("title").isMandatory());
      assertEquals(false, projectVertexType.getProperty("title").isReadonly());
      assertEquals(false, projectVertexType.getProperty("title").isNotNull());

      assertNotNull(projectVertexType.getProperty("projectManager"));
      assertEquals("projectManager", projectVertexType.getProperty("projectManager").getName());
      assertEquals(OType.STRING, projectVertexType.getProperty("projectManager").getType());
      assertEquals(false, projectVertexType.getProperty("projectManager").isMandatory());
      assertEquals(false, projectVertexType.getProperty("projectManager").isReadonly());
      assertEquals(false, projectVertexType.getProperty("projectManager").isNotNull());

      // edges check
      assertNotNull(worksAtProjectEdgeType);
      assertNotNull(hasManagerEdgeType);

      assertEquals("WorksAtProject", worksAtProjectEdgeType.getName());
      assertEquals(1, worksAtProjectEdgeType.propertiesMap().size());

      assertEquals("updatedOn", worksAtProjectEdgeType.getProperty("updatedOn").getName());
      assertEquals(OType.DATE, worksAtProjectEdgeType.getProperty("updatedOn").getType());
      assertEquals(true, worksAtProjectEdgeType.getProperty("updatedOn").isMandatory());
      assertEquals(false, worksAtProjectEdgeType.getProperty("updatedOn").isReadonly());
      assertEquals(false, worksAtProjectEdgeType.getProperty("updatedOn").isNotNull());

      assertEquals("HasManager", hasManagerEdgeType.getName());
      assertEquals(1, hasManagerEdgeType.propertiesMap().size());

      assertEquals("updatedOn", hasManagerEdgeType.getProperty("updatedOn").getName());
      assertEquals(OType.DATE, hasManagerEdgeType.getProperty("updatedOn").getType());
      assertEquals(false, hasManagerEdgeType.getProperty("updatedOn").isMandatory());
      assertEquals(false, hasManagerEdgeType.getProperty("updatedOn").isReadonly());
      assertEquals(false, hasManagerEdgeType.getProperty("updatedOn").isNotNull());

      // Indices check
      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().existsIndex("Employee.pkey"));
      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().areIndexed("Employee", "empId"));

      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().existsIndex("Project.pkey"));
      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().areIndexed("Project", "id"));

    }catch(Exception e) {
      e.printStackTrace();
      fail();
    }finally {
      try {

        // Dropping Source DB Schema and OrientGraph
        String dbDropping = "drop schema public cascade";
        st.execute(dbDropping);
        connection.close();
      }catch(Exception e) {
        e.printStackTrace();
        fail();
      }
      if(orientGraph != null) {
        orientGraph.drop();
        orientGraph.shutdown();
      }
    }

  }

  @Test

  /*
   *  Two tables: 2 relationships declared through foreign keys but the first one is overridden through a configuration.
   *  Changes on the final edge:
   *  - name
   *  - direction inverted
   *  - property added
   *
   *  EMPLOYEE: foreign key (PROJECT) references PROJECT(ID)
   *  PROJECT: foreign key (PROJECT_MANAGER) references EMPLOYEE(EMP_ID)
   *
   *  With default mapping we would obtain:
   *
   *  EMPLOYEE --[HasProject]--> PROJECT
   *  PROJECT --[HasProjectManager]--> EMPLOYEE
   *
   *  But through configuration we obtain:
   *
   *  PROJECT --[HasEmployee]--> EMPLOYEE
   *  PROJECT --[HasProjectManager]--> EMPLOYEE
   *
   *  Properties manually configured on edges:
   *
   *  * HasEmployee:
   *    - updatedOn (type DATE): mandatory=T, readOnly=F, notNull=F.
   *    - propWithoutTypeField (type not present in config --> property will be dropped): mandatory=T, readOnly=F, notNull=F.
   */

  public void test2() {

    Connection connection = null;
    Statement st = null;
    OrientGraphNoTx orientGraph = null;

    try {

      Class.forName("org.hsqldb.jdbc.JDBCDriver");
      connection = DriverManager.getConnection("jdbc:hsqldb:mem:mydb", "SA", "");

      String parentTableBuilding = "create memory table EMPLOYEE (EMP_ID varchar(256) not null,"+
          " FIRST_NAME varchar(256) not null, LAST_NAME varchar(256) not null, PROJECT varchar(256) not null, primary key (EMP_ID))";
      st = connection.createStatement();
      st.execute(parentTableBuilding);

      String foreignTableBuilding = "create memory table PROJECT (ID  varchar(256),"+
          " TITLE varchar(256) not null, PROJECT_MANAGER varchar(256) not null, primary key (ID), "
          + "foreign key (PROJECT_MANAGER) references EMPLOYEE(EMP_ID))";
      st.execute(foreignTableBuilding);

      parentTableBuilding = "alter table EMPLOYEE add foreign key (PROJECT) references PROJECT(ID)";
      st = connection.createStatement();
      st.execute(parentTableBuilding);

      ODocument config = OFileManager.buildJsonFromFile(this.configInverseEdgesPath);

      this.mapper = new OER2GraphMapper("org.hsqldb.jdbc.JDBCDriver", "jdbc:hsqldb:mem:mydb", "SA", "", null, null, config);
      mapper.buildSourceSchema(this.context);
      mapper.buildGraphModel(new OJavaConventionNameResolver(), context);
      modelWriter.writeModelOnOrient(mapper.getGraphModel(), new OHSQLDBDataTypeHandler(), this.outOrientGraphUri, context);


      /*
       *  Testing context information
       */

      assertEquals(2, context.getStatistics().totalNumberOfVertexType);
      assertEquals(2, context.getStatistics().wroteVertexType);
      assertEquals(2, context.getStatistics().totalNumberOfEdgeType);
      assertEquals(2, context.getStatistics().wroteEdgeType);
      assertEquals(2, context.getStatistics().totalNumberOfIndices);
      assertEquals(2, context.getStatistics().wroteIndexes);

      /*
       *  Testing built OrientDB schema
       */

      orientGraph = new OrientGraphNoTx(this.outOrientGraphUri);
      OrientVertexType employeeVertexType =  orientGraph.getVertexType("Employee");
      OrientVertexType projectVertexType = orientGraph.getVertexType("Project");
      OrientEdgeType hasEmployeeEdgeType = orientGraph.getEdgeType("HasEmployee");
      OrientEdgeType hasProjectManagerEdgeType = orientGraph.getEdgeType("HasProjectManager");

      // vertices check
      assertNotNull(employeeVertexType);
      assertNotNull(projectVertexType);

      // properties check
      assertNotNull(employeeVertexType.getProperty("empId"));
      assertEquals("empId", employeeVertexType.getProperty("empId").getName());
      assertEquals(OType.STRING, employeeVertexType.getProperty("empId").getType());
      assertEquals(false, employeeVertexType.getProperty("empId").isMandatory());
      assertEquals(false, employeeVertexType.getProperty("empId").isReadonly());
      assertEquals(false, employeeVertexType.getProperty("empId").isNotNull());

      assertNotNull(employeeVertexType.getProperty("firstName"));
      assertEquals("firstName", employeeVertexType.getProperty("firstName").getName());
      assertEquals(OType.STRING, employeeVertexType.getProperty("firstName").getType());
      assertEquals(false, employeeVertexType.getProperty("firstName").isMandatory());
      assertEquals(false, employeeVertexType.getProperty("firstName").isReadonly());
      assertEquals(false, employeeVertexType.getProperty("firstName").isNotNull());

      assertNotNull(employeeVertexType.getProperty("lastName"));
      assertEquals("lastName", employeeVertexType.getProperty("lastName").getName());
      assertEquals(OType.STRING, employeeVertexType.getProperty("lastName").getType());
      assertEquals(false, employeeVertexType.getProperty("lastName").isMandatory());
      assertEquals(false, employeeVertexType.getProperty("lastName").isReadonly());
      assertEquals(false, employeeVertexType.getProperty("lastName").isNotNull());

      assertNotNull(employeeVertexType.getProperty("project"));
      assertEquals("project", employeeVertexType.getProperty("project").getName());
      assertEquals(OType.STRING, employeeVertexType.getProperty("project").getType());
      assertEquals(false, employeeVertexType.getProperty("project").isMandatory());
      assertEquals(false, employeeVertexType.getProperty("project").isReadonly());
      assertEquals(false, employeeVertexType.getProperty("project").isNotNull());

      assertNotNull(projectVertexType.getProperty("id"));
      assertEquals("id", projectVertexType.getProperty("id").getName());
      assertEquals(OType.STRING, projectVertexType.getProperty("id").getType());
      assertEquals(false, projectVertexType.getProperty("id").isMandatory());
      assertEquals(false, projectVertexType.getProperty("id").isReadonly());
      assertEquals(false, projectVertexType.getProperty("id").isNotNull());

      assertNotNull(projectVertexType.getProperty("title"));
      assertEquals("title", projectVertexType.getProperty("title").getName());
      assertEquals(OType.STRING, projectVertexType.getProperty("title").getType());
      assertEquals(false, projectVertexType.getProperty("title").isMandatory());
      assertEquals(false, projectVertexType.getProperty("title").isReadonly());
      assertEquals(false, projectVertexType.getProperty("title").isNotNull());

      assertNotNull(projectVertexType.getProperty("projectManager"));
      assertEquals("projectManager", projectVertexType.getProperty("projectManager").getName());
      assertEquals(OType.STRING, projectVertexType.getProperty("projectManager").getType());
      assertEquals(false, projectVertexType.getProperty("projectManager").isMandatory());
      assertEquals(false, projectVertexType.getProperty("projectManager").isReadonly());
      assertEquals(false, projectVertexType.getProperty("projectManager").isNotNull());

      // edges check
      assertNotNull(hasEmployeeEdgeType);
      assertNotNull(hasProjectManagerEdgeType);

      assertEquals("HasEmployee", hasEmployeeEdgeType.getName());
      assertEquals(1, hasEmployeeEdgeType.propertiesMap().size());

      assertEquals("updatedOn", hasEmployeeEdgeType.getProperty("updatedOn").getName());
      assertEquals(OType.DATE, hasEmployeeEdgeType.getProperty("updatedOn").getType());
      assertEquals(true, hasEmployeeEdgeType.getProperty("updatedOn").isMandatory());
      assertEquals(false, hasEmployeeEdgeType.getProperty("updatedOn").isReadonly());
      assertEquals(false, hasEmployeeEdgeType.getProperty("updatedOn").isNotNull());

      assertEquals("HasProjectManager", hasProjectManagerEdgeType.getName());
      assertEquals(0, hasProjectManagerEdgeType.propertiesMap().size());

      // Indices check
      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().existsIndex("Employee.pkey"));
      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().areIndexed("Employee", "empId"));

      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().existsIndex("Project.pkey"));
      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().areIndexed("Project", "id"));

    }catch(Exception e) {
      e.printStackTrace();
      fail();
    }finally {
      try {

        // Dropping Source DB Schema and OrientGraph
        String dbDropping = "drop schema public cascade";
        st.execute(dbDropping);
        connection.close();
      }catch(Exception e) {
        e.printStackTrace();
        fail();
      }
      if(orientGraph != null) {
        orientGraph.drop();
        orientGraph.shutdown();
      }
    }
  }

  @Test

  /*
   *  Three tables: 1  N-N relationship, no foreign keys declared for the join table in the db.
   *  Through the configuration we obtain the following schema:
   *
   *  ACTOR
   *  FILM
   *  ACTOR2FILM: foreign key (ACTOR_ID) references ACTOR(ID)
   *              foreign key (FILM_ID) references FILM(ID)
   *
   *  With "direct" direction in the configuration we obtain:
   *
   *  ACTOR --[Performs]--> FILM
   *
   *  Properties manually configured on edges:
   *
   *  Performs:
   *    - year (type DATE): mandatory=T, readOnly=F, notNull=F.
   */

  public void test3() {

    this.context.setExecutionStrategy("naive-aggregate");
    Connection connection = null;
    Statement st = null;
    OrientGraphNoTx orientGraph = null;

    try {

      Class.forName("org.hsqldb.jdbc.JDBCDriver");
      connection = DriverManager.getConnection("jdbc:hsqldb:mem:mydb", "SA", "");

      String parentTableBuilding = "create memory table ACTOR (ID varchar(256) not null,"+
          " FIRST_NAME varchar(256) not null, LAST_NAME varchar(256) not null, primary key (ID))";
      st = connection.createStatement();
      st.execute(parentTableBuilding);

      String foreignTableBuilding = "create memory table FILM (ID varchar(256),"+
          " TITLE varchar(256) not null, CATEGORY varchar(256), primary key (ID))";
      st.execute(foreignTableBuilding);

      String actorFilmTableBuilding = "create memory table ACTOR_FILM (ACTOR_ID  varchar(256),"+
          " FILM_ID varchar(256) not null, PAYMENT integer, primary key (ACTOR_ID, FILM_ID))";
      st.execute(actorFilmTableBuilding);

      ODocument config = OFileManager.buildJsonFromFile(this.configJoinTableDirectEdgesPath);

      this.mapper = new OER2GraphMapper("org.hsqldb.jdbc.JDBCDriver", "jdbc:hsqldb:mem:mydb", "SA", "", null, null, config);
      mapper.buildSourceSchema(this.context);
      mapper.buildGraphModel(new OJavaConventionNameResolver(), context);
      mapper.joinTableDim2Aggregation(this.context);
      modelWriter.writeModelOnOrient(mapper.getGraphModel(), new OHSQLDBDataTypeHandler(), this.outOrientGraphUri, context);


      /*
       *  Testing context information
       */

      assertEquals(2, context.getStatistics().totalNumberOfVertexType);
      assertEquals(2, context.getStatistics().wroteVertexType);
      assertEquals(1, context.getStatistics().totalNumberOfEdgeType);
      assertEquals(1, context.getStatistics().wroteEdgeType);
      assertEquals(2, context.getStatistics().totalNumberOfIndices);
      assertEquals(2, context.getStatistics().wroteIndexes);

      /*
       *  Testing built OrientDB schema
       */

      orientGraph = new OrientGraphNoTx(this.outOrientGraphUri);
      OrientVertexType actorVertexType =  orientGraph.getVertexType("Actor");
      OrientVertexType filmVertexType = orientGraph.getVertexType("Film");
      OrientEdgeType performsEdgeType = orientGraph.getEdgeType("Performs");

      // vertices check
      assertNotNull(actorVertexType);
      assertNotNull(filmVertexType);

      // properties check
      assertNotNull(actorVertexType.getProperty("id"));
      assertEquals("id", actorVertexType.getProperty("id").getName());
      assertEquals(OType.STRING, actorVertexType.getProperty("id").getType());
      assertEquals(false, actorVertexType.getProperty("id").isMandatory());
      assertEquals(false, actorVertexType.getProperty("id").isReadonly());
      assertEquals(false, actorVertexType.getProperty("id").isNotNull());

      assertNotNull(actorVertexType.getProperty("firstName"));
      assertEquals("firstName", actorVertexType.getProperty("firstName").getName());
      assertEquals(OType.STRING, actorVertexType.getProperty("firstName").getType());
      assertEquals(false, actorVertexType.getProperty("firstName").isMandatory());
      assertEquals(false, actorVertexType.getProperty("firstName").isReadonly());
      assertEquals(false, actorVertexType.getProperty("firstName").isNotNull());

      assertNotNull(actorVertexType.getProperty("lastName"));
      assertEquals("lastName", actorVertexType.getProperty("lastName").getName());
      assertEquals(OType.STRING, actorVertexType.getProperty("lastName").getType());
      assertEquals(false, actorVertexType.getProperty("lastName").isMandatory());
      assertEquals(false, actorVertexType.getProperty("lastName").isReadonly());
      assertEquals(false, actorVertexType.getProperty("lastName").isNotNull());

      assertNotNull(filmVertexType.getProperty("id"));
      assertEquals("id", filmVertexType.getProperty("id").getName());
      assertEquals(OType.STRING, filmVertexType.getProperty("id").getType());
      assertEquals(false, filmVertexType.getProperty("id").isMandatory());
      assertEquals(false, filmVertexType.getProperty("id").isReadonly());
      assertEquals(false, filmVertexType.getProperty("id").isNotNull());

      assertNotNull(filmVertexType.getProperty("title"));
      assertEquals("title", filmVertexType.getProperty("title").getName());
      assertEquals(OType.STRING, filmVertexType.getProperty("title").getType());
      assertEquals(false, filmVertexType.getProperty("title").isMandatory());
      assertEquals(false, filmVertexType.getProperty("title").isReadonly());
      assertEquals(false, filmVertexType.getProperty("title").isNotNull());

      assertNotNull(filmVertexType.getProperty("category"));
      assertEquals("category", filmVertexType.getProperty("category").getName());
      assertEquals(OType.STRING, filmVertexType.getProperty("category").getType());
      assertEquals(false, filmVertexType.getProperty("category").isMandatory());
      assertEquals(false, filmVertexType.getProperty("category").isReadonly());
      assertEquals(false, filmVertexType.getProperty("category").isNotNull());

      // edges check
      assertNotNull(performsEdgeType);

      assertEquals("Performs", performsEdgeType.getName());
      assertEquals(2, performsEdgeType.propertiesMap().size());

      assertEquals("year", performsEdgeType.getProperty("year").getName());
      assertEquals(OType.DATE, performsEdgeType.getProperty("year").getType());
      assertEquals(true, performsEdgeType.getProperty("year").isMandatory());
      assertEquals(false, performsEdgeType.getProperty("year").isReadonly());
      assertEquals(false, performsEdgeType.getProperty("year").isNotNull());

      assertEquals("payment", performsEdgeType.getProperty("payment").getName());
      assertEquals(OType.INTEGER, performsEdgeType.getProperty("payment").getType());
      assertEquals(false, performsEdgeType.getProperty("payment").isMandatory());
      assertEquals(false, performsEdgeType.getProperty("payment").isReadonly());
      assertEquals(false, performsEdgeType.getProperty("payment").isNotNull());

      // Indices check
      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().existsIndex("Actor.pkey"));
      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().areIndexed("Actor", "id"));

      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().existsIndex("Film.pkey"));
      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().areIndexed("Film", "id"));

    }catch(Exception e) {
      e.printStackTrace();
      fail();
    }finally {
      try {

        // Dropping Source DB Schema and OrientGraph
        String dbDropping = "drop schema public cascade";
        st.execute(dbDropping);
        connection.close();
      }catch(Exception e) {
        e.printStackTrace();
        fail();
      }
    }
  }


  @Test

  /*
   *  Three tables: 1  N-N relationship, no foreign keys declared for the join table in the db.
   *  Through the configuration we obtain the following schema:
   *
   *  ACTOR
   *  FILM
   *  ACTOR2FILM: foreign key (ACTOR_ID) references ACTOR(ID)
   *              foreign key (FILM_ID) references FILM(ID)
   *
   *  With "direct" direction in the configuration we would obtain:
   *
   *  FILM --[Performs]--> ACTOR
   *
   *  But with the "inverse" direction we obtain:
   *
   *  ACTOR --[Performs]--> FILM
   *
   *  Performs:
   *    - year (type DATE): mandatory=T, readOnly=F, notNull=F.
   */

  public void test4() {

    this.context.setExecutionStrategy("naive-aggregate");
    Connection connection = null;
    Statement st = null;
    OrientGraphNoTx orientGraph = null;

    try {

      Class.forName("org.hsqldb.jdbc.JDBCDriver");
      connection = DriverManager.getConnection("jdbc:hsqldb:mem:mydb", "SA", "");

      String parentTableBuilding = "create memory table ACTOR (ID varchar(256) not null,"+
          " FIRST_NAME varchar(256) not null, LAST_NAME varchar(256) not null, primary key (ID))";
      st = connection.createStatement();
      st.execute(parentTableBuilding);

      String foreignTableBuilding = "create memory table FILM (ID varchar(256),"+
          " TITLE varchar(256) not null, CATEGORY varchar(256), primary key (ID))";
      st.execute(foreignTableBuilding);

      String actorFilmTableBuilding = "create memory table FILM_ACTOR (ACTOR_ID  varchar(256),"+
          " FILM_ID varchar(256) not null, PAYMENT integer, primary key (ACTOR_ID, FILM_ID))";
      st.execute(actorFilmTableBuilding);

      ODocument config = OFileManager.buildJsonFromFile(this.configJoinTableInverseEdgesPath);

      this.mapper = new OER2GraphMapper("org.hsqldb.jdbc.JDBCDriver", "jdbc:hsqldb:mem:mydb", "SA", "", null, null, config);
      mapper.buildSourceSchema(this.context);
      mapper.buildGraphModel(new OJavaConventionNameResolver(), context);
      mapper.joinTableDim2Aggregation(this.context);
      modelWriter.writeModelOnOrient(mapper.getGraphModel(), new OHSQLDBDataTypeHandler(), this.outOrientGraphUri, context);


      /*
       *  Testing context information
       */

      assertEquals(2, context.getStatistics().totalNumberOfVertexType);
      assertEquals(2, context.getStatistics().wroteVertexType);
      assertEquals(1, context.getStatistics().totalNumberOfEdgeType);
      assertEquals(1, context.getStatistics().wroteEdgeType);
      assertEquals(2, context.getStatistics().totalNumberOfIndices);
      assertEquals(2, context.getStatistics().wroteIndexes);

      /*
       *  Testing built OrientDB schema
       */

      orientGraph = new OrientGraphNoTx(this.outOrientGraphUri);
      OrientVertexType actorVertexType =  orientGraph.getVertexType("Actor");
      OrientVertexType filmVertexType = orientGraph.getVertexType("Film");
      OrientEdgeType performsEdgeType = orientGraph.getEdgeType("Performs");

      // vertices check
      assertNotNull(actorVertexType);
      assertNotNull(filmVertexType);

      // properties check
      assertNotNull(actorVertexType.getProperty("id"));
      assertEquals("id", actorVertexType.getProperty("id").getName());
      assertEquals(OType.STRING, actorVertexType.getProperty("id").getType());
      assertEquals(false, actorVertexType.getProperty("id").isMandatory());
      assertEquals(false, actorVertexType.getProperty("id").isReadonly());
      assertEquals(false, actorVertexType.getProperty("id").isNotNull());

      assertNotNull(actorVertexType.getProperty("firstName"));
      assertEquals("firstName", actorVertexType.getProperty("firstName").getName());
      assertEquals(OType.STRING, actorVertexType.getProperty("firstName").getType());
      assertEquals(false, actorVertexType.getProperty("firstName").isMandatory());
      assertEquals(false, actorVertexType.getProperty("firstName").isReadonly());
      assertEquals(false, actorVertexType.getProperty("firstName").isNotNull());

      assertNotNull(actorVertexType.getProperty("lastName"));
      assertEquals("lastName", actorVertexType.getProperty("lastName").getName());
      assertEquals(OType.STRING, actorVertexType.getProperty("lastName").getType());
      assertEquals(false, actorVertexType.getProperty("lastName").isMandatory());
      assertEquals(false, actorVertexType.getProperty("lastName").isReadonly());
      assertEquals(false, actorVertexType.getProperty("lastName").isNotNull());

      assertNotNull(filmVertexType.getProperty("id"));
      assertEquals("id", filmVertexType.getProperty("id").getName());
      assertEquals(OType.STRING, filmVertexType.getProperty("id").getType());
      assertEquals(false, filmVertexType.getProperty("id").isMandatory());
      assertEquals(false, filmVertexType.getProperty("id").isReadonly());
      assertEquals(false, filmVertexType.getProperty("id").isNotNull());

      assertNotNull(filmVertexType.getProperty("title"));
      assertEquals("title", filmVertexType.getProperty("title").getName());
      assertEquals(OType.STRING, filmVertexType.getProperty("title").getType());
      assertEquals(false, filmVertexType.getProperty("title").isMandatory());
      assertEquals(false, filmVertexType.getProperty("title").isReadonly());
      assertEquals(false, filmVertexType.getProperty("title").isNotNull());

      assertNotNull(filmVertexType.getProperty("category"));
      assertEquals("category", filmVertexType.getProperty("category").getName());
      assertEquals(OType.STRING, filmVertexType.getProperty("category").getType());
      assertEquals(false, filmVertexType.getProperty("category").isMandatory());
      assertEquals(false, filmVertexType.getProperty("category").isReadonly());
      assertEquals(false, filmVertexType.getProperty("category").isNotNull());

      // edges check
      assertNotNull(performsEdgeType);

      assertEquals("Performs", performsEdgeType.getName());
      assertEquals(2, performsEdgeType.propertiesMap().size());

      assertEquals("year", performsEdgeType.getProperty("year").getName());
      assertEquals(OType.DATE, performsEdgeType.getProperty("year").getType());
      assertEquals(true, performsEdgeType.getProperty("year").isMandatory());
      assertEquals(false, performsEdgeType.getProperty("year").isReadonly());
      assertEquals(false, performsEdgeType.getProperty("year").isNotNull());

      assertEquals("payment", performsEdgeType.getProperty("payment").getName());
      assertEquals(OType.INTEGER, performsEdgeType.getProperty("payment").getType());
      assertEquals(false, performsEdgeType.getProperty("payment").isMandatory());
      assertEquals(false, performsEdgeType.getProperty("payment").isReadonly());
      assertEquals(false, performsEdgeType.getProperty("payment").isNotNull());

      // Indices check
      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().existsIndex("Actor.pkey"));
      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().areIndexed("Actor", "id"));

      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().existsIndex("Film.pkey"));
      assertEquals(true, orientGraph.getRawGraph().getMetadata().getIndexManager().areIndexed("Film", "id"));

    }catch(Exception e) {
      e.printStackTrace();
      fail();
    }finally {
      try {

        // Dropping Source DB Schema and OrientGraph
        String dbDropping = "drop schema public cascade";
        st.execute(dbDropping);
        connection.close();
      }catch(Exception e) {
        e.printStackTrace();
        fail();
      }
    }
  }


}