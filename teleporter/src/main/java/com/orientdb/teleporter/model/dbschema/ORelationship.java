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

package com.orientdb.teleporter.model.dbschema;

/**
 * It represents the relationship between two entities (foreign and parent entity)
 * based on the importing of a single primary key (composite or not) through a foreign key.
 * 
 * @author Gabriele Ponzi
 * @email  <gabriele.ponzi--at--gmail.com>
 * 
 */

public class ORelationship {

  private String foreignEntityName;				// Entity importing the key (starting entity)
  private String parentEntityName;				// Entity exporting the key (arrival entity)
  private OForeignKey foreignKey;
  private OPrimaryKey primaryKey;
  private String direction;               // represents the direction of the relationship

  public ORelationship(String foreignEntityName, String parentEntityName) {
    this.foreignEntityName = foreignEntityName;
    this.parentEntityName = parentEntityName;
  }

  public ORelationship(String foreignEntityName, String parentEntityName, OForeignKey foreignKey, OPrimaryKey primaryKey) {
    this.foreignEntityName = foreignEntityName;
    this.parentEntityName = parentEntityName;
    this.foreignKey = foreignKey;
    this.primaryKey = primaryKey;
  }

  public String getForeignEntityName() {
    return this.foreignEntityName;
  }

  public void setForeignEntityName(String foreignEntityName) {
    this.foreignEntityName = foreignEntityName;
  }

  public String getParentEntityName() {
    return this.parentEntityName;
  }

  public void setParentEntityName(String parentEntityName) {
    this.parentEntityName = parentEntityName;
  }

  public OForeignKey getForeignKey() {
    return foreignKey;
  }

  public void setForeignKey(OForeignKey foreignKey) {
    this.foreignKey = foreignKey;
  }

  public OPrimaryKey getPrimaryKey() {
    return primaryKey;
  }

  public void setPrimaryKey(OPrimaryKey primaryKey) {
    this.primaryKey = primaryKey;
  }

  public String getDirection() {
    return this.direction;
  }

  public void setDirection(String direction) {
    this.direction = direction;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((foreignEntityName == null) ? 0 : foreignEntityName.hashCode());
    result = prime * result + ((parentEntityName == null) ? 0 : parentEntityName.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    ORelationship that = (ORelationship) obj;
    if(this.foreignEntityName.equals(that.getForeignEntityName()) && this.parentEntityName.equals(that.getParentEntityName())) {
      if(this.foreignKey.equals(that.getForeignKey()) && this.primaryKey.equals(that.getPrimaryKey())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "ORelationship [foreignEntityName=" + foreignEntityName + ", parentEntityName=" + parentEntityName
        + ", Foreign key=" + this.foreignKey.toString() + ", Primary key=" + this.primaryKey.toString() + "]";
  }



}
