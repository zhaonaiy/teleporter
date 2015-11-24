/*
 * Copyright 2015 Orient Technologies LTD (info--at--orientechnologies.com)
 * All Rights Reserved. Commercial License.
 * 
 * NOTICE:  All information contained herein is, and remains the property of
 * Orient Technologies LTD and its suppliers, if any.  The intellectual and
 * technical concepts contained herein are proprietary to
 * Orient Technologies LTD and its suppliers and may be covered by United
 * Kingdom and Foreign Patents, patents in process, and are protected by trade
 * secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Orient Technologies LTD.
 * 
 * For more information: http://www.orientechnologies.com
 */

package com.orientechnologies.teleporter.mapper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.orientechnologies.teleporter.context.OTeleporterContext;
import com.orientechnologies.teleporter.context.OTeleporterStatistics;
import com.orientechnologies.teleporter.model.dbschema.OAttribute;
import com.orientechnologies.teleporter.model.dbschema.ODataBaseSchema;
import com.orientechnologies.teleporter.model.dbschema.ODataSourceSchema;
import com.orientechnologies.teleporter.model.dbschema.OEntity;
import com.orientechnologies.teleporter.model.dbschema.OForeignKey;
import com.orientechnologies.teleporter.model.dbschema.OPrimaryKey;
import com.orientechnologies.teleporter.model.dbschema.ORelationship;
import com.orientechnologies.teleporter.model.graphmodel.OEdgeType;
import com.orientechnologies.teleporter.model.graphmodel.OElementType;
import com.orientechnologies.teleporter.model.graphmodel.OGraphModel;
import com.orientechnologies.teleporter.model.graphmodel.OModelProperty;
import com.orientechnologies.teleporter.model.graphmodel.OVertexType;
import com.orientechnologies.teleporter.nameresolver.ONameResolver;
import com.orientechnologies.teleporter.persistence.util.ODBSourceConnection;

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

	// Rules
	protected Map<OEntity,OVertexType> entity2vertexType;  
	protected Map<ORelationship,OEdgeType> relationship2edgeType;
	protected Map<String,Integer> edgeTypeName2count;
	protected Map<String,OAggregatorEdge> joinVertex2aggregatorEdges;

	// filters
	protected List<String> includedTables;
	protected List<String> excludedTables;

	public OER2GraphMapper (String driver, String uri, String username, String password, List<String> includedTables, List<String> excludedTables) {
		this.dbSourceConnection = new ODBSourceConnection(driver, uri, username, password);
		this.entity2vertexType = new LinkedHashMap<OEntity,OVertexType>();
		this.relationship2edgeType = new LinkedHashMap<ORelationship,OEdgeType>();
		this.edgeTypeName2count = new TreeMap<String,Integer>();
		this.joinVertex2aggregatorEdges = new LinkedHashMap<String, OAggregatorEdge>();

		if(includedTables != null)
			this.includedTables = includedTables;
		else
			this.includedTables = new ArrayList<String>();

		if(excludedTables != null)
			this.excludedTables = excludedTables;

		else
			this.excludedTables = new ArrayList<String>();

	}


	public void buildSourceSchema(OTeleporterContext context) {

		Connection connection = null;
		OTeleporterStatistics statistics = context.getStatistics();
		statistics.startWork1Time = new Date();
		statistics.runningStepNumber = 1;
		statistics.notifyListeners();

		try {

			connection = this.dbSourceConnection.getConnection(context);
			DatabaseMetaData databaseMetaData = connection.getMetaData();
			String quote = context.getQueryQuote();

			/*
			 *  General DB Info
			 */

			int majorVersion = databaseMetaData.getDatabaseMajorVersion();
			int minorVersion = databaseMetaData.getDatabaseMinorVersion();
			int driverMajorVersion = databaseMetaData.getDriverMajorVersion();
			int driverMinorVersion = databaseMetaData.getDriverMinorVersion();
			String productName = databaseMetaData.getDatabaseProductName();
			String productVersion = databaseMetaData.getDatabaseProductVersion();

			this.dataBaseSchema = new ODataBaseSchema(majorVersion, minorVersion, driverMajorVersion, driverMinorVersion, productName, productVersion);

			Map<String,String> tablesName2schema = new LinkedHashMap<String,String>();

			String tableCatalog = null;
			String tableSchemaPattern = null;
			if(this.dbSourceConnection.getDriver().contains("Oracle")) {
				ResultSet schemas = databaseMetaData.getSchemas();
				while(schemas.next()) {
					if(schemas.getString(1).equalsIgnoreCase(this.dbSourceConnection.getUsername())) {
						tableSchemaPattern = schemas.getString(1);
						break;
					}
				}
			}

			String tableNamePattern = null;
			String[] tableTypes = {"TABLE"};

			ResultSet resultTable = null;
			resultTable = databaseMetaData.getTables(tableCatalog, tableSchemaPattern, tableNamePattern, tableTypes);
			ResultSet resultColumns;
			ResultSet resultPrimaryKeys;
			ResultSet resultForeignKeys;

			String tableSchema = null;
			String tableName = null;

			// Giving db's table names
			while(resultTable.next()) {
				tableSchema = resultTable.getString("TABLE_SCHEM");
				tableName = resultTable.getString("TABLE_NAME");

				if(this.isTableAllowed(tableName))  // filtering tables according to "include-list" and "exclude-list"
					tablesName2schema.put(tableName,tableSchema);  
			}

			int numberOfTables = tablesName2schema.size();
			statistics.totalNumberOfEntities = numberOfTables;

			// closing resultTable
			this.closeCursor(resultTable, context);

			context.getOutputManager().debug("\n%s tables found.\n", numberOfTables);

			OEntity currentEntity;
			OPrimaryKey pKey;

			List<LinkedHashMap<String,String>> currentEntityRelationships1;
			List<LinkedHashMap<String,String>> currentEntityRelationships2;

			// Variables for records counting
			Statement statement = connection.createStatement();
			String sql;
			ResultSet currentTableRecordAmount;
			int totalNumberOfRecord = 0;


			/*
			 *  Entity building
			 */
			String currentTableSchema;
			int iteration = 1;
			for(String currentTableName: tablesName2schema.keySet()) {

				context.getOutputManager().debug("\nBuilding '%s' entity (%s/%s)...\n", currentTableName, iteration, numberOfTables);

				// Counting current-table's record
				currentTableSchema = tablesName2schema.get(currentTableName);
				if(currentTableSchema != null)
					sql = "select count(*) from " + currentTableSchema + "." + quote + currentTableName + quote;
				else
					sql = "select count(*) from " + quote + currentTableName + quote;

				currentTableRecordAmount = statement.executeQuery(sql);
				if (currentTableRecordAmount.next()) {
					totalNumberOfRecord += currentTableRecordAmount.getInt(1);
				}
				this.closeCursor(currentTableRecordAmount, context);

				// creating entity
				currentEntity = new OEntity(currentTableName, currentTableSchema);

				// adding attributes and primary keys
				pKey = new OPrimaryKey(currentEntity);

				String columnCatalog = null;
				String columnSchemaPattern = null;
				String columnNamePattern = null;

				String primaryKeyCatalog = null;
				String primaryKeySchema = currentTableSchema;

				resultColumns = databaseMetaData.getColumns(columnCatalog, columnSchemaPattern, currentTableName, columnNamePattern);
				resultPrimaryKeys = databaseMetaData.getPrimaryKeys(primaryKeyCatalog, primaryKeySchema, currentTableName);

				while(resultColumns.next()) {
					OAttribute currentAttribute = new OAttribute(resultColumns.getString(4), resultColumns.getInt(17), resultColumns.getString(6), currentEntity);
					currentEntity.addAttribute(currentAttribute);

					// if the current attribute is involved in the primary key, it will be added to the attributes of pKey.
					if(this.isPresentInResultPrimaryKeys(resultPrimaryKeys, currentAttribute.getName())) {
						pKey.addAttribute(currentAttribute);
					}
				}
				this.closeCursor(resultColumns, context);
				this.closeCursor(resultPrimaryKeys, context);

				currentEntity.setPrimaryKey(pKey);

				// adding entity to db schema
				this.dataBaseSchema.getEntities().add(currentEntity);

				iteration++;
				context.getOutputManager().debug("\nEntity %s built.\n", currentTableName);
				statistics.builtEntities++;
				statistics.totalNumberOfRecords = totalNumberOfRecord;

			}
			statement.close();


			/*
			 *  Building relationships
			 */

			iteration = 1;
			for(OEntity currentForeignEntity: this.dataBaseSchema.getEntities()) {

				String currentForeignEntityName = currentForeignEntity.getName();
				String foreignSchema = currentForeignEntity.getSchemaName();

				context.getOutputManager().debug("\nBuilding relationships starting from '%s' entity (%s/%s)...\n", currentForeignEntityName, iteration, numberOfTables);

				String foreignCatalog = null;

				resultForeignKeys = databaseMetaData.getImportedKeys(foreignCatalog, foreignSchema, currentForeignEntityName);

				// copy of resultset in a HashLinkedMap
				currentEntityRelationships1 = this.fromResultSetToList(resultForeignKeys, context);
				currentEntityRelationships2 = new LinkedList<LinkedHashMap<String,String>>();

				for(LinkedHashMap<String,String> row: currentEntityRelationships1) {
					currentEntityRelationships2.add(row);
				}

				this.closeCursor(resultForeignKeys, context);

				Iterator<LinkedHashMap<String,String>> it1 = currentEntityRelationships1.iterator();
				Iterator<LinkedHashMap<String,String>> it2 = currentEntityRelationships2.iterator();

				LinkedHashMap<String,String> currentExternalRow;        
				String currentParentTableName = null;
				int currentKeySeq;
				ORelationship currentRelationship;
				OForeignKey currentFk;
				OPrimaryKey currentPk;

				while(it1.hasNext()) {
					currentExternalRow = it1.next();

					// current row has Key_Seq equals to '2' then algorithm is finished and is stopped
					if(currentExternalRow.get("key_seq").equals("2")) {
						break;
					}

					// the original relationship is fetched from the record through the 'parent table' and the 'key sequence numbers'
					currentParentTableName = currentExternalRow.get("pktable_name");
					currentKeySeq = Integer.parseInt(currentExternalRow.get("key_seq"));

					// building each single relationship from each correspondent foreign key
					currentRelationship = new ORelationship(currentForeignEntityName, currentParentTableName);
					currentFk = new OForeignKey(currentForeignEntity);
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
					currentPk = this.dataBaseSchema.getEntityByName(currentParentTableName).getPrimaryKey();

					// adding foreign key to the entity and the relationship, and adding the foreign key to the 'foreign entity'
					currentRelationship.setPrimaryKey(currentPk);
					currentRelationship.setForeignKey(currentFk);
					currentForeignEntity.getForeignKeys().add(currentFk);

					// adding the relationship to the db schema
					this.dataBaseSchema.getRelationships().add(currentRelationship);
					// adding relationship to the current entity
					currentForeignEntity.getRelationships().add(currentRelationship);
					// updating statistics
					statistics.detectedRelationships += 1;
				}

				iteration++;
				context.getOutputManager().debug("\nRelationships from %s built.\n", currentForeignEntityName);
				statistics.doneEntity4Relationship++;

			}

		}catch(SQLException e) {
			if(e.getMessage() != null)
				context.getOutputManager().error(e.getClass().getName() + " - " + e.getMessage());
			else
				context.getOutputManager().error(e.getClass().getName());

			Writer writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			String s = writer.toString();
			context.getOutputManager().debug("\n" + s + "\n");
			System.exit(0);
		}finally {
			try {
				if(connection != null) {
					connection.close();
				}
			}catch(SQLException e) {
				if(e.getMessage() != null)
					context.getOutputManager().error(e.getClass().getName() + " - " + e.getMessage());
				else
					context.getOutputManager().error(e.getClass().getName());

				Writer writer = new StringWriter();
				e.printStackTrace(new PrintWriter(writer));
				String s = writer.toString();
				context.getOutputManager().debug("\n" + s + "\n");
			}
		}

		try {
			if(connection.isClosed())
				context.getOutputManager().debug("\nConnection to DB closed.\n");
			else {
				statistics.warningMessages.add("\nConnection to DB not closed.\n");
			}      
		}catch(SQLException e) {
			if(e.getMessage() != null)
				context.getOutputManager().error(e.getClass().getName() + " - " + e.getMessage());
			else
				context.getOutputManager().error(e.getClass().getName());

			Writer writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			String s = writer.toString();
			context.getOutputManager().debug("\n" + s + "\n");
		}
		statistics.notifyListeners();
		statistics.runningStepNumber = -1;
	}

	/**
	 * @param result
	 */
	private void closeCursor(ResultSet result, OTeleporterContext context) {
		try {
			if(result != null)
				result.close();
		} catch (SQLException e) {
			if(e.getMessage() != null)
				context.getOutputManager().error(e.getClass().getName() + " - " + e.getMessage());
			else
				context.getOutputManager().error(e.getClass().getName());

			Writer writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			String s = writer.toString();
			context.getOutputManager().debug("\n" + s + "\n");
		}

	}

	private boolean isPresentInResultPrimaryKeys(ResultSet resultPrimaryKeys, String attributeName) throws SQLException {

		while(resultPrimaryKeys.next()) {
			if(resultPrimaryKeys.getString(4).equals(attributeName))
				return true;
		}

		return false;   
	}


	/*
	 * Transforms a ResultSet in a List, filtering relationships according to "include/exclude-lists"
	 */

	private List<LinkedHashMap<String,String>> fromResultSetToList(ResultSet resultForeignKeys, OTeleporterContext context) {

		List<LinkedHashMap<String, String>> rows = new LinkedList<LinkedHashMap<String,String>>();

		try{
			int columnsAmount = resultForeignKeys.getMetaData().getColumnCount();

			while(resultForeignKeys.next()) {

				if(this.isTableAllowed(resultForeignKeys.getString("pktable_name"))) {

					LinkedHashMap<String,String> row = new LinkedHashMap<String,String>();
					for(int i=1; i<=columnsAmount; i++) {
						row.put(resultForeignKeys.getMetaData().getColumnName(i).toLowerCase(), resultForeignKeys.getString(i));
					}
					rows.add(row);
				}
			}
		}catch(SQLException e) {
			if(e.getMessage() != null)
				context.getOutputManager().error(e.getClass().getName() + " - " + e.getMessage());
			else
				context.getOutputManager().error(e.getClass().getName());

			Writer writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			String s = writer.toString();
			context.getOutputManager().debug("\n" + s + "\n");
			System.exit(0);
		}
		return rows;
	}


	@Override
	public void buildGraphModel(ONameResolver nameResolver, OTeleporterContext context) {

		this.graphModel = new OGraphModel();
		OTeleporterStatistics statistics = context.getStatistics();
		statistics.startWork2Time = new Date();
		statistics.runningStepNumber = 2;


		/*
		 *  Vertex-type building
		 */

		OVertexType currentVertexType;
		String currentVertexTypeName;
		OModelProperty currentProperty = null;
		OElementType currentParentElement = null;

		int numberOfVertexType = this.dataBaseSchema.getEntities().size();
		statistics.totalNumberOfModelVertices = numberOfVertexType;
		int iteration = 1;
		for(OEntity currentEntity: this.dataBaseSchema.getEntities()) {

			context.getOutputManager().debug("\nBuilding '%s' vertex-type (%s/%s)...\n", currentEntity.getName(), iteration, numberOfVertexType);

			// building correspondent vertex-type
			currentVertexTypeName = nameResolver.resolveVertexName(currentEntity.getName());
			currentVertexType = new OVertexType(currentVertexTypeName);

			// recognizing joint tables of dimension 2
			if(currentEntity.isJoinEntityDim2())
				currentVertexType.setIsFromJoinTable(true);
			else
				currentVertexType.setIsFromJoinTable(false);

			// adding attributes to vertex-type
			for(OAttribute attribute: currentEntity.getAttributes()) {        
				currentProperty = new OModelProperty(nameResolver.resolveVertexProperty(attribute.getName()), attribute.getOrdinalPosition(), attribute.getDataType(), currentEntity.getPrimaryKey().getInvolvedAttributes().contains(attribute));
				currentVertexType.getProperties().add(currentProperty);
			}

			// adding parent vertex if the corresponding entity has a parent
			if(currentEntity.getParentEntity() != null) {
				currentParentElement = this.graphModel.getVertexByNameIgnoreCase(currentEntity.getParentEntity().getName());
				currentVertexType.setParentType(currentParentElement);
				currentVertexType.setInheritanceLevel(currentEntity.getInheritanceLevel());
			}

			// adding inherited attributes to vertex-type
			for(OAttribute attribute: currentEntity.getInheritedAttributes()) {        
				currentProperty = new OModelProperty(nameResolver.resolveVertexProperty(attribute.getName()), attribute.getOrdinalPosition(), attribute.getDataType(), currentEntity.getPrimaryKey().getInvolvedAttributes().contains(attribute));
				currentVertexType.getInheritedProperties().add(currentProperty);
			}

			// adding vertex to the graph model
			this.graphModel.getVerticesType().add(currentVertexType);

			// rules updating
			this.entity2vertexType.put(currentEntity, currentVertexType);

			iteration++;
			context.getOutputManager().debug("\nVertex-type %s built.\n", currentVertexTypeName);
			statistics.builtModelVertexTypes++;
		}

		// sorting vertices type for inheritance level and then for name
		Collections.sort(this.graphModel.getVerticesType());


		/*
		 *  Edge-type building
		 */

		OEdgeType currentEdgeType = null;
		String edgeType = null;
		OVertexType currentOutVertex;
		OVertexType currentInVertex;

		int numberOfEdgeType = this.dataBaseSchema.getRelationships().size();
		statistics.totalNumberOfRelationships = numberOfEdgeType;
		iteration = 1;

		if (numberOfEdgeType > 0) {

			// edges added through relationships (foreign keys of db)
			for(OEntity currentEntity: this.dataBaseSchema.getEntities()) {

				for(ORelationship relationship: currentEntity.getRelationships()) {  
					currentOutVertex = this.graphModel.getVertexByName(nameResolver.resolveVertexName(relationship.getForeignEntityName()));
					currentInVertex = this.graphModel.getVertexByName(nameResolver.resolveVertexName(relationship.getParentEntityName()));
					context.getOutputManager().debug("\nBuilding edge-type from '%s' to '%s' (%s/%s)...\n", currentOutVertex.getName(), currentInVertex.getName(), iteration, numberOfEdgeType);

					if(currentOutVertex != null && currentInVertex != null) {

						// relationships which represents inheritance between different entities don't generate new edge-types,
						// thus new edge type is created iff the parent entity's name (of the relationship) doesn't coincide 
						// with the name of the parent entity of the current entity.
						if(currentEntity.getParentEntity() == null || !currentEntity.getParentEntity().getName().equals(relationship.getParentEntityName())) {

							// if the class edge doesn't exists, it will be created
							edgeType = nameResolver.resolveEdgeName(relationship);

							currentEdgeType = this.graphModel.getEdgeTypeByName(edgeType);
							if(currentEdgeType == null) {
								currentEdgeType = new OEdgeType(edgeType, null, currentInVertex);  // TO UPDATE !!!!!!!!
								this.graphModel.getEdgesType().add(currentEdgeType);
								context.getOutputManager().debug("\nEdge-type %s built.\n", currentEdgeType.getName());
								statistics.builtModelEdgeTypes++;
							}

							// adding the edge to the two vertices
							currentOutVertex.getOutEdgesType().add(currentEdgeType);
							currentInVertex.getInEdgesType().add(currentEdgeType);

							// rules updating
							this.relationship2edgeType.put(relationship, currentEdgeType);
						}
					}
					else {
						context.getOutputManager().error("Error during graph model building phase: informations loss, relationship missed. Edge-type not built.\n");
					}

					iteration++;
					statistics.analizedRelationships++;
				}

				for(ORelationship relationship: currentEntity.getInheritedRelationships()) {  
					currentOutVertex = this.graphModel.getVertexByName(nameResolver.resolveVertexName(currentEntity.getName()));
					currentInVertex = this.graphModel.getVertexByName(nameResolver.resolveVertexName(relationship.getParentEntityName()));
					context.getOutputManager().debug("\nBuilding edge-type from '%s' to '%s' (%s/%s)...\n", currentOutVertex.getName(), currentInVertex.getName(), iteration, numberOfEdgeType);

					if(currentOutVertex != null && currentInVertex != null) {

						currentEdgeType = this.graphModel.getEdgeTypeByName(edgeType);

						// adding the edge to the two vertices
						currentOutVertex.getOutEdgesType().add(currentEdgeType);
						currentInVertex.getInEdgesType().add(currentEdgeType);
						context.getOutputManager().debug("\nEdge-type built.\n");
					}
					else {
						context.getOutputManager().error("Error during graph model building phase: informations loss, relationship missed. Edge-type not built.\n");
					}
				}
			}
		}

		statistics.notifyListeners();
		statistics.runningStepNumber = -1;
	}


	public void JoinTableDim2Aggregation(OTeleporterContext context) {

		OEdgeType newAggregatorEdge;
		OEdgeType currentOutEdge1;
		OEdgeType currentOutEdge2;
		OVertexType outVertexType;
		OVertexType inVertexType;
		String edgeType;

		Iterator<OVertexType> iter = this.graphModel.getVerticesType().iterator();
		OVertexType currentVertex;

		context.getOutputManager().debug("\n\nJoin Table aggregation phase...\n");

		while(iter.hasNext()) {
			currentVertex = iter.next();

			// if vertex is obtained from a join table of dimension 2,
			// then aggregation is performed
			if(currentVertex.isFromJoinTable() && currentVertex.getOutEdgesType().size() == 2) { 

				// building new edge
				currentOutEdge1 = currentVertex.getOutEdgesType().get(0);
				outVertexType = currentOutEdge1.getInVertexType();       
				currentOutEdge2 = currentVertex.getOutEdgesType().get(1); 
				inVertexType = currentOutEdge2.getInVertexType();       
				edgeType = currentOutEdge2.getName();
				newAggregatorEdge = new OEdgeType(edgeType, null, inVertexType);     // TO UPDATE  

				// adding to the edge all properties not belonging to the primary key
				for(OModelProperty currentProperty: currentVertex.getProperties()) {

					// if property does not belong to the primary key
					if(!currentProperty.isFromPrimaryKey()) {
						newAggregatorEdge.getProperties().add(currentProperty);
					}
				}

				// adding to the edge all properties belonging to the old edges
				for(OModelProperty currentProperty: currentOutEdge1.getProperties()) {
					newAggregatorEdge.getProperties().add(currentProperty);
				}
				for(OModelProperty currentProperty: currentOutEdge2.getProperties()) {
					newAggregatorEdge.getProperties().add(currentProperty);
				}


				// removing old edges from graph model and from vertices' "in-edges" collection
				this.graphModel.getEdgesType().remove(currentOutEdge1);
				this.graphModel.getEdgesType().remove(currentOutEdge2);
				outVertexType.getInEdgesType().remove(currentOutEdge1);
				inVertexType.getInEdgesType().remove(currentOutEdge2);

				// adding entry to the map
				this.joinVertex2aggregatorEdges.put(currentVertex.getName(), new OAggregatorEdge(outVertexType.getName(), inVertexType.getName(), newAggregatorEdge.getName()));

				// removing old vertex
				iter.remove();

				// adding new edge to graph model
				this.graphModel.getEdgesType().add(newAggregatorEdge);

				// adding new edge to the vertices' "in/out-edges" collections
				outVertexType.getOutEdgesType().add(newAggregatorEdge);
				inVertexType.getInEdgesType().add(newAggregatorEdge);
			}
		}
		context.getOutputManager().debug("\nAggregation performed.\n");
	}

	public ODataSourceSchema getSourceSchema() {
		return this.getDataBaseSchema();
	}

	public ODataBaseSchema getDataBaseSchema() {
		return this.dataBaseSchema;
	}


	public void setDataBaseSchema(ODataBaseSchema dataBaseSchema) {
		this.dataBaseSchema = dataBaseSchema;
	}


	public Map<OEntity, OVertexType> getEntity2vertexType() {
		return this.entity2vertexType;
	}

	public OEntity getEntityByVertexType(String vertexType) {

		if(vertexType != null) {

			for(OEntity currentEntity: this.entity2vertexType.keySet()) {
				if(this.entity2vertexType.get(currentEntity).getName().equals(vertexType)) {
					return currentEntity;
				}
			}
		}

		return null;
	}

	public OAttribute getAttributeByVertexTypeAndProperty(String vertexType, String propertyName) {

		int position = 0;
		OModelProperty currentProperty;

		if(vertexType != null) {

			OVertexType currentVertexType;

			for(OEntity currentEntity: this.entity2vertexType.keySet()) {
				currentVertexType = this.entity2vertexType.get(currentEntity);
				if(currentVertexType.getName().equals(vertexType)) {
					currentProperty = currentVertexType.getPropertyByName(propertyName);

					// if the current vertex has not the current property and if it has parents, a recursive lookup is performed (inheritance case)
					OVertexType parentType = (OVertexType) currentVertexType.getParentType();
					if(currentProperty == null && parentType != null) {
						position = this.getAttributeByVertexTypeAndProperty(parentType.getName(), propertyName).getOrdinalPosition();
					}
					else {
						position = currentVertexType.getPropertyByName(propertyName).getOrdinalPosition();
					}
					return currentEntity.getAttributeByOrdinalPosition(position);
				}
			}
		}

		return null;

	}

	public void setEntity2vertexType(Map<OEntity, OVertexType> entity2vertexType) {
		this.entity2vertexType = entity2vertexType;
	}


	public Map<ORelationship, OEdgeType> getRelationship2edgeType() {
		return this.relationship2edgeType;
	}


	public void setRelationship2edgeType(Map<ORelationship, OEdgeType> relationship2edgeTypeRules) {
		this.relationship2edgeType = relationship2edgeTypeRules;
	}

	public OVertexType getVertexTypeByName(String name) {

		for(OVertexType currentVertexType: this.entity2vertexType.values()) {
			if(currentVertexType.getName().equals(name)) {
				return currentVertexType;
			}
		}
		return null;
	}

	public Map<String,Integer> getEdgeTypeName2count() {
		return this.edgeTypeName2count;
	}


	public void setEdgeTypeName2count(Map<String, Integer> edgeTypeName2count) {
		this.edgeTypeName2count = edgeTypeName2count;
	}

	public Map<String, OAggregatorEdge> getJoinVertex2aggregatorEdges() {
		return joinVertex2aggregatorEdges;
	}


	public void setJoinVertex2aggregatorEdges(Map<String, OAggregatorEdge> joinVertex2aggregatorEdges) {
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
		s += "- Entity2VertexType Rules:\n\n";
		for(OEntity entity: this.entity2vertexType.keySet()) {
			s += entity.getName() + " --> " + this.entity2vertexType.get(entity).getName() + "\n";
		}
		s += "\n\n- Relaionship2EdgeType Rules:\n\n";
		for(ORelationship relationship: this.relationship2edgeType.keySet()) {
			s += relationship.getForeignEntityName() + "2" + relationship.getParentEntityName() + " --> " + this.relationship2edgeType.get(relationship).getName() + "\n";
		}
		s += "\n\n- EdgeTypeName2Count Rules:\n\n";
		for(String edgeName: this.edgeTypeName2count.keySet()) {
			s += edgeName + " --> " + this.edgeTypeName2count.get(edgeName) + "\n";
		}
		s += "\n";


		return s;
	}

}
