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

package com.orientechnologies.orient.teleport.model.dbschema;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * It represents a generic key of the source DB. It's extended from OPrimaryKey
 * and OForeignKey that differ each other only by their usage.
 * 
 * @author Gabriele Ponzi
 * @email  <gabriele.ponzi--at--gmail.com>
 * 
 */

public class OKey {

  protected OEntity belongingEntity;
  protected List<OAttribute> involvedAttributes;

  public OKey() {
    this.involvedAttributes = new LinkedList<OAttribute>();
  }

  public OKey(OEntity belongingEntity) {
    this.belongingEntity = belongingEntity;
    this.involvedAttributes = new LinkedList<OAttribute>();
  }

  public OEntity getBelongingEntity() {
    return this.belongingEntity;
  }

  public void setBelongingEntity(OEntity belongingEntity) {
    this.belongingEntity = belongingEntity;
  }

  public List<OAttribute> getInvolvedAttributes() {
    return this.involvedAttributes;
  }

  public void setInvolvedAttributes(List<OAttribute> involvedAttributes) {
    this.involvedAttributes = involvedAttributes;
  }

  public boolean addAttribute(OAttribute attribute) {
    boolean added = this.involvedAttributes.add(attribute);
    
    if(added) {
      Collections.sort(this.involvedAttributes);
    }
    return added;
  }

  public boolean removeAttribute(OAttribute toRemove) {
    return this.involvedAttributes.remove(toRemove);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((belongingEntity == null) ? 0 : belongingEntity.getName().hashCode());
    result = prime * result + ((involvedAttributes == null) ? 0 : involvedAttributes.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    OKey that = (OKey) obj;

    if(this.belongingEntity.getName().equals(that.belongingEntity.getName())) {
      if(this.involvedAttributes.equals(that.getInvolvedAttributes())) {
        return true;
      }
    }

    return false;
  }
  
  public String toString() {
    String s = "[";
    
    for(OAttribute attribute: this.involvedAttributes) {
      s += attribute.getName() + ",";
    }
    
    s = s.substring(0, s.length()-1);
    
    s += "]";
    
    return s;
  }



}
