package com.avaje.ebean.dbmigration.ddlgeneration.platform;

import com.avaje.ebean.config.dbplatform.DatabasePlatform;

/**
 * DB2 platform specific DDL.
 */
public class DB2Ddl extends PlatformDdl {

  public DB2Ddl(DatabasePlatform platform) {
    super(platform);
    this.identitySuffix = " generated by default as identity";
  }

}
