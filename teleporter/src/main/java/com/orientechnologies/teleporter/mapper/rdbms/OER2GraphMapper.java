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

package com.orientechnologies.teleporter.mapper.rdbms;

import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.teleporter.configuration.OConfigurationHandler;
import com.orientechnologies.teleporter.configuration.api.*;
import com.orientechnologies.teleporter.context.OTeleporterContext;
import com.orientechnologies.teleporter.context.OTeleporterStatistics;
import com.orientechnologies.teleporter.exception.OTeleporterRuntimeException;
import com.orientechnologies.teleporter.importengine.rdbms.dbengine.ODBQueryEngine;
import com.orientechnologies.teleporter.mapper.OSource2GraphMapper;
import com.orientechnologies.teleporter.mapper.rdbms.classmapper.OClassMapper;
import com.orientechnologies.teleporter.model.dbschema.*;
import com.orientechnologies.teleporter.model.graphmodel.*;
import com.orientechnologies.teleporter.nameresolver.ONameResolver;
import com.orientechnologies.teleporter.persistence.util.ODBSourceConnection;
import com.orientechnologies.teleporter.persistence.util.OQueryResult;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * Implementation of OSource2GraphMapper that manages the source DB schema and the destination graph model with their correspondences.
 * It has the responsibility to build in memory the two models: the first is built from the source DB meta-data through the JDBC driver,
 * the second from the source DB schema just created.
 *
 * @author Gabriele Ponzi
 * @email  <gabriele.ponzi--at--gmail.com>
 *
 */

public class OER2GraphMapper extends OSource2GraphMapper {

  protected ODBSourceConnection dbSourceConnection;

  // source model
  protected ODataBaseSchema dataBaseSchema;

  // New Rules
  protected Map<OEntity,List<OClassMapper>> entity2classMappers;
  protected Map<OVertexType,List<OClassMapper>> vertexType2classMappers;
  protected Map<ORelationship,OEdgeType> relationship2edgeType;
  protected Map<OEdgeType,LinkedList<ORelationship>> edgeType2relationships;
  protected Map<String,Integer> edgeTypeName2count;
  protected Map<OVertexType,OAggregatorEdge> joinVertex2aggregatorEdges;

  // filters
  protected List<String> includedTables;
  protected List<String> excludedTables;

  // supplementary jsonConfiguration
  protected ODocument jsonConfiguration;
  protected OConfigurationHandler parser;

  public final int DEFAULT_CLASS_MAPPER_INDEX = 0;

  public OER2GraphMapper (String driver, String uri, String username, String password, List<String> includedTables, List<String> excludedTables, ODocument configuration) {
    this.dbSourceConnection = new ODBSourceConnection(driver, uri, username, password);

    // new maps
    this.entity2classMappers = new IdentityHashMap<OEntity,List<OClassMapper>>();
    this.vertexType2classMappers = new IdentityHashMap<OVertexType,List<OClassMapper>>();

    this.relationship2edgeType = new IdentityHashMap<ORelationship,OEdgeType>();
    this.edgeType2relationships = new IdentityHashMap<OEdgeType,LinkedList<ORelationship>>();
    this.edgeTypeName2count = new TreeMap<String,Integer>();
    this.joinVertex2aggregatorEdges = new LinkedHashMap<OVertexType, OAggregatorEdge>();

    if(includedTables != null)
      this.includedTables = includedTables;
    else
      this.includedTables = new ArrayList<String>();

    if(excludedTables != null)
      this.excludedTables = excludedTables;

    else
      this.excludedTables = new ArrayList<String>();
    this.jsonConfiguration = configuration;

    // creating the two empty models
    this.dataBaseSchema = new ODataBaseSchema();
    this.graphModel = new OGraphModel();

    this.parser = new OConfigurationHandler();

  }

  // old map managing
  public void upsertRelationshipEdgeRules(ORelationship currentRelationship, OEdgeType currentEdgeType) {
    this.relationship2edgeType.put(currentRelationship, currentEdgeType);

    LinkedList<ORelationship> representedRelationships = this.edgeType2relationships.get(currentEdgeType);
    if(representedRelationships == null) {
      representedRelationships = new LinkedList<ORelationship>();
    }
    representedRelationships.add(currentRelationship);
    this.edgeType2relationships.put(currentEdgeType, representedRelationships);
  }

  public void upsertClassMappingRules(OEntity currentEntity, OVertexType currentVertexType, OClassMapper classMapper) {

    List<OClassMapper> classMappings = this.entity2classMappers.get(currentEntity);
    if(classMappings == null) {
      classMappings = new LinkedList<OClassMapper>();
    }
    classMappings.add(classMapper);
    this.entity2classMappers.put(currentEntity, classMappings);

    classMappings = this.vertexType2classMappers.get(currentVertexType);
    if(classMappings == null) {
      classMappings = new LinkedList<OClassMapper>();
    }
    classMappings.add(classMapper);
    this.vertexType2classMappers.put(currentVertexType, classMappings);
  }

  public List<OClassMapper> getClassMappersByVertex(OVertexType vertexType) {
    return this.vertexType2classMappers.get(vertexType);
  }

  public List<OClassMapper> getClassMappersByEntity(OEntity entity) {
    return this.entity2classMappers.get(entity);
  }

  public Map<OEntity, List<OClassMapper>> getEntity2classMappers() {
    return this.entity2classMappers;
  }

  public Map<OVertexType, List<OClassMapper>> getVertexType2classMappers() {
    return this.vertexType2classMappers;
  }

  /**
   * MACRO EXECUTION BLOCK: BUILD SOURCE DATABASE SCHEMA
   * Builds the database schema and the rules for the mapping with the graph model through 3 micro execution blocks:
   *  - Build Entities
   *  - Build Out-Relationships
   *  - Build In-Relationships
   *
   * @param context
   */

  public void buildSourceDatabaseSchema(OTeleporterContext context) {

    Connection connection = null;
    OTeleporterStatistics statistics = context.getStatistics();
    statistics.startWork1Time = new Date();
    statistics.runningStepNumber = 1;
    statistics.notifyListeners();

    try {

      connection = this.dbSourceConnection.getConnection(context);
      DatabaseMetaData databaseMetaData = connection.getMetaData();

      /*
       *  General DB Info and table filtering
       */

      int majorVersion = databaseMetaData.getDatabaseMajorVersion();
      int minorVersion = databaseMetaData.getDatabaseMinorVersion();
      int driverMajorVersion = databaseMetaData.getDriverMajorVersion();
      int driverMinorVersion = databaseMetaData.getDriverMinorVersion();
      String productName = databaseMetaData.getDatabaseProductName();
      String productVersion = databaseMetaData.getDatabaseProductVersion();

      this.dataBaseSchema.setMajorVersion(majorVersion);
      this.dataBaseSchema.setMinorVersion(minorVersion);
      this.dataBaseSchema.setDriverMajorVersion(driverMajorVersion);
      this.dataBaseSchema.setDriverMinorVersion(driverMinorVersion);
      this.dataBaseSchema.setProductName(productName);
      this.dataBaseSchema.setProductVersion(productVersion);

      /*
       *  Entity building
       */

      int numberOfTables = this.buildEntities(databaseMetaData, connection, context);

      /*
       *  Building Out-relationships
       */

      buildOutRelationships(databaseMetaData, numberOfTables, context);


      /*
       *  Building In-relationships
       */

      buildInRelationships(context);


    } catch (SQLException e) {
      String mess = "";
      context.printExceptionMessage(e, mess, "error");
      context.printExceptionStackTrace(e, "error");
      throw new OTeleporterRuntimeException(e);
    }finally {
      try {
        if(connection != null) {
          connection.close();
        }
      } catch (SQLException e) {
        String mess = "";
        context.printExceptionMessage(e, mess, "error");
        context.printExceptionStackTrace(e, "debug");
      }
    }

    try {
      if(connection.isClosed())
        context.getOutputManager().debug("\nConnection to DB closed.\n");
      else {
        statistics.warningMessages.add("\nConnection to DB not closed.\n");
      }
    } catch (SQLException e) {
      String mess = "";
      context.printExceptionMessage(e, mess, "error");
      context.printExceptionStackTrace(e, "debug");
    }
    statistics.notifyListeners();
    statistics.runningStepNumber = -1;
  }


  /**
   * MICRO EXECUTION BLOCK: BUILD SOURCE DATABASE SCHEMA - BUILD ENTITIES
   * Builds the Entities starting from the source database metadata.
   *
   * @param databaseMetaData
   * @param sourceDBConnection
   * @param context
   * @return
   * @throws SQLException
   */

  private int buildEntities(DatabaseMetaData databaseMetaData, Connection sourceDBConnection, OTeleporterContext context) throws SQLException {

    OTeleporterStatistics statistics = context.getStatistics();
    Map<String,String> tablesName2schema = new LinkedHashMap<String,String>();

    String tableCatalog = null;
    String tableSchemaPattern = null;
    String tableNamePattern = null;
    String[] tableTypes = {"TABLE"};
    if(this.dbSourceConnection.getDriver().contains("Oracle")) {
      ResultSet schemas = databaseMetaData.getSchemas();
      while(schemas.next()) {
        if(schemas.getString(1).equalsIgnoreCase(this.dbSourceConnection.getUsername())) {
          tableSchemaPattern = schemas.getString(1);
          break;
        }
      }
    }
    ResultSet resultTable = databaseMetaData.getTables(tableCatalog, tableSchemaPattern, tableNamePattern, tableTypes);

    // Giving db's table names
    while(resultTable.next()) {
      String tableSchema = resultTable.getString("TABLE_SCHEM");
      String tableName = resultTable.getString("TABLE_NAME");

      if(this.isTableAllowed(tableName))  // filtering tables according to "include-list" and "exclude-list"
        tablesName2schema.put(tableName,tableSchema);
    }

    int numberOfTables = tablesName2schema.size();
    statistics.totalNumberOfEntities = numberOfTables;

    // closing resultTable
    this.closeCursor(resultTable, context);
    context.getOutputManager().debug("\n%s tables found.\n", numberOfTables);

    // Variables for records counting
    Statement statement = sourceDBConnection.createStatement();
    ODBQueryEngine dbQueryEngine = context.getDbQueryEngine();
    int totalNumberOfRecord = 0;

    int iteration = 1;
    for(String currentTableName: tablesName2schema.keySet()) {

      context.getOutputManager().debug("\nBuilding '%s' entity (%s/%s)...\n", currentTableName, iteration, numberOfTables);

      // Counting current-table's record
      String currentTableSchema = tablesName2schema.get(currentTableName);
      String sql;

      OQueryResult result = dbQueryEngine.countTableRecords(currentTableName, currentTableSchema, context);

      ResultSet currentTableRecordAmount = result.getResult();
      if (currentTableRecordAmount.next()) {
        totalNumberOfRecord += currentTableRecordAmount.getInt(1);
      }
      this.closeCursor(currentTableRecordAmount, context);

      // creating entity
      OEntity currentEntity = new OEntity(currentTableName, currentTableSchema);

      // adding attributes and primary keys
      OPrimaryKey pKey = new OPrimaryKey(currentEntity);

      String columnCatalog = null;
      String columnSchemaPattern = null;
      String columnNamePattern = null;
      String primaryKeyCatalog = null;
      String primaryKeySchema = currentTableSchema;

      ResultSet resultColumns = databaseMetaData.getColumns(columnCatalog, columnSchemaPattern, currentTableName, columnNamePattern);
      ResultSet resultPrimaryKeys = databaseMetaData.getPrimaryKeys(primaryKeyCatalog, primaryKeySchema, currentTableName);

      List<String> currentPrimaryKeys = this.getPrimaryKeysFromResulset(resultPrimaryKeys);

      while(resultColumns.next()) {
        OAttribute currentAttribute = new OAttribute(resultColumns.getString("COLUMN_NAME"), resultColumns.getInt("ORDINAL_POSITION"), resultColumns.getString("TYPE_NAME"), currentEntity);
        currentEntity.addAttribute(currentAttribute);

        // if the current attribute is involved in the primary key, it will be added to the attributes of pKey.
        if(currentPrimaryKeys.contains(currentAttribute.getName())) {
          pKey.addAttribute(currentAttribute);
        }
      }
      this.closeCursor(resultColumns, context);
      this.closeCursor(resultPrimaryKeys, context);

      currentEntity.setPrimaryKey(pKey);

      // if the primary key doesn't involve any attribute, a warning message is generated
      if(pKey.getInvolvedAttributes().size() == 0)
        context.getStatistics().warningMessages.add("It's not declared a primary key for the Entity " + currentEntity.getName() + ", this might lead to issues during the migration or the sync executions "
                + "(the first importing is quite safe).");

      // adding entity to db schema
      this.dataBaseSchema.getEntities().add(currentEntity);

      iteration++;
      context.getOutputManager().debug("\nEntity %s built.\n", currentTableName);
      statistics.builtEntities++;
      statistics.totalNumberOfRecords = totalNumberOfRecord;
    }
    statement.close();

    return numberOfTables;
  }


  /**
   * MICRO EXECUTION BLOCK: BUILD SOURCE DATABASE SCHEMA - BUILD OUT-RELATIONSHIPS
   * Builds the references to the "Out Relationships" starting from the source database metadata.
   *
   * @param databaseMetaData
   * @param numberOfTables
   * @param context
   * @throws SQLException
   */

  private void buildOutRelationships(DatabaseMetaData databaseMetaData, int numberOfTables, OTeleporterContext context) throws SQLException {

    OTeleporterStatistics statistics = context.getStatistics();

    int iteration = 1;
    for(OEntity currentForeignEntity: this.dataBaseSchema.getEntities()) {

      String currentForeignEntityName = currentForeignEntity.getName();
      String foreignSchema = currentForeignEntity.getSchemaName();
      context.getOutputManager().debug("\nBuilding OUT relationships starting from '%s' entity (%s/%s)...\n", currentForeignEntityName, iteration, numberOfTables);

      String foreignCatalog = null;
      ResultSet resultForeignKeys = databaseMetaData.getImportedKeys(foreignCatalog, foreignSchema, currentForeignEntityName);

      // copy of Resultset in a HashLinkedMap
      List<LinkedHashMap<String, String>> currentEntityRelationships1 = this.fromResultSetToList(resultForeignKeys, context);
      List<LinkedHashMap<String, String>> currentEntityRelationships2 = new LinkedList<LinkedHashMap<String,String>>();

      for(LinkedHashMap<String,String> row: currentEntityRelationships1) {
        currentEntityRelationships2.add(row);
      }

      this.closeCursor(resultForeignKeys, context);

      Iterator<LinkedHashMap<String,String>> it1 = currentEntityRelationships1.iterator();
      Iterator<LinkedHashMap<String,String>> it2 = currentEntityRelationships2.iterator();

      while(it1.hasNext()) {
        LinkedHashMap<String,String> currentExternalRow = it1.next();

        // current row has Key_Seq equals to '2' then algorithm is finished and is stopped
        if(currentExternalRow.get("key_seq").equals("2")) {
          break;
        }

        // the original relationship is fetched from the record through the 'parent table' and the 'key sequence numbers'
        String currentParentTableName = currentExternalRow.get("pktable_name");
        int currentKeySeq = Integer.parseInt(currentExternalRow.get("key_seq"));

        // building each single relationship from each correspondent foreign key
        OEntity currentParentTable = this.dataBaseSchema.getEntityByName(currentParentTableName);
        ORelationship currentRelationship = new ORelationship(currentForeignEntity, currentParentTable);
        OForeignKey currentFk = new OForeignKey(currentForeignEntity);
        while(it2.hasNext()) {
          LinkedHashMap<String,String> row = it2.next();
          if(row.get("pktable_name").equals(currentParentTableName) && Integer.parseInt(row.get("key_seq")) == currentKeySeq) {
            currentFk.addAttribute(currentForeignEntity.getAttributeByName((String) row.get("fkcolumn_name")));
            it2.remove();
            currentKeySeq++;
          }
        }

        // iterator reset
        it2 = currentEntityRelationships2.iterator();

        // searching correspondent primary key
        OPrimaryKey currentPk = this.dataBaseSchema.getEntityByName(currentParentTableName).getPrimaryKey();

        // adding foreign key to the entity and the relationship, and adding the foreign key to the 'foreign entity'
        currentRelationship.setPrimaryKey(currentPk);
        currentRelationship.setForeignKey(currentFk);
        currentForeignEntity.getForeignKeys().add(currentFk);

        // adding the relationship to the db schema
        this.dataBaseSchema.getRelationships().add(currentRelationship);
        // adding relationship to the current entity
        currentForeignEntity.getOutRelationships().add(currentRelationship);
        // updating statistics
        statistics.builtRelationships += 1;
      }

      iteration++;
      context.getOutputManager().debug("\nOUT Relationships from %s built.\n", currentForeignEntityName);
      statistics.entitiesAnalyzedForRelationship++;
    }

    statistics.totalNumberOfRelationships = this.dataBaseSchema.getRelationships().size();
  }


  /**
   * MICRO EXECUTION BLOCK: BUILD SOURCE DATABASE SCHEMA - BUILD IN-RELATIONSHIPS
   * Builds the references to the "In Relationships" starting from the references to the "Out Relationships".
   *
   * @param context
   */

  private void buildInRelationships(OTeleporterContext context) {

    int iteration = 1;
    context.getOutputManager().debug("\nConnecting IN relationships...\n");

    for(ORelationship currentRelationship: this.dataBaseSchema.getRelationships()) {
      OEntity currentInEntity = this.getDataBaseSchema().getEntityByName(currentRelationship.getParentEntity().getName());
      currentInEntity.getInRelationships().add(currentRelationship);
    }
    context.getOutputManager().debug("\nIN relationships built.\n");
  }


  private List<String> getPrimaryKeysFromResulset(ResultSet resultPrimaryKeys) throws SQLException {

    List<String> currentPrimaryKeys = new LinkedList<String>();

    while(resultPrimaryKeys.next()) {
      currentPrimaryKeys.add(resultPrimaryKeys.getString(4));
    }
    return currentPrimaryKeys;
  }


  /**
   * @param result
   */
  private void closeCursor(ResultSet result, OTeleporterContext context) {
    try {
      if(result != null)
        result.close();
    } catch (SQLException e) {
      String mess = "";
      context.printExceptionMessage(e, mess, "error");
      context.printExceptionStackTrace(e, "debug");
    }
  }


  /*
   * Transforms a ResultSet in a List, filtering relationships according to "include/exclude-lists"
   */

  private List<LinkedHashMap<String,String>> fromResultSetToList(ResultSet resultForeignKeys, OTeleporterContext context) {

    List<LinkedHashMap<String, String>> rows = new LinkedList<LinkedHashMap<String,String>>();

    try{
      int columnsAmount = resultForeignKeys.getMetaData().getColumnCount();

      while(resultForeignKeys.next()) {

        if(this.isTableAllowed(resultForeignKeys.getString("pktable_name")) && this.dataBaseSchema.getEntityByName(resultForeignKeys.getString("pktable_name")) != null) {
          //          if(this.isTableAllowed(resultForeignKeys.getString("pktable_name")) && this.dataBaseSchema.getEntityByName(resultForeignKeys.getString("pktable_name")) != null) {

          LinkedHashMap<String,String> row = new LinkedHashMap<String,String>();
          for(int i=1; i<=columnsAmount; i++) {
            row.put(resultForeignKeys.getMetaData().getColumnName(i).toLowerCase(), resultForeignKeys.getString(i));
          }
          rows.add(row);
        }
      }
    }catch(SQLException e) {
      String mess = "";
      context.printExceptionMessage(e, mess, "error");
      context.printExceptionStackTrace(e, "error");
      throw new OTeleporterRuntimeException(e);
    }
    return rows;
  }


  @Override
  /**
   * MACRO EXECUTION BLOCK: BUILD GRAPH MODEL
   * Builds the graph model and the rules for the mapping with the source database schema through 2 micro execution blocks:
   *  - Build Vertex Types
   *  - Build Edge Types
   *
   *  @param nameResolver
   *  @param context
   */

  public void buildGraphModel(ONameResolver nameResolver, OTeleporterContext context) {

    OTeleporterStatistics statistics = context.getStatistics();
    statistics.startWork2Time = new Date();
    statistics.runningStepNumber = 2;


    /*
     *  Vertex-types building
     */

    this.buildVertexTypes(nameResolver, context);


    /*
     *  Edge-types building
     */

    this.buildEdgeTypes(nameResolver, context);

    statistics.notifyListeners();
    statistics.runningStepNumber = -1;
  }


  /**
   * MICRO EXECUTION BLOCK: BUILD GRAPH MODEL - BUILD VERTEX TYPES
   * Builds the Vertex Types starting from the Entities in the source database schema.
   *
   * @param nameResolver
   * @param context
   */

  private void buildVertexTypes(ONameResolver nameResolver, OTeleporterContext context) {

    OTeleporterStatistics statistics = context.getStatistics();

    int numberOfVertexType = this.dataBaseSchema.getEntities().size();
    statistics.totalNumberOfModelVertices = numberOfVertexType;
    int iteration = 1;
    for(OEntity currentEntity: this.dataBaseSchema.getEntities()) {

      context.getOutputManager().debug("\nBuilding '%s' vertex-type (%s/%s)...\n", currentEntity.getName(), iteration, numberOfVertexType);

      // building correspondent vertex-type
      String currentVertexTypeName = nameResolver.resolveVertexName(currentEntity.getName());

      // fetch the vertex type from the graph model (empty vertex, only name defined), if does not exist create it.
      boolean alreadyPresentInGraphModel = true;
      OVertexType currentVertexType = this.graphModel.getVertexTypeByName(currentVertexTypeName);
      if(currentVertexType == null) {
        currentVertexType = new OVertexType(currentVertexTypeName);
        alreadyPresentInGraphModel = false;
      }

      // recognizing joint tables of dimension 2
      if(currentEntity.isAggregableJoinTable())
        currentVertexType.setIsFromJoinTable(true);
      else
        currentVertexType.setIsFromJoinTable(false);

      // adding attributes to vertex-type
      Map<String,String> attribute2property = new LinkedHashMap<String,String>();   // map to maintain the mapping between the attributes of the current entity and the properties of the correspondent vertex type
      Map<String,String> property2attribute = new LinkedHashMap<String,String>();   // map to maintain the mapping between the properties of the current vertex type and the attributes of the correspondent entity
      for(OAttribute currentAttribute: currentEntity.getAttributes()) {
        OModelProperty currentProperty = new OModelProperty(nameResolver.resolveVertexProperty(currentAttribute.getName()), currentAttribute.getOrdinalPosition(),
                currentAttribute.getDataType(), currentEntity.getPrimaryKey().getInvolvedAttributes().contains(currentAttribute), currentVertexType);
        currentVertexType.getProperties().add(currentProperty);

        attribute2property.put(currentAttribute.getName(), currentProperty.getName());
        property2attribute.put(currentProperty.getName(), currentAttribute.getName());
      }

      // adding inherited attributes to vertex-type
      for(OAttribute attribute: currentEntity.getInheritedAttributes()) {
        OModelProperty currentProperty = new OModelProperty(nameResolver.resolveVertexProperty(attribute.getName()), attribute.getOrdinalPosition(),
                attribute.getDataType(), currentEntity.getPrimaryKey().getInvolvedAttributes().contains(attribute), currentVertexType);
        currentVertexType.getInheritedProperties().add(currentProperty);
        // TODO: Adding inherited attributes and props to the maps?
      }

      // adding parent vertex if the corresponding entity has a parent
      if(currentEntity.getParentEntity() != null) {
        OElementType currentParentElement = this.graphModel.getVertexTypeByNameIgnoreCase(currentEntity.getParentEntity().getName());
        currentVertexType.setParentType(currentParentElement);
        currentVertexType.setInheritanceLevel(currentEntity.getInheritanceLevel());
      }

      // adding vertex to the graph model
      if(!alreadyPresentInGraphModel) {
        this.graphModel.getVerticesType().add(currentVertexType);
      }

      // rules updating
      OClassMapper classMapper = new OClassMapper(currentEntity, currentVertexType, attribute2property, property2attribute);
      this.upsertClassMappingRules(currentEntity, currentVertexType, classMapper);

      iteration++;
      context.getOutputManager().debug("\nVertex-type %s built.\n", currentVertexTypeName);
      statistics.builtModelVertexTypes++;
    }

    // sorting vertices type for inheritance level and then for name
    Collections.sort(this.graphModel.getVerticesType());
  }


  /**
   * MICRO EXECUTION BLOCK: BUILD GRAPH MODEL - BUILD EDGE TYPES
   * Builds the Edge Types starting from the Relationships in the source database schema.
   *
   * @param nameResolver
   * @param context
   */

  private void buildEdgeTypes(ONameResolver nameResolver, OTeleporterContext context) {

    OTeleporterStatistics statistics = context.getStatistics();

    int numberOfEdgeType = this.dataBaseSchema.getRelationships().size();
    statistics.totalNumberOfModelEdges = numberOfEdgeType;
    String edgeType = null;
    int iteration = 1;

    if (numberOfEdgeType > 0) {

      // edges added through relationships (foreign keys of db)
      for(OEntity currentEntity: this.dataBaseSchema.getEntities()) {

        for(ORelationship relationship: currentEntity.getOutRelationships()) {

          OVertexType currentOutVertex = this.getVertexTypeByEntity(relationship.getForeignEntity());
          OVertexType currentInVertex = this.getVertexTypeByEntity(relationship.getParentEntity());
          context.getOutputManager().debug("\nBuilding edge-type from '%s' to '%s' (%s/%s)...\n", currentOutVertex.getName(), currentInVertex.getName(), iteration, numberOfEdgeType);

          if(currentOutVertex != null && currentInVertex != null) {

            // check on the presence of the relationship in the map performed in order to avoid generating several edgeTypes for the same relationship.
            // when the edge was built before from the jsonConfiguration and the relationship was inserted with that edgeType in the map, the relationships
            // mustn't be analyzed at this point! CHANGE IT when you'll implement the pipeline
            if(!this.relationship2edgeType.containsKey(relationship)) {

              // relationships which represents inheritance between different entities don't generate new edge-types,
              // thus new edge type is created iff the parent-table's name (of the relationship) does not coincide
              // with the name of the parent entity of the current entity.
              if (currentEntity.getParentEntity() == null || !currentEntity.getParentEntity().getName().equals(relationship.getParentEntity().getName())) {

                // if the class edge doesn't exists, it will be created
                edgeType = nameResolver.resolveEdgeName(relationship);

                OEdgeType currentEdgeType = this.graphModel.getEdgeTypeByName(edgeType);
                if (currentEdgeType == null) {
                  currentEdgeType = new OEdgeType(edgeType, null, currentInVertex);
                  this.graphModel.getEdgesType().add(currentEdgeType);
                  context.getOutputManager().debug("\nEdge-type %s built.\n", currentEdgeType.getName());
                  statistics.builtModelEdgeTypes++;
                } else {
                  // edge already present, the counter of relationships represented by the edge is incremented
                  currentEdgeType.setNumberRelationshipsRepresented(currentEdgeType.getNumberRelationshipsRepresented() + 1);
                }

                // adding the edge to the two vertices
                if (!currentOutVertex.getOutEdgesType().contains(currentEdgeType)) {
                  currentOutVertex.getOutEdgesType().add(currentEdgeType);
                }
                if (!currentInVertex.getInEdgesType().contains(currentEdgeType)) {
                  currentInVertex.getInEdgesType().add(currentEdgeType);
                }

                // rules updating
                this.upsertRelationshipEdgeRules(relationship, currentEdgeType);
              }
            }
          }
          else {
            context.getOutputManager().error("Error during graph model building phase: information loss, relationship missed. Edge-type not built.\n");
          }

          iteration++;
        }

        // building edges starting from inherited relationships

        for(ORelationship relationship: currentEntity.getInheritedOutRelationships()) {
          OVertexType currentOutVertex = this.getVertexTypeByEntity(currentEntity);
          OVertexType currentInVertex = this.getVertexTypeByEntity(relationship.getParentEntity());

          context.getOutputManager().debug("\nBuilding edge-type from '%s' to '%s' (%s/%s)...\n", currentOutVertex.getName(), currentInVertex.getName(), iteration, numberOfEdgeType);

          if(currentOutVertex != null && currentInVertex != null) {

            OEdgeType currentEdgeType = this.graphModel.getEdgeTypeByName(edgeType);

            // adding the edge to the two vertices
            currentOutVertex.getOutEdgesType().add(currentEdgeType);
            currentInVertex.getInEdgesType().add(currentEdgeType);
            context.getOutputManager().debug("\nEdge-type built.\n");
          }
          else {
            context.getOutputManager().error("Error during graph model building phase: information loss, relationship missed. Edge-type not built.\n");
          }
        }
      }

      // Updating the total number of model edges with the actual number of built model edges since it was initialized with the number of relationships in the source db schema.
      // In fact if there are relationships representing hierarchy then the number of built edges is less than the number of relationships.
      statistics.totalNumberOfModelEdges = statistics.builtModelEdgeTypes;

    }
  }


  /**
   * MACRO EXECUTION BLOCK: APPLY IMPORT CONFIGURATION
   * Builds the graph model and the rules for the mapping with the source database schema through 2 micro execution blocks:
   *  - upsert Relationships according to the jsonConfiguration
   *
   *  @param context
   */

  public void applyImportConfiguration(OTeleporterContext context) {

    if(this.jsonConfiguration != null) {

      OConfiguration configuration = this.parser.buildConfigurationFromJSON(this.jsonConfiguration, context);

    /*
     * Adding/updating classes according to the manual jsonConfiguration
     */
      this.upsertClassesFromConfiguration(configuration, context);

    /*
     * Adding/updating relationships according to the manual jsonConfiguration
     */
      this.upsertRelationshipsFromConfiguration(configuration, context);
    }
  }


  /**
   * MICRO EXECUTION BLOCK: APPLY IMPORT CONFIGURATION - UPSERT CLASSES' MAPPING FROM CONFIGURATION
   * Builds the Vertex Types starting from the Entities in the source database schema.
   * Adds and/or updates Vertex Types and Entities in the source database schema according to the manual jsonConfiguration passed to Teleporter.
   *
   * @param configuration
   * @param context
   */

  private void upsertClassesFromConfiguration(OConfiguration configuration, OTeleporterContext context) {

    OTeleporterStatistics statistics = context.getStatistics();
    List<OConfiguredVertexClass> configuredVertices = configuration.getConfiguredVertices();

    // building the current-in-vertex and the current-out-vertex and adding the edge to them
    ONameResolver nameResolver = context.getNameResolver();

    for(OConfiguredVertexClass currentConfiguredVertexClass: configuredVertices) {
      OVertexMappingInformation currentMapping = currentConfiguredVertexClass.getMapping();
      List<OSourceTable> sourceTables = currentMapping.getSourceTables();
      String aggregationFunction = currentMapping.getAggregationFunction();

      Map<String,String> sourceId2tableName = new HashMap<String,String>();

      for(OSourceTable currentSourceTable: sourceTables) {
        sourceId2tableName.put(currentSourceTable.getSourceIdName(), currentSourceTable.getTableName());
      }

      // no-aggregation case
      if(sourceId2tableName.size() == 1) {

        String sourceTableName = sourceId2tableName.entrySet().iterator().next().getValue();
        OClassMapper currentClassMapper = this.entity2classMappers.get(this.dataBaseSchema.getEntityByName(sourceTableName)).get(0);

        // updating vertex and table mapping according to the jsonConfiguration
        OVertexType currentVertexType = currentClassMapper.getVertexType();
        currentVertexType.setName(currentConfiguredVertexClass.getName());

        for(OConfiguredProperty configuredProperty: currentConfiguredVertexClass.getConfiguredProperties()) {

          OConfiguredPropertyMapping propertyMapping = configuredProperty.getPropertyMapping();
          String columnName = propertyMapping.getColumnName();
          String originalType = propertyMapping.getType();
          String originalPropertyName = currentClassMapper.getPropertyByAttribute(propertyMapping.getColumnName());
          OModelProperty propertyToUpdate = currentVertexType.getPropertyByNameAmongAll(originalPropertyName);
          String actualPropertyName = configuredProperty.getPropertyName();

          if(!originalPropertyName.equals(actualPropertyName)) {
            propertyToUpdate.setName(actualPropertyName);
            // updating properties mapping
            currentClassMapper.getAttribute2property().put(columnName,actualPropertyName);
            currentClassMapper.getProperty2attribute().remove(originalPropertyName);
            currentClassMapper.getProperty2attribute().put(actualPropertyName,columnName);
          }
          propertyToUpdate.setIncludedInMigration(configuredProperty.isIncludedInMigration());
          propertyToUpdate.setOrientdbType(configuredProperty.getPropertyType());
          propertyToUpdate.setMandatory(configuredProperty.isMandatory());
          propertyToUpdate.setReadOnly(configuredProperty.isReadOnly());
          propertyToUpdate.setNotNull(configuredProperty.isNotNull());
          propertyToUpdate.setOriginalType(originalType);

          /**
           * Updating rules
           */

          // updating properties mapping
          if(!configuredProperty.isIncludedInMigration()) {
            currentClassMapper.getAttribute2property().put(columnName,null);
            currentClassMapper.getProperty2attribute().remove(actualPropertyName);
          }
        }
      }

      // aggregation case
      else if(sourceId2tableName.size() > 1) {

        String[][] columns = new String[sourceTables.size()][sourceTables.get(0).getAggregationColumns().size()];
        int j = 0;
        for (OSourceTable currentSourceTable : sourceTables) {
          List<String> aggregationColumns = currentSourceTable.getAggregationColumns();

          if (aggregationColumns != null) {
            int k = 0;
            for (String attribute : aggregationColumns) {
              columns[j][k] = attribute;
              k++;
            }
          }
          j++;
        }

        if (aggregationFunction != null) {
          context.setAggregationFunction(aggregationFunction);
        }
        if (columns[0][0] != null) {
          context.setColumns(columns);
        }

        List<OVertexType> verticesToMerge = new LinkedList<OVertexType>();
        OVertexType newAggregatedVertexType = new OVertexType(currentConfiguredVertexClass.getName());

        // merging vertices correspondent
        for (String sourceTableName : sourceId2tableName.values()) {
          List<OClassMapper> currentClassMappers = this.entity2classMappers.get(this.dataBaseSchema.getEntityByName(sourceTableName));
          for (OClassMapper currentClassMapper : currentClassMappers) {
            verticesToMerge.add(currentClassMapper.getVertexType());
          }
        }

        /**
         * checking the vertex types are aggregable:
         * (i)  not coming from join tables
         * (ii) if they belong tho hierarchical bag, merge is allowed iff vertices are all siblings (they all have the same parentType)
         */

        boolean aggregable = true;
        OVertexType currentParentType = null;
        String errorMsg = null;

        // (i)
        for (OVertexType v : verticesToMerge) {
          if (v.isFromJoinTable()) {
            aggregable = false;
            errorMsg = "'" + v.getName() + "' comes from a join table, thus the requested aggregation will be skipped.";
            break;
          }
        }

        if (aggregable) {

          // (ii)
          OVertexType firstParentVertexType = (OVertexType) verticesToMerge.get(0).getParentType();
          for (OVertexType v : verticesToMerge) {
            OVertexType currentParentVertexType = (OVertexType) v.getParentType();
            if (firstParentVertexType == null) {
              if (currentParentVertexType != null) {
                aggregable = false;
                break;
              }
            } else if (!v.getParentType().equals(firstParentVertexType)) {
              aggregable = false;
              break;
            }
          }
        }

        if (aggregable) {

          /**
           *  merging in and out edges
           */

          for (OVertexType currentVertexType : verticesToMerge) {

            // updating in edges
            for (OEdgeType currentInEdgeType : currentVertexType.getInEdgesType()) {
              currentInEdgeType.setInVertexType(newAggregatedVertexType);
              newAggregatedVertexType.getInEdgesType().add(currentInEdgeType);
            }

            // updating out edges
            for (OEdgeType currentOutEdgeType : currentVertexType.getOutEdgesType()) {
              currentOutEdgeType.setOutVertexType(newAggregatedVertexType);
              newAggregatedVertexType.getOutEdgesType().add(currentOutEdgeType);
            }
          }

          /**
           *  merging properties
           */

          int ordinalPosition = 1;
          Map<String, List<OConfiguredProperty>> originalTable2configuredProperties = new LinkedHashMap<String, List<OConfiguredProperty>>();

          for (OConfiguredProperty configuredProperty : currentConfiguredVertexClass.getConfiguredProperties()) {

            // clustering configured properties by original table they are coming from
            OConfiguredPropertyMapping propertyMapping = configuredProperty.getPropertyMapping();
            String source = propertyMapping.getSourceName();
            String tableName = sourceId2tableName.get(source);
            List<OConfiguredProperty> configuredproperties = originalTable2configuredProperties.get(tableName);
            if (configuredproperties == null) {
              configuredproperties = new LinkedList<OConfiguredProperty>();
            }
            configuredproperties.add(configuredProperty);
            originalTable2configuredProperties.put(tableName, configuredproperties);

            String propertyName = configuredProperty.getPropertyName();
            boolean include = configuredProperty.isIncludedInMigration();
            String orientdbType = configuredProperty.getPropertyType();
            boolean isMandatory = configuredProperty.isMandatory();
            boolean isReadOnly = configuredProperty.isReadOnly();
            boolean isNotNull = configuredProperty.isNotNull();

            String columnName = propertyMapping.getColumnName();
            String originalType = propertyMapping.getType();
            String originalSource = propertyMapping.getSourceName();
            String originalSourceTableName = sourceId2tableName.get(originalSource);

            boolean fromPrimaryKey = false;
            OClassMapper currentClassMapper = this.entity2classMappers.get(this.dataBaseSchema.getEntityByName(originalSourceTableName)).get(0);
            OEntity currentEntity =  currentClassMapper.getEntity();
            if (currentEntity.getPrimaryKey().getAttributeByName(columnName) != null) {
              fromPrimaryKey = true;
            }

            OModelProperty property = new OModelProperty(propertyName, ordinalPosition, originalType, orientdbType, fromPrimaryKey, newAggregatedVertexType, isMandatory, isReadOnly, isNotNull);
            property.setIncludedInMigration(include);

            // adding the property to the new aggregated vertex type
            newAggregatedVertexType.getProperties().add(property);
            ordinalPosition++;
          }


          /**
           *  updating rules
           */

          // removing old vertices' rules
          for (OVertexType v : verticesToMerge) {
            this.vertexType2classMappers.remove(v);
          }

          for (String tableName : originalTable2configuredProperties.keySet()) {
            OEntity currentEntity = this.getDataBaseSchema().getEntityByName(tableName);
            Map<String, String> attribute2property = new HashMap<String, String>();
            Map<String, String> property2attribute = new HashMap<String, String>();

            for(OConfiguredProperty prop: originalTable2configuredProperties.get(tableName)) {
              String columnName = prop.getPropertyMapping().getColumnName();
              String propertyName = prop.getPropertyName();
              if (prop.isIncludedInMigration()) {
                attribute2property.put(columnName, propertyName);
                property2attribute.put(propertyName, columnName);
              } else {
                attribute2property.put(columnName, null);
              }
            }

            // removing old entities' rules
            this.entity2classMappers.remove(currentEntity);
            OClassMapper currentNewCM = new OClassMapper(currentEntity, newAggregatedVertexType, attribute2property, property2attribute);

            // adding new rules
            this.upsertClassMappingRules(currentEntity, newAggregatedVertexType, currentNewCM);
          }

          // deleting old vertices just aggregated into the new vertex type
          for (OVertexType v : verticesToMerge) {
            this.getGraphModel().getVerticesType().remove(v);
            statistics.totalNumberOfModelVertices--;
            statistics.builtModelVertexTypes--;
          }

          // adding the new aggregated vertex type
          this.getGraphModel().getVerticesType().add(newAggregatedVertexType);
          statistics.totalNumberOfModelVertices++;
          statistics.builtModelVertexTypes++;
        }
      }

    }
  }



  /**
   * MICRO EXECUTION BLOCK: APPLY IMPORT CONFIGURATION - UPSERT RELATIONSHIPS FROM CONFIGURATION
   * Builds the Edge Types starting from the Relationships in the source database schema.
   * Adds and/or updates Relationships in the source database schema according to the manual jsonConfiguration passed to Teleporter.
   *
   * @param configuration
   * @param context
   */

  private void upsertRelationshipsFromConfiguration(OConfiguration configuration, OTeleporterContext context) {

    List<OConfiguredEdgeClass> configuredEdges = configuration.getConfiguredEdges();

    // building the current-in-vertex and the current-out-vertex and adding the edge to them
    ONameResolver nameResolver = context.getNameResolver();

    for(OConfiguredEdgeClass currentEdgeClass: configuredEdges) {

      String edgeName = currentEdgeClass.getName();
      OEdgeMappingInformation edgeMapping = currentEdgeClass.getMapping();

      // building relationship
      String currentForeignEntityName = edgeMapping.getFromTableName();
      String currentParentEntityName = edgeMapping.getToTableName();
      List<String> fromColumns = edgeMapping.getFromColumns();
      List<String> toColumns = edgeMapping.getToColumns();
      OAggregatedJoinTableMapping joinTableMapping = edgeMapping.getRepresentedJoinTableMapping();

      // jsonConfiguration errors managing (draconian approach)
      if(currentForeignEntityName == null) {
        context.getOutputManager().error("Configuration error: 'fromTable' field not found in the '%s' edge-type definition.",  edgeName);
        throw new OTeleporterRuntimeException();
      }
      if(currentParentEntityName == null) {
        context.getOutputManager().error("Configuration error: 'toTable' field not found in the '%s' edge-type definition.",  edgeName);
        throw new OTeleporterRuntimeException();
      }
      if(fromColumns == null) {
        context.getOutputManager().error("Configuration error: 'fromColumns' field not found in the '%s' edge-type definition.",  edgeName);
        throw new OTeleporterRuntimeException();
      }
      if(toColumns == null) {
        context.getOutputManager().error("Configuration error: 'toColumns' field not found in the '%s' edge-type definition.",  edgeName);
        throw new OTeleporterRuntimeException();
      }

      String direction = edgeMapping.getDirection();

      if(direction != null && !(direction.equals("direct") || direction.equals("inverse"))) {
        context.getOutputManager().error("Configuration error: direction for the edge %s cannot be '%s'. Allowed values: 'direct' or 'inverse' \n", edgeName, direction);
        throw new OTeleporterRuntimeException();
      }

      boolean foreignEntityIsJoinTableToAggregate = false;

      if(joinTableMapping == null) {

        // building relationship
        ORelationship currentRelationship = buildRelationshipFromConfig(currentForeignEntityName, currentParentEntityName, fromColumns, toColumns, direction, foreignEntityIsJoinTableToAggregate, context);

        // building correspondent edgeType (check on inheritance not needed)
        buildEdgeTypeFromConfiguredRelationship(currentRelationship, edgeName, currentEdgeClass, foreignEntityIsJoinTableToAggregate, context);

      }
      else {

        String joinTableName = joinTableMapping.getTableName();
        foreignEntityIsJoinTableToAggregate = true;

        if(context.getExecutionStrategy().equals("naive-aggregate")) { // strategy is aggregated
          List<String> joinTableFromColumns = joinTableMapping.getFromColumns();
          List<String> joinTableToColumns = joinTableMapping.getToColumns();

          // building left relationship
          ORelationship currentRelationship = buildRelationshipFromConfig(joinTableName, currentForeignEntityName, joinTableFromColumns, fromColumns, direction, foreignEntityIsJoinTableToAggregate, context);

          // building correspondent edgeType (check on inheritance not needed)
          buildEdgeTypeFromConfiguredRelationship(currentRelationship, edgeName + "-left", currentEdgeClass, foreignEntityIsJoinTableToAggregate, context);

          // building right relationship
          currentRelationship = buildRelationshipFromConfig(joinTableName, currentParentEntityName, joinTableToColumns, toColumns, direction, foreignEntityIsJoinTableToAggregate, context);

          // building correspondent edgeType (check on inheritance not needed)
          buildEdgeTypeFromConfiguredRelationship(currentRelationship, edgeName + "-right", currentEdgeClass, foreignEntityIsJoinTableToAggregate, context);

          // setting attributes of the join table
          OEntity joinTable = this.dataBaseSchema.getEntityByName(joinTableName);
          joinTable.setIsAggregableJoinTable(true);
          joinTable.setDirectionOfN2NRepresentedRelationship(direction);
          joinTable.setNameOfN2NRepresentedRelationship(edgeName);

          // setting attributes of the correspondent vertex type
          OVertexType correspondentVertexType = this.getVertexTypeByEntity(joinTable);
          correspondentVertexType.setIsFromJoinTable(true);

        }
        else if(context.getExecutionStrategy().equals("naive")) {
          context.getOutputManager().error("Configuration not compliant with the chosen strategy: you cannot perform the aggregation declared in the jsonConfiguration for the "
                  + "join table %s while executing migration with a not-aggregating strategy. Thus no aggregation will be performed.\n", joinTableName);
          throw new OTeleporterRuntimeException();
        }

      }
    }

  }


  /**
   *
   * @param currentForeignEntityName
   * @param currentParentEntityName
   * @param fromColumns
   * @param toColumns
   * @param direction
   * @param context
   * @return
   */
  private ORelationship buildRelationshipFromConfig(String currentForeignEntityName, String currentParentEntityName, List<String> fromColumns,
                                                    List<String> toColumns, String direction, boolean foreignEntityIsJoinTableToAggregate, OTeleporterContext context) {

    OTeleporterStatistics statistics = context.getStatistics();

    // fetching foreign and parent entities
    OEntity currentForeignEntity = this.dataBaseSchema.getEntityByName(currentForeignEntityName);
    OEntity currentParentEntity = this.dataBaseSchema.getEntityByName(currentParentEntityName);

    // fetch relationship from current db schema, if not present create a new one
    boolean relationshipAlreadyPresentInDBSchema = true;
    ORelationship currentRelationship = this.dataBaseSchema.getRelationshipByInvolvedEntitiesAndAttributes(currentForeignEntity, currentParentEntity, fromColumns, toColumns);
    if (currentRelationship == null) {
      currentRelationship = new ORelationship(currentForeignEntity, currentParentEntity);
      relationshipAlreadyPresentInDBSchema = false;

      // updating statistics
      statistics.builtRelationships += 1;
      statistics.totalNumberOfRelationships += 1;
    }

    // Adding the direction of the relationship if it's different from null and if the foreign entity is not a join table.
    // In fact when the foreign table is a join table to aggregate, direction is referred to the aggregator edge, so left and right relationships remains direct.
    if (direction != null && !foreignEntityIsJoinTableToAggregate) {
      if ((direction.equals("direct") || direction.equals("inverse"))) {
        currentRelationship.setDirection(direction);
      } else {
        context.getOutputManager().error(
                "Wrong value for the direction of the relationship between %s and %s: \"%s\" is not a valid direction. " + "Please choose between \"direct\" or \"inverse\" \n", currentRelationship.getForeignEntity(),
                currentRelationship.getParentEntity(), direction);
      }
    }

    // if Relationship was not already present in the schema we must add the foreign key both to the 'foreign entity' and the relationship, and the primary key to the relationship in the db schema.
    if (!relationshipAlreadyPresentInDBSchema) {
      OForeignKey currentFk = new OForeignKey(currentForeignEntity);

      // adding attributes involved in the foreign key
      for (String column : fromColumns) {
        currentFk.addAttribute(currentForeignEntity.getAttributeByName(column));
      }

      // searching correspondent primary key
      OPrimaryKey currentPk = this.dataBaseSchema.getEntityByName(currentParentEntityName).getPrimaryKey();
      currentRelationship.setPrimaryKey(currentPk);
      currentRelationship.setForeignKey(currentFk);

      currentForeignEntity.getForeignKeys().add(currentFk);
      this.dataBaseSchema.getRelationships().add(currentRelationship);

      // adding relationship to the current entity
      currentForeignEntity.getOutRelationships().add(currentRelationship);
      currentParentEntity.getInRelationships().add(currentRelationship);
    }

    return currentRelationship;
  }


  /**
   *  @param currentRelationship
   * @param edgeName
   * @param currentEdgeClass
   * @param foreignEntityIsJoinTableToAggregate
   * @param context
   */
  private void buildEdgeTypeFromConfiguredRelationship(ORelationship currentRelationship, String edgeName, OConfiguredEdgeClass currentEdgeClass, boolean foreignEntityIsJoinTableToAggregate,
                                                       OTeleporterContext context) {

    OTeleporterStatistics statistics = context.getStatistics();

    OEntity currentParentEntity = currentRelationship.getParentEntity();
    OEntity currentForeignEntity = currentRelationship.getForeignEntity();

    // retrieving edge type, if not present is created from scratch
    boolean edgeTypeAlreadyPresent = true;
    OEdgeType currentEdgeType = this.relationship2edgeType.get(currentRelationship);
    if(currentEdgeType == null) {
      edgeTypeAlreadyPresent = false;
      currentEdgeType = new OEdgeType(edgeName, null, null);
      this.graphModel.getEdgesType().add(currentEdgeType);
      context.getOutputManager().debug("\nEdge-type %s built.\n", currentEdgeType.getName());
      statistics.builtModelEdgeTypes++;
      statistics.totalNumberOfModelEdges++;
    }
    currentEdgeType.setName(edgeName);

    // extracting properties info if present and adding them to the current edge-type
    List<OConfiguredProperty> properties = currentEdgeClass.getConfiguredProperties();

    // adding properties to the edge
    if(properties != null) {

      int ordinalPosition = currentEdgeType.getProperties().size() + 1;
      for(OConfiguredProperty configuredProperty: properties) {
        String propertyName = configuredProperty.getPropertyName();
        String propertyType = configuredProperty.getPropertyType();

        OModelProperty currentProperty = currentEdgeType.getPropertyByName(propertyName);
        if(currentProperty == null) {
          currentProperty = new OModelProperty(propertyName, ordinalPosition, propertyType, false, currentEdgeType);
          ordinalPosition++;
        }
        currentProperty.setFromPrimaryKey(false);
        Boolean mandatory = configuredProperty.isMandatory();
        if(mandatory != null) {
          currentProperty.setMandatory(mandatory);
        }
        Boolean readOnly = configuredProperty.isReadOnly();
        if(readOnly != null) {
          currentProperty.setReadOnly(readOnly);
        }
        Boolean notNull = configuredProperty.isNotNull();
        if(notNull != null) {
          currentProperty.setNotNull(notNull);
        }
        currentEdgeType.getProperties().add(currentProperty);
      }

    }

    String currentRelationshipDirection = currentRelationship.getDirection();
    OVertexType currentInVertexType;
    OVertexType currentOutVertexType;

    if(edgeTypeAlreadyPresent) {

      // If the edge type was already present and the direction is inverse (as to say if it was modified) the references with in-vertex and out-vertex must to be updated
      // If the foreign table is a join table then the direction is referred to the aggregator edge and not to the left or right one. So direction field is always "direct"
      // as it is inferred from the left or right relationship.
      if(currentRelationshipDirection != null && currentRelationshipDirection.equals("inverse")) {
        currentInVertexType = this.getVertexTypeByEntity(currentForeignEntity);
        currentOutVertexType = this.getVertexTypeByEntity(currentParentEntity);

        // removing on vertices the old references to the edge type
        currentInVertexType.getOutEdgesType().remove(currentEdgeType);
        currentOutVertexType.getInEdgesType().remove(currentEdgeType);

        // defining on vertices the new references to the edge type
        currentInVertexType.getInEdgesType().add(currentEdgeType);
        currentOutVertexType.getOutEdgesType().add(currentEdgeType);
        currentEdgeType.setInVertexType(currentInVertexType);
      }
    }

    // new references with in-vertex and out-vertex are added and the old
    else {

      /**
       *  Direction of the edge is decided according to three conditions:
       *  - direction != null or direction == null
       *  - current foreign entity is a join table or not
       *  - direction is direct or inverse
       */

      // if direction is null the edge will be direct by default
      if (currentRelationshipDirection == null) {
        currentInVertexType = this.getVertexTypeByEntity(currentParentEntity);
        currentOutVertexType = this.getVertexTypeByEntity(currentForeignEntity);
      } else {

        // if the current foreign entity is a join table we will aggregate then the edge will be direct
        if (foreignEntityIsJoinTableToAggregate) {
          currentInVertexType = this.getVertexTypeByEntity(currentParentEntity);
          currentOutVertexType = this.getVertexTypeByEntity(currentForeignEntity);
        } else {

          // edge direction chosen according to the value of the parameter direction
          if (currentRelationshipDirection.equals("direct")) {
            currentInVertexType = this.getVertexTypeByEntity(currentParentEntity);
            currentOutVertexType = this.getVertexTypeByEntity(currentForeignEntity);
          } else {
            currentInVertexType = this.getVertexTypeByEntity(currentForeignEntity);
            currentOutVertexType = this.getVertexTypeByEntity(currentParentEntity);
          }
        }
      }

      currentInVertexType.getInEdgesType().add(currentEdgeType);
      currentOutVertexType.getOutEdgesType().add(currentEdgeType);
      currentEdgeType.setInVertexType(currentInVertexType);

      // rules updating
      upsertRelationshipEdgeRules(currentRelationship, currentEdgeType);
    }

  }


  /**
   * MACRO EXECUTION BLOCK: PERFORM AGGREGATIONS
   * Performs aggregation strategies on the graph model through the following micro execution blocks:
   *  - Many-To-Many Aggregation
   *
   * @param context
   */

  public void performAggregations(OTeleporterContext context) {

    /*
     * Many-To-Many Aggregation
     */
    performMany2ManyAggregation(context);
  }


  /**
   * MICRO EXECUTION BLOCK: PERFORM AGGREGATIONS - MANY TO MANY AGGREGATION
   * Aggregates Many-To-Many Relationships represented by join tables of dimension == 2.
   *
   * @param context
   */

  public void performMany2ManyAggregation(OTeleporterContext context) {

    OTeleporterStatistics statistics = context.getStatistics();
    Iterator<OVertexType> it = this.graphModel.getVerticesType().iterator();

    context.getOutputManager().debug("\n\nJoin Table aggregation phase...\n");

    while(it.hasNext()) {
      OVertexType currentVertexType = it.next();

      // if vertex is obtained from a join table of dimension 2,
      // then aggregation is performed
      if(currentVertexType.isFromJoinTable() && currentVertexType.getOutEdgesType().size() == 2) {

        // building new edge
        OEdgeType currentOutEdge1 = currentVertexType.getOutEdgesType().get(0);
        OEdgeType currentOutEdge2 = currentVertexType.getOutEdgesType().get(1);

        OVertexType outVertexType;
        OVertexType inVertexType;
        String direction = getEntityByVertexType(currentVertexType).getDirectionOfN2NRepresentedRelationship();
        if(direction.equals("direct")) {
          outVertexType = currentOutEdge1.getInVertexType();
          inVertexType = currentOutEdge2.getInVertexType();
        }
        else {
          outVertexType = currentOutEdge2.getInVertexType();
          inVertexType = currentOutEdge1.getInVertexType();
        }

        OEntity joinTable = this.getEntityByVertexType(currentVertexType);
        String nameOfRelationship = joinTable.getNameOfN2NRepresentedRelationship();
        String edgeType;
        if(nameOfRelationship != null)
          edgeType = nameOfRelationship;
        else
          edgeType = currentVertexType.getName();

        OEdgeType newAggregatorEdge = new OEdgeType(edgeType, null, inVertexType);

        int position = 1;
        // adding to the edge all properties not belonging to the primary key
        for(OModelProperty currentProperty: currentVertexType.getProperties()) {

          // if property does not belong to the primary key add it to the aggregator edge
          if(!currentProperty.isFromPrimaryKey()) {
            OModelProperty newProperty = new OModelProperty(currentProperty.getName(), position, currentProperty.getOriginalType(), currentProperty.isFromPrimaryKey(), newAggregatorEdge);
            if(currentProperty.isMandatory() != null)
              newProperty.setMandatory(currentProperty.isMandatory());
            if(currentProperty.isReadOnly() != null)
              newProperty.setReadOnly(currentProperty.isReadOnly());
            if(currentProperty.isNotNull() != null)
              newProperty.setNotNull(currentProperty.isNotNull());
            newAggregatorEdge.getProperties().add(newProperty);
            position++;
          }
        }

        // adding to the edge all properties belonging to the old edges
        for(OModelProperty currentProperty: currentOutEdge1.getProperties()) {
          if(newAggregatorEdge.getPropertyByName(currentProperty.getName()) == null) {
            OModelProperty newProperty = new OModelProperty(currentProperty.getName(), position, currentProperty.getOriginalType(), currentProperty.isFromPrimaryKey(), newAggregatorEdge);
            if(currentProperty.isMandatory() != null)
              newProperty.setMandatory(currentProperty.isMandatory());
            if(currentProperty.isReadOnly() != null)
              newProperty.setReadOnly(currentProperty.isReadOnly());
            if(currentProperty.isNotNull() != null)
              newProperty.setNotNull(currentProperty.isNotNull());
            newAggregatorEdge.getProperties().add(newProperty);
            position++;
          }
        }
        for(OModelProperty currentProperty: currentOutEdge2.getProperties()) {
          if(newAggregatorEdge.getPropertyByName(currentProperty.getName()) == null) {
            OModelProperty newProperty = new OModelProperty(currentProperty.getName(), position, currentProperty.getOriginalType(), currentProperty.isFromPrimaryKey(), newAggregatorEdge);
            if(currentProperty.isMandatory() != null)
              newProperty.setMandatory(currentProperty.isMandatory());
            if(currentProperty.isReadOnly() != null)
              newProperty.setReadOnly(currentProperty.isReadOnly());
            if(currentProperty.isNotNull() != null)
              newProperty.setNotNull(currentProperty.isNotNull());
            newAggregatorEdge.getProperties().add(newProperty);
            position++;
          }
        }

        // removing old edges from graph model and from vertices' "in-edges" collection
        currentOutEdge1.setNumberRelationshipsRepresented(currentOutEdge1.getNumberRelationshipsRepresented()-1);
        currentOutEdge2.setNumberRelationshipsRepresented(currentOutEdge2.getNumberRelationshipsRepresented()-1);

        if(currentOutEdge1.getNumberRelationshipsRepresented() == 0) {
          this.graphModel.getEdgesType().remove(currentOutEdge1);
          statistics.builtModelEdgeTypes--;
          statistics.totalNumberOfModelEdges--;
        }
        if(currentOutEdge2.getNumberRelationshipsRepresented() == 0) {
          this.graphModel.getEdgesType().remove(currentOutEdge2);
          statistics.builtModelEdgeTypes--;
          statistics.totalNumberOfModelEdges--;
        }
        if(direction.equals("direct")) {
          outVertexType.getInEdgesType().remove(currentOutEdge1);
          inVertexType.getInEdgesType().remove(currentOutEdge2);
        }
        else {
          outVertexType.getInEdgesType().remove(currentOutEdge2);
          inVertexType.getInEdgesType().remove(currentOutEdge1);
        }

        // adding entry to the map
        this.joinVertex2aggregatorEdges.put(currentVertexType, new OAggregatorEdge(outVertexType.getName(), inVertexType.getName(), newAggregatorEdge));

        // removing old vertex
        it.remove();
        statistics.builtModelVertexTypes--;
        statistics.totalNumberOfModelVertices--;

        // adding new edge to graph model
        this.graphModel.getEdgesType().add(newAggregatorEdge);
        statistics.builtModelEdgeTypes++;
        statistics.totalNumberOfModelEdges++;

        // adding new edge to the vertices' "in/out-edges" collections
        outVertexType.getOutEdgesType().add(newAggregatorEdge);
        inVertexType.getInEdgesType().add(newAggregatorEdge);
      }
    }
    context.getOutputManager().debug("\nAggregation performed.\n");
  }

  public ODataBaseSchema getDataBaseSchema() {
    return this.dataBaseSchema;
  }

  public void setDataBaseSchema(ODataBaseSchema dataBaseSchema) {
    this.dataBaseSchema = dataBaseSchema;
  }

  public OEntity getEntityByVertexType(OVertexType vertexType) {
    return this.getEntityByVertexType(vertexType, DEFAULT_CLASS_MAPPER_INDEX);
  }

  public OEntity getEntityByVertexType(OVertexType vertexType, int classMapperIndex) {
    return this.getClassMappersByVertex(vertexType).get(classMapperIndex).getEntity();
  }

  public OVertexType getVertexTypeByEntity(OEntity entity) {
    return this.getVertexTypeByEntity(entity, DEFAULT_CLASS_MAPPER_INDEX);
  }

  public OVertexType getVertexTypeByEntity(OEntity entity, int classMapperIndex) {
    return this.getClassMappersByEntity(entity).get(classMapperIndex).getVertexType();
  }

  public String getAttributeNameByVertexTypeAndProperty(OVertexType vertexType, String propertyName) {

    String attributeName = null;

    for(OClassMapper cm: this.getClassMappersByVertex(vertexType)) {
      attributeName = cm.getAttributeByProperty(propertyName);
      if(attributeName != null) {
        break;
      }
    }

    if(attributeName == null) {
      OVertexType parentType = (OVertexType) vertexType.getParentType();
      if(parentType != null) {
        return this.getAttributeNameByVertexTypeAndProperty(parentType, propertyName);
      }
    }

    return attributeName;
  }

  public String getPropertyNameByVertexTypeAndAttribute(OVertexType vertexType, String attributeName) {

    List<OClassMapper> classMappers = this.getClassMappersByVertex(vertexType);

    String propertyName = null;
    for(OClassMapper currentClassMapper: classMappers) {
      propertyName = currentClassMapper.getPropertyByAttribute(attributeName);
      if(propertyName != null) {
        // the right class mapper was found and the right property name with it
        break;
      }
    }

    if(propertyName == null) {
      OVertexType parentType = (OVertexType) vertexType.getParentType();
      if(parentType != null) {
        return this.getPropertyNameByVertexTypeAndAttribute(parentType, attributeName);
      }
    }

    return propertyName;
  }

  public String getPropertyNameByEntityAndAttribute(OEntity entity, String attributeName) {

    List<OClassMapper> classMappers = this.getClassMappersByEntity(entity);

    String propertyName = null;
    for(OClassMapper currentClassMapper: classMappers) {
      propertyName = currentClassMapper.getPropertyByAttribute(attributeName);
      if(propertyName != null) {
        // the right class mapper was found and the right property name with it
        break;
      }
    }

    if(propertyName == null) {
      OEntity parentEntity = (OEntity) entity.getParentEntity();
      if(parentEntity != null) {
        return this.getPropertyNameByEntityAndAttribute(parentEntity, attributeName);
      }
    }

    return propertyName;
  }


  /**
   * It returns the vertex type mapped with the aggregator edge correspondent to the original join table.
   *
   * @param edgeType
   * @return
   */

  public OVertexType getJoinVertexTypeByAggregatorEdge(String edgeType) {

    for(Map.Entry<OVertexType,OAggregatorEdge> entry: this.joinVertex2aggregatorEdges.entrySet()) {
      if(entry.getValue().getEdgeType().getName().equals(edgeType)) {
        OVertexType joinVertexType = entry.getKey();
        return joinVertexType;
      }
    }
    return null;
  }

  public OAggregatorEdge getAggregatorEdgeByJoinVertexTypeName(String vertexTypeName) {

    for(OVertexType currentVertexType: this.joinVertex2aggregatorEdges.keySet()) {
      if(currentVertexType.getName().equals(vertexTypeName)) {
        return this.joinVertex2aggregatorEdges.get(currentVertexType);
      }
    }
    return null;
  }

  public Map<ORelationship, OEdgeType> getRelationship2edgeType() {
    return this.relationship2edgeType;
  }

  public void setRelationship2edgeType(Map<ORelationship, OEdgeType> relationship2edgeTypeRules) {
    this.relationship2edgeType = relationship2edgeTypeRules;
  }

  public Map<OEdgeType, LinkedList<ORelationship>> getEdgeType2relationships() {
    return this.edgeType2relationships;
  }

  public void setEdgeType2relationships(Map<OEdgeType, LinkedList<ORelationship>> edgeType2relationships) {
    this.edgeType2relationships = edgeType2relationships;
  }

  public Map<String,Integer> getEdgeTypeName2count() {
    return this.edgeTypeName2count;
  }

  public void setEdgeTypeName2count(Map<String, Integer> edgeTypeName2count) {
    this.edgeTypeName2count = edgeTypeName2count;
  }

  public Map<OVertexType, OAggregatorEdge> getJoinVertex2aggregatorEdges() {
    return joinVertex2aggregatorEdges;
  }


  public void setJoinVertex2aggregatorEdges(Map<OVertexType, OAggregatorEdge> joinVertex2aggregatorEdges) {
    this.joinVertex2aggregatorEdges = joinVertex2aggregatorEdges;
  }


  public List<String> getIncludedTables() {
    return includedTables;
  }


  public void setIncludedTables(List<String> includedTables) {
    this.includedTables = includedTables;
  }


  public List<String> getExcludedTables() {
    return excludedTables;
  }


  public void setExcludedTables(List<String> excludedTables) {
    this.excludedTables = excludedTables;
  }

  public ODocument getJsonConfiguration() {
    return this.jsonConfiguration;
  }

  public void setJsonConfiguration(ODocument jsonConfiguration) {
    this.jsonConfiguration = jsonConfiguration;
  }

  public boolean isTableAllowed(String tableName) {

    if(this.includedTables.size() > 0)
      return this.includedTables.contains(tableName);
    else if (this.excludedTables.size() > 0)
      return !this.excludedTables.contains(tableName);
    else
      return true;

  }

  public String toString() {

    String s = "\n\n\n------------------------------ MAPPER DESCRIPTION ------------------------------\n\n\n";
    s += "RULES\n\n";
    s += "- Class mappings:\n\n";
    for(List<OClassMapper> classMappers: this.entity2classMappers.values()) {
      for(OClassMapper classMapper: classMappers) {
        s += classMapper.toString() + "\n";
      }
    }
    s += "\n\n- Relaionship2EdgeType Rules:\n\n";
    for(ORelationship relationship: this.relationship2edgeType.keySet()) {
      s += relationship.getForeignEntity() + "2" + relationship.getParentEntity() + " --> " + this.relationship2edgeType.get(relationship).getName() + "\n";
    }
    s += "\n\n- EdgeTypeName2Count Rules:\n\n";
    for(String edgeName: this.edgeTypeName2count.keySet()) {
      s += edgeName + " --> " + this.edgeTypeName2count.get(edgeName) + "\n";
    }
    s += "\n";


    return s;
  }

}
