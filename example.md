# Developing Morpheus Plugins

## Using the sample Morpheus custom report plugin
The sample custom report plugin

* Query the cypher_item table from the MySQL morpheus database
* Count the total number of cypher items
* Count the different cypher item types (secret, tfvars, uuid, etc.)

### Building the sample plugin

Now that we know what the sample plugin does we'll walk through how to build the plugin.

**Prerequisites**

Before building the sample plugin you must meet the following requirements:

* 

**Build the plugin**

1. Clone the sample plugin repository

   ```bash
   git clone https://github.com/sample-repository
   ```

2. Change the directory to the sample plugin directory

   ```
   cd sample-plugin-repository
   ```

3. Build the sample plugin

   ```
   gradle shadowJar
   ```

4. ftt

### Upload the plugin
Before we can use the plugin, we need to upload the plugin to our Morpheus installation. The plugin 
Administration > Integrations > Plugins


## Creating a Morpheus custom report plugin

### File structure

|Name|Description|Path|
|----|-----------|----|
|build.gradle|||
|gradle.properties|||
|CustomReportProvider.groovy||src/main/com/morpheusdata/reports/ReportsPlugin.groovy|
|ReportsPlugin.groovy|||
|cypherReport.hbs||src/mainresources/renderer/hbs/cypherReport.hbs|



#### Accessing the Morpheus Database
The MySQL database listens on the loopback for a single node AIO installation by default. This means that we have log 

```bash
cat /etc/morpheus/morpheus-secrets.json
```


```bash
{
  "mysql": {
    "root_password": "389dd03291033se42c5404d5d",
    "morpheus_password": "a0c4c59322c898qyw2e1ca59b",
    "ops_password": "0c1d000b792432lwp413f0f546c"
  },
  "rabbitmq": {
    "morpheus_password": "03868913jasdfebb1",
    "queue_user_password": "2c590a02284186ce",
    "cookie": "1401B43E0103PWAA25BE"
  },
  "ui": {
    "ajp_secret": "c4a0ap91d6de6110d59100ac"
  }
}
```

Enter the **morpheus_password** when prompted for the **morpheus** user password.


```bash
/opt/morpheus/embedded/mysql/bin/mysql -u morpheus -h 127.0.0.1 -p
```

Access the **morpheus** database with the `USE moprheus` SQL statement.

```sql
USE morpheus;
```

The tables in the **morpheus** database can be listed with the `SHOW TABLES` SQL statement.

```sql
SHOW TABLES;
```

The `SHOW TABLES` command should output the list of tables in the morpheus database similar to the list displayed below. 

> *The list has been truncated for brevity purposes*

```sql
+--------------------------------------------------------------+
| Tables_in_morpheus                                           |
+--------------------------------------------------------------+
| DATABASECHANGELOG                                            |
| DATABASECHANGELOGLOCK                                        |
| access_token                                                 |
| account                                                      |
| account_invoice                                              |
| backup_integration                                           |
| build_job_execution                                          |
| campaign                                                     |
| compute_action                                               |
| currency_rate_history                                        |
| currency_type                                                |
| cveitem                                                      |
| cypher_item                                                  |
| datastore                                                    |
| datastore_compute_zone_pool                                  |
| datastore_datastore                                          |
| deployment                                                   |
| wiki_page                                                    |
| workload_state                                               |
+--------------------------------------------------------------+
698 rows in set (0.00 sec)

```

In this example the table that we want to use is the **cypher_items** database table which contains the cypher items. Now that we have the table that we want to use we need to know what field are available in the table.

```sql
select COLUMN_NAME,DATA_TYPE from INFORMATION_SCHEMA.COLUMNS where TABLE_NAME='cypher_item';
```

The SQL query should display a list of the fields in the **cypher_items** table that we can access data from.

```
+------------------+-----------+
| COLUMN_NAME      | DATA_TYPE |
+------------------+-----------+
| id               | bigint    |
| encryption_key   | varchar   |
| item_value       | text      |
| date_created     | datetime  |
| last_updated     | datetime  |
| cypher_id        | varchar   |
| lease_timeout    | bigint    |
| last_accessed    | datetime  |
| lease_object_ref | varchar   |
| created_by       | varchar   |
| expire_date      | datetime  |
| item_key         | varchar   |
+------------------+-----------+
12 rows in set (0.00 sec)
```

Now that know the fields that are available in the table we're ready to create a SQL statement to query database for the value of the fields that we're interested in. In our example the fields that we're interested in are the item_key, last_update, last_accessed and least_timeout fields.

```sql
SELECT item_key,last_updated,last_accessed,lease_timeout from cypher_item order by item_key asc
```

The

```
+--------------------------------+---------------------+---------------------+---------------+
| item_key                       | last_updated        | last_accessed       | lease_timeout |
+--------------------------------+---------------------+---------------------+---------------+
| password/32/passwordtest       | 2021-02-17 23:09:19 | 2021-03-08 21:06:10 |             0 |
| password/test                  | 2021-02-17 23:07:50 | 2021-03-08 21:06:07 |             0 |
| random/pltest                  | 2021-03-08 20:50:46 | 2021-03-08 21:06:15 |             0 |
| secret/demo-kube-cluster-token | 2021-05-26 16:54:10 | 2021-05-26 16:54:10 |             0 |
| secret/pechallenge             | 2021-06-01 16:34:17 | 2021-06-01 16:34:20 |             0 |
| tfvars/dev-aws-credentials     | 2021-06-03 16:55:10 | 2021-06-03 16:55:10 |             0 |
| tfvars/vspheredemo             | 2021-02-22 17:23:34 | 2021-06-03 16:53:46 |             0 |
+--------------------------------+---------------------+---------------------+---------------+
7 rows in set (0.00 sec)
```



## Developing a Morpheus plugin



## Create a build.gradle file

Plugin version: 

```groovy
plugins {
    id "com.bertramlabs.asset-pipeline" version "3.3.2"
    id "com.github.johnrengelman.plugin-shadow" version "2.0.3"
}

apply plugin: 'java'
apply plugin: 'groovy'
apply plugin: 'maven-publish'

group = 'com.example'
version = '1.2.2'

sourceCompatibility = '1.8'
targetCompatibility = '1.8'

ext.isReleaseVersion = !version.endsWith("SNAPSHOT")

repositories {
    mavenCentral()
}

dependencies {
    compileOnly 'com.morpheusdata:morpheus-plugin-api:0.8.0'
    compileOnly 'org.codehaus.groovy:groovy-all:2.5.6'
    compileOnly 'io.reactivex.rxjava2:rxjava:2.2.0'
    compileOnly "org.slf4j:slf4j-api:1.7.26"
    compileOnly "org.slf4j:slf4j-parent:1.7.26"
}

jar {
    manifest {
        attributes(
            'Plugin-Class': 'com.morpheusdata.reports.ReportsPlugin', //Reference to Plugin class
            'Plugin-Version': archiveVersion.get() // Get version defined in gradle
        )
    }
}

tasks.assemble.dependsOn tasks.shadowJar
```

## Create a Plugin Provider

```groovy
package com.morpheusdata.reports

import com.morpheusdata.core.Plugin

class ReportsPlugin extends Plugin {

	@Override
	void initialize() {
		CustomReportProvider customReportProvider = new CustomReportProvider(this, morpheus)
		this.pluginProviders.put(customReportProvider.code, customReportProvider)
		this.setName("Custom Cypher Report")
		this.setDescription("A custom report plugin for cypher items")
		
	}

	@Override
	void onDestroy() {
	}
}
```




## Resources

* https://developer.morpheusdata.com/docs
* https://developer.morpheusdata.com/api/index.html?overview-summary.html