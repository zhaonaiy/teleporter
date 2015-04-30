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

package com.orientechnologies.orient.drakkar.importengine;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import com.orientechnologies.orient.drakkar.context.ODrakkarContext;
import com.orientechnologies.orient.drakkar.context.ODrakkarStatistics;
import com.orientechnologies.orient.drakkar.mapper.OER2GraphMapper;
import com.orientechnologies.orient.drakkar.mapper.OSource2GraphMapper;
import com.orientechnologies.orient.drakkar.model.dbschema.OEntity;
import com.orientechnologies.orient.drakkar.model.dbschema.ORelationship;
import com.orientechnologies.orient.drakkar.model.graphmodel.OEdgeType;
import com.orientechnologies.orient.drakkar.model.graphmodel.OVertexType;
import com.orientechnologies.orient.drakkar.nameresolver.ONameResolver;
import com.tinkerpop.blueprints.Vertex;

/**
 * It orchestrates the phases of the importing algorithm from a DB to an Orient Graph DB.
 * Uses an ODBQueryEngine and an OGraphDBCommandEngine for the source DB and the destination Orient DB managing.
 * 
 * @author Gabriele Ponzi
 * @email  gabriele.ponzi--at--gmail.com
 *
 */

public class ODB2GraphImportEngine {


  public ODB2GraphImportEngine() {}


  public void executeImport(String driver, String uri, String username, String password, String outOrientGraphUri, OSource2GraphMapper genericMapper, ONameResolver nameResolver, ODrakkarContext context) throws SQLException {

    ODrakkarStatistics statistics = context.getStatistics();
    statistics.setStartWork4Time(new Date());

    OER2GraphMapper mapper = (OER2GraphMapper) genericMapper;
    ODBQueryEngine dbQueryEngine = new ODBQueryEngine(driver, uri, username, password);    
    OGraphDBCommandEngine graphDBCommandEngine = new OGraphDBCommandEngine(outOrientGraphUri);

    OVertexType currentOutVertexType = null;  
    OVertexType currentInVertexType = null;  
    Vertex currentOutVertex = null;
    OEdgeType edgeType = null;
    
    int insertedVisitedVertex = 0;

    for(OEntity entity: mapper.getDataBaseSchema().getEntities()) {
      
      // for each entity in dbSchema all records are retrieved
      ResultSet records = dbQueryEngine.getRecordsByEntity(entity.getName());
      currentOutVertexType = mapper.getEntity2vertexType().get(entity);

      ResultSet currentRecord = null;
      // each record imported as vertex in the orient graph
      while(records.next()) {
        // upsert of the vertex
        currentRecord = records;
        currentOutVertex = graphDBCommandEngine.upsertVisitedVertex(currentRecord, currentOutVertexType, nameResolver);

        // for each attribute of the entity belonging to the primary key, correspondent relationship is
        // built as edge and for the referenced record a vertex is built (only id)
        for(ORelationship currentRelation: entity.getRelationships()) {
          currentInVertexType = mapper.getVertexTypeByName(nameResolver.resolveVertexName(currentRelation.getParentEntityName())); // aggiungi getVertexTypeByName!
          edgeType = mapper.getRelationship2edgeType().get(currentRelation);
          graphDBCommandEngine.upsertReachedVertexWithEdge(currentRecord, currentRelation, currentOutVertex, currentInVertexType, edgeType.getType(), nameResolver);
        }   
        
        // Statistics updated every 500 inserted visited vertex
        insertedVisitedVertex++;
        if(insertedVisitedVertex % 500 == 0) {
          statistics.incrementImportedRecords(500);
          insertedVisitedVertex = 0;
        }
      }

      // closing connection and statement
      dbQueryEngine.closeAll();
    }
    // Statistics updated with remaining records inserted (if presents)
    statistics.incrementImportedRecords(insertedVisitedVertex);
  }

}
